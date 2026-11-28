package pdf_editor.domain;

import pdf_editor.adapter.PngImages;
import org.junit.jupiter.api.Test;
import pdf_editor.use_case.MaterialStore;
import java.awt.Color;
import java.awt.image.BufferedImage;
import static org.junit.jupiter.api.Assertions.*;

class PdfMaterialTest {
    @Test void imageDataIsImmutableAndGraphHasOwnFormula() {
        MaterialStore library = new MaterialStore();
        BufferedImage image = new BufferedImage(100, 50, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0,0, Color.RED.getRGB());
        MaterialStore.Material graph = library.addGraph("y=x^2", PngImages.encode(image));
        image.setRGB(0,0, Color.BLUE.getRGB());
        assertTrue(graph.isGraph());
        // addGraph represents only the image. The graph action publishes the
        // source formula separately, so addGraph must not duplicate that card.
        assertEquals(1, library.items().size());
        library.addFormula("y=x^2");
        assertEquals(2, library.items().size());
        assertFalse(library.items().get(1).isGraph());
        byte[] bytes = graph.imagePng();
        byte before = bytes[0]; bytes[0] ^= 1;
        assertEquals(before, graph.imagePng()[0]);
    }
    @Test void listenerCanBeUnsubscribed() {
        MaterialStore library = new MaterialStore();
        int[] calls = {0};
        Runnable unsubscribe = library.onChange(() -> calls[0]++);
        library.addFormula("x");
        assertEquals(1, calls[0]);
        unsubscribe.run();
        library.addFormula("y");
        assertEquals(1, calls[0]);
    }
}
