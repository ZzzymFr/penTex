package view.board;

import view.DrawingCanvas;
import view.board.PdfPanel;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

/**
 * Pen / Eraser / Lasso | colours | Undo / Clear, floating over the canvas area. Drag it by the grip
 * on its end; it docks to the middle of the nearest edge (standing up on the left / right) and remembers it.
 */
public final class FloatingToolbar {
    private static final int FRAME_MS = Motion.FRAME_MS;
    private static final float DECELERATION = 0.99f;  // snappy throw, ~0.1s of travel per px/s
    private static final long VELOCITY_WINDOW_NS = 90_000_000L;

    private final DrawingCanvas canvas;
    private final JComponent area;
    private final PdfPanel pdfPanel;
    private final GlassBar bar;
    private final GlassBar.Item[] tools = new GlassBar.Item[4];
    private final GlassBar.Item pen;
    private final ToolOptionsPopup options;
    private DrawingCanvas.Tool lastClicked;
    private boolean toolClickArmed;
    private boolean optionsOpen;
    /** PDF pointer restores the editor's native selection/double-click behavior. */
    private boolean pdfPointerMode;
    private final Spring sx = new Spring();
    private final Spring sy = new Spring();
    private final Timer timer = new Timer(FRAME_MS, e -> step());
    private final Motion.Clock clock = new Motion.Clock();
    private final java.util.ArrayDeque<long[]> samples = new java.util.ArrayDeque<>();  // {nanos, x, y}
    private ToolbarSpot spot = ToolbarSpot.BOTTOM;
    private boolean dragging;
    private Point grab;
    private Point pointer;   // mouse in area coords while dragging

    // area = where the bar floats, same coords as the canvas
    public FloatingToolbar(DrawingCanvas canvas, JComponent area, PdfPanel pdfPanel) {
        this.pdfPanel = java.util.Objects.requireNonNull(pdfPanel);
        this.canvas = canvas;
        this.area = area;
        bar = new GlassBar(BoardTheme.font(14f), canvas, canvas::paintContent, canvas::contentVersion);
        bar.grip(new GlassBar.GripListener() {
            @Override
            public void pressed(Point screen) {
                startDrag(screen);
            }

            @Override
            public void dragged(Point screen) {
                dragTo(screen);
            }

            @Override
            public void released(Point screen) {
                drop();
            }
        });
        options = new ToolOptionsPopup(canvas, this::useColor);
        tools[3] = bar.segment("Pointer", Icons::pointer, 1, this::choosePointer);
        pen = tools[0] = bar.segment("Pen", Icons::pencil, 1, () -> chooseTool(DrawingCanvas.Tool.PEN));
        tools[1] = bar.segment("Eraser", Icons::eraser, 1, () -> chooseTool(DrawingCanvas.Tool.ERASER));
        tools[2] = bar.segment("Lasso", Icons::lasso, 1, () -> chooseTool(DrawingCanvas.Tool.LASSO));
        bar.separator();
        bar.action("Undo", Icons::undo, canvas::undo, canvas::canUndo);
        bar.action("Redo", Icons::redo, canvas::redo, canvas::canRedo);
        bar.action("Clear", Icons::trash, canvas::clear, () -> !canvas.isBlank());
        bar.finish();
        bar.select(itemFor(canvas.getTool()));
        pdfPanel.editor().pageView().configurePdfDrawing(true,
                () -> pdfPointerMode ? null : canvas.getTool(),
                canvas::getInkColor, canvas::getPenWidth, canvas::getEraserMode, canvas::getEraserSize);
        bar.setHoverToolLabels(true, this::hoverResized);

        canvas.addPropertyChangeListener(DrawingCanvas.TOOL_PROPERTY, e -> {
            pdfPointerMode = false;
            bar.select(itemFor((DrawingCanvas.Tool) e.getNewValue()));
            optionsOpen = false;
            if (options.isVisible()) options.setVisible(false);
            bar.repaint();
        });
        canvas.addUserActionListener(() -> SwingUtilities.invokeLater(bar::repaint));  // Undo / Clear greys
        canvas.addStrokeListener(s -> bar.repaint());
        area.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                relayout();
            }
        });
        if (area instanceof BoardWindow board) {
            board.addPropertyChangeListener("canvasDockBounds", e -> relayout());
            // The Board reports the actual clipped viewport, not the overscanned page.
        }
    }

    public JComponent component() {
        return bar;
    }

    public GlassBar bar() {
        return bar;
    }

    public ToolbarSpot getSpot() {
        return spot;
    }

    // jumps there, no animation
    public void setSpot(ToolbarSpot spot) {
        this.spot = spot;
        relayout();
    }

    private GlassBar.Item itemFor(DrawingCanvas.Tool tool) {
        return tools[tool == DrawingCanvas.Tool.RECTANGLE ? 2 : tool.ordinal()];
    }

    private boolean choosePointer() {
        pdfPanel.editor().pageView().clearPageSelection();
        // The ordinary pointer belongs to the PDF interaction mode; Canvas keeps
        // its existing safe selection behavior without modifying teammates' code.
        canvas.setTool(DrawingCanvas.Tool.LASSO);
        pdfPointerMode = true;
        optionsOpen = false;
        options.setVisible(false);
        bar.select(tools[3]);
        pdfPanel.editor().pageView().setCursor(java.awt.Cursor.getDefaultCursor());
        toolClickArmed = false;
        lastClicked = null;
        return true;
    }

    private boolean chooseTool(DrawingCanvas.Tool tool) {
        boolean wasPointer = pdfPointerMode;
        if (wasPointer || canvas.getTool() != tool)
            pdfPanel.editor().pageView().clearPageSelection();
        pdfPointerMode = false;
        boolean sameSelection = tool == DrawingCanvas.Tool.LASSO
                && canvas.getTool() == DrawingCanvas.Tool.RECTANGLE;
        if (toolClickArmed && lastClicked == tool && (canvas.getTool() == tool || sameSelection)) {
            // Swing may dismiss a popup on mouse press, before our tool's release callback.
            // Track explicit clicks, not isVisible(), so every click really toggles.
            optionsOpen = !optionsOpen;
            if (optionsOpen) {
                options.showFor(canvas.getTool(), bar, spot, bar.capsuleBounds(), bar.centerOf(itemFor(tool)));
            } else {
                options.setVisible(false);
            }
        } else {
            optionsOpen = false;
            options.setVisible(false);
            canvas.setTool(tool);
        }
        toolClickArmed = true;
        lastClicked = tool;
        return true;
    }

    // picking a colour recolours a lasso pick, else goes back to the pen
    private void useColor(Color c) {
        canvas.setInkColor(c);
        if (canvas.recolorSelection(c)) {
            return;
        }
        if (canvas.getTool() != DrawingCanvas.Tool.PEN) {
            canvas.setTool(DrawingCanvas.Tool.PEN);
            bar.select(pen);
        }
    }

    // a hovered tool's label grows the bar: keep its start edge where it is, so the tool under the
    // pointer stays under it (re-centring here made the whole bar slide on every hover)
    private void hoverResized() {
        if (dragging) {
            return;
        }
        bar.setSize(bar.getPreferredSize());
        bar.repaint();
    }

    // stands up on the side edges; narrow room: labels off, then squeezes; then to the remembered spot
    public void relayout() {
        if (!dragging) {
            fit(spot);
        }
        if (!dragging && !timer.isRunning()) {
            bar.setLocation(target());
        }
        bar.repaint();
    }

    // where it docks: the canvas part of the window (not over an open PDF / History panel)
    private java.awt.Rectangle dockFrame() {
        if (area instanceof BoardWindow board) {
            java.awt.Rectangle page = board.pageBounds();
            if (page.width > 0 && page.height > 0) {
                return page;
            }
        }
        return new java.awt.Rectangle(0, 0, area.getWidth(), area.getHeight());
    }

    private void fit(ToolbarSpot s) {
        java.awt.Rectangle f = area instanceof BoardWindow board ? board.toolbarFitBounds() : dockFrame();
        bar.setVertical(s.isVertical());
        bar.fitTo(s.room(f.width, f.height));
        bar.setSize(bar.getPreferredSize());
    }

    private Point target() {
        java.awt.Rectangle f = dockFrame();
        Point cap = spot.capsuleAt(f.width, f.height, bar.restingCapsuleWidth(), GlassBar.CAP_H);
        return new Point(f.x + cap.x - GlassBar.MARGIN, f.y + cap.y - GlassBar.MARGIN);
    }

    // middle of the grip, in bar coords
    private Point gripCenter() {
        int along = GlassBar.MARGIN + GlassBar.PAD + GlassBar.GRIP_W / 2;
        int across = GlassBar.MARGIN + GlassBar.CAP_H / 2;
        return bar.isVertical() ? new Point(across, along) : new Point(along, across);
    }

    private void startDrag(Point screen) {
        dragging = true;
        timer.stop();  // caught mid-flight: carry on from where it is on screen
        clock.reset();
        samples.clear();
        Point at = bar.getLocationOnScreen();
        grab = new Point(screen.x - at.x, screen.y - at.y);
    }

    // follows the mouse; turns upright / flat as soon as a side edge / the top or bottom is closest
    private void dragTo(Point screen) {
        if (!dragging) {
            return;
        }
        Point areaAt = area.getLocationOnScreen();
        pointer = new Point(screen.x - areaAt.x, screen.y - areaAt.y);
        java.awt.Rectangle f = dockFrame();
        ToolbarSpot live = ToolbarSpot.nearest(new Point(pointer.x - f.x, pointer.y - f.y), f.width, f.height);
        if (live.isVertical() != bar.isVertical()) {
            fit(live);
            grab = gripCenter();  // keep the grip under the mouse
        }
        remember(pointer);
        int m = GlassBar.MARGIN;
        int x = soften(pointer.x - grab.x, -m, area.getWidth() - bar.getWidth() + m, bar.getWidth());
        int y = soften(pointer.y - grab.y, -m, area.getHeight() - bar.getHeight() + m, bar.getHeight());
        bar.setLocation(x, y);
        bar.repaint();
    }

    // past an edge it still follows, just less and less
    private static int soften(int v, int lo, int hi, int size) {
        if (v < lo) return lo + Math.round(Motion.rubberband(v - lo, size));
        if (v > hi) return hi + Math.round(Motion.rubberband(v - hi, size));
        return v;
    }

    private void remember(Point p) {
        long now = System.nanoTime();
        samples.addLast(new long[]{now, p.x, p.y});
        while (samples.size() > 2 && now - samples.peekFirst()[0] > VELOCITY_WINDOW_NS) {
            samples.removeFirst();
        }
    }

    // px/s over the last ~90ms; zero if the mouse sat still before letting go
    private float[] releaseVelocity() {
        if (samples.size() < 2) return new float[]{0, 0};
        long[] a = samples.peekFirst();
        long[] b = samples.peekLast();
        long sinceLast = System.nanoTime() - b[0];
        float secs = (b[0] - a[0]) / 1e9f;
        if (secs <= 0.005f || sinceLast > VELOCITY_WINDOW_NS) return new float[]{0, 0};
        return new float[]{(b[1] - a[1]) / secs, (b[2] - a[2]) / secs};
    }

    // throw it: dock at the edge the flick is heading for, keep the hand's speed
    private void drop() {
        dragging = false;
        float[] v = releaseVelocity();
        samples.clear();
        if (pointer != null) {
            Point projected = new Point(
                    pointer.x + Math.round(Motion.project(v[0], DECELERATION)),
                    pointer.y + Math.round(Motion.project(v[1], DECELERATION)));
            java.awt.Rectangle f = dockFrame();
            spot = ToolbarSpot.nearest(new Point(projected.x - f.x, projected.y - f.y), f.width, f.height);
        }
        pointer = null;
        Point from = bar.getLocation();
        boolean turned = bar.isVertical() != spot.isVertical();
        fit(spot);
        sx.x = from.x;
        sy.x = from.y;
        // turning upright swaps the bar's axes, the old speed wouldn't mean much
        sx.v = turned ? 0 : v[0];
        sy.v = turned ? 0 : v[1];
        clock.reset();
        timer.start();
    }

    private void step() {
        float dt = clock.tick();
        Point t = target();
        boolean done = sx.step(t.x, Motion.THROW, dt) & sy.step(t.y, Motion.THROW, dt);
        bar.setLocation(Math.round(sx.x), Math.round(sy.x));
        bar.repaint();
        if (done) {
            timer.stop();
            clock.reset();
        }
    }
}
