package view;

import entity.graph.GraphDefinition;
import interface_adapter.graph.GraphDefinitionCodec;
import org.junit.jupiter.api.Test;
import workspace.domain.CanvasState;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.Point;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** UI regression: Open keeps the old thumbnail until a live graph can take its exact slot. */
class InteractiveGraphCanvasPlaceholderRegressionTest {
    @Test void reopeningAndReplacingGraphKeepsSavedWorldBoundsAndLiveSource() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var canvas = new DrawingCanvas();
            canvas.setSize(900, 600);
            var original = GraphDefinitionCodec.encode(new GraphDefinition(List.of("y=x*x"),
                    new GraphDefinition.Range(-3, 3), new GraphDefinition.Range(0, 9), null));
            BufferedImage image = new BufferedImage(100, 80, BufferedImage.TYPE_INT_ARGB);
            canvas.showGraphAt(image, new Point(30, 65), (CanvasGraphSource) () -> original);
            var saved = canvas.exportCanvasState();
            assertEquals(original, saved.assets().get(0).graphDefinition());

            try { canvas.restoreCanvasState(saved); }
            catch (java.io.IOException e) { throw new AssertionError(e); }
            assertEquals(original, canvas.exportCanvasState().assets().get(0).graphDefinition());
            Object liveTag = (CanvasGraphSource) () -> original;
            var a = saved.assets().get(0);
            var bounds = new Rectangle2D.Double(a.x(), a.y(), a.width(), a.height());
            canvas.showSavedGraph(image, bounds, original, liveTag);
            var placed = canvas.getGraphCardOnScreen(liveTag);
            assertNotNull(placed, "restored card has an interactive tag");
            assertEquals(a.x(), placed.getX(), 1e-6);
            assertEquals(a.y(), placed.getY(), 1e-6);
            assertEquals(a.width(), placed.getWidth(), 1e-6);
            assertEquals(a.height(), placed.getHeight(), 1e-6);
            assertEquals(original, canvas.exportCanvasState().assets().get(0).graphDefinition());
            assertEquals(1, canvas.exportCanvasState().assets().size(), "placeholder is replaced, not duplicated");
        });
    }

    @Test void abortedGraphRestoreDoesNotReviveDeletedPlaceholder() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var canvas = new DrawingCanvas();
            canvas.setSize(900, 600);
            String encoded = GraphDefinitionCodec.encode(GraphDefinition.of("y=x"));
            var image = new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB);
            canvas.showSavedGraph(image, new Rectangle2D.Double(10, 20, 100, 50), encoded, new Object());
            assertTrue(canvas.exportCanvasState().assets().isEmpty());
        });
    }
}
