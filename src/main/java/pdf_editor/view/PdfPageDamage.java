package pdf_editor.view;

import pdf_editor.domain.PdfDocument;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Computes screen-only damage without changing document order or selection painting. */
final class PdfPageDamage {
    private PdfPageDamage() { }

    record State(int page, double zoom, Dimension size, Color background,
                 List<PdfDocument.Placement> placements, List<PdfDocument.NoteSpan> body,
                 Set<String> selection, boolean editingBody, String editingNote) { }

    /** Only the geometry of wrapping media affects the body layout. */
    record WrapBounds(float x, float y, float width, float height) { }

    static List<WrapBounds> wrappingBounds(List<PdfDocument.Placement> placements) {
        return placements.stream()
                .filter(p -> !p.isNote() && p.imageLayer() == PdfDocument.ImageLayer.WRAP_TEXT)
                .map(p -> new WrapBounds(p.x(), p.y(), p.width(), p.height()))
                .toList();
    }

    /** Called only when the physical backing area and device transform are unchanged.
     * Unknown states, ordering changes, body changes and text reflow need a full repaint.
     * Otherwise both old and new bounds are repainted in their original stacking order.
     */
    static Rectangle between(Object previous, Object next, Rectangle visible) {
        if (Objects.equals(previous, next)) return new Rectangle();
        if (!(previous instanceof State a) || !(next instanceof State b)
                || a.page() != b.page() || a.zoom() != b.zoom()
                || !a.size().equals(b.size()) || !Objects.equals(a.background(), b.background())
                || a.editingBody() != b.editingBody() || !Objects.equals(a.editingNote(), b.editingNote())
                || !a.body().equals(b.body()) || a.placements().size() != b.placements().size()
                || (!b.body().isEmpty() && !wrappingBounds(a.placements()).equals(wrappingBounds(b.placements())))) {
            return new Rectangle(visible);
        }
        Rectangle damage = null;
        for (int i = 0; i < a.placements().size(); i++) {
            var old = a.placements().get(i);
            var now = b.placements().get(i);
            if (!old.id().equals(now.id())) return new Rectangle(visible);
            if (old != now || a.selection().contains(old.id()) != b.selection().contains(now.id())) {
                Rectangle bounds = bounds(old, a.zoom()).union(bounds(now, b.zoom())).intersection(visible);
                if (!bounds.isEmpty()) damage = damage == null ? bounds : damage.union(bounds);
            }
        }
        return damage == null ? new Rectangle() : damage;
    }

    private static Rectangle bounds(PdfDocument.Placement p, double zoom) {
        // Match the page painter's rounded logical coordinates; allow for its dashed
        // selection stroke, corner radius, resize handle and antialiasing at any DPI.
        int margin = PdfPageCanvas.PAGE_MARGIN;
        int pad = (int)Math.ceil(6 * zoom) + 4;
        int left = margin + (int)Math.floor(Math.round(p.x()) * zoom);
        int top = margin + (int)Math.floor(Math.round(p.y()) * zoom);
        int right = margin + (int)Math.ceil((Math.round(p.x()) + Math.round(p.width())) * zoom);
        int bottom = margin + (int)Math.ceil((Math.round(p.y()) + Math.round(p.height())) * zoom);
        return new Rectangle(left - pad, top - pad, right - left + 2 * pad, bottom - top + 2 * pad);
    }
}
