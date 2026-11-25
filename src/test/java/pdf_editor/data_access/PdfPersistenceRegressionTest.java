package pdf_editor.data_access;

import org.junit.jupiter.api.Test;

final class PdfPersistenceRegressionTest {
    @Test void editableDocumentsAndHistoryInkSurviveRestart() throws Exception {
        PdfPersistenceSmoke.run();
    }
}
