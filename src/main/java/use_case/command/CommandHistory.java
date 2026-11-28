package use_case.command;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/**
 * The invoker of the Command pattern: executes commands and keeps bounded undo and redo stacks.
 *
 * <p>Executing a new command clears the redo stack, as in every editor. The oldest steps are
 * dropped once {@code limit} is exceeded. Stacks are updated <em>before</em> a command runs, so any
 * listener the command triggers already sees the new {@link #canUndo()} / {@link #canRedo()} state.
 * Not thread-safe: use it from one thread (the Swing event thread for the canvas).
 */
public final class CommandHistory {
    public static final int DEFAULT_LIMIT = 100;

    private final Deque<Command> undoStack = new ArrayDeque<>();
    private final Deque<Command> redoStack = new ArrayDeque<>();
    private final List<Runnable> listeners = new ArrayList<>();
    private final int limit;

    public CommandHistory() {
        this(DEFAULT_LIMIT);
    }

    public CommandHistory(int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("History limit must be positive");
        }
        this.limit = limit;
    }

    /** Runs the command and records it as one undo step (or folds it into the newest step). */
    public void execute(Command command) {
        Objects.requireNonNull(command, "command");
        if (!command.changesState()) {
            return;
        }
        redoStack.clear();
        Command newest = undoStack.peek();
        if (newest != null && newest.mergeWith(command)) {
            command.execute();
            fireChanged();
            return;
        }
        undoStack.push(command);
        while (undoStack.size() > limit) {
            undoStack.removeLast();
        }
        command.execute();
        fireChanged();
    }

    /** Reverts the newest step; false when there is nothing to undo. */
    public boolean undo() {
        if (undoStack.isEmpty()) {
            return false;
        }
        Command command = undoStack.pop();
        redoStack.push(command);
        command.undo();
        fireChanged();
        return true;
    }

    /** Re-applies the newest undone step; false when there is nothing to redo. */
    public boolean redo() {
        if (redoStack.isEmpty()) {
            return false;
        }
        Command command = redoStack.pop();
        undoStack.push(command);
        command.redo();
        fireChanged();
        return true;
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    /** Name of the step {@link #undo()} would revert, or "" when there is none. */
    public String undoName() {
        return undoStack.isEmpty() ? "" : undoStack.peek().name();
    }

    /** Name of the step {@link #redo()} would re-apply, or "" when there is none. */
    public String redoName() {
        return redoStack.isEmpty() ? "" : redoStack.peek().name();
    }

    /** Number of undo steps currently kept. */
    public int undoSize() {
        return undoStack.size();
    }

    /** Forgets every step, e.g. after a different document was opened. */
    public void clear() {
        if (undoStack.isEmpty() && redoStack.isEmpty()) {
            return;
        }
        undoStack.clear();
        redoStack.clear();
        fireChanged();
    }

    /** Called after every change of the undo/redo stacks. */
    public void addChangeListener(Runnable listener) {
        listeners.add(Objects.requireNonNull(listener));
    }

    private void fireChanged() {
        for (Runnable listener : new ArrayList<>(listeners)) {
            listener.run();
        }
    }
}
