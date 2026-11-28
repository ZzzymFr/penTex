package view.drawing;

import java.awt.event.MouseEvent;

/**
 * One drawing tool's response to the primary mouse button (Strategy pattern): the canvas input
 * routes each gesture to the active tool, so a new tool is added without touching the router or
 * the other tools (Open/Closed).
 */
public interface CanvasTool {

    /** The primary button went down on the canvas (panning and close buttons are handled before). */
    void press(MouseEvent e);

    void drag(MouseEvent e);

    /** The button came up while this tool is active. */
    void release(MouseEvent e);

    /** The button came up while another tool is active: finish anything left over. */
    default void releaseWhileInactive() {
    }

    /** The pointer moved with no button down (not over a graph card's close button). */
    default void hover(MouseEvent e) {
    }

    /** The pointer left the canvas. */
    default void pointerLeft() {
    }

    /** Another tool was chosen: forget anything shown for this one. */
    default void reset() {
    }
}
