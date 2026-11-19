package view.answer;

import entity.InkPoint;
import entity.Stroke;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FractionsTest {
    private static final double H = 40;

    private static Stroke stroke(int... xy) {
        Stroke s = new Stroke();
        for (int i = 0; i < xy.length; i += 2) {
            s.addPoint(new InkPoint(xy[i], xy[i + 1], i));
        }
        return s;
    }

    // only a + b loose
    private static boolean across(List<Stroke> a, List<Stroke> b, double h) {
        List<Stroke> loose = new java.util.ArrayList<>(a);
        loose.addAll(b);
        return Fractions.across(a, b, loose, h);
    }

    private final Stroke one = stroke(120, 100, 120, 140);      // 1, 40 tall
    private final Stroke bar = stroke(100, 150, 140, 150);
    private final Stroke two = stroke(105, 160, 135, 160, 105, 200, 135, 200);  // 2-ish under it

    @Test
    void topAndBarJoinTheBottom() {
        assertTrue(across(List.of(one, bar), List.of(two), H));
        assertTrue(across(List.of(two), List.of(one, bar), H));
    }

    @Test
    void barAloneJoinsTopAndBottom() {
        assertTrue(across(List.of(bar), List.of(one, two), H));
    }

    @Test
    void dashWithNothingUnderItIsNoFraction() {
        assertFalse(across(List.of(one), List.of(bar), H));
    }

    @Test
    void equalsBarsDontCountAsTopOrBottom() {
        Stroke lower = stroke(100, 165, 140, 165);
        assertFalse(across(List.of(one, bar), List.of(lower), H));
    }

    @Test
    void inkOffToTheSideIsNotUnderTheBar() {
        Stroke side = stroke(200, 160, 230, 160, 200, 200, 230, 200);
        assertFalse(across(List.of(one, bar), List.of(side), H));
    }

    @Test
    void bottomTooFarDownIsTheNextLine() {
        Stroke low = stroke(105, 190, 135, 190, 105, 230, 135, 230);  // 40 under the bar, past 3/4 char
        assertFalse(across(List.of(one, bar), List.of(low), H));
    }

    @Test
    void topBarAndBottomEachOnTheirOwnRowStillMeet() {
        List<Stroke> loose = List.of(one, bar, two);
        assertTrue(Fractions.across(List.of(two), List.of(bar), loose, H), "top found in the rest of the ink");
        assertTrue(Fractions.across(List.of(two, bar), List.of(one), loose, H));
    }

    @Test
    void barWithoutItsTopInTheLooseInkIsNoFraction() {
        assertFalse(Fractions.across(List.of(two), List.of(bar), List.of(two, bar), H));
    }

    @Test
    void shortTopFarishOverTheBarStillCounts() {
        Stroke shortOne = stroke(120, 104, 120, 124);           // 1, 20 tall, 26 over the bar
        Stroke five = stroke(105, 174, 135, 174, 105, 214);     // 40 tall, 24 under it
        List<Stroke> loose = List.of(shortOne, bar, five);
        assertTrue(Fractions.across(List.of(five), List.of(bar), loose, 20), "reach goes by the bigger digit");
    }
}
