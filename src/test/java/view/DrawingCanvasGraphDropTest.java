package view;

import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// lassoed ink dragged onto a graph card: the card hears about it, the ink stays where it was
class DrawingCanvasGraphDropTest {
    private static void fire(DrawingCanvas canvas, int id, int x, int y) {
        int button = id == MouseEvent.MOUSE_DRAGGED ? MouseEvent.NOBUTTON : MouseEvent.BUTTON1;
        MouseEvent e = new MouseEvent(canvas, id, System.currentTimeMillis(), 0, x, y, 1, false, button);
        if (id == MouseEvent.MOUSE_DRAGGED) {
            for (var h : canvas.getMouseMotionListeners()) {
                h.mouseDragged(e);
            }
        } else if (id == MouseEvent.MOUSE_PRESSED) {
            for (var h : canvas.getMouseListeners()) {
                h.mousePressed(e);
            }
        } else {
            for (var h : canvas.getMouseListeners()) {
                h.mouseReleased(e);
            }
        }
    }

    private final DrawingCanvas canvas = new DrawingCanvas();
    private final Object tag = new Object();
    private final List<Object> dropped = new ArrayList<>();
    private final List<Object> hovered = new ArrayList<>();

    private int firstX() {
        return canvas.getStrokes().get(0).getPoints().get(0).getX();
    }

    private void setUp() {
        canvas.setSize(900, 600);
        fire(canvas, MouseEvent.MOUSE_PRESSED, 60, 60);   // a stroke of ink
        fire(canvas, MouseEvent.MOUSE_DRAGGED, 90, 80);
        fire(canvas, MouseEvent.MOUSE_RELEASED, 90, 80);
        canvas.showGraph(new BufferedImage(440, 300, BufferedImage.TYPE_INT_ARGB), new Rectangle(40, 300, 60, 30), tag);
        canvas.setTool(DrawingCanvas.Tool.RECTANGLE);
        fire(canvas, MouseEvent.MOUSE_PRESSED, 40, 40);   // box around the ink
        fire(canvas, MouseEvent.MOUSE_DRAGGED, 110, 100);
        fire(canvas, MouseEvent.MOUSE_RELEASED, 110, 100);
        canvas.addGraphDropListener(dropped::add);
        canvas.addGraphDropHoverListener(hovered::add);
    }

    @Test
    void lettingGoOnACardDropsTheFormulaThereAndLeavesTheInk() {
        setUp();
        Rectangle2D card = canvas.getGraphCardOnScreen(tag);
        assertNotNull(card);
        int before = firstX();
        int cx = (int) card.getCenterX();
        int cy = (int) card.getCenterY();
        fire(canvas, MouseEvent.MOUSE_PRESSED, 75, 70);
        fire(canvas, MouseEvent.MOUSE_DRAGGED, cx, cy);
        assertEquals(List.of(tag), hovered, "the card lights up while the ink is over it");
        fire(canvas, MouseEvent.MOUSE_RELEASED, cx, cy);
        assertEquals(List.of(tag), dropped);
        assertEquals(tag, hovered.get(0));
        assertNull(hovered.get(hovered.size() - 1), "lit up no more after the drop");
        assertEquals(before, firstX(), "ink stays where it was written");
    }

    @Test
    void draggingPastTheCardJustMovesTheInk() {
        setUp();
        int before = firstX();
        fire(canvas, MouseEvent.MOUSE_PRESSED, 75, 70);
        fire(canvas, MouseEvent.MOUSE_DRAGGED, 700, 100);
        fire(canvas, MouseEvent.MOUSE_RELEASED, 700, 100);
        assertTrue(dropped.isEmpty());
        assertNotEquals(before, firstX(), "a plain move");
    }
}
