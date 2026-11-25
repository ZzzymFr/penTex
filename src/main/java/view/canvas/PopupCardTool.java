package view.canvas;

import view.FormulaCapture;
import view.graph.PlotCard;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * A popup button that puts live cards beside formulas (Graph, Phase Portrait). Every graph gets its
 * own {@link Live}: its own use-case session (a "kit" of controller + view model), its own card
 * floating over its canvas card, and its own range tag on that card's plot. So several graphs can
 * be on the canvas at once and each one pans, zooms and re-ranges on its own. The canvas keeps a
 * picture of each card for saving.
 *
 * <p>Given one shared kit instead of a factory, there is only ever one live card: a new graph
 * replaces it, as before multiple graphs were supported.
 *
 * @param <K> the per-graph kit (controllers + view model) the subclass runs its use case with
 */
abstract class PopupCardTool<K> {
    // first picture size, sets the card's shape; later ones follow the card
    static final int SNAPSHOT_WIDTH = 880;
    static final int SNAPSHOT_HEIGHT = 600;
    private static final int SNAPSHOT_DELAY_MS = 250;

    /** Where cards and their range boxes go: under the floating controls, over the ink. */
    interface Layers {
        void underlay(JComponent card);

        // called for the range tag, then its panel: the panel ends up on top
        void overlay(JComponent ranges);
    }

    private final SharedCanvas canvas;
    private final Notices notices;
    private final JComponent area;
    private final BiConsumer<FormulaCapture, String> saved;
    private final Supplier<K> kits;
    private Supplier<K> restoreKits;
    private final ExecutorService executor;
    private final String failTitle;
    private final String progressText;
    private final String successText;
    private final List<Live> lives = new ArrayList<>();
    private Live latest;              // the one that ran last
    private Layers layers;
    private String[] presetRange;     // typed before the next graph exists (tests, restore)
    private Runnable onShown = () -> { };

    PopupCardTool(SharedCanvas canvas, Supplier<K> kits, Notices notices, JComponent area,
                  BiConsumer<FormulaCapture, String> saved, String thread, String failTitle,
                  String progressText, String successText) {
        this.canvas = canvas;
        this.kits = Objects.requireNonNull(kits);
        this.restoreKits = kits;
        this.notices = notices;
        this.area = area;
        this.saved = Objects.requireNonNull(saved);
        this.failTitle = failTitle;
        this.progressText = progressText;
        this.successText = successText;
        this.layers = new Layers() {
            @Override
            public void underlay(JComponent card) {
                area.add(card);
            }

            @Override
            public void overlay(JComponent ranges) {
                area.add(ranges, 0);
            }
        };
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, thread);
            t.setDaemon(true);
            return t;
        });
        canvas.addViewListener(this::placeRanges);
        canvas.addUserActionListener(() -> SwingUtilities.invokeLater(this::placeRanges));
        canvas.getDrawingCanvas().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                placeRanges();  // undo / clear / × may have taken a card
            }
        });
    }

    // a kit -> its live card, per tool
    abstract Live newLive(K kit);
    /** Restore sessions are quiet: opening an archived graph never republishes it to History. */
    void setRestoreKits(Supplier<K> restoreKits) {
        this.restoreKits = Objects.requireNonNull(restoreKits);
    }

    /** The page decides where cards go (under its docks); set before the first graph. */
    void setLayers(Layers layers) {
        this.layers = Objects.requireNonNull(layers);
        for (Live live : lives) {
            layers.underlay(live.card);
            layers.overlay(live.ranges);
            layers.overlay(live.ranges.panel());
        }
    }

    // the newest card (null before the first graph); tests and the single-card page use it
    JComponent card() {
        return latest == null ? null : latest.card;
    }

    GraphRangeChip rangeChip() {
        return latest == null ? null : latest.ranges;
    }

    // how many live cards exist (shown or waiting for reuse)
    int liveCount() {
        return lives.size();
    }

    void setRangeText(String x, String y) {
        if (latest != null && !latest.onCanvas()) {
            latest.ranges.setRangeText(x, y);
        }
        presetRange = new String[]{x, y};
    }

    // someone else's card may need to know when ours shows up
    void setOnShown(Runnable onShown) {
        this.onShown = onShown == null ? () -> { } : onShown;
    }

    /** Restore a saved native graph at a precise Canvas location instead of importing its PNG. */
    void restore(List<String> functions, double[] xRange, double[] yRange, double[] zRange,
                 java.awt.Point canvasPosition, entity.graph.GraphDefinition.Camera camera) {
        if (functions == null || functions.isEmpty()) throw new IllegalArgumentException("No graph functions");
        Live live = newLive(restoreKits.get());
        lives.add(live);
        layers.underlay(live.card);
        layers.overlay(live.ranges);
        layers.overlay(live.ranges.panel());
        latest = live;
        live.restoring = true;
        live.newGraph = false;
        live.pendingCapture = null;
        live.lastFormula = null;
        live.pendingCanvasPosition = new java.awt.Point(canvasPosition);
        live.pendingCamera = camera;
        live.startWithRanges(functions, xRange, yRange, zRange);
    }

    /** Rebuild a native saved Graph into its original world bounds, initially leaving its
     * PNG placeholder visible until the new computation succeeds.
     */
    void restoreSaved(List<String> functions, double[] xRange, double[] yRange, double[] zRange,
                      java.awt.geom.Rectangle2D bounds, String encodedDefinition,
                      entity.graph.GraphDefinition.Camera camera) {
        Live live = newLive(restoreKits.get());
        lives.add(live);
        layers.underlay(live.card);
        layers.overlay(live.ranges);
        layers.overlay(live.ranges.panel());
        latest = live;
        live.restoring = true;
        live.newGraph = false;
        live.pendingSavedBounds = (java.awt.geom.Rectangle2D) bounds.clone();
        live.savedDefinition = encodedDefinition;
        live.pendingCamera = camera;
        live.startWithRanges(functions, xRange, yRange, zRange);
    }

    /** Discard cards from a previous saved document, including work still calculating. */
    void clearLives() {
        for (Live live : lives) {
            live.discarded = true;
            live.snapshotLater.stop();
            live.ranges.hideAll();
            live.drawing.run();
            for (JComponent component : new JComponent[]{live.card, live.ranges, live.ranges.panel()}) {
                java.awt.Container container = component.getParent();
                if (container != null) { container.remove(component); container.repaint(); }
            }
        }
        lives.clear();
        latest = null;
        area.revalidate();
        area.repaint();
    }

    void run(String latex, Rectangle formulaWorld, FormulaCapture capture) {
        Live live = liveForNewGraph();
        live.ranges.resetToDefault();   // a new graph starts on Auto, not on the last one's window
        if (presetRange != null) {
            live.ranges.setRangeText(presetRange[0], presetRange[1]);
            presetRange = null;
        }
        latest = live;
        live.restoring = false;
        live.newGraph = true;
        live.pendingCapture = capture;
        live.lastFormula = formulaWorld;
        live.start(List.of(latex), formulaWorld, false, false);
    }

    /**
     * A new graph gets a card of its own. An idle card (closed, or never shown because its run
     * failed) is reused before asking for a fresh kit; a kit already in use (one shared kit) means
     * that card is replaced.
     */
    private Live liveForNewGraph() {
        for (Live live : lives) {
            if (!live.onCanvas() && !live.busy) {
                return live;
            }
        }
        K kit = kits.get();
        for (Live live : lives) {
            if (live.kit == kit) {
                return live;
            }
        }
        Live live = newLive(kit);
        lives.add(live);
        layers.underlay(live.card);
        layers.overlay(live.ranges);
        layers.overlay(live.ranges.panel());
        return live;
    }

    // is this one of our cards (the tag the canvas knows it by)
    boolean owns(Object tag) {
        return lives.stream().anyMatch(live -> live == tag);
    }

    /**
     * A formula dropped on the card with this tag joins that graph, drawn in its own colour.
     * False when that card can't take another function (a phase portrait) or isn't ours.
     */
    boolean addTo(Object tag, String latex) {
        for (Live live : lives) {
            if (live == tag && live.onCanvas() && live.takesMoreFunctions()) {
                live.addFunction(latex);
                return true;
            }
        }
        return false;
    }

    // ink being dragged over a card: light up that one if it can take the formula, null = none
    void showDropTarget(Object tag) {
        for (Live live : lives) {
            live.card.setDropHighlight(live == tag && live.takesMoreFunctions());
        }
    }

    // every card + its range boxes ride along with pan and zoom of the canvas
    void placeRanges() {
        for (Live live : lives) {
            live.place();
        }
    }

    /** One graph on the canvas: its own session, live card and range tag. */
    abstract class Live {
        final K kit;
        final PlotCard card;
        final GraphRangeChip ranges;
        private final Timer snapshotLater;   // pan / zoom settle -> refresh the saved picture
        private FormulaCapture pendingCapture;
        private List<String> functions = List.of();   // what the card shows, first one first
        private List<String> asked = List.of();       // what the run under way will show
        private Rectangle lastFormula;       // world coords
        private Rectangle pending;           // where the card goes when the answer comes back
        private java.awt.Point pendingCanvasPosition; // explicit History drop position
        private entity.graph.GraphDefinition.Camera pendingCamera;
        private java.awt.geom.Rectangle2D pendingSavedBounds;
        private String savedDefinition;
        private boolean discarded;
        private boolean restoring;
        private boolean newGraph;
        private boolean rerun;               // new range from the panel: keep the card where it is
        private volatile boolean busy;
        private double[] shownWindow;        // last window shown on the range tag
        private Runnable drawing = () -> { };  // takes the Drawing… card down

        Live(K kit, PlotCard card) {
            this.kit = kit;
            this.card = card;
            this.ranges = new GraphRangeChip(this::rerun);
            this.card.setHost(new CardHost());
            this.snapshotLater = new Timer(SNAPSHOT_DELAY_MS, e -> refreshSnapshot());
            this.snapshotLater.setRepeats(false);
        }

        // ---- the use case + its state, per tool

        // runs off the ui thread; the answer comes back through the view model
        // a range is {min, max}, or null for Auto; zRange only ever comes for a 3D graph
        abstract void execute(List<String> latexes, double[] xRange, double[] yRange, double[] zRange);

        // can another formula be dropped onto this card (2D / 3D graphs yes, phase portraits no)
        boolean takesMoreFunctions() {
            return false;
        }

        abstract String error();

        abstract boolean hasContent();

        abstract String latex();

        // {xMin, xMax, yMin, yMax}
        abstract double[] window();

        // 3d keeps y as set (Auto = same as x), the rest follow the window
        abstract boolean followsY();

        // {zMin, zMax} of a 3D graph, null for anything flat
        double[] zWindow() {
            return null;
        }

        abstract BufferedImage firstPicture();

        /** Called exactly once for a successful new graph, never when restoring/reranging. */
        void publishedNew(BufferedImage image) { }

        String sourceLatex() {
            return latex();
        }

        // ----

        boolean onCanvas() {
            return canvas.getDrawingCanvas().hasGraph(this);
        }

        // Done / Reset in the range panel: same functions, new range, same spot
        private void rerun() {
            if (!functions.isEmpty()) {
                start(functions, lastFormula, true, false);
            }
        }

        // one more function on this graph, same spot; a 2D y range is fitted again so both show
        private void addFunction(String latex) {
            List<String> next = new ArrayList<>(functions);
            next.add(latex);
            start(next, lastFormula, true, true);
        }

        private void start(List<String> latexes, Rectangle formulaWorld, boolean again, boolean refitY) {
            startWork(latexes, formulaWorld, again, refitY && followsY() ? null : ranges.range("y"),
                    ranges.range("x"), ranges.range("z"));
        }
        private void startWithRanges(List<String> latexes, double[] x, double[] y, double[] z) {
            startWork(latexes, null, false, y, x, z);
        }
        private void startWork(List<String> latexes, Rectangle formulaWorld, boolean again,
                               double[] y, double[] x, double[] z) {
            asked = List.copyOf(latexes);
            pending = formulaWorld;
            rerun = again;
            busy = true;
            drawing.run();
            // Opening a saved workspace is not a new user action: do not emit a
            // progress toast for each restored 2D/3D graph.
            drawing = restoring ? () -> { }
                    : notices.progress(progressText, latexes.get(latexes.size() - 1));
            executor.execute(() -> {
                try {
                    execute(latexes, x, y, z);
                } catch (RuntimeException e) {
                    String reason = e.getMessage() == null ? e.toString() : e.getMessage();
                    SwingUtilities.invokeLater(() -> {
                        busy = false;
                        drawing.run();
                        if (!restoring) notices.error(failTitle, reason);
                        else System.err.println("Graph restoration failed: " + reason);
                    });
                }
            });
        }

        // view model said STATE: brand new card or an error
        final void stateChanged() {
            if (!discarded) SwingUtilities.invokeLater(this::show);
        }

        // view model said VIEW: pan / zoom / trace
        final void viewChanged() {
            if (!discarded) SwingUtilities.invokeLater(() -> syncRangeText(true));
        }

        private void show() {
            if (discarded) return;
            busy = false;
            drawing.run();
            if (error() != null) {
                if (!restoring) notices.error(failTitle, error());
                else System.err.println("Graph restoration failed: " + error());
            } else if (hasContent()) {
                BufferedImage picture = firstPicture();
                if (rerun && onCanvas()) {
                    canvas.getDrawingCanvas().replaceGraphImage(this, picture);
                } else {
                    if (pendingSavedBounds != null) {
                        canvas.getDrawingCanvas().showSavedGraph(picture, pendingSavedBounds,
                                savedDefinition, this);
                        pendingSavedBounds = null;
                        savedDefinition = null;
                    } else if (pendingCanvasPosition != null) {
                        canvas.getDrawingCanvas().showGraphAt(picture, pendingCanvasPosition, this);
                        pendingCanvasPosition = null;
                    } else canvas.getDrawingCanvas().showGraph(picture, pending, this);
                }
                rerun = false;
                functions = asked;
                if (pendingCamera != null) {
                    restoreCamera(pendingCamera);
                    pendingCamera = null;
                }
                // Explicit, successful run is the publication boundary for formula-only material.
                // Re-running a range does not create duplicates: the source revision is tracked by the page.
                if (pendingCapture != null && functions.size() == 1
                        && Objects.equals(functions.get(0), sourceLatex())) {
                    saved.accept(pendingCapture, functions.get(0));
                }
                if (newGraph && !restoring) {
                    newGraph = false;
                    publishedNew(picture);
                }
                if (!restoring) notices.success(successText, latex());
                ranges.setShowsZ(zWindow() != null);
                shownWindow = null;
                syncRangeText(false);
                placeRanges();
                refreshSnapshot();
                onShown.run();
            }
        }

        // the tag shows the window you're looking at; moved = a pan / zoom, not a fresh drawing
        private void syncRangeText(boolean moved) {
            if (!hasContent() || !onCanvas()) {
                return;
            }
            double[] window = window();
            if (Arrays.equals(window, shownWindow)) {
                return;   // just a hover or a spin, window didn't move
            }
            shownWindow = window;
            ranges.showWindow(new double[]{window[0], window[1]}, new double[]{window[2], window[3]},
                    zWindow(), followsY(), moved);
            place();
        }

        // canvas keeps a picture of the card for saving / pdf, redo it at 2x so it's sharp
        private void refreshSnapshot() {
            if (discarded) return;
            Rectangle2D on = canvas.getDrawingCanvas().getGraphCardOnScreen(this);
            if (on == null) {
                return;
            }
            int w = (int) Math.max(2, Math.round(on.getWidth() * 2));
            int h = (int) Math.max(2, Math.round(on.getHeight() * 2));
            // 3d cards draw this on their render thread, card may be gone by the time it lands
            card.snapshotLater(w, h, image -> {
                if (onCanvas()) {
                    canvas.getDrawingCanvas().replaceGraphImage(this, image);
                }
            });
        }

        // card over this graph's canvas card, range tag on its plot
        private void place() {
            Rectangle2D on = canvas.getDrawingCanvas().getGraphCardOnScreen(this);
            if (on == null) {
                canvas.getDrawingCanvas().setGraphCovered(this, false);
                hide(card);
                ranges.hideAll();
                return;
            }
            Rectangle at = SwingUtilities.convertRectangle(canvas.getDrawingCanvas(), new Rectangle(
                    (int) Math.round(on.getX()), (int) Math.round(on.getY()),
                    (int) Math.round(on.getWidth()), (int) Math.round(on.getHeight())), area);
            card.setBounds(at);
            card.setVisible(true);
            // card sits exactly on the slot now, canvas can stop drawing the picture under it
            canvas.getDrawingCanvas().setGraphCovered(this, true);
            ranges.placeOn(at, card);
            area.repaint();
        }

        private void hide(JComponent c) {
            if (c.isVisible()) {
                c.setVisible(false);
                area.repaint();
            }
        }

        void restoreCamera(entity.graph.GraphDefinition.Camera camera) { }
        private java.awt.geom.Rectangle2D graphDragOrigin;

        final void captureGraphDragOrigin() {
            java.awt.geom.Rectangle2D current = canvas.getDrawingCanvas().getGraphCardOnScreen(this);
            graphDragOrigin = current == null ? null : (java.awt.geom.Rectangle2D) current.clone();
        }

        /** A PDF drop copies the graph, so restore its original Canvas position. */
        final void restoreGraphDragOrigin() {
            java.awt.geom.Rectangle2D current = canvas.getDrawingCanvas().getGraphCardOnScreen(this);
            if (current != null && graphDragOrigin != null) {
                canvas.getDrawingCanvas().moveGraphBy(this,
                        graphDragOrigin.getX() - current.getX(),
                        graphDragOrigin.getY() - current.getY());
                place();
            }
        }

        final void forgetGraphDragOrigin() { graphDragOrigin = null; }

        /** Remove a transferred live card without touching the source graph use case. */
        final void removeTransferredCard() {
            canvas.getDrawingCanvas().removeGraph(this);
            place();
        }
        /** Optional native-graph copy lifecycle; phase portraits keep their normal drag behavior. */
        void startedDragging() { }
        /** Override only in native graph cards; phase portraits preserve existing behavior. */
        void finishedDragging(java.awt.Point cursorOnScreen) { }
        void draggingAt(java.awt.Point cursorOnScreen) { }

        // what the card asks the canvas to do, for this graph only
        private final class CardHost implements PlotCard.Host {
            @Override
            public void dragStarted() { startedDragging(); }

            @Override
            public void moveBy(double dx, double dy) {
                canvas.getDrawingCanvas().moveGraphBy(Live.this, dx, dy);
                place();
            }

            @Override
            public void resizeTo(double width, double height) {
                canvas.getDrawingCanvas().resizeGraphTo(Live.this, width, height);
                place();
                snapshotLater.restart();
            }

            @Override
            public void close() {
                canvas.getDrawingCanvas().removeGraph(Live.this);
                place();
            }

            @Override
            public void viewChanged() {
                canvas.getDrawingCanvas().notifyGraphStateChanged();
                snapshotLater.restart();
            }
            @Override
            public void dragFinished(java.awt.Point cursorOnScreen) {
                finishedDragging(cursorOnScreen);
            }
            @Override
            public void dragMoved(java.awt.Point cursorOnScreen) {
                draggingAt(cursorOnScreen);
            }
        }
    }
}
