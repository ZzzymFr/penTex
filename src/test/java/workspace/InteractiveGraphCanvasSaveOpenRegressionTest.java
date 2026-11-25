package workspace;

import entity.graph.GraphDefinition;
import interface_adapter.graph.GraphDefinitionCodec;
import org.junit.jupiter.api.Test;
import workspace.data_access.FileCanvasRepository;
import workspace.domain.CanvasState;
import workspace.domain.CanvasStateComparison;
import workspace.domain.SavedCanvas;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Canvas Save/Open preserves native graph source, viewport and camera without breaking old files. */
class InteractiveGraphCanvasSaveOpenRegressionTest {
    private static final byte[] IMAGE = {1, 2, 3, 4}; // repository stores bytes without decoding them

    private static GraphDefinition graph(double yaw) {
        return new GraphDefinition(List.of("z=x*y", "z=x*x+y*y"),
                new GraphDefinition.Range(-4, 4), new GraphDefinition.Range(-5, 5),
                new GraphDefinition.Range(-12, 12), new GraphDefinition.Camera(yaw, .37));
    }

    private static CanvasState savedState(GraphDefinition graph, byte[] thumbnail) {
        return new CanvasState(List.of(), List.of(), List.of(new CanvasState.Asset("", "", thumbnail,
                101.25, 47.5, 310, 190, true, GraphDefinitionCodec.encode(graph))), 1.6, 30, -50);
    }

    @Test void nativeGraphRoundTripsThroughV5AndPreservesAngleSizeAndFunctions() throws Exception {
        Path directory = Files.createTempDirectory("pentex-native-canvas-");
        var repo = new FileCanvasRepository(directory);
        String id = UUID.randomUUID().toString();
        var original = new SavedCanvas(id, "3D 图形", Instant.now(), savedState(graph(.83), IMAGE));
        repo.write(original);
        var reopened = new FileCanvasRepository(directory).find(id).orElseThrow().state();
        var asset = reopened.assets().get(0);
        assertEquals(graph(.83), GraphDefinitionCodec.decode(asset.graphDefinition()));
        assertEquals(101.25, asset.x());
        assertEquals(47.5, asset.y());
        assertEquals(310, asset.width());
        assertEquals(190, asset.height());
        assertArrayEquals(IMAGE, asset.png());
        assertEquals(1.6, reopened.zoom());
        assertEquals(-50, reopened.offsetY());
    }

    @Test void newCameraMarksCanvasEditedButDelayedThumbnailDoesNot() {
        var original = savedState(graph(.83), IMAGE);
        assertTrue(CanvasStateComparison.sameContent(original, savedState(graph(.83), new byte[]{9, 8, 7})));
        assertFalse(CanvasStateComparison.sameContent(original, savedState(graph(.97), IMAGE)));
        var changedSize = new CanvasState(List.of(), List.of(), List.of(new CanvasState.Asset("", "", IMAGE,
                101.25, 47.5, 330, 190, true, GraphDefinitionCodec.encode(graph(.83)))), 1.6, 30, -50);
        assertFalse(CanvasStateComparison.sameContent(original, changedSize));
    }

    @Test void oldMlc4DocumentStillOpensAsStaticGraph() throws Exception {
        Path directory = Files.createTempDirectory("pentex-legacy-canvas-");
        String id = UUID.randomUUID().toString();
        Path file = directory.resolve(id + ".mlcanvas");
        try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(file))) {
            out.writeInt(0x4d4c4334);  // legacy MLC4
            out.writeUTF(id); out.writeUTF("legacy"); out.writeLong(0);
            out.writeDouble(1); out.writeDouble(0); out.writeDouble(0);
            out.writeInt(0);  // strokes
            out.writeInt(0);  // formulas
            out.writeInt(1);  // graph assets
            out.writeUTF(""); out.writeUTF("");
            out.writeDouble(20); out.writeDouble(35); out.writeDouble(180); out.writeDouble(110);
            out.writeBoolean(true);
            out.writeInt(IMAGE.length); out.write(IMAGE);
        }
        var asset = new FileCanvasRepository(directory).find(id).orElseThrow().state().assets().get(0);
        assertTrue(asset.graph());
        assertEquals("", asset.graphDefinition());
        assertArrayEquals(IMAGE, asset.png());
    }
}
