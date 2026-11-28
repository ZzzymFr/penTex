package pdf_editor;

import pdf_editor.adapter.PngImages;
import pdf_editor.use_case.MaterialStore;
import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class MathLibraryDeletionTest {
    @Test void manualDeletionIsReversible() {
        MaterialStore lib = new MaterialStore(); var item = lib.addFormula("x+1");
        assertTrue(lib.delete(item.id())); assertNull(lib.find(item.id()));
        assertTrue(lib.undoDelete()); assertEquals(item.id(), lib.items().get(0).id());
    }
    @Test void unknownDeletionDoesNotEnterUndoStack() {
        MaterialStore lib = new MaterialStore();
        assertFalse(lib.delete("missing"));assertFalse(lib.undoDelete());
    }
    @Test void deletedMaterialsAreRestoredLastFirst() {
        MaterialStore lib = new MaterialStore(); var a=lib.addFormula("x");var b=lib.addFormula("y");
        lib.delete(a.id());lib.delete(b.id()); assertTrue(lib.undoDelete());
        assertNotNull(lib.find(b.id()));assertNull(lib.find(a.id()));assertTrue(lib.undoDelete());assertNotNull(lib.find(a.id()));
    }
    @Test void duplicateExplicitSelectionsDoNotMultiplyCards() {
        MaterialStore lib = new MaterialStore();
        assertEquals(lib.addFormula("x").id(), lib.addFormula(" x ").id());assertEquals(1,lib.items().size());
    }
    @Test void graphClickAddsGraphWithoutUnrequestedFormulaCard() {
        MaterialStore lib = new MaterialStore();var img=new BufferedImage(60,30,BufferedImage.TYPE_INT_RGB);
        lib.addGraph("y=x",PngImages.encode(img));assertEquals(1,lib.items().size());assertTrue(lib.items().get(0).isGraph());
    }
    @Test void invalidationIsNotAnUndoableDelete() {
        MaterialStore lib = new MaterialStore();var m=lib.addFormula("x");
        assertTrue(lib.invalidate(m.id()));assertFalse(lib.undoDelete());
    }
    @Test void removeSourceLeavesUnrelatedMaterials() {
        MaterialStore lib = new MaterialStore();var formula=lib.addFormula("other");var calc=lib.addSourceCalculation(42,"x","2");
        lib.retainActiveSources(Set.of());assertNull(lib.find(calc.id()));assertNotNull(lib.find(formula.id()));
    }
    @Test void changingCalculationReplacesPreviousSource() {
        MaterialStore lib = new MaterialStore();var old=lib.addSourceCalculation(42,"x","2");
        var fresh=lib.addSourceCalculation(42,"x","3");
        assertNull(lib.find(old.id()));assertEquals(1,lib.items().size());assertNotNull(lib.find(fresh.id()));
    }
    @Test void clearCannotBeUndoneAsManualDelete() {
        MaterialStore lib=new MaterialStore();var m=lib.addFormula("x");lib.delete(m.id());lib.addFormula("z");
        lib.clear();assertFalse(lib.undoDelete());assertTrue(lib.items().isEmpty());
    }
    @Test void clearWhileVisuallyEmptyAlsoResetsUndeleteHistory() {
        MaterialStore lib = new MaterialStore(); var previous = lib.addFormula("x");
        assertTrue(lib.delete(previous.id()));
        assertTrue(lib.items().isEmpty());
        lib.clear();
        assertFalse(lib.undoDelete());
        assertTrue(lib.items().isEmpty());
    }
    @Test void deletedGraphRetainsOriginalImageWhenRestored() {
        MaterialStore lib=new MaterialStore();var g=lib.addGraph("x",PngImages.encode(new BufferedImage(20,15,2)));
        lib.delete(g.id());lib.undoDelete();assertEquals(20,lib.find(g.id()).imageWidth());
    }
}
