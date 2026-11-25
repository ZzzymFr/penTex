package pdf_editor.view;

import org.junit.jupiter.api.Test;

final class PdfViewportPaintRegressionTest {
    @Test void visiblePageReuseAndInvalidation() throws Exception {
        PdfViewportPaintSmoke.verify();
    }
}
