package view;

/** Presentation-side source of portable graph metadata for Canvas Save/Open.
 * The drawing canvas can persist graph identity without depending on Swing graph internals.
 */
public interface CanvasGraphSource {
    /** Returns a versioned definition of the graph as currently displayed. */
    String graphDefinition();
}
