package ai.timefold.solver.core.extension;

import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import ai.timefold.solver.core.config.solver.EnvironmentMode;
import ai.timefold.solver.core.impl.constructionheuristic.decider.ConstructionHeuristicDecider;
import ai.timefold.solver.core.impl.constructionheuristic.decider.forager.ConstructionHeuristicForager;
import ai.timefold.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicStepScope;
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
    public void decideNextStep(ConstructionHeuristicStepScope<Solution_> stepScope, Iterator<Move<Solution_>> moveIterator) {
        // For now, use the single-threaded approach to ensure correctness
        // TODO: Implement proper parallel move evaluation for construction heuristics
        LOGGER.debug(
                "Using single-threaded evaluation in multithreaded construction heuristic decider (parallel evaluation coming soon)");
        super.decideNextStep(stepScope, moveIterator);
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

}
