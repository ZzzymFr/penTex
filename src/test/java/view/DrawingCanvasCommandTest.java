package view;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Drawing, clearing and zooming the real canvas are commands that undo and redo. */
class DrawingCanvasCommandTest {
    private static final double EPS = 1e-9;
    private final DrawingCanvas canvas = new DrawingCanvas();

    private void drag(int... xy) {
        send(MouseEvent.MOUSE_PRESSED, xy[0], xy[1]);
        for (int i = 2; i < xy.length; i += 2) {
            send(MouseEvent.MOUSE_DRAGGED, xy[i], xy[i + 1]);
        }
        send(MouseEvent.MOUSE_RELEASED, xy[xy.length - 2], xy[xy.length - 1]);
    }

    private void send(int id, int x, int y) {
        canvas.dispatchEvent(new MouseEvent(canvas, id, 0, 0, x, y, 1, false, MouseEvent.BUTTON1));
    }

    private void ctrlWheel(long when, int notches) {
        canvas.dispatchEvent(new MouseWheelEvent(canvas, MouseEvent.MOUSE_WHEEL, when,
                InputEvent.CTRL_DOWN_MASK, 0, 0, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, notches));
    }

    @Test
    void drawingAStrokeIsOneUndoStep() {
        drag(10, 10, 40, 30, 60, 50);
        assertEquals(1, canvas.getStrokes().size());
        assertEquals("Draw", canvas.undoName());

        canvas.undo();
        assertTrue(canvas.isBlank());
        assertEquals("Draw", canvas.redoName());

        canvas.redo();
        assertEquals(1, canvas.getStrokes().size());
        assertEquals(3, canvas.getStrokes().get(0).getPoints().size());
    }

    @Test
    void clearIsUndoableAndRedoable() {
        drag(10, 10, 40, 30);
        drag(100, 10, 140, 30);
        canvas.clear();
        assertTrue(canvas.isBlank());
        assertEquals("Clear", canvas.undoName());

        canvas.undo();
        assertEquals(2, canvas.getStrokes().size());
        canvas.redo();
        assertTrue(canvas.isBlank());
    }

    @Test
    void recolouringTheSelectionIsUndoable() {
        drag(10, 10, 40, 30);
        canvas.setTool(DrawingCanvas.Tool.RECTANGLE);
        drag(0, 0, 100, 100);
        assertTrue(canvas.recolorSelection(Color.RED));
        assertEquals(Color.RED.getRGB(), canvas.exportCanvasState().ink().get(0).argb());
        assertEquals("Recolour", canvas.undoName());
        canvas.undo();
        assertEquals(Color.BLACK.getRGB(), canvas.exportCanvasState().ink().get(0).argb());
    }

    @Test
    void zoomIsUndoableAndRedoable() {
        canvas.zoomAt(0, 0, 2);
        assertEquals(2, canvas.getZoom(), EPS);
        assertEquals("Zoom", canvas.undoName());

        canvas.undo();
        assertEquals(1, canvas.getZoom(), EPS);
        canvas.redo();
        assertEquals(2, canvas.getZoom(), EPS);
    }

    @Test
    void oneCtrlScrollGestureIsOneUndoStep() {
        ctrlWheel(1000, -1);
        ctrlWheel(1020, -1);
        ctrlWheel(1040, -1);
        assertEquals(1.331, canvas.getZoom(), 1e-9);
        canvas.undo();
        assertEquals(1, canvas.getZoom(), EPS);
        assertFalse(canvas.canUndo());
    }

    @Test
    void undoWalksBackThroughInkAndViewInOrder() {
        drag(10, 10, 40, 30);
        canvas.zoomAt(0, 0, 2);
        canvas.clear();

        canvas.undo();                         // Clear
        assertEquals(1, canvas.getStrokes().size());
        assertEquals(2, canvas.getZoom(), EPS);
        canvas.undo();                         // Zoom
        assertEquals(1, canvas.getZoom(), EPS);
        assertEquals(1, canvas.getStrokes().size());
        canvas.undo();                         // Draw
        assertTrue(canvas.isBlank());
        assertFalse(canvas.canUndo());
    }

    @Test
    void resetViewIsUndoable() {
        canvas.zoomAt(0, 0, 3);
        canvas.resetView();
        assertEquals(1, canvas.getZoom(), EPS);
        canvas.undo();
        assertEquals(3, canvas.getZoom(), EPS);
    }

    @Test
    void openingASavedCanvasForgetsTheHistory() throws Exception {
        drag(10, 10, 40, 30);
        canvas.zoomAt(0, 0, 2);
        DrawingCanvas other = new DrawingCanvas();
        other.restoreCanvasState(canvas.exportCanvasState());
        assertFalse(other.canUndo());
        canvas.restoreCanvasState(canvas.exportCanvasState());
        assertFalse(canvas.canUndo());
        assertFalse(canvas.canRedo());
    }

    @Test
    void switchingToolMidDragEndsTheDragCleanly() {
        drag(10, 10, 40, 30);
        canvas.setTool(DrawingCanvas.Tool.RECTANGLE);
        drag(0, 0, 100, 100);                       // pick the stroke
        send(MouseEvent.MOUSE_PRESSED, 20, 20);     // start moving it...
        send(MouseEvent.MOUSE_DRAGGED, 60, 60);
        canvas.setTool(DrawingCanvas.Tool.PEN);     // ...and switch tool before letting go
        send(MouseEvent.MOUSE_RELEASED, 60, 60);
        assertFalse(canvas.isBusy());
        assertEquals(10, canvas.getStrokes().get(0).getPoints().get(0).getX());  // nothing moved
    }

    @Test
    void switchingToolMidStrokeStillAnnouncesTheStroke() {
        java.util.List<entity.Stroke> finished = new java.util.ArrayList<>();
        canvas.addStrokeListener(finished::add);
        send(MouseEvent.MOUSE_PRESSED, 10, 10);
        send(MouseEvent.MOUSE_DRAGGED, 40, 30);
        canvas.setTool(DrawingCanvas.Tool.ERASER);
        assertEquals(1, finished.size());
        send(MouseEvent.MOUSE_RELEASED, 40, 30);
        assertEquals(1, finished.size());
        assertFalse(canvas.isBusy());
    }
}
