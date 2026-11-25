package use_case.lookup;

import entity.graph.LookupResult;

import java.util.ArrayList;
import java.util.List;

/**
 * A find on a graph or phase portrait, as plain data for the presenter: what was typed per axis
 * (NaN = blank), the matching points, and the cut paths through a 3D graph.
 */
public final class LookupOutputData {
    public enum Problem { NONE, BAD_NUMBER, NOTHING_GIVEN, NOTHING_TO_FIND, NOT_FOUND, NO_SOLUTIONS }

    /** z NaN on a flat graph; layer = which function / solution it's on. */
    public record Point(double x, double y, double z, int layer) {
    }

    public record Path(double[] xs, double[] ys, double[] zs, int layer) {
    }

    private final double[] given;
    private final int badAxis;
    private final Problem problem;
    private final List<Point> points;
    private final List<Path> paths;
    private final boolean inView;
    private final boolean truncated;

    public LookupOutputData(double[] given, int badAxis, Problem problem, List<Point> points, List<Path> paths,
                            boolean inView, boolean truncated) {
        this.given = given.clone();
        this.badAxis = badAxis;
        this.problem = problem;
        this.points = List.copyOf(points);
        this.paths = List.copyOf(paths);
        this.inView = inView;
        this.truncated = truncated;
    }

    public static LookupOutputData from(LookupResult r) {
        List<Point> points = new ArrayList<>();
        for (LookupResult.Point p : r.getPoints()) {
            points.add(new Point(p.x(), p.y(), p.z(), p.layer()));
        }
        List<Path> paths = new ArrayList<>();
        for (LookupResult.Path p : r.getPaths()) {
            paths.add(new Path(p.xs().clone(), p.ys().clone(), p.zs().clone(), p.layer()));
        }
        return new LookupOutputData(r.getQuery().values(), r.getQuery().badAxis(),
                Problem.valueOf(r.getProblem().name()), points, paths, r.isInView(), r.isTruncated());
    }

    // what was typed in each box, NaN = blank
    public double[] getGiven() {
        return given.clone();
    }

    public int getBadAxis() {
        return badAxis;
    }

    public Problem getProblem() {
        return problem;
    }

    public List<Point> getPoints() {
        return points;
    }

    public List<Path> getPaths() {
        return paths;
    }

    // the blank was searched for inside the window only
    public boolean isInView() {
        return inView;
    }

    public boolean isTruncated() {
        return truncated;
    }
}
