package interface_adapter.canvas;

/**
 * Receiver port for content-editing commands: anything whose content can be saved and restored.
 * The canvas view implements it, so commands depend on this abstraction, not on Swing (DIP).
 */
public interface CanvasMementoOriginator {

    /** The content as it is now. */
    CanvasMemento captureState();

    /** Puts the content back exactly as captured. */
    void restoreState(CanvasMemento memento);
}
