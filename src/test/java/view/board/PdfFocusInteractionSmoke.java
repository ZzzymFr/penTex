package view.board;

import interface_adapter.history.HistoryViewModel;
import view.DrawingCanvas;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/** The actual toolbar callback and focus transition clock, without a visible window. */
public final class PdfFocusInteractionSmoke {
    private PdfFocusInteractionSmoke() { }
    public static void main(String[] args) throws Exception {
        verify();
        System.out.println("PDF toggle and focus timing regression passed");
    }
    public static void verify() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try { timing(); toggle(); }
            catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
        });
    }

    private static void timing() throws ReflectiveOperationException {
        long duration=240_000_000L;
        check(FocusMorph.snapshotScale(800,600,1)==1,"Normal-sized standard DPI capture stays native");
        double scale=FocusMorph.snapshotScale(1250,800,2);
        check(scale<2 && 1250*800*scale*scale<=1_200_001,"Temporary Retina capture is bounded");
        float previous=0;
        for(long t=0;t<=duration;t+=1_000_000) {
            float next=FocusMorph.progressAt(t,duration);
            check(next>=previous && next<=1,"Monotonic critically damped motion");
            previous=next;
        }
        check(FocusMorph.progressAt(0,duration)==0,"Starts at source");
        check(FocusMorph.progressAt(duration,duration)==1,"Ends on time");
        check(FocusMorph.progressAt(500_000_000L,duration)==1,"Delayed frame catches up");
        var morph=new FocusMorph();var root=new JPanel(null);root.add(morph);
        morph.setBounds(0,0,320,200);
        var image=new BufferedImage(320,200,BufferedImage.TYPE_INT_RGB);
        var from=new Rectangle(180,0,140,200);var to=new Rectangle(0,0,320,200);
        morph.begin(image,from);morph.land(image,to);
        check(morph.isVisible()&&morph.liveRect().equals(from),"Transition begins from current PDF bounds");
        long activeDuration=(long)field(morph,"duration");
        check(activeDuration==(Motion.reduced()?120_000_000L:240_000_000L),"Correct motion duration");
        Field started=FocusMorph.class.getDeclaredField("started");started.setAccessible(true);
        started.setLong(morph,System.nanoTime()-activeDuration-50_000_000L);
        Method step=FocusMorph.class.getDeclaredMethod("step");step.setAccessible(true);step.invoke(morph);
        check(!morph.isVisible() && morph.getParent()==null,"Late frame reveals live layout immediately");
        check(!((Timer)field(morph,"timer")).isRunning(),"Timer stops after deadline");
    }

    private static void toggle() throws ReflectiveOperationException {
        var page=new JPanel();var pdf=new JPanel();var canvas=new DrawingCanvas();
        var board=new BoardWindow(page,canvas,host->new HistoryPanel(new HistoryViewModel(),host),pdf);
        board.setSize(1440,800);board.doLayout();board.setHistoryOpen(true);
        try {
            for(int i=0;i<6;i++) {
                activate(board,"pdfBar","PDF");board.doLayout();
                boolean open=i%2==0;
                check(board.isPdfOpen()==open && pdf.isVisible()==open,"PDF button toggles on every click");
                check(board.isHistoryOpen(),"PDF button leaves History alone");
            }
            activate(board,"pdfBar","PDF");board.doLayout();
            activate(board,"pdfClose",null);board.doLayout();
            check(!board.isPdfOpen(),"Existing close button still works");
            board.setPdfOpen(true);board.doLayout();
            board.setPdfFullscreen(true);board.doLayout();
            check(board.isPdfFullscreen() && pdf.getWidth()==board.getWidth(),"Focus layout still fills board");
            board.setPdfFullscreen(false);board.doLayout();
            check(board.isPdfOpen() && page.isVisible(),"Exit focus restores split layout");
            activate(board,"pdfBar","PDF");board.doLayout();
            check(!board.isPdfOpen()&&!board.isPdfFullscreen(),"Toggle works after focus mode");
        } finally {
            board.setPdfOpen(false);board.setHistoryOpen(false);
            // No native peer was opened; stop any presentation timers started by activation.
            for(String name:new String[]{"pdfBar","pdfClose","historyBar","historyClose","fullscreenExit"}) {
                ((Timer)field(field(board,name),"timer")).stop();
            }
            ((Timer)field(board,"panelTimer")).stop();
            ((Timer)field(board,"saveStateDebounce")).stop();
        }
    }
    private static void activate(BoardWindow board,String barName,String label) throws ReflectiveOperationException {
        GlassBar bar=(GlassBar)field(board,barName);
        for(Object raw:(List<?>)field(bar,"items")) {
            GlassBar.Item item=(GlassBar.Item)raw;
            if(java.util.Objects.equals(label,item.getLabel())) {bar.activate(item);return;}
        }
        throw new AssertionError("Missing toolbar action: "+barName);
    }
    private static Object field(Object object,String name) throws ReflectiveOperationException {
        Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);
    }
    private static void check(boolean ok,String message) {if(!ok)throw new AssertionError(message);}
}
