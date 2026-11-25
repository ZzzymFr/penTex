package pdf_editor.view;

import entity.graph.GraphDefinition;
import interface_adapter.graph.GraphViewModel;
import pdf_editor.adapter.PdfEditorController;
import pdf_editor.domain.PdfDocument;
import view.canvas.GraphSession;
import view.graph.GraphCard;
import view.graph.PlotCard;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The PDF view's native interactive graph adapter. Domain placements remain ordinary
 * independently resizable/movable media; this class owns the Swing graph cards and
 * converts their current view into a high-resolution snapshot for PDFBox.
 *
 * A thumbnail can never be mistaken for the graph's source. Graph sessions are
 * independent per placement, including copies of the same Library material.
 */
final class InteractiveGraphLayer {
    private static final int MAX_RENDER_SIDE = 3200;
    private static final ExecutorService CALCULATE = Executors.newCachedThreadPool(task -> {
        Thread worker = new Thread(task, "pdf-interactive-graph");
        worker.setDaemon(true);
        return worker;
    });
    private final PdfPageCanvas page;
    private final PdfEditorController controller;
    private final Supplier<GraphSession> sessions;
    private final Map<String, Live> live = new LinkedHashMap<>();

    InteractiveGraphLayer(PdfPageCanvas page, PdfEditorController controller,
                          Supplier<GraphSession> sessions) {
        this.page = Objects.requireNonNull(page);
        this.controller = Objects.requireNonNull(controller);
        this.sessions = Objects.requireNonNull(sessions);
    }

    /** Reposition on selection, zoom, page changes and document edits. */
    void refresh() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::refresh);
            return;
        }
        for (PdfDocument.Placement placement : controller.placements()) {
            if (placement.isNote() || !placement.material().isInteractiveGraph()) continue;
            Live entry = live.computeIfAbsent(placement.id(),
                    id -> new Live(id, placement.material().graphDefinition()));
            boolean selected = controller.isSelected(placement.id()) && !page.isTextEditorShowing();
            if (selected && entry.ready) {
                Rectangle bounds = page.graphScreenBounds(placement);
                if (!entry.card.getBounds().equals(bounds)) entry.card.setBounds(bounds);
                if (!entry.card.isVisible()) entry.card.setVisible(true);
            } else if (entry.card.isVisible()) entry.card.setVisible(false);
        }
        for (Live entry : live.values()) {
            PdfDocument.Placement placement = currentPlacement(entry.id);
            if (placement == null && entry.card.isVisible()) entry.card.setVisible(false);
        }
    }

    /** The actual visible live GraphCard, if one is selected on this PDF page. */
    PdfDocument.Placement visiblePlacement() {
        for (Live entry : live.values()) {
            if (entry.card.isVisible()) {
                PdfDocument.Placement current = currentPlacement(entry.id);
                if (current != null) return current;
            }
        }
        return null;
    }

    /** Include graphs on other pages in the export, not just the currently visible one. */
    private void ensureDocumentGraphs() {
        PdfDocument document = controller.documentSnapshot();
        for (List<PdfDocument.Placement> placements : document.pages()) {
            for (PdfDocument.Placement item : placements) {
                if (!item.isNote() && item.material().isInteractiveGraph())
                    live.computeIfAbsent(item.id(),
                            id -> new Live(id, item.material().graphDefinition()));
            }
        }
    }

    private PdfDocument.Placement currentPlacement(String id) {
        for (PdfDocument.Placement placement : controller.placements())
            if (placement.id().equals(id)) return placement;
        return null;
    }
    private PdfDocument.Placement anyPlacement(String id) {
        for (List<PdfDocument.Placement> items : controller.documentSnapshot().pages())
            for (PdfDocument.Placement p : items) if (id.equals(p.id())) return p;
        return null;
    }

    /** Freeze every current 2D/3D view at PDF output resolution before exporting/previewing.
     * Callback always executes on EDT; an error never exports a stale, misleading picture.
     */
    void prepareSnapshots(Runnable success, Consumer<String> failure) {
        ensureDocumentGraphs();
        List<Live> entries = new ArrayList<>();
        for (Live entry : live.values()) if (anyPlacement(entry.id) != null) entries.add(entry);
        if (entries.isEmpty()) { success.run(); return; }
        prepareNext(entries, 0, success, failure);
    }
    private void prepareNext(List<Live> entries, int i, Runnable success, Consumer<String> failure) {
        if (i >= entries.size()) { success.run(); return; }
        Live entry = entries.get(i);
        if (entry.error != null) { failure.accept(entry.error); return; }
        if (!entry.ready) {
            entry.whenReady.add(() -> prepareNext(entries, i, success, failure));
            return;
        }
        PdfDocument.Placement placement = anyPlacement(entry.id);
        if (placement == null) { prepareNext(entries, i+1, success, failure); return; }
        int width = Math.max(8, Math.min(MAX_RENDER_SIDE, Math.round(placement.width() * 4)));
        int height = Math.max(8, Math.min(MAX_RENDER_SIDE, Math.round(placement.height() * 4)));
        // Discard any older low-resolution hover/rotation callback.  Export must
        // be exactly the image frozen for this request, not a late stale frame.
        int revision = ++entry.renderRevision;
        entry.card.snapshotLater(width, height, image -> {
            if (revision != entry.renderRevision) {
                failure.accept("Graph changed during export; please export again");
                return;
            }
            if (image == null) { failure.accept("Graph renderer returned no image"); return; }
            controller.updateGraphSnapshot(entry.id, png(image), image.getWidth(), image.getHeight());
            prepareNext(entries, i+1, success, failure);
        });
    }

    private static byte[] png(BufferedImage image) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            if (!ImageIO.write(image, "png", bytes)) throw new IOException("PNG encoder unavailable");
            return bytes.toByteArray();
        } catch (IOException failure) { throw new IllegalStateException("Graph snapshot failed", failure); }
    }

    private final class Live implements PlotCard.Host {
        final String id;
        final GraphCard card;
        final GraphSession session;
        final List<Runnable> whenReady = new ArrayList<>();
        boolean ready;
        String error;
        boolean transforming;
        int renderRevision;
        Live(String id, GraphDefinition definition) {
            this.id = id;
            session = sessions.get();
            card = new GraphCard(session.viewModel(), session.explore());
            card.setHost(this);
            card.addMouseListener(new MouseAdapter() {
                private boolean popupOnPress;
                @Override public void mousePressed(MouseEvent e) {
                    popupOnPress = e.isPopupTrigger();
                    if (popupOnPress) page.graphContextMenu(id, e);
                }
                @Override public void mouseReleased(MouseEvent e) {
                    if (e.isPopupTrigger() && !popupOnPress) page.graphContextMenu(id, e);
                    popupOnPress = false;
                }
            });
            page.add(card, 0);
            session.viewModel().addPropertyChangeListener(event -> {
                if (!GraphViewModel.STATE_PROPERTY.equals(event.getPropertyName())) return;
                SwingUtilities.invokeLater(() -> {
                    if (session.viewModel().getState().getError() != null)
                        error = session.viewModel().getState().getError();
                    else if (session.viewModel().getState().hasGraph()) {
                        card.restoreCamera(definition.camera());
                        ready = true;
                        refresh();
                        List<Runnable> callbacks = List.copyOf(whenReady);
                        whenReady.clear();
                        callbacks.forEach(Runnable::run);
                    }
                    if (error != null) {
                        List<Runnable> callbacks = List.copyOf(whenReady);
                        whenReady.clear();
                        callbacks.forEach(Runnable::run);
                    }
                });
            });
            CALCULATE.execute(() -> {
                try {
                    session.controller().execute(definition.functions(), definition.xPair(),
                            definition.yPair(), definition.zPair());
                } catch (RuntimeException bad) {
                    SwingUtilities.invokeLater(() -> {
                        error = bad.getMessage() == null ? bad.toString() : bad.getMessage();
                        List<Runnable> callbacks = List.copyOf(whenReady);
                        whenReady.clear();
                        callbacks.forEach(Runnable::run);
                    });
                }
            });
        }
        private PdfDocument.Placement placement() { return currentPlacement(id); }
        private void begin() {
            if (!transforming) {
                controller.select(id);
                controller.beginTransform();
                transforming = true;
            }
        }
        @Override public void moveBy(double dx, double dy) {
            PdfDocument.Placement p = placement();
            if (p == null) return;
            begin();
            controller.transform((float)(p.x() + dx / page.zoom()),
                    (float)(p.y() + dy / page.zoom()), p.width(), p.height());
            refresh(); page.repaint();
        }
        @Override public double minimumWidth() { return 90 * page.zoom(); }
        @Override public double minimumHeight() { return 70 * page.zoom(); }
        @Override public void resizeTo(double w, double h) {
            PdfDocument.Placement p = placement();
            if (p == null) return;
            begin();
            controller.transform(p.x(), p.y(), (float)(w / page.zoom()), (float)(h / page.zoom()));
            refresh(); page.repaint();
        }
        @Override public void close() {
            controller.select(id);
            controller.deleteSelected();
            refresh(); page.repaint();
        }
        @Override public void viewChanged() {
            transforming = false;
            // A view change is not a PDF placement edit; refresh its static fallback separately.
            if (ready && placement() != null) {
                PdfDocument.Placement item = placement();
                int width = Math.max(8, Math.min(MAX_RENDER_SIDE, Math.round(item.width() * 3)));
                int height = Math.max(8, Math.min(MAX_RENDER_SIDE, Math.round(item.height() * 3)));
                int token = ++renderRevision;
                card.snapshotLater(width, height, picture -> {
                    if (token == renderRevision && picture != null) {
                        controller.updateGraphSnapshot(id, png(picture), picture.getWidth(), picture.getHeight());
                        page.repaint();
                    }
                });
            }
        }
    }
}
