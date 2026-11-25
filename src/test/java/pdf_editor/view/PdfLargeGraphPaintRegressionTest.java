package pdf_editor.view;

import org.junit.jupiter.api.Test;

class PdfLargeGraphPaintRegressionTest {
    @Test void zoomedGraphAndFractionalViewport() throws Exception {
        PdfLargeGraphPaintSmoke.verify();
    }
}
