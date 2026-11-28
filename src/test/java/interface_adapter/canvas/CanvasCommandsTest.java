package interface_adapter.canvas;

import org.junit.jupiter.api.Test;
import use_case.command.CommandHistory;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The canvas commands against plain fakes of their receiver ports: no Swing needed. */
class CanvasCommandsTest {
    private static final double EPS = 1e-9;

    /** A list of words standing in for the canvas content. */
    private static final class Words implements CanvasMementoOriginator {
        private record Saved(List<String> words) implements CanvasMemento { }

        final List<String> words = new ArrayList<>();

        @Override public CanvasMemento captureState() { return new Saved(List.copyOf(words)); }

        @Override public void restoreState(CanvasMemento memento) {
            words.clear();
            words.addAll(((Saved) memento).words());
        }
    }

    private static final class View implements ViewportTarget {
        ViewportState state = ViewportState.IDENTITY;
        int applied;

        @Override public ViewportState viewport() { return state; }

        @Override public void applyViewport(ViewportState next) {
            state = next;
            applied++;
        }
    }

    private final CommandHistory history = new CommandHistory();

    @Test
    void drawThenClearThenUndoBothAndRedoBoth() {
        Words canvas = new Words();
        history.execute(new DrawStrokeCommand(canvas, () -> canvas.words.add("stroke")));
        history.execute(new ClearCanvasCommand(canvas, canvas.words::clear));
        assertTrue(canvas.words.isEmpty());

        history.undo();
        assertEquals(List.of("stroke"), canvas.words);
        history.undo();
        assertTrue(canvas.words.isEmpty());

        history.redo();
        assertEquals(List.of("stroke"), canvas.words);
        history.redo();
        assertTrue(canvas.words.isEmpty());
    }

    @Test
    void undoKeepsWhatHappenedAfterTheCommandForRedo() {
        Words canvas = new Words();
        history.execute(new EraseCommand(canvas, () -> canvas.words.add("first rub")));
        canvas.words.add("rest of the drag");  // same gesture, no new command
        history.undo();
        assertTrue(canvas.words.isEmpty());
        history.redo();
        assertEquals(List.of("first rub", "rest of the drag"), canvas.words);
    }

    @Test
    void commandsAreNamedForTheirOperation() {
        Words canvas = new Words();
        assertEquals("Draw", new DrawStrokeCommand(canvas, () -> { }).name());
        assertEquals("Clear", new ClearCanvasCommand(canvas, () -> { }).name());
        assertEquals("Erase", new EraseCommand(canvas, () -> { }).name());
        assertEquals("Delete", new SelectionEditCommand(SelectionEditCommand.Kind.DELETE, canvas, () -> { }).name());
        assertEquals("Place picture",
                new PictureEditCommand(PictureEditCommand.Kind.PLACE, canvas, () -> { }).name());
        assertEquals("Zoom", new ZoomCommand(new View(), 0, 0, 2, 0.25, 4, 0).name());
        assertEquals("Reset view", new ResetViewCommand(new View(), ViewportState.IDENTITY).name());
    }

    @Test
    void anEditCommandCannotBeUndoneBeforeItRan() {
        DrawStrokeCommand draw = new DrawStrokeCommand(new Words(), () -> { });
        assertThrows(IllegalStateException.class, draw::undo);
        assertThrows(IllegalStateException.class, draw::redo);
    }

    @Test
    void zoomKeepsThePointUnderTheMouseAndUndoes() {
        View view = new View();
        history.execute(new ZoomCommand(view, 100, 50, 2, 0.25, 4, 0));
        assertEquals(2, view.state.zoom(), EPS);
        assertEquals(100, view.state.toScreenX(100), EPS);   // world (100, 50) stays under the mouse
        assertEquals(50, view.state.toScreenY(50), EPS);

        history.undo();
        assertEquals(ViewportState.IDENTITY, view.state);
        history.redo();
        assertEquals(2, view.state.zoom(), EPS);
    }

    @Test
    void notchesOfOneGestureMergeIntoOneStep() {
        View view = new View();
        history.execute(new ZoomCommand(view, 0, 0, 1.1, 0.25, 4, 1000));
        history.execute(new ZoomCommand(view, 0, 0, 1.1, 0.25, 4, 1050));
        history.execute(new ZoomCommand(view, 0, 0, 1.1, 0.25, 4, 1100));
        assertEquals(1, history.undoSize());
        assertEquals(1.331, view.state.zoom(), 1e-9);
        history.undo();
        assertEquals(1, view.state.zoom(), EPS);
    }

    @Test
    void zoomsFarApartAreSeparateSteps() {
        View view = new View();
        history.execute(new ZoomCommand(view, 0, 0, 2, 0.25, 4, 0));
        history.execute(new ZoomCommand(view, 0, 0, 2, 0.25, 4, ZoomCommand.GESTURE_GAP_MILLIS + 1));
        assertEquals(2, history.undoSize());
    }

    @Test
    void aZoomAlreadyAtItsLimitIsNotAStep() {
        View view = new View();
        view.state = new ViewportState(4, 0, 0);
        history.execute(new ZoomCommand(view, 0, 0, 2, 0.25, 4, 0));
        assertFalse(history.canUndo());
        assertEquals(0, view.applied);
    }

    @Test
    void resetViewUndoesBackToThePreviousViewport() {
        View view = new View();
        ViewportState zoomed = new ViewportState(3, 40, -20);
        view.state = zoomed;
        history.execute(new ResetViewCommand(view, ViewportState.IDENTITY));
        assertEquals(ViewportState.IDENTITY, view.state);
        history.undo();
        assertSame(zoomed, view.state);
    }

    @Test
    void viewportMathRoundTrips() {
        ViewportState v = new ViewportState(2, 10, -4).pannedBy(5, 6);
        assertEquals(15, v.originX(), EPS);
        assertEquals(2, v.originY(), EPS);
        assertEquals(7, v.toWorldX(v.toScreenX(7)), EPS);
        assertEquals(-3, v.toWorldY(v.toScreenY(-3)), EPS);
        assertThrows(IllegalArgumentException.class, () -> new ViewportState(0, 0, 0));
    }
}
