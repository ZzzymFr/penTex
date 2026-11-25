package use_case.explore_phase;

// what the live phase card can ask for; snapH / snapV = how near (world units) counts as "on" something
public interface ExplorePhaseInputBoundary {
    void pan(double dx, double dy);

    void zoom(double cx, double cy, double fx, double fy);

    // home window, pinned solutions cleared
    void reset();

    void trace(double h, double v, double snapH, double snapV);

    void endTrace();

    // click: pin the solution through here, or unpin the one under the cursor
    void toggleSolution(double h, double v, double snapH, double snapV);

    /**
     * Find: {horizontal, vertical} as typed, blank = read it off the pinned solutions; both filled pins
     * the solution through that point. A boundary that can't look values up ignores it.
     */
    default void lookup(java.util.List<String> typed) {
    }

    default void clearLookup() {
    }
}
