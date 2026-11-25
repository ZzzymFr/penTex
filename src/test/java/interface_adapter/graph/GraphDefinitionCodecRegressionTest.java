package interface_adapter.graph;

import entity.graph.GraphDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** A thumbnail is never sufficient to reconstruct a native interactive plot. */
class GraphDefinitionCodecRegressionTest {
    @Test void roundTripsMultipleFunctionsUnicodeAxisRangesAndCamera() {
        GraphDefinition original = new GraphDefinition(
                List.of("y=\\sin(x)", "z=x^2+y^2", "中文 + π"),
                new GraphDefinition.Range(-12.5, 8), new GraphDefinition.Range(-4, 20),
                new GraphDefinition.Range(-2, 9), new GraphDefinition.Camera(-0.47, 1.2));
        assertEquals(original, GraphDefinitionCodec.decode(GraphDefinitionCodec.encode(original)));
    }

    @Test void decodesOlderGraphDefinitionsWithoutCamera() {
        GraphDefinition restored = GraphDefinitionCodec.decode("graph-v1;auto;auto;auto;eT14");
        assertEquals(List.of("y=x"), restored.functions());
        assertEquals(null, restored.camera());
    }

    @Test void refusesMalformedInputRatherThanInventingAStaticGraphSource() {
        assertThrows(IllegalArgumentException.class, () -> GraphDefinitionCodec.decode("PNG"));
        assertThrows(IllegalArgumentException.class, () ->
                GraphDefinitionCodec.decode("graph-v2;auto;auto;auto;0,0;"));
    }
}
