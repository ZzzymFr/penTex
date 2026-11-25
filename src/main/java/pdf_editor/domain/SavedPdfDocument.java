package pdf_editor.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A portable snapshot of an editable PDF, not a flattened export. */
public record SavedPdfDocument(UUID id, String name, Instant modifiedAt, PdfDocument document,
                               int page, double zoom) {
    public SavedPdfDocument {
        Objects.requireNonNull(id);
        Objects.requireNonNull(name);
        Objects.requireNonNull(modifiedAt);
        document = Objects.requireNonNull(document).copy();
        if (name.isBlank() || name.length() > 200 || page < 0 || page >= document.pageCount()
                || !Double.isFinite(zoom) || zoom < 0.35 || zoom > 3.0)
            throw new IllegalArgumentException("Invalid PDF save metadata");
    }
    @Override public PdfDocument document() { return document.copy(); }
}
