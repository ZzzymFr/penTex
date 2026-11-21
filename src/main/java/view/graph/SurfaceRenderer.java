package view.graph;

import interface_adapter.graph.GraphState;
import interface_adapter.graph.GraphTick;

import javax.swing.SwingUtilities;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Paints the slow part of a 3d card (floor, mesh, labels, ~35 ms) on its own thread and hands the edt a
 * finished picture, so the canvas never waits on it. One thread for every 3d card; while a new frame is
 * cooking the card keeps showing the last one, and frames nobody is waiting for any more get skipped.
 */
final class SurfaceRenderer {
    // shared by all 3d cards, daemon so it never keeps the app alive
    private static final ExecutorService THREAD = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "surface-render");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1);   // canvas ink wins if the cpu is busy
        return t;
    });

    // everything the picture depends on; a different key = draw it again
    private record Key(Object z, double xMin, double xMax, double yMin, double yMax, double zMin, double zMax,
                       List<GraphTick> xTicks, List<GraphTick> yTicks, List<GraphTick> zTicks,
                       double yaw, double pitch, Rectangle2D plot, float ui, double scale) {
        static Key of(GraphState s, OrbitCamera c, Rectangle2D plot, float ui, double scale) {
            return new Key(s.getZLayers(), s.getXMin(), s.getXMax(), s.getYMin(), s.getYMax(), s.getZMin(), s.getZMax(),
                    s.getXTicks(), s.getYTicks(), s.getZTicks(), c.yaw(), c.pitch(),
                    new Rectangle2D.Double(plot.getX(), plot.getY(), plot.getWidth(), plot.getHeight()), ui, scale);
        }
    }

    private final Runnable repaint;
    private final AtomicLong latest = new AtomicLong();   // newest frame asked for, render thread reads it
    // edt only from here down
    private Key wanted;
    private Key shownKey;
    private long shownTicket;
    private BufferedImage shown;

    SurfaceRenderer(Runnable repaint) {
        this.repaint = repaint;
    }

    // runs any 3d job off the edt, same thread so jobs land in the order they were asked for
    static void background(Runnable job) {
        THREAD.execute(job);
    }

    /**
     * Edt: the picture for this graph / camera / size. If it isn't drawn yet this asks the render thread
     * for it and returns the last picture instead (null before the very first one).
     */
    BufferedImage picture(GraphState s, OrbitCamera camera, Rectangle2D plot, float ui, double scale) {
        Key key = Key.of(s, camera, plot, ui, scale);
        if (key.equals(shownKey)) {
            return shown;
        }
        if (!key.equals(wanted)) {
            wanted = key;
            request(key, new GraphState(s), camera.copy(), plot, ui, scale);
        }
        return shown;
    }

    // is the picture on screen the up to date one (tests + "still drawing" checks)
    boolean upToDate(GraphState s, OrbitCamera camera, Rectangle2D plot, float ui, double scale) {
        return Key.of(s, camera, plot, ui, scale).equals(shownKey);
    }

    private void request(Key key, GraphState state, OrbitCamera camera, Rectangle2D plot, float ui, double scale) {
        long ticket = latest.incrementAndGet();
        THREAD.execute(() -> {
            if (ticket != latest.get()) {
                return;   // spun on past this angle already, dont waste 35 ms on it
            }
            BufferedImage image = render(state, camera, plot, ui, scale);
            SwingUtilities.invokeLater(() -> {
                if (ticket < shownTicket) {
                    return;
                }
                shownTicket = ticket;
                shownKey = key;
                shown = image;
                repaint.run();
            });
        });
    }

    // just the plot area, at screen pixels (2x on retina) so blitting it back is 1:1
    static BufferedImage render(GraphState s, OrbitCamera camera, Rectangle2D plot, float ui, double scale) {
        int w = (int) Math.max(1, Math.ceil(plot.getWidth() * scale));
        int h = (int) Math.max(1, Math.ceil(plot.getHeight() * scale));
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.scale(scale, scale);
            g.translate(-plot.getX(), -plot.getY());
            SurfacePainter.paintBody(g, s, SurfaceScene.build(s, camera, plot), ui);
        } finally {
            g.dispose();
        }
        return image;
    }
}
