package entity.graph;

import java.util.List;

/**
 * What a find on a graph came back with: the points that match the typed values (one per function they
 * hit), and for a 3D graph given a single value, the cut through it drawn as paths. Immutable.
 */
public final class LookupResult {
    public enum Problem {
        NONE,
        BAD_NUMBER,        // a box isn't a number
        NOTHING_GIVEN,     // every box is blank
        NOTHING_TO_FIND,   // every box is filled
        NOT_FOUND,         // nothing on the graph matches
        NO_SOLUTIONS       // a phase portrait with no solution curve to read off
    }

    /** A matching point; z is NaN on a 2D graph, layer = which function it's on. */
    public record Point(double x, double y, double z, int layer) {
    }

    /** An unbroken run of a cut through a 3D graph, all three coordinates per point. */
    public record Path(double[] xs, double[] ys, double[] zs, int layer) {
    }

    private final LookupQuery query;
    private final List<Point> points;
    private final List<Path> paths;
    private final Problem problem;
    private final boolean inView;      // only the window was searched (vs worked out straight from the formula)
    private final boolean truncated;   // more points than we keep

    private LookupResult(LookupQuery query, List<Point> points, List<Path> paths, Problem problem, boolean inView,
                         boolean truncated) {
        this.query = query;
        this.points = List.copyOf(points);
        this.paths = List.copyOf(paths);
        this.problem = problem;
        this.inView = inView;
        this.truncated = truncated;
    }

    /** What was found; nothing at all is reported as {@link Problem#NOT_FOUND}. */
    public static LookupResult found(LookupQuery query, List<Point> points, List<Path> paths, boolean inView,
                                     boolean truncated) {
        Problem problem = points.isEmpty() && paths.isEmpty() ? Problem.NOT_FOUND : Problem.NONE;
        return new LookupResult(query, points, paths, problem, inView, truncated);
    }

    public static LookupResult problem(LookupQuery query, Problem problem) {
        return new LookupResult(query, List.of(), List.of(), problem, false, false);
    }

    public LookupQuery getQuery() {
        return query;
    }

    public List<Point> getPoints() {
        return points;
    }

    public List<Path> getPaths() {
        return paths;
    }

    public Problem getProblem() {
        return problem;
    }

    public boolean isInView() {
        return inView;
    }

    public boolean isTruncated() {
        return truncated;
    }
}
