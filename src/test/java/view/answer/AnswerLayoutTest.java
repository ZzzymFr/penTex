package view.answer;

import entity.InkPoint;
import entity.Stroke;
import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnswerLayoutTest {

    private static Stroke stroke(int... xy) {
        Stroke s = new Stroke();
        for (int i = 0; i < xy.length; i += 2) {
            s.addPoint(new InkPoint(xy[i], xy[i + 1], i));
        }
        return s;
    }

    // "1+2=" written 40 tall, "=" at 240..270
    private static List<Stroke> onePlusTwoEquals() {
        return List.of(
                stroke(100, 100, 100, 140),               // 1
                stroke(120, 120, 160, 120),               // + bar
                stroke(140, 100, 140, 140),               // + stem
                stroke(180, 100, 220, 100, 180, 140, 220, 140),  // 2
                stroke(240, 115, 270, 115),               // = top
                stroke(240, 125, 270, 125));              // = bottom
    }

    @Test
    void glyphsGroupOverlappingStrokes() {
        List<Rectangle> glyphs = AnswerLayout.glyphs(onePlusTwoEquals());
        assertEquals(4, glyphs.size());
        assertEquals(new Rectangle(120, 100, 40, 40), glyphs.get(1));
        assertEquals(new Rectangle(240, 115, 30, 10), glyphs.get(3));
    }

    @Test
    void charHeightIgnoresEqualsAndMinus() {
        assertEquals(40, AnswerLayout.charHeight(onePlusTwoEquals()), 0.01);
    }

    @Test
    void charHeightIgnoresATallFraction() {
        List<Stroke> ink = List.of(
                stroke(100, 100, 100, 140), stroke(120, 100, 120, 140),
                stroke(140, 100, 140, 140), stroke(160, 100, 160, 140),
                stroke(200, 40, 200, 160));  // 120 tall
        assertEquals(40, AnswerLayout.charHeight(ink), 0.01);
    }

    private static List<Stroke> ink(int[][] xy) {
        List<Stroke> out = new java.util.ArrayList<>();
        for (int[] p : xy) {
            out.add(stroke(p));
        }
        return out;
    }

    // 2/3 - 1/5 = off the bug report, digits ~45 tall
    private static final int[][] TWO_THIRDS_MINUS_A_FIFTH = {
        {60, 55, 75, 52, 92, 52, 105, 59, 107, 70, 100, 82, 85, 92, 65, 103, 112, 103},  // 2
        {45, 124, 114, 124},                                                            // bar
        {65, 150, 98, 150, 81, 167, 95, 170, 101, 180, 97, 190, 85, 194, 65, 194},      // 3
        {139, 132, 174, 132},                                                           // -
        {258, 74, 258, 95},                                                             // 1
        {216, 121, 301, 121},                                                           // bar
        {284, 146, 256, 146, 255, 165, 270, 161, 282, 165, 287, 175, 282, 186, 270, 188, 254, 186},  // 5
        {338, 116, 364, 116}, {338, 131, 366, 131},                                     // =
    };

    // same digits in a row: 23 - 15 =
    private static final int[][] TWENTY_THREE_MINUS_FIFTEEN = {
        {60, 105, 75, 102, 92, 102, 105, 109, 107, 120, 100, 132, 85, 142, 65, 153, 112, 153},
        {125, 106, 158, 106, 141, 123, 155, 126, 161, 136, 157, 146, 145, 150, 125, 150},
        {180, 130, 215, 130},
        {240, 130, 240, 151},                                                           // short 1 like the stacked one
        {290, 107, 262, 107, 261, 126, 276, 122, 288, 126, 293, 136, 288, 147, 276, 149, 260, 147},
        {320, 122, 346, 122}, {320, 137, 348, 137},
    };

    @Test
    void fractionsTopAndBottomAreCharsNotOneTallGlyph() {
        double inARow = AnswerLayout.charHeight(ink(TWENTY_THREE_MINUS_FIFTEEN));
        assertEquals(42, inARow, 0.01);
        assertEquals(inARow, AnswerLayout.charHeight(ink(TWO_THIRDS_MINUS_A_FIFTH)), 0.01, "stacking digits doesnt make them bigger");
    }

    @Test
    void loneHalfIsCharSizeNotStackSize() {
        List<Stroke> half = List.of(
                stroke(120, 100, 120, 140),                       // 1, 40 tall
                stroke(100, 150, 140, 150),                       // bar
                stroke(105, 160, 135, 160, 105, 200, 135, 200));  // 2, 40 tall
        assertEquals(40, AnswerLayout.charHeight(half), 0.01);
    }

    // lim over x->inf off a saved canvas, chars ~20 tall
    private static final int[][] LIM = {
        {66, 66, 68, 63, 68, 71, 61, 112, 62, 122},
        {82, 97, 79, 114}, {87, 87, 87, 81, 89, 85},
        {92, 106, 90, 117, 92, 109, 99, 98, 105, 99, 104, 115, 113, 99, 119, 97, 121, 118},
    };
    private static final int[][] X_TO_INF = {
        {51, 149, 52, 143, 56, 141, 67, 160}, {69, 142, 54, 162},
        {79, 150, 98, 148}, {92, 141, 99, 144, 101, 149, 87, 161},
        {110, 157, 109, 152, 113, 142, 125, 161, 128, 160, 129, 137, 109, 162},
    };

    @Test
    void limitUnderLimIsCharSizeNotStackSize() {
        List<Stroke> stacked = ink(LIM);
        stacked.addAll(ink(X_TO_INF));
        List<Stroke> inARow = ink(LIM);
        for (int[] p : X_TO_INF) {
            int[] moved = p.clone();
            for (int i = 0; i < moved.length; i += 2) {
                moved[i] += 80;      // right of the m
                moved[i + 1] -= 60;  // up on lim's line
            }
            inARow.add(stroke(moved));
        }
        assertEquals(AnswerLayout.charHeight(inARow), AnswerLayout.charHeight(stacked), 0.01);
    }

    @Test
    void charHeightIsClamped() {
        assertEquals(AnswerLayout.MIN_CHAR_HEIGHT, AnswerLayout.charHeight(List.of(stroke(0, 0, 100, 0))), 0.01);
    }

    @Test
    void rightOfStartsPastTheEqualsSign() {
        List<Stroke> ink = List.of(stroke(100, 100, 100, 140), stroke(260, 115, 300, 115), stroke(260, 125, 300, 125));
        Point2D.Double at = AnswerLayout.rightOf(ink, 40, 30);
        assertEquals(300 + 0.4 * 40, at.x, 0.01);
        assertEquals(120, at.y + 15, 0.01);  // answer middle == "=" middle
    }

    @Test
    void belowStartsUnderTheLeftEdge() {
        List<Stroke> ink = List.of(stroke(100, 110, 200, 150));
        Point2D.Double at = AnswerLayout.below(ink, 40);
        assertEquals(100, at.x, 0.01);
        assertEquals(162, at.y, 0.01);
    }

    @Test
    void strokeOnTheFormulaJoins() {
        assertTrue(AnswerLayout.joins(new Rectangle(100, 100, 200, 40), 40, new Rectangle(310, 105, 20, 30)));
    }

    @Test
    void verticalLineOnTheFormulaJoins() {
        assertTrue(AnswerLayout.joins(new Rectangle(100, 100, 200, 40), 40, new Rectangle(150, 90, 0, 60)));
    }

    @Test
    void nextLineBelowIsNotJoined() {
        assertFalse(AnswerLayout.joins(new Rectangle(100, 100, 200, 40), 40, new Rectangle(100, 152, 200, 40)));
    }

    @Test
    void tallestStrokeIgnoresFractionStacking() {
        List<Stroke> fraction = List.of(
                stroke(200, 176, 220, 200), stroke(220, 176, 200, 200),    // x, 24
                stroke(257, 168, 265, 160, 265, 200),                      // 1, 40
                stroke(195, 205, 285, 205),                                // bar
                stroke(230, 220, 236, 212, 250, 218, 230, 252, 252, 252)); // 2, 40
        assertEquals(40, AnswerLayout.tallestStroke(fraction), 0.01, "not the 92 tall stack");
    }
}
