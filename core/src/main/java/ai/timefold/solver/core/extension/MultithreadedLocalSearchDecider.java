package ai.timefold.solver.core.extension;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;

import ai.timefold.solver.core.api.score.Score;
import ai.timefold.solver.core.config.solver.EnvironmentMode;
import ai.timefold.solver.core.impl.heuristic.move.LegacyMoveAdapter;
import ai.timefold.solver.core.impl.localsearch.decider.LocalSearchDecider;
import ai.timefold.solver.core.impl.localsearch.decider.acceptor.Acceptor;
import ai.timefold.solver.core.impl.localsearch.decider.forager.LocalSearchForager;
import ai.timefold.solver.core.impl.localsearch.scope.LocalSearchMoveScope;
import ai.timefold.solver.core.impl.localsearch.scope.LocalSearchPhaseScope;
import ai.timefold.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import ai.timefold.solver.core.impl.move.MoveRepository;
import ai.timefold.solver.core.impl.score.director.InnerScoreDirector;
import ai.timefold.solver.core.impl.solver.scope.SolverScope;
import ai.timefold.solver.core.impl.solver.termination.PhaseTermination;
import ai.timefold.solver.core.preview.api.move.Move;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Multithreaded version of {@link LocalSearchDecider} that evaluates moves in parallel.
 * Each thread gets its own clone of the ScoreDirector and evaluates a subset of moves.
 * Results are aggregated in a thread-safe forager.
 *
 * @param <Solution_> the solution type
 */
public class MultithreadedLocalSearchDecider<Solution_> extends LocalSearchDecider<Solution_> {

    private static final Logger LOGGER = LoggerFactory.getLogger(MultithreadedLocalSearchDecider.class);

    private final int moveThreadCount;
    private final ThreadFactory threadFactory;
    private final EnvironmentMode environmentMode;
    private ExecutorService executorService;
    private ThreadLocalScoreDirectorPool<Solution_> scoreDirectorPool;

    public MultithreadedLocalSearchDecider(String logIndentation, PhaseTermination<Solution_> termination,
            MoveRepository<Solution_> moveRepository, Acceptor<Solution_> acceptor, LocalSearchForager<Solution_> forager,
            int moveThreadCount, ThreadFactory threadFactory, EnvironmentMode environmentMode) {
        super(logIndentation, termination, moveRepository, acceptor,
                new ThreadSafeLocalSearchForager<>(forager));
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
        LOGGER.debug("Started multithreaded local search with {} threads", moveThreadCount);
    }

    @Override
    public void phaseStarted(LocalSearchPhaseScope<Solution_> phaseScope) {
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

        LOGGER.debug("Initialized ScoreDirector pool with {} threads", moveThreadCount);
    }

    @Override
    public void stepEnded(LocalSearchStepScope<Solution_> stepScope) {
        super.stepEnded(stepScope);

        // Apply the same move that was applied to main solution to all thread solutions
        // This is much cheaper than re-cloning the entire solution
        var pickedMove = stepScope.getStep();
        if (pickedMove != null && scoreDirectorPool != null) {
            // Apply the move to each thread's solution to keep them in sync
            for (int i = 0; i < moveThreadCount; i++) {
                var threadScoreDirector = scoreDirectorPool.get(i);
                threadScoreDirector.executeMove(pickedMove);
            }
            LOGGER.trace("Applied picked move to {} thread solutions", moveThreadCount);
        }
    }

    @Override
    public void decideNextStep(LocalSearchStepScope<Solution_> stepScope) {
        var scoreDirector = stepScope.getScoreDirector();
        scoreDirector.setAllChangesWillBeUndoneBeforeStepEnds(true);

        LOGGER.debug("Starting parallel move evaluation with {} threads", moveThreadCount);

        // Create move dispatcher for coordinating threads
        var moveIterator = moveRepository.iterator();
        var dispatcher = new SynchronizedMoveDispatcher<Solution_>(moveIterator);

        // Launch worker threads - they will use pooled score directors
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

        scoreDirector.setAllChangesWillBeUndoneBeforeStepEnds(false);
        pickMove(stepScope);
    }

    @Override
    public void phaseEnded(LocalSearchPhaseScope<Solution_> phaseScope) {
        super.phaseEnded(phaseScope);

        // Close all pooled score directors
        if (scoreDirectorPool != null) {
            scoreDirectorPool.close();
            LOGGER.debug("Closed ScoreDirector pool");
        }
    }

    @Override
    public void solvingEnded(SolverScope<Solution_> solverScope) {
        super.solvingEnded(solverScope);
        if (executorService != null) {
            executorService.shutdown();
            LOGGER.debug("Shut down multithreaded local search executor service");
        }
    }

    @Override
    public void solvingError(SolverScope<Solution_> solverScope, Exception exception) {
        super.solvingError(solverScope, exception);
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }

    /**
     * Worker task that evaluates moves on a dedicated thread.
     * Pulls moves from the shared dispatcher and evaluates them with a thread-local ScoreDirector.
     */
    private class MoveEvaluationTask implements Callable<Void> {
        private final LocalSearchStepScope<Solution_> stepScope;
        private final SynchronizedMoveDispatcher<Solution_> dispatcher;
        private final int threadIndex;

        MoveEvaluationTask(LocalSearchStepScope<Solution_> stepScope,
                SynchronizedMoveDispatcher<Solution_> dispatcher, int threadIndex) {
            this.stepScope = stepScope;
            this.dispatcher = dispatcher;
            this.threadIndex = threadIndex;
        }

        @Override
        public Void call() {
            var phaseScope = stepScope.getPhaseScope();
            var solverScope = phaseScope.getSolverScope();

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
                    var moveScope = new LocalSearchMoveScope<>(stepScope, moveWithIndex.index, move);

                    // Evaluate the move on this thread's reused score director
                    evaluateMoveOnThread(moveScope, threadScoreDirector);
                    evaluatedCount++;

                    // Check if forager wants to quit early AFTER adding the move
                    if (forager.isQuitEarly()) {
                        dispatcher.signalStop();
                        break;
                    }

                    // Periodically yield and check termination
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

        private <Score_ extends Score<Score_>> void evaluateMoveOnThread(LocalSearchMoveScope<Solution_> moveScope,
                InnerScoreDirector<Solution_, Score_> threadScoreDirector) {
            var moveDirector = threadScoreDirector.getMoveDirector();
            var move = moveScope.getMove();

            if (!LegacyMoveAdapter.isDoable(moveDirector, move)) {
                // Skip non-doable moves
                LOGGER.trace("{}        Thread {} skipping non-doable move: {}", logIndentation, threadIndex, move);
                return;
            }

            var score = threadScoreDirector.executeTemporaryMove(move, assertMoveScoreFromScratch);
            moveScope.setScore(score);
            moveScope.setAccepted(acceptor.isAccepted(moveScope));
            forager.addMove(moveScope);

            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("{}        Thread {} - Move index ({}), score ({}), accepted ({}), move ({}).",
                        logIndentation, threadIndex, moveScope.getMoveIndex(), moveScope.getScore().raw(),
                        moveScope.getAccepted(), moveScope.getMove());
            }
        }
    }

}
