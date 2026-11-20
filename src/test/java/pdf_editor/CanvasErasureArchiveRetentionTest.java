package pdf_editor;

import history.data_access.FileHistoryRepository;
import history.use_case.HistoryInteractor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pdf_editor.adapter.SelectedInkRegistry;
import view.DrawingCanvas;
import view.canvas.SharedCanvas;
import javax.swing.SwingUtilities;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class CanvasErasureArchiveRetentionTest {
    @TempDir Path dir;

    @Test void synchronizingEmptyCanvasNeverRemovesSavedCalculation() throws Exception {
        var history = new HistoryInteractor(new FileHistoryRepository(dir));
        var studio = new PdfStudioLauncher(history);
        var drawing = new DrawingCanvas();
        var shared = new SharedCanvas(drawing);
        SwingUtilities.invokeAndWait(() -> {
            studio.addCalculationWithSource(99L, "3+4", "7");
            studio.synchronizeCanvas(shared);
            drawing.clear();
            studio.synchronizeCanvas(shared);
        });
        assertEquals(1, studio.getLibrary().items().size());
        assertEquals("7", studio.getLibrary().items().get(0).result());
        assertEquals(1, history.list().size());
        assertEquals(1, new HistoryInteractor(new FileHistoryRepository(dir)).list().size());
    }

    @Test void savedInkRemainsAfterCanvasIsCleared() {
        var drawing = new DrawingCanvas();
        var library = new MathLibrary();
        var registry = new SelectedInkRegistry(drawing, library);
        var item = library.addFormula("archived");
        registry.track(item.id(), java.util.List.of());
        drawing.clear();
        registry.synchronize();
        assertNotNull(library.find(item.id()));
        library.clear();
        assertTrue(library.items().isEmpty());
        assertFalse(library.undoDelete());
    }
}
