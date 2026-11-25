package interface_adapter.graph;

import java.util.List;

/**
 * A find, ready to show: the boxes' answers, the result lines for the panel, the short summary for the
 * tag, and the points / cut paths to draw on the card. Shared by graphs and phase portraits.
 */
public final class GraphLookup {
    /** A found point; z NaN when flat; text = "(2, 4)". */
    public record Point(double x, double y, double z, int layer, String text) {
    }

    /** One run of a cut through a 3D graph. */
    public record Path(double[] xs, double[] ys, double[] zs, int layer) {
    }

    /** A line of the result; layer = colour dot of that function, -1 = none. */
    public record Line(String text, int layer) {
    }

    private final List<String> names;
    private final double[] given;
    private final List<Point> points;
    private final List<Path> paths;
    private final String[] answers;
    private final List<Line> lines;
    private final String message;
    private final String summary;

    public GraphLookup(List<String> names, double[] given, List<Point> points, List<Path> paths, String[] answers,
                       List<Line> lines, String message, String summary) {
        this.names = List.copyOf(names);
        this.given = given.clone();
        this.points = List.copyOf(points);
        this.paths = List.copyOf(paths);
        this.answers = answers.clone();
        this.lines = List.copyOf(lines);
        this.message = message;
        this.summary = summary;
    }

    // axis names as shown: x y (z), or t y / y y′ on a phase portrait
    public List<String> getNames() {
        return names;
    }

    // what was typed per axis, NaN = blank
    public double[] getGiven() {
        return given.clone();
    }

    public boolean isGiven(int axis) {
        return axis < given.length && !Double.isNaN(given[axis]);
    }

    public List<Point> getPoints() {
        return points;
    }

    public List<Path> getPaths() {
        return paths;
    }

    // what goes in each blank box (one value found), null = leave it blank
    public String getAnswer(int axis) {
        return axis < answers.length ? answers[axis] : null;
    }

    public List<Line> getLines() {
        return lines;
    }

    // what went wrong or nothing found, null if fine
    public String getMessage() {
        return message;
    }

    // a few words for the tag, e.g. "x = 2 → y = 4"; null = nothing worth saying
    public String getSummary() {
        return summary;
    }
}
