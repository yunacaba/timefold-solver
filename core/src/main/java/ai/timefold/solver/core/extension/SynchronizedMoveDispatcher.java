package ai.timefold.solver.core.extension;

import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import ai.timefold.solver.core.preview.api.move.Move;

/**
 * Thread-safe dispatcher that coordinates move distribution across multiple threads.
 * Ensures each move is evaluated exactly once by synchronizing access to the underlying iterator.
 *
 * @param <Solution_> the solution type
 */
public class SynchronizedMoveDispatcher<Solution_> {

    private final Iterator<Move<Solution_>> moveIterator;
    private final AtomicInteger moveIndex;
    private final AtomicBoolean shouldStop;

    public SynchronizedMoveDispatcher(Iterator<Move<Solution_>> moveIterator) {
        this.moveIterator = moveIterator;
        this.moveIndex = new AtomicInteger(0);
        this.shouldStop = new AtomicBoolean(false);
    }

    /**
     * Get the next move from the iterator in a thread-safe manner.
     * Returns null if there are no more moves or if stop has been signaled.
     *
     * @return the next move and its index, or null if no more moves
     */
    public synchronized MoveWithIndex getNextMove() {
        if (shouldStop.get() || !moveIterator.hasNext()) {
            return null;
        }
        var move = moveIterator.next();
        var index = moveIndex.getAndIncrement();
        return new MoveWithIndex(move, index);
    }

    /**
     * Signal that all threads should stop processing moves.
     * This is called when early termination conditions are met.
     */
    public void signalStop() {
        shouldStop.set(true);
    }

    /**
     * Check if stop has been signaled.
     */
    public boolean shouldStop() {
        return shouldStop.get();
    }

    /**
     * Container for a move and its index.
     */
    public static class MoveWithIndex {
        public final Move<?> move;
        public final int index;

        MoveWithIndex(Move<?> move, int index) {
            this.move = move;
            this.index = index;
        }
    }
}
