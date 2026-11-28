package view;

import interface_adapter.AnswerKind;
import interface_adapter.canvas.CanvasMemento;
import interface_adapter.canvas.CanvasMementoOriginator;
import interface_adapter.canvas.ClearCanvasCommand;
import interface_adapter.canvas.DrawStrokeCommand;
import interface_adapter.canvas.EraseCommand;
import interface_adapter.canvas.PictureEditCommand;
import interface_adapter.canvas.ResetViewCommand;
import interface_adapter.canvas.SelectionEditCommand;
import interface_adapter.canvas.ViewportState;
import interface_adapter.canvas.ViewportTarget;
import interface_adapter.canvas.ZoomCommand;
import view.drawing.CanvasPicture;
import view.drawing.CanvasSelection;
import view.drawing.CanvasSnapshot;
import view.drawing.CanvasStateMapper;
import view.drawing.InkModel;
import view.drawing.CanvasDocument;
import view.drawing.CanvasEvents;
import view.drawing.InkImageExporter;
import view.drawing.CanvasInput;
import view.drawing.CanvasTool;
import view.drawing.EraserTool;
import view.drawing.PenTool;
import view.drawing.SelectionTool;
import view.drawing.CanvasPainter;
import view.drawing.MathAssetRenderer;
import view.drawing.SelectionDrag;
import view.drawing.CanvasViewport;
import view.drawing.FormulaGrouper;
import view.drawing.GraphCardLayer;
import view.drawing.GraphSlot;
import view.drawing.GraphCardPlacement;
import use_case.command.Command;
import use_case.command.CommandHistory;
import interface_adapter.history.CanvasHistorySelection;
import pdf_editor.adapter.CanvasInkSource;
import view.answer.AnswerColor;
import view.answer.AnswerLayout;
import view.answer.AnswerPlacer;
import view.answer.CanvasAnswer;
import interface_adapter.TextRun;
import view.answer.Formula;
import entity.InkEditor;
import entity.InkPoint;
import entity.Stroke;
import workspace.domain.CanvasState;
import java.io.IOException;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.function.UnaryOperator;

/**
 * An infinite white canvas the user writes on with the mouse or a pen; keeps the ink as strokes.
 *
 * <p>The ink lives in an unbounded "world"; the panel is a window onto it that can be moved and
 * zoomed. Scroll to move up and down, Shift+scroll to move sideways, drag with the right or middle
 * button (or with Space held) to move freely, and Ctrl+scroll to zoom around the mouse pointer.
 * Strokes are stored in world coordinates, so moving or zooming never changes the ink.
 * A picture such as the graph of the formula can be shown as a card beside the ink ({@link #showGraph}).
 *
 * <p>Three tools: the pen writes, the eraser removes ink, and the lasso selects the strokes it is
 * drawn around. The eraser has two modes: precise rubs out only the ink under it (cutting strokes
 * where it passes), stroke removes every stroke it touches. Drag a lasso selection to move it,
 * drag one of its corner handles to resize it (keeping its shape, around the opposite corner),
 * or press Delete to remove it. Undo (also Ctrl+Z) steps back through every change, including Clear.
 *
 * <p>Every change is a {@link Command} run through one {@link CommandHistory} (Command pattern):
 * drawing a stroke, erasing, clearing, editing the selection, placing / editing / removing a picture,
 * zooming and resetting the view. Content commands save a {@link CanvasMemento} of the canvas before
 * they act, view commands keep the {@link ViewportState} they replaced, so every one can be undone
 * and redone. The notches of one zoom gesture form a single step.
 *
 * <p>Structure (Single Responsibility, Dependency Inversion): this Swing component is the view and
 * controller only. The content is a Swing-free {@link CanvasDocument} (ink, formulas, pictures,
 * graph cards, selection); {@link CanvasViewport} holds the view transform; {@link CanvasInput}
 * routes the mouse to one {@link CanvasTool} per tool (Strategy); {@link CanvasPainter} draws;
 * {@link CanvasEvents} tells subscribers what changed (Observer); {@link CanvasStateMapper} saves and
 * opens; {@code CanvasFormulaReader} prepares recognition. The canvas keeps its public API and
 * forwards to these parts.
 */
public class DrawingCanvas extends JPanel implements CanvasInkSource {
    /** The drawing tools. */
    public enum Tool { PEN, ERASER, LASSO, RECTANGLE }

    /** How the eraser works: rub out just the ink under it, or remove each stroke it touches. */
    public enum EraserMode { PRECISE, STROKE }

    /** Property fired when the tool changes, so toolbars can show the current tool. */
    public static final String TOOL_PROPERTY = "tool";
    /** Property fired when the eraser mode changes. */
    public static final String ERASER_MODE_PROPERTY = "eraserMode";
    /** Property fired when the zoom changes (a double; 1 = 100%). */
    public static final String ZOOM_PROPERTY = "zoom";

    public static final double MIN_ZOOM = 0.25;
    public static final double MAX_ZOOM = 4;

    private static final float INK_WIDTH = InkModel.DEFAULT_WIDTH;
    private static final Color INK_COLOR = InkModel.DEFAULT_COLOR;
    // char heights the model reads fine with pen 4 (kept here for existing callers)
    static final double READABLE_MIN_HEIGHT = CanvasFormulaReader.READABLE_MIN_HEIGHT;
    static final double READABLE_MAX_HEIGHT = CanvasFormulaReader.READABLE_MAX_HEIGHT;
    private static final int HISTORY_LIMIT = 100;
    private static final int HANDLE_REACH = 9;       // how close (screen px) a press must be to grab one

    private final CanvasEvents events = new CanvasEvents();  // who follows the canvas
    // the model: ink, pictures, graph cards and what is picked, with no Swing in it
    private final CanvasDocument document = new CanvasDocument(events);
    private final InkModel inkModel = document.ink();  // the handwriting: strokes, their pens, formulas + answers
    // recognition: grouping ink into formulas and making the images the model reads
    private final CanvasFormulaReader reader = new CanvasFormulaReader(document, new FormulaGrouper(inkModel), this::repaint);
    // Command pattern invoker: every undoable change goes through here
    private final CommandHistory commands = new CommandHistory(HISTORY_LIMIT);
    // receiver ports the commands act on; kept private so the canvas' public API stays the same
    private final CanvasMementoOriginator contentState = new CanvasMementoOriginator() {
        @Override
        public CanvasMemento captureState() {
            return snapshot();
        }

        @Override
        public void restoreState(CanvasMemento memento) {
            restore((CanvasSnapshot) memento);
        }
    };
    private final ViewportTarget viewportTarget = new ViewportTarget() {
        @Override
        public ViewportState viewport() {
            return currentViewport();
        }

        @Override
        public void applyViewport(ViewportState state) {
            setViewport(state);
        }
    };
    private Object dropTarget;        // tag of the graph card the dragged selection is over, or null
    private Tool tool = Tool.PEN;
    private EraserMode eraserMode = EraserMode.PRECISE;

    // view: screen = world * zoom + origin
    private final CanvasViewport viewport = new CanvasViewport();

    // picked with the lasso / box (world coordinates)
    private final CanvasSelection picks = document.selection();
    private final SelectionDrag drag = document.drag();  // move / resize of the selection in progress
    // graph cards: pictures shown next to formulas, in world coordinates so they pan and zoom with the ink.
    // Several can be on the canvas at once; oldest first, so the newest is painted on top.
    private final GraphCardLayer graphs = document.graphs();
    private GraphSlot overGraphClose;  // card whose × is under the mouse, or null

    // input: one router, one strategy per tool (they reach the canvas only through ToolPorts)
    private final ToolPorts ports = new ToolPorts();
    private final PenTool pen = new PenTool(ports);
    private final EraserTool eraser = new EraserTool(ports);
    private final SelectionTool selector = new SelectionTool(ports, drag);
    private final CanvasInput input = new CanvasInput(ports, List.of(pen, eraser, selector));

    // one-board look: pen colour per stroke (screen only), no grid, pictures from history
    private Color inkColor = INK_COLOR;
    private float penWidth = INK_WIDTH;
    private int eraserSize = 16;
    private boolean gridVisible = true;
    private final List<CanvasPicture> pictures = document.pictures();
    private MathAssetRenderer mathAssetRenderer;
    public void setMathAssetRenderer(MathAssetRenderer renderer) { mathAssetRenderer = renderer; repaint(); }

    public DrawingCanvas() {
        setBackground(Color.WHITE);
        setPreferredSize(new Dimension(900, 500));
        setFocusable(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        addMouseListener(input);
        addMouseMotionListener(input);
        addMouseWheelListener(input);
        bindKey("DELETE", "deleteSelection", this::deleteSelection);
        bindKey("BACK_SPACE", "deleteSelection", this::deleteSelection);
        bindKey("ESCAPE", "clearSelection", this::clearSelection);
        bindKey("ctrl Z", "undo", this::undo);
        bindKey("pressed SPACE", "panOn", () -> input.setSpaceDown(true));
        bindKey("released SPACE", "panOff", () -> input.setSpaceDown(false));
    }

    private void bindKey(String key, String name, Runnable action) {
        getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key), name);
        getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    // ---------------------------------------------------------------- view: pan and zoom

    /** The zoom: 1 = 100%. */
    public double getZoom() {
        return viewport.zoom();
    }

    /** Moves the view by (dx, dy) screen pixels; the ink moves with it. */
    public void panBy(double dx, double dy) {
        viewport.panBy(dx, dy);
        repaint();
        events.viewChanged();
    }

    /** Zooms by a factor, keeping the world point under (screenX, screenY) in place; one undo step. */
    public void zoomAt(double screenX, double screenY, double factor) {
        zoomAt(screenX, screenY, factor, System.currentTimeMillis());
    }

    // `when` lets the notches of one wheel gesture merge into a single undo step
    private void zoomAt(double screenX, double screenY, double factor, long when) {
        commands.execute(new ZoomCommand(viewportTarget, screenX, screenY, factor, MIN_ZOOM, MAX_ZOOM, when));
    }

    /** Back to 100%, with the ink (if any) in the middle of the view; undoable. */
    public void resetView() {
        Rectangle ink = inkBounds();
        ViewportState reset = ink == null ? ViewportState.IDENTITY
                : new ViewportState(1, getWidth() / 2.0 - ink.getCenterX(), getHeight() / 2.0 - ink.getCenterY());
        commands.execute(new ResetViewCommand(viewportTarget, reset));
    }

    private ViewportState currentViewport() {
        return viewport.state();
    }

    // receiver of the view commands: jump to a viewport and tell whoever follows the zoom
    private void setViewport(ViewportState state) {
        double old = viewport.zoom();
        viewport.set(state);
        repaint();
        firePropertyChange(ZOOM_PROPERTY, old, viewport.zoom());
        events.viewChanged();
    }

    // ---------------------------------------------------------------- graph card

    /** Shows a graph card beside the formula as it is now (the lasso selection, or all the ink). */
    public void showGraph(BufferedImage image) {
        showGraph(image, getFormulaBounds());
    }

    /**
     * Shows a picture (the graph of a written function) as a card beside the given formula: to its
     * right, centred on it vertically, 40% of the visible width, so it never fills the canvas.
     * The view moves if needed so the card can be seen. Each graph gets its own card, so several can
     * be on the canvas at once; a card moves and zooms with the ink, closes with its × button, and is
     * never part of the formula image.
     *
     * @param formula where the formula is, in world coordinates ({@link #getFormulaBounds()} when it
     *                was captured); null puts the card in the middle of the view
     */
    public void showGraph(BufferedImage image, Rectangle formula) {
        showGraph(image, formula, null);
    }

    /**
     * Same, tagged so a live graph view can find its own card again. Showing with a tag that already
     * has a card replaces that card (a new graph from the same view); other cards are left alone.
     */
    public void showGraph(BufferedImage image, Rectangle formula, Object tag) {
        GraphCardPlacement.Card card = GraphCardPlacement.besideFormula(image, formula, viewport,
                getWidth() > 0 ? getWidth() : getPreferredSize().width, getWidth(), getHeight());
        GraphSlot previous = graphs.slotOf(tag);
        if (previous != null) {
            graphs.remove(previous);
        }
        Rectangle2D box = graphs.clearOfOtherCards(card.box(), GraphCardPlacement.stackGap(viewport));
        graphs.add(new GraphSlot(card.image(), box, card.padding(), tag));
        bringIntoView(formula == null ? box : box.createUnion(formula), box);
        repaint();
        notifyGraphStateChanged();
    }

    /** Place a restored live graph under the History drag cursor (screen coordinates).
     * Its tagged card is subsequently managed by the normal graph overlay, not a static Picture.
     */
    public void showGraphAt(BufferedImage image, Point screenTopLeft, Object tag) {
        java.util.Objects.requireNonNull(image, "image");
        java.util.Objects.requireNonNull(screenTopLeft, "screenTopLeft");
        double w = Math.min(340, image.getWidth() / 2.0);
        double h = w * image.getHeight() / Math.max(1.0, image.getWidth());
        Point2D world = toWorld(screenTopLeft);
        GraphSlot previous = graphs.slotOf(tag);
        if (previous != null) graphs.remove(previous);
        graphs.add(new GraphSlot(image, new Rectangle2D.Double(world.getX(), world.getY(),
                Math.max(1, w / viewport.zoom()), Math.max(1, h / viewport.zoom())), 0, tag));
        repaint();
        notifyGraphStateChanged();
    }

    /** Replace a saved graph's static placeholder only when the interactive reconstruction succeeds.
     * World-space bounds preserve the exact saved location and dimensions across zoom levels.
     */
    public void showSavedGraph(BufferedImage image, Rectangle2D bounds,
                               String encodedDefinition, Object liveTag) {
        java.util.Objects.requireNonNull(image);
        java.util.Objects.requireNonNull(bounds);
        java.util.Objects.requireNonNull(encodedDefinition);
        java.util.Objects.requireNonNull(liveTag);
        // A missing placeholder can occur if the graph was removed while its async calculation ran.
        // Do not resurrect a deleted graph.
        if (graphs.replaceSavedPlaceholder(image, bounds, encodedDefinition, liveTag)) {
            repaint();
            notifyGraphStateChanged();
        }
    }

    private GraphSlot slotOf(Object tag) {
        return graphs.slotOf(tag);
    }

    private GraphSlot newest() {
        return graphs.newest();
    }

    private void replaceSlot(GraphSlot old, GraphSlot next) {
        if (graphs.replace(old, next)) {
            repaint();
            notifyGraphStateChanged();
        }
    }

    private void removeSlot(GraphSlot slot) {
        if (!graphs.remove(slot)) {
            return;
        }
        if (overGraphClose == slot) {
            overGraphClose = null;
            setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        }
        repaint();
        notifyGraphStateChanged();
    }

    /** Removes the newest graph card. */
    public void removeGraph() {
        removeSlot(newest());
    }

    /** Removes the card shown with this tag, leaving the other graphs. */
    public void removeGraph(Object tag) {
        removeSlot(slotOf(tag));
    }

    public boolean hasGraph() {
        return !graphs.isEmpty();
    }

    public boolean hasGraph(Object tag) {
        return slotOf(tag) != null;
    }

    /**
     * A live card is drawn exactly over this graph's slot (true) or not any more (false). While covered
     * the screen skips the slot's white body + picture, nobody can see them and the bicubic scale was
     * ~15 ms a frame; the shadow still shows round the edge. Saving / pdf ({@link #paintContent}) still
     * draws the picture.
     */
    public void setGraphCovered(Object tag, boolean covered) {
        if (graphs.setCovered(tag, covered)) {
            repaint();
        }
    }

    boolean isGraphCovered(Object tag) {
        return graphs.isCovered(tag);
    }

    /** How many graph cards are on the canvas. */
    public int graphCount() {
        return graphs.size();
    }

    // who showed the newest card (null after a restore / clear, or for a plain picture)
    public Object getGraphTag() {
        GraphSlot slot = newest();
        return slot == null || !slot.hasLiveOwner() ? null : slot.tag();
    }

    // drag the newest card around, screen px, issue #45
    public void moveGraphBy(double dxScreen, double dyScreen) {
        moveSlotBy(newest(), dxScreen, dyScreen);
    }

    public void moveGraphBy(Object tag, double dxScreen, double dyScreen) {
        moveSlotBy(slotOf(tag), dxScreen, dyScreen);
    }

    private void moveSlotBy(GraphSlot slot, double dxScreen, double dyScreen) {
        if (slot == null) {
            return;
        }
        Rectangle2D c = slot.card();
        replaceSlot(slot, slot.withCard(new Rectangle2D.Double(c.getX() + dxScreen / viewport.zoom(), c.getY() + dyScreen / viewport.zoom(),
                c.getWidth(), c.getHeight())));
    }

    // new size in screen px, top-left stays
    public void resizeGraphTo(double widthScreen, double heightScreen) {
        resizeSlotTo(newest(), widthScreen, heightScreen);
    }

    public void resizeGraphTo(Object tag, double widthScreen, double heightScreen) {
        resizeSlotTo(slotOf(tag), widthScreen, heightScreen);
    }

    private void resizeSlotTo(GraphSlot slot, double widthScreen, double heightScreen) {
        if (slot == null || !(widthScreen > 0) || !(heightScreen > 0)) {
            return;
        }
        Rectangle2D c = slot.card();
        replaceSlot(slot, slot.withCard(new Rectangle2D.Double(c.getX(), c.getY(), widthScreen / viewport.zoom(),
                heightScreen / viewport.zoom())));
    }

    // fresh picture for the same card (after pan / zoom), so save + pdf get what's on screen
    public void replaceGraphImage(BufferedImage image) {
        replaceSlotImage(newest(), image);
    }

    public void replaceGraphImage(Object tag, BufferedImage image) {
        replaceSlotImage(slotOf(tag), image);
    }

    private void replaceSlotImage(GraphSlot slot, BufferedImage image) {
        if (slot == null || image == null) {
            return;
        }
        // live snapshots already draw their own card, so no white border
        replaceSlot(slot, new GraphSlot(image, slot.card(), 0, slot.tag()));
    }

    /** Where the newest graph card is on screen right now, or null if there is none. */
    public Rectangle2D getGraphCardOnScreen() {
        GraphSlot slot = newest();
        return slot == null ? null : toScreen(slot.card());
    }

    // tag of the topmost graph card at this screen point, null if none (or it has no tag)
    private Object graphTagAt(Point p) {
        return graphs.liveTagAt(p, viewport);
    }

    private void setGraphDropTarget(Object tag) {
        if (tag != dropTarget) {
            dropTarget = tag;
            events.graphHovered(tag);
        }
    }

    /** A lasso selection was dragged onto the graph card with this tag and let go there. */
    public void addGraphDropListener(Consumer<Object> listener) {
        events.onGraphDrop(listener);
    }

    /** A lasso selection being dragged is now over this graph card's tag (null: over none). */
    public void addGraphDropHoverListener(Consumer<Object> listener) {
        events.onGraphHover(listener);
    }

    /** Where the card shown with this tag is on screen right now, or null if it is gone. */
    public Rectangle2D getGraphCardOnScreen(Object tag) {
        GraphSlot slot = slotOf(tag);
        return slot == null ? null : toScreen(slot.card());
    }

    /** The point to click to close the newest graph card, on screen, or null if there is none. */
    public Point2D getGraphCloseOnScreen() {
        return closeOf(newest());
    }

    private Point2D closeOf(GraphSlot slot) {
        return graphs.closeOf(slot, viewport);
    }

    // topmost card whose × is at p
    private GraphSlot graphCloseAt(Point p) {
        return graphs.closeButtonAt(p, viewport);
    }

    /** Pans (without zooming) so the world rectangle, or at least {@code fallback}, is in view. */
    private void bringIntoView(Rectangle2D world, Rectangle2D fallback) {
        if (getWidth() <= 0 || getHeight() <= 0) {
            return;
        }
        Point2D shift = GraphCardPlacement.panToShow(world, fallback, viewport, getWidth(), getHeight());
        panBy(shift.getX(), shift.getY());
    }


    // world box -> where it is on screen now
    public Rectangle2D worldToScreen(Rectangle2D world) {
        return toScreen(world);
    }

    private Rectangle2D toScreen(Rectangle2D world) {
        return viewport.toScreen(world);
    }


    private Point2D toWorld(Point screen) {
        return viewport.toWorld(screen);
    }

    private InkPoint worldPoint(MouseEvent e) {
        Point2D p = toWorld(e.getPoint());
        return new InkPoint((int) Math.round(p.getX()), (int) Math.round(p.getY()), e.getWhen());
    }

    // ---------------------------------------------------------------- tools

    public Tool getTool() {
        return tool;
    }

    public void setTool(Tool tool) {
        Tool old = this.tool;
        if (old == tool) {
            return;
        }
        this.tool = tool;
        pen.reset();
        selector.reset();
        eraser.reset();
        setGraphDropTarget(null);  // a drag over a graph card ends with the tool
        clearSelection();
        setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        repaint();
        firePropertyChange(TOOL_PROPERTY, old, tool);
        events.userActed();
    }

    public float getPenWidth() {
        return penWidth;
    }

    /** Width used for subsequently drawn strokes, in canvas units. */
    public void setPenWidth(float width) {
        if (!Float.isFinite(width) || width < 1 || width > 16) {
            throw new IllegalArgumentException("Pen width must be between 1 and 16");
        }
        penWidth = width;
    }

    public EraserMode getEraserMode() {
        return eraserMode;
    }

    public void setEraserMode(EraserMode eraserMode) {
        EraserMode old = this.eraserMode;
        if (old == eraserMode) {
            return;
        }
        this.eraserMode = eraserMode;
        repaint();
        firePropertyChange(ERASER_MODE_PROPERTY, old, eraserMode);
        events.userActed();
    }

    /** Eraser size in screen pixels, independent of canvas zoom. */
    public int getEraserSize() { return eraserSize; }

    public void setEraserSize(int size) {
        if (size < 4 || size > 48) throw new IllegalArgumentException("Unsupported eraser size");
        eraserSize = size;
        repaint();
    }

    private int eraserRadius() {
        return eraserMode == EraserMode.PRECISE ? eraserSize : Math.max(3, eraserSize / 2);
    }

    // receiver of the pen: a new stroke with the current pen, one undo step
    private void beginStroke(Stroke stroke, InkPoint first) {
        clearSelection();
        commands.execute(new DrawStrokeCommand(contentState, () -> {
            inkModel.add(stroke, inkColor, penWidth);
            stroke.addPoint(first);
            repaint();
        }));
    }

    private void strokeFinished(Stroke done) {
        inkChanged(List.of(), List.of(done));  // one check per stroke
        events.strokeFinished(done);
    }

    // receiver of the eraser: rubs along one segment as part of this drag's undo step; true if anything went
    private boolean eraseBetween(Point2D from, Point2D to, boolean newUndoStep) {
        double radius = eraserRadius() / viewport.zoom();
        CanvasDocument.Erasure erasure = document.planErase(from, to, radius, eraserMode == EraserMode.PRECISE);
        Runnable rub = () -> document.applyErase(erasure);
        if (erasure.changed() && newUndoStep) {
            commands.execute(new EraseCommand(contentState, rub));
        } else {
            rub.run();
        }
        return erasure.changed();
    }


    private boolean pickPictureAt(Point2D p) {
        return document.pickPictureAt(p);
    }

    private void pickInside(List<InkPoint> lassoPath) {
        document.pickInside(lassoPath);
    }

    private void moveSelection(int dx, int dy) {
        transformSelection(SelectionEditCommand.Kind.MOVE,
                s -> InkEditor.translate(s, dx, dy), a -> a.translated(dx, dy));
    }

    private void resizeSelection(Point2D anchor, double factor) {
        transformSelection(SelectionEditCommand.Kind.RESIZE,
                s -> InkEditor.scale(s, anchor.getX(), anchor.getY(), factor),
                a -> SelectionDrag.resized(a, anchor, factor));
    }

    private void droppedOnGraph(Object target) {
        events.graphDropped(target);
    }

    /** Replaces each selected stroke with its changed copy, as one undo step; the copies stay selected. */
    private void transformSelection(SelectionEditCommand.Kind kind, UnaryOperator<Stroke> change,
                                    UnaryOperator<CanvasAnswer> answerChange) {
        commands.execute(new SelectionEditCommand(kind, contentState,
                () -> applyTransform(change, answerChange)));
    }

    private void applyTransform(UnaryOperator<Stroke> change, UnaryOperator<CanvasAnswer> answerChange) {
        document.applyTransform(change, answerChange);
    }

    private List<Stroke> selectionPreview() {
        return document.selectionPreview();
    }

    private List<CanvasAnswer> answerPreview() {
        return document.answerPreview();
    }

    private boolean nothingSelected() {
        return document.nothingSelected();
    }

    /** Corner i of a box: 0 top-left, 1 top-right, 2 bottom-right, 3 bottom-left. */
    private static Point2D corner(Rectangle box, int i) {
        return CanvasPainter.corner(box, i);
    }

    /** The selection corner handle under a screen point, or -1. */
    private int handleAt(Point screen) {
        Rectangle bounds = selectionBounds();
        if (bounds == null) {
            return -1;
        }
        for (int i = 0; i < 4; i++) {
            Point2D c = corner(bounds, i);
            Point2D onScreen = viewport.toScreen(c);
            double sx = onScreen.getX();
            double sy = onScreen.getY();
            if (Math.abs(screen.x - sx) <= HANDLE_REACH && Math.abs(screen.y - sy) <= HANDLE_REACH) {
                return i;
            }
        }
        return -1;
    }

    public boolean hasSelection() {
        return !nothingSelected();
    }

    /** Removes the strokes and answers selected with the lasso. */
    public void deleteSelection() {
        if (nothingSelected()) {
            return;
        }
        events.userActed();
        commands.execute(new SelectionEditCommand(SelectionEditCommand.Kind.DELETE, contentState,
                this::removeSelected));
    }

    private void removeSelected() {
        document.removeSelected();
        repaint();
    }

    // lasso'd strokes + answers get a new colour, one undo step; false if nothing picked
    public boolean recolorSelection(Color color) {
        if (nothingSelected() || color == null) {
            return false;
        }
        commands.execute(new SelectionEditCommand(SelectionEditCommand.Kind.RECOLOR, contentState,
                () -> paintSelected(color)));
        repaint();
        events.userActed();
        events.inkChanged();
        return true;
    }

    private void paintSelected(Color color) {
        document.paintSelected(color);
    }

    public void clearSelection() {
        if (!picks.isEmpty()) {
            picks.clear();
            repaint();
        }
    }

    // kept here by name: tests read it reflectively
    private Rectangle selectionBounds() {
        return document.selectionBounds();
    }

    private Rectangle inkBounds() {
        return document.inkBounds();
    }

    private static Rectangle boundsOf(Iterable<Stroke> strokeSet) {
        return AnswerLayout.bounds(strokeSet);
    }

    // ---------------------------------------------------------------- strokes and undo

    /** The strokes, in world coordinates. */
    public List<Stroke> getStrokes() {
        return Collections.unmodifiableList(inkModel.strokes());
    }

    public boolean isBlank() {
        return document.isBlank();
    }

    /** Steps back one command (drawing, erasing, Clear, zoom, ...) without destroying the redo chain. */
    public void undo() {
        commands.undo();
    }

    /** Reapplies a command undone by undo(). */
    public void redo() {
        commands.redo();
    }

    public boolean canRedo() {
        return commands.canRedo();
    }

    /** Name of the step Undo would revert ("Draw", "Clear", "Zoom", ...), or "" if none. */
    public String undoName() {
        return commands.undoName();
    }

    /** Name of the step Redo would re-apply, or "" if none. */
    public String redoName() {
        return commands.redoName();
    }

    /** Called whenever what Undo / Redo can do changes. */
    public void addUndoStateListener(Runnable listener) {
        commands.addChangeListener(listener);
    }

    private void restore(CanvasSnapshot back) {
        document.restore(back);
        overGraphClose = null;
        pen.abandon();
        repaint();
        for (Formula f : inkModel.book().all()) {
            if (f.getAnswer() == null) notifyChanged(f.getId());
        }
        events.userActed();
        events.inkChanged();
    }

    public void clear() {
        if (inkModel.strokes().isEmpty() && pictures.isEmpty() && graphs.isEmpty()) return;
        events.userActed();
        commands.execute(new ClearCanvasCommand(contentState, this::removeEverything));
    }

    private void removeEverything() {
        document.removeEverything();
        overGraphClose = null;
        pen.abandon();
        repaint();
        events.inkChanged();
    }

    private CanvasSnapshot snapshot() {
        return document.snapshot();
    }


    private static List<Stroke> minus(List<Stroke> a, List<Stroke> b) {
        return CanvasDocument.minus(a, b);
    }

    private void inkChanged(List<Stroke> removed, List<Stroke> added) {
        document.inkChanged(removed, added);
    }

    private void notifyChanged(long id) {
        events.formulaChanged(id);
    }

    // mouse still down on something
    public boolean isBusy() {
        return busy();
    }

    // mid-gesture a pan would shift the ink under the pen
    private boolean busy() {
        return pen.current() != null || drag.isActive() || selector.lassoPath() != null
                || eraser.isErasing() || input.isPanning();
    }

    private long nextRevision() {
        return inkModel.nextRevision();
    }


    // ---------------------------------------------------------------- formulas and answers

    // fires per edited formula id, not on undo
    public void addFormulaChangeListener(LongConsumer listener) {
        events.onFormulaChanged(listener);
    }

    // fires per whole formula moved or resized (not on undo)
    public void addFormulaMoveListener(LongConsumer listener) {
        events.onFormulaMoved(listener);
    }

    // pen stroke finished (after the formula check)
    public void addStrokeListener(Consumer<Stroke> listener) {
        events.onStrokeFinished(listener);
    }
    /** Called after actual ink changes, including erase, transform, clear and undo. */
    @Override
    public void addInkChangeListener(Runnable listener) {
        events.onInkChanged(listener);
    }
    /** Graph edits (including camera-only changes) update the Save/Edited status. */
    public void addGraphStateListener(Runnable listener) {
        events.onGraphStateChanged(listener);
    }
    public void notifyGraphStateChanged() {
        events.graphStateChanged();
    }
    /** Snapshot only the deliberately selected strokes, not the whole canvas. */
    public List<Stroke> selectedInkSnapshot() {
        return document.selectedInk();
    }
    @Override
    public boolean containsAllInk(List<Stroke> source) {
        return source != null && !source.isEmpty() && inkModel.strokes().containsAll(source);
    }
    public BufferedImage selectedInkImage() {
        List<Stroke> selected = selectedInkSnapshot();
        if (selected.isEmpty()) throw new IllegalStateException("Select handwriting first with Box or Lasso.");
        return imageOf(selected);
    }
    /** Full-colour high-resolution copy for exporting selected ink, including its answer. */
    public BufferedImage selectedInkColorImage() {
        List<Stroke> selected = selectedInkSnapshot();
        if (selected.isEmpty()) throw new IllegalStateException("Select handwriting first with Box or Lasso.");
        return InkImageExporter.inColor(selected, inkModel);
    }
    /** Can an existing ink selection begin a copy gesture at this screen position? */
    public boolean canCopySelectedInkAt(Point screenPoint) {
        if (tool != Tool.LASSO && tool != Tool.RECTANGLE) return false;
        if (!picks.hasStrokes() || input.isSpaceDown() || input.isPanning()) return false;
        Rectangle bounds = selectionBounds();
        return bounds != null && bounds.contains(toWorld(screenPoint)) && handleAt(screenPoint) < 0;
    }

    /** A History picture can be moved on the Canvas or copied across the PDF boundary.
     * Read-only hit testing: the existing selection/move/resize algorithms are unchanged.
     */
    public boolean canCopySelectedHistoryAt(Point screenPoint) {
        if (tool != Tool.LASSO && tool != Tool.RECTANGLE) return false;
        if (!picks.hasPictures() || input.isSpaceDown() || input.isPanning()) return false;
        Rectangle bounds = selectionBounds();
        return bounds != null && bounds.contains(toWorld(screenPoint)) && handleAt(screenPoint) < 0;
    }

    /** Retain structured text and original graph pixels instead of exporting a screenshot. */
    public CanvasHistorySelection selectedHistoryTransfer() {
        java.util.List<CanvasHistorySelection.Asset> assets = new ArrayList<>();
        for (CanvasPicture p : pictures) {
            if (picks.isPicked(p)) {
                assets.add(new CanvasHistorySelection.Asset(p.image(), p.latex(), p.result(),
                        p.box().getX(), p.box().getY(), p.box().getWidth(), p.box().getHeight()));
            }
        }
        return new CanvasHistorySelection(assets);
    }

    /** Cancel the in-progress move preview when a native copy drag takes over. */
    public void cancelSelectionMoveForCopy() {
        drag.endMove();
        repaint();
    }

    /** Export only selected ink on transparent pixels, without changing recognition screenshots. */
    public BufferedImage selectedInkTransparentImage() {
        List<Stroke> selected = selectedInkSnapshot();
        if (selected.isEmpty()) throw new IllegalStateException("Select ink with Box or Lasso first.");
        return InkImageExporter.onTransparent(selected, inkModel);
    }

    /** The already-calculated result associated with a wholly selected formula, if any. */
    public String selectedAnswer() {
        return InkImageExporter.wholeAnswerText(selectedInkSnapshot(), inkModel.book());
    }

    // left press with a tool, tool/mode switch, undo, clear, delete
    public void addUserActionListener(Runnable listener) {
        events.onUserAction(listener);
    }

    public void addViewListener(Runnable listener) {
        events.onViewChanged(listener);
    }

    public void addPickListener(Runnable listener) {
        events.onPick(listener);
    }

    public boolean isBound(Stroke s) {
        return inkModel.book().isBound(s);
    }

    // -1 if loose
    public long formulaIdOf(Stroke s) {
        Formula f = inkModel.book().holding(s);
        return f == null ? -1 : f.getId();
    }

    // world box of a formula, null if gone
    public Rectangle formulaBounds(long id) {
        Formula f = inkModel.book().get(id);
        return f == null ? null : boundsOf(f.getStrokes());
    }

    // bind loose ink near last as one formula (other session groups too); null if last gone/bound
    public FormulaCapture registerNewInk(Stroke last, Collection<Stroke> session) {
        return reader.registerNewInk(last, session);
    }

    // edited formula at the pause: regrouped w/ the guesses next to it first, ink added to half a fraction finds the rest
    public FormulaCapture captureEdited(long id) {
        return reader.captureEdited(id);
    }



    // Lasso over one answered formula plus extra ink means correct the grouping.
    // A lasso spanning multiple answered formulas must not merge them accidentally.
    public FormulaCapture registerSelection() {
        return reader.registerSelection();
    }

    // lasso selection is exactly this formula's ink
    public boolean selectionIsFormula(long id) {
        return reader.selectionIsFormula(id);
    }

    // pick is exactly one answered formula: its capture anyway, null if not
    public FormulaCapture capturePickedAnswered() {
        return reader.capturePickedAnswered();
    }

    public List<Formula> getFormulas() {
        return inkModel.book().all();
    }

    public List<CanvasAnswer> getAnswers() {
        List<CanvasAnswer> answers = new ArrayList<>();
        for (Formula f : inkModel.book().all()) {
            if (f.getAnswer() != null) {
                answers.add(f.getAnswer());
            }
        }
        return answers;
    }

    // lasso pick, else unbound ink, else redo every formula; throws if blank
    public List<FormulaCapture> takeFormulasToCalculate() {
        return reader.takeFormulasToCalculate();
    }

    // null if gone or already answered
    public FormulaCapture captureForRecalculation(long id) {
        return reader.captureForRecalculation(id);
    }

    // false if the formula is gone or changed since capture
    public boolean isCurrent(long id, long revision) {
        return reader.isCurrent(id, revision);
    }

    // false if the formula is gone or changed since capture
    public boolean showAnswer(long id, long revision, AnswerKind kind, String text, boolean error) {
        return placeAnswer(id, revision, f -> AnswerPlacer.place(inDrawingOrder(f), kind, text, null, error,
                answerPen(f.getStrokes(), inkModel.widths()), box -> hitsOtherInk(box, f)));
    }

    // same spot rules, text w/ raised exponents
    public boolean showAnswer(long id, long revision, AnswerKind kind, List<TextRun> runs, boolean error) {
        return placeAnswer(id, revision, f -> AnswerPlacer.place(inDrawingOrder(f), kind, TextRun.plain(runs), runs,
                error, answerPen(f.getStrokes(), inkModel.widths()), box -> hitsOtherInk(box, f)));
    }

    // typeset: fractions stacked, powers raised; text is the readable copy that gets saved
    public boolean showTypesetAnswer(long id, long revision, AnswerKind kind, String text, String latex) {
        return placeAnswer(id, revision, f -> AnswerPlacer.placeTypeset(inDrawingOrder(f), kind, text, latex, false,
                answerPen(f.getStrokes(), inkModel.widths()), box -> hitsOtherInk(box, f)));
    }

    // answer's written w/ the pen most of its formula was
    private static float answerPen(Collection<Stroke> formula, Map<Stroke, Float> widths) {
        return InkModel.mostUsedPen(formula, widths);
    }

    private boolean placeAnswer(long id, long revision, Function<Formula, CanvasAnswer> placer) {
        Formula f = inkModel.book().get(id);
        if (f == null || f.getRevision() != revision) {
            return false;
        }
        CanvasAnswer answer = placer.apply(f).withColor(AnswerColor.of(f.getStrokes(), this::colorOf, INK_COLOR));
        inkModel.book().put(f.withAnswer(answer));
        if (!busy()) {
            bringIntoView(answer.getBox(), answer.getBox());
        }
        repaint();
        return true;
    }

    private boolean hitsOtherInk(Rectangle2D box, Formula f) {
        for (Stroke s : inkModel.strokes()) {
            Rectangle b = f.getStrokes().contains(s) ? null : boundsOf(List.of(s));
            if (b != null && box.intersects(b.x, b.y, Math.max(1, b.width), Math.max(1, b.height))) {
                return true;
            }
        }
        return false;
    }

    private List<Stroke> inDrawingOrder(Formula f) {
        return reader.inDrawingOrder(f);
    }


    // how much to blow up / shrink ink so pen 4 looks like what the model reads ok (probed 25..240)
    static double recognitionScale(double charHeight) {
        return CanvasFormulaReader.recognitionScale(charHeight);
    }


    // ---------------------------------------------------------------- images

    /** Renders the ink as dark strokes on a white background: what the canvas shows, the size of the canvas. */
    public BufferedImage toImage() {
        int width = Math.max(1, getWidth());
        int height = Math.max(1, getHeight());
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = image.createGraphics();
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, width, height);
        paintInk(g2);
        g2.dispose();
        return image;
    }

    /** Export visible canvas objects over transparent pixels, leaving recognition snapshots white. */
    public BufferedImage toTransparentInkImage() {
        int width = Math.max(1, getWidth()), height = Math.max(1, getHeight());
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try { paintInk(g); } finally { g.dispose(); }
        return image;
    }

    private List<Stroke> formulaStrokes() {
        return document.formulaStrokes();
    }

    /** Where the formula is (world coordinates): the lasso selection, or all the ink; null if blank. */
    public Rectangle getFormulaBounds() {
        List<Stroke> formula = formulaStrokes();
        return formula.isEmpty() ? null : boundsOf(formula);
    }

    /**
     * Picture of just the formula: the lasso selection if there is one, otherwise all the ink.
     * Rendered from the strokes at a fixed scale whatever the zoom, cropped to the ink plus the pen
     * width, black on white in gray, at least 8 px of margin, padded (never stretched) to multiples
     * of 16. This is the image handed to handwriting recognition.
     *
     * @throws IllegalStateException if nothing has been written
     */
    public BufferedImage toFormulaImage() {
        if (isBlank()) {
            throw new IllegalStateException("The canvas is empty.");
        }
        return imageOf(formulaStrokes());
    }

    private BufferedImage imageOf(List<Stroke> formula) {
        return reader.imageOf(formula);
    }

    /** Draws the ink (no background) as it appears on screen, e.g. onto a copy of the canvas elsewhere. */
    public void paintInk(Graphics2D g2) {
        Graphics2D world = (Graphics2D) g2.create();
        viewport.applyTo(world);
        CanvasPainter.drawStrokes(world, inkModel);
        world.dispose();
    }

    public Rectangle getAllInkBounds() {
        return boundsOf(inkModel.strokes());
    }

    /** Draws the graph card and the ink (no background) as they appear on screen. */
    public void paintContent(Graphics2D g2) {
        Graphics2D world = (Graphics2D) g2.create();
        viewport.applyTo(world);
        CanvasPainter.paintGraphCards(world, graphs, viewport.zoom(), false);  // export: always the real picture, live cards aren't in here
        paintPictures(world);
        CanvasPainter.drawStrokes(world, inkModel);
        CanvasPainter.drawAnswers(world, getAnswers());
        world.dispose();
    }

    /** Draws the grid, the graph card and the ink (no background) as they appear on screen. */
    public void paintView(Graphics2D g2) {
        if (gridVisible) {
            CanvasPainter.paintGrid(g2, viewport, getWidth(), getHeight());
        }
        paintContent(g2);
    }

    // ---------------------------------------------------------------- one-board extras

    // pen colour for new strokes, formula images stay black
    public void setInkColor(Color color) {
        inkColor = color == null ? INK_COLOR : color;
    }

    public Color getInkColor() {
        return inkColor;
    }

    public void setGridVisible(boolean visible) {
        gridVisible = visible;
        repaint();
    }

    public boolean canUndo() {
        return commands.canUndo();
    }

    // changes whenever what's on screen changes, glass caches key on it
    public long contentVersion() {
        long h = Double.doubleToLongBits(viewport.zoom());
        h = h * 31 + Double.doubleToLongBits(viewport.originX());
        h = h * 31 + Double.doubleToLongBits(viewport.originY());
        for (Stroke s : inkModel.strokes()) {
            h = h * 31 + System.identityHashCode(s);
            h = h * 31 + colorOf(s).getRGB();
        }
        h = h * 31 + (pen.current() == null ? 0 : pen.current().getPoints().size());
        for (CanvasAnswer a : getAnswers()) {
            h = h * 31 + System.identityHashCode(a);
        }
        h = h * 31 + picks.strokes().size() + 7L * picks.answerIds().size();
        h = h * 31 + drag.dx() * 131L + drag.dy();
        h = h * 31 + Double.doubleToLongBits(drag.factor());
        h = h * 31 + (selector.lassoPath() == null ? -1 : selector.lassoPath().size());
        for (GraphSlot slot : graphs.all()) {
            h = h * 31 + slot.card().hashCode() + System.identityHashCode(slot.image());
        }
        h = h * 31 + pictures.size();
        return h * 31 + getWidth() * 7919L + getHeight();
    }

    /** Drag a history record onto the canvas as an independently editable object. */
    public void addHistoryAsset(BufferedImage image, String latex, String result, Point2D screenTopLeft) {
        if (image == null) throw new IllegalArgumentException("Image required");
        Point2D world = toWorld(new Point((int) screenTopLeft.getX(), (int) screenTopLeft.getY()));
        events.userActed();
        boolean graph = latex == null || latex.isBlank();
        double displayW = graph ? Math.min(340, image.getWidth() / 2.0) : image.getWidth() / 2.0;
        double displayH = graph ? displayW * image.getHeight() / image.getWidth() : image.getHeight() / 2.0;
        CanvasPicture asset = new CanvasPicture(image, new Rectangle2D.Double(world.getX(), world.getY(),
                Math.max(32, displayW / viewport.zoom()), Math.max(20, displayH / viewport.zoom())),
                latex == null ? "" : latex, result == null ? "" : result);
        commands.execute(new PictureEditCommand(PictureEditCommand.Kind.PLACE, contentState, () -> {
            pictures.add(asset);
            picks.clearPictures();
            picks.addPicture(asset);
            picks.setPrimaryPicture(asset);
        }));
        repaint();events.inkChanged();
    }

    public void addPicture(BufferedImage image, Point2D screenTopLeft) {
        addHistoryAsset(image, "", "", screenTopLeft);
    }

    public int getPictureCount() { return pictures.size(); }
    /** Number of imported History items in the current lasso/box selection. */
    public int getSelectedPictureCount() { return picks.pictures().size(); }
    public boolean hasSelectedHistoryAsset() { return picks.primaryPicture() != null && !picks.primaryPicture().latex().isBlank(); }
    public String selectedHistoryFormula() { return picks.primaryPicture() == null ? "" : picks.primaryPicture().latex(); }
    public String selectedHistoryResult() { return picks.primaryPicture() == null ? "" : picks.primaryPicture().result(); }

    /** Re-typeset edited LaTeX in the adapter, then replace the selected object as one Undo step. */
    public boolean editHistoryAsset(String latex, String result, BufferedImage preview) {
        if (picks.primaryPicture() == null || picks.primaryPicture().latex().isBlank() || latex == null || latex.isBlank()
                || preview == null) return false;
        events.userActed();
        CanvasPicture replacement = new CanvasPicture(preview, picks.primaryPicture().box(), latex.trim(), result == null ? "" : result.trim());
        commands.execute(new PictureEditCommand(PictureEditCommand.Kind.EDIT, contentState, () -> {
            pictures.set(pictures.indexOf(picks.primaryPicture()), replacement);
            picks.removePicture(picks.primaryPicture());
            picks.addPicture(replacement);
            picks.setPrimaryPicture(replacement);
        }));
        repaint();events.inkChanged();
        return true;
    }

    private boolean erasePictureAt(Point screen) {
        Point2D location = toWorld(screen);
        for (int i = pictures.size()-1; i >= 0; i--) {
            if (pictures.get(i).box().contains(location)) {
                events.userActed();
                int at = i;
                commands.execute(new PictureEditCommand(PictureEditCommand.Kind.REMOVE, contentState, () -> {
                    CanvasPicture erased = pictures.remove(at);
                    picks.removePicture(erased);
                    if (picks.primaryPicture() == erased) picks.setPrimaryPicture(null);
                }));
                repaint();events.inkChanged();
                return true;
            }
        }
        return false;
    }

    /** Immutable session data; no rendered whole-canvas screenshot is used for saving. */
    public CanvasState exportCanvasState() {
        return CanvasStateMapper.export(inkModel, pictures, graphs.all(), viewport.state(),
                tag -> tag instanceof CanvasGraphSource live ? live.graphDefinition() : "");
    }

    /** Load into temporary structures first: invalid/corrupt data never erases the current session. */
    public void restoreCanvasState(CanvasState state) throws IOException {
        CanvasStateMapper.Restored restored = CanvasStateMapper.read(state, this::nextRevision);
        events.userActed();
        inkModel.replaceAll(restored.strokes(), restored.colors(), restored.widths(), restored.book());
        pictures.clear();pictures.addAll(restored.pictures());
        commands.clear();
        picks.clear();
        pen.abandon();graphs.replaceAll(restored.graphs());
        overGraphClose = null;
        viewport.set(restored.viewport());
        repaint();events.inkChanged();events.viewChanged();
    }

    private Color colorOf(Stroke s) {
        return inkModel.colorOf(s);
    }


    private Rectangle2D picturePreviewBounds(CanvasPicture picture) {
        return document.picturePreviewBounds(picture);
    }

    // Reflection-tested entry point kept on the canvas; the conversion itself lives with the painter.
    private static BufferedImage transparentPaper(BufferedImage input) {
        return CanvasPainter.transparentPaper(input);
    }

    // ---------------------------------------------------------------- painting

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        if (gridVisible) {
            CanvasPainter.paintGrid(g2, viewport, getWidth(), getHeight());
        }

        Graphics2D world = (Graphics2D) g2.create();
        viewport.applyTo(world);
        CanvasPainter.paintGraphCards(world, graphs, viewport.zoom(), true);  // under the ink, so writing over it stays visible
        paintPictures(world);
        CanvasPainter.drawStrokesExcept(world, inkModel, picks::contains);
        for (Formula f : inkModel.book().all()) {
            if (f.getAnswer() != null && !picks.isAnswerPicked(f.getId())) {
                CanvasPainter.drawAnswer(world, f.getAnswer());
            }
        }
        if (!nothingSelected()) {
            List<Stroke> picked = new ArrayList<>(picks.strokes());  // same order as the preview, widths live on these
            CanvasPainter.paintSelection(world, inkModel, selectionPreview(), picked, answerPreview(),
                    selectionBounds(), viewport.zoom());
        }
        List<InkPoint> lassoPath = selector.lassoPath();
        if (lassoPath != null && lassoPath.size() > 1) {
            CanvasPainter.paintLasso(world, lassoPath, viewport.zoom());
        }
        world.dispose();

        CanvasPainter.paintGraphCloseButtons(g2, graphs, viewport, overGraphClose);
        if (tool == Tool.ERASER && eraser.outlineAt() != null) {
            CanvasPainter.paintEraser(g2, eraser.outlineAt(), eraserRadius(), eraserMode == EraserMode.PRECISE);
        }
        g2.dispose();
    }

    private void paintPictures(Graphics2D world) {
        CanvasPainter.paintPictures(world, pictures, this::picturePreviewBounds, viewport.zoom(), mathAssetRenderer);
    }

    // ---------------------------------------------------------------- tool ports

    /**
     * The canvas as each tool and the input router see it: one narrow interface per client
     * (Interface Segregation), every call forwarded to the canvas' own private operations.
     * Calls are qualified with {@code DrawingCanvas.this} so none can loop back into this class.
     */
    private final class ToolPorts implements PenTool.Target, EraserTool.Target, SelectionTool.Target,
            CanvasInput.Target {

        @Override public Point2D toWorld(Point screen) { return DrawingCanvas.this.toWorld(screen); }
        @Override public InkPoint worldPoint(MouseEvent e) { return DrawingCanvas.this.worldPoint(e); }
        @Override public void repaint() { DrawingCanvas.this.repaint(); }
        @Override public void setCursor(int cursorType) {
            DrawingCanvas.this.setCursor(Cursor.getPredefinedCursor(cursorType));
        }

        // pen
        @Override public void beginStroke(Stroke stroke, InkPoint first) { DrawingCanvas.this.beginStroke(stroke, first); }
        @Override public void strokeFinished(Stroke stroke) { DrawingCanvas.this.strokeFinished(stroke); }

        // eraser
        @Override public boolean erasePictureAt(Point screen) { return DrawingCanvas.this.erasePictureAt(screen); }
        @Override public boolean eraseBetween(Point2D from, Point2D to, boolean newUndoStep) {
            return DrawingCanvas.this.eraseBetween(from, to, newUndoStep);
        }

        // lasso / box
        @Override public boolean isRectangle() { return tool == Tool.RECTANGLE; }
        @Override public boolean panKeyDown() { return input.isSpaceDown(); }
        @Override public Rectangle selectionBounds() { return DrawingCanvas.this.selectionBounds(); }
        @Override public int handleAt(Point screen) { return DrawingCanvas.this.handleAt(screen); }
        @Override public void clearSelection() { DrawingCanvas.this.clearSelection(); }
        @Override public boolean pickPictureAt(Point2D world) { return DrawingCanvas.this.pickPictureAt(world); }
        @Override public void draggedOver(Point screen) {
            setGraphDropTarget(!picks.hasStrokes() ? null : graphTagAt(screen));
        }
        @Override public Object dropTarget() { return dropTarget; }
        @Override public void clearDropTarget() { setGraphDropTarget(null); }
        @Override public void droppedOnGraph(Object target) { DrawingCanvas.this.droppedOnGraph(target); }
        @Override public void moveSelection(int dx, int dy) { DrawingCanvas.this.moveSelection(dx, dy); }
        @Override public void resizeSelection(Point2D anchor, double factor) {
            DrawingCanvas.this.resizeSelection(anchor, factor);
        }
        @Override public void pickInside(List<InkPoint> loop) { DrawingCanvas.this.pickInside(loop); }
        @Override public void announcePick() {
            if (picks.hasStrokes()) {
                events.picked();
            }
        }

        // router
        @Override public void focus() { requestFocusInWindow(); }
        @Override public CanvasTool activeTool() {
            switch (tool) {
                case PEN:
                    return pen;
                case ERASER:
                    return eraser;
                default:
                    return selector;
            }
        }
        @Override public void userActed() { events.userActed(); }
        @Override public GraphSlot closeButtonAt(Point screen) { return graphCloseAt(screen); }
        @Override public void closeGraph(GraphSlot card) { removeSlot(card); }
        @Override public GraphSlot hoveredClose() { return overGraphClose; }
        @Override public void setHoveredClose(GraphSlot card) { overGraphClose = card; }
        @Override public void panBy(double dx, double dy) { DrawingCanvas.this.panBy(dx, dy); }
        @Override public void zoomAt(double screenX, double screenY, double factor, long when) {
            DrawingCanvas.this.zoomAt(screenX, screenY, factor, when);
        }
    }
}
