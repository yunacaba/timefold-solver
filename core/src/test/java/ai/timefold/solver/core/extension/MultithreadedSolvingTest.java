package ai.timefold.solver.core.extension;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import ai.timefold.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import ai.timefold.solver.core.config.localsearch.LocalSearchPhaseConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import ai.timefold.solver.core.testdomain.TestdataEntity;
import ai.timefold.solver.core.testdomain.TestdataSolution;
import ai.timefold.solver.core.testdomain.TestdataValue;
import ai.timefold.solver.core.testutil.PlannerTestUtils;

import org.junit.jupiter.api.Test;

/**
 * Tests for multithreaded solving in Community Edition.
 */
class MultithreadedSolvingTest {

    @Test
    void multithreadedLocalSearchWithTwoThreads() {
        var solverConfig = PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class);
        solverConfig.setMoveThreadCount("2");

        var phaseConfig = new LocalSearchPhaseConfig();
        phaseConfig.setTerminationConfig(new TerminationConfig().withScoreCalculationCountLimit(100L));
        solverConfig.setPhaseConfigList(Arrays.asList(
                // First run construction heuristic
                solverConfig.getPhaseConfigList().get(0),
                // Then run local search with 2 threads
                phaseConfig));

        var solution = createTestSolution();
        solution = PlannerTestUtils.solve(solverConfig, solution);

        assertThat(solution).isNotNull();
        assertThat(solution.getScore()).isNotNull();
        assertThat(solution.getScore().score()).isGreaterThanOrEqualTo(-3);
        // Verify all entities have values assigned
        for (var entity : solution.getEntityList()) {
            assertThat(entity.getValue()).isNotNull();
        }
    }

    @Test
    void multithreadedLocalSearchWithFourThreads() {
        var solverConfig = PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class);
        solverConfig.setMoveThreadCount("4");

        var phaseConfig = new LocalSearchPhaseConfig();
        phaseConfig.setTerminationConfig(new TerminationConfig().withScoreCalculationCountLimit(100L));
        solverConfig.setPhaseConfigList(Arrays.asList(
                solverConfig.getPhaseConfigList().get(0),
                phaseConfig));

        var solution = createTestSolution();
        solution = PlannerTestUtils.solve(solverConfig, solution);

        assertThat(solution).isNotNull();
        assertThat(solution.getScore()).isNotNull();
        // Verify all entities have values assigned
        for (var entity : solution.getEntityList()) {
            assertThat(entity.getValue()).isNotNull();
        }
    }

    @Test
    void multithreadedConstructionHeuristicWithTwoThreads() {
        var solverConfig = PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class);
        solverConfig.setMoveThreadCount("2");

        var constructionPhaseConfig = new ConstructionHeuristicPhaseConfig();
        solverConfig.setPhaseConfigList(Arrays.asList(constructionPhaseConfig));

        var solution = createUnassignedTestSolution();
        solution = PlannerTestUtils.solve(solverConfig, solution);

        assertThat(solution).isNotNull();
        assertThat(solution.getScore()).isNotNull();
        // Verify all entities have values assigned after construction heuristic
        for (var entity : solution.getEntityList()) {
            assertThat(entity.getValue()).isNotNull();
        }
    }

    @Test
    void multithreadedConstructionHeuristicWithFourThreads() {
        var solverConfig = PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class);
        solverConfig.setMoveThreadCount("4");

        var constructionPhaseConfig = new ConstructionHeuristicPhaseConfig();
        solverConfig.setPhaseConfigList(Arrays.asList(constructionPhaseConfig));

        var solution = createUnassignedTestSolution();
        solution = PlannerTestUtils.solve(solverConfig, solution);

        assertThat(solution).isNotNull();
        assertThat(solution.getScore()).isNotNull();
        // Verify all entities have values assigned after construction heuristic
        for (var entity : solution.getEntityList()) {
            assertThat(entity.getValue()).isNotNull();
        }
    }

    @Test
    void multithreadedSolvingProducesValidResults() {
        // Solve with single thread
        var singleThreadConfig = PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class);
        singleThreadConfig.setMoveThreadCount("1");
        singleThreadConfig.setRandomSeed(0L); // Fixed seed for determinism

        var phaseConfig = new LocalSearchPhaseConfig();
        phaseConfig.setTerminationConfig(new TerminationConfig().withScoreCalculationCountLimit(50L));
        singleThreadConfig.setPhaseConfigList(Arrays.asList(
                singleThreadConfig.getPhaseConfigList().get(0),
                phaseConfig));

        var solution1 = createTestSolution();
        solution1 = PlannerTestUtils.solve(singleThreadConfig, solution1);

        // Solve with multiple threads
        var multiThreadConfig = PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class);
        multiThreadConfig.setMoveThreadCount("2");
        multiThreadConfig.setRandomSeed(0L); // Same seed

        var phaseConfig2 = new LocalSearchPhaseConfig();
        phaseConfig2.setTerminationConfig(new TerminationConfig().withScoreCalculationCountLimit(50L));
        multiThreadConfig.setPhaseConfigList(Arrays.asList(
                multiThreadConfig.getPhaseConfigList().get(0),
                phaseConfig2));

        var solution2 = createTestSolution();
        solution2 = PlannerTestUtils.solve(multiThreadConfig, solution2);

        // Both should produce valid solutions
        assertThat(solution1.getScore()).isNotNull();
        assertThat(solution2.getScore()).isNotNull();

        // Verify all entities have values
        for (var entity : solution1.getEntityList()) {
            assertThat(entity.getValue()).isNotNull();
        }
        for (var entity : solution2.getEntityList()) {
            assertThat(entity.getValue()).isNotNull();
        }

        // The scores might differ due to different evaluation order,
        // but both should be reasonable solutions
        assertThat(solution1.getScore().score()).isLessThanOrEqualTo(0);
        assertThat(solution2.getScore().score()).isLessThanOrEqualTo(0);
    }

    @Test
    void singleThreadedFallbackWorks() {
        var solverConfig = PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class);
        solverConfig.setMoveThreadCount("1"); // Explicitly single-threaded

        var phaseConfig = new LocalSearchPhaseConfig();
        phaseConfig.setTerminationConfig(new TerminationConfig().withScoreCalculationCountLimit(50L));
        solverConfig.setPhaseConfigList(Arrays.asList(
                solverConfig.getPhaseConfigList().get(0),
                phaseConfig));

        var solution = createTestSolution();
        solution = PlannerTestUtils.solve(solverConfig, solution);

        assertThat(solution).isNotNull();
        assertThat(solution.getScore()).isNotNull();
        for (var entity : solution.getEntityList()) {
            assertThat(entity.getValue()).isNotNull();
        }
    }

    @Test
    void noThreadCountConfiguredUsesDefault() {
        var solverConfig = PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class);
        // Don't set moveThreadCount - should use default single-threaded behavior

        var phaseConfig = new LocalSearchPhaseConfig();
        phaseConfig.setTerminationConfig(new TerminationConfig().withScoreCalculationCountLimit(50L));
        solverConfig.setPhaseConfigList(Arrays.asList(
                solverConfig.getPhaseConfigList().get(0),
                phaseConfig));

        var solution = createTestSolution();
        solution = PlannerTestUtils.solve(solverConfig, solution);

        assertThat(solution).isNotNull();
        assertThat(solution.getScore()).isNotNull();
        for (var entity : solution.getEntityList()) {
            assertThat(entity.getValue()).isNotNull();
        }
    }

    private TestdataSolution createTestSolution() {
        var solution = new TestdataSolution("s1");
        var v1 = new TestdataValue("v1");
        var v2 = new TestdataValue("v2");
        var v3 = new TestdataValue("v3");
        solution.setValueList(Arrays.asList(v1, v2, v3));
        solution.setEntityList(Arrays.asList(
                new TestdataEntity("e1", v1),
                new TestdataEntity("e2", v2),
                new TestdataEntity("e3", v3)));
        return solution;
    }

    private TestdataSolution createUnassignedTestSolution() {
        var solution = new TestdataSolution("s1");
        var v1 = new TestdataValue("v1");
        var v2 = new TestdataValue("v2");
        var v3 = new TestdataValue("v3");
        solution.setValueList(Arrays.asList(v1, v2, v3));
        solution.setEntityList(Arrays.asList(
                new TestdataEntity("e1", null),
                new TestdataEntity("e2", null),
                new TestdataEntity("e3", null)));
        return solution;
    }
}
