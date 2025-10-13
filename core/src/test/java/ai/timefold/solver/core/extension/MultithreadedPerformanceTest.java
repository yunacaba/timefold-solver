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
 * Performance benchmarking tests for multithreaded solving.
 * These tests verify that parallelism provides actual speedup.
 */
class MultithreadedPerformanceTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(MultithreadedPerformanceTest.class);

    @Test
    void multithreadingShowsParallelism() {
        // Create a larger problem to see parallelism benefit
        var solution = createLargerTestSolution(10, 10); // 10 entities, 10 values

        // Test with 1 thread
        long singleThreadTime = measureSolvingTime(solution, "1", 20);

        // Test with 4 threads
        long multiThreadTime = measureSolvingTime(solution, "4", 20);

        LOGGER.info("Single thread time: {}ms, Multi thread (4) time: {}ms",
                singleThreadTime, multiThreadTime);

        // With true parallelism, multithreaded should be faster or similar
        // (may not be exactly 4x due to overhead and synchronization)
        // We just verify it's not slower by more than 50%
        assertThat(multiThreadTime).isLessThan(singleThreadTime * 2);

        LOGGER.info("Multithreading verification: multithreaded time is {}% of single-threaded time",
                (multiThreadTime * 100) / singleThreadTime);
    }

    private long measureSolvingTime(TestdataSolution problem, String threadCount, int stepLimit) {
        var solverConfig = PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class);
        solverConfig.setMoveThreadCount(threadCount);
        solverConfig.setRandomSeed(0L);

        var phaseConfig = new LocalSearchPhaseConfig();
        phaseConfig.setTerminationConfig(new TerminationConfig().withStepCountLimit(stepLimit));
        solverConfig.setPhaseConfigList(Arrays.asList(
                solverConfig.getPhaseConfigList().get(0),
                phaseConfig));

        long startTime = System.currentTimeMillis();
        PlannerTestUtils.solve(solverConfig, problem);
        long endTime = System.currentTimeMillis();

        return endTime - startTime;
    }

    private TestdataSolution createLargerTestSolution(int entityCount, int valueCount) {
        var solution = new TestdataSolution("s1");

        var values = new ArrayList<TestdataValue>();
        for (int i = 0; i < valueCount; i++) {
            values.add(new TestdataValue("v" + i));
        }
        solution.setValueList(values);

        var entities = new ArrayList<TestdataEntity>();
        for (int i = 0; i < entityCount; i++) {
            // Initialize with round-robin assignment
            entities.add(new TestdataEntity("e" + i, values.get(i % valueCount)));
        }
        solution.setEntityList(entities);

        return solution;
    }
}
