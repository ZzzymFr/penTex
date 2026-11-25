package view.graph;

import interface_adapter.graph.GraphLookup;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

// what a find put on the card: dashed lines at the typed values, a dot + value tag per point, cut paths in 3d
final class LookupPainter {
    private static final int MAX_TAGS = 8;   // more points than this: dots only, the panel lists them

    private LookupPainter() {
    }

    // 2d graph or phase portrait; colour = a function's colour by layer
    static void paintFlat(Graphics2D g0, GraphLookup lookup, PlotFrame f, float ui, IntFunction<Color> colour) {
        if (lookup == null) {
            return;
        }
        Graphics2D g = (Graphics2D) g0.create();
        try {
            Rectangle2D a = f.area();
            g.setColor(GraphTheme.LOOKUP_LINE);
            g.setStroke(dashed(ui));
            double[] given = lookup.getGiven();
            if (lookup.isGiven(0) && !lookup.isGiven(1)) {
                double x = f.px(given[0]);
                g.draw(new Line2D.Double(x, a.getMinY(), x, a.getMaxY()));
            } else if (lookup.isGiven(1) && !lookup.isGiven(0)) {
                double y = f.py(given[1]);
                g.draw(new Line2D.Double(a.getMinX(), y, a.getMaxX(), y));
            }
            boolean tags = lookup.getPoints().size() <= MAX_TAGS;
            List<Rectangle2D> placed = new ArrayList<>();
            for (GraphLookup.Point p : lookup.getPoints()) {
                dot(g, f.px(p.x()), f.py(p.y()), colour.apply(p.layer()), ui, a, placed);
            }
            for (GraphLookup.Point p : lookup.getPoints()) {
                if (tags) {
                    tag(g, f.px(p.x()), f.py(p.y()), p.text(), ui, a, placed);
                }
            }
        } finally {
            g.dispose();
        }
    }

    // 3d graph: cut paths over the surface, then the points
    static void paintSurface(Graphics2D g0, GraphLookup lookup, SurfaceScene scene, float ui,
                             IntFunction<Color> colour) {
        if (lookup == null || scene == null) {
            return;
        }
        Graphics2D g = (Graphics2D) g0.create();
        try {
            Rectangle2D a = scene.area();
            g.clip(a);
            for (GraphLookup.Path path : lookup.getPaths()) {
                Path2D.Double line = new Path2D.Double();
                double[] xs = path.xs();
                for (int k = 0; k < xs.length; k++) {
                    double[] p = scene.project(xs[k], path.ys()[k], path.zs()[k]);
                    if (k == 0) {
                        line.moveTo(p[0], p[1]);
                    } else {
                        line.lineTo(p[0], p[1]);
                    }
                }
                if (xs.length == 1) {
                    double[] p = scene.project(xs[0], path.ys()[0], path.zs()[0]);
                    g.setColor(colour.apply(path.layer()));
                    double r = 2.5 * ui;
                    g.fill(new Ellipse2D.Double(p[0] - r, p[1] - r, 2 * r, 2 * r));
                    continue;
                }
                g.setColor(GraphTheme.CARD);   // white halo so it reads over any colour of the surface
                g.setStroke(new BasicStroke(5f * ui, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(line);
                g.setColor(colour.apply(path.layer()));
                g.setStroke(new BasicStroke(2.6f * ui, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(line);
            }
            boolean tags = lookup.getPoints().size() <= MAX_TAGS;
            List<Rectangle2D> placed = new ArrayList<>();
            for (GraphLookup.Point p : lookup.getPoints()) {
                double[] at = scene.project(p.x(), p.y(), p.z());
                dot(g, at[0], at[1], colour.apply(p.layer()), ui, a, placed);
            }
            for (GraphLookup.Point p : lookup.getPoints()) {
                double[] at = scene.project(p.x(), p.y(), p.z());
                if (tags) {
                    tag(g, at[0], at[1], p.text(), ui, a, placed);
                }
            }
        } finally {
            g.dispose();
        }
    }

    private static BasicStroke dashed(float ui) {
        return new BasicStroke(1.3f * ui, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10,
                new float[]{5 * ui, 4 * ui}, 0);
    }

    private static final double DOT = 5;

    // ringed dot; its spot goes in taken so no tag covers it
    private static void dot(Graphics2D g, double px, double py, Color colour, float ui, Rectangle2D area,
                            List<Rectangle2D> taken) {
        if (!Double.isFinite(px) || !Double.isFinite(py) || !area.contains(px, py)) {
            return;
        }
        double r = DOT * ui;
        taken.add(new Rectangle2D.Double(px - r - 2 * ui, py - r - 2 * ui, 2 * r + 4 * ui, 2 * r + 4 * ui));
        g.setColor(GraphTheme.CARD);
        g.fill(new Ellipse2D.Double(px - r - 2 * ui, py - r - 2 * ui, 2 * r + 4 * ui, 2 * r + 4 * ui));
        g.setColor(colour);
        g.fill(new Ellipse2D.Double(px - r, py - r, 2 * r, 2 * r));
    }

    // its value in a small dark tag: above the dot, else below, else further out, off other tags and dots
    private static void tag(Graphics2D g, double px, double py, String text, float ui, Rectangle2D area,
                            List<Rectangle2D> placed) {
        if (!Double.isFinite(px) || !Double.isFinite(py) || !area.contains(px, py)) {
            return;
        }
        double gap = DOT * ui + 12 * ui;
        double step = 20 * ui;
        double[] tries = {-gap, gap, -gap - step, gap + step, -gap - 2 * step, gap + 2 * step};
        double cy = py + tries[0];
        for (double dy : tries) {
            Rectangle2D at = TraceBubble.pillBounds(g, px, py + dy, text, ui, area);
            if (placed.stream().noneMatch(at::intersects)) {
                cy = py + dy;
                break;
            }
        }
        placed.add(TraceBubble.pillBounds(g, px, cy, text, ui, area));
        TraceBubble.pill(g, px, cy, text, ui, area);
    }
}
