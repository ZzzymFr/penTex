package view.board;

import org.junit.jupiter.api.Test;

class HistoryDragReleaseRegressionTest {
    @Test void rowAndExternalReleaseCompleteExactlyOnce() throws Exception {
        HistoryDragReleaseSmoke.verify();
    }
}
