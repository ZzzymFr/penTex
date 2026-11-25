package use_case.graph_check;

import entity.LatexFunctionParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphCheckInteractorTest {
    private final GraphCheckInteractor check = new GraphCheckInteractor(new LatexFunctionParser());

    @Test
    void functionsCanBeGraphed() {
        assertTrue(check.canGraph("y=x^2"));
        assertTrue(check.canGraph("f(x)=\\sin x"));
    }

    @Test
    void functionNameOnTheRightSideCanBeGraphedToo() {
        assertTrue(check.canGraph("x^2=y"));
        assertTrue(check.canGraph("2x+1 = y"));
        assertTrue(check.canGraph("\\sin x=f(x)"));
        assertTrue(check.canGraph("x = y"));
    }

    @Test
    void equationsAndPlainSumsCannot() {
        assertFalse(check.canGraph("x^2-4=0"));
        assertFalse(check.canGraph("0=x^2-4"));
        assertFalse(check.canGraph("y=x=y"));
        assertFalse(check.canGraph("1+3"));
        assertFalse(check.canGraph(""));
        assertFalse(check.canGraph(null));
    }

    @Test
    void xyEquationsWithoutALoneYCanBeGraphed() {
        assertTrue(check.canGraph("4=3y^3+2-9x^2"));
        assertTrue(check.canGraph("x^2+y^2=1"));
        assertTrue(check.canGraph("y^2=x"));
        assertTrue(check.canGraph("xy=1"));
        assertFalse(check.canGraph("y^2=4"), "only y, nothing to draw against x");
        assertFalse(check.canGraph("x^2+y^2>1"));
        assertFalse(check.canGraph("x^2+a=y^2"));
    }

    @Test
    void surfacesGetTheGraphButtonToo() {
        assertTrue(check.canGraph("z=x^2+y^2"));
        assertTrue(check.canGraph("f(x,y)=\\sin x\\cos y"));
        assertTrue(check.canGraph("xy=z"));
        assertFalse(check.canGraph("z=x+w"));
    }

    @Test
    void anyEquationInXYZGetsTheGraphButton() {
        assertTrue(check.canGraph("x+y+z=1"));
        assertTrue(check.canGraph("x^2+y^2+z^2=1"));
        assertTrue(check.canGraph("2x+3y-z=4"));
        assertTrue(check.canGraph("xyz=1"));
        assertTrue(check.canGraph("z^2=x^2+y^2"));
        assertTrue(check.canGraph("z=x+y+z^2"));
    }

    @Test
    void notQuiteXYZEquationsStillDont() {
        assertFalse(check.canGraph("x+y+z="));
        assertFalse(check.canGraph("x+y+z"));
        assertFalse(check.canGraph("a+b+c=1"));
        assertFalse(check.canGraph("x+y+z+w=1"));
        assertFalse(check.canGraph("x+y+z>1"));
    }
}
