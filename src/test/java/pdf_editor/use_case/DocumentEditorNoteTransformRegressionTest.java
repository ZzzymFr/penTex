package pdf_editor.use_case;

import org.junit.jupiter.api.Test;
import pdf_editor.MathLibrary;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Regression: text boxes have no material, but must remain resizable alongside thin PDF ink. */
class DocumentEditorNoteTransformRegressionTest {
    @Test void textBoxCanExpandAndStillUsesOriginalMinimumSize() {
        DocumentEditor editor = new DocumentEditor();
        var note = editor.addNote(0, "A\nB", 72f, 72f);
        editor.beginTransform(0, note.id());
        var expanded = editor.transform(0, note.id(), 72f, 72f, 451f, 690f);
        assertEquals(451f, expanded.width(), 0.001f);
        assertEquals(690f, expanded.height(), 0.001f);
        var compact = editor.transform(0, note.id(), 72f, 72f, 10f, 10f);
        assertEquals(45f, compact.width(), 0.001f);
        assertEquals(35f, compact.height(), 0.001f);
    }

    @Test void narrowPdfInkRemainsNarrowAfterTransformingTextBox() {
        DocumentEditor editor = new DocumentEditor();
        var note = editor.addNote(0, "Text", 72f, 72f);
        editor.beginTransform(0, note.id());
        editor.transform(0, note.id(), 72f, 72f, 300f, 180f);

        var ink = MathLibrary.region("PDF ink stroke",
                new BufferedImage(30, 450, BufferedImage.TYPE_INT_ARGB));
        var stroke = editor.place(0, ink, 210f, 100f, 10f, 150f);
        editor.beginTransform(0, stroke.id());
        var moved = editor.transform(0, stroke.id(), 240f, 110f, 10f, 150f);
        assertEquals(240f, moved.x(), 0.001f);
        assertEquals(10f, moved.width(), 0.001f);
    }
}
