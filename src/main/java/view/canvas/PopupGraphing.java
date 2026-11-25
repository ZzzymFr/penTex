package view.canvas;

import interface_adapter.graph.GraphController;
import interface_adapter.graph.GraphExploreController;
import interface_adapter.graph.GraphState;
import interface_adapter.graph.GraphViewModel;
import view.FormulaCapture;
import view.graph.GraphCard;
import view.graph.GraphSnapshot;

import javax.swing.JComponent;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

// Graph from the popup: uses the latex already read, each graph gets its own live card beside its formula
final class PopupGraphing extends PopupCardTool<GraphSession> {


    private GraphDropHandler pdfDrop = (definition, image, point) -> false;
    private java.util.function.BiConsumer<entity.graph.GraphDefinition, BufferedImage> graphCreated =
            (definition, image) -> { };
    void setGraphCreatedListener(java.util.function.BiConsumer<entity.graph.GraphDefinition,
            BufferedImage> listener) { graphCreated = java.util.Objects.requireNonNull(listener); }
    void setPdfDropHandler(GraphDropHandler handler) {
        pdfDrop = java.util.Objects.requireNonNull(handler);
    }
    void restore(entity.graph.GraphDefinition definition, java.awt.Point canvasPosition) {
        super.restore(definition.functions(), definition.xPair(), definition.yPair(),
                definition.zPair(), canvasPosition, definition.camera());
    }
    void restoreSaved(entity.graph.GraphDefinition definition, java.awt.geom.Rectangle2D bounds,
                      String encodedDefinition) {
        super.restoreSaved(definition.functions(), definition.xPair(), definition.yPair(),
                definition.zPair(), bounds, encodedDefinition, definition.camera());
    }

    // one shared session: a single live card, a new graph replaces it
    PopupGraphing(SharedCanvas canvas, GraphController controller, GraphExploreController explore,
                  GraphViewModel viewModel, Notices notices, JComponent area,
                  BiConsumer<FormulaCapture, String> graphSaved) {
        this(canvas, shared(new GraphSession(controller, explore, viewModel)), notices, area, graphSaved);
    }

    // a fresh session per graph: several live graphs on the canvas at once
    PopupGraphing(SharedCanvas canvas, Supplier<GraphSession> kits, Notices notices, JComponent area,
                  BiConsumer<FormulaCapture, String> graphSaved) {
        super(canvas, kits, notices, area, graphSaved, "popup-graph",
                "Can't graph", "Drawing the graph of", "Graph of");
    }

    private static Supplier<GraphSession> shared(GraphSession kit) {
        return () -> kit;
    }

    JComponent graphCard() {
        return card();
    }

    void graph(String latex, Rectangle formulaWorld, FormulaCapture capture) {
        run(latex, formulaWorld, capture);
    }

    @Override
    Live newLive(GraphSession kit) {
        return new GraphLive(kit);
    }

    private final class GraphLive extends Live implements view.CanvasGraphSource {
        private final GraphViewModel viewModel;

        GraphLive(GraphSession kit) {
            super(kit, new GraphCard(kit.viewModel(), kit.explore()));
            this.viewModel = kit.viewModel();
            viewModel.addPropertyChangeListener(e -> {
                if (GraphViewModel.STATE_PROPERTY.equals(e.getPropertyName())) {
                    stateChanged();
                } else if (GraphViewModel.VIEW_PROPERTY.equals(e.getPropertyName())) {
                    viewChanged();
                }
            });
        }

        @Override
        public String graphDefinition() {
            if (!viewModel.getState().hasGraph()) return "";
            return interface_adapter.graph.GraphDefinitionCodec.encode(
                    interface_adapter.graph.GraphDefinitionMapper.from(viewModel.getState())
                            .withCamera(((GraphCard) card).cameraState()));
        }

        @Override
        void execute(List<String> latexes, double[] xRange, double[] yRange, double[] zRange) {
            kit.controller().execute(latexes, xRange, yRange, zRange);
        }

        @Override
        boolean takesMoreFunctions() {
            return true;
        }

        @Override
        String error() {
            return viewModel.getState().getError();
        }

        @Override
        boolean hasContent() {
            return viewModel.getState().hasGraph();
        }

        @Override
        String latex() {
            return viewModel.getState().getLatex();
        }

        @Override
        double[] window() {
            GraphState s = viewModel.getState();
            return new double[]{s.getXMin(), s.getXMax(), s.getYMin(), s.getYMax()};
        }

        @Override
        boolean followsY() {
            return viewModel.getState().getKind() == GraphState.Kind.CURVE;
        }

        @Override
        double[] zWindow() {
            GraphState s = viewModel.getState();
            return s.getKind() == GraphState.Kind.SURFACE ? new double[]{s.getZMin(), s.getZMax()} : null;
        }

        @Override
        void restoreCamera(entity.graph.GraphDefinition.Camera state) {
            ((GraphCard) card).restoreCamera(state);
        }
        private boolean transferPending;
        private BufferedImage dragThumbnail;
        private java.awt.Point activeDragPointer;
        private boolean draggingGraph;

        @Override
        void startedDragging() {
            captureGraphDragOrigin();
            draggingGraph = true;
            activeDragPointer = null;
            // Render the current (possibly rotated) view once per gesture, not for
            // every mouse movement. 3D snapshots finish asynchronously on the EDT.
            card.snapshotLater(SNAPSHOT_WIDTH, SNAPSHOT_HEIGHT, image -> {
                if (image == null) return;
                dragThumbnail = image;
                if (draggingGraph && activeDragPointer != null && pdfDrop.accepts(activeDragPointer)) {
                    pdfDrop.preview(activeDragPointer, (double) image.getWidth() / image.getHeight(), image);
                }
            });
        }

        @Override
        void draggingAt(java.awt.Point cursorOnScreen) {
            // Crossing only previews; release commits a copy and leaves Canvas intact.
            activeDragPointer = cursorOnScreen == null ? null : new java.awt.Point(cursorOnScreen);
            if (cursorOnScreen != null && pdfDrop.accepts(cursorOnScreen)) {
                BufferedImage thumbnail = dragThumbnail;
                double ratio = thumbnail == null ? (double) SNAPSHOT_WIDTH / SNAPSHOT_HEIGHT
                        : (double) thumbnail.getWidth() / Math.max(1, thumbnail.getHeight());
                pdfDrop.preview(cursorOnScreen, ratio, thumbnail);
            } else {
                pdfDrop.clearPreview();
            }
        }

        @Override
        void finishedDragging(java.awt.Point cursorOnScreen) {
            draggingGraph = false;
            activeDragPointer = null;
            pdfDrop.clearPreview();
            boolean toPdf = cursorOnScreen != null && !transferPending && onCanvas()
                    && pdfDrop.shouldAttemptDropOnRelease(cursorOnScreen);
            // A successful copy leaves the source graph at its original Canvas location.
            if (toPdf) restoreGraphDragOrigin();
            forgetGraphDragOrigin();
            if (!toPdf) return;
            transferPending = true;
            var definition = interface_adapter.graph.GraphDefinitionMapper.from(viewModel.getState())
                    .withCamera(((GraphCard) card).cameraState());
            java.awt.Point destination = new java.awt.Point(cursorOnScreen);
            // Commit a COPY only after release: the original Canvas graph stays live.
            // The destination creates its own independent interactive graph session.
            card.snapshotLater(SNAPSHOT_WIDTH, SNAPSHOT_HEIGHT, image -> {
                try {
                    if (image != null && onCanvas()) pdfDrop.drop(definition, image, destination);
                } finally {
                    transferPending = false;
                }
            });
        }

        @Override
        void publishedNew(BufferedImage image) {
            graphCreated.accept(interface_adapter.graph.GraphDefinitionMapper.from(viewModel.getState())
                    .withCamera(((GraphCard) card).cameraState()), image);
        }

        @Override
        BufferedImage firstPicture() {
            dragThumbnail = GraphSnapshot.render(viewModel.getState(), SNAPSHOT_WIDTH, SNAPSHOT_HEIGHT);
            return dragThumbnail;
        }
    }
}
