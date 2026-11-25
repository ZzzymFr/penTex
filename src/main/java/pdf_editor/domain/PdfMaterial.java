package pdf_editor.domain;

import java.util.Objects;
import entity.graph.GraphDefinition;
import java.util.UUID;

/** Immutable domain value representing a typeset formula, result, or image. No Swing/PDFBox dependency. */
public class PdfMaterial {
    private final String id;
    private final String title;
    private final String formula;
    private final String result;
    private final byte[] imagePng;
    private final int imageWidth;
    private final int imageHeight;
    private final GraphDefinition graphDefinition;

    protected PdfMaterial(String title, String formula, String result,
                          byte[] imagePng, int imageWidth, int imageHeight) {
        this(UUID.randomUUID().toString(), title, formula, result, imagePng, imageWidth, imageHeight, null);
    }

    protected PdfMaterial(String id, String title, String formula, String result,
                          byte[] imagePng, int imageWidth, int imageHeight) {
        this(id, title, formula, result, imagePng, imageWidth, imageHeight, null);
    }
    protected PdfMaterial(String title, String formula, String result,
                          byte[] imagePng, int imageWidth, int imageHeight,
                          GraphDefinition definition) {
        this(UUID.randomUUID().toString(), title, formula, result, imagePng, imageWidth, imageHeight, definition);
    }
    protected PdfMaterial(String id, String title, String formula, String result,
                          byte[] imagePng, int imageWidth, int imageHeight,
                          GraphDefinition definition) {
        this.id = Objects.requireNonNull(id, "id");
        this.title = Objects.requireNonNull(title, "title");
        this.formula = formula == null ? "" : formula;
        this.result = result == null ? "" : result;
        this.imagePng = imagePng == null ? null : imagePng.clone();
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.graphDefinition = definition;
        if (definition != null && imagePng == null)
            throw new IllegalArgumentException("Interactive graph requires a static thumbnail fallback");
        if (imagePng != null && (imageWidth <= 0 || imageHeight <= 0))
            throw new IllegalArgumentException("Invalid image dimensions");
    }

    /** Reconstruct a stored value, retaining source identity and native graph metadata. */
    public static PdfMaterial restore(String id, String title, String formula, String result,
                                      byte[] png, int width, int height, GraphDefinition definition) {
        return new PdfMaterial(id, title, formula, result, png, width, height, definition);
    }

    /** Capture an independent placement's current interactive graph view. */
    public final PdfMaterial withGraphDefinition(GraphDefinition definition) {
        if (!isInteractiveGraph()) throw new IllegalStateException("Not an interactive graph");
        return new PdfMaterial(id, title, formula, result, imagePng, imageWidth, imageHeight,
                Objects.requireNonNull(definition));
    }

    /** Domain rule: split a calculation without duplicating the equals sign. */
    public static PdfMaterial formulaPart(PdfMaterial source) {
        Objects.requireNonNull(source, "source");
        String formula = source.formula().trim().replaceFirst("\\s*=+\\s*$", "");
        return new PdfMaterial(source.title() + " - Formula", formula, "", null, 0, 0);
    }
    public static PdfMaterial resultPart(PdfMaterial source) {
        Objects.requireNonNull(source, "source");
        String result = source.result().trim().replaceFirst("^\\s*=+\\s*", "");
        return new PdfMaterial(source.title() + " - Result", "", "=" + result, null, 0, 0);
    }

    /** Return a new material value with its own ID (copy-on-write).
     * The placement identity remains stable, while PDF appendix deduplication
     * can distinguish differently edited copies of one source material.
     * Image/graph materials are deliberately not editable as LaTeX text. */
    public final PdfMaterial withText(String newFormula, String newResult) {
        if (isGraph()) throw new IllegalStateException("An image cannot be edited as a formula");
        return new PdfMaterial(title, Objects.requireNonNull(newFormula, "formula"),
                Objects.requireNonNull(newResult, "result"), null, 0, 0);
    }

    /** An edit of a shared material preserves its identity across PDF placements and Library. */
    public final PdfMaterial revised(String newFormula, String newResult) {
        if (isGraph()) throw new IllegalStateException("An image cannot be edited as a formula");
        return new PdfMaterial(id, title, Objects.requireNonNull(newFormula, "formula"),
                Objects.requireNonNull(newResult, "result"), null, 0, 0);
    }

    public final String id() { return id; }
    public final String title() { return title; }
    public final String formula() { return formula; }
    public final String result() { return result; }
    public final boolean isGraph() { return imagePng != null; }
    public final boolean isInteractiveGraph() { return graphDefinition != null; }
    public final GraphDefinition graphDefinition() { return graphDefinition; }
    /** Keep the original graph source when its current high-resolution view is exported. */
    public final PdfMaterial withGraphSnapshot(byte[] png, int width, int height) {
        if (graphDefinition == null) throw new IllegalStateException("Not an interactive graph");
        return new PdfMaterial(id, title, formula, result, png, width, height, graphDefinition);
    }
    public final int imageWidth() { return imageWidth; }
    public final int imageHeight() { return imageHeight; }
    public final byte[] imagePng() { return imagePng == null ? null : imagePng.clone(); }
    public final boolean hasResult() { return !result.isBlank(); }
}
