package view.drawing;

import entity.Stroke;
import view.answer.AnswerLayout;
import view.answer.CanvasAnswer;
import view.answer.Formula;
import view.answer.FormulaBook;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * High-resolution images of picked ink for copying out of the canvas (to History, the Library or
 * a PDF). Only image making (Single Responsibility); recognition images are made by
 * {@link FormulaImageRenderer} and stay black on white.
 */
public final class InkImageExporter {
    private static final int MARGIN = 18;     // world units around the ink
    private static final int RESOLUTION = 3;  // image pixels per world unit

    private InkImageExporter() {
    }

    /**
     * Full-colour copy of the picked ink on transparent pixels, including the answer of a formula
     * whose ink is wholly picked.
     */
    public static BufferedImage inColor(List<Stroke> selected, InkModel ink) {
        Formula selectedFormula = wholeAnsweredFormula(selected, ink.book());
        Rectangle box = AnswerLayout.bounds(selected);
        if (selectedFormula != null) box = box.union(selectedFormula.getAnswer().getBox().getBounds());
        BufferedImage image = new BufferedImage(Math.max(1, (box.width + 2 * MARGIN) * RESOLUTION),
                Math.max(1, (box.height + 2 * MARGIN) * RESOLUTION), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.scale(RESOLUTION, RESOLUTION);
            g.translate(MARGIN - box.x, MARGIN - box.y);
            CanvasPainter.setUpInk(g);
            for (Stroke stroke : selected) {
                g.setColor(ink.colorOf(stroke));
                CanvasPainter.drawStroke(g, stroke);
            }
            if (selectedFormula != null) CanvasPainter.drawAnswer(g, selectedFormula.getAnswer());
        } finally {
            g.dispose();
        }
        return image;
    }

    /** Only the picked ink on transparent pixels, without answers. */
    public static BufferedImage onTransparent(List<Stroke> selected, InkModel ink) {
        Rectangle bounds = AnswerLayout.bounds(selected);
        BufferedImage result = new BufferedImage(
                Math.max(1, (bounds.width + MARGIN * 2) * RESOLUTION),
                Math.max(1, (bounds.height + MARGIN * 2) * RESOLUTION), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics();
        try {
            g.scale(RESOLUTION, RESOLUTION);
            g.translate(MARGIN - bounds.x, MARGIN - bounds.y);
            CanvasPainter.setUpInk(g);
            for (Stroke stroke : selected) {
                g.setColor(ink.colorOf(stroke));
                CanvasPainter.drawStroke(g, stroke);
            }
        } finally {
            g.dispose();
        }
        return result;
    }

    /** The first answered formula whose ink is all among the picked strokes, or null. */
    public static Formula wholeAnsweredFormula(List<Stroke> selected, FormulaBook book) {
        for (Stroke stroke : selected) {
            Formula current = book.holding(stroke);
            if (current != null && current.getAnswer() != null && selected.containsAll(current.getStrokes())) {
                return current;
            }
        }
        return null;
    }

    /** The answer text of a wholly picked answered formula, or "". */
    public static String wholeAnswerText(List<Stroke> selected, FormulaBook book) {
        Formula f = wholeAnsweredFormula(selected, book);
        CanvasAnswer answer = f == null ? null : f.getAnswer();
        return answer == null ? "" : answer.getText();
    }
}
