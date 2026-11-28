package interface_adapter.canvas;

import use_case.command.Command;

import java.util.Objects;

/**
 * Base class of every command that edits canvas content (ink, answers, pictures, graph cards).
 *
 * <p>Executing saves a memento of the content and then runs the receiver action. Undo saves the
 * content as it is at that moment (so anything that happened after the edit, such as an answer
 * placed by the background worker or the rest of an eraser drag, is kept for redo) and restores
 * the earlier memento; redo does the reverse. Subclasses only name the operation.
 */
public abstract class CanvasEditCommand implements Command {
    private final String name;
    private final CanvasMementoOriginator canvas;
    private final Runnable action;
    private CanvasMemento before;
    private CanvasMemento after;

    protected CanvasEditCommand(String name, CanvasMementoOriginator canvas, Runnable action) {
        this.name = Objects.requireNonNull(name, "name");
        this.canvas = Objects.requireNonNull(canvas, "canvas");
        this.action = Objects.requireNonNull(action, "action");
    }

    @Override
    public final void execute() {
        before = canvas.captureState();
        action.run();
    }

    @Override
    public final void undo() {
        if (before == null) {
            throw new IllegalStateException(name + " was never executed");
        }
        after = canvas.captureState();
        canvas.restoreState(before);
    }

    @Override
    public final void redo() {
        if (after == null) {
            throw new IllegalStateException(name + " was never undone");
        }
        before = canvas.captureState();
        canvas.restoreState(after);
    }

    @Override
    public final String name() {
        return name;
    }
}
