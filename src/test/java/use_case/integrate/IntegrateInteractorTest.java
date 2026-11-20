package use_case.integrate;

import entity.integral.Integral;
import org.junit.jupiter.api.Test;
import use_case.evaluate.EvaluateInputData;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntegrateInteractorTest {
    // remembers what it was shown
    private static final class Spy implements IntegrateOutputBoundary {
        IntegrateOutputData answer;
        IntegralFailure failure;

        @Override
        public void prepareSuccessView(IntegrateOutputData answer) {
            this.answer = answer;
        }

        @Override
        public void prepareFailView(IntegralFailure why) {
            this.failure = why;
        }
    }

    private static AntiderivativeGateway always(String latex) {
        return integral -> new Antiderivative(latex, latex);
    }

    @Test
    void indefiniteIsJustF() {
        List<Integral> asked = new ArrayList<>();
        Spy spy = new Spy();
        new IntegrateInteractor(integral -> {
            asked.add(integral);
            return new Antiderivative("\\frac{1}{3} x^{3}", "1/3 x^3");
        }, spy).execute(new IntegrateInputData("\\int x^2 dx ="));

        assertEquals(1, asked.size());
        assertFalse(spy.answer.isDefinite());
        assertEquals("\\frac{1}{3} x^{3}", spy.answer.getLatex());
        assertTrue(spy.answer.isAfterEquals());
        assertNull(spy.failure);
    }

    @Test
    void definiteDoesFOfBMinusFOfA() {
        Spy spy = new Spy();
        new IntegrateInteractor(always("\\frac{1}{3} x^{3}"), spy)
                .execute(new IntegrateInputData("\\int_1^{2} x^2 dx"));
        assertTrue(spy.answer.isDefinite());
        assertEquals(7.0 / 3, spy.answer.getValue(), 1e-12);
        assertFalse(spy.answer.isAfterEquals());
    }

    @Test
    void infiniteBoundNeverHitsTheApi() {
        Spy spy = new Spy();
        new IntegrateInteractor(integral -> {
            throw new AssertionError("should not be called");
        }, spy).execute(new IntegrateInputData("\\int_0^{\\infty} e^{-x} dx"));
        assertEquals(IntegralFailure.IMPROPER, spy.failure);
    }

    @Test
    void blowsUpBetweenTheBounds() {
        Spy spy = new Spy();
        new IntegrateInteractor(always("-\\frac{1}{x}"), spy)
                .execute(new IntegrateInputData("\\int_{-1}^{1} \\frac{1}{x^2} dx"));
        assertEquals(IntegralFailure.UNDEFINED, spy.failure);
    }

    @Test
    void apiFailurePassesThrough() {
        Spy spy = new Spy();
        new IntegrateInteractor(integral -> {
            throw new IntegralServiceException(IntegralFailure.OFFLINE, "no wifi");
        }, spy).execute(new IntegrateInputData("\\int x dx"));
        assertEquals(IntegralFailure.OFFLINE, spy.failure);
        assertNull(spy.answer);
    }

    @Test
    void notAnIntegralNeverHitsTheApi() {
        Spy spy = new Spy();
        new IntegrateInteractor(integral -> {
            throw new AssertionError("should not be called");
        }, spy).execute(new IntegrateInputData("1 + 1"));
        assertEquals(IntegralFailure.NOT_AN_INTEGRAL, spy.failure);
    }

    @Test
    void weirdFWeCantEvaluate() {
        Spy spy = new Spy();
        new IntegrateInteractor(always("\\operatorname{erf}(x)"), spy)
                .execute(new IntegrateInputData("\\int_0^1 e^{x^2} dx"));
        assertEquals(IntegralFailure.NO_ANSWER, spy.failure);
    }

    @Test
    void routerOnlyTakesIntegrals() {
        List<String> integrated = new ArrayList<>();
        List<String> evaluated = new ArrayList<>();
        IntegralRouter router = new IntegralRouter(in -> integrated.add(in.getLatex()),
                in -> evaluated.add(in.getExpression()));
        router.execute(new EvaluateInputData("\\int \\sin x dx ="));
        router.execute(new EvaluateInputData("1 + 2 ="));
        assertEquals(List.of("\\int \\sin x dx ="), integrated);
        assertEquals(List.of("1 + 2 ="), evaluated);
    }

    @Test
    void calculateCheckSaysYesToIntegrals() {
        IntegralCalculateCheck check = new IntegralCalculateCheck(latex -> latex.equals("1+1"));
        assertTrue(check.canCalculate("\\int x dx"));
        assertTrue(check.canCalculate("1+1"));
        assertFalse(check.canCalculate("2 \\int x dx"));
    }
}
