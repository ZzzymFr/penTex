package interface_adapter.graph;

import use_case.lookup.LookupOutputData;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Words a find for the panel, the tag and the card. The graph and phase presenters both use it, each
 * with its own axis names; {@code solutions} = a phase portrait (results sit on pinned solutions).
 */
public final class LookupWording {
    private LookupWording() {
    }

    /**
     * @param names    each axis as shown, e.g. x y z, or t y
     * @param coloured several functions share the graph: lines get their function's colour dot
     * @param solutions a phase portrait, where values are read off pinned solutions
     */
    public static GraphLookup word(LookupOutputData d, List<String> names, boolean coloured, boolean solutions) {
        int axes = names.size();
        double[] given = d.getGiven();
        double[] typed = new double[axes];
        for (int i = 0; i < axes; i++) {
            typed[i] = i < given.length ? given[i] : Double.NaN;
        }
        String asked = asked(names, typed);
        List<GraphLookup.Point> points = new ArrayList<>();
        for (LookupOutputData.Point p : d.getPoints()) {
            points.add(new GraphLookup.Point(p.x(), p.y(), p.z(), p.layer(), pointText(p, axes)));
        }
        List<GraphLookup.Path> paths = new ArrayList<>();
        for (LookupOutputData.Path p : d.getPaths()) {
            paths.add(new GraphLookup.Path(p.xs(), p.ys(), p.zs(), p.layer()));
        }
        String[] answers = new String[axes];
        if (d.getProblem() != LookupOutputData.Problem.NONE) {
            return new GraphLookup(names, typed, points, paths, answers, List.of(),
                    message(d, names, asked, solutions), d.getProblem() == LookupOutputData.Problem.NOT_FOUND
                    ? asked + " → none" : null);
        }
        boolean allGiven = count(typed) == axes;
        List<GraphLookup.Line> lines = new ArrayList<>();
        if (allGiven) {
            lines.add(new GraphLookup.Line("Solution through " + points.get(0).text() + " pinned", -1));
        } else {
            for (GraphLookup.Point p : points) {
                lines.add(new GraphLookup.Line(unknowns(p, typed, names), coloured ? p.layer() : -1));
            }
            cutLines(d, names, typed, coloured, lines);
            if (d.isTruncated()) {
                lines.add(new GraphLookup.Line("Showing the first " + points.size() + ".", -1));
            }
            if (paths.isEmpty()) {
                for (int i = 0; i < axes; i++) {
                    if (Double.isNaN(typed[i])) {
                        answers[i] = single(points, i);
                    }
                }
            }
        }
        return new GraphLookup(names, typed, points, paths, answers, lines, null,
                summary(names, typed, asked, answers, points, paths, allGiven));
    }

    static String number(double v) {
        return GraphNumbers.format(v).replace('-', '−');
    }

    private static int count(double[] typed) {
        int n = 0;
        for (double v : typed) {
            if (!Double.isNaN(v)) {
                n++;
            }
        }
        return n;
    }

    private static double coordinate(GraphLookup.Point p, int axis) {
        return axis == 0 ? p.x() : axis == 1 ? p.y() : p.z();
    }

    private static double coordinate(LookupOutputData.Point p, int axis) {
        return axis == 0 ? p.x() : axis == 1 ? p.y() : p.z();
    }

    // "x = 2" or "x = 2, z = 1"
    private static String asked(List<String> names, double[] typed) {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < typed.length; i++) {
            if (!Double.isNaN(typed[i])) {
                parts.add(names.get(i) + " = " + number(typed[i]));
            }
        }
        return String.join(", ", parts);
    }

    private static String pointText(LookupOutputData.Point p, int axes) {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < axes; i++) {
            parts.add(number(coordinate(p, i)));
        }
        return "(" + String.join(", ", parts) + ")";
    }

    // the worked-out coordinates: "y = 4", or "x = 1, y = 2"
    private static String unknowns(GraphLookup.Point p, double[] typed, List<String> names) {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < typed.length; i++) {
            if (Double.isNaN(typed[i])) {
                parts.add(names.get(i) + " = " + number(coordinate(p, i)));
            }
        }
        return String.join(",  ", parts);
    }

    // one value for this axis across every point, or null
    private static String single(List<GraphLookup.Point> points, int axis) {
        Set<String> values = new LinkedHashSet<>();
        for (GraphLookup.Point p : points) {
            values.add(number(coordinate(p, axis)));
        }
        return values.size() == 1 ? values.iterator().next() : null;
    }

    // per function cut: "Cut drawn: y −5 to 5, z −3 to 4"
    private static void cutLines(LookupOutputData d, List<String> names, double[] typed, boolean coloured,
                                 List<GraphLookup.Line> lines) {
        Set<Integer> layers = new LinkedHashSet<>();
        for (LookupOutputData.Path p : d.getPaths()) {
            layers.add(p.layer());
        }
        for (int layer : layers) {
            List<String> spans = new ArrayList<>();
            for (int axis = 0; axis < typed.length; axis++) {
                if (!Double.isNaN(typed[axis])) {
                    continue;
                }
                double lo = Double.POSITIVE_INFINITY;
                double hi = Double.NEGATIVE_INFINITY;
                for (LookupOutputData.Path p : d.getPaths()) {
                    if (p.layer() != layer) {
                        continue;
                    }
                    double[] values = axis == 0 ? p.xs() : axis == 1 ? p.ys() : p.zs();
                    for (double v : values) {
                        lo = Math.min(lo, v);
                        hi = Math.max(hi, v);
                    }
                }
                if (lo <= hi) {
                    spans.add(names.get(axis) + " " + number(lo) + " to " + number(hi));
                }
            }
            lines.add(new GraphLookup.Line("Cut drawn: " + String.join(",  ", spans), coloured ? layer : -1));
        }
    }

    private static String summary(List<String> names, double[] typed, String asked, String[] answers,
                                  List<GraphLookup.Point> points, List<GraphLookup.Path> paths, boolean allGiven) {
        if (allGiven) {
            return asked + " → pinned";
        }
        if (!paths.isEmpty()) {
            return asked + " → cut drawn";
        }
        List<String> found = new ArrayList<>();
        for (int i = 0; i < typed.length; i++) {
            if (Double.isNaN(typed[i])) {
                if (answers[i] == null) {
                    return asked + " → " + points.size() + (points.size() == 1 ? " point" : " points");
                }
                found.add(names.get(i) + " = " + answers[i]);
            }
        }
        return asked + " → " + String.join(", ", found);
    }

    private static String message(LookupOutputData d, List<String> names, String asked, boolean solutions) {
        switch (d.getProblem()) {
            case BAD_NUMBER:
                String axis = d.getBadAxis() >= 0 && d.getBadAxis() < names.size() ? names.get(d.getBadAxis()) : "Each";
                return "The " + axis + " box needs a number, like 2, −1.5 or pi/2.";
            case NOTHING_GIVEN:
                return names.size() == 3 ? "Type one or two numbers; the blank boxes are worked out."
                        : "Type a number in one box; the other is worked out.";
            case NOTHING_TO_FIND:
                return names.size() == 3 ? "Leave at least one box blank: that's what gets worked out."
                        : "Leave one box blank: that's the one to work out.";
            case NO_SOLUTIONS:
                return "No solution curve to read from yet. Click the plot to pin one, or type both "
                        + names.get(0) + " and " + names.get(1) + " to start one there.";
            case NOT_FOUND:
                if (solutions) {
                    return d.isInView() ? "No pinned solution passes " + asked + " in view."
                            : "No pinned solution reaches " + asked + " (it blows up or isn't defined on the way).";
                }
                return d.isInView() ? "Nothing on the graph with " + asked + " in view. Zoom out or widen the range"
                        + " to look further." : "The graph isn't defined at " + asked + ".";
            default:
                return null;
        }
    }
}
