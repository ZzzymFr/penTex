package entity.integral;

import entity.UncalculableException;
import entity.UndefinedException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefiniteIntegralTest {
    private static Integral integral(String latex) {
        return IntegralParser.parse(latex).orElseThrow();
    }

    @Test
    void fundamentalTheorem() {
        assertEquals(1.0 / 3, DefiniteIntegral.value(integral("\\int_0^1 x^2 dx"), "\\frac{1}{3} x^{3}"), 1e-12);
        assertEquals(2, DefiniteIntegral.value(integral("\\int_0^{\\pi} \\sin x dx"), "-\\cos(x)"), 1e-12);
        assertEquals(Math.log(2), DefiniteIntegral.value(integral("\\int_1^2 \\frac{1}{t} dt"), "\\ln(t)"), 1e-12);
    }

    @Test
    void boundsAreOurs() {
        assertEquals(Math.PI / 2, DefiniteIntegral.bound(List.of("\\frac", "{", "\\pi", "}", "{", "2", "}")), 1e-12);
        assertThrows(UncalculableException.class, () -> DefiniteIntegral.bound(List.of("\\infty")));
    }

    @Test
    void blowsUpInBetween() {
        // F = -1/x would say -2, which is nonsense
        assertThrows(UndefinedException.class,
                () -> DefiniteIntegral.value(integral("\\int_{-1}^{1} \\frac{1}{x^2} dx"), "-\\frac{1}{x}"));
        assertThrows(UndefinedException.class,
                () -> DefiniteIntegral.value(integral("\\int_{-2}^{-1} \\frac{1}{x} dx"), "\\ln(x)"));
    }
}
