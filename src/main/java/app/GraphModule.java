package app;

import data_access.InMemoryGraphSessionDataAccessObject;
import data_access.InMemoryPhaseSessionDataAccessObject;
import entity.LatexFunctionParser;
import entity.phase.DifferentialEquationParser;
import interface_adapter.graph.GraphController;
import interface_adapter.graph.GraphExploreController;
import interface_adapter.graph.GraphPresenter;
import interface_adapter.graph.GraphViewModel;
import interface_adapter.graph_check.GraphCheckController;
import interface_adapter.phase.PhaseCheckController;
import interface_adapter.phase.PhaseController;
import interface_adapter.phase.PhaseExploreController;
import interface_adapter.phase.PhasePresenter;
import interface_adapter.phase.PhaseState;
import interface_adapter.phase.PhaseViewModel;
import use_case.explore_graph.ExploreGraphInteractor;
import use_case.explore_phase.ExplorePhaseInteractor;
import use_case.graph_check.GraphCheckInteractor;
import use_case.graph_function.GraphFunctionInteractor;
import use_case.phase_check.PhaseCheckInteractor;
import use_case.phase_portrait.PhasePortraitInteractor;
import view.canvas.GraphSession;
import view.canvas.PhaseSession;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Wiring of graphing (US4): every graph or phase portrait on the canvas gets its own session
 * (creating and exploring share it), so several stay live at once.
 */
final class GraphModule {
    final GraphCheckController graphCheck =
            new GraphCheckController(new GraphCheckInteractor(new LatexFunctionParser()));
    final PhaseCheckController phaseCheck =
            new PhaseCheckController(new PhaseCheckInteractor(new DifferentialEquationParser()));

    /** A fresh session for one graph card. */
    static GraphSession newGraphSession() {
        GraphViewModel graphViewModel = new GraphViewModel();
        InMemoryGraphSessionDataAccessObject graphSession = new InMemoryGraphSessionDataAccessObject();
        GraphPresenter graphPresenter = new GraphPresenter(graphViewModel);
        return new GraphSession(
                new GraphController(new GraphFunctionInteractor(new LatexFunctionParser(), graphSession,
                        graphPresenter)),
                new GraphExploreController(new ExploreGraphInteractor(graphSession, graphPresenter)),
                graphViewModel);
    }

    /** Sessions for new graphs from recognition. */
    Supplier<GraphSession> graphSessions() {
        return GraphModule::newGraphSession;
    }

    /**
     * Sessions for editing / restoring an existing graph: quiet ones, so only a new graph from
     * recognition publishes a fresh Library/History record.
     */
    Supplier<GraphSession> restoredGraphSessions() {
        return GraphModule::newGraphSession;
    }

    /**
     * Sessions for phase portraits of ODEs.
     *
     * @param portraitDrawn called with each successfully drawn portrait (it also goes to PDF Studio)
     */
    Supplier<PhaseSession> phaseSessions(Consumer<PhaseState> portraitDrawn) {
        return () -> {
            PhaseViewModel phaseViewModel = new PhaseViewModel();
            phaseViewModel.addPropertyChangeListener(event -> {
                if (PhaseViewModel.STATE_PROPERTY.equals(event.getPropertyName())) {
                    PhaseState state = phaseViewModel.getState();
                    if (state.getError() == null && state.hasPortrait()) {
                        portraitDrawn.accept(state);
                    }
                }
            });
            InMemoryPhaseSessionDataAccessObject phaseSession = new InMemoryPhaseSessionDataAccessObject();
            PhasePresenter phasePresenter = new PhasePresenter(phaseViewModel);
            return new PhaseSession(
                    new PhaseController(new PhasePortraitInteractor(new DifferentialEquationParser(), phaseSession,
                            phasePresenter)),
                    new PhaseExploreController(new ExplorePhaseInteractor(phaseSession, phasePresenter)),
                    phaseViewModel);
        };
    }
}
