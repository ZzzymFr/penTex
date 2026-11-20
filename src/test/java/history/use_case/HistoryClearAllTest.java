package history.use_case;

import history.data_access.FileHistoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class HistoryClearAllTest {
    @TempDir Path dir;

    @Test void clearPermanentlyRemovesRecordsButNotUnrelatedFiles() throws Exception {
        var repo = new FileHistoryRepository(dir);
        var history = new HistoryInteractor(repo);
        Path keep = dir.resolve("my-notes.txt");
        Files.createDirectories(dir);
        Files.writeString(keep, "keep me");
        history.recordCalculation("1+1", "2");
        history.recordFormula("x^2");
        history.recordGraph("x", new byte[] {(byte)137, 80, 78, 71, 13, 10, 26, 10, 1, 2, 3}, 5, 5);
        AtomicInteger changes = new AtomicInteger();
        history.onChange(changes::incrementAndGet);
        assertEquals(3, history.clearAll());
        assertEquals(1, changes.get());
        assertTrue(history.list().isEmpty());
        assertTrue(new HistoryInteractor(new FileHistoryRepository(dir)).list().isEmpty());
        assertTrue(Files.exists(keep));
        assertFalse(history.undoDelete(), "Clear All must never be undoable");
        assertEquals(0, history.clearAll());
        assertEquals(1, changes.get());
        assertTrue(history.recordCalculation("3+3", "6"));
        assertEquals(1, history.list().size());
    }

    @Test void clearingAfterIndividualDeleteAlsoDropsOldUndoHistory() throws Exception {
        var history = new HistoryInteractor(new FileHistoryRepository(dir));
        history.recordFormula("x");
        String id = history.list().get(0).id();
        assertTrue(history.delete(id));
        history.recordFormula("y");
        history.clearAll();
        assertFalse(history.undoDelete());
        assertTrue(history.list().isEmpty());
    }
}
