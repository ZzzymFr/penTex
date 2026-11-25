package view.board;

import interface_adapter.history.HistoryItem;
import interface_adapter.history.HistoryViewModel;
import org.junit.jupiter.api.Test;
import view.DrawingCanvas;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

/** Cross-divider feedback must be visible even before the PDF paper is under the pointer. */
class GraphPdfPreviewVisibilityRegressionTest {
    @Test void previewStaysAtCursorWhenPageOriginIsFarAway() {
        Rectangle pdf = new Rectangle(450, 0, 600, 600);
        Rectangle paper = new Rectangle(760, 170, 130, 90);
        Point pointer = new Point(470, 220); // cursor has just crossed the divider
        Rectangle preview = BoardWindow.visibleInteractiveGraphPreview(paper, pointer, pdf);
        assertTrue(pdf.contains(preview));
        assertTrue(Math.abs(preview.x - pointer.x) < 30);
        assertTrue(Math.abs(preview.y - pointer.y) < 30);
        assertEquals(130, preview.width);
        assertEquals(90, preview.height);
    }

    @Test void previewMatchesPageWhenPointerIsOverPaper() {
        Rectangle pdf = new Rectangle(450, 0, 600, 600);
        Rectangle paper = new Rectangle(640, 245, 130, 90);
        assertEquals(paper, BoardWindow.visibleInteractiveGraphPreview(
                paper, new Point(644, 248), pdf));
    }

    @Test void smallPdfPanelClipsPreviewWithoutDisappearing() {
        Rectangle pdf = new Rectangle(700, 0, 125, 160);
        Rectangle page = new Rectangle(930, 90, 180, 120);
        Rectangle preview = BoardWindow.visibleInteractiveGraphPreview(page,
                new Point(715, 120), pdf);
        assertTrue(pdf.contains(preview));
        assertTrue(preview.width > 0 && preview.height > 0);
    }

    @Test void pdfWithoutLaidOutPaperStillHasVisibleOutline() throws Exception {
        if (GraphicsEnvironment.isHeadless()) return;
        SwingUtilities.invokeAndWait(() -> {
            JFrame frame = new JFrame();
            try {
                class Destination extends JPanel implements PdfWorkspacePort {
                    @Override public void fitWhenOpened() { }
                    @Override public void addHistoryItem(HistoryItem item) { }
                    @Override public boolean dropHistoryItem(HistoryItem item, Point cursor) { return false; }
                    @Override public Rectangle interactiveGraphPreviewBounds(Point point, double ratio) {
                        return null; // paper is not ready yet
                    }
                }
                Destination pdf = new Destination();
                BoardWindow board = new BoardWindow(new JPanel(), new DrawingCanvas(),
                        host -> new HistoryPanel(new HistoryViewModel(), host), pdf);
                frame.setContentPane(board);
                frame.setSize(1050, 650);
                frame.setLocation(20, 20);
                frame.setVisible(true);
                board.setPdfOpen(true);
                board.doLayout();
                Point position = new Point(pdf.getX() + 12, 220);
                SwingUtilities.convertPointToScreen(position, board);
                board.previewInteractiveGraphDrop(position, 1.5);
                JComponent ghost = (JComponent) board.getComponent(0);
                assertTrue(painted(ghost));
                board.cancelInteractiveGraphPreview();
                assertTrue(!painted(ghost));
            } finally {
                frame.dispose();
            }
        });
    }

    private static boolean painted(JComponent view) {
        BufferedImage pixels = new BufferedImage(Math.max(1, view.getWidth()),
                Math.max(1, view.getHeight()), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = pixels.createGraphics();
        view.paint(graphics);
        graphics.dispose();
        for (int y = 0; y < pixels.getHeight(); y += 3)
            for (int x = 0; x < pixels.getWidth(); x += 3)
                if ((pixels.getRGB(x, y) >>> 24) != 0) return true;
        return false;
    }
}
