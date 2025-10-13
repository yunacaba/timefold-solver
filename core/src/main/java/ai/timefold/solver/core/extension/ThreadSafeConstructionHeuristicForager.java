package ai.timefold.solver.core.extension;

import ai.timefold.solver.core.impl.constructionheuristic.decider.forager.ConstructionHeuristicForager;
import ai.timefold.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicMoveScope;
import ai.timefold.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicPhaseScope;
import ai.timefold.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicStepScope;
import ai.timefold.solver.core.impl.solver.scope.SolverScope;

/**
 * Thread-safe wrapper around a ConstructionHeuristicForager to enable multithreaded move evaluation.
 * Synchronizes access to the underlying forager for concurrent addMove() calls.
 *
 * @param <Solution_> the solution type
 */
public class ThreadSafeConstructionHeuristicForager<Solution_> implements ConstructionHeuristicForager<Solution_> {

    private final ConstructionHeuristicForager<Solution_> delegate;
    private final Object lock = new Object();

    public ThreadSafeConstructionHeuristicForager(ConstructionHeuristicForager<Solution_> delegate) {
        this.delegate = delegate;
    }

    @Override
    public void addMove(ConstructionHeuristicMoveScope<Solution_> moveScope) {
        synchronized (lock) {
            delegate.addMove(moveScope);
        }
    }

    @Override
    public boolean isQuitEarly() {
        synchronized (lock) {
            return delegate.isQuitEarly();
        }
    }

    @Override
    public ConstructionHeuristicMoveScope<Solution_> pickMove(ConstructionHeuristicStepScope<Solution_> stepScope) {
        synchronized (lock) {
            return delegate.pickMove(stepScope);
        }
    }

    @Override
    public void solvingStarted(SolverScope<Solution_> solverScope) {
        delegate.solvingStarted(solverScope);
    }

    @Override
    public void phaseStarted(ConstructionHeuristicPhaseScope<Solution_> phaseScope) {
        delegate.phaseStarted(phaseScope);
    }

    @Override
    public void stepStarted(ConstructionHeuristicStepScope<Solution_> stepScope) {
        delegate.stepStarted(stepScope);
    }

    @Override
    public void stepEnded(ConstructionHeuristicStepScope<Solution_> stepScope) {
        delegate.stepEnded(stepScope);
    }

    @Override
    public void phaseEnded(ConstructionHeuristicPhaseScope<Solution_> phaseScope) {
        delegate.phaseEnded(phaseScope);
    }

    @Override
    public void solvingEnded(SolverScope<Solution_> solverScope) {
        delegate.solvingEnded(solverScope);
    }
}
