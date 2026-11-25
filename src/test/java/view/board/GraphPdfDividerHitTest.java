package view.board;

import entity.graph.GraphDefinition;
import interface_adapter.history.HistoryItem;
import interface_adapter.history.HistoryViewModel;
import org.junit.jupiter.api.Test;
import view.DrawingCanvas;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** The split PDF panel is the target, never the Canvas or dividing bar. */
class GraphPdfDividerHitTest {
    private static final class Destination extends JPanel implements PdfWorkspacePort {
        int transfers;
        @Override public void fitWhenOpened() { }
        @Override public void addHistoryItem(HistoryItem item) { }
        @Override public boolean dropHistoryItem(HistoryItem item, Point screen) {return false;}
        @Override public boolean dropInteractiveGraph(GraphDefinition graph, BufferedImage thumbnail, Point screen) {
            transfers++;
            return true;
        }
    }
    @Test void transfersOnlyAfterCursorActuallyCrossesVisibleDivider() throws Exception {
        if (GraphicsEnvironment.isHeadless()) return;
        AtomicReference<Throwable> issue = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            JFrame frame = new JFrame();
            try {
                Destination destination = new Destination();
                BoardWindow board = new BoardWindow(new JPanel(), new DrawingCanvas(),
                        host -> new HistoryPanel(new HistoryViewModel(), host), destination);
                frame.setContentPane(board);
                frame.setSize(1100, 680);
                frame.setLocation(75, 70);
                frame.setVisible(true);
                board.setPdfOpen(true);
                board.doLayout();
                Point pdfPoint = new Point(destination.getX()+destination.getWidth()/2, 300);
                Point canvasPoint = new Point(Math.max(0, destination.getX()-60), 300);
                SwingUtilities.convertPointToScreen(pdfPoint, board);
                SwingUtilities.convertPointToScreen(canvasPoint, board);
                assertTrue(board.acceptsInteractiveGraphDrop(pdfPoint));
                assertTrue(!board.acceptsInteractiveGraphDrop(canvasPoint));
                var image = new BufferedImage(80, 50, BufferedImage.TYPE_INT_ARGB);
                var graph = GraphDefinition.of("y=x");
                assertTrue(board.dropInteractiveGraph(graph, image, pdfPoint));
                assertEquals(1, destination.transfers);
                assertTrue(!board.dropInteractiveGraph(graph, image, canvasPoint));
                assertEquals(1, destination.transfers);
                board.setPdfOpen(false);
                board.doLayout();
                assertTrue(!board.acceptsInteractiveGraphDrop(pdfPoint));
            } catch(Throwable error) {issue.set(error);}
            finally {frame.dispose();}
        });
        if (issue.get() != null) throw new AssertionError("PDF divider handoff", issue.get());
    }
}
