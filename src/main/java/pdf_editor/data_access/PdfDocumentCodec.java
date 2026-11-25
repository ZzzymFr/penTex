package pdf_editor.data_access;

import entity.graph.GraphDefinition;
import interface_adapter.graph.GraphDefinitionCodec;
import pdf_editor.domain.PdfDocument;
import pdf_editor.domain.PdfMaterial;
import pdf_editor.domain.SavedPdfDocument;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/** Versioned, bounded binary encoding. No Swing objects or Java deserialization. */
final class PdfDocumentCodec {
    private static final int MAGIC = 0x4d4c5031; // MLP1
    private static final int MAX_TEXT = 4 * 1024 * 1024;
    private static final int MAX_IMAGE = 64 * 1024 * 1024;
    private static final int MAX_PAGES = 1000;
    private static final int MAX_ITEMS = 100000;
    record Header(UUID id, String name, Instant modified, int pages, byte[] thumbnail, int page, double zoom) { }

    void write(DataOutputStream out, SavedPdfDocument saved, byte[] thumbnail) throws IOException {
        PdfDocument doc = saved.document();
        out.writeInt(MAGIC);
        out.writeLong(saved.id().getMostSignificantBits()); out.writeLong(saved.id().getLeastSignificantBits());
        text(out, saved.name()); out.writeLong(saved.modifiedAt().toEpochMilli());
        count(out, doc.pageCount(), MAX_PAGES); bytes(out, thumbnail, MAX_IMAGE);
        out.writeInt(saved.page()); out.writeDouble(saved.zoom());
        Set<String> ids = new HashSet<>();
        for (int page = 0; page < doc.pageCount(); page++) {
            spans(out, doc.body(page));
            var placements = doc.placements(page); count(out, placements.size(), MAX_ITEMS);
            for (var p : placements) {
                if (!ids.add(p.id())) throw new IOException("Duplicate placement ID");
                text(out, p.id()); out.writeFloat(p.x()); out.writeFloat(p.y());
                out.writeFloat(p.width()); out.writeFloat(p.height());
                text(out, p.imageLayer().name()); text(out, p.anchorMode().name()); out.writeInt(p.bodyAnchorOffset());
                out.writeInt(p.noteStyle()); out.writeInt(p.noteSize()); out.writeBoolean(p.isNote());
                if (p.isNote()) { text(out, p.noteText()); spans(out, p.noteSpans()); }
                else material(out, p.material());
            }
        }
    }
    Header header(DataInputStream in) throws IOException {
        if (in.readInt() != MAGIC) throw new IOException("Unsupported PDF document version");
        UUID id = new UUID(in.readLong(), in.readLong());
        String name = text(in); Instant modified = Instant.ofEpochMilli(in.readLong());
        int pages = count(in, MAX_PAGES); byte[] thumbnail = bytes(in, MAX_IMAGE);
        int page = in.readInt(); double zoom = in.readDouble();
        if (pages == 0 || name.isBlank() || name.length() > 200 || page < 0 || page >= pages
                || !Double.isFinite(zoom) || zoom < 0.35 || zoom > 3) throw new IOException("Invalid PDF header");
        return new Header(id, name, modified, pages, thumbnail, page, zoom);
    }
    SavedPdfDocument read(DataInputStream in) throws IOException {
        try {
            Header h = header(in); PdfDocument doc = new PdfDocument(); Set<String> ids = new HashSet<>();
            for (int page = 0; page < h.pages(); page++) {
                if (page > 0) doc.addPage(); doc.setBody(page, spans(in));
                int items = count(in, MAX_ITEMS);
                for (int j = 0; j < items; j++) {
                    String id = text(in);
                    if (!ids.add(id)) throw new IOException("Duplicate placement ID");
                    float x = in.readFloat(), y = in.readFloat(), w = in.readFloat(), height = in.readFloat();
                    var layer = PdfDocument.ImageLayer.valueOf(text(in));
                    var anchor = PdfDocument.AnchorMode.valueOf(text(in)); int offset = in.readInt();
                    int style = in.readInt(), size = in.readInt(); boolean note = in.readBoolean();
                    String noteText = note ? text(in) : null;
                    var noteSpans = note ? spans(in) : List.<PdfDocument.NoteSpan>of();
                    PdfMaterial material = note ? null : material(in);
                    doc.add(page, PdfDocument.Placement.restore(id, material, x, y, w, height,
                            noteText, noteSpans, style, size, layer, anchor, offset));
                }
            }
            if (in.read() != -1) throw new IOException("Unexpected trailing PDF data");
            return new SavedPdfDocument(h.id(), h.name(), h.modified(), doc, h.page(), h.zoom());
        } catch (IllegalArgumentException invalid) { throw new IOException("Invalid PDF document", invalid); }
    }
    private static void material(DataOutputStream out, PdfMaterial material) throws IOException {
        text(out, material.id()); text(out, material.title()); text(out, material.formula()); text(out, material.result());
        out.writeInt(material.imageWidth()); out.writeInt(material.imageHeight()); bytes(out, material.imagePng(), MAX_IMAGE);
        GraphDefinition graph = material.graphDefinition(); out.writeBoolean(graph != null);
        if (graph != null) text(out, GraphDefinitionCodec.encode(graph));
    }
    private static PdfMaterial material(DataInputStream in) throws IOException {
        String id = text(in), title = text(in), formula = text(in), result = text(in);
        int width = in.readInt(), height = in.readInt(); byte[] png = bytes(in, MAX_IMAGE);
        GraphDefinition graph = in.readBoolean() ? GraphDefinitionCodec.decode(text(in)) : null;
        if (id.isBlank() || (png == null && (width != 0 || height != 0))) throw new IOException("Invalid material");
        return PdfMaterial.restore(id, title, formula, result, png, width, height, graph);
    }
    private static void spans(DataOutputStream out, List<PdfDocument.NoteSpan> spans) throws IOException {
        count(out, spans.size(), MAX_ITEMS);
        for (var span : spans) {
            text(out, span.text()); out.writeBoolean(span.bold()); out.writeBoolean(span.italic());
            out.writeBoolean(span.underline()); out.writeInt(span.size()); text(out, span.fontFamily());
        }
    }
    private static List<PdfDocument.NoteSpan> spans(DataInputStream in) throws IOException {
        int size = count(in, MAX_ITEMS); List<PdfDocument.NoteSpan> result = new ArrayList<>();
        for (int i = 0; i < size; i++) result.add(new PdfDocument.NoteSpan(text(in), in.readBoolean(),
                in.readBoolean(), in.readBoolean(), in.readInt(), text(in)));
        return result;
    }
    private static void count(DataOutputStream out, int n, int max) throws IOException {
        if (n < 0 || n > max) throw new IOException("PDF document exceeds format limit"); out.writeInt(n);
    }
    private static int count(DataInputStream in, int max) throws IOException {
        int n = in.readInt(); if (n < 0 || n > max) throw new IOException("Invalid PDF record length"); return n;
    }
    private static void text(DataOutputStream out, String s) throws IOException { bytes(out, s.getBytes(StandardCharsets.UTF_8), MAX_TEXT); }
    private static String text(DataInputStream in) throws IOException {
        byte[] b = bytes(in, MAX_TEXT); if (b == null) throw new IOException("Missing PDF text");
        return new String(b, StandardCharsets.UTF_8);
    }
    private static void bytes(DataOutputStream out, byte[] b, int max) throws IOException {
        if (b == null) { out.writeInt(-1); return; }
        count(out, b.length, max); out.write(b);
    }
    private static byte[] bytes(DataInputStream in, int max) throws IOException {
        int n = in.readInt(); if (n == -1) return null;
        if (n < 0 || n > max) throw new IOException("Invalid PDF payload length");
        byte[] b = in.readNBytes(n); if (b.length != n) throw new EOFException("Truncated PDF document"); return b;
    }
}
