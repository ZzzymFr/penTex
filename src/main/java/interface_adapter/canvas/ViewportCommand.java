package interface_adapter.canvas;

import use_case.command.Command;

import java.util.Objects;

/** Moves the view to a computed viewport; undo goes back to the viewport it replaced. */
public class ViewportCommand implements Command {
    private final String name;
    private final ViewportTarget target;
    private final ViewportState before;
    private ViewportState after;

    public ViewportCommand(String name, ViewportTarget target, ViewportState after) {
        this.name = Objects.requireNonNull(name, "name");
        this.target = Objects.requireNonNull(target, "target");
        this.before = target.viewport();
        this.after = Objects.requireNonNull(after, "after");
    }

    @Override
    public void execute() {
        target.applyViewport(after);
    }

    @Override
    public void undo() {
        target.applyViewport(before);
    }

    @Override
    public void redo() {
        target.applyViewport(after);
    }

    @Override
    public boolean changesState() {
        return !before.equals(after);
    }

    @Override
    public String name() {
        return name;
    }

    protected final ViewportTarget target() {
        return target;
    }

    protected final ViewportState after() {
        return after;
    }

    /** Lets a merged command move this command's end point. */
    protected final void extendTo(ViewportState next) {
        after = Objects.requireNonNull(next);
    }
}
