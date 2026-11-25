package pdf_editor.view;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Java2D tests of the PDF-only host; no JLaTeXMath, graph engine or platform stubs needed. */
public final class PdfLargeGraphPaintSmoke {
    private PdfLargeGraphPaintSmoke() { }

    public static void main(String[] args) throws Exception {
        verify();
        System.out.println("PDF large graph / fractional backing regression passed");
    }

    public static void verify() throws Exception {
        AtomicReference<PdfGraphPaintHost> host = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            checkFractionalBacking();
            Probe card = new Probe();
            AtomicBoolean synchronous = new AtomicBoolean(true);
            PdfGraphPaintHost surface = new PdfGraphPaintHost(card, synchronous::get);
            surface.setBounds(0, 0, 1650, 2100);
            surface.setActive(true);
            check(card.getWidth() == 1650 && card.getHeight() == 2100, "Native input geometry remains full size");
            check(surface.findComponentAt(new Point(400, 500)) == card, "Actual card receives mouse input");
            check(paint(surface) == Color.RED.getRGB(), "Initial 2D paint");
            for (int i = 0; i < 40; i++) paint(surface);
            check(card.draws == 1, "Forty unrelated ancestor paints reuse the 2D frame");
            check(card.pixels <= 3_000_000, "Oversized 2D raster has a fixed pixel budget");
            card.color = Color.BLUE;
            surface.invalidateFrame();
            check(paint(surface) == Color.BLUE.getRGB() && card.draws == 2, "Model change refreshes frame");
            card.color = Color.GREEN;
            card.dispatchEvent(new MouseEvent(card, MouseEvent.MOUSE_MOVED, 0, 0, 100, 100, 0, false));
            check(paint(surface) == Color.GREEN.getRGB() && card.draws == 3, "Mouse change refreshes frame");
            surface.setBounds(0, 0, 1200, 1900);
            check(card.getSize().equals(surface.getSize()), "Resize retains native hit test geometry");
            paint(surface);
            check(card.draws == 4, "Resize invalidates frame");
            surface.setActive(false);
            check(!card.isVisible() && !surface.isVisible(), "Deselection hides actual graph and host");
            synchronous.set(false);
            surface.setActive(true);
            paint(surface);
            card.color = Color.MAGENTA;
            for (int i = 0; i < 12; i++) paint(surface);
            // Timing is machine-dependent. The bound is tested structurally and the
            // latest asynchronous frame must become visible after the refresh window.
            check(card.pixels <= PdfGraphPaintHost.MAX_FRAME_PIXELS, "Oversized 3D raster is bounded");
            host.set(surface);
        });
        Thread.sleep(250);
        SwingUtilities.invokeAndWait(() -> {
            check(paint(host.get()) == Color.MAGENTA.getRGB(), "Asynchronous surface frame not permanently cached");
            host.get().setActive(false);
        });
    }

    private static void checkFractionalBacking() {
        var cache = new PdfViewportPaintCache();
        var area = new Rectangle(21, 19, 360, 270);
        var image = new BufferedImage(800, 650, BufferedImage.TYPE_INT_RGB);
        var renders = new java.util.concurrent.atomic.AtomicInteger();
        for (double scale : new double[] {1.25, 1.5, 2}) {
            cache.clear(); renders.set(0);
            Graphics2D g = image.createGraphics();
            try {
                g.translate(.25, .5); g.scale(scale, scale);
                var painter = (java.util.function.Consumer<Graphics2D>) layer -> {
                    renders.incrementAndGet();
                    check(layer.getTransform().getScaleX() == scale, "Device pixel scale preserved");
                    layer.setColor(Color.RED); layer.fill(area);
                    layer.setColor(Color.BLUE); layer.fillRect(area.x + 70, area.y + 80, 60, 50);
                };
                g.setClip(area);
                check(cache.paint(g, area, "stable", painter), "Fractional transform supported");
                for (int i = 0; i < 50; i++) {
                    g.setClip(area.x + i, area.y + i, 170, 150);
                    check(cache.paint(g, area, "stable", painter), "Fractional dirty paint supported");
                }
                check(renders.get() == 1, "50 dirty paints reuse software pixels at " + scale);
                int x = (int) ((area.x + 90) * scale + .25);
                int y = (int) ((area.y + 100) * scale + .5);
                check(image.getRGB(x, y) == Color.BLUE.getRGB(), "Correct fractional pixel position");
                check(cache.paint(g, area, "changed", painter) && renders.get() == 2, "Changed content redraws");
            } finally { g.dispose(); cache.clear(); }
        }
    }

    private static int paint(PdfGraphPaintHost host) {
        BufferedImage out = new BufferedImage(600, 500, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.scale(2, 2);
            g.setClip(0, 0, 300, 250);
            host.paint(g);
            return out.getRGB(100, 100);
        } finally { g.dispose(); out.flush(); }
    }

    private static final class Probe extends JComponent {
        int draws;
        double pixels;
        Color color = Color.RED;
        @Override protected void paintComponent(Graphics graphics) {
            draws++;
            double scale = ((Graphics2D) graphics).getTransform().getScaleX();
            pixels = getWidth() * (double) getHeight() * scale * scale;
            graphics.setColor(color); graphics.fillRect(0, 0, getWidth(), getHeight());
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
