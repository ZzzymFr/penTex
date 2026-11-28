package pdf_editor.domain;

import java.util.ArrayList;
import java.util.List;

/** Plain operations on styled text runs; no layout, no toolkit. */
public final class NoteSpans {
    private NoteSpans() {
    }

    public static String plainText(List<PdfDocument.NoteSpan> spans) {
        StringBuilder text = new StringBuilder();
        for (PdfDocument.NoteSpan span : spans) text.append(span.text());
        return text.toString();
    }

    public static List<PdfDocument.NoteSpan> concat(List<List<PdfDocument.NoteSpan>> parts) {
        List<PdfDocument.NoteSpan> combined = new ArrayList<>();
        for (List<PdfDocument.NoteSpan> part : parts) combined.addAll(part);
        return combined;
    }

    /** Slice styled runs on character boundaries without discarding formatting. */
    public static List<PdfDocument.NoteSpan> slice(List<PdfDocument.NoteSpan> source, int from, int to) {
        if (from < 0 || to < from) throw new IllegalArgumentException("Invalid range");
        List<PdfDocument.NoteSpan> result = new ArrayList<>();
        int offset = 0;
        for (PdfDocument.NoteSpan span : source) {
            int length = span.text().length();
            int a = Math.max(0, from - offset), b = Math.min(length, to - offset);
            if (b > a) result.add(new PdfDocument.NoteSpan(span.text().substring(a, b),
                    span.bold(), span.italic(), span.underline(), span.size(), span.fontFamily()));
            offset += length;
        }
        if (to > offset) throw new IllegalArgumentException("Range past end of text");
        return List.copyOf(result);
    }
}
