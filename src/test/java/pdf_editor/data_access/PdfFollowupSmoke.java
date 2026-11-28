package pdf_editor.data_access;

import history.data_access.FileHistoryRepository;
import history.domain.HistoryEntry;
import history.use_case.HistoryInteractor;
import pdf_editor.MaterialStore;
import pdf_editor.adapter.PdfEditorController;
import pdf_editor.domain.PdfDocument;
import pdf_editor.domain.PdfMaterial;
import pdf_editor.use_case.AutoLayoutInteractor;
import pdf_editor.use_case.DocumentEditor;
import pdf_editor.use_case.PdfExportInteractor;
import pdf_editor.use_case.PdfWorkspace;
import pdf_editor.view.PdfPageCanvas;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** Standalone regression for the follow-up patch, also called by JUnit. */
public final class PdfFollowupSmoke {
    private PdfFollowupSmoke() { }
    public static void main(String[] args) throws Exception {
        persistenceAndRecency();
        screenRasterAndGhost();
        System.out.println("PDF follow-up: History recency, rename/delete, date, and raster/ghost reuse passed");
    }
    public static void persistenceAndRecency() throws Exception {
        Path temp = Files.createTempDirectory("pentex-followup-");
        try {
            Instant now = Instant.parse("2026-11-25T04:16:00Z");
            Clock clock = Clock.fixed(now, ZoneOffset.UTC);
            var files = new FileHistoryRepository(temp.resolve("history"));
            var history = new HistoryInteractor(files, clock);
            AtomicInteger notices = new AtomicInteger();
            history.onChange(notices::incrementAndGet);
            check(history.recordCalculation("3+2", "5"), "First calculation inserted");
            var original = history.list().get(0);
            check(history.recordCalculation("4+4", "8"), "Another calculation inserted");
            check(!history.recordCalculation(" 3+2 ", " 5 "), "Repeat is not a new record");
            check(history.list().size() == 2, "No duplicate row");
            check(history.list().get(0).id().equals(original.id()), "Repeat moves existing ID to top");
            check(history.list().get(0).createdAt().isAfter(original.createdAt()), "Recent timestamp refreshed");
            check(notices.get() == 3, "Repeat notifies History presenter");
            var afterRestart = new HistoryInteractor(new FileHistoryRepository(files.directory()), clock);
            check(afterRestart.list().get(0).id().equals(original.id()), "Recency survives restart");
            check(!afterRestart.recordCalculation("4+4", "8"), "Reopened duplicate is still not newly inserted");
            check(afterRestart.list().get(0).formula().equals("4+4"), "Repeated record moves ahead after restart even with equal clock");
            check(!afterRestart.recordFormula("3+2"), "Existing calculation preferred to formula-only duplicate");
            check(afterRestart.list().get(0).id().equals(original.id()), "Formula-only repeat promotes matching calculation");
            check(!files.saveIfAbsent(original), "Original insertion-only repository API unchanged");
            byte[] png = png();
            check(afterRestart.recordInk("Notes", png, 80, 40), "Ink inserted");
            String inkId = afterRestart.list().get(0).id();
            check(afterRestart.recordGraph("x", png, 80, 40), "Graph inserted separately");
            check(!afterRestart.recordInk("Notes", png, 80, 40), "Identical ink deduplicated");
            check(afterRestart.list().get(0).id().equals(inkId), "Identical ink promoted with original ID");
            check(!afterRestart.recordGraph("x", png, 80, 40), "Identical graph deduplicated");
            check(afterRestart.list().get(0).kind() == HistoryEntry.Kind.GRAPH, "Identical graph promoted");
            check(afterRestart.list().size() == 4, "Four unique records remain");
            // The Canvas lifecycle must restore the latest timestamp, not the one before a repeat.
            var linkedHistory = new HistoryInteractor(new FileHistoryRepository(temp.resolve("linked")), clock);
            var lifecycle = new history.use_case.CanvasHistoryLifecycle(linkedHistory);
            lifecycle.recordCalculation(1L, "3+2", "5");
            linkedHistory.recordCalculation("4+4", "8");
            lifecycle.recordCalculation(2L, "3+2", "5");
            var recentlyUsed = linkedHistory.list().get(0);
            lifecycle.synchronize(java.util.Set.of());
            lifecycle.synchronize(java.util.Set.of(1L));
            check(linkedHistory.list().get(0).id().equals(recentlyUsed.id())
                    && linkedHistory.list().get(0).createdAt().equals(recentlyUsed.createdAt()),
                    "Canvas undo preserves the latest timestamp");

            // A repeated native-graph handoff uses the existing definition match, even with a different thumbnail.
            var store = new MaterialStore();
            new pdf_editor.adapter.MathLabCollector(store, afterRestart);
            var definition = new entity.graph.GraphDefinition(List.of("y=x"),
                    new entity.graph.GraphDefinition.Range(-5, 5), new entity.graph.GraphDefinition.Range(-5, 5),
                    null, new entity.graph.GraphDefinition.Camera(0, 0));
            store.archive(store.addInteractiveGraph(definition, ImageIO.read(new java.io.ByteArrayInputStream(png))));
            var nativeRecord = afterRestart.list().get(0);
            afterRestart.recordFormula("x+7");
            store.archive(store.addInteractiveGraph(definition, new BufferedImage(160, 80, BufferedImage.TYPE_INT_ARGB)));
            check(afterRestart.list().get(0).id().equals(nativeRecord.id()), "Repeated graph definition moves existing row to top");
            check(afterRestart.list().stream().filter(e -> e.graphDefinition() != null).count() == 1,
                    "Native graph handoff does not duplicate thumbnails");


            var directory = temp.resolve("pdf");
            var workspace = new PdfWorkspace(new FilePdfDocumentRepository(directory), clock);
            var saved = new PdfDocument(); saved.addPage();
            saved.add(1, PdfDocument.Placement.note("Saved content", 30, 40, 100, 70));
            UUID id = UUID.randomUUID();
            workspace.save(workspace.snapshot(id, "Original", saved, 1, 2.0), png);
            var draft = saved.copy(); draft.add(0, PdfDocument.Placement.note("Unsaved", 5, 5, 20, 20));
            workspace.saveRecovery(workspace.snapshot(id, "Original", draft, 0, 1.5));
            var otherId = UUID.randomUUID();
            workspace.save(workspace.snapshot(otherId, "Other", new PdfDocument(), 0, 1), null);
            var renamed = workspace.rename(id, "  Renamed 中文  ");
            check(renamed.id().equals(id) && renamed.name().equals("Renamed 中文"), "Rename retains identity and Unicode name");
            check(renamed.page() == 1 && renamed.zoom() == 2.0, "Rename retains view metadata");
            check(renamed.document().placements(0).isEmpty(), "Rename does not overwrite saved content with draft");
            check(renamed.document().placements(1).get(0).noteText().equals("Saved content"), "Rename retains document content");
            var reopened = new PdfWorkspace(new FilePdfDocumentRepository(directory), clock);
            check(reopened.open(id).name().equals("Renamed 中文"), "Rename persisted");
            var summary = reopened.list().stream().filter(s -> s.id().equals(id)).findFirst().orElseThrow();
            check(Arrays.equals(summary.thumbnail(), png) && summary.pages() == 2, "Rename keeps thumbnail and page count");
            try { reopened.rename(id, "  "); throw new AssertionError("Blank rename accepted"); }
            catch (IllegalArgumentException expected) { }
            reopened.delete(id);
            check(reopened.list().size() == 1 && reopened.list().get(0).id().equals(otherId), "Only selected named record deleted");
            check(reopened.recover().orElseThrow().document().placements(0).get(0).noteText().equals("Unsaved"), "Named deletion leaves live recovery draft intact");
        } finally {
            try (var paths = Files.walk(temp)) {
                for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
            }
        }
    }

    public static void screenRasterAndGhost() throws Exception {
        var date = Class.forName("pdf_editor.view.SavedPdfChooser").getDeclaredMethod("formatModifiedAt", Instant.class, ZoneId.class);
        date.setAccessible(true);
        check(date.invoke(null, Instant.parse("2026-11-25T04:16:00Z"), ZoneId.of("America/Toronto"))
                .equals("2026-11-24  23:16"), "Locale-neutral numeric date with local timezone");
        SwingUtilities.invokeAndWait(() -> {
            try {
                var cache = new PdfScreenRasterCache(4096);
                Object source = new Object();
                AtomicInteger renders = new AtomicInteger();
                java.util.function.Supplier<BufferedImage> render = () -> {
                    renders.incrementAndGet(); return new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
                };
                cache.duringPaint(() -> {
                    var a = PdfScreenRasterCache.render(source, 16, 16, render);
                    for (int i = 0; i < 100; i++) check(a == PdfScreenRasterCache.render(source, 16, 16, render), "Stable raster reused");
                    check(renders.get() == 1, "100 redraws render once");
                    PdfScreenRasterCache.render(source, 20, 20, render);
                    check(renders.get() == 2, "Resolution change invalidates key");
                    PdfScreenRasterCache.render(new Object(), 16, 16, render);
                    check(renders.get() == 3, "New content invalidates key");
                });
                PdfScreenRasterCache.render(source, 16, 16, render);
                check(renders.get() == 4, "Outside screen scope uses uncached original renderer");
                cache.clear();
                cache.duringPaint(() -> PdfScreenRasterCache.render(source, 16, 16, render));
                check(renders.get() == 5, "Explicit invalidation releases old rasters");
                // Real Java2D raster painting of a zoomed PDF page, with a graph/image and a rich note.
                var library = new MaterialStore();
                var editor = new DocumentEditor();
                var controller = new PdfEditorController(library, editor, new AutoLayoutInteractor(editor),
                        new PdfExportInteractor((doc, path) -> {}, (doc, dpi) -> List.of()));
                var material = PdfMaterial.restore("image", "Ink", "", "", png(), 80, 40, null);
                controller.place(material, 40, 45, 280, 140);
                editor.addNote(0, "Visible note", 40, 210);
                var page = new PdfPageCanvas(controller, new PdfPageCanvas.Listener() {
                    public void contextMenu(MouseEvent e) { }
                    public void editNote() { }
                    public void editFormula() { }
                });
                page.setZoom(3); page.setSize(page.getPreferredSize());
                var frame = new BufferedImage(1200, 900, BufferedImage.TYPE_INT_ARGB);
                paint(page, frame);
                var cacheField = PdfPageCanvas.class.getDeclaredField("screenRasters"); cacheField.setAccessible(true);
                var rastersField = PdfScreenRasterCache.class.getDeclaredField("rasters"); rastersField.setAccessible(true);
                Map<?,?> rasters = (Map<?,?>) rastersField.get(cacheField.get(page));
                check(rasters.size() >= 2, "Zoomed real page populated cache for image and note");
                var firstRasters = List.copyOf(rasters.values());
                for (int i = 0; i < 20; i++) paint(page, frame);
                check(rasters.size() == firstRasters.size() && rasters.values().containsAll(firstRasters), "Zoomed page reuses real raster objects on repeated paint");
                page.setZoom(2); page.setSize(page.getPreferredSize());
                check(rasters.isEmpty(), "Zoom change drops old screen rasters");
                paint(page, frame);
                check(rasters.size() >= 2 && rasters.values().stream().noneMatch(firstRasters::contains), "New zoom uses fresh high-resolution raster");

                // Test the shared History/Canvas-graph overlay, not native OS drag delivery.
                Class<?> ghostType = Class.forName("view.board.BoardWindow$Ghost");
                var constructor = ghostType.getDeclaredConstructor(); constructor.setAccessible(true);
                Object ghost = constructor.newInstance();
                var show = ghostType.getDeclaredMethod("showGraphPreview", BufferedImage.class, Rectangle.class, Rectangle.class); show.setAccessible(true);
                var scaled = ghostType.getDeclaredField("scaledGraphGhost"); scaled.setAccessible(true);
                BufferedImage thumbnail = ImageIO.read(new java.io.ByteArrayInputStream(png()));
                Rectangle clip = new Rectangle(0, 0, 1200, 900);
                show.invoke(ghost, thumbnail, new Rectangle(10, 10, 840, 420), clip);
                BufferedImage first = (BufferedImage) scaled.get(ghost);
                check(first.getWidth() == 840 && first.getHeight() == 420, "Preview retains placement dimensions at 3x zoom");
                int alpha = first.getRGB(400, 200) >>> 24;
                check(alpha >= 135 && alpha <= 145, "Preview translucent, not full opacity");
                for (int i = 0; i < 100; i++) show.invoke(ghost, thumbnail, new Rectangle(i, i, 840, 420), clip);
                check(first == scaled.get(ghost), "100 cursor moves reuse one pre-scaled translucent image");
                show.invoke(ghost, thumbnail, new Rectangle(20, 20, 560, 280), clip);
                check(first != scaled.get(ghost), "Changed zoom/size rerenders ghost");
                show.invoke(ghost, null, null, null);
                check(scaled.get(ghost) == null, "Drag cancellation releases preview");
            } catch (Exception failure) { throw new IllegalStateException(failure); }
        });
    }
    private static void paint(PdfPageCanvas page, BufferedImage frame) {
        Graphics2D g = frame.createGraphics();
        try { g.setClip(0, 0, frame.getWidth(), frame.getHeight()); page.paint(g); }
        finally { g.dispose(); }
    }
    private static byte[] png() throws java.io.IOException {
        var image = new BufferedImage(80, 40, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try { g.setColor(Color.BLUE); g.fillRect(0, 0, 80, 40); }
        finally { g.dispose(); }
        var bytes = new ByteArrayOutputStream(); ImageIO.write(image, "png", bytes); return bytes.toByteArray();
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
