package view.board;

import history.domain.HistoryEntry;
import interface_adapter.history.HistoryItem;
import interface_adapter.history.HistoryViewModel;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.List;

/** Exercises real component event routing without opening a window or moving the OS mouse. */
final class HistoryDragReleaseSmoke {
    private HistoryDragReleaseSmoke() { }

    private static final class Gesture implements HistoryPanel.DragHost {
        final JComponent row;
        int drops;
        int clears;
        Point cursor;
        Point preview;

        Gesture() {
            HistoryViewModel model = new HistoryViewModel();
            model.setItems(List.of(new HistoryItem(new HistoryEntry("h1", HistoryEntry.Kind.CALCULATION,
                    "Calculation", "2+3", "5", null, 0, 0, Instant.EPOCH))));
            HistoryPanel panel = new HistoryPanel(model, this);
            JPanel list = (JPanel) ((JScrollPane) panel.getComponent(1)).getViewport().getView();
            row = (JComponent) list.getComponent(0);
            row.setSize(300, HistoryPanel.ROW_H);
        }

        @Override public void dragging(BufferedImage image, Point p) { }
        @Override public boolean dropped(BufferedImage image, Point p) { return false; }
        @Override public boolean droppedAt(HistoryItem item, BufferedImage image, Point start, Point at) {
            drops++;
            cursor = at;
            preview = start;
            return true;
        }
        @Override public void cancelDrag() { clears++; }

        void start() {
            dispatch(row, MouseEvent.MOUSE_PRESSED, 75, 24, 275, 124);
            dispatch(row, MouseEvent.MOUSE_DRAGGED, 84, 28, 284, 128);
        }
        void release(JComponent source, int x, int y) {
            dispatch(source, MouseEvent.MOUSE_RELEASED, 310, 80, x, y);
        }
        void assertDrop(int count, Point expected) {
            check(drops == count, "Expected " + count + " drops, got " + drops);
            check(expected.equals(cursor), "Actual pointer must be used: " + cursor);
            check(preview != null && !preview.equals(cursor), "Preview top-left must be independent");
            check(clears == count, "Release cleanup must occur once per completed gesture: " + clears);
        }
    }

    private static void dispatch(JComponent source, int id, int x, int y, int screenX, int screenY) {
        source.dispatchEvent(new MouseEvent(source, id, System.currentTimeMillis(), 0,
                x, y, screenX, screenY, 1, false, MouseEvent.BUTTON1));
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    static void verify() throws Exception {
        Gesture[] direct = new Gesture[1];
        SwingUtilities.invokeAndWait(() -> {
            direct[0] = new Gesture();
            direct[0].start();
            direct[0].release(direct[0].row, 510, 180);
            direct[0].assertDrop(1, new Point(510, 180));
        });
        // Drain any deferred AWT callback; same-row release must not clear twice.
        SwingUtilities.invokeAndWait(() -> direct[0].assertDrop(1, new Point(510, 180)));

        Gesture[] external = new Gesture[1];
        SwingUtilities.invokeAndWait(() -> {
            external[0] = new Gesture();
            external[0].start();
            external[0].release(new JPanel(), 610, 280);
        });
        SwingUtilities.invokeAndWait(() -> external[0].assertDrop(1, new Point(610, 280)));

        // A queued external release from an old gesture must not finish a newer one.
        Gesture[] reused = new Gesture[1];
        SwingUtilities.invokeAndWait(() -> {
            reused[0] = new Gesture();
            reused[0].start();
            reused[0].release(new JPanel(), 610, 280);
            reused[0].release(reused[0].row, 510, 180);
            reused[0].start();
        });
        SwingUtilities.invokeAndWait(() -> {
            reused[0].assertDrop(1, new Point(510, 180));
            reused[0].release(reused[0].row, 710, 380);
            reused[0].assertDrop(2, new Point(710, 380));
        });
        SwingUtilities.invokeAndWait(() -> reused[0].assertDrop(2, new Point(710, 380)));
    }

    public static void main(String[] args) throws Exception {
        verify();
        System.out.println("History drag release regression passed");
    }
}
