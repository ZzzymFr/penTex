package view.answer;

import entity.Stroke;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

// a bar w/ chars right over and under it, d/dx or 1/2 written alone w/ nothing beside to tie the rows
public final class Fractions {
    static final double FLAT = 0.35;     // bar at most this tall (x char height)
    static final double MIN_BAR = 0.5;   // and at least this wide
    static final double REACH = 0.75;    // gap bar <-> top/bottom, up to 3/4 of a char

    private Fractions() {
    }

    // some fraction has bits in both a and b, its top/bottom can be anywhere in loose (own rows)
    public static boolean across(Collection<Stroke> a, Collection<Stroke> b, Collection<Stroke> loose, double h) {
        List<Stroke> bars = new ArrayList<>(a);
        bars.addAll(b);
        List<Stroke> ink = new ArrayList<>(loose);
        for (Stroke s : bars) {
            if (!ink.contains(s)) {
                ink.add(s);
            }
        }
        for (Stroke bar : bars) {
            List<Stroke> fraction = around(bar, ink, h);
            if (!fraction.isEmpty() && !Collections.disjoint(fraction, a) && !Collections.disjoint(fraction, b)) {
                return true;
            }
        }
        return false;
    }

    // bar + the chars right over/under it, empty if it's not a bar w/ both
    private static List<Stroke> around(Stroke bar, List<Stroke> ink, double h) {
        Rectangle r = AnswerLayout.bounds(List.of(bar));
        if (r == null || r.height > FLAT * h || r.width < MIN_BAR * h) {
            return List.of();
        }
        List<Stroke> column = new ArrayList<>();
        for (Stroke s : ink) {
            Rectangle b = AnswerLayout.bounds(List.of(s));
            if (s != bar && b != null && AnswerLayout.hasChars(List.of(s))
                    && b.getCenterX() >= r.getMinX() && b.getCenterX() <= r.getMaxX()) {
                column.add(s);  // "-" "=" don't count, has to sit over the bar
            }
        }
        // short 1 over a 5: reach goes by the bigger of h + the digits right next to the bar
        double reach = REACH * Math.max(h, Math.max(nextTo(r, column, true), nextTo(r, column, false)));
        List<Stroke> fraction = new ArrayList<>(List.of(bar));
        boolean over = false;
        boolean under = false;
        for (Stroke s : column) {
            Rectangle b = AnswerLayout.bounds(List.of(s));
            if (b.getMaxY() <= r.getMinY() && r.getMinY() - b.getMaxY() <= reach) {
                over = true;
                fraction.add(s);
            } else if (b.getMinY() >= r.getMaxY() && b.getMinY() - r.getMaxY() <= reach) {
                under = true;
                fraction.add(s);
            }
        }
        return over && under ? fraction : List.of();
    }

    // height of the closest char over (or under) the bar, 0 if none
    private static double nextTo(Rectangle bar, List<Stroke> column, boolean above) {
        double gap = Double.MAX_VALUE;
        double height = 0;
        for (Stroke s : column) {
            Rectangle b = AnswerLayout.bounds(List.of(s));
            double g = above ? bar.getMinY() - b.getMaxY() : b.getMinY() - bar.getMaxY();
            if (g >= 0 && g < gap) {
                gap = g;
                height = b.height;
            }
        }
        return height;
    }
}
