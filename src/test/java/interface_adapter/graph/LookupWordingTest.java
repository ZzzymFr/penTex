package interface_adapter.graph;

import org.junit.jupiter.api.Test;
import use_case.lookup.LookupOutputData;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// a find put into words for the panel and the tag
class LookupWordingTest {
    private static final double NO = Double.NaN;
    private static final List<String> XY = List.of("x", "y");

    private static LookupOutputData found(double[] given, List<LookupOutputData.Point> points) {
        return new LookupOutputData(given, -1, LookupOutputData.Problem.NONE, points, List.of(), false, false);
    }

    @Test
    void oneAnswerFillsTheBlankBox() {
        GraphLookup l = LookupWording.word(found(new double[]{2, NO},
                List.of(new LookupOutputData.Point(2, 4, NO, 0))), XY, false, false);
        assertNull(l.getAnswer(0), "x was typed");
        assertEquals("4", l.getAnswer(1));
        assertEquals("y = 4", l.getLines().get(0).text());
        assertEquals("x = 2 → y = 4", l.getSummary());
        assertEquals("(2, 4)", l.getPoints().get(0).text());
        assertNull(l.getMessage());
    }

    @Test
    void severalAnswersAreListedNotBoxed() {
        GraphLookup l = LookupWording.word(found(new double[]{NO, 4}, List.of(
                new LookupOutputData.Point(-2, 4, NO, 0), new LookupOutputData.Point(2, 4, NO, 0))), XY, false, false);
        assertNull(l.getAnswer(0));
        assertEquals(List.of("x = −2", "x = 2"), l.getLines().stream().map(GraphLookup.Line::text).toList());
        assertEquals("y = 4 → 2 points", l.getSummary());
    }

    @Test
    void severalFunctionsColourTheirLines() {
        GraphLookup l = LookupWording.word(found(new double[]{1, NO}, List.of(
                new LookupOutputData.Point(1, 1, NO, 0), new LookupOutputData.Point(1, 3, NO, 1))), XY, true, false);
        assertEquals(0, l.getLines().get(0).layer());
        assertEquals(1, l.getLines().get(1).layer());
    }

    @Test
    void cutThroughA3dGraphSaysWhereItRuns() {
        LookupOutputData d = new LookupOutputData(new double[]{1, NO, NO}, -1, LookupOutputData.Problem.NONE,
                List.of(), List.of(new LookupOutputData.Path(new double[]{1, 1}, new double[]{-3, 3},
                new double[]{10, 2}, 0)), true, false);
        GraphLookup l = LookupWording.word(d, List.of("x", "y", "z"), false, false);
        assertEquals("Cut drawn: y −3 to 3,  z 2 to 10", l.getLines().get(0).text());
        assertEquals("x = 1 → cut drawn", l.getSummary());
        assertNull(l.getAnswer(1));
    }

    @Test
    void problemsComeBackAsAMessage() {
        LookupOutputData none = new LookupOutputData(new double[]{NO, 50}, -1, LookupOutputData.Problem.NOT_FOUND,
                List.of(), List.of(), true, false);
        GraphLookup l = LookupWording.word(none, XY, false, false);
        assertTrue(l.getMessage().contains("y = 50 in view"), l.getMessage());
        assertEquals("y = 50 → none", l.getSummary());

        LookupOutputData bad = new LookupOutputData(new double[]{NO, NO}, 1, LookupOutputData.Problem.BAD_NUMBER,
                List.of(), List.of(), false, false);
        assertTrue(LookupWording.word(bad, XY, false, false).getMessage().startsWith("The y box"));

        LookupOutputData unpinned = new LookupOutputData(new double[]{1, NO}, -1,
                LookupOutputData.Problem.NO_SOLUTIONS, List.of(), List.of(), false, false);
        assertTrue(LookupWording.word(unpinned, List.of("t", "y"), false, true).getMessage()
                .contains("type both t and y"));
    }
}
