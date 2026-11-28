package interface_adapter.canvas;

/**
 * Opaque saved state of the canvas content (Memento pattern). Only the canvas that created it can
 * read it; commands just hold on to it so they can put the canvas back.
 */
public interface CanvasMemento {
}
