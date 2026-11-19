package view.answer;

import entity.InkPoint;
import entity.Stroke;

import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

// where answers go and how big, all in world coords
public final class AnswerLayout {
    public static final double GAP_RIGHT = 0.4;
    public static final double GAP_BELOW = 0.3;
    public static final double JOIN_MARGIN_X = 0.6;
    public static final double JOIN_MARGIN_Y = 0.15;
    public static final double MIN_CHAR_HEIGHT = 12;
    public static final double MAX_CHAR_HEIGHT = 400;
    public static final double NEW_INK_GAP_X = 1.5;
    public static final double NEW_INK_GAP_Y = 0.35;
    private static final double DOT = 6;
    private static final double LINE = 0.5;  // a stacked line needs a stroke this tall (x char), an i's dot isnt one

    private AnswerLayout() {
    }

    // strokes overlapping in x merged into one box, left to right
    public static List<Rectangle> glyphs(Collection<Stroke> strokes) {
        List<Rectangle> boxes = new ArrayList<>();
        for (Stroke s : strokes) {
            Rectangle b = bounds(List.of(s));
            if (b != null) {
                boxes.add(b);
            }
        }
        boxes.sort(Comparator.comparingInt(b -> b.x));
        List<Rectangle> glyphs = new ArrayList<>();
        Rectangle current = null;
        for (Rectangle b : boxes) {
            if (current != null && b.x <= current.getMaxX()) {
                current.add(b);
            } else {
                current = new Rectangle(b);
                glyphs.add(current);
            }
        }
        return glyphs;
    }

    // median height of the tall glyphs, skips "-", "=", dots; a fraction's top + bottom are chars of their own
    public static double charHeight(Collection<Stroke> strokes) {
        List<Integer> heights = new ArrayList<>();
        for (List<Stroke> part : unstacked(strokes)) {
            heights.addAll(tallHeights(glyphs(part)));
        }
        if (heights.isEmpty()) {
            Rectangle all = bounds(strokes);
            return clamped(all == null ? 0 : all.height);
        }
        return clamped(median(heights));
    }

    // fractions + lines over lines pulled apart (x overlap merged 2 over 3, lim over x->inf into one glyph), just the ink if there's no real char
    private static List<List<Stroke>> unstacked(Collection<Stroke> strokes) {
        if (tallHeights(strokeBoxes(strokes)).isEmpty()) {
            return List.of(new ArrayList<>(strokes));
        }
        double h = strokeHeight(strokes);
        List<List<Stroke>> parts = new ArrayList<>();
        for (List<Stroke> layer : Fractions.layers(strokes, h)) {
            parts.addAll(lines(layer, h));
        }
        return parts;
    }

    // ink cut where no stroke spans the y gap, each band w/ a char half as tall as h is a line; dots + bars go w/ the closest
    private static List<List<Stroke>> lines(List<Stroke> layer, double h) {
        List<Stroke> byTop = new ArrayList<>();
        for (Stroke s : layer) {
            if (bounds(List.of(s)) != null) {
                byTop.add(s);
            }
        }
        byTop.sort(Comparator.comparingInt(s -> bounds(List.of(s)).y));
        List<List<Stroke>> bands = new ArrayList<>();
        double bottom = Double.NEGATIVE_INFINITY;
        for (Stroke s : byTop) {
            Rectangle b = bounds(List.of(s));
            if (b.getMinY() > bottom) {
                bands.add(new ArrayList<>());
            }
            bands.get(bands.size() - 1).add(s);
            bottom = Math.max(bottom, b.getMaxY());
        }
        List<List<Stroke>> lines = new ArrayList<>();
        for (List<Stroke> band : bands) {
            if (tallHeights(strokeBoxes(band)).stream().anyMatch(t -> t >= LINE * h)) {
                lines.add(band);
            }
        }
        if (lines.size() < 2) {
            return List.of(layer);
        }
        for (List<Stroke> band : bands) {
            if (!lines.contains(band)) {
                closest(lines, bounds(band)).addAll(band);
            }
        }
        return lines;
    }

    private static List<Stroke> closest(List<List<Stroke>> lines, Rectangle box) {
        List<Stroke> best = lines.get(0);
        double gap = Double.MAX_VALUE;
        for (List<Stroke> line : lines) {
            Rectangle b = bounds(line);
            double g = Math.max(b.getMinY() - box.getMaxY(), box.getMinY() - b.getMaxY());
            if (g < gap) {
                gap = g;
                best = line;
            }
        }
        return best;
    }

    // any real char in there, not just "-" "=" dots
    public static boolean hasChars(Collection<Stroke> strokes) {
        return !tallHeights(glyphs(strokes)).isEmpty();
    }

    // top-left of an answer right of the formula, centred on its last glyph
    public static Point2D.Double rightOf(Collection<Stroke> formula, double charHeight, double answerHeight) {
        return rightOfAt(formula, charHeight, answerHeight / 2);
    }

    // same, but this far down the answer lands on the last glyph's middle (a fraction bar, not the box centre)
    public static Point2D.Double rightOfAt(Collection<Stroke> formula, double charHeight, double answerMiddle) {
        Rectangle all = bounds(formula);
        List<Rectangle> glyphs = glyphs(formula);
        Rectangle last = glyphs.get(glyphs.size() - 1);
        return new Point2D.Double(all.getMaxX() + GAP_RIGHT * charHeight, last.getCenterY() - answerMiddle);
    }

    // top-left of an answer under the formula
    public static Point2D.Double below(Collection<Stroke> formula, double charHeight) {
        Rectangle all = bounds(formula);
        return new Point2D.Double(all.getMinX(), all.getMaxY() + GAP_BELOW * charHeight);
    }

    // does a new stroke land on the formula
    public static boolean joins(Rectangle formulaBounds, double charHeight, Rectangle strokeBounds) {
        return near(formulaBounds, strokeBounds, JOIN_MARGIN_X * charHeight, JOIN_MARGIN_Y * charHeight);
    }

    // how far the middle of a stroke is outside the formula box, 0 = inside
    static double gap(Rectangle formulaBounds, Rectangle strokeBounds) {
        double x = strokeBounds.getCenterX();
        double y = strokeBounds.getCenterY();
        double dx = Math.max(0, Math.max(formulaBounds.getMinX() - x, x - formulaBounds.getMaxX()));
        double dy = Math.max(0, Math.max(formulaBounds.getMinY() - y, y - formulaBounds.getMaxY()));
        return Math.hypot(dx, dy);
    }

    // 3/4 up the tall single strokes, lowercase + "+" don't shrink it; lines stacked above each other don't add up
    public static double strokeHeight(Collection<Stroke> strokes) {
        List<Integer> heights = tallHeights(strokeBoxes(strokes));
        if (heights.isEmpty()) {
            return charHeight(strokes);
        }
        heights.sort(null);
        return clamped(heights.get((int) (0.75 * (heights.size() - 1))));
    }

    // tallest single stroke = a full char, a fraction doesn't stack up, lowercase doesn't drag it down
    public static double tallestStroke(Collection<Stroke> strokes) {
        List<Integer> heights = tallHeights(strokeBoxes(strokes));
        if (heights.isEmpty()) {
            return charHeight(strokes);
        }
        return clamped(java.util.Collections.max(heights));
    }

    private static List<Rectangle> strokeBoxes(Collection<Stroke> strokes) {
        List<Rectangle> boxes = new ArrayList<>();
        for (Stroke s : strokes) {
            Rectangle b = bounds(List.of(s));
            if (b != null) {
                boxes.add(b);
            }
        }
        return boxes;
    }

    // fresh loose ink: same formula if on the same line, gaps up to 1.5 chars
    public static boolean sameNewFormula(Rectangle group, double charHeight, Rectangle stroke) {
        return near(group, stroke, NEW_INK_GAP_X * charHeight, NEW_INK_GAP_Y * charHeight);
    }

    // b within mx/my of a, inclusive so 0-wide lines count
    private static boolean near(Rectangle a, Rectangle b, double mx, double my) {
        return b.getMinX() <= a.getMaxX() + mx
                && b.getMaxX() >= a.getMinX() - mx
                && b.getMinY() <= a.getMaxY() + my
                && b.getMaxY() >= a.getMinY() - my;
    }

    // heights of boxes that look like real chars, not "-" or dots
    private static List<Integer> tallHeights(List<Rectangle> boxes) {
        List<Integer> heights = new ArrayList<>();
        for (Rectangle b : boxes) {
            if (b.height >= DOT && b.height >= 0.5 * b.width) {
                heights.add(b.height);
            }
        }
        return heights;
    }

    private static double median(List<Integer> values) {
        values.sort(null);
        int n = values.size();
        return n % 2 == 1 ? values.get(n / 2) : (values.get(n / 2 - 1) + values.get(n / 2)) / 2.0;
    }

    private static double clamped(double height) {
        return Math.max(MIN_CHAR_HEIGHT, Math.min(MAX_CHAR_HEIGHT, height));
    }

    // box around every point, null if none
    public static Rectangle bounds(Iterable<Stroke> strokes) {
        Rectangle bounds = null;
        for (Stroke stroke : strokes) {
            for (InkPoint p : stroke.getPoints()) {
                if (bounds == null) {
                    bounds = new Rectangle(p.getX(), p.getY(), 0, 0);
                } else {
                    bounds.add(p.getX(), p.getY());
                }
            }
        }
        return bounds;
    }
}
