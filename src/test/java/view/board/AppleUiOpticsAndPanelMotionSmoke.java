package view.board;

import interface_adapter.history.HistoryViewModel;
import view.DrawingCanvas;
import workspace.domain.CanvasState;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Geometry and actual painted-pixel checks for the optical and split transition changes. */
public final class AppleUiOpticsAndPanelMotionSmoke {
    private AppleUiOpticsAndPanelMotionSmoke() { }
    public static void main(String[] args) throws Exception {
        verify();
        System.out.println("Apple UI optics and synchronized panel motion passed");
    }
    public static void verify() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            backgroundCoordinates(false);
            backgroundCoordinates(true);
            opticalRaster();
            actualBar();
            if (!GraphicsEnvironment.isHeadless()) panelMotion();
        });
    }

    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private static Object field(Object o, String name) {
        try { Field f=o.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(o); }
        catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
    }
    private static void invoke(Object o, String name, Class<?>[] types, Object... args) {
        try { Method m=o.getClass().getDeclaredMethod(name,types); m.setAccessible(true); m.invoke(o,args); }
        catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
    }

    private static void backgroundCoordinates(boolean vertical) {
        GlassBackdropCache cache=new GlassBackdropCache(()->{});
        AtomicInteger captures=new AtomicInteger();
        java.util.function.Function<Rectangle,BufferedImage> source=area->{
            captures.incrementAndGet();
            double s=GlassBackdropCache.SCALE;
            int width=(int)Math.ceil((vertical?area.height:area.width)*s);
            int height=(int)Math.ceil((vertical?area.width:area.height)*s);
            BufferedImage img=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
            // Deterministic colored reference-space marks, not a uniform background.
            for(int y=0;y<height;y++) for(int x=0;x<width;x++) {
                int u=(int)Math.floor(x/s)+(vertical?area.y:area.x);
                int v=(int)Math.floor(y/s)+(vertical?area.x:area.y);
                img.setRGB(x,y,0xff000000 | (Math.floorMod(u,64)*4<<16) | (Math.floorMod(v,64)*4<<8) | 80);
            }
            return img;
        };
        Point origin=new Point(100,90);
        GlassBackdropCache.Sample first=cache.region(100,60,vertical,origin,1,false,source);
        BufferedImage original=paintSample(first,100,60);
        GlassBackdropCache.Sample expanded=cache.region(172,60,vertical,origin,1,true,source);
        BufferedImage wider=paintSample(expanded,172,60);
        check(first.image()==expanded.image(),"Expanding bar must use same sampled image");
        for(int y=3;y<57;y++) for(int x=3;x<97;x++)
            check(original.getRGB(x,y)==wider.getRGB(x,y),"Expansion stretched a reference-space pixel");
        Point moved=new Point(origin.x+(vertical?0:12),origin.y+(vertical?12:0));
        GlassBackdropCache.Sample shifted=cache.region(172,60,vertical,moved,1,true,source);
        BufferedImage translated=paintSample(shifted,172,60);
        check(captures.get()==1,"Expansion/short translation caused repeated capture");
        for(int y=3;y<57;y++) for(int x=3;x<85;x++)
            check(wider.getRGB(x+12,y)==translated.getRGB(x,y),"Dock motion changed optical scale");
        cache.clear();
    }
    private static BufferedImage paintSample(GlassBackdropCache.Sample sample,int w,int h) {
        BufferedImage img=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=img.createGraphics(); sample.paint(g);g.dispose();return img;
    }

    private static void opticalRaster() {
        GlassLensRaster raster=new GlassLensRaster();
        GlassBackdropCache.Sample sample=new GlassBackdropCache.Sample(new BufferedImage(200,100,BufferedImage.TYPE_INT_ARGB),0,0);
        RoundRectangle2D.Float lens=new RoundRectangle2D.Float(20,20,120,68,68,68);
        BufferedImage output=new BufferedImage(180,120,BufferedImage.TYPE_INT_ARGB);
        java.util.function.Consumer<Graphics2D> stripes=g->{
            g.setColor(Color.WHITE);g.fillRect(0,0,180,120);
            g.setColor(Color.BLACK);g.fillRect(100,0,2,120);
            g.setColor(Color.RED);g.fillRect(5,0,9,120);
        };
        Graphics2D g=output.createGraphics();
        raster.paint(g,lens,sample,1,2,1.28f,stripes);
        // The line at x=100 lies 20 px from center; 1.28x makes it appear near x=106.
        Color magnified=new Color(output.getRGB(106,54),true);
        check(magnified.getAlpha()>200 && magnified.getRed()<120,"Droplet must actually magnify scene content");
        long begin=System.nanoTime();
        for(int i=0;i<100;i++) raster.paint(g,lens,sample,1,2,1.28f,stripes);
        long elapsed=System.nanoTime()-begin;
        g.dispose();
        check(raster.buildCount()==1,"Stationary lens rebuilt despite identical scene");
        check(output.getRGB(10,10)>>>24==0,"Optics escaped lens bounds");
        System.out.printf("100 stable local lens draws: %.2f ms; raster builds: %d%n",elapsed/1e6,raster.buildCount());
    }

    private static void actualBar() {
        GlassBar bar=new GlassBar(BoardTheme.font(14),Point::new,g->{
            g.setColor(new Color(60,90,125));g.fillRect(-100,-100,1500,500);
            g.setColor(Color.BLACK);g.fillRect(70,0,3,120);
        },()->1L);
        GlassBar.Item tool=bar.action("Pen",Icons::pencil,()->{},()->true);
        bar.action("Eraser",Icons::eraser,()->{},()->true);bar.finish();bar.setHoverToolLabels(true,()->{});
        int center=bar.centerOf(tool).x, y=bar.centerOf(tool).y;
        BufferedImage before=paintBar(bar);
        bar.dispatchEvent(new java.awt.event.MouseEvent(bar,java.awt.event.MouseEvent.MOUSE_MOVED,1,0,center,y,0,false));
        // Deterministic non-window stepping, just as the existing optical regression does.
        for(int i=0;i<12;i++) invoke(bar,"step",new Class<?>[0]);
        BufferedImage after=paintBar(bar);
        int lensCenter=bar.centerOf(tool).x*2;
        check((before.getRGB(center*2,(GlassBar.MARGIN-4)*2)>>>24)==0,"Resting bar must have transparent margin");
        check((after.getRGB(lensCenter,(GlassBar.MARGIN-4)*2)>>>24)>40,"Hover droplet must extend visibly outside the resting capsule");
        check((after.getRGB(4,4)>>>24)==0,"Hover must retain transparent outer margin");
        ((Timer)field(bar,"timer")).stop();
    }
    private static BufferedImage paintBar(GlassBar bar) {
        BufferedImage img=new BufferedImage(bar.getWidth()*2,bar.getHeight()*2,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=img.createGraphics();g.scale(2,2);bar.paint(g);g.dispose();return img;
    }

    private static void panelMotion() {
        // Use a real component hierarchy and ink, not a snapshot or a fake coordinate model.
        DrawingCanvas canvas=new DrawingCanvas();canvas.setGridVisible(false);canvas.setBackground(Color.MAGENTA);
        try {canvas.restoreCanvasState(new CanvasState(List.of(new CanvasState.Ink(List.of(
                new CanvasState.Point(60,280,0),new CanvasState.Point(1000,280,1)),Color.BLACK.getRGB())),
                List.of(),List.of(),1,0,0));} catch (java.io.IOException e){throw new AssertionError(e);}
        JPanel pdf=new JPanel();pdf.setBackground(Color.WHITE);
        BoardWindow board=new BoardWindow(canvas,canvas,h->new HistoryPanel(new HistoryViewModel(),h),pdf);
        JFrame frame=new JFrame("Panel motion regression");
        try {
            frame.setContentPane(board);frame.setSize(1400,850);frame.setVisible(true);board.doLayout();
            double initial=screenX(board,canvas);
            motion(board,canvas,"setHistoryOpen",true);
            motion(board,canvas,"setPdfOpen",true);
            motion(board,canvas,"setHistoryOpen",false);
            motion(board,canvas,"setPdfOpen",false);
            check(Math.abs(screenX(board,canvas)-initial)<.01,"Four transitions changed final Canvas position");
            check(canvas.exportCanvasState().ink().size()==1,"Lost stroke during animation");
            // Reverse an in-progress PDF transition and end at original view coordinates.
            invoke(board,"setPdfOpen",new Class<?>[]{boolean.class,boolean.class},true,true);board.doLayout();
            tick(board,.4);
            invoke(board,"setPdfOpen",new Class<?>[]{boolean.class,boolean.class},false,true);board.doLayout();
            tick(board,.4);tick(board,1.01);
            check(Math.abs(screenX(board,canvas)-initial)<.01,"Reversed transition left residual pan");
            check(!board.isPanelAnimating(),"Panel timer did not finish");
        } finally {frame.dispose();}
    }
    private static double screenX(BoardWindow board,DrawingCanvas canvas) {
        return SwingUtilities.convertPoint(canvas,0,0,board).x+canvas.worldToScreen(new Rectangle2D.Double(100,200,1,1)).getX();
    }
    private static void motion(BoardWindow board,DrawingCanvas canvas,String action,boolean open) {
        Rectangle start=board.pageBounds();double original=screenX(board,canvas);
        invoke(board,action,new Class<?>[]{boolean.class,boolean.class},open,true);board.doLayout();
        int stableWidth=canvas.getWidth();
        for(double t:new double[]{.15,.35,.55,.75,.95,1.01}) {
            tick(board,t);
            Rectangle visible=board.pageBounds();
            double expected=original+visible.x-start.x+visible.getMaxX()-start.getMaxX();
            check(Math.abs(screenX(board,canvas)-expected)<.01,"Canvas and panel disagree at frame "+t+" of "+action);
            if(t<1) {
                check(canvas.getWidth()==stableWidth,"Live Canvas was resized mid-animation");
                CanvasTransitionMask mask=(CanvasTransitionMask)field(board,"transitionMask");
                check(mask.isVisible()&&!mask.contains(visible.x+Math.max(0,visible.width/2),400),"Mask steals Canvas input");
                if(visible.x>6) {
                    // Render the actual board at an excluded point, proving the transparent History
                    // cannot reveal the magenta Canvas underneath during the transition.
                    BufferedImage img=new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g=img.createGraphics();g.translate(-3,-400);board.paint(g);g.dispose();
                    check(img.getRGB(0,0)!=Color.MAGENTA.getRGB(),"Canvas shows through History");
                }
            }
        }
        check(!board.isPanelAnimating(),"Panel transition failed to settle");
        check(canvas.getBounds().equals(board.pageBounds()),"Final live Canvas must have exact viewport bounds");
    }
    // springs now: one ~frame per call, 1.0+ runs until it settles
    private static void tick(BoardWindow board,double fraction) {
        int frames=fraction>=1?400:1;
        for(int i=0;i<frames&&(i==0||board.isPanelAnimating());i++)
            invoke(board,"stepPanels",new Class<?>[]{float.class},.05f);
    }
}
