package entity.graph;

// one graph being explored: its function(s) + the window it's looked at through
public interface Plot {
    // what the title shows: each function's latex, joined with " & " when there are several
    String getLatex();

    static String join(java.util.List<String> latexes) {
        return String.join(" \\;\\&\\; ", latexes);
    }

    Viewport getViewport();

    // where it started, reset goes back here
    Viewport getHome();

    // same function, new window
    Plot lookingAt(Viewport viewport);

    // snap the cursor onto the real graph, snapX = how near in x counts as on a key point, null = nothing there
    TracePoint trace(double x, double y, double snapX);

    // same, on one of several functions (the one the cursor is over); plots with one function ignore it
    default TracePoint trace(double x, double y, double snapX, int layer) {
        return trace(x, y, snapX);
    }

    <T> T accept(PlotVisitor<T> visitor);
}
