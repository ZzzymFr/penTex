package pdf_editor.adapter;

import pdf_editor.domain.PdfDocument;

import java.util.List;

/**
 * Splits the document's flowing body into pages around its wrap-text pictures. Line breaking
 * needs real font metrics, so the implementation lives with the rendering code and is handed to
 * the controller (Dependency Inversion).
 */
@FunctionalInterface
public interface TextReflow {
    List<List<PdfDocument.NoteSpan>> paginate(List<PdfDocument.NoteSpan> story, PdfDocument snapshot);
}
