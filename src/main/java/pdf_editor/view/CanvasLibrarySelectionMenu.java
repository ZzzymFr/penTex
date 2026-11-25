package pdf_editor.view;

import pdf_editor.adapter.SelectedInkRegistry;
import pdf_editor.use_case.SelectionToHistory;
import view.DrawingCanvas;

/** @deprecated Use CanvasHistorySelectionMenu; no standalone Library UI is created. */
@Deprecated
public final class CanvasLibrarySelectionMenu {
    private CanvasLibrarySelectionMenu() { }
    public static void install(DrawingCanvas canvas, SelectionToHistory action, SelectedInkRegistry registry) {
        CanvasHistorySelectionMenu.install(canvas, action, registry);
    }
}
