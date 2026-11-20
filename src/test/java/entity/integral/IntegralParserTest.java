package entity.integral;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntegralParserTest {
    @Test
    void indefiniteWithDx() {
        Integral integral = IntegralParser.parse("\\int x^{2} dx").orElseThrow();
        assertFalse(integral.isDefinite());
        assertEquals("x", integral.getVariable());
        assertEquals(List.of("x", "^", "{", "2", "}"), integral.getIntegrand());
    }

    @Test
    void definiteBothOrders() {
        Integral a = IntegralParser.parse("\\int_{0}^{1} x dx =").orElseThrow();
        Integral b = IntegralParser.parse("\\int^{\\pi}_0 \\sin t \\, dt").orElseThrow();
        assertEquals(List.of("0"), a.getLower());
        assertEquals(List.of("1"), a.getUpper());
        assertEquals(List.of("0"), b.getLower());
        assertEquals(List.of("\\pi"), b.getUpper());
        assertEquals("t", b.getVariable());
    }

    @Test
    void uprightDAndMissingDx() {
        assertEquals("y", IntegralParser.parse("\\int y \\mathrm{d} y").orElseThrow().getVariable());
        Integral guessed = IntegralParser.parse("\\int \\cos x").orElseThrow();
        assertEquals("x", guessed.getVariable());
        assertEquals(List.of("\\cos", "x"), guessed.getIntegrand());
    }

    @Test
    void notJustAnIntegral() {
        assertFalse(IntegralParser.isIntegral("2 \\int x dx"));
        assertFalse(IntegralParser.isIntegral("\\int x dx + 1 = 3"));
        assertFalse(IntegralParser.isIntegral("\\int_0 x dx"));
        assertFalse(IntegralParser.isIntegral("\\int dx"));
        assertFalse(IntegralParser.isIntegral("x^2 + 1"));
        assertFalse(IntegralParser.isIntegral(null));
        assertTrue(IntegralParser.isIntegral("\\iint x dx"));
    }

    @Test
    void equalsAtTheEnd() {
        assertTrue(IntegralParser.endsWithEquals("\\int x dx ="));
        assertFalse(IntegralParser.endsWithEquals("\\int x dx"));
    }
}
