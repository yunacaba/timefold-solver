package ai.timefold.solver.core.extension;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import ai.timefold.solver.core.preview.api.move.Move;
import ai.timefold.solver.core.preview.api.move.MutableSolutionView;
import ai.timefold.solver.core.preview.api.move.Rebaser;

import org.junit.jupiter.api.Test;

/**
 * Tests for the SynchronizedMoveDispatcher to verify thread-safe move distribution.
 */
class SynchronizedMoveDispatcherTest {

    @Test
    void dispatcherDistributesMovesWithoutDuplicates() throws Exception {
        // Create a list of 100 test moves
        List<Move<Object>> moves = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            moves.add(createTestMove(i));
        }

        var dispatcher = new SynchronizedMoveDispatcher<>(moves.iterator());

        // Track which moves each thread processed
        Set<Integer> processedIndices = ConcurrentHashMap.newKeySet();
        AtomicInteger totalProcessed = new AtomicInteger(0);

        // Create 4 threads that will pull moves from the dispatcher
        ExecutorService executor = Executors.newFixedThreadPool(4);
        List<Future<?>> futures = new ArrayList<>();

        for (int threadId = 0; threadId < 4; threadId++) {
            futures.add(executor.submit(() -> {
                while (true) {
                    var moveWithIndex = dispatcher.getNextMove();
                    if (moveWithIndex == null) {
                        break;
                    }
                    processedIndices.add(moveWithIndex.index);
                    totalProcessed.incrementAndGet();
                }
            }));
        }

        // Wait for all threads to finish
        for (Future<?> future : futures) {
            future.get();
        }
        executor.shutdown();

        // Verify all 100 moves were processed exactly once
        assertThat(totalProcessed.get()).isEqualTo(100);
        assertThat(processedIndices).hasSize(100);
        for (int i = 0; i < 100; i++) {
            assertThat(processedIndices).contains(i);
        }
    }

    @Test
    void dispatcherStopsWhenSignaled() throws Exception {
        // Create an infinite sequence of moves
        List<Move<Object>> moves = new ArrayList<>();
        for (int i = 0; i < 10000; i++) {
            moves.add(createTestMove(i));
        }

        var dispatcher = new SynchronizedMoveDispatcher<>(moves.iterator());
        AtomicInteger totalProcessed = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<?>> futures = new ArrayList<>();

        for (int threadId = 0; threadId < 2; threadId++) {
            futures.add(executor.submit(() -> {
                while (true) {
                    if (dispatcher.shouldStop()) {
                        break;
                    }
                    var moveWithIndex = dispatcher.getNextMove();
                    if (moveWithIndex == null) {
                        break;
                    }
                    totalProcessed.incrementAndGet();

                    // Stop after processing 50 moves total
                    if (totalProcessed.get() >= 50) {
                        dispatcher.signalStop();
                    }
                }
            }));
        }

        for (Future<?> future : futures) {
            future.get();
        }
        executor.shutdown();

        // Should have stopped around 50, but might be slightly more due to race conditions
        assertThat(totalProcessed.get()).isGreaterThanOrEqualTo(50);
        assertThat(totalProcessed.get()).isLessThan(100); // Much less than total available
    }

    private Move<Object> createTestMove(int index) {
        // Create a simple mock move for testing
        return new Move<Object>() {
            @Override
            public void execute(MutableSolutionView<Object> solutionView) {
                // No-op move for testing
            }

            @Override
            public Move<Object> rebase(Rebaser rebaser) {
                return this; // No rebasing needed for this test move
            }

            @Override
            public String toString() {
                return "TestMove(" + index + ")";
            }
        };
    }
}
