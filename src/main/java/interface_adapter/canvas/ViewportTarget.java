package interface_adapter.canvas;

/** Receiver port for view commands (zoom, reset view): reads and sets the canvas viewport. */
public interface ViewportTarget {

    ViewportState viewport();

    void applyViewport(ViewportState state);
}
