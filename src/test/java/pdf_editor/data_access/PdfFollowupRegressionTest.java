package pdf_editor.data_access;

import org.junit.jupiter.api.Test;

final class PdfFollowupRegressionTest {
    @Test void duplicateRecencyAndSavedDocumentMetadataSurviveRestart() throws Exception {
        PdfFollowupSmoke.persistenceAndRecency();
    }
    @Test void zoomedScreenRasterAndTranslucentGhostAreReused() throws Exception {
        PdfFollowupSmoke.screenRasterAndGhost();
    }
}
