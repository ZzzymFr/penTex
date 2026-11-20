package interface_adapter.integrate;

import interface_adapter.AnswerKind;
import interface_adapter.MathLabState;
import interface_adapter.MathLabViewModel;
import interface_adapter.evaluate.AnswerFormatter;
import use_case.integrate.IntegralFailure;
import use_case.integrate.IntegrateOutputBoundary;
import use_case.integrate.IntegrateOutputData;

import java.util.EnumMap;
import java.util.Map;

// same view model as evaluate so the canvas cant tell the difference
public class IntegratePresenter implements IntegrateOutputBoundary {
    static final String TITLE = "Can't integrate";
    private static final int MAX_ANSWER_LENGTH = 120;  // api can spit out monsters, past this it wont fit
    private static final Map<IntegralFailure, String> WORDS = new EnumMap<>(IntegralFailure.class);

    static {
        WORDS.put(IntegralFailure.NOT_AN_INTEGRAL, "Just one integral, like ∫ f(x) dx");
        WORDS.put(IntegralFailure.OFFLINE, "Couldn't reach the Newton API");
        WORDS.put(IntegralFailure.NO_ANSWER, "Newton couldn't do this one");
        WORDS.put(IntegralFailure.IMPROPER, "Bounds have to be finite numbers");
        WORDS.put(IntegralFailure.UNDEFINED, "Undefined between the bounds");
    }

    private final MathLabViewModel viewModel;

    public IntegratePresenter(MathLabViewModel viewModel) {
        this.viewModel = viewModel;
    }

    @Override
    public void prepareSuccessView(IntegrateOutputData answer) {
        AnswerKind kind = answer.isAfterEquals() ? AnswerKind.AFTER_EQUALS : AnswerKind.RIGHT;
        String latex;
        String text;
        if (answer.isDefinite()) {
            // same rounding / p/q as every other number the calculator shows
            text = AnswerFormatter.format(answer.getValue());
            latex = fraction(text);
        } else {
            latex = answer.getLatex() + " + C";
            text = answer.getText() + " + C";
        }
        if (text.length() > MAX_ANSWER_LENGTH) {
            prepareFailView("Answer too long to fit");
            return;
        }
        String result = "= " + latex;
        String drawn = kind == AnswerKind.AFTER_EQUALS ? text : "= " + text;
        publish(kind, result, drawn.replace('-', '−'), kind == AnswerKind.AFTER_EQUALS ? latex : result, "", "");
    }

    @Override
    public void prepareFailView(IntegralFailure why) {
        prepareFailView(WORDS.getOrDefault(why, WORDS.get(IntegralFailure.NO_ANSWER)));
    }

    private void prepareFailView(String reason) {
        publish(AnswerKind.RIGHT, "", "", "", TITLE, reason);
    }

    // 7/3 -> \frac{7}{3} so it stacks on the canvas like other answers
    private static String fraction(String number) {
        return number.replaceAll("(-?)(\\d+)/(\\d+)", "$1\\\\frac{$2}{$3}");
    }

    private void publish(AnswerKind kind, String resultText, String answerText, String answerLatex,
                         String errorTitle, String errorText) {
        MathLabState state = viewModel.getState();
        state.setResultText(resultText);  // this one wipes answerLatex so it has to go first
        state.setAnswerKind(kind);
        state.setAnswerText(answerText);
        state.setAnswerLatex(answerLatex);
        state.setErrorTitle(errorTitle);
        state.setErrorText(errorText);
        state.setDefinition(false);
        state.setDefinedLatex("");
        viewModel.firePropertyChanged(MathLabViewModel.RESULT);
    }
}
