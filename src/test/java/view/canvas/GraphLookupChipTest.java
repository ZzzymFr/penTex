package view.canvas;

import data_access.InMemoryGraphSessionDataAccessObject;
import entity.LatexFunctionParser;
import interface_adapter.graph.GraphController;
import interface_adapter.graph.GraphExploreController;
import interface_adapter.graph.GraphLookup;
import interface_adapter.graph.GraphPresenter;
import interface_adapter.graph.GraphState;
import interface_adapter.graph.GraphViewModel;
import org.junit.jupiter.api.Test;
import use_case.explore_graph.ExploreGraphInteractor;
import use_case.graph_function.GraphFunctionInteractor;
import view.DrawingCanvas;
import view.FormulaCapture;
import view.graph.GraphCard;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

// the find tag on the plot's top-right corner: type x, get y
class GraphLookupChipTest {
    private final List<List<String>> asked = new ArrayList<>();
    private int cleared;
    private final GraphLookupChip chip = new GraphLookupChip(new GraphLookupChip.Asker() {
        @Override
        public void find(List<String> typed) {
            asked.add(typed);
        }

        @Override
        public void clear() {
            cleared++;
        }
    });
    private final GraphLookupChip.Panel panel = (GraphLookupChip.Panel) chip.panel();

    private static GraphLookup answer(String y) {
        return new GraphLookup(List.of("x", "y"), new double[]{2, Double.NaN},
                List.of(new GraphLookup.Point(2, 4, Double.NaN, 0, "(2, 4)")), List.of(),
                new String[]{null, y}, List.of(new GraphLookup.Line("y = " + y, -1)), null, "x = 2 → y = " + y);
    }

    @Test
    void tagSitsOnThePlotsTopRightCorner() throws Exception {
        GraphViewModel viewModel = new GraphViewModel();
        InMemoryGraphSessionDataAccessObject session = new InMemoryGraphSessionDataAccessObject();
        GraphPresenter presenter = new GraphPresenter(viewModel);
        GraphCard card = new GraphCard(viewModel, new GraphExploreController(new ExploreGraphInteractor(session, presenter)));
        new GraphFunctionInteractor(new LatexFunctionParser(), session, presenter)
                .execute(new use_case.graph_function.GraphFunctionInputData("x^2", -10, 10, -10, 10));
        SwingUtilities.invokeAndWait(() -> { });
        card.setSize(440, 300);
        Rectangle at = new Rectangle(100, 50, 440, 300);
        chip.placeOn(at, card);
        Rectangle plot = card.plotBounds();
        Rectangle tag = chip.getBounds();
        assertTrue(chip.isVisible());
        assertTrue(tag.y >= at.y + plot.y && tag.y < at.y + plot.y + 20, "top of the plot, under the header: " + tag);
        assertTrue(tag.x + tag.width <= at.x + at.width && tag.x + tag.width > at.x + at.width - 20,
                "on the right: " + tag);
    }

    @Test
    void findSendsWhatsTypedAndTheAnswerFillsTheBlankBox() {
        chip.setAxes(List.of("x", "y"), 1, "Type x to get y.");
        assertEquals("x → y", chip.tagText());
        panel.row("x").box.setText("2");
        panel.clickFind();
        assertEquals(List.of(List.of("2", "")), asked);
        assertEquals(List.of("2", ""), chip.lastQuery());

        chip.showResult(answer("4"));
        assertEquals("4", panel.row("y").box.getText());
        assertTrue(panel.row("y").answer);
        assertEquals("x = 2 → y = 4", chip.tagText());
        assertEquals(List.of("y = 4"), panel.shownLines());

        panel.row("x").box.setText("3");   // a new question: the old answer goes
        assertEquals("", panel.row("y").box.getText());
        panel.clickFind();
        assertEquals(List.of("3", ""), asked.get(1), "an answer in a box is never sent as typed");
    }

    @Test
    void onlyOneBoxKeepsWhatWasTypedOnAFlatGraph() {
        chip.setAxes(List.of("x", "y"), 1, "");
        panel.row("x").box.setText("2");
        panel.row("y").box.setText("5");
        assertEquals("", panel.row("x").box.getText(), "typing y gives up x");
        assertEquals("5", panel.row("y").box.getText());
    }

    @Test
    void threeDKeepsTwoTypedBoxesTheOldestGivesWay() {
        chip.setAxes(List.of("x", "y", "z"), 2, "");
        assertEquals(3, panel.rowCount());
        assertEquals("x y → z", chip.tagText());
        panel.row("x").box.setText("1");
        panel.row("y").box.setText("2");
        panel.row("z").box.setText("3");
        assertEquals("", panel.row("x").box.getText());
        assertEquals("2", panel.row("y").box.getText());
        assertEquals("3", panel.row("z").box.getText());
    }

    @Test
    void clearEmptiesTheBoxesAndTakesTheMarksOff() {
        chip.setAxes(List.of("x", "y"), 1, "");
        panel.row("x").box.setText("2");
        panel.clickFind();
        chip.showResult(answer("4"));
        panel.clickClear();
        assertEquals(1, cleared);
        assertEquals("", panel.row("x").box.getText());
        assertEquals("", panel.row("y").box.getText());
        assertNull(chip.lastQuery());
        assertEquals("x → y", chip.tagText());
    }

    @Test
    void aProblemIsShownInsteadOfLines() {
        chip.setAxes(List.of("x", "y"), 1, "");
        chip.showResult(new GraphLookup(List.of("x", "y"), new double[]{Double.NaN, 50}, List.of(), List.of(),
                new String[2], List.of(), "Nothing on the graph with y = 50 in view.", "y = 50 → none"));
        assertNotNull(panel.message());
        assertFalse(panel.shownLines().isEmpty());
        assertEquals("y = 50 → none", chip.tagText());
    }

    // ---- through a live card: the boxes reach the use case and the answer comes back to the chip

    private final List<String> errors = new ArrayList<>();
    private final Notices notices = new Notices() {
        @Override public void success(String words, String latex) { }
        @Override public void error(String title, String reason) { errors.add(reason); }
        @Override public void clearError() { }
        @Override public Runnable progress(String words, String latex) { return () -> { }; }
    };

    private static GraphState waitUntil(GraphViewModel vm, Predicate<GraphState> ok) throws Exception {
        for (int i = 0; i < 100; i++) {
            SwingUtilities.invokeAndWait(() -> { });
            if (ok.test(vm.getState())) {
                SwingUtilities.invokeAndWait(() -> { });
                return vm.getState();
            }
            Thread.sleep(30);
        }
        return vm.getState();
    }

    // Model publication and Swing repaint are separate asynchronous steps. Wait for
    // the actual displayed result, not just the ViewModel state, with a firm deadline.
    private static String[] awaitDisplayedLookup(GraphLookupChip live, GraphLookupChip.Panel panel,
                                                  String answer, String summary) throws Exception {
        String[] displayed = new String[2];
        for (int i = 0; i < 100; i++) {
            SwingUtilities.invokeAndWait(() -> {
                displayed[0] = panel.row("y").box.getText();
                displayed[1] = live.tagText();
            });
            if (answer.equals(displayed[0]) && summary.equals(displayed[1])) {
                return displayed;
            }
            Thread.sleep(30);
        }
        return displayed; // The unchanged expected-value assertions below still fail on a real bug.
    }

    @Test
    void liveGraphAnswersThroughItsOwnUseCase() throws Exception {
        SharedCanvas shared = new SharedCanvas(new DrawingCanvas());
        JPanel area = new JPanel(null);
        shared.getDrawingCanvas().setSize(900, 600);
        area.setSize(900, 600);
        GraphViewModel viewModel = new GraphViewModel();
        InMemoryGraphSessionDataAccessObject session = new InMemoryGraphSessionDataAccessObject();
        GraphPresenter presenter = new GraphPresenter(viewModel);
        GraphSession kit = new GraphSession(
                new GraphController(new GraphFunctionInteractor(new LatexFunctionParser(), session, presenter)),
                new GraphExploreController(new ExploreGraphInteractor(session, presenter)), viewModel);
        CountDownLatch saved = new CountDownLatch(1);
        PopupGraphing tool = new PopupGraphing(shared, () -> kit, notices, area, (c, l) -> saved.countDown());
        SwingUtilities.invokeAndWait(() -> tool.graph("y=x^2", new Rectangle(40, 40, 80, 30),
                new FormulaCapture(1, 1, new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB))));
        assertTrue(saved.await(5, TimeUnit.SECONDS));
        SwingUtilities.invokeAndWait(() -> { });

        GraphLookupChip live = tool.lookupChip();
        assertTrue(live.isVisible(), "the find tag is on the card");
        GraphLookupChip.Panel p = (GraphLookupChip.Panel) live.panel();
        SwingUtilities.invokeAndWait(() -> {
            p.row("x").box.setText("3");
            p.clickFind();
        });
        GraphState s = waitUntil(viewModel, st -> st.getLookup() != null);
        assertNotNull(s.getLookup());
        String[] displayed = awaitDisplayedLookup(live, p, "9", "x = 3 → y = 9");
        assertEquals("9", displayed[0]);
        assertEquals("x = 3 → y = 9", displayed[1]);

        // a new range redraws the graph: the find is asked again on the new drawing
        SwingUtilities.invokeAndWait(() -> tool.rangeChip().resetToDefault());
        SwingUtilities.invokeAndWait(() -> {
            p.row("x").box.setText("");
            p.row("y").box.setText("4");
            p.clickFind();
        });
        s = waitUntil(viewModel, st -> st.getLookup() != null && st.getLookup().getPoints().size() == 2);
        assertEquals(2, s.getLookup().getPoints().size(), "x = ±2");
        assertTrue(errors.isEmpty(), errors.toString());
    }
}
