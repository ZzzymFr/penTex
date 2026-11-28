package use_case.command;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandHistoryTest {
    /** Adds a number to a shared list; undo removes it. */
    private static final class Append implements Command {
        private final List<Integer> target;
        private final int value;

        Append(List<Integer> target, int value) {
            this.target = target;
            this.value = value;
        }

        @Override public void execute() { target.add(value); }
        @Override public void undo() { target.remove(target.size() - 1); }
        @Override public String name() { return "Append " + value; }
    }

    private final List<Integer> numbers = new ArrayList<>();
    private final CommandHistory history = new CommandHistory();

    @Test
    void executeUndoRedoWalkThroughTheSteps() {
        history.execute(new Append(numbers, 1));
        history.execute(new Append(numbers, 2));
        assertEquals(List.of(1, 2), numbers);
        assertEquals("Append 2", history.undoName());

        assertTrue(history.undo());
        assertEquals(List.of(1), numbers);
        assertTrue(history.canRedo());
        assertEquals("Append 2", history.redoName());

        assertTrue(history.redo());
        assertEquals(List.of(1, 2), numbers);
        assertFalse(history.canRedo());
    }

    @Test
    void nothingToUndoOrRedoIsHarmless() {
        assertFalse(history.undo());
        assertFalse(history.redo());
        assertEquals("", history.undoName());
        assertEquals("", history.redoName());
    }

    @Test
    void aNewCommandDropsTheRedoChain() {
        history.execute(new Append(numbers, 1));
        history.undo();
        history.execute(new Append(numbers, 3));
        assertFalse(history.canRedo());
        assertEquals(List.of(3), numbers);
    }

    @Test
    void aCommandThatChangesNothingIsNotRecordedAndKeepsRedo() {
        history.execute(new Append(numbers, 1));
        history.undo();
        history.execute(new Command() {
            @Override public void execute() { throw new AssertionError("must not run"); }
            @Override public void undo() { }
            @Override public boolean changesState() { return false; }
        });
        assertTrue(history.canRedo());
        assertFalse(history.canUndo());
    }

    @Test
    void theOldestStepsAreDroppedPastTheLimit() {
        CommandHistory small = new CommandHistory(2);
        for (int i = 0; i < 5; i++) {
            small.execute(new Append(numbers, i));
        }
        assertEquals(2, small.undoSize());
        small.undo();
        small.undo();
        assertFalse(small.undo());
        assertEquals(List.of(0, 1, 2), numbers);
    }

    @Test
    void mergedCommandsAreOneUndoStep() {
        List<String> log = new ArrayList<>();
        class Merging implements Command {
            @Override public void execute() { log.add("run"); }
            @Override public void undo() { log.add("undo"); }
            @Override public boolean mergeWith(Command next) { return next instanceof Merging; }
        }
        history.execute(new Merging());
        history.execute(new Merging());
        history.execute(new Merging());
        assertEquals(1, history.undoSize());
        assertEquals(List.of("run", "run", "run"), log);
    }

    @Test
    void listenersSeeTheStacksAlreadyUpdatedWhileACommandRuns() {
        List<Boolean> seen = new ArrayList<>();
        history.execute(new Command() {
            @Override public void execute() { seen.add(history.canUndo()); }
            @Override public void undo() { seen.add(history.canRedo()); }
        });
        history.undo();
        assertEquals(List.of(true, true), seen);
    }

    @Test
    void changeListenersHearEveryStackChange() {
        int[] calls = {0};
        history.addChangeListener(() -> calls[0]++);
        history.execute(new Append(numbers, 1));
        history.undo();
        history.redo();
        history.clear();
        assertEquals(4, calls[0]);
        assertFalse(history.canUndo());
    }

    @Test
    void limitMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> new CommandHistory(0));
    }
}
