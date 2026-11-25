package view.board;

import interface_adapter.history.HistoryItem;
import interface_adapter.history.HistoryViewModel;
import org.junit.jupiter.api.Test;
import pdf_editor.view.GraphPlacementSizing;
import view.DrawingCanvas;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Native-graph drop feedback uses actual scaled pixels, not an empty oversized rectangle. */
class InteractiveGraphThumbnailPreviewRegressionTest {
    @Test void sizingPolicyMatchesThumbnailAndPlacementIncludingTallImages() {
        var normal = GraphPlacementSizing.forImage(880, 600);
        var sameRatio = GraphPlacementSizing.forAspect(880d / 600);
        assertEquals(normal.width(), sameRatio.width(), .001f);
        assertEquals(normal.height(), sameRatio.height(), .001f);
        assertEquals(280f, normal.width(), .001f);
        assertTrue(normal.height() > 65 && normal.height() < 600);

        var tall = GraphPlacementSizing.forImage(50, 800);
        assertEquals(600f, tall.height(), .001f);
        assertTrue(tall.width() < 280);
    }

    @Test void fallbackKeepsExactPlacementDimensionsInsteadOfShrinkingThumbnail() {
        Rectangle paper = new Rectangle(950, 180, 360, 225);
        Rectangle preview = BoardWindow.sameSizeFallbackPreview(paper,
                new Point(640, 220), new Rectangle(625, 0, 250, 650));
        assertEquals(paper.width, preview.width);
        assertEquals(paper.height, preview.height);
    }

    @Test void previewPaintsTranslucentThumbnailThenClearsWhenDragIsCanceled() throws Exception {
        if (GraphicsEnvironment.isHeadless()) return;
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            JFrame window = new JFrame();
            try {
                class PdfTarget extends JPanel implements PdfWorkspacePort {
                    @Override public void fitWhenOpened() { }
                    @Override public void addHistoryItem(HistoryItem item) { }
                    @Override public boolean dropHistoryItem(HistoryItem item, Point location) { return false; }
                    @Override public Rectangle interactiveGraphPreviewBounds(Point screen, double ratio) {
                        return new Rectangle(screen.x - 15, screen.y - 15, 110, 78);
                    }
                }
                PdfTarget pdf = new PdfTarget();
                BoardWindow board = new BoardWindow(new JPanel(), new DrawingCanvas(),
                        host -> new HistoryPanel(new HistoryViewModel(), host), pdf);
                window.setContentPane(board);
                window.setSize(1050, 640);
                window.setLocation(20, 20);
                window.setVisible(true);
                board.setPdfOpen(true);
                board.doLayout();
                BufferedImage red = new BufferedImage(220, 156, BufferedImage.TYPE_INT_RGB);
                Graphics2D brush = red.createGraphics();
                brush.setColor(Color.RED);
                brush.fillRect(0, 0, red.getWidth(), red.getHeight());
                brush.dispose();

                Point pointer = new Point(pdf.getX() + pdf.getWidth() / 2, 220);
                SwingUtilities.convertPointToScreen(pointer, board);
                board.previewInteractiveGraphDrop(pointer, 220.0 / 156.0, red);
                JComponent ghost = (JComponent) board.getComponent(0);
                BufferedImage frame = render(ghost);
                Point inside = new Point(pointer);
                SwingUtilities.convertPointFromScreen(inside, board);
                int argb = frame.getRGB(inside.x + 10, inside.y + 10);
                int alpha = argb >>> 24;
                assertTrue(alpha >= 100 && alpha <= 190);
                assertTrue(((argb >> 16) & 255) > (argb & 255));

                board.cancelInteractiveGraphPreview();
                int gone = render(ghost).getRGB(inside.x + 10, inside.y + 10);
                assertEquals(0, gone >>> 24);
            } catch (Throwable unexpected) {
                failure.set(unexpected);
            } finally {
                window.dispose();
            }
        });
        if (failure.get() != null) throw new AssertionError("Graph preview rendering", failure.get());
    }

    private static BufferedImage render(JComponent ghost) {
        BufferedImage image = new BufferedImage(ghost.getWidth(), ghost.getHeight(),
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D target = image.createGraphics();
        ghost.paint(target);
        target.dispose();
        return image;
    }
}
