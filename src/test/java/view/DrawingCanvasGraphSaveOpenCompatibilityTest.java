package view;

import entity.graph.GraphDefinition;
import interface_adapter.graph.GraphDefinitionCodec;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Graph Save/Open distinguishes legacy picture tags from reconstructible graph sources. */
class DrawingCanvasGraphSaveOpenCompatibilityTest {
    private static final BufferedImage THUMBNAIL =
            new BufferedImage(160, 100, BufferedImage.TYPE_INT_ARGB);

    @Test
    void arbitraryLegacyTagsRemainStaticAfterSaveAndOpen() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            DrawingCanvas canvas = new DrawingCanvas();
            canvas.setSize(900, 500);
            canvas.showGraph(THUMBNAIL, new Rectangle(60, 60, 50, 30), "first");
            canvas.showGraph(THUMBNAIL, new Rectangle(60, 300, 50, 30), "second");
            var saved = canvas.exportCanvasState();
            assertEquals(2, saved.assets().size());
            assertTrue(saved.assets().stream().allMatch(a -> a.graphDefinition().isEmpty()),
                    "a UI card tag is not a serialized graph definition");

            DrawingCanvas reopened = new DrawingCanvas();
            reopened.setSize(900, 500);
            try { reopened.restoreCanvasState(saved); }
            catch (java.io.IOException e) { throw new AssertionError(e); }
            assertEquals(2, reopened.graphCount());
            assertNull(reopened.getGraphTag(), "legacy images have no live graph owner");
        });
    }

    @Test
    void savedInteractiveDefinitionSurvivesPlaceholderAndLiveReplacement() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            String encoded = GraphDefinitionCodec.encode(new GraphDefinition(List.of("z=x*y"),
                    new GraphDefinition.Range(-4, 4), new GraphDefinition.Range(-4, 4),
                    new GraphDefinition.Range(-16, 16), new GraphDefinition.Camera(.8, .35)));
            DrawingCanvas canvas = new DrawingCanvas();
            canvas.setSize(900, 500);
            canvas.showGraphAt(THUMBNAIL, new Point(120, 80), (CanvasGraphSource) () -> encoded);
            var saved = canvas.exportCanvasState();
            assertEquals(encoded, saved.assets().get(0).graphDefinition());

            DrawingCanvas reopened = new DrawingCanvas();
            reopened.setSize(900, 500);
            try { reopened.restoreCanvasState(saved); }
            catch (java.io.IOException e) { throw new AssertionError(e); }
            assertNull(reopened.getGraphTag(), "saved placeholder is not yet a live card");
            assertEquals(encoded, reopened.exportCanvasState().assets().get(0).graphDefinition());

            var asset = saved.assets().get(0);
            Rectangle2D bounds = new Rectangle2D.Double(asset.x(), asset.y(), asset.width(), asset.height());
            CanvasGraphSource liveOwner = () -> encoded;
            reopened.showSavedGraph(THUMBNAIL, bounds, encoded, liveOwner);
            assertSame(liveOwner, reopened.getGraphTag());
            assertEquals(1, reopened.graphCount(), "live card replaces the saved thumbnail");
            assertEquals(encoded, reopened.exportCanvasState().assets().get(0).graphDefinition());
        });
    }
}
