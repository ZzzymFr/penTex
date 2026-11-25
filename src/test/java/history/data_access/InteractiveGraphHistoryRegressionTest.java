package history.data_access;

import entity.graph.GraphDefinition;
import history.domain.HistoryEntry;
import history.use_case.HistoryInteractor;
import interface_adapter.graph.GraphDefinitionCodec;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** v1 PNG-only entries survive while v2 entries reconstruct a graph from their source. */
class InteractiveGraphHistoryRegressionTest {
    private static byte[] png() throws Exception {
        BufferedImage image = new BufferedImage(20, 12, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    @Test void nativeGraphStateSurvivesRestartWithoutChangingOldHistory() throws Exception {
        var dir = Files.createTempDirectory("pentex-native-graph-history-");
        GraphDefinition definition = new GraphDefinition(List.of("y=x^2", "y=2x"),
                new GraphDefinition.Range(-5, 5), new GraphDefinition.Range(-7, 7), null,
                new GraphDefinition.Camera(0.8, 0.3));
        String encoded = GraphDefinitionCodec.encode(definition);
        HistoryInteractor beforeRestart = new HistoryInteractor(new FileHistoryRepository(dir));
        assertTrue(beforeRestart.recordGraph("y=x^2", png(), 20, 12, encoded));
        assertTrue(beforeRestart.recordGraph("legacy screenshot", png(), 20, 12));
        List<HistoryEntry> entries = new HistoryInteractor(new FileHistoryRepository(dir)).list();
        assertEquals(2, entries.size());
        var modern = entries.stream().filter(e -> e.graphDefinition() != null).findFirst().orElseThrow();
        assertEquals(definition, GraphDefinitionCodec.decode(modern.graphDefinition()));
        var legacy = entries.stream().filter(e -> e.graphDefinition() == null).findFirst().orElseThrow();
        assertEquals("legacy screenshot", legacy.formula());
    }
}
