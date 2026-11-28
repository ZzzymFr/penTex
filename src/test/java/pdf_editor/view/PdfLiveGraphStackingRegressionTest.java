package pdf_editor.view;

import pdf_editor.adapter.PngImages;
import org.junit.jupiter.api.Test;
import pdf_editor.use_case.MaterialStore;
import pdf_editor.domain.PdfDocument;

import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Interactive Swing child must respect the same text/media paint order as exported PDF. */
class PdfLiveGraphStackingRegressionTest {
    private final MaterialStore library = new MaterialStore();
    private PdfDocument.Placement graph() {
        return PdfDocument.Placement.material(
                library.addInteractiveGraph(entity.graph.GraphDefinition.of("y=x*x"),
                        PngImages.encode(new BufferedImage(40, 25, BufferedImage.TYPE_INT_ARGB))), 70, 110, 220, 140);
    }
    private PdfDocument.Placement media(String name) {
        return PdfDocument.Placement.material(library.addFormula(name), 80, 125, 120, 45);
    }

    @Test void foregroundGraphObeysArrangeOrderInsteadOfAlwaysCoveringOtherItems() {
        var underneath = media("x");
        var live = graph();
        var above = media("y");
        var objects = List.of(underneath, live, above);
        assertEquals(List.of(above), PdfGraphStacking.foregroundAbove(objects, live));
        assertTrue(PdfGraphStacking.behindAbove(objects, live).isEmpty());
        assertTrue(!PdfGraphStacking.bodyAbove(live));
        // Bringing to front changes no special Swing z-order: nothing needs repainting above.
        assertTrue(PdfGraphStacking.foregroundAbove(List.of(underneath, above, live), live).isEmpty());
    }

    @Test void behindTextGraphKeepsBodyAndForegroundMediaAboveIt() {
        var live = graph().withLayout(PdfDocument.ImageLayer.BEHIND_TEXT,
                PdfDocument.AnchorMode.FIXED_ON_PAGE, -1);
        var laterBehind = media("a").withLayout(PdfDocument.ImageLayer.BEHIND_TEXT,
                PdfDocument.AnchorMode.FIXED_ON_PAGE, -1);
        var foreground = media("b");
        var note = PdfDocument.Placement.note("Text", 80, 120, 80, 45);
        var before = media("c");
        var objects = List.of(before, live, laterBehind, foreground, note);
        assertTrue(PdfGraphStacking.bodyAbove(live));
        assertEquals(List.of(laterBehind), PdfGraphStacking.behindAbove(objects, live));
        assertEquals(List.of(before, foreground, note), PdfGraphStacking.foregroundAbove(objects, live));
    }

    @Test void wrapTextGraphUsesItsExclusionAndNormalForegroundArrangeOrder() {
        var graph = graph().withLayout(PdfDocument.ImageLayer.WRAP_TEXT,
                PdfDocument.AnchorMode.FIXED_ON_PAGE, -1);
        var later = media("z");
        assertTrue(!PdfGraphStacking.bodyAbove(graph));
        assertEquals(List.of(later), PdfGraphStacking.foregroundAbove(List.of(graph, later), graph));
    }
}
