package history.use_case;

import history.domain.HistoryEntry;
import java.io.IOException;
import java.util.List;

/** Storage boundary. Implementations must deduplicate by HistoryEntry.contentKey(). */
public interface HistoryRepository {
    /** @return true iff a new unique record was stored. */
    boolean saveIfAbsent(HistoryEntry entry) throws IOException;
    List<HistoryEntry> findAll() throws IOException;
    boolean deleteById(String id) throws IOException;
    /** Permanently remove the records owned by this repository (never unrelated files). */
    default int clearAll() throws IOException {
        int removed = 0;
        for (HistoryEntry record : findAll()) if (deleteById(record.id())) removed++;
        return removed;
    }
}
