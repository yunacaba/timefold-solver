package ai.timefold.solver.core.extension;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.heuristic.selector.common.SelectionCacheType;
import ai.timefold.solver.core.config.heuristic.selector.common.SelectionOrder;
import ai.timefold.solver.core.config.heuristic.selector.common.nearby.NearbySelectionConfig;
import ai.timefold.solver.core.config.heuristic.selector.entity.EntitySelectorConfig;
import ai.timefold.solver.core.config.heuristic.selector.list.DestinationSelectorConfig;
import ai.timefold.solver.core.config.heuristic.selector.list.SubListSelectorConfig;
import ai.timefold.solver.core.config.heuristic.selector.value.ValueSelectorConfig;
import ai.timefold.solver.core.config.partitionedsearch.PartitionedSearchPhaseConfig;
import ai.timefold.solver.core.config.solver.EnvironmentMode;
import ai.timefold.solver.core.impl.constructionheuristic.decider.ConstructionHeuristicDecider;
import ai.timefold.solver.core.impl.constructionheuristic.decider.forager.ConstructionHeuristicForager;
import ai.timefold.solver.core.impl.domain.entity.descriptor.EntityDescriptor;
import ai.timefold.solver.core.impl.domain.variable.declarative.TopologicalOrderGraph;
import ai.timefold.solver.core.impl.heuristic.HeuristicConfigPolicy;
import ai.timefold.solver.core.impl.heuristic.selector.entity.EntitySelector;
import ai.timefold.solver.core.impl.heuristic.selector.list.DestinationSelector;
import ai.timefold.solver.core.impl.heuristic.selector.list.ElementDestinationSelector;
import ai.timefold.solver.core.impl.heuristic.selector.list.RandomSubListSelector;
import ai.timefold.solver.core.impl.heuristic.selector.list.SubListSelector;
import ai.timefold.solver.core.impl.heuristic.selector.value.ValueSelector;
import ai.timefold.solver.core.impl.localsearch.decider.LocalSearchDecider;
import ai.timefold.solver.core.impl.localsearch.decider.acceptor.Acceptor;
import ai.timefold.solver.core.impl.localsearch.decider.forager.LocalSearchForager;
import ai.timefold.solver.core.impl.move.MoveRepository;
import ai.timefold.solver.core.impl.partitionedsearch.PartitionedSearchPhase;
import ai.timefold.solver.core.impl.solver.termination.PhaseTermination;
import ai.timefold.solver.core.impl.solver.termination.SolverTermination;

public interface TimefoldSolverExtensionService {

    String SOLVER_NAME = "Timefold Solver";
    String COMMUNITY_NAME = "Community Edition";
    String COMMUNITY_COORDINATES = "ai.timefold.solver:timefold-solver-core";
    String DEVELOPMENT_SNAPSHOT = "Development Snapshot";

    static String identifySolverVersion() {
        var version = getVersionString(SolverFactory.class);
        return COMMUNITY_NAME + " " + version;
    }

    private static String getVersionString(Class<?> clz) {
        var version = clz.getPackage().getImplementationVersion();
        return (version == null ? DEVELOPMENT_SNAPSHOT : "v" + version);
    }

    static TimefoldSolverExtensionService load() {
        return new DefaultTimefoldSolverExtensionService();
    }

    static <T> T buildOrDefault(Function<TimefoldSolverExtensionService, T> builder, Supplier<T> defaultValue) {
        try {
            var service = load();
            return builder.apply(service);
        } catch (Exception e) {
            return defaultValue.get();
        }
    }

    TopologicalOrderGraph buildTopologyGraph(int size);

    Class<? extends ConstraintProvider>
            buildLambdaSharedConstraintProvider(Class<? extends ConstraintProvider> originalConstraintProvider);

    <Solution_> ConstructionHeuristicDecider<Solution_> buildConstructionHeuristic(PhaseTermination<Solution_> termination,
            ConstructionHeuristicForager<Solution_> forager, HeuristicConfigPolicy<Solution_> configPolicy);

    <Solution_> LocalSearchDecider<Solution_> buildLocalSearch(int moveThreadCount, PhaseTermination<Solution_> termination,
            MoveRepository<Solution_> moveRepository, Acceptor<Solution_> acceptor, LocalSearchForager<Solution_> forager,
            EnvironmentMode environmentMode, HeuristicConfigPolicy<Solution_> configPolicy);

    <Solution_> PartitionedSearchPhase<Solution_> buildPartitionedSearch(int phaseIndex,
            PartitionedSearchPhaseConfig phaseConfig, HeuristicConfigPolicy<Solution_> solverConfigPolicy,
            SolverTermination<Solution_> solverTermination,
            BiFunction<HeuristicConfigPolicy<Solution_>, SolverTermination<Solution_>, PhaseTermination<Solution_>> phaseTerminationFunction);

    <Solution_> EntitySelector<Solution_> applyNearbySelection(EntitySelectorConfig entitySelectorConfig,
            HeuristicConfigPolicy<Solution_> configPolicy, NearbySelectionConfig nearbySelectionConfig,
            SelectionCacheType minimumCacheType, SelectionOrder resolvedSelectionOrder,
            EntitySelector<Solution_> entitySelector);

    <Solution_> ValueSelector<Solution_> applyNearbySelection(ValueSelectorConfig valueSelectorConfig,
            HeuristicConfigPolicy<Solution_> configPolicy, EntityDescriptor<Solution_> entityDescriptor,
            SelectionCacheType minimumCacheType, SelectionOrder resolvedSelectionOrder, ValueSelector<Solution_> valueSelector);

    <Solution_> SubListSelector<Solution_> applyNearbySelection(SubListSelectorConfig subListSelectorConfig,
            HeuristicConfigPolicy<Solution_> configPolicy, SelectionCacheType minimumCacheType,
            SelectionOrder resolvedSelectionOrder, RandomSubListSelector<Solution_> subListSelector);

    <Solution_> DestinationSelector<Solution_> applyNearbySelection(DestinationSelectorConfig destinationSelectorConfig,
            HeuristicConfigPolicy<Solution_> configPolicy, SelectionCacheType minimumCacheType,
            SelectionOrder resolvedSelectionOrder, ElementDestinationSelector<Solution_> destinationSelector);

}