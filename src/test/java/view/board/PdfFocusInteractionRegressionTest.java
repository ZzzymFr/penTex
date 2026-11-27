package view.board;

import org.junit.jupiter.api.Test;

final class PdfFocusInteractionRegressionTest {
    @Test void pdfToolbarToggleAndWallClockFocusAnimation() throws Exception {
        PdfFocusInteractionSmoke.verify();
    }
}
