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

/** Cross-panel transfer must be checked during a header drag, before mouse release. */
class GraphCrossDividerDragRegressionTest {
    private static final class Card extends PlotCard {
        @Override boolean hasContent() { return true; }
        @Override void paintCard(Graphics2D g, int hot) { }
        @Override void plotDragged(double dx, double dy) { }
        @Override void plotClicked(Point p) { }
        @Override void hover(Point p) { }
        @Override void hoverEnded() { }
        @Override void wheel(MouseWheelEvent e, double factor) { }
        @Override void resetView() { }
        @Override public BufferedImage snapshot(int w, int h) { return new BufferedImage(w, h, 2); }
    }
    private static final class Host implements PlotCard.Host {
        final List<Point> through = new ArrayList<>();
        Point finished;
        @Override public void moveBy(double dx, double dy) { }
        @Override public void resizeTo(double w, double h) { }
        @Override public void close() { }
        @Override public void viewChanged() { }
        @Override public void dragMoved(Point screen) { through.add(new Point(screen)); }
        @Override public void dragFinished(Point screen) { finished = new Point(screen); }
    }
    private static void mouse(Card card, int id, int x, int y, int screenX, int screenY) {
        int mask = id == MouseEvent.MOUSE_RELEASED ? 0 : MouseEvent.BUTTON1_DOWN_MASK;
        card.dispatchEvent(new MouseEvent(card, id, System.currentTimeMillis(), mask,
                x, y, screenX, screenY, 1, false, MouseEvent.BUTTON1));
    }
    @Test void headerDragNotifiesDropTargetBeforeRelease() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Card card = new Card(); card.setSize(320, 240); card.setVisible(true);
            Host host = new Host(); card.setHost(host);
            mouse(card, MouseEvent.MOUSE_PRESSED, 60, 12, 90, 110);
            mouse(card, MouseEvent.MOUSE_DRAGGED, 180, 18, 410, 124);
            assertEquals(List.of(new Point(410, 124)), host.through);
            assertEquals(null, host.finished);
            mouse(card, MouseEvent.MOUSE_RELEASED, 180, 18, 410, 124);
            assertEquals(new Point(410, 124), host.finished);
        });
    }
    @Test void plottingInsideGraphDoesNotTriggerWorkspaceHandoff() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Card card = new Card(); card.setSize(320, 240); card.setVisible(true);
            Host host = new Host(); card.setHost(host);
            mouse(card, MouseEvent.MOUSE_PRESSED, 100, 120, 160, 220);
            mouse(card, MouseEvent.MOUSE_DRAGGED, 150, 128, 210, 228);
            assertTrue(host.through.isEmpty());
        });
    }
}
