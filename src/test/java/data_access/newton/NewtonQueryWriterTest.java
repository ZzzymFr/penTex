package data_access.newton;

import entity.integral.Integral;
import entity.integral.IntegralParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NewtonQueryWriterTest {
    private static String query(String latex) {
        Integral integral = IntegralParser.parse(latex).orElseThrow();
        return NewtonQueryWriter.expression(integral.getIntegrand(), integral.getVariable());
    }

    @Test
    void plainStuff() {
        assertEquals("x^(2)", query("\\int x^{2} dx"));
        assertEquals("3 x^(2) + 1", query("\\int 3x^2 + 1 dx ="));
        assertEquals("x * e^(x)", query("\\int x \\cdot e^x dx"));
        assertEquals("cos(2 x)", query("\\int \\cos(2x) dx"));
    }

    @Test
    void fractionsRootsAndTrig() {
        assertEquals("((1)/(x))", query("\\int \\frac{1}{x} dx"));
        assertEquals("sqrt(1 - x^(2))", query("\\int \\sqrt{1-x^2} dx"));
        assertEquals("((x)^(1/(3)))", query("\\int \\sqrt[3]{x} dx"));
        assertEquals("sin(x)^(2)", query("\\int \\sin^2 x dx"));
        assertEquals("sin(x) pi", query("\\int \\sin x \\pi dx"));
    }

    @Test
    void logsBecomeNewtonsNaturalLog() {
        assertEquals("log(x)", query("\\int \\ln x dx"));
        assertEquals("(log(x)/log(10))", query("\\int \\log x dx"));
        assertEquals("(log(x)/log(2))", query("\\int \\log_2 x dx"));
    }

    @Test
    void otherVariableGetsSwappedWithX() {
        // ∫ x t dt: t is the variable so newton sees it as x, and the old x becomes t
        assertEquals("t x", query("\\int x t dt"));
        assertEquals("sin(x)", query("\\int \\sin t dt"));
    }
}
