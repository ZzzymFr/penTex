package view.canvas;

/** One axis: Auto, or a fixed {min, max}. */
final class Axis {
    final String name;
    boolean auto = true;
    boolean keepAuto;                 // Auto chosen in the panel: a redraw doesn't fill it in
    double min = Double.NaN;          // last numbers, also shown when switching Auto off
    double max = Double.NaN;

    Axis(String name) {
        this.name = name;
    }

    boolean known() {
        return Double.isFinite(min) && Double.isFinite(max);
    }
}
