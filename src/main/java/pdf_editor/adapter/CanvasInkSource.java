package pdf_editor.adapter;

import entity.Stroke;

import java.util.List;

/**
 * What the PDF adapters need to know about the Math Lab canvas ink (Interface Segregation):
 * whether some strokes are still there, and when the ink changes. The canvas view implements it,
 * so this adapter layer never depends on the Swing component itself (Dependency Inversion).
 */
public interface CanvasInkSource {

    /** True when every one of these strokes (by identity) is still on the canvas. */
    boolean containsAllInk(List<Stroke> ink);

    /** Called after the ink changed in any way, including undo and clear. */
    void addInkChangeListener(Runnable listener);
}
