package ai.timefold.solver.core.extension;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;

import ai.timefold.solver.core.api.score.Score;
import ai.timefold.solver.core.config.solver.EnvironmentMode;
import ai.timefold.solver.core.impl.constructionheuristic.decider.ConstructionHeuristicDecider;
import ai.timefold.solver.core.impl.constructionheuristic.decider.forager.ConstructionHeuristicForager;
import ai.timefold.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicMoveScope;
import ai.timefold.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicPhaseScope;
import ai.timefold.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicStepScope;
import ai.timefold.solver.core.impl.heuristic.move.LegacyMoveAdapter;
import ai.timefold.solver.core.impl.heuristic.move.NoChangeMove;
import ai.timefold.solver.core.impl.score.director.InnerScoreDirector;
import ai.timefold.solver.core.impl.solver.scope.SolverScope;
import ai.timefold.solver.core.impl.solver.termination.PhaseTermination;
import ai.timefold.solver.core.preview.api.move.Move;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Multithreaded version of {@link ConstructionHeuristicDecider} that evaluates moves in parallel.
 * Each thread gets its own clone of the ScoreDirector and evaluates a subset of moves.
 * Results are aggregated in a thread-safe forager.
 *
 * @param <Solution_> the solution type
 */
public class MultithreadedConstructionHeuristicDecider<Solution_> extends ConstructionHeuristicDecider<Solution_> {

    private static final Logger LOGGER = LoggerFactory.getLogger(MultithreadedConstructionHeuristicDecider.class);

    private final int moveThreadCount;
    private final ThreadFactory threadFactory;
    private final EnvironmentMode environmentMode;
    private ExecutorService executorService;
    private ThreadLocalScoreDirectorPool<Solution_> scoreDirectorPool;

    public MultithreadedConstructionHeuristicDecider(String logIndentation, PhaseTermination<Solution_> termination,
            ConstructionHeuristicForager<Solution_> forager, int moveThreadCount, ThreadFactory threadFactory,
            EnvironmentMode environmentMode) {
        super(logIndentation, termination, new ThreadSafeConstructionHeuristicForager<>(forager));
        if (moveThreadCount < 2) {
            throw new IllegalArgumentException(
                    "The moveThreadCount (" + moveThreadCount + ") must be at least 2 for multithreaded solving.");
        }
        this.moveThreadCount = moveThreadCount;
        this.threadFactory = threadFactory;
        this.environmentMode = environmentMode;
    }

    @Override
    public void solvingStarted(SolverScope<Solution_> solverScope) {
        super.solvingStarted(solverScope);
        executorService = threadFactory != null
                ? Executors.newFixedThreadPool(moveThreadCount, threadFactory)
                : Executors.newFixedThreadPool(moveThreadCount);
        LOGGER.debug("Started multithreaded construction heuristic with {} threads", moveThreadCount);
    }

    @Override
    public void phaseStarted(ConstructionHeuristicPhaseScope<Solution_> phaseScope) {
        super.phaseStarted(phaseScope);

        // Initialize the score director pool once at phase start
        var solverScope = phaseScope.getSolverScope();
        scoreDirectorPool = new ThreadLocalScoreDirectorPool<>(
                moveThreadCount,
                solverScope.getSolutionDescriptor());

        // Clone solution once per thread instead of once per step
        var mainScoreDirector = phaseScope.getScoreDirector();
        scoreDirectorPool.initialize(
                mainScoreDirector.getScoreDirectorFactory(),
                mainScoreDirector.getWorkingSolution());

        LOGGER.debug("Initialized ScoreDirector pool with {} threads for construction heuristic", moveThreadCount);
    }

    @Override
    public void stepStarted(ConstructionHeuristicStepScope<Solution_> stepScope) {
        super.stepStarted(stepScope);

        // Sync thread solutions with main solution before evaluating moves
        var mainSolution = stepScope.getScoreDirector().getWorkingSolution();
        scoreDirectorPool.syncFromMainSolution(mainSolution);
    }

    @Override
    public void decideNextStep(ConstructionHeuristicStepScope<Solution_> stepScope, Iterator<Move<Solution_>> moveIterator) {
        LOGGER.debug("Starting parallel move evaluation with {} threads", moveThreadCount);

        // Create move dispatcher for coordinating threads
        var dispatcher = new SynchronizedMoveDispatcher<Solution_>(moveIterator);

        // Launch worker threads
        List<Future<Void>> futures = new ArrayList<>();
        for (int threadIndex = 0; threadIndex < moveThreadCount; threadIndex++) {
            var task = new MoveEvaluationTask(stepScope, dispatcher, threadIndex);
            futures.add(executorService.submit(task));
        }

        // Wait for all threads to complete
        try {
            for (Future<Void> future : futures) {
                future.get();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            dispatcher.signalStop();
            throw new RuntimeException("Multithreaded move evaluation was interrupted", e);
        } catch (ExecutionException e) {
            dispatcher.signalStop();
            throw new RuntimeException("Error during multithreaded move evaluation", e.getCause());
        }

        // Pick the best move
        pickMove(stepScope);
    }

    @Override
    public void phaseEnded(ConstructionHeuristicPhaseScope<Solution_> phaseScope) {
        super.phaseEnded(phaseScope);

        // Close all pooled score directors
        if (scoreDirectorPool != null) {
            scoreDirectorPool.close();
            LOGGER.debug("Closed ScoreDirector pool for construction heuristic");
        }
    }

    @Override
    public void solvingEnded(SolverScope<Solution_> solverScope) {
        super.solvingEnded(solverScope);
        if (executorService != null) {
            executorService.shutdown();
            LOGGER.debug("Shut down multithreaded construction heuristic executor service");
        }
    }

    @Override
    public void solvingError(SolverScope<Solution_> solverScope, Exception exception) {
        super.solvingError(solverScope, exception);
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }

    private static <Solution_> boolean isAllowedNonDoableMove(Move<Solution_> move) {
        if (move instanceof LegacyMoveAdapter<Solution_> legacyMove) {
            var adaptedMove = legacyMove.legacyMove();
            return adaptedMove instanceof NoChangeMove<Solution_>
                    || adaptedMove instanceof ai.timefold.solver.core.impl.heuristic.selector.move.generic.ChangeMove<Solution_>;
        } else {
            return false;
        }
    }

    /**
     * Worker task that evaluates moves on a dedicated thread.
     * Pulls moves from the shared dispatcher and evaluates them with a thread-local ScoreDirector.
     */
    private class MoveEvaluationTask implements Callable<Void> {
        private final ConstructionHeuristicStepScope<Solution_> stepScope;
        private final SynchronizedMoveDispatcher<Solution_> dispatcher;
        private final int threadIndex;

        MoveEvaluationTask(ConstructionHeuristicStepScope<Solution_> stepScope,
                SynchronizedMoveDispatcher<Solution_> dispatcher, int threadIndex) {
            this.stepScope = stepScope;
            this.dispatcher = dispatcher;
            this.threadIndex = threadIndex;
        }

        @Override
        public Void call() {
            var phaseScope = stepScope.getPhaseScope();
            var solverScope = phaseScope.getSolverScope();
            var moveDirector = stepScope.getMoveDirector();

            try {
                // Get the reused score director for this thread from the pool
                // This avoids cloning the solution on every step!
                var threadScoreDirector = scoreDirectorPool.get(threadIndex);

                int evaluatedCount = 0;

                // Process moves until dispatcher signals stop or no more moves
                while (!dispatcher.shouldStop()) {
                    // Check phase termination
                    if (termination.isPhaseTerminated(phaseScope)) {
                        dispatcher.signalStop();
                        break;
                    }

                    // Get next move from dispatcher
                    var moveWithIndex = dispatcher.getNextMove();
                    if (moveWithIndex == null) {
                        break; // No more moves
                    }

                    @SuppressWarnings("unchecked")
                    var move = (Move<Solution_>) moveWithIndex.move;

                    // Check if move is doable (construction heuristic specific logic)
                    var allowedNonDoableMove = isAllowedNonDoableMove(move);
                    if (!allowedNonDoableMove && !LegacyMoveAdapter.isDoable(moveDirector, move)) {
                        continue; // Skip non-doable moves
                    }

                    var moveScope = new ConstructionHeuristicMoveScope<>(stepScope, moveWithIndex.index, move);

                    // Evaluate the move on this thread's reused score director
                    evaluateMoveOnThread(moveScope, threadScoreDirector);
                    evaluatedCount++;

                    // Check if forager wants to quit early AFTER adding the move
                    if (getForager().isQuitEarly()) {
                        dispatcher.signalStop();
                        break;
                    }

                    // Periodically yield
                    if (evaluatedCount % 100 == 0) {
                        solverScope.checkYielding();
                    }
                }

                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Thread {} evaluated {} moves", threadIndex, evaluatedCount);
                }

            } catch (Exception e) {
                dispatcher.signalStop();
                throw e;
            }

            return null;
        }

        private <Score_ extends Score<Score_>> void evaluateMoveOnThread(ConstructionHeuristicMoveScope<Solution_> moveScope,
                InnerScoreDirector<Solution_, Score_> threadScoreDirector) {
            var score = threadScoreDirector.executeTemporaryMove(moveScope.getMove(), assertMoveScoreFromScratch);
            moveScope.setScore(score);
            getForager().addMove(moveScope);

            if (isLoggingEnabled() && LOGGER.isTraceEnabled()) {
                LOGGER.trace("{}        Thread {} - Move index ({}), score ({}), move ({}).",
                        logIndentation, threadIndex, moveScope.getMoveIndex(), moveScope.getScore().raw(),
                        moveScope.getMove());
            }
        }
    }

}
