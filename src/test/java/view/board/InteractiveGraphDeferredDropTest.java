package view.board;

import entity.graph.GraphDefinition;
import interface_adapter.history.HistoryItem;
import interface_adapter.history.HistoryViewModel;
import org.junit.jupiter.api.Test;
import view.DrawingCanvas;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Hovering over PDF must never mutate History, Canvas or Library. Only release may copy. */
class InteractiveGraphDeferredDropTest {
    private static final class Destination extends JPanel implements PdfWorkspacePort {
        int committed;
        @Override public void fitWhenOpened() { }
        @Override public void addHistoryItem(HistoryItem item) { }
        @Override public boolean dropHistoryItem(HistoryItem item, Point screen) { return false; }
        @Override public boolean dropInteractiveGraph(GraphDefinition definition, BufferedImage image, Point screen) {
            committed++;
            return true;
        }
        @Override public Rectangle interactiveGraphPreviewBounds(Point screen, double ratio) {
            assertEquals(1.5f, (float) ratio, 0.01f);
            return new Rectangle(screen.x, screen.y, 140, 90);
        }
    }
    private static boolean painted(JComponent ghost) {
        var screenshot = new BufferedImage(Math.max(1, ghost.getWidth()),
                Math.max(1, ghost.getHeight()), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = screenshot.createGraphics();
        ghost.paint(g);
        g.dispose();
        for (int y = 0; y < screenshot.getHeight(); y += 2)
            for (int x = 0; x < screenshot.getWidth(); x += 2)
                if ((screenshot.getRGB(x, y) >>> 24) != 0) return true;
        return false;
    }
    @Test void movingAcrossDividerOnlyShowsPreviewAndMovingBackClearsIt() throws Exception {
        if (java.awt.GraphicsEnvironment.isHeadless()) return;
        AtomicReference<Throwable> failed = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            JFrame frame = new JFrame();
            try {
                Destination pdf = new Destination();
                BoardWindow board = new BoardWindow(new JPanel(), new DrawingCanvas(),
                        host -> new HistoryPanel(new HistoryViewModel(), host), pdf);
                frame.setContentPane(board);
                frame.setSize(1120, 700);
                frame.setLocation(30, 30);
                frame.setVisible(true);
                board.setPdfOpen(true);
                board.doLayout();
                Point inside = new Point(pdf.getX() + Math.min(120, pdf.getWidth()/2), 240);
                Point outside = new Point(Math.max(0, pdf.getX()-120), 240);
                SwingUtilities.convertPointToScreen(inside, board);
                SwingUtilities.convertPointToScreen(outside, board);
                JComponent ghost = (JComponent) board.getComponent(0);
                board.previewInteractiveGraphDrop(inside, 1.5);
                assertTrue(painted(ghost));
                assertEquals(0, pdf.committed);
                // The same gesture may return to Canvas before mouse-up.
                board.previewInteractiveGraphDrop(outside, 1.5);
                assertTrue(!painted(ghost));
                assertEquals(0, pdf.committed);
                board.previewInteractiveGraphDrop(inside, 1.5);
                board.cancelInteractiveGraphPreview();
                assertTrue(!painted(ghost));
                var graph = GraphDefinition.of("y=x");
                var image = new BufferedImage(880, 600, BufferedImage.TYPE_INT_ARGB);
                assertTrue(!board.dropInteractiveGraph(graph, image, outside));
                assertEquals(0, pdf.committed);
                // The sole real mutation happens after the release inside PDF.
                assertTrue(board.dropInteractiveGraph(graph, image, inside));
                assertEquals(1, pdf.committed);
            } catch (Throwable t) { failed.set(t); }
            finally { frame.dispose(); }
        });
        if (failed.get() != null) throw new AssertionError("Deferred PDF Graph copy", failed.get());
    }
}
