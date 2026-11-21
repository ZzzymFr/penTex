package view.canvas;

import data_access.InMemoryGraphSessionDataAccessObject;
import entity.LatexFunctionParser;
import interface_adapter.graph.GraphController;
import interface_adapter.graph.GraphExploreController;
import interface_adapter.graph.GraphPresenter;
import interface_adapter.graph.GraphState;
import interface_adapter.graph.GraphViewModel;
import org.junit.jupiter.api.Test;
import use_case.explore_graph.ExploreGraphInteractor;
import use_case.graph_function.GraphFunctionInteractor;
import view.DrawingCanvas;
import view.FormulaCapture;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

// a formula dropped on a live graph card joins that graph: same card, both functions, title "a & b"
class PopupGraphAddFunctionTest {
    private final List<String> errors = new ArrayList<>();
    private final Notices notices = new Notices() {
        @Override public void success(String words, String latex) { }
        @Override public void error(String title, String reason) { errors.add(reason); }
        @Override public void clearError() { }
        @Override public Runnable progress(String words, String latex) { return () -> { }; }
    };
    private final SharedCanvas shared = new SharedCanvas(new DrawingCanvas());
    private final JPanel area = new JPanel(null);
    private final GraphViewModel viewModel = new GraphViewModel();
    private CountDownLatch saved;

    private PopupGraphing tool() {
        shared.getDrawingCanvas().setSize(900, 600);
        area.setSize(900, 600);
        InMemoryGraphSessionDataAccessObject session = new InMemoryGraphSessionDataAccessObject();
        GraphPresenter presenter = new GraphPresenter(viewModel);
        GraphSession kit = new GraphSession(
                new GraphController(new GraphFunctionInteractor(new LatexFunctionParser(), session, presenter)),
                new GraphExploreController(new ExploreGraphInteractor(session, presenter)), viewModel);
        return new PopupGraphing(shared, () -> kit, notices, area, (capture, latex) -> saved.countDown());
    }

    private void graph(PopupGraphing tool, String latex) throws Exception {
        saved = new CountDownLatch(1);
        SwingUtilities.invokeAndWait(() -> tool.graph(latex, new Rectangle(40, 40, 80, 30),
                new FormulaCapture(1, 1, new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB))));
        assertTrue(saved.await(5, TimeUnit.SECONDS));
        SwingUtilities.invokeAndWait(() -> { });
    }

    // waits until the card shows this many functions (or an error came back)
    private GraphState waitFor(int functions) throws Exception {
        for (int i = 0; i < 100; i++) {
            SwingUtilities.invokeAndWait(() -> { });
            GraphState s = viewModel.getState();
            if (s.getFunctions().size() == functions || !errors.isEmpty()) {
                return s;
            }
            Thread.sleep(50);
        }
        return viewModel.getState();
    }

    @Test
    void droppedFormulaJoinsTheGraphOnTheSameCard() throws Exception {
        PopupGraphing tool = tool();
        graph(tool, "x^2");
        Object tag = shared.getDrawingCanvas().getGraphTag();
        assertTrue(tool.owns(tag));
        Rectangle2DHolder before = new Rectangle2DHolder(shared.getDrawingCanvas().getGraphCardOnScreen(tag));

        SwingUtilities.invokeAndWait(() -> assertTrue(tool.addTo(tag, "\\sin(x)")));
        GraphState s = waitFor(2);

        assertTrue(errors.isEmpty(), errors.toString());
        assertEquals(List.of("x^2", "\\sin(x)"), s.getFunctions());
        assertEquals(2, s.getCurveLayers().size(), "one line each");
        assertTrue(s.getLatex().contains("\\&"), s.getLatex());
        assertEquals(1, shared.getDrawingCanvas().graphCount(), "still one card");
        assertEquals(before.rect, shared.getDrawingCanvas().getGraphCardOnScreen(tag), "in the same spot");
    }

    @Test
    void aSurfaceCantJoinACurve() throws Exception {
        PopupGraphing tool = tool();
        graph(tool, "x^2");
        Object tag = shared.getDrawingCanvas().getGraphTag();
        SwingUtilities.invokeAndWait(() -> tool.addTo(tag, "z=x+y"));
        GraphState s = waitFor(2);
        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).contains("can't share"), errors.toString());
        assertEquals(List.of("x^2"), s.getFunctions(), "the graph stays as it was");
    }

    @Test
    void cardsLightUpOnlyWhenTheyCanTakeTheFormula() throws Exception {
        PopupGraphing tool = tool();
        graph(tool, "x^2");
        Object tag = shared.getDrawingCanvas().getGraphTag();
        SwingUtilities.invokeAndWait(() -> tool.showDropTarget(tag));
        assertTrue(((view.graph.PlotCard) tool.card()).isDropHighlighted());
        SwingUtilities.invokeAndWait(() -> tool.showDropTarget(null));
        assertFalse(((view.graph.PlotCard) tool.card()).isDropHighlighted());
    }

    private record Rectangle2DHolder(java.awt.geom.Rectangle2D rect) {
    }
}
