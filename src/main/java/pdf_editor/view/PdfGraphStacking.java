package pdf_editor.view;

import pdf_editor.domain.PdfDocument;

import java.util.ArrayList;
import java.util.List;

/**
 * Paint-order policy for a selected interactive GraphCard.
 *
 * Swing children paint above the page's paintComponent. The GraphCard must be
 * composed back into the document's existing text and media order without
 * changing the domain's ordering rules or the GraphCard implementation.
 */
final class PdfGraphStacking {
    private PdfGraphStacking() { }

    static boolean bodyAbove(PdfDocument.Placement graph) {
        return graph.imageLayer() == PdfDocument.ImageLayer.BEHIND_TEXT;
    }

    static List<PdfDocument.Placement> behindAbove(List<PdfDocument.Placement> page,
                                                    PdfDocument.Placement graph) {
        if (!bodyAbove(graph)) return List.of();
        int index = page.indexOf(graph);
        if (index < 0) return List.of();
        List<PdfDocument.Placement> ordered = new ArrayList<>();
        for (int i = index + 1; i < page.size(); i++) {
            PdfDocument.Placement p = page.get(i);
            if (!p.isNote() && p.imageLayer() == PdfDocument.ImageLayer.BEHIND_TEXT)
                ordered.add(p);
        }
        return List.copyOf(ordered);
    }

    static List<PdfDocument.Placement> foregroundAbove(List<PdfDocument.Placement> page,
                                                        PdfDocument.Placement graph) {
        int graphIndex = page.indexOf(graph);
        if (graphIndex < 0) return List.of();
        boolean behind = bodyAbove(graph);
        List<PdfDocument.Placement> ordered = new ArrayList<>();
        for (int i = behind ? 0 : graphIndex + 1; i < page.size(); i++) {
            PdfDocument.Placement p = page.get(i);
            if (p.isNote() || p.imageLayer() != PdfDocument.ImageLayer.BEHIND_TEXT)
                ordered.add(p);
        }
        return List.copyOf(ordered);
    }
}
