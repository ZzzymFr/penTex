package view.drawing;

import entity.Stroke;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

/**
 * Everyone who follows the canvas, by what they follow (Observer pattern). The canvas announces
 * events here; recognition, the toolbar, saving and the PDF studio subscribe without the canvas
 * knowing who they are. Listeners are called on the Swing event thread, in subscription order.
 */
public final class CanvasEvents {
    private final List<LongConsumer> formulaChanged = new ArrayList<>();
    private final List<LongConsumer> formulaMoved = new ArrayList<>();  // whole formula moved/resized, same ink
    private final List<Consumer<Stroke>> strokeFinished = new ArrayList<>();
    private final List<Runnable> inkChanged = new ArrayList<>();
    private final List<Runnable> graphStateChanged = new ArrayList<>();
    private final List<Runnable> userActed = new ArrayList<>();  // user started doing something
    private final List<Runnable> viewChanged = new ArrayList<>();    // pan / zoom
    private final List<Runnable> picked = new ArrayList<>();    // fresh lasso/box pick w/ ink
    private final List<Consumer<Object>> graphDropped = new ArrayList<>();   // ink dropped on a card
    private final List<Consumer<Object>> graphHovered = new ArrayList<>();  // ink dragged over a card

    // ---------------------------------------------------------------- subscribe

    /** Per edited formula id (not on undo). */
    public void onFormulaChanged(LongConsumer listener) {
        formulaChanged.add(listener);
    }

    /** Per whole formula moved or resized (not on undo). */
    public void onFormulaMoved(LongConsumer listener) {
        formulaMoved.add(listener);
    }

    /** A pen stroke is finished (after the formula check). */
    public void onStrokeFinished(Consumer<Stroke> listener) {
        strokeFinished.add(listener);
    }

    /** After actual ink changes, including erase, transform, clear and undo. */
    public void onInkChanged(Runnable listener) {
        inkChanged.add(Objects.requireNonNull(listener));
    }

    /** Graph edits, including camera-only changes. */
    public void onGraphStateChanged(Runnable listener) {
        graphStateChanged.add(Objects.requireNonNull(listener));
    }

    /** Left press with a tool, tool/mode switch, undo, clear, delete. */
    public void onUserAction(Runnable listener) {
        userActed.add(listener);
    }

    /** Pan or zoom. */
    public void onViewChanged(Runnable listener) {
        viewChanged.add(listener);
    }

    /** A fresh lasso/box pick holding ink. */
    public void onPick(Runnable listener) {
        picked.add(listener);
    }

    /** Picked ink was dropped onto the graph card with this tag. */
    public void onGraphDrop(Consumer<Object> listener) {
        graphDropped.add(listener);
    }

    /** Picked ink being dragged is now over this graph card's tag (null: over none). */
    public void onGraphHover(Consumer<Object> listener) {
        graphHovered.add(listener);
    }

    // ---------------------------------------------------------------- announce

    public void formulaChanged(long id) {
        for (LongConsumer listener : formulaChanged) {
            listener.accept(id);
        }
    }

    public void formulaMoved(long id) {
        for (LongConsumer l : new ArrayList<>(formulaMoved)) {
            l.accept(id);
        }
    }

    public void strokeFinished(Stroke stroke) {
        for (Consumer<Stroke> l : new ArrayList<>(strokeFinished)) {
            l.accept(stroke);
        }
    }

    public void inkChanged() {
        fire(inkChanged);
    }

    public void graphStateChanged() {
        fire(graphStateChanged);
    }

    public void userActed() {
        fire(userActed);
    }

    public void viewChanged() {
        fire(viewChanged);
    }

    public void picked() {
        fire(picked);
    }

    public void graphDropped(Object tag) {
        for (Consumer<Object> l : new ArrayList<>(graphDropped)) {
            l.accept(tag);
        }
    }

    public void graphHovered(Object tag) {
        for (Consumer<Object> l : new ArrayList<>(graphHovered)) {
            l.accept(tag);
        }
    }

    private static void fire(List<Runnable> listeners) {
        for (Runnable l : new ArrayList<>(listeners)) {
            l.run();
        }
    }
}
