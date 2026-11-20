package interface_adapter.integrate;

import interface_adapter.AnswerKind;
import interface_adapter.MathLabState;
import interface_adapter.MathLabViewModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import use_case.integrate.Antiderivative;
import use_case.integrate.IntegralFailure;
import use_case.integrate.IntegrateOutputData;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class IntegratePresenterTest {
    private static final Antiderivative CUBE = new Antiderivative("\\frac{1}{3} x^{3}", "1/3 x^3");

    private MathLabViewModel viewModel;
    private IntegratePresenter presenter;
    private int fired;

    @BeforeEach
    void setUp() {
        viewModel = new MathLabViewModel();
        presenter = new IntegratePresenter(viewModel);
        viewModel.addPropertyChangeListener(e -> fired++);
    }

    @Test
    void indefiniteGetsPlusC() {
        presenter.prepareSuccessView(IntegrateOutputData.indefinite(CUBE, true));
        MathLabState state = viewModel.getState();
        assertEquals(AnswerKind.AFTER_EQUALS, state.getAnswerKind());
        assertEquals("= \\frac{1}{3} x^{3} + C", state.getResultText());
        assertEquals("\\frac{1}{3} x^{3} + C", state.getAnswerLatex());
        assertEquals("1/3 x^3 + C", state.getAnswerText());
        assertEquals("", state.getErrorTitle());
        assertEquals(1, fired);
    }

    @Test
    void definiteIsANumberLikeEverythingElse() {
        presenter.prepareSuccessView(IntegrateOutputData.definite(CUBE, 7.0 / 3, false));
        MathLabState state = viewModel.getState();
        assertEquals(AnswerKind.RIGHT, state.getAnswerKind());
        assertEquals("= \\frac{7}{3}", state.getAnswerLatex());
        assertEquals("= 7/3", state.getAnswerText());

        presenter.prepareSuccessView(IntegrateOutputData.definite(CUBE, Math.log(2), true));
        assertEquals("0.69", viewModel.getState().getAnswerLatex());
        presenter.prepareSuccessView(IntegrateOutputData.definite(CUBE, -0.5, true));
        assertEquals("-0.5", viewModel.getState().getAnswerLatex());
        assertEquals("−0.5", viewModel.getState().getAnswerText());
    }

    @Test
    void failureWords() {
        presenter.prepareFailView(IntegralFailure.OFFLINE);
        MathLabState state = viewModel.getState();
        assertEquals(IntegratePresenter.TITLE, state.getErrorTitle());
        assertEquals("Couldn't reach the Newton API", state.getErrorText());
        assertEquals("", state.getResultText());
        assertFalse(state.isDefinition());
        for (IntegralFailure why : IntegralFailure.values()) {
            presenter.prepareFailView(why);
            assertFalse(viewModel.getState().getErrorText().isBlank(), why.name());
        }
    }

    @Test
    void hugeAnswerIsRefused() {
        String huge = "x".repeat(200);
        presenter.prepareSuccessView(IntegrateOutputData.indefinite(new Antiderivative(huge, huge), true));
        assertEquals("Answer too long to fit", viewModel.getState().getErrorText());
    }
}
