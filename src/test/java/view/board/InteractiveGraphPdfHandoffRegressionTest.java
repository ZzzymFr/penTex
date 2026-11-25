package view.board;

import entity.graph.GraphDefinition;
import history.domain.HistoryEntry;
import interface_adapter.graph.GraphDefinitionCodec;
import interface_adapter.history.HistoryItem;
import org.junit.jupiter.api.Test;
import pdf_editor.MathLibrary;
import pdf_editor.adapter.PdfEditorController;
import pdf_editor.use_case.AutoLayoutInteractor;
import pdf_editor.use_case.DocumentEditor;
import pdf_editor.use_case.PdfExportInteractor;

import javax.imageio.ImageIO;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Cross-workspace transfers must carry the native graph, not just its PNG. */
class InteractiveGraphPdfHandoffRegressionTest {
    private static BufferedImage thumbnail() {
        return new BufferedImage(160, 110, BufferedImage.TYPE_INT_ARGB);
    }
    private static byte[] png() throws Exception {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        ImageIO.write(thumbnail(), "png", data);
        return data.toByteArray();
    }
    private static PdfPanel panel(MathLibrary library, DocumentEditor document) {
        return new PdfPanel(new PdfEditorController(library, document,
                new AutoLayoutInteractor(document),
                new PdfExportInteractor((d, file) -> { }, (d, dpi) -> List.of())));
    }

    @Test void liveCanvasGraphDroppedOnPdfBecomesEditableAndReusable() {
        var lib = new MathLibrary();
        var doc = new DocumentEditor();
        var pdf = panel(lib, doc);
        var graph = new GraphDefinition(List.of("y=x^2"), null, null, null);
        assertTrue(pdf.dropInteractiveGraph(graph, thumbnail(), new Point(100, 100)));
        assertEquals(1, lib.items().size());
        assertTrue(lib.items().get(0).isInteractiveGraph());
        assertEquals(1, doc.placements(0).size());
        assertEquals(graph, doc.placements(0).get(0).material().graphDefinition());
        assertEquals(lib.items().get(0).id(), doc.placements(0).get(0).material().id());
    }

    @Test void historyGraphDroppedOnPdfRetainsItsDefinitionInLibraryAndDocument() throws Exception {
        var graph = new GraphDefinition(List.of("z=x*y"), null, null, null,
                new GraphDefinition.Camera(0.7, -0.3));
        var entry = new HistoryEntry("graph-1", HistoryEntry.Kind.GRAPH, "Graph", "z=x*y", "",
                png(), 160, 110, Instant.EPOCH, GraphDefinitionCodec.encode(graph));
        var lib = new MathLibrary();
        var doc = new DocumentEditor();
        var pdf = panel(lib, doc);
        assertTrue(pdf.dropHistoryItem(new HistoryItem(entry), new Point(80, 90)));
        assertEquals(graph, lib.items().get(0).graphDefinition());
        assertEquals(graph, doc.placements(0).get(0).material().graphDefinition());
    }
}
