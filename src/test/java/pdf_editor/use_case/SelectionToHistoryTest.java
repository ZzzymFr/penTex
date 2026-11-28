package pdf_editor.use_case;

import pdf_editor.adapter.PngImages;
import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import static org.junit.jupiter.api.Assertions.*;

class SelectionToHistoryTest {
    private final BufferedImage selected=new BufferedImage(25,15,BufferedImage.TYPE_INT_RGB);
    @Test void explicitRecognitionAddsEditableFormula() throws Exception {
        MaterialStore library=new MaterialStore();var useCase=new SelectionToHistory<java.awt.image.BufferedImage>(i->"x^2",library);
        assertEquals("x^2",useCase.recognize(selected));
        var material=useCase.addFormula("x^2");assertFalse(material.isGraph());assertEquals("x^2",material.formula());
    }
    @Test void previewDoesNotAddAnything() throws Exception {
        MaterialStore library=new MaterialStore();var useCase=new SelectionToHistory<java.awt.image.BufferedImage>(i->"x",library);
        useCase.recognize(selected);assertTrue(library.items().isEmpty());
    }
    @Test void rejectedBlankRecognitionDoesNotAdd() {
        MaterialStore library=new MaterialStore();var useCase=new SelectionToHistory<java.awt.image.BufferedImage>(i->"  ",library);
        assertThrows(IllegalArgumentException.class,()->useCase.recognize(selected));assertTrue(library.items().isEmpty());
    }
    @Test void recognitionErrorsPropagateWithoutAdding() {
        MaterialStore library=new MaterialStore();var useCase=new SelectionToHistory<java.awt.image.BufferedImage>(i->{throw new IllegalStateException("offline");},library);
        assertThrows(IllegalStateException.class,()->useCase.recognize(selected));assertTrue(library.items().isEmpty());
    }
    @Test void explicitInkCapturePreservesDimensions() {
        MaterialStore library=new MaterialStore();var useCase=new SelectionToHistory<java.awt.image.BufferedImage>(i->"x",library);
        var img=useCase.addInk(PngImages.encode(selected));assertTrue(img.isGraph());assertEquals(25,img.imageWidth());assertEquals(15,img.imageHeight());
    }
    @Test void nullSelectionsCannotBeRecognized() {
        var useCase=new SelectionToHistory<java.awt.image.BufferedImage>(i->"x",new MaterialStore());
        assertThrows(NullPointerException.class,()->useCase.recognize(null));
    }
    @Test void multipleRecognitionsOfSameFormulaDoNotDuplicateMaterials() {
        MaterialStore library=new MaterialStore();var useCase=new SelectionToHistory<java.awt.image.BufferedImage>(i->"x^2",library);
        useCase.addFormula("x^2");useCase.addFormula("x^2");assertEquals(1,library.items().size());
    }
}
