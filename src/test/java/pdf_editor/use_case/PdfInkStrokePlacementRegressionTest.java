package pdf_editor.use_case;

import org.junit.jupiter.api.Test;
import pdf_editor.MathLibrary;
import pdf_editor.domain.PdfDocument;
import java.awt.image.BufferedImage;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression: committing a thin pen stroke must not shift it into a minimum-size image box. */
class PdfInkStrokePlacementRegressionTest {
    private static MathLibrary.Material ink(int pixelWidth, int pixelHeight) {
        return MathLibrary.region("PDF ink stroke",
                new BufferedImage(pixelWidth, pixelHeight, BufferedImage.TYPE_INT_ARGB));
    }

    @Test void verticalStrokeRetainsItsPositionAndNarrowWidth() {
        DocumentEditor editor = new DocumentEditor();
        var placement = editor.place(0, ink(30, 450), 210f, 100f, 10f, 150f);
        assertEquals(210f, placement.x(), 0.001f);
        assertEquals(100f, placement.y(), 0.001f);
        assertEquals(10f, placement.width(), 0.001f);
        assertEquals(150f, placement.height(), 0.001f);
        editor.beginTransform(0, placement.id());
        var moved = editor.transform(0, placement.id(), 240f, 110f, 10f, 150f);
        assertEquals(240f, moved.x(), 0.001f);
        assertEquals(10f, moved.width(), 0.001f);
        assertTrue(editor.undo());
        assertEquals(210f, editor.find(0, placement.id()).x(), 0.001f);
    }

    @Test void horizontalStrokeRetainsItsPositionAndShortHeight() {
        DocumentEditor editor = new DocumentEditor();
        var placement = editor.place(0, ink(450, 30), 100f, 220f, 150f, 10f);
        assertEquals(220f, placement.y(), 0.001f);
        assertEquals(10f, placement.height(), 0.001f);
    }

    @Test void narrowInkAtPageEdgeIsNotRepositionedBySizeExpansion() {
        DocumentEditor editor = new DocumentEditor();
        var placement = editor.place(0, ink(18, 90), PdfDocument.PAGE_WIDTH - 6f,
                PdfDocument.PAGE_HEIGHT - 30f, 6f, 30f);
        assertEquals(PdfDocument.PAGE_WIDTH - 6f, placement.x(), 0.001f);
        assertEquals(PdfDocument.PAGE_HEIGHT - 30f, placement.y(), 0.001f);
        assertEquals(6f, placement.width(), 0.001f);
        assertEquals(30f, placement.height(), 0.001f);
    }

    @Test void otherMaterialsStillUseOriginalMinimumDimensions() {
        DocumentEditor editor = new DocumentEditor();
        var image = MathLibrary.region("Other image",
                new BufferedImage(30, 30, BufferedImage.TYPE_INT_ARGB));
        var placement = editor.place(0, image, 100f, 100f, 10f, 10f);
        assertEquals(45f, placement.width(), 0.001f);
        assertEquals(35f, placement.height(), 0.001f);
        var formula = editor.place(0, new MathLibrary().addFormula("x"), 100f, 100f, 10f, 10f);
        assertEquals(45f, formula.width(), 0.001f);
        assertEquals(35f, formula.height(), 0.001f);
    }
}
