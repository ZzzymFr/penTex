package view.board;

import javax.swing.SwingUtilities;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicInteger;

/** Pixel-level guard against accidentally reintroducing frosted, low-resolution glass. */
public final class GlassClaritySmoke {
    private GlassClaritySmoke() { }
    public static void main(String[] args) throws Exception {
        verify();
        System.out.println("Glass clarity checks passed");
    }
    public static void verify() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (double dpi : new double[]{1, 1.25, 1.5, 2}) {
                backdrop(dpi, false);
                backdrop(dpi, true);
                lens(dpi);
            }
        });
    }
    private static void require(boolean ok, String reason) {
        if (!ok) throw new AssertionError(reason);
    }
    private static void backdrop(double dpi, boolean vertical) {
        GlassBackdropCache cache = new GlassBackdropCache(() -> { });
        AtomicInteger captures = new AtomicInteger();
        java.util.function.BiFunction<Rectangle, Double, BufferedImage> capture = (bounds, scale) -> {
            captures.incrementAndGet();
            require(scale == dpi, "Backdrop is not sampled at display scale");
            BufferedImage image = new BufferedImage((int)Math.ceil((vertical ? bounds.height : bounds.width)*scale),
                    (int)Math.ceil((vertical ? bounds.width : bounds.height)*scale), BufferedImage.TYPE_INT_ARGB);
            // One-device-pixel black and white vertical strokes emulate very fine glyph detail.
            for (int y=0; y<image.getHeight(); y++) for (int x=0; x<image.getWidth(); x++)
                image.setRGB(x,y,x%2==0 ? Color.BLACK.getRGB() : Color.WHITE.getRGB());
            return image;
        };
        Point origin = new Point(80, 100);
        GlassBackdropCache.Sample first = cache.region(100,60,vertical,origin,1,false,dpi,capture);
        int black = first.image().getRGB(60,60)&255, white = first.image().getRGB(61,60)&255;
        require(black <= 5 && white >= 250, "The near-clear filter significantly blurred one-pixel strokes: " + black + "/" + white);
        GlassBackdropCache.Sample expanded = cache.region(172,60,vertical,origin,1,true,dpi,capture);
        require(first.image()==expanded.image() && captures.get()==1, "Expansion repeated background capture");
        BufferedImage screen = new BufferedImage((int)(100*dpi),(int)(60*dpi),BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = screen.createGraphics(); g.scale(dpi,dpi); expanded.paint(g); g.dispose();
        int contrast = Math.abs((screen.getRGB(10,10)&255)-(screen.getRGB(11,10)&255));
        require(contrast >= 245, "Display mapping lost native-pixel contrast at " + dpi + "x: " + contrast);
        cache.clear();
    }
    private static void lens(double dpi) {
        GlassLensRaster raster = new GlassLensRaster();
        GlassBackdropCache.Sample sample = new GlassBackdropCache.Sample(new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB),0,0);
        BufferedImage output = new BufferedImage((int)(180*dpi),(int)(120*dpi),BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = output.createGraphics(); g.scale(dpi,dpi);
        RoundRectangle2D.Float shape = new RoundRectangle2D.Float(20,20,120,68,68,68);
        java.util.function.Consumer<Graphics2D> source = scene -> {
            scene.setColor(Color.WHITE); scene.fillRect(0,0,180,120);
            scene.setColor(Color.BLACK);
            scene.fill(new java.awt.geom.Rectangle2D.Double(80-1/dpi,24,2/dpi,60));
        };
        raster.paint(g,shape,sample,1,dpi,1.28f,source);
        int darkest = 255;
        int center = (int)(80*dpi), y = (int)(54*dpi);
        for (int x=center-2; x<=center+2; x++) darkest = Math.min(darkest, output.getRGB(x,y)&255);
        require(darkest <= 50, "Magnified thin stroke became too soft at " + dpi + "x: " + darkest);
        long start=System.nanoTime();
        for (int i=0; i<100; i++) raster.paint(g,shape,sample,1,dpi,1.28f,source);
        require(raster.buildCount()==1, "Stable lens was regenerated");
        long stable=System.nanoTime()-start;
        start=System.nanoTime();
        for (int i=0; i<30; i++) {
            shape.x = 20+i%4;
            raster.paint(g,shape,sample,2+i,dpi,1.28f,source);
        }
        System.out.printf("%.2fx: native stroke darkest=%d; 100 cached draws %.2f ms; 30 changed frames %.2f ms%n",
                dpi,darkest,stable/1e6,(System.nanoTime()-start)/1e6);
        g.dispose();
    }
}
