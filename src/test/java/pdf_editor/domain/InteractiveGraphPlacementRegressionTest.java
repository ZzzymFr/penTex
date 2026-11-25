package pdf_editor.domain;

import entity.graph.GraphDefinition;
import org.junit.jupiter.api.Test;
import pdf_editor.MathLibrary;
import pdf_editor.use_case.DocumentEditor;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Library thumbnails and live PDF placements retain graph identity through layout edits. */
class InteractiveGraphPlacementRegressionTest {
    @Test void graphSupportsMoveResizeUndoAndSnapshotWithoutLosingItsDefinition() throws Exception {
        GraphDefinition definition = new GraphDefinition(List.of("z=x*y"),
                new GraphDefinition.Range(-4, 4), new GraphDefinition.Range(-4, 4),
                new GraphDefinition.Range(-8, 8), new GraphDefinition.Camera(-0.6, 0.9));
        MathLibrary library = new MathLibrary();
        var thumbnail = new BufferedImage(240, 180, BufferedImage.TYPE_INT_ARGB);
        var material = library.addInteractiveGraph(definition, thumbnail);
        DocumentEditor editor = new DocumentEditor();
        var placed = editor.place(0, material, 65f, 83f, 280f, 190f);
        assertTrue(placed.material().isInteractiveGraph());
        editor.beginTransform(0, placed.id());
        editor.transform(0, placed.id(), 75f, 93f, 300f, 205f);
        assertEquals(300f, editor.find(0, placed.id()).width(), 0.01f);
        ByteArrayOutputStream pixels = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(1200, 820, BufferedImage.TYPE_INT_ARGB), "png", pixels);
        assertTrue(editor.updateGraphSnapshot(placed.id(), pixels.toByteArray(), 1200, 820));
        assertEquals(definition, editor.find(0, placed.id()).material().graphDefinition());
        assertEquals(1200, editor.find(0, placed.id()).material().imageWidth());
        assertTrue(editor.undo());
        assertEquals(65f, editor.find(0, placed.id()).x(), 0.01f);
        assertEquals(definition, editor.find(0, placed.id()).material().graphDefinition());
        assertTrue(library.items().get(0).isInteractiveGraph());
        assertEquals(240, library.items().get(0).imageWidth()); // stays a thumbnail
    }
}
