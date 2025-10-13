package ai.timefold.solver.core.extension;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.localsearch.LocalSearchPhaseConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import ai.timefold.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import ai.timefold.solver.core.impl.phase.scope.AbstractPhaseScope;
import ai.timefold.solver.core.impl.solver.DefaultSolver;
import ai.timefold.solver.core.testdomain.TestdataEntity;
import ai.timefold.solver.core.testdomain.TestdataSolution;
import ai.timefold.solver.core.testdomain.TestdataValue;
import ai.timefold.solver.core.testutil.PlannerTestUtils;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Throughput benchmark that measures steps per second with different thread counts.
 * This test runs for 10 seconds and measures how much work gets done.
 */
class MultithreadedThroughputBenchmark {

    private static final Logger LOGGER = LoggerFactory.getLogger(MultithreadedThroughputBenchmark.class);

    @Test
    void tenSecondThroughputBenchmark() {
        // Create a problem with many possible moves per step
        var solution = createLargerTestSolution(100, 100);

        LOGGER.info("=".repeat(80));
        LOGGER.info("MULTITHREADING THROUGHPUT BENCHMARK (10 seconds each)");
        LOGGER.info("Problem size: 100 entities, 100 values");
        LOGGER.info("=".repeat(80));

        Duration testDuration = Duration.ofSeconds(10);

        var result1 = measureThroughput(solution, "1", testDuration);
        LOGGER.info("1 thread:  {} steps in 10s", result1.stepsCompleted);

        var result2 = measureThroughput(solution, "2", testDuration);
        double speedup2 = (double) result2.stepsCompleted / result1.stepsCompleted;
        LOGGER.info("2 threads: {} steps in 10s ({}x throughput)", result2.stepsCompleted,
                String.format("%.2f", speedup2));

        var result4 = measureThroughput(solution, "4", testDuration);
        double speedup4 = (double) result4.stepsCompleted / result1.stepsCompleted;
        LOGGER.info("4 threads: {} steps in 10s ({}x throughput)", result4.stepsCompleted,
                String.format("%.2f", speedup4));

        var result8 = measureThroughput(solution, "8", testDuration);
        double speedup8 = (double) result8.stepsCompleted / result1.stepsCompleted;
        LOGGER.info("8 threads: {} steps in 10s ({}x throughput)", result8.stepsCompleted,
                String.format("%.2f", speedup8));

        LOGGER.info("=".repeat(80));
        LOGGER.info("RESULTS SUMMARY:");
        LOGGER.info("  2-thread throughput improvement: {}x", String.format("%.2f", speedup2));
        LOGGER.info("  4-thread throughput improvement: {}x", String.format("%.2f", speedup4));
        LOGGER.info("  8-thread throughput improvement: {}x", String.format("%.2f", speedup8));
        LOGGER.info("  Parallel efficiency (4 threads): {}%",
                String.format("%.1f", speedup4 / 4.0 * 100));
        LOGGER.info("=".repeat(80));

        // Verify multithreading improves throughput
        assertThat(result2.stepsCompleted).isGreaterThan(result1.stepsCompleted);
        assertThat(result4.stepsCompleted).isGreaterThan(result2.stepsCompleted);

        // 4 threads should provide at least 1.3x throughput improvement
        assertThat(speedup4).isGreaterThan(1.3);

        LOGGER.info("✓ Multithreading improves throughput by {}x with 4 threads",
                String.format("%.2f", speedup4));
    }

    private ThroughputResult measureThroughput(TestdataSolution problem, String threadCount, Duration timeLimit) {
        var solverConfig = PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class);
        solverConfig.setMoveThreadCount(threadCount);
        solverConfig.setRandomSeed(0L);

        var phaseConfig = new LocalSearchPhaseConfig();
        phaseConfig.setTerminationConfig(new TerminationConfig().withSpentLimit(timeLimit));
        solverConfig.setPhaseConfigList(Arrays.asList(
                solverConfig.getPhaseConfigList().get(0),
                phaseConfig));

        AtomicLong stepCount = new AtomicLong(0);

        SolverFactory<TestdataSolution> solverFactory = SolverFactory.create(solverConfig);
        Solver<TestdataSolution> solver = solverFactory.buildSolver();

        // Track step count from local search phase
        ((DefaultSolver<TestdataSolution>) solver).addPhaseLifecycleListener(new PhaseLifecycleListenerAdapter<>() {
            @Override
            public void phaseEnded(AbstractPhaseScope<TestdataSolution> phaseScope) {
                // Only count local search phase steps (phase index 1)
                if (phaseScope.getPhaseIndex() == 1) {
                    stepCount.set(phaseScope.getNextStepIndex());
                }
            }
        });

        solver.solve(problem);

        return new ThroughputResult(stepCount.get());
    }

    private TestdataSolution createLargerTestSolution(int entityCount, int valueCount) {
        var solution = new TestdataSolution("benchmark");

        var values = new ArrayList<TestdataValue>();
        for (int i = 0; i < valueCount; i++) {
            values.add(new TestdataValue("v" + i));
        }
        solution.setValueList(values);

        var entities = new ArrayList<TestdataEntity>();
        for (int i = 0; i < entityCount; i++) {
            // Initialize with round-robin assignment to start from a valid solution
            entities.add(new TestdataEntity("e" + i, values.get(i % valueCount)));
        }
        solution.setEntityList(entities);

        return solution;
    }

    private static class ThroughputResult {
        final long stepsCompleted;

        ThroughputResult(long stepsCompleted) {
            this.stepsCompleted = stepsCompleted;
        }
    }
}
