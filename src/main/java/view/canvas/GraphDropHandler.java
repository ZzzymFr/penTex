package view.canvas;

import entity.graph.GraphDefinition;
import java.awt.Point;
import java.awt.image.BufferedImage;

/** Presentation-level handoff when an existing live Canvas graph crosses into PDF. */
@FunctionalInterface
public interface GraphDropHandler {
    boolean drop(GraphDefinition graph, BufferedImage preview, Point cursorOnScreen);

    /** Hit-test before moving any data. The default is conservative for old handlers. */
    default boolean accepts(Point cursorOnScreen) { return false; }

    /** Release-time compatibility: older lambda handlers decide target validity in drop(). */
    default boolean shouldAttemptDropOnRelease(Point cursorOnScreen) { return true; }

    /** Only show a reversible PDF placement outline; never mutate either workspace. */
    default void preview(Point cursorOnScreen, double graphAspectRatio) { }

    /** An optional current-view thumbnail; old consumers can keep the outline-only callback. */
    default void preview(Point cursorOnScreen, double graphAspectRatio, BufferedImage thumbnail) {
        preview(cursorOnScreen, graphAspectRatio);
    }

    /** Remove the transient outline on return to Canvas, cancellation or release. */
    default void clearPreview() { }
}
