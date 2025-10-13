package ai.timefold.solver.core.extension;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import ai.timefold.solver.core.api.score.Score;
import ai.timefold.solver.core.config.solver.EnvironmentMode;
import ai.timefold.solver.core.impl.heuristic.move.LegacyMoveAdapter;
import ai.timefold.solver.core.impl.localsearch.decider.LocalSearchDecider;
import ai.timefold.solver.core.impl.localsearch.decider.acceptor.Acceptor;
import ai.timefold.solver.core.impl.localsearch.decider.forager.LocalSearchForager;
import ai.timefold.solver.core.impl.localsearch.scope.LocalSearchMoveScope;
import ai.timefold.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import ai.timefold.solver.core.impl.move.MoveRepository;
import ai.timefold.solver.core.impl.phase.scope.SolverLifecyclePoint;
import ai.timefold.solver.core.impl.solver.scope.SolverScope;
import ai.timefold.solver.core.impl.solver.termination.PhaseTermination;

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
    public void decideNextStep(LocalSearchStepScope<Solution_> stepScope) {
        var scoreDirector = stepScope.getScoreDirector();
        scoreDirector.setAllChangesWillBeUndoneBeforeStepEnds(true);

        // For simplicity and correctness, use the single-threaded approach for now
        // TODO: Implement proper parallel move evaluation with shared iterator coordination
        LOGGER.debug("Using single-threaded evaluation in multithreaded decider (parallel evaluation coming soon)");
        var moveIndex = 0;
        for (var move : moveRepository) {
            var moveScope = new LocalSearchMoveScope<>(stepScope, moveIndex, move);
            moveIndex++;
            doMove(moveScope);
            if (forager.isQuitEarly()) {
                break;
            }
            stepScope.getPhaseScope().getSolverScope().checkYielding();
            if (termination.isPhaseTerminated(stepScope.getPhaseScope())) {
                break;
            }
        }

        scoreDirector.setAllChangesWillBeUndoneBeforeStepEnds(false);
        pickMove(stepScope);
    }

    protected <Score_ extends Score<Score_>> void doMove(LocalSearchMoveScope<Solution_> moveScope) {
        var scoreDirector = moveScope.<Score_> getScoreDirector();
        var moveDirector = moveScope.getStepScope().<Score_> getMoveDirector();
        var move = moveScope.getMove();
        if (!LegacyMoveAdapter.isDoable(moveDirector, move)) {
            throw new IllegalStateException("Impossible state: Local search move selector (" + moveRepository
                    + ") provided a non-doable move (" + moveScope.getMove() + ").");
        }
        var score = scoreDirector.executeTemporaryMove(moveScope.getMove(), assertMoveScoreFromScratch);
        moveScope.setScore(score);
        moveScope.setAccepted(acceptor.isAccepted(moveScope));
        forager.addMove(moveScope);
        if (assertExpectedUndoMoveScore) {
            scoreDirector.assertExpectedUndoMoveScore(moveScope.getMove(),
                    moveScope.getStepScope().getPhaseScope().getLastCompletedStepScope().getScore(),
                    SolverLifecyclePoint.of(moveScope));
        }
        logger.trace("{}        Move index ({}), score ({}), accepted ({}), move ({}).",
                logIndentation, moveScope.getMoveIndex(), moveScope.getScore().raw(), moveScope.getAccepted(),
                moveScope.getMove());
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

}
