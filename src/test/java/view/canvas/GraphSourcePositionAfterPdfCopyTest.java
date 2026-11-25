package view.canvas;

import org.junit.jupiter.api.Test;
import view.DrawingCanvas;

import javax.swing.JPanel;
import java.awt.Point;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Copying to PDF must not leave the source Canvas graph displaced at the divider. */
class GraphSourcePositionAfterPdfCopyTest {
    private static final class Tool extends PopupCardTool<Integer> {
        Live source;
        Tool(SharedCanvas canvas, JPanel area) {
            super(canvas, () -> 1, new Notices() {
                @Override public void success(String a,String b) { }
                @Override public void error(String a,String b) { }
                @Override public void clearError() { }
                @Override public Runnable progress(String a,String b) {return () -> {};}
            }, area, (capture,latex) -> {}, "graph-source-test", "error", "progress", "success");
        }
        @Override Live newLive(Integer number) {
            source = new Live(number, new view.graph.StubPlotCard()) {
                private volatile boolean hasGraph;
                @Override void execute(List<String> functions,double[] x,double[] y,double[] z) {
                    hasGraph=true;stateChanged();
                }
                @Override String error() {return null;}
                @Override boolean hasContent() {return hasGraph;}
                @Override String latex() {return "y=x";}
                @Override double[] window() {return new double[]{-5,5,-5,5};}
                @Override boolean followsY() {return false;}
                @Override BufferedImage firstPicture() {return new BufferedImage(200,140,2);}
                // This test uses a fake graph with no coordinate lookup results.
                @Override List<String> lookupNames() {return List.of("x", "y");}
                @Override String lookupHint() {return "";}
                @Override void lookup(List<String> typed) { }
                @Override void clearLookup() { }
                @Override interface_adapter.graph.GraphLookup lookupResult() {return null;}
            };
            return source;
        }
    }
    @Test void restoringDragStartLocationPreservesOriginalCanvasGraph() throws Exception {
        DrawingCanvas drawing=new DrawingCanvas(); drawing.setSize(850,600);
        JPanel area=new JPanel(null);area.setSize(850,600);drawing.setBounds(0,0,850,600);area.add(drawing);
        Tool tool=new Tool(new SharedCanvas(drawing),area);
        CountDownLatch shown=new CountDownLatch(1);tool.setOnShown(shown::countDown);
        tool.restore(List.of("y=x"),new double[]{-5,5},new double[]{-5,5},null,
                new Point(90,120),null);
        assertTrue(shown.await(8, TimeUnit.SECONDS));
        var source=tool.source;
        Rectangle2D start=drawing.getGraphCardOnScreen(source);
        assertTrue(start != null);
        source.captureGraphDragOrigin();
        drawing.moveGraphBy(source,110,34);
        Rectangle2D displaced=drawing.getGraphCardOnScreen(source);
        assertTrue(displaced.getX()!=start.getX());
        source.restoreGraphDragOrigin();
        source.forgetGraphDragOrigin();
        Rectangle2D after=drawing.getGraphCardOnScreen(source);
        assertEquals((float)start.getX(),(float)after.getX(),0.001f);
        assertEquals((float)start.getY(),(float)after.getY(),0.001f);
        assertEquals(1,drawing.graphCount());
    }
}
