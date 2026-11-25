package view.board.doc;

import org.junit.jupiter.api.Test;
import pdf_editor.MathLibrary;
import pdf_editor.adapter.PdfEditorController;
import pdf_editor.use_case.AutoLayoutInteractor;
import pdf_editor.use_case.DocumentEditor;
import pdf_editor.use_case.PdfExportInteractor;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Removed Library surface must not remain in the component hierarchy. */
class LibraryTransparentSurfaceTest {
    @Test void noHiddenLibraryRowsOrViewportRemain() {
        DocumentEditor editor = new DocumentEditor();
        MathLibrary library = new MathLibrary();
        library.addFormula("x^2");
        PdfEditorController controller = new PdfEditorController(library, editor,
                new AutoLayoutInteractor(editor),
                new PdfExportInteractor((doc,path)->{}, (doc,dpi)->List.of()));
        DocEditorPanel panel = new DocEditorPanel(controller);
        try {
            List<JList<?>> lists = new ArrayList<>();
            findLists(panel, lists);
            assertTrue(lists.isEmpty(), "The removed Library must not leave a second, hidden list/viewport");
        } finally {panel.close();}
    }
    private static void findLists(Container root, List<JList<?>> found) {
        for (Component c : root.getComponents()) {
            if (c instanceof JList<?> list) found.add(list);
            if (c instanceof Container child) findLists(child, found);
        }
    }
}
