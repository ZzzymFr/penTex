package pdf_editor.domain;

import pdf_editor.adapter.PngImages;import org.junit.jupiter.api.Test;
import pdf_editor.use_case.MaterialStore;
import java.awt.image.BufferedImage;
import static org.junit.jupiter.api.Assertions.*;

class MaterialCorrectionTest {
    private final MaterialStore library = new MaterialStore();
    @Test void replacementIsImmutable() {
        PdfMaterial before = library.addFormula("a"); PdfMaterial after = before.withText("b", "");
        assertEquals("a", before.formula()); assertEquals("b", after.formula());
    }
    @Test void replacementGetsDistinctMaterialIdForPdfAppendix() {
        PdfMaterial before = library.addFormula("a");
        assertFalse(before.id().equals(before.withText("b", "").id()));
    }
    @Test void replacementPreservesTitle() {
        PdfMaterial before = library.addFormula("a");
        assertEquals(before.title(), before.withText("b", "").title());
    }
    @Test void rejectsEditingGraphImage() {
        PdfMaterial graph = library.addGraph("y=x", PngImages.encode(new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB)));
        assertThrows(IllegalStateException.class, () -> graph.withText("x", ""));
    }
    @Test void acceptsEmptyFormula() { assertEquals("", library.addFormula("x").withText("", "3").formula()); }
    @Test void rejectsNullResult() { assertThrows(NullPointerException.class, () -> library.addFormula("a").withText("b", null)); }
    @Test void rejectsNullFormula() { assertThrows(NullPointerException.class, () -> library.addFormula("a").withText(null, "b")); }
    @Test void editedMaterialIsNotAnImage() { assertFalse(library.addFormula("a").withText("b", "").isGraph()); }
}
