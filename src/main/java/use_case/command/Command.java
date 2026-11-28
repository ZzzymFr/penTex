package use_case.command;

/**
 * An operation wrapped as an object (Command pattern) so it can be recorded, undone and redone.
 *
 * <p>Implementations know their receiver; the {@link CommandHistory} invoker knows only this
 * interface, so new kinds of operation are added without changing the history (Open/Closed).
 */
public interface Command {

    /** Performs the operation for the first time. */
    void execute();

    /** Reverts the effect of {@link #execute()} (or of the last {@link #redo()}). */
    void undo();

    /** Re-applies the operation after an {@link #undo()}; by default the same as executing it again. */
    default void redo() {
        execute();
    }

    /** A short human-readable name, e.g. for an "Undo Clear" tooltip. */
    default String name() {
        return getClass().getSimpleName();
    }

    /**
     * Whether executing now would change anything. A command that would not (a zoom already at its
     * limit, say) is skipped by the history, so it neither becomes an undo step nor clears redo.
     */
    default boolean changesState() {
        return true;
    }

    /**
     * Offers the next command to this one, which is the newest undo step. Returning true means this
     * command absorbed {@code next} (so one undo reverts both), e.g. the notches of one zoom gesture.
     */
    default boolean mergeWith(Command next) {
        return false;
    }
}
