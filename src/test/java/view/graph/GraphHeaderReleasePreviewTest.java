package view.graph;

import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The floating Graph card reports reversible hover and a single final release. */
class GraphHeaderReleasePreviewTest {
    private static final class Card extends PlotCard {
        @Override boolean hasContent() { return true; }
        @Override void paintCard(Graphics2D g, int hot) { }
        @Override void plotDragged(double dx, double dy) { }
        @Override void plotClicked(Point p) { }
        @Override void hover(Point p) { }
        @Override void hoverEnded() { }
        @Override void wheel(MouseWheelEvent e, double factor) { }
        @Override void resetView() { }
        @Override public BufferedImage snapshot(int w,int h) {return new BufferedImage(w,h,2);}
    }
    private static final class Host implements PlotCard.Host {
        final List<Point> visited = new ArrayList<>();
        int commits;
        boolean overPdf;
        @Override public void moveBy(double dx,double dy) { }
        @Override public void resizeTo(double w,double h) { }
        @Override public void close() { }
        @Override public void viewChanged() { }
        @Override public void dragMoved(Point screen) {
            visited.add(new Point(screen));
            overPdf = screen.x > 500;
        }
        @Override public void dragFinished(Point screen) {
            overPdf = false;
            if (screen != null && screen.x > 500) commits++;
        }
    }
    private static void event(Card card, int id, int sx, int sy) {
        int mask = id == MouseEvent.MOUSE_RELEASED ? 0 : MouseEvent.BUTTON1_DOWN_MASK;
        card.dispatchEvent(new MouseEvent(card, id, System.currentTimeMillis(), mask,
                50, 12, sx, sy, 1, false, MouseEvent.BUTTON1));
    }
    @Test void crossingPdfOnlyPreviewsUntilRelease() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Card card = new Card(); card.setSize(320,240); card.setVisible(true);
            Host host = new Host(); card.setHost(host);
            event(card, MouseEvent.MOUSE_PRESSED, 100,100);
            event(card, MouseEvent.MOUSE_DRAGGED, 600,110);
            assertTrue(host.overPdf);
            assertEquals(0, host.commits);
            event(card, MouseEvent.MOUSE_RELEASED, 600,110);
            assertEquals(1, host.commits);
            assertTrue(!host.overPdf);
            event(card, MouseEvent.MOUSE_RELEASED, 600,110);
            assertEquals(1, host.commits);
        });
    }
    @Test void returningToCanvasBeforeReleaseCancelsPdfPreview() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Card card = new Card(); card.setSize(320,240); card.setVisible(true);
            Host host = new Host(); card.setHost(host);
            event(card, MouseEvent.MOUSE_PRESSED, 100,100);
            event(card, MouseEvent.MOUSE_DRAGGED, 600,110);
            event(card, MouseEvent.MOUSE_DRAGGED, 220,110);
            assertTrue(!host.overPdf);
            event(card, MouseEvent.MOUSE_RELEASED, 220,110);
            assertEquals(0, host.commits);
        });
    }
}
