package pdf_editor.view;

import pdf_editor.adapter.PdfWrapTextLayout;
import pdf_editor.domain.PdfDocument;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Objects;

/** One EDT-owned body raster, independent of the material raster LRU.
 * Moving non-wrapping media or selecting any object cannot invalidate typography.
 * The existing layout renderer is still the sole source of the body pixels.
 */
final class PdfBodyRasterCache {
    private record Key(List<PdfDocument.NoteSpan> spans, List<PdfPageDamage.WrapBounds> wrapping,
                       int width, int height) { }
    private Key key;
    private BufferedImage image;

    BufferedImage render(List<PdfDocument.NoteSpan> spans, List<PdfDocument.Placement> placements,
                         int width, int height) {
        Key next = new Key(List.copyOf(spans), PdfPageDamage.wrappingBounds(placements), width, height);
        if (image == null || !Objects.equals(key, next)) {
            clear();
            image = PdfWrapTextLayout.render(spans, placements, width, height);
            key = next;
        }
        return image;
    }

    void clear() {
        if (image != null) image.flush();
        image = null;
        key = null;
    }
}
