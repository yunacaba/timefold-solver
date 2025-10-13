package ai.timefold.solver.core.extension;

import ai.timefold.solver.core.impl.localsearch.decider.forager.LocalSearchForager;
import ai.timefold.solver.core.impl.localsearch.scope.LocalSearchMoveScope;
import ai.timefold.solver.core.impl.localsearch.scope.LocalSearchPhaseScope;
import ai.timefold.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import ai.timefold.solver.core.impl.solver.scope.SolverScope;

/**
 * Thread-safe wrapper around a LocalSearchForager to enable multithreaded move evaluation.
 * Synchronizes access to the underlying forager for concurrent addMove() calls.
 *
 * @param <Solution_> the solution type
 */
public class ThreadSafeLocalSearchForager<Solution_> implements LocalSearchForager<Solution_> {

    private final LocalSearchForager<Solution_> delegate;
    private final Object lock = new Object();

    public ThreadSafeLocalSearchForager(LocalSearchForager<Solution_> delegate) {
        this.delegate = delegate;
    }

    @Override
    public boolean supportsNeverEndingMoveSelector() {
        return delegate.supportsNeverEndingMoveSelector();
    }

    @Override
    public synchronized void addMove(LocalSearchMoveScope<Solution_> moveScope) {
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
    public LocalSearchMoveScope<Solution_> pickMove(LocalSearchStepScope<Solution_> stepScope) {
        synchronized (lock) {
            return delegate.pickMove(stepScope);
        }
    }

    @Override
    public void solvingStarted(SolverScope<Solution_> solverScope) {
        delegate.solvingStarted(solverScope);
    }

    @Override
    public void phaseStarted(LocalSearchPhaseScope<Solution_> phaseScope) {
        delegate.phaseStarted(phaseScope);
    }

    @Override
    public void stepStarted(LocalSearchStepScope<Solution_> stepScope) {
        delegate.stepStarted(stepScope);
    }

    @Override
    public void stepEnded(LocalSearchStepScope<Solution_> stepScope) {
        delegate.stepEnded(stepScope);
    }

    @Override
    public void phaseEnded(LocalSearchPhaseScope<Solution_> phaseScope) {
        delegate.phaseEnded(phaseScope);
    }

    @Override
    public void solvingEnded(SolverScope<Solution_> solverScope) {
        delegate.solvingEnded(solverScope);
    }
}
