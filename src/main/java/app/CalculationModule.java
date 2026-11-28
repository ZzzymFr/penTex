package app;

import data_access.InMemoryFunctionDataAccessObject;
import data_access.newton.JdkNewtonHttp;
import data_access.newton.NewtonAntiderivativeGateway;
import interface_adapter.MathLabViewModel;
import interface_adapter.evaluate.CalculateCheckController;
import interface_adapter.evaluate.DefinitionCheckController;
import interface_adapter.evaluate.EvaluateController;
import interface_adapter.evaluate.EvaluatePresenter;
import interface_adapter.evaluate.QuestionCheckController;
import interface_adapter.integrate.IntegratePresenter;
import pdf_editor.adapter.HistoryFormulaCalculator;
import use_case.evaluate.CalculateCheckInteractor;
import use_case.evaluate.DefinitionCheckInteractor;
import use_case.evaluate.EvaluateInteractor;
import use_case.evaluate.QuestionCheckInteractor;
import use_case.integrate.AntiderivativeGateway;
import use_case.integrate.IntegralCalculateCheck;
import use_case.integrate.IntegralRouter;
import use_case.integrate.IntegrateInteractor;

/**
 * Wiring of calculation (US2): the evaluator with its integral route and the checks the popup
 * asks before offering Calculate. One function store is shared, so a definition made on the
 * board is known everywhere.
 */
final class CalculationModule {
    final MathLabViewModel viewModel = new MathLabViewModel();
    private final InMemoryFunctionDataAccessObject functions = new InMemoryFunctionDataAccessObject();
    // integrals: newton api finds F, everything else still goes to our own evaluate
    private final AntiderivativeGateway integralSolver = new NewtonAntiderivativeGateway(new JdkNewtonHttp());
    final EvaluateController evaluate;
    final CalculateCheckController calculateCheck;
    final DefinitionCheckController definitionCheck;
    final QuestionCheckController questionCheck;

    CalculationModule() {
        evaluate = evaluatorInto(viewModel);
        calculateCheck = new CalculateCheckController(
                new IntegralCalculateCheck(new CalculateCheckInteractor(functions)));
        definitionCheck = new DefinitionCheckController(new DefinitionCheckInteractor());
        questionCheck = new QuestionCheckController(new QuestionCheckInteractor());
    }

    private EvaluateController evaluatorInto(MathLabViewModel model) {
        return new EvaluateController(new IntegralRouter(
                new IntegrateInteractor(integralSolver, new IntegratePresenter(model)),
                new EvaluateInteractor(functions, new EvaluatePresenter(model))));
    }

    /**
     * Reuses the same evaluation use case with a dedicated view model, so editing History does not
     * race with the Canvas answer worker's view-model updates.
     */
    HistoryFormulaCalculator historyCalculator() {
        MathLabViewModel historyEditModel = new MathLabViewModel();
        return new HistoryFormulaCalculator(evaluatorInto(historyEditModel), historyEditModel);
    }
}
