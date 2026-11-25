package pdf_editor.view;

import pdf_editor.adapter.PdfEditorController;
import pdf_editor.domain.PdfDocument;
import pdf_editor.domain.SavedPdfDocument;
import pdf_editor.use_case.PdfPreviewGateway;
import pdf_editor.use_case.PdfWorkspace;
import view.glass.GlassDialogs;
import view.glass.GlassNotices;

import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Component;
import java.io.IOException;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Presentation coordinator. Capture on EDT, persist immutable snapshots on one ordered worker. */
public final class PdfWorkspaceDialogs {
    private final Component parent;
    private final PdfEditorController controller;
    private final PdfPageCanvas paper;
    private final PdfWorkspace workspace;
    private final PdfPreviewGateway previewer;
    private final Consumer<SavedPdfDocument> showDocument;
    private final Consumer<String> title;
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "pdf-document-storage"); thread.setDaemon(true); return thread;
    });
    private final Timer timer = new Timer(2000, e -> autosave());
    private UUID id = UUID.randomUUID();
    private String name = "Untitled document";
    private boolean named, busy, closed, recoveryFailed;
    private SavedPdfDocument lastQueued;
    private SavedPdfDocument lastNamed;
    private Future<?> recoveryWrite;

    public PdfWorkspaceDialogs(Component parent, PdfEditorController controller, PdfPageCanvas paper,
                               PdfWorkspace workspace, PdfPreviewGateway previewer,
                               Consumer<SavedPdfDocument> showDocument, Consumer<String> title) {
        this.parent = Objects.requireNonNull(parent); this.controller = Objects.requireNonNull(controller);
        this.paper = Objects.requireNonNull(paper); this.workspace = Objects.requireNonNull(workspace);
        this.previewer = Objects.requireNonNull(previewer); this.showDocument = Objects.requireNonNull(showDocument);
        this.title = Objects.requireNonNull(title);
    }
    public void start() {
        // Startup restores once, before user interaction; no timer until loading finishes.
        try {
            var recovery = workspace.recover();
            if (recovery.isPresent()) {
                var saved = recovery.get();
                boolean exists = workspace.list().stream().anyMatch(s -> s.id().equals(saved.id()));
                if (exists) {
                    try { lastNamed = workspace.open(saved.id()); }
                    catch (IOException corrupt) { exists = false; }
                }
                install(saved, exists);
                lastQueued = saved;
            }
        } catch (IOException | RuntimeException failure) {
            // Keep a failed recovery file until the user actually edits this new document.
            recoveryFailed = true;
            lastQueued = snapshot();
            System.err.println("PDF recovery failed: " + failure.getMessage());
            SwingUtilities.invokeLater(() -> {
                if (!closed && parent.isShowing()) GlassNotices.showError(parent, "PDF recovery failed", failure.getMessage());
            });
        }
        if (lastQueued == null) lastQueued = snapshot();
        timer.start();
    }
    private void install(SavedPdfDocument saved, boolean isNamed) {
        paper.finishInlineEdit();
        paper.resetInteractiveGraphs();
        controller.loadDocument(saved.document(), saved.page());
        id = saved.id(); name = saved.name(); named = isNamed;
        title.accept(name); showDocument.accept(saved);
    }
    private SavedPdfDocument snapshot() {
        paper.captureGraphViews();
        return workspace.snapshot(id, name, controller.documentSnapshot(), controller.currentPage(), paper.zoom());
    }
    private static boolean same(SavedPdfDocument a, SavedPdfDocument b) {
        if (a == null || b == null || !a.id().equals(b.id()) || !a.name().equals(b.name())
                || a.page() != b.page() || a.zoom() != b.zoom()) return false;
        return sameContent(a.document(), b.document());
    }
    private static boolean sameContent(PdfDocument x, PdfDocument y) {
        if (x.pageCount() != y.pageCount()) return false;
        for (int page = 0; page < x.pageCount(); page++) {
            if (!x.body(page).equals(y.body(page))) return false;
            var first = x.placements(page); var second = y.placements(page);
            if (first.size() != second.size()) return false;
            for (int i = 0; i < first.size(); i++) {
                var p = first.get(i); var q = second.get(i);
                if (p == q) continue;
                if (!p.id().equals(q.id()) || p.x() != q.x() || p.y() != q.y()
                        || p.width() != q.width() || p.height() != q.height()
                        || p.imageLayer() != q.imageLayer() || p.anchorMode() != q.anchorMode()
                        || p.bodyAnchorOffset() != q.bodyAnchorOffset() || p.isNote() != q.isNote()
                        || !Objects.equals(p.noteText(), q.noteText()) || !p.noteSpans().equals(q.noteSpans())
                        || p.noteStyle() != q.noteStyle() || p.noteSize() != q.noteSize()) return false;
                var m = p.material(); var n = q.material();
                if (m == n) continue;
                if (m == null || n == null || !m.id().equals(n.id()) || !m.title().equals(n.title())
                        || !m.formula().equals(n.formula()) || !m.result().equals(n.result())
                        || m.imageWidth() != n.imageWidth() || m.imageHeight() != n.imageHeight()
                        || !Objects.equals(m.graphDefinition(), n.graphDefinition())
                        || !java.util.Arrays.equals(m.imagePng(), n.imagePng())) return false;
            }
        }
        return true;
    }
    private void autosave() {
        if (closed || busy || paper.isEditingNote() || (recoveryWrite != null && !recoveryWrite.isDone())) return;
        SavedPdfDocument draft = snapshot();
        if (same(lastQueued, draft)) return;
        queueRecovery(draft);
    }
    private void queueRecovery(SavedPdfDocument draft) {
        lastQueued = draft; recoveryFailed = false;
        recoveryWrite = io.submit(() -> {
            try { workspace.saveRecovery(draft); }
            catch (IOException | RuntimeException failure) {
                SwingUtilities.invokeLater(() -> {
                    if (lastQueued == draft) lastQueued = null;
                    if (!closed && !recoveryFailed) {
                        recoveryFailed = true;
                        GlassNotices.showError(parent, "PDF autosave failed", failure.getMessage());
                    }
                });
            }
        });
    }
    public void save() { save(false); }
    public void saveAs() { save(true); }
    private void save(boolean copy) {
        if (closed || busy) return;
        paper.finishInlineEdit();
        String chosen = name;
        if (!named || copy) {
            chosen = GlassDialogs.input(parent, "Save PDF Document", "Name", name, "Save");
            if (chosen == null) return;
            chosen = chosen.strip();
            if (chosen.isBlank() || chosen.length() > 200) { GlassDialogs.warning(parent, "Enter a name (1–200 characters)."); return; }
        }
        UUID target = copy ? UUID.randomUUID() : id;
        String targetName = chosen;
        busy = true;
        // Capture the current range synchronously; for named saves also freeze the latest visual.
        paper.prepareGraphSnapshots(() -> {
            if (closed) return;
            SavedPdfDocument saved = workspace.snapshot(target, targetName, controller.documentSnapshot(),
                    controller.currentPage(), paper.zoom());
            io.submit(() -> {
                try {
                    byte[] thumbnail = createThumbnail(saved.document());
                    workspace.save(saved, thumbnail);
                    SwingUtilities.invokeLater(() -> {
                        if (closed) return;
                        id = target; name = targetName; named = true; lastNamed = saved;
                        title.accept(name); busy = false; queueRecovery(snapshot());
                        GlassNotices.showSuccess(parent, "Saved “" + name + "”");
                    });
                } catch (IOException | RuntimeException failure) {
                    SwingUtilities.invokeLater(() -> fail("Can't save PDF", failure));
                }
            });
        }, problem -> fail("Can't save PDF", new IOException(problem)));
    }
    private byte[] createThumbnail(PdfDocument source) throws IOException {
        PdfDocument first = new PdfDocument(); first.setBody(0, source.body(0));
        for (var p : source.placements(0)) first.add(0, p);
        var images = previewer.preview(first, 18);
        return images.isEmpty() ? null : images.get(0).png();
    }
    public void open() {
        if (closed || busy) return;
        paper.finishInlineEdit();
        // Keep named saves separate from the latest recovery; confirm before leaving edits.
        try {
            var entries = workspace.list();
            if (entries.isEmpty()) { GlassDialogs.info(parent, "No saved PDF documents yet."); return; }
            var choice = SavedPdfChooser.choose(parent, entries, this::deleteSaved);
            if (choice == null) return;
            if (choice.rename()) {
                renameSaved(choice.id());
                return;
            }
            SavedPdfDocument next = workspace.open(choice.id()); // validate before replacing any live state
            SavedPdfDocument current = snapshot();
            if ((lastNamed == null || !sameContent(lastNamed.document(), current.document())) && (!current.document().pages().stream().allMatch(java.util.List::isEmpty)
                    || hasBody(current.document()))) {
                if (!GlassDialogs.confirm(parent, "Open PDF Document", "Open another document without saving the current changes?", "Open")) return;
            }
            install(next, true); lastNamed = next; queueRecovery(snapshot());
        } catch (IOException | RuntimeException failure) { fail("Can't open PDF", failure); }
    }
    private void renameSaved(UUID selected) throws IOException {
        var old = workspace.open(selected);
        String chosen = GlassDialogs.input(parent, "Rename PDF Document", "Name", old.name(), "Rename");
        if (chosen == null) return;
        chosen = chosen.strip();
        if (chosen.isBlank() || chosen.length() > 200) { GlassDialogs.warning(parent, "Enter a name (1–200 characters)."); return; }
        var renamed = workspace.rename(selected, chosen);
        if (id.equals(selected)) {
            name = renamed.name(); named = true; lastNamed = renamed;
            title.accept(name); queueRecovery(snapshot());
        }
        GlassNotices.showSuccess(parent, "Renamed to “" + renamed.name() + "”");
    }

    private boolean deleteSaved(Component owner, pdf_editor.use_case.PdfDocumentRepository.Summary selected) {
        if (!GlassDialogs.confirm(owner, "Delete PDF Document",
                "Permanently delete “" + selected.name() + "”? This cannot be undone.", "Delete")) return false;
        try {
            workspace.delete(selected.id());
            if (id.equals(selected.id())) {
                // Keep the live work, but never silently recreate the deleted named save.
                id = UUID.randomUUID(); named = false; lastNamed = null;
                name = "Untitled document"; title.accept(name); queueRecovery(snapshot());
            }
            GlassNotices.showSuccess(parent, "Deleted “" + selected.name() + "”");
            return true;
        } catch (IOException | RuntimeException failure) {
            GlassNotices.showError(parent, "Can't delete PDF", failure.getMessage());
            return false;
        }
    }
    private static boolean hasBody(PdfDocument doc) {
        for (int p = 0; p < doc.pageCount(); p++) if (!doc.body(p).isEmpty()) return true;
        return false;
    }
    private void fail(String message, Exception failure) {
        busy = false;
        if (!closed) GlassNotices.showError(parent, message, failure.getMessage());
    }
    public void close() {
        if (closed) return;
        timer.stop(); paper.finishInlineEdit();
        SavedPdfDocument finalDraft = snapshot(); closed = true;
        // Ordered after any pending write; never let an older queued draft replace the final state.
        if (!recoveryFailed || !same(lastQueued, finalDraft)) {
            Future<?> finalWrite = io.submit(() -> {
                try { workspace.saveRecovery(finalDraft); }
                catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
            });
            try { finalWrite.get(); }
            catch (InterruptedException failure) { Thread.currentThread().interrupt(); }
            catch (ExecutionException failure) { System.err.println("PDF recovery save failed: " + failure.getCause()); }
        }
        io.shutdown();
    }
}
