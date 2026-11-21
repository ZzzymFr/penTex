package view.canvas;

import data_access.InMemoryGraphSessionDataAccessObject;
import entity.LatexFunctionParser;
import interface_adapter.graph.GraphExploreController;
import interface_adapter.graph.GraphPresenter;
import interface_adapter.graph.GraphViewModel;
import org.junit.jupiter.api.Test;
import use_case.explore_graph.ExploreGraphInteractor;
import use_case.graph_function.GraphFunctionInputData;
import use_case.graph_function.GraphFunctionInteractor;
import view.graph.GraphCard;

import javax.swing.SwingUtilities;
import java.awt.Rectangle;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

// the range as a tag on the plot's corner, and the panel it opens
class GraphRangeChipTest {
    private final AtomicInteger applied = new AtomicInteger();
    private final GraphRangeChip chip = new GraphRangeChip(applied::incrementAndGet);
    private final GraphRangeChip.Panel panel = (GraphRangeChip.Panel) chip.panel();

    private static double[] r(double lo, double hi) {
        return new double[]{lo, hi};
    }

    @Test
    void tagSitsOnThePlotsBottomLeftCornerNotInTheHeader() throws Exception {
        GraphViewModel viewModel = new GraphViewModel();
        InMemoryGraphSessionDataAccessObject session = new InMemoryGraphSessionDataAccessObject();
        GraphPresenter presenter = new GraphPresenter(viewModel);
        GraphCard card = new GraphCard(viewModel, new GraphExploreController(new ExploreGraphInteractor(session, presenter)));
        new GraphFunctionInteractor(new LatexFunctionParser(), session, presenter)
                .execute(new GraphFunctionInputData("x^2", -10, 10, -10, 10));
        SwingUtilities.invokeAndWait(() -> { });
        card.setSize(440, 300);
        Rectangle at = new Rectangle(100, 50, 440, 300);
        chip.placeOn(at, card);
        Rectangle plot = card.plotBounds();
        Rectangle tag = chip.getBounds();
        assertTrue(chip.isVisible());
        assertTrue(tag.y >= at.y + plot.y, "below the header: " + tag);
        assertTrue(tag.y + tag.height <= at.y + at.height, "inside the card: " + tag);
        assertTrue(tag.y + tag.height > at.y + at.height - 30, "at the bottom: " + tag);
        assertTrue(tag.x >= at.x && tag.x < at.x + 20, "on the left: " + tag);
    }

    @Test
    void flatGraphShowsTheWindowItDrew() {
        chip.showWindow(r(-10, 10), r(-11, 11), null, true, false);
        assertEquals("x −10 – 10  y −11 – 11", chip.tagText());
        assertArrayEquals(r(-11, 11), chip.range("y"));
    }

    @Test
    void surfaceKeepsYAndZOnAutoAndGetsAZRow() {
        chip.setShowsZ(true);
        chip.showWindow(r(-1.93, 1.93), r(-1.93, 1.93), r(-4, 7), false, true);
        assertEquals("x −1.93 – 1.93  y auto  z auto", chip.tagText());
        assertNull(chip.range("y"));
        assertNull(chip.range("z"));
        assertEquals(3, panel.rowCount());
        chip.setShowsZ(false);
        assertEquals(2, panel.rowCount());
        assertNull(chip.range("z"), "a flat graph never sends a z range");
    }

    @Test
    void doneSendsTheTypedRangeAndRedraws() {
        chip.showWindow(r(-10, 10), r(-11, 11), null, true, false);
        panel.open();
        GraphRangeChip.Panel.Row x = panel.row("x");
        x.min.setText("−5");   // typographic minus is fine
        x.max.setText("5");
        panel.clickDone();
        assertFalse(panel.isVisible());
        assertEquals(1, applied.get());
        assertArrayEquals(r(-5, 5), chip.range("x"));
    }

    @Test
    void badBoxesKeepThePanelOpenAndSayWhy() {
        chip.showWindow(r(-10, 10), r(-11, 11), null, true, false);
        panel.open();
        GraphRangeChip.Panel.Row y = panel.row("y");
        y.min.setText("3");
        y.max.setText("1");
        panel.clickDone();
        assertTrue(panel.isVisible());
        assertEquals(0, applied.get());
        assertNotNull(panel.error());
        assertTrue(y.min.bad && y.max.bad);
        y.max.setText("abc");
        panel.clickDone();
        assertTrue(panel.error().contains("number"));
        assertArrayEquals(r(-11, 11), chip.range("y"), "nothing applied");
        panel.close();
    }

    @Test
    void autoHidesTheBoxesAndSticksUntilYouPan() {
        chip.showWindow(r(-10, 10), r(-11, 11), null, true, false);
        panel.open();
        GraphRangeChip.Panel.Row y = panel.row("y");
        y.toggleAuto();
        assertFalse(y.min.isVisible());
        assertFalse(y.max.isVisible());
        panel.clickDone();
        assertNull(chip.range("y"));
        assertEquals(1, applied.get());
        chip.showWindow(r(-10, 10), r(-3, 120), null, true, false);   // the redraw
        assertNull(chip.range("y"), "still on Auto after the graph picked");
        assertTrue(chip.tagText().endsWith("y auto"));
        chip.showWindow(r(-8, 12), r(-2, 121), null, true, true);     // a pan
        assertArrayEquals(r(-2, 121), chip.range("y"));
    }

    @Test
    void switchingAutoOffStartsFromWhatYouSee() {
        chip.setShowsZ(true);
        chip.showWindow(r(-10, 10), r(-10, 10), r(-4, 7), false, false);
        panel.open();
        GraphRangeChip.Panel.Row z = panel.row("z");
        assertTrue(z.auto);
        z.toggleAuto();
        assertEquals("-4", z.min.getText());
        assertEquals("7", z.max.getText());
        panel.close();
    }

    @Test
    void resetPutsEveryAxisBackOnAutoAndRedraws() {
        chip.setShowsZ(true);
        chip.showWindow(r(-10, 10), r(-10, 10), r(-4, 7), true, true);
        panel.open();
        panel.clickReset();
        assertFalse(panel.isVisible());
        assertEquals(1, applied.get());
        assertNull(chip.range("x"));
        assertNull(chip.range("y"));
        assertNull(chip.range("z"));
    }

    @Test
    void oldStyleTextStillRestoresARange() {
        chip.setRangeText("-5, 5", "auto");
        assertArrayEquals(r(-5, 5), chip.range("x"));
        assertNull(chip.range("y"));
    }
}
