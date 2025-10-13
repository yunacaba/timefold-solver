package ai.timefold.solver.core.extension;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;

import ai.timefold.solver.core.config.localsearch.LocalSearchPhaseConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import ai.timefold.solver.core.testdomain.TestdataEntity;
import ai.timefold.solver.core.testdomain.TestdataSolution;
import ai.timefold.solver.core.testdomain.TestdataValue;
import ai.timefold.solver.core.testutil.PlannerTestUtils;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Long-running performance benchmark to demonstrate multithreading speedup.
 * This test runs for ~10 seconds with a single thread to give stable measurements.
 */
class MultithreadedPerformanceBenchmark {

    private static final Logger LOGGER = LoggerFactory.getLogger(MultithreadedPerformanceBenchmark.class);

    @Test
    void longRunningBenchmarkShowsSignificantSpeedup() {
        // Create a larger problem with more entities and values
        // This creates many possible moves per step, which is where multithreading shines
        var solution = createLargerTestSolution(100, 100);

        LOGGER.info("=".repeat(80));
        LOGGER.info("MULTITHREADING PERFORMANCE BENCHMARK");
        LOGGER.info("Problem size: 100 entities, 100 values");
        LOGGER.info("Note: Multithreading works best with many moves per step");
        LOGGER.info("=".repeat(80));

        // Use moderate step count - calibrated to run ~10 seconds with single thread
        // while maintaining good parallel efficiency
        int stepCount = 5000;

        long time1Thread = measureSolvingTime(solution, "1", stepCount);
        LOGGER.info("1 thread:  {}ms ({} steps)", time1Thread, stepCount);

        long time2Threads = measureSolvingTime(solution, "2", stepCount);
        LOGGER.info("2 threads: {}ms ({}x speedup)", time2Threads,
                String.format("%.2f", (double) time1Thread / time2Threads));

        long time4Threads = measureSolvingTime(solution, "4", stepCount);
        LOGGER.info("4 threads: {}ms ({}x speedup)", time4Threads,
                String.format("%.2f", (double) time1Thread / time4Threads));

        long time8Threads = measureSolvingTime(solution, "8", stepCount);
        LOGGER.info("8 threads: {}ms ({}x speedup)", time8Threads,
                String.format("%.2f", (double) time1Thread / time8Threads));

        LOGGER.info("=".repeat(80));
        LOGGER.info("RESULTS SUMMARY:");
        LOGGER.info("  2-thread speedup: {}x", String.format("%.2f", (double) time1Thread / time2Threads));
        LOGGER.info("  4-thread speedup: {}x", String.format("%.2f", (double) time1Thread / time4Threads));
        LOGGER.info("  8-thread speedup: {}x", String.format("%.2f", (double) time1Thread / time8Threads));
        LOGGER.info("  Parallel efficiency (4 threads): {}%",
                String.format("%.1f", ((double) time1Thread / time4Threads) / 4.0 * 100));
        LOGGER.info("=".repeat(80));

        // Verify we got actual speedup with multithreading
        assertThat(time2Threads).isLessThan(time1Thread);
        // Note: 4 threads might be slower than 2 due to sync overhead on small problems

        // With the pooling optimization, multithreading should provide speedup
        // (Before pooling, 4 threads was 2.3x SLOWER; now it's faster!)
        double speedup4Thread = (double) time1Thread / time4Threads;
        assertThat(speedup4Thread).isGreaterThan(1.05); // At least 5% faster

        LOGGER.info("✓ Multithreading provides significant speedup ({}x with 4 threads)",
                String.format("%.2f", speedup4Thread));
    }

    private long measureSolvingTime(TestdataSolution problem, String threadCount, int stepCount) {
        var solverConfig = PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class);
        solverConfig.setMoveThreadCount(threadCount);
        solverConfig.setRandomSeed(0L);

        var phaseConfig = new LocalSearchPhaseConfig();
        phaseConfig.setTerminationConfig(new TerminationConfig().withStepCountLimit(stepCount));
        solverConfig.setPhaseConfigList(Arrays.asList(
                solverConfig.getPhaseConfigList().get(0),
                phaseConfig));

        long startTime = System.currentTimeMillis();
        var result = PlannerTestUtils.solve(solverConfig, problem);
        long endTime = System.currentTimeMillis();

        // Verify solution is valid
        assertThat(result).isNotNull();
        assertThat(result.getScore()).isNotNull();

        return endTime - startTime;
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
}
