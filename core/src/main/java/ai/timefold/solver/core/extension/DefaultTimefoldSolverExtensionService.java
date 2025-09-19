package ai.timefold.solver.core.extension;

import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
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
import ai.timefold.solver.core.impl.domain.variable.declarative.DefaultTopologicalOrderGraph;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.BiFunction;

public class DefaultTimefoldSolverExtensionService implements TimefoldSolverExtensionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultTimefoldSolverExtensionService.class);

    @Override
    public TopologicalOrderGraph buildTopologyGraph(int size) {
        return new DefaultTopologicalOrderGraph(size);
    }

    @Override
    public Class<? extends ConstraintProvider> buildLambdaSharedConstraintProvider(
            Class<? extends ConstraintProvider> originalConstraintProvider) {
        // No optimization available in community edition - return original provider
        return originalConstraintProvider;
    }

    @Override
    public <Solution_> ConstructionHeuristicDecider<Solution_> buildConstructionHeuristic(
            PhaseTermination<Solution_> termination,
            ConstructionHeuristicForager<Solution_> forager,
            HeuristicConfigPolicy<Solution_> configPolicy) {
        // Stub implementation: ignore multi-threading and return single-threaded decider
        LOGGER.debug("Multi-threaded construction heuristic requested but using single-threaded implementation in Community Edition");
        return new ConstructionHeuristicDecider<>(configPolicy.getLogIndentation(), termination, forager);
    }

    @Override
    public <Solution_> LocalSearchDecider<Solution_> buildLocalSearch(int moveThreadCount,
            PhaseTermination<Solution_> termination,
            MoveRepository<Solution_> moveRepository,
            Acceptor<Solution_> acceptor,
            LocalSearchForager<Solution_> forager,
            EnvironmentMode environmentMode,
            HeuristicConfigPolicy<Solution_> configPolicy) {
        // Stub implementation: ignore moveThreadCount and return single-threaded decider
        if (moveThreadCount > 1) {
            LOGGER.debug("Multi-threaded local search with {} threads requested but using single-threaded implementation in Community Edition", moveThreadCount);
        }
        return new LocalSearchDecider<>(configPolicy.getLogIndentation(), termination, moveRepository, acceptor, forager);
    }

    @Override
    public <Solution_> PartitionedSearchPhase<Solution_> buildPartitionedSearch(int phaseIndex,
            PartitionedSearchPhaseConfig phaseConfig,
            HeuristicConfigPolicy<Solution_> solverConfigPolicy,
            SolverTermination<Solution_> solverTermination,
            BiFunction<HeuristicConfigPolicy<Solution_>, SolverTermination<Solution_>, PhaseTermination<Solution_>> phaseTerminationFunction) {
        // TODO: Implement stub partitioned search that creates a single partition
        throw new UnsupportedOperationException(
                "Partitioned search is not yet implemented in Community Edition. " +
                "This is a stub implementation that will be enhanced in future versions.");
    }

    @Override
    public <Solution_> EntitySelector<Solution_> applyNearbySelection(EntitySelectorConfig entitySelectorConfig,
            HeuristicConfigPolicy<Solution_> configPolicy,
            NearbySelectionConfig nearbySelectionConfig,
            SelectionCacheType minimumCacheType,
            SelectionOrder resolvedSelectionOrder,
            EntitySelector<Solution_> entitySelector) {
        // Stub implementation: ignore nearby selection and return original selector
        LOGGER.debug("Nearby selection requested but using standard selection in Community Edition");
        return entitySelector;
    }

    @Override
    public <Solution_> ValueSelector<Solution_> applyNearbySelection(ValueSelectorConfig valueSelectorConfig,
            HeuristicConfigPolicy<Solution_> configPolicy,
            EntityDescriptor<Solution_> entityDescriptor,
            SelectionCacheType minimumCacheType,
            SelectionOrder resolvedSelectionOrder,
            ValueSelector<Solution_> valueSelector) {
        // Stub implementation: ignore nearby selection and return original selector
        LOGGER.debug("Nearby selection requested but using standard selection in Community Edition");
        return valueSelector;
    }

    @Override
    public <Solution_> SubListSelector<Solution_> applyNearbySelection(SubListSelectorConfig subListSelectorConfig,
            HeuristicConfigPolicy<Solution_> configPolicy,
            SelectionCacheType minimumCacheType,
            SelectionOrder resolvedSelectionOrder,
            RandomSubListSelector<Solution_> subListSelector) {
        // Stub implementation: ignore nearby selection and return original selector
        LOGGER.debug("Nearby selection requested but using standard selection in Community Edition");
        return subListSelector;
    }

    @Override
    public <Solution_> DestinationSelector<Solution_> applyNearbySelection(DestinationSelectorConfig destinationSelectorConfig,
            HeuristicConfigPolicy<Solution_> configPolicy,
            SelectionCacheType minimumCacheType,
            SelectionOrder resolvedSelectionOrder,
            ElementDestinationSelector<Solution_> destinationSelector) {
        // Stub implementation: ignore nearby selection and return original selector
        LOGGER.debug("Nearby selection requested but using standard selection in Community Edition");
        return destinationSelector;
    }
}