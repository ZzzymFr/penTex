package entity.graph;

import entity.LatexFunctionParser;
import entity.LatexParseException;
import entity.MathFunction;

import java.util.Arrays;
import java.util.List;

/**
 * What was typed into the find boxes, one per axis (x, y and z on a 3D graph): a number such as
 * {@code 2}, {@code -1.5}, {@code pi/2} or {@code \sqrt{2}}, or blank = that's the one to work out.
 */
public final class LookupQuery {
    private static final double[] PROBES = {0, 1.2345};   // a number reads the same whatever x is

    private final double[] values;   // NaN = left blank
    private final int badAxis;       // the first box that isn't a number, -1 = all fine

    private LookupQuery(double[] values, int badAxis) {
        this.values = values;
        this.badAxis = badAxis;
    }

    /** Numbers straight away, NaN for a blank box. */
    public static LookupQuery of(double... values) {
        return new LookupQuery(values.clone(), -1);
    }

    /** Reads each box; null or blank = blank, anything that isn't one number marks {@link #badAxis()}. */
    public static LookupQuery read(List<String> typed) {
        LatexFunctionParser parser = new LatexFunctionParser();
        double[] values = new double[typed.size()];
        int bad = -1;
        for (int i = 0; i < values.length; i++) {
            String text = typed.get(i) == null ? "" : typed.get(i).trim();
            if (text.isEmpty()) {
                values[i] = Double.NaN;
                continue;
            }
            values[i] = number(parser, text);
            if (Double.isNaN(values[i]) && bad < 0) {
                bad = i;
            }
        }
        return new LookupQuery(values, bad);
    }

    // NaN if it isn't a single finite number
    private static double number(LatexFunctionParser parser, String text) {
        String t = text.replace('−', '-').replace('–', '-').replace("π", "\\pi ");
        try {
            double v = Double.parseDouble(t);
            return Double.isFinite(v) ? v : Double.NaN;
        } catch (NumberFormatException e) {
            // not a plain number, maybe pi/2 or \sqrt{2}
        }
        MathFunction f;
        try {
            f = parser.parse("y=" + t);
        } catch (LatexParseException | RuntimeException e) {
            return Double.NaN;
        }
        double first = f.evaluate(PROBES[0]);
        for (double probe : PROBES) {
            if (Double.compare(f.evaluate(probe), first) != 0) {
                return Double.NaN;   // depends on x: not a number
            }
        }
        return Double.isFinite(first) ? first : Double.NaN;
    }

    public int size() {
        return values.length;
    }

    /** The typed number, NaN if blank (or past the last box). */
    public double get(int axis) {
        return axis < values.length ? values[axis] : Double.NaN;
    }

    public boolean given(int axis) {
        return !Double.isNaN(get(axis));
    }

    /** How many of the first {@code axes} boxes have a number. */
    public int givenCount(int axes) {
        int n = 0;
        for (int i = 0; i < axes; i++) {
            if (given(i)) {
                n++;
            }
        }
        return n;
    }

    public int badAxis() {
        return badAxis;
    }

    public double[] values() {
        return values.clone();
    }

    @Override
    public String toString() {
        return Arrays.toString(values);
    }
}
