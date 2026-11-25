package interface_adapter.graph;

import entity.graph.GraphDefinition;
import java.util.List;
import java.util.Objects;

/** Translate the live graph view state into immutable, portable graph data. */
public final class GraphDefinitionMapper {
    private GraphDefinitionMapper() { }
    public static GraphDefinition from(GraphState state) {
        Objects.requireNonNull(state, "state");
        if (!state.hasGraph()) throw new IllegalArgumentException("No graph to preserve");
        List<String> functions = state.getFunctions().isEmpty()
                ? List.of(state.getLatex()) : state.getFunctions();
        GraphDefinition.Range z = state.getKind() == GraphState.Kind.SURFACE
                ? new GraphDefinition.Range(state.getZMin(), state.getZMax()) : null;
        return new GraphDefinition(functions,
                new GraphDefinition.Range(state.getXMin(), state.getXMax()),
                new GraphDefinition.Range(state.getYMin(), state.getYMax()), z);
    }
}
