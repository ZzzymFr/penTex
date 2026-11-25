package view.board.doc;

import org.junit.jupiter.api.Test;
import pdf_editor.MathLibrary;
import pdf_editor.adapter.PdfEditorController;
import pdf_editor.use_case.AutoLayoutInteractor;
import pdf_editor.use_case.DocumentEditor;
import pdf_editor.use_case.PdfExportInteractor;

import javax.swing.JToggleButton;
import javax.swing.JButton;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Library has one persistent top-level entry, not duplicated inside ribbon tabs. */
class LibraryRibbonRegressionTest {
    private static DocEditorPanel editor() {
        DocumentEditor document = new DocumentEditor();
        return new DocEditorPanel(new PdfEditorController(new MathLibrary(), document,
                new AutoLayoutInteractor(document), new PdfExportInteractor((pdf, path) -> {}, (pdf, dpi) -> List.of())));
    }

    private static <T extends Component> List<T> find(Component root, Class<T> type) {
        List<T> matches = new ArrayList<>();
        if (type.isInstance(root)) matches.add(type.cast(root));
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) matches.addAll(find(child, type));
        }
        return matches;
    }

    private static void tab(DocEditorPanel editor, String title) {
        JToggleButton button = find(editor, JToggleButton.class).stream()
                .filter(b -> title.equals(b.getText())).findFirst().orElseThrow();
        button.doClick();
    }

    private static long activeLibraryCommands(DocEditorPanel editor) {
        return find(editor, RibbonButton.class).stream()
                .filter(b -> "ribbon.Library".equals(b.getName())).count();
    }

    @Test void saveAndOpenReplaceLibraryAcrossAllRibbonTabs() {
        DocEditorPanel editor = editor();
        try {
            for (String name : List.of("Home", "Insert", "Layout", "View")) {
                tab(editor, name);
                var buttons = find(editor, JButton.class);
                assertEquals(1L, buttons.stream().filter(b -> "pdfSaveButton".equals(b.getName())).count());
                assertEquals(1L, buttons.stream().filter(b -> "pdfOpenButton".equals(b.getName())).count());
                assertFalse(buttons.stream().anyMatch(b -> "Library".equals(b.getText()) || "libraryTopButton".equals(b.getName())));
                assertEquals(0L, activeLibraryCommands(editor));
            }
            assertFalse(editor.isLibraryVisible());
        } finally { editor.close(); }
    }

    @Test void noStandaloneLibraryHeaderOrCloseControlRemains() {
        DocEditorPanel editor = editor();
        try {
            assertTrue(find(editor, LibraryHeaderCloseButton.class).isEmpty());
            assertTrue(find(editor, javax.swing.JList.class).isEmpty());
        } finally { editor.close(); }
    }
}
