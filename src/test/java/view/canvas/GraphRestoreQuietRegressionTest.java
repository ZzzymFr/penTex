package view.canvas;

import org.junit.jupiter.api.Test;
import view.DrawingCanvas;

import javax.swing.JPanel;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Save/Open restoration must not trigger the new-graph Library event or toast storms. */
class GraphRestoreQuietRegressionTest {
    private static final class Toasts implements Notices {
        final AtomicInteger successes = new AtomicInteger(), progress = new AtomicInteger(), errors = new AtomicInteger();
        @Override public void success(String words, String latex) {successes.incrementAndGet();}
        @Override public void error(String title, String reason) {errors.incrementAndGet();}
        @Override public void clearError() { }
        @Override public Runnable progress(String words,String latex) {progress.incrementAndGet();return () -> {} ;}
    }
    private static final class Tool extends PopupCardTool<Integer> {
        final AtomicInteger published = new AtomicInteger();
        Tool(SharedCanvas canvas, Toasts toasts, JPanel area) {
            super(canvas, () -> 1, toasts, area, (capture,latex) -> {}, "graph-test", "err", "drawing", "graph of");
        }
        @Override Live newLive(Integer ignored) {
            return new Live(ignored, new view.graph.StubPlotCard()) {
                private volatile boolean content;
                @Override void execute(List<String> functions, double[] x, double[] y, double[] z) {
                    content = true; stateChanged();
                }
                @Override String error() {return null;}
                @Override boolean hasContent() {return content;}
                @Override String latex() {return "y=x";}
                @Override double[] window() {return new double[]{-5,5,-5,5};}
                @Override boolean followsY() {return false;}
                @Override BufferedImage firstPicture() {return new BufferedImage(80,60,2);}
                @Override void publishedNew(BufferedImage image) {published.incrementAndGet();}
            };
        }
    }
    private static Tool tool(Toasts toasts, CountDownLatch done) {
        DrawingCanvas drawing = new DrawingCanvas(); drawing.setSize(800,600);
        JPanel area = new JPanel(null); area.setSize(800,600); area.add(drawing);
        drawing.setBounds(0,0,800,600);
        Tool tool = new Tool(new SharedCanvas(drawing), toasts, area);
        tool.setOnShown(done::countDown);
        return tool;
    }
    @Test void restoreIsSilentAndDoesNotPublishDuplicateLibraryGraph() throws Exception {
        Toasts toast = new Toasts(); CountDownLatch shown = new CountDownLatch(1);
        Tool tool = tool(toast, shown);
        tool.restore(List.of("y=x"), new double[]{-5,5}, new double[]{-5,5}, null,
                new Point(110,90), null);
        assertTrue(shown.await(8, TimeUnit.SECONDS));
        assertEquals(0, toast.progress.get());
        assertEquals(0, toast.successes.get());
        assertEquals(0, tool.published.get());
    }
    @Test void newGraphPublishesExactlyOnceWhenSuccessfullyShown() throws Exception {
        Toasts toast = new Toasts(); CountDownLatch shown = new CountDownLatch(1);
        Tool tool = tool(toast, shown);
        tool.run("y=x", new Rectangle(15,20,60,25), null);
        assertTrue(shown.await(8, TimeUnit.SECONDS));
        assertEquals(1, tool.published.get());
        assertEquals(1, toast.successes.get());
        assertEquals(1, toast.progress.get());
    }
}
