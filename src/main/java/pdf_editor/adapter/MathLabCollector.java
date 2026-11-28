package pdf_editor.adapter;

import pdf_editor.use_case.MaterialStore;

import interface_adapter.UiThread;
import java.awt.image.BufferedImage;

public final class MathLabCollector {
    private final MaterialStore library;
    private final history.use_case.HistoryInteractor history;
    private final history.use_case.CanvasHistoryLifecycle linkedHistory;
    private void persist(HistoryCommand command) {
        if (history == null) return;
        try { command.execute(); }
        catch (java.io.IOException | IllegalArgumentException e) {
            System.err.println("Math Lab history save failed: " + e.getMessage());
        }
    }
    @FunctionalInterface private interface HistoryCommand { void execute() throws java.io.IOException; }
    private final UiThread ui;  // materials are published on the UI thread
    public MathLabCollector(MaterialStore library, UiThread ui) { this(library, null, null, ui); }
    public MathLabCollector(MaterialStore library, history.use_case.HistoryInteractor history, UiThread ui) {
        this(library, history, null, ui);
    }
    public MathLabCollector(MaterialStore library, history.use_case.HistoryInteractor history,
                            history.use_case.CanvasHistoryLifecycle linkedHistory, UiThread ui) {
        this.ui = java.util.Objects.requireNonNull(ui, "ui");
        this.library = library;
        this.history = history;
        this.linkedHistory = linkedHistory;
        if (history != null) {
            library.setInkPublisher(material -> persist(() -> history.recordInk(
                    material.title(), material.imagePng(), material.imageWidth(), material.imageHeight())));
            library.setArchivePublisher(material -> persist(() -> {
                if (material.isInteractiveGraph()) {
                    String definition = interface_adapter.graph.GraphDefinitionCodec.encode(material.graphDefinition());
                    var existing = history.list().stream().filter(entry -> definition.equals(entry.graphDefinition())).findFirst();
                    if (existing.isPresent()) history.refresh(existing.get().id());
                    else history.recordGraph(material.formula(), material.imagePng(), material.imageWidth(),
                            material.imageHeight(), definition);
                } else if (material.isGraph()) {
                    // An image-only Canvas history asset has no native graph metadata.
                    if (material.formula().isBlank()) {
                        var existing = archivedImage(material);
                        if (existing != null) history.refresh(existing.id());
                        else history.recordInk("Handwritten notes", material.imagePng(),
                                material.imageWidth(), material.imageHeight());
                    }
                    else history.recordGraph(material.formula(), material.imagePng(), material.imageWidth(), material.imageHeight());
                } else history.recordCalculation(material.formula(), material.result());
            }));
        }
    }
    /** The Canvas image transfer predates explicit ink/legacy-graph kinds. Match existing
     * pixels (including the display-only white-matte conversion) instead of reclassifying an archived graph. */
    private history.domain.HistoryEntry archivedImage(MaterialStore.Material material) throws java.io.IOException {
        byte[] png = material.imagePng();
        BufferedImage incoming = null;
        for (var entry : history.list()) {
            if (entry.imageWidth() != material.imageWidth() || entry.imageHeight() != material.imageHeight()) continue;
            byte[] previous = entry.png();
            if (previous == null) continue;
            if (java.util.Arrays.equals(previous, png)) return entry;
            if (incoming == null) incoming = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(png));
            BufferedImage original;
            try { original = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(previous)); }
            catch (java.io.IOException bad) { continue; }
            if (incoming == null || original == null) continue;
            BufferedImage displayed = rendering.TransparentArtwork.removeWhiteMatte(original);
            if (displayed.getWidth() != incoming.getWidth() || displayed.getHeight() != incoming.getHeight()) continue;
            boolean equal = true;
            for (int y = 0; y < incoming.getHeight() && equal; y++)
                for (int x = 0; x < incoming.getWidth(); x++)
                    if (displayed.getRGB(x, y) != incoming.getRGB(x, y)) { equal = false; break; }
            if (equal) return entry;
        }
        return null;
    }
    public void recordSelectedFormula(String latex, String result) {
        persist(() -> history.recordCalculation(latex, result));
    }
    public void acceptCalculation(String latex, String result) {
        if (latex != null && !latex.trim().isEmpty())
            onEdt(() -> {
                // Persist first: Library listeners must not observe a result that History
                // has not finished recording yet (the former caused intermittent mismatch).
                persist(() -> history.recordCalculation(latex, result));
                library.addCalculation(latex, result == null ? "" : result);
            });
    }
    public void acceptCalculation(long sourceId, String latex, String result) {
        if (latex != null && !latex.isBlank())
            onEdt(() -> {
                // History and Library represent the same completed event. Finish durable
                // storage before publishing the in-memory change to UI subscribers.
                if (linkedHistory != null) persist(() -> linkedHistory.recordCalculation(sourceId, latex, result));
                else persist(() -> history.recordCalculation(latex, result));
                library.addSourceCalculation(sourceId, latex, result == null ? "" : result);
            });
    }
    /** Save one new native graph. History/Library thumbnails are not its interactive data. */
    public void acceptInteractiveGraph(entity.graph.GraphDefinition definition, BufferedImage image) {
        if (definition == null || image == null) return;
        onEdt(() -> {
            MaterialStore.Material material = library.addInteractiveGraph(definition, PngImages.encode(image));
            String encoded = interface_adapter.graph.GraphDefinitionCodec.encode(definition);
            persist(() -> history.recordGraph(definition.functions().get(0), material.imagePng(),
                    material.imageWidth(), material.imageHeight(), encoded));
        });
    }
    public void acceptGraph(String title, BufferedImage image) {
        if (image != null) onEdt(() -> {
            MaterialStore.Material material = library.addGraph(title == null ? "Graph" : title, PngImages.encode(image));
            persist(() -> history.recordGraph(title, material.imagePng(),
                    material.imageWidth(), material.imageHeight()));
        });
    }
    private void onEdt(Runnable task) {
        ui.run(task);
    }
}
