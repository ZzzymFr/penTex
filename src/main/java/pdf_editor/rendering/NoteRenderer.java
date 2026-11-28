package pdf_editor.rendering;

import pdf_editor.domain.PdfDocument;
import javax.swing.JTextPane;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/** Render note using the same styled document used in the editor, for preview/export parity. */
public final class NoteRenderer {
    private NoteRenderer() { }
    public static BufferedImage render(PdfDocument.Placement note, int width, int height) {
        int w = Math.max(8, width), h = Math.max(8, height);
        // A move creates a new immutable placement, but does not change its typography.
        // Position, z-order and ID are not part of the rendered image.
        NoteRaster key = new NoteRaster(note.noteText(), note.noteSpans(), note.noteStyle(), note.noteSize(),
                Math.max(8, Math.round(note.width())), Math.max(8, Math.round(note.height())));
        return PdfScreenRasterCache.render(key, w, h, () -> renderUncached(note, w, h));
    }
    private record NoteRaster(String text, java.util.List<PdfDocument.NoteSpan> spans, int style,
                              int size, int logicalWidth, int logicalHeight) {
    }
    private static BufferedImage renderUncached(PdfDocument.Placement note, int width, int height) {
        width = Math.max(8, width); height = Math.max(8, height);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            JTextPane pane = NoteTextDocumentFactory.createPane(note);
            pane.setOpaque(false);
            int logicalWidth = Math.max(8, Math.round(note.width()));
            int logicalHeight = Math.max(8, Math.round(note.height()));
            pane.setSize(logicalWidth, logicalHeight);
            pane.validate();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.scale(width / (double) logicalWidth, height / (double) logicalHeight);
            pane.printAll(g);
        } finally { g.dispose(); }
        return image;
    }
}
