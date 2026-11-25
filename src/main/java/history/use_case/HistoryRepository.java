package history.use_case;

import history.domain.HistoryEntry;
import java.io.IOException;
import java.util.List;

/** Storage boundary. Implementations must deduplicate by HistoryEntry.contentKey(). */
public interface HistoryRepository {
    /** @return true iff a new unique record was stored. */
    boolean saveIfAbsent(HistoryEntry entry) throws IOException;
    /** Record an explicit reuse, preserving the existing ID. Returns true only for a new record.
     * Durable implementations should replace an existing record atomically.
     */
    default boolean saveOrRefresh(HistoryEntry entry) throws IOException {
        if (saveIfAbsent(entry)) return true;
        for (HistoryEntry old : findAll()) {
            if (!old.contentKey().equals(entry.contentKey())) continue;
            if (!entry.createdAt().isAfter(old.createdAt())) return false;
            if (!deleteById(old.id())) throw new IOException("History record disappeared");
            try {
                if (!saveIfAbsent(old.revisitedAt(entry.createdAt())))
                    throw new IOException("Could not refresh history record");
            } catch (IOException | RuntimeException failure) {
                try { saveIfAbsent(old); } catch (IOException | RuntimeException restore) { failure.addSuppressed(restore); }
                throw failure;
            }
            return false;
        }
        return false;
    }
    List<HistoryEntry> findAll() throws IOException;
    boolean deleteById(String id) throws IOException;
    /** Permanently remove the records owned by this repository (never unrelated files). */
    default int clearAll() throws IOException {
        int removed = 0;
        for (HistoryEntry record : findAll()) if (deleteById(record.id())) removed++;
        return removed;
    }
}
