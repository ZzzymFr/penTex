package use_case.explore_graph;

// what you can do to a graph once it's up, all in world units of the current window
public interface ExploreGraphInputBoundary {
    void pan(double dx, double dy);

    // (cx, cy) stays under the cursor, factor < 1 zooms in
    void zoom(double cx, double cy, double fx, double fy);

    // back to where the graph started
    void reset();

    // cursor at (x, y), snapX = how close in x counts as on a key point
    void trace(double x, double y, double snapX);

    // same, on surface number layer of a graph with several (the one under the cursor)
    default void trace(double x, double y, double snapX, int layer) {
        trace(x, y, snapX);
    }

    void endTrace();

    /**
     * Find: one entry per axis as typed (x, y, and z on a 3D graph), blank = work that one out. The
     * answer comes back as a lookup view; a boundary that can't look values up ignores it.
     */
    default void lookup(java.util.List<String> typed) {
    }

    // take the found points off the graph
    default void clearLookup() {
    }
}
