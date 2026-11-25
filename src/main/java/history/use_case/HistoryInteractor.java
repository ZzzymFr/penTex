package history.use_case;

import history.domain.HistoryEntry;
import java.io.IOException;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/** US5: auto-save, deduplication, browse and delete; independent of Swing and files. */
public final class HistoryInteractor {
    private final HistoryRepository repository;
    private final Clock clock;
    private java.time.Instant lastRecordedAt;
    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final java.util.Deque<HistoryEntry> deleted = new java.util.ArrayDeque<>();

    public Runnable onChange(Runnable listener) {
        listeners.add(Objects.requireNonNull(listener));
        return () -> listeners.remove(listener);
    }
    private void changed() { for (Runnable listener : listeners) listener.run(); }
    public HistoryInteractor(HistoryRepository repository) { this(repository, Clock.systemUTC()); }
    public HistoryInteractor(HistoryRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository);
        this.clock = Objects.requireNonNull(clock);
    }

    public synchronized boolean recordFormula(String latex) throws IOException {
        if (latex == null || latex.isBlank()) return false;
        String formula = latex.trim();
        // A calculation already containing this formula is more informative than a formula-only copy.
        // When only a formula is available, promote the most recent matching calculation.
        for (HistoryEntry previous : list()) {
            if (previous.kind() == HistoryEntry.Kind.CALCULATION && previous.formula().equals(formula)) {
                repository.saveOrRefresh(previous.revisitedAt(nextTimestamp()));
                changed();
                return false;
            }
        }
        boolean saved = repository.saveOrRefresh(newEntry(HistoryEntry.Kind.FORMULA, "Formula", formula, "", null, 0, 0));
        changed();
        return saved;
    }

    public synchronized boolean recordCalculation(String latex, String result) throws IOException {
        if (latex == null || latex.isBlank()) return false;
        String formula = latex.trim();
        if (result == null || result.isBlank()) return recordFormula(formula);
        HistoryEntry entry = newEntry(HistoryEntry.Kind.CALCULATION, "Calculation", formula,
                result.trim(), null, 0, 0);
        boolean saved = repository.saveOrRefresh(entry);
        // Upgrade earlier formula-only records, including when this calculation already existed.
        for (HistoryEntry previous : repository.findAll()) {
            if (previous.kind() == HistoryEntry.Kind.FORMULA && previous.formula().equals(formula))
                repository.deleteById(previous.id());
        }
        changed();
        return saved;
    }

    /** A thumbnail is retained for History; the optional definition restores a live plot. */
    public boolean recordGraph(String source, byte[] png, int width, int height) throws IOException {
        return recordGraph(source, png, width, height, null);
    }
    public synchronized boolean recordGraph(String source, byte[] png, int width, int height,
                               String graphDefinition) throws IOException {
        if (png == null) return false;
        boolean saved = repository.saveOrRefresh(new HistoryEntry(
                UUID.randomUUID().toString(), HistoryEntry.Kind.GRAPH, "Graph",
                source == null ? "" : source.trim(), "", png, width, height,
                nextTimestamp(), graphDefinition));
        changed();
        return saved;
    }

    /** Persist a reusable handwritten selection with its own kind and content key. */
    public synchronized boolean recordInk(String title, byte[] png, int width, int height) throws IOException {
        String label = title == null || title.isBlank() ? "Handwritten notes" : title.strip();
        boolean saved = repository.saveOrRefresh(new HistoryEntry(UUID.randomUUID().toString(),
                HistoryEntry.Kind.INK, label, "", "", png, width, height, nextTimestamp()));
        changed();
        return saved;
    }

    /** Existing material already matched by an adapter (for example a native graph with a new thumbnail). */
    public synchronized boolean refresh(String id) throws IOException {
        HistoryEntry existing = find(id);
        if (existing == null) return false;
        repository.saveOrRefresh(existing.revisitedAt(nextTimestamp()));
        changed();
        return true;
    }

    public List<HistoryEntry> list() throws IOException {
        return repository.findAll().stream()
                .sorted(Comparator.comparing(HistoryEntry::createdAt).reversed()
                        .thenComparing(HistoryEntry::id))
                .toList();
    }
    public HistoryEntry find(String id) throws IOException {
        if (id == null) return null;
        return list().stream().filter(record -> record.id().equals(id)).findFirst().orElse(null);
    }
    public synchronized boolean delete(String id) throws IOException {
        HistoryEntry item = find(id);
        if (item == null || !repository.deleteById(id)) return false;
        deleted.push(item);
        while (deleted.size() > 30) deleted.removeLast();
        changed();
        return true;
    }
    /** Restore the most recently user-deleted record, preserving its identity and timestamp. */
    public synchronized boolean undoDelete() throws IOException {
        if (deleted.isEmpty()) return false;
        HistoryEntry item = deleted.peek();
        if (!repository.saveIfAbsent(item)) return false;
        deleted.pop();
        changed();
        return true;
    }

    /** Explicit destructive operation. No undo stack survives a confirmed clear. */
    public synchronized int clearAll() throws IOException {
        int removed;
        try {
            removed = repository.clearAll();
        } finally {
            // A failed/partial clear must not offer to restore stale pre-clear records.
            deleted.clear();
        }
        if (removed > 0) changed();
        return removed;
    }

    /** Automatic source invalidation is separate from explicit user deletion/Undo delete. */
    synchronized HistoryEntry suspendLinked(String id) throws IOException {
        HistoryEntry current = find(id);
        if (current == null || !repository.deleteById(id)) return null;
        changed(); return current;
    }
    synchronized boolean restoreLinked(HistoryEntry entry) throws IOException {
        if (!repository.saveIfAbsent(Objects.requireNonNull(entry))) return false;
        changed(); return true;
    }

    /** Break same-clock ties deterministically and initialize from persisted records after restart. */
    private java.time.Instant nextTimestamp() throws IOException {
        if (lastRecordedAt == null) lastRecordedAt = repository.findAll().stream()
                .map(HistoryEntry::createdAt).max(java.util.Comparator.naturalOrder()).orElse(null);
        java.time.Instant now = clock.instant();
        if (lastRecordedAt != null && !now.isAfter(lastRecordedAt)) now = lastRecordedAt.plusNanos(1);
        lastRecordedAt = now;
        return now;
    }

    private HistoryEntry newEntry(HistoryEntry.Kind kind, String title, String formula, String result,
                                  byte[] png, int width, int height) throws IOException {
        return new HistoryEntry(UUID.randomUUID().toString(), kind, title, formula, result,
                png, width, height, nextTimestamp());
    }
}
