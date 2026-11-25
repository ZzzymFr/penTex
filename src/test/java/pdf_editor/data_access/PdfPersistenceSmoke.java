package pdf_editor.data_access;

import entity.graph.GraphDefinition;
import history.adapter.HistoryBoardPresenter;
import history.data_access.FileHistoryRepository;
import history.domain.HistoryEntry;
import history.use_case.HistoryInteractor;
import interface_adapter.history.HistoryViewModel;
import pdf_editor.MaterialStore;
import pdf_editor.adapter.HistoryMaterialImporter;
import pdf_editor.adapter.MathLabCollector;
import pdf_editor.domain.PdfDocument;
import pdf_editor.domain.PdfMaterial;
import pdf_editor.use_case.DocumentEditor;
import pdf_editor.use_case.PdfWorkspace;
import pdf_editor.use_case.SelectionToHistory;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Runnable dependency-free regression checks, also invoked by JUnit. */
public final class PdfPersistenceSmoke {
    private PdfPersistenceSmoke() { }
    public static void main(String[] args) throws Exception {
        run();
        System.out.println("PDF persistence and History ink regression checks passed");
    }
    public static void run() throws Exception {
        Path temp = Files.createTempDirectory("pentex-pdf-regression-");
        try {
            var image = new BufferedImage(40, 25, BufferedImage.TYPE_INT_ARGB);
            image.setRGB(3, 4, 0xff123456);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            ImageIO.write(image, "png", bytes);
            byte[] png = bytes.toByteArray();
            var library = new MaterialStore();
            var historyPath = temp.resolve("history");
            var history = new HistoryInteractor(new FileHistoryRepository(historyPath));
            var collector = new MathLabCollector(library, history);
            var body = new PdfDocument.NoteSpan("Résumé ∑", true, true, false, 17, "Dialog");
            var graph = new GraphDefinition(List.of("z=x^2+y^2"), new GraphDefinition.Range(-3, 4),
                    new GraphDefinition.Range(-5, 6), new GraphDefinition.Range(-7, 8),
                    new GraphDefinition.Camera(.4, -.2));
            var nativeMaterial = PdfMaterial.restore("native-id", "Plot", "", "", png, 40, 25, graph);
            var formula = library.addCalculation("x+1", "2");
            var ink = library.addInk("Canvas selection", image);
            library.addInk("Canvas selection", image);
            var selection = new SelectionToHistory(ignored -> "y+2", library, collector::recordSelectedFormula);
            selection.addFormula("y+2");
            selection.addCalculation("y+2", "3");
            var reopenedHistory = new HistoryInteractor(new FileHistoryRepository(historyPath));
            var historyEntries = reopenedHistory.list();
            check(historyEntries.size() == 2, "Ink deduplication and formula upgrade");
            var inkEntry = historyEntries.stream().filter(e -> e.kind() == HistoryEntry.Kind.INK).findFirst().orElseThrow();
            check(Arrays.equals(inkEntry.png(), ink.imagePng()), "Persisted handwriting pixels");
            var model = new HistoryViewModel();
            SwingUtilities.invokeAndWait(() -> new HistoryBoardPresenter(reopenedHistory, model).refresh());
            var item = model.getItems().stream().filter(i -> i.isInk()).findFirst().orElseThrow();
            check(item.hasImage() && !item.isGraph(), "Ink is image-backed but is not a native graph");
            var imported = HistoryMaterialImporter.importRecord(item, library);
            check(Arrays.equals(imported.imagePng(), ink.imagePng()), "History ink restored as image");
            check(history.list().size() == 2, "Import does not republish History");
            check(imported == HistoryMaterialImporter.importRecord(item, library), "Reuse does not duplicate cached material");
            check(imported.id().equals(inkEntry.id()), "Imported material keeps persistent History source ID");
            var live = library.addInteractiveGraph(graph, image);
            library.archive(live);
            var anotherThumbnail = new BufferedImage(80, 50, BufferedImage.TYPE_INT_ARGB);
            library.archive(library.addInteractiveGraph(graph, anotherThumbnail));
            check(history.list().stream().filter(e -> e.kind() == HistoryEntry.Kind.GRAPH).count() == 1,
                    "Repeated native graph handoff with different thumbnail is deduplicated");
            var graphEntry = history.list().stream().filter(e -> e.kind() == HistoryEntry.Kind.GRAPH).findFirst().orElseThrow();
            var graphItem = new interface_adapter.history.HistoryItem(graphEntry);
            check(HistoryMaterialImporter.importRecord(graphItem, library).graphDefinition().equals(graph),
                    "History import restores native graph definition, not just PNG");
            var editing = new DocumentEditor();
            var controller = new pdf_editor.adapter.PdfEditorController(library, editing,
                    new pdf_editor.use_case.AutoLayoutInteractor(editing),
                    new pdf_editor.use_case.PdfExportInteractor((d, p) -> {}, (d, dpi) -> List.of()));
            var archivedCalc = history.list().stream().filter(e -> e.kind() == HistoryEntry.Kind.CALCULATION).findFirst().orElseThrow();
            var importedCalc = HistoryMaterialImporter.importRecord(new interface_adapter.history.HistoryItem(archivedCalc), library);
            var first = controller.place(importedCalc, 20, 20, 100, 50);
            var second = controller.place(importedCalc, 30, 30, 100, 50);
            check(!first.id().equals(second.id()), "Repeated PDF imports create independent placements");
            int originalHistorySize = history.list().size();
            pdf_editor.adapter.CanvasHistoryImporter.importAt(new interface_adapter.history.CanvasHistorySelection(List.of(
                    new interface_adapter.history.CanvasHistorySelection.Asset(image, "", "", 10, 10, 40, 25))), controller, 40, 100);
            check(history.list().size() == originalHistorySize, "History→Canvas→PDF does not duplicate existing ink");
            var revisions = new pdf_editor.adapter.FormulaRevisionCoordinator(library, history, f -> "9");
            revisions.editHistory(controller, archivedCalc, "y+8", "9");
            check(controller.placements().stream().filter(p -> !p.material().isGraph()).allMatch(p -> p.material().formula().equals("y+8")), "Archive revision updates matching PDF placements");
            check(history.list().stream().noneMatch(e -> e.id().equals(archivedCalc.id())), "Old archive revision retired");
            check(history.list().stream().anyMatch(e -> e.formula().equals("y+8") && e.result().equals("9")), "New archive revision persists");

            var document = new PdfDocument(); document.addPage(); document.setBody(1, List.of(body));
            var note = PdfDocument.Placement.note("", 12, 34, 170, 45).withSpans(List.of(body));
            var plot = PdfDocument.Placement.material(nativeMaterial, 80, 100, 200, 150)
                    .withLayout(PdfDocument.ImageLayer.WRAP_TEXT, PdfDocument.AnchorMode.MOVE_WITH_TEXT, 2);
            var formulaPlacement = PdfDocument.Placement.material(formula, 20, 100, 250, 60);
            var inkPlacement = PdfDocument.Placement.material(ink, 50, 260, 170, 90);
            document.add(0, note); document.add(0, formulaPlacement); document.add(1, plot); document.add(1, inkPlacement);
            Path docs = temp.resolve("documents");
            var workspace = new PdfWorkspace(new FilePdfDocumentRepository(docs));
            UUID id = UUID.randomUUID();
            workspace.save(workspace.snapshot(id, "Math notes", document, 1, 1.2), png);
            var restored = new PdfWorkspace(new FilePdfDocumentRepository(docs));
            var summary = restored.list().get(0);
            check(summary.id().equals(id) && summary.pages() == 2 && summary.name().equals("Math notes"), "Saved document metadata");
            check(Arrays.equals(summary.thumbnail(), png), "Saved thumbnail");
            var saved = restored.open(id);
            var loaded = saved.document();
            check(saved.page() == 1 && saved.zoom() == 1.2, "Saved view metadata");
            check(loaded.body(1).equals(List.of(body)), "Rich body text");
            check(loaded.placements(0).get(0).id().equals(note.id()), "Stable placement ID/order");
            check(loaded.placements(0).get(0).noteSpans().equals(note.noteSpans()), "Rich note");
            check(loaded.placements(0).get(1).material().result().equals("2"), "Calculation");
            var restoredPlot = loaded.placements(1).get(0);
            check(restoredPlot.id().equals(plot.id()) && restoredPlot.material().id().equals("native-id"), "Graph identity");
            check(restoredPlot.x() == 80 && restoredPlot.y() == 100 && restoredPlot.width() == 200 && restoredPlot.height() == 150, "Placement bounds");
            check(restoredPlot.anchorMode() == plot.anchorMode() && restoredPlot.imageLayer() == plot.imageLayer()
                    && restoredPlot.bodyAnchorOffset() == 2, "Image anchor and layer");
            check(graph.equals(restoredPlot.material().graphDefinition()), "Native 3D graph range and camera");
            check(Arrays.equals(png, restoredPlot.material().imagePng()), "Graph fallback");
            check(Arrays.equals(ink.imagePng(), loaded.placements(1).get(1).material().imagePng()), "Restored handwritten note");
            var editor = new DocumentEditor(); editor.load(loaded);
            check(!editor.undo(), "New document has fresh undo history");
            var moved = graph.withCamera(new GraphDefinition.Camera(.8, .3));
            check(editor.updateGraphView(plot.id(), moved), "Graph view update");
            editor.addNote(0, "Unsaved change", 40, 40);
            restored.saveRecovery(restored.snapshot(id, "Math notes", editor.snapshot(), 0, 1));
            var afterRestart = new PdfWorkspace(new FilePdfDocumentRepository(docs));
            check(afterRestart.recover().orElseThrow().document().placements(0).size() == 3, "Recovery includes unsaved edit");
            check(afterRestart.recover().orElseThrow().document().placements(1).get(0).material().graphDefinition().equals(moved), "Recovery captures current graph camera");
            check(afterRestart.open(id).document().placements(0).size() == 2, "Recovery does not overwrite named save");
            history.clearAll();
            check(afterRestart.open(id).document().placements(1).size() == 2, "Clearing History cannot erase document contents");
            UUID corrupt = UUID.randomUUID(); Files.write(docs.resolve(corrupt + ".mlpdf"), new byte[]{1,2,3});
            check(afterRestart.list().size() == 1, "Corrupt header omitted from list");
            try { afterRestart.open(corrupt); throw new AssertionError("Corrupt document accepted"); }
            catch (IOException expected) { /* expected */ }
            try (var paths = Files.list(docs)) { check(paths.noneMatch(p -> p.toString().endsWith(".tmp")), "No temporary file leak"); }
        } finally {
            try (var paths = Files.walk(temp)) {
                for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
            }
        }
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
