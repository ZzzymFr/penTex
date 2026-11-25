package view.board;

import view.DrawingCanvas;
import interface_adapter.history.HistoryItem;
import javax.swing.JPopupMenu;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

/**
 * The one board: a full-window canvas with History (left) and PDF (right) panels that slide in
 * next to it behind a draggable divider, glass capsules on top, and the floating toolbar.
 */
public class BoardWindow extends JPanel implements HistoryPanel.DragHost {
    private final JComponent page;
    private final DrawingCanvas canvas;
    private final HistoryPanel history;
    private final JComponent pdf;
    private final SplitDivider historyDivider;
    private final SplitDivider pdfDivider;
    private final GlassBar historyBar;
    private final GlassBar.Item historyItem;
    private final GlassBar historyClose;
    private final GlassBar pdfBar;
    private final GlassBar pdfClose;
    private final GlassBar fullscreenExit;
    private final Ghost ghost = new Ghost();
    private final JComponent titleStrip;
    private boolean historyOpen;
    private boolean pdfOpen;
    private boolean pdfFullscreen;
    private JComponent floatingToolbar;
    private double historyRatio = 0.24;  // kept while the app runs
    private double pdfRatio = 0.56;
    private boolean pdfOverlay;
    private int lastCanvasRight = -1;
    private int lastBoardWidth = -1;
    private final Function<String, Boolean> deleteHistory;
    private final Runnable saveCanvas;
    private final Runnable saveAsCanvas;
    private final GlassBar.Item saveItem;
    private final CanvasStatusChip saveStatusChip = new CanvasStatusChip();
    private Supplier<SaveMenuState> saveState = SaveMenuState::newCanvas;
    private SaveMenuState lastSaveState = SaveMenuState.newCanvas();
    // Coalesce rapid pen/pan events: serializing image assets for the dirty check is expensive.
    private final javax.swing.Timer saveStateDebounce = new javax.swing.Timer(150, e -> refreshSaveState());

    public BoardWindow(JComponent page, DrawingCanvas canvas,
                       java.util.function.Function<HistoryPanel.DragHost, HistoryPanel> history, JComponent pdf) {
        this(page, canvas, history, pdf, id -> false);
    }

    /** The composition root supplies the persistent-history delete command. */
    public BoardWindow(JComponent page, DrawingCanvas canvas,
                       java.util.function.Function<HistoryPanel.DragHost, HistoryPanel> history,
                       JComponent pdf, Function<String, Boolean> deleteHistory) {
        this(page, canvas, history, pdf, deleteHistory, () -> {}, () -> {});
    }

    /** Composition-root commands own Save/Open, not the layout view. */
    public BoardWindow(JComponent page, DrawingCanvas canvas,
                       java.util.function.Function<HistoryPanel.DragHost, HistoryPanel> history,
                       JComponent pdf, Function<String, Boolean> deleteHistory,
                       Runnable saveCanvas, Runnable openCanvas) {
        this(page, canvas, history, pdf, deleteHistory, saveCanvas, saveCanvas, openCanvas);
    }

    /** Distinct Save / Save As callbacks supplied by the composition root. */
    public BoardWindow(JComponent page, DrawingCanvas canvas,
                       java.util.function.Function<HistoryPanel.DragHost, HistoryPanel> history,
                       JComponent pdf, Function<String, Boolean> deleteHistory,
                       Runnable saveCanvas, Runnable saveAsCanvas, Runnable openCanvas) {
        super(null);
        saveStateDebounce.setRepeats(false);
        saveStatusChip.setGlassBackdrop(this::paintBackdrop, this::backdropVersion);
        saveStatusChip.setRenameAction(() -> {
            openCanvas.run(); // Open's existing glass chooser exposes Rename; no shared wiring changes.
            refreshSaveState();
        });
        this.saveCanvas = java.util.Objects.requireNonNull(saveCanvas);
        this.saveAsCanvas = java.util.Objects.requireNonNull(saveAsCanvas);
        this.page = page;
        this.canvas = canvas;
        this.history = history.apply(this);
        this.pdf = pdf;
        this.deleteHistory = deleteHistory;
        setBackground(BoardTheme.CANVAS);

        historyDivider = new SplitDivider(x -> {
            historyRatio = SplitMath.ratioFor(x, getWidth());
            relayout();
        }, () -> {
            historyRatio = 0.24;
            relayout();
        });
        pdfDivider = new SplitDivider(x -> {
            pdfRatio = SplitMath.ratioFor(getWidth() - x, getWidth());
            relayout();
        }, () -> {
            pdfRatio = 0.56;
            relayout();
        });

        historyBar = bar(15f);
        historyItem = historyBar.segment("History", Icons::history, 0, () -> {
            setHistoryOpen(!historyOpen);
            return historyOpen;
        });
        saveItem = historyBar.action("Save…", Icons::save, () -> {
            if (lastSaveState.phase() != SaveMenuState.Phase.SAVED) {
                runSaveWithStatus(this.saveCanvas);
            }
        }, () -> true);
        saveItem.setEnabled(() -> lastSaveState.phase() != SaveMenuState.Phase.SAVED);
        saveItem.setSplitGroup(1);
        historyBar.action(null, Icons::chevronDown, this::showSaveChoices, () -> true).setSplitGroup(1);
        historyBar.action("Open", Icons::openFolder, () -> {
            openCanvas.run();
            refreshSaveState();
        }, () -> true);
        historyBar.finish();
        historyClose = bar(15f);
        historyClose.action(null, Icons::close, () -> setHistoryOpen(false), () -> true);
        historyClose.finish();
        pdfBar = bar(15f);
        // Default to real split view, with no second overlay mode in the UI.
        pdfBar.action("PDF", Icons::document, () -> {pdfOverlay = false; setPdfOpen(true);}, () -> true);
        pdfBar.finish();
        pdfClose = bar(15f);
        pdfClose.action(null, Icons::close, () -> setPdfOpen(false), () -> true);
        pdfClose.finish();
        fullscreenExit = bar(15f);
        fullscreenExit.action(null, Icons::close, () -> setPdfFullscreen(false), () -> true);
        fullscreenExit.finish();
        fullscreenExit.setToolTipText("Exit PDF fullscreen (Esc)");
        titleStrip = BoardTheme.MAC ? new TitleStrip() : null;

        // first added = drawn on top
        add(ghost);
        add(fullscreenExit);
        add(saveStatusChip);
        add(historyBar);
        add(historyClose);
        add(pdfBar);
        add(pdfClose);
        if (titleStrip != null) {
            add(titleStrip);
        }
        add(historyDivider);
        add(pdfDivider);
        add(this.history);
        add(pdf);
        add(page);
        relayout();
    }

    /** Wire the adapter-provided canvas state after the composition root has built both objects. */
    public void setSaveStateSupplier(Supplier<SaveMenuState> supplier) {
        saveState = java.util.Objects.requireNonNull(supplier);
        refreshSaveState();
    }

    /** Coalesce rapid mouse events; the visible Saved/Edited status settles after the gesture. */
    public void scheduleSaveStateRefresh() {
        saveStateDebounce.restart();
    }

    /** Called immediately after Open, Save and keyboard actions. */
    public void refreshSaveState() {
        SaveMenuState next = java.util.Objects.requireNonNull(saveState.get());
        if (next.equals(lastSaveState)) {
            // A cancelled synchronous save restores the previous visual status as well.
            saveStatusChip.setState(next);
            return;
        }
        lastSaveState = next;
        saveStatusChip.setState(next);
        switch (next.phase()) {
            case NEW -> saveItem.setDisplay("Save…", Icons::save);
            case MODIFIED -> saveItem.setDisplay("Save", Icons::save);
            case SAVING -> saveItem.setDisplay("Saving…", Icons::save);
            case SAVED -> saveItem.setDisplay("Saved", Icons::check);
        }
        historyBar.finish();
        relayout();
    }

    /** Show saving feedback immediately, then restore the workspace's authoritative state. */
    private void runSaveWithStatus(Runnable action) {
        saveStatusChip.setState(new SaveMenuState(lastSaveState.name(), SaveMenuState.Phase.SAVING));
        if (saveStatusChip.isShowing())
            saveStatusChip.paintImmediately(0, 0, saveStatusChip.getWidth(), saveStatusChip.getHeight());
        try { action.run(); }
        finally { refreshSaveState(); }
    }

    /** The menu always opens from the chevron, with options appropriate to the current state. */
    JPopupMenu createSaveMenu() {
        return new GlassSaveMenu(lastSaveState,
                () -> runSaveWithStatus(saveCanvas),
                () -> runSaveWithStatus(saveAsCanvas));
    }

    private void showSaveChoices() {
        // GlassBar includes a generous shadow margin. Anchor below the visible capsule.
        // Align with the Save/chevron group, not an old hard-coded offset.
        createSaveMenu().show(historyBar,
                GlassBar.MARGIN + GlassBar.PAD + Math.round(saveItem.x),
                GlassBar.MARGIN + GlassBar.CAP_H);
    }

    private GlassBar bar(float size) {
        return new GlassBar(BoardTheme.font(size), this, this::paintBackdrop, this::backdropVersion);
    }

    public boolean isHistoryOpen() {
        return historyOpen;
    }

    public boolean isPdfOpen() {
        return pdfOpen;
    }

    // only one panel at a time, the window is too narrow for both
    public void setHistoryOpen(boolean open) {
        historyOpen = open;
        if (open) {
            historyBar.select(historyItem);
        } else {
            historyBar.deselect(0);
        }
        relayout();
    }

    public void setPdfOpen(boolean open) {
        if (!open) { pdfFullscreen = false; ghost.clearOutline(); }
        boolean firstOpen = open && !pdfOpen;
        pdfOpen = open;
        relayout();
        if (firstOpen && pdf instanceof PdfWorkspacePort panel) {
            SwingUtilities.invokeLater(panel::fitWhenOpened);
        }
    }

    public boolean isPdfFullscreen() { return pdfFullscreen; }
    /** Full PDF focus mode, escaped with Esc or the View ribbon control. */
    public void setPdfFullscreen(boolean fullscreen) {
        if (fullscreen) { pdfOpen = true; ghost.clearOutline(); }
        pdfFullscreen = fullscreen;
        relayout();
        if (fullscreen && pdf instanceof PdfWorkspacePort port) SwingUtilities.invokeLater(port::fitWhenOpened);
    }
    public void addFloatingToolbar(JComponent toolbar) {
        floatingToolbar = java.util.Objects.requireNonNull(toolbar);
        add(toolbar, 0); // Swing index 0 paints over both panes
        revalidate();repaint();
    }
    public boolean isPdfOverlay() { return pdfOverlay; }
    /** Overlay keeps the canvas at full width behind the PDF pane; split reserves its own width. */
    public void setPdfOverlay(boolean overlay) { pdfOverlay = overlay; relayout(); }

    double getHistoryRatio() {
        return historyRatio;
    }

    double getPdfRatio() {
        return pdfRatio;
    }

    Rectangle pageBounds() {
        return page.getBounds();
    }

    private void relayout() {
        revalidate();
        repaint();
    }

    // windows with child overlap must repaint the capsules when the canvas repaints
    @Override
    public boolean isOptimizedDrawingEnabled() {
        return false;
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(900, 640);
    }

    @Override
    public void doLayout() {
        int w = getWidth();
        int h = getHeight();
        int half = SplitDivider.HIT / 2;
        if (pdfFullscreen) {
            history.setVisible(false);historyDivider.setVisible(false);
            pdf.setVisible(true);pdfDivider.setVisible(false);
            page.setVisible(false);
            historyBar.setVisible(false);historyClose.setVisible(false);saveStatusChip.setVisible(false);
            pdfBar.setVisible(false);pdfClose.setVisible(false);
            fullscreenExit.setVisible(true);
            int margin = GlassBar.MARGIN;
            Dimension closeSize = fullscreenExit.getPreferredSize();
            fullscreenExit.setBounds(w - BoardTheme.SIDE - fullscreenExit.capsuleWidth() - margin,
                    BoardTheme.TOP - margin, closeSize.width, closeSize.height);
            if (floatingToolbar != null) floatingToolbar.setVisible(false);
            if (titleStrip != null) titleStrip.setVisible(false);
            pdf.setBounds(0, 0, w, h);
            ghost.setBounds(0, 0, w, h);
            return;
        }
        page.setVisible(true);
        fullscreenExit.setVisible(false);
        historyBar.setVisible(true);pdfBar.setVisible(true);
        if (floatingToolbar != null) floatingToolbar.setVisible(true);
        if (titleStrip != null) titleStrip.setVisible(true);
        history.setVisible(historyOpen);
        historyDivider.setVisible(historyOpen);
        pdf.setVisible(pdfOpen);
        pdfDivider.setVisible(pdfOpen);
        // In split mode the Canvas really owns a smaller viewport: panels never overlap it.
        // Ratio constraints are based on the remaining width after History.
        int hw = historyOpen ? Math.min(SplitMath.panelWidth(historyRatio, w), Math.max(0, w - 340)) : 0;
        int remaining = Math.max(0, w - hw);
        int pw = pdfOpen ? Math.max(0, Math.min(SplitMath.panelWidth(pdfRatio, w),
                Math.max(0, remaining - (pdfOverlay ? 0 : 250)))) : 0;
        if (historyOpen) {
            history.setBounds(0, 0, hw, h);
            historyDivider.setBounds(hw - half, 0, SplitDivider.HIT, h);
        }
        if (pdfOpen) {
            pdf.setBounds(w - pw, 0, pw, h);
            pdfDivider.setBounds(w - pw - half, 0, SplitDivider.HIT, h);
        }
        int canvasWidth = Math.max(0, w - hw - (pdfOpen && !pdfOverlay ? pw : 0));
        page.setBounds(hw, 0, canvasWidth, h);
        page.doLayout();
        // Only changes in the right edge of the canvas caused by PDF splitting
        // move the ink view. A left-side History opening changes the left boundary
        // but should not push canvas ink behind History. Window resize does not pan.
        int rightEdge = hw + canvasWidth;
        if (lastCanvasRight >= 0 && lastBoardWidth == w && lastCanvasRight != rightEdge) {
            canvas.panBy(rightEdge - lastCanvasRight, 0);
        }
        lastCanvasRight = rightEdge;
        lastBoardWidth = w;

        int m = GlassBar.MARGIN;
        Dimension hb = historyBar.getPreferredSize();
        boolean showStatus = w >= 1050 && !pdfFullscreen;
        saveStatusChip.setVisible(showStatus);
        int historyStart = BoardTheme.HISTORY_LEFT;
        if (showStatus) {
            Dimension state = saveStatusChip.getPreferredSize();
            saveStatusChip.setBounds(BoardTheme.HISTORY_LEFT - 35, BoardTheme.TOP + 2,
                    state.width, state.height);
            historyStart = BoardTheme.HISTORY_LEFT + state.width - 18;
        }
        historyBar.setBounds(historyStart - m, BoardTheme.TOP - m, hb.width, hb.height);
        Dimension hc = historyClose.getPreferredSize();
        historyClose.setVisible(historyOpen);
        historyClose.setBounds(historyStart + historyBar.capsuleWidth() + 8 - m, BoardTheme.TOP - m,
                hc.width, hc.height);
        Dimension pb = pdfBar.getPreferredSize();
        pdfBar.setVisible(true);
        pdfBar.setBounds(w - BoardTheme.SIDE - pdfBar.capsuleWidth() - m, BoardTheme.TOP - m, pb.width, pb.height);
        Dimension pc = pdfClose.getPreferredSize();
        pdfClose.setVisible(pdfOpen);
        pdfClose.setBounds(w - BoardTheme.SIDE - pdfBar.capsuleWidth() - pdfClose.capsuleWidth() - 13 - m, BoardTheme.TOP - m,
                pc.width, pc.height);
        if (titleStrip != null) {
            titleStrip.setBounds(0, 0, w, BoardTheme.TITLEBAR_H);
        }
        ghost.setBounds(0, 0, w, h);
    }

    // what the top capsules see: panels as flat colour, the canvas content where it is
    private void paintBackdrop(Graphics2D g) {
        if (history.isVisible()) {
            g.setColor(BoardTheme.PANEL);
            g.fill(history.getBounds());
        }
        if (pdf.isVisible()) {
            g.setColor(Color.WHITE);
            g.fill(pdf.getBounds());
        }
        if (canvas.getWidth() <= 0) {
            return;
        }
        Point c = SwingUtilities.convertPoint(canvas, 0, 0, this);
        Graphics2D cg = (Graphics2D) g.create();
        cg.clip(page.getBounds());
        cg.clipRect(c.x, c.y, canvas.getWidth(), canvas.getHeight());
        cg.translate(c.x, c.y);
        canvas.paintContent(cg);
        cg.dispose();
    }

    private long backdropVersion() {
        long v = canvas.contentVersion();
        v = v * 31 + (historyOpen ? 1 : 0) + (pdfOpen ? 2 : 0);
        return v * 31 + page.getX() * 131L + page.getWidth();
    }

    @Override public void importIntoPdf(HistoryItem item) {
        if (pdf instanceof PdfWorkspacePort panel) {
            setPdfOpen(true);
            panel.addHistoryItem(item);
        }
    }
    @Override public void deleteHistory(HistoryItem item) { deleteHistory.apply(item.getId()); }
    @Override public void editHistoryFormula(HistoryItem item) {
        if (pdf instanceof PdfWorkspacePort panel) panel.editHistoryFormula(item);
    }

    @Override public boolean dropped(HistoryItem item, BufferedImage picture, Point screenTopLeft) {
        return droppedAt(item, picture, screenTopLeft, screenTopLeft);
    }

    @Override public boolean droppedAt(HistoryItem item, BufferedImage picture,
                                       Point previewTopLeft, Point cursorScreen) {
        ghost.clear(); ghost.clearOutline();
        // Hit-test against the board's actual PDF bounds, not the nested editor's
        // transient Swing showing state (which can change while dragging).
        Point onBoard = new Point(cursorScreen);
        SwingUtilities.convertPointFromScreen(onBoard, this);
        if (pdf instanceof PdfWorkspacePort panel && isPdfDropTarget(onBoard)) {
            return panel.dropHistoryItem(item, cursorScreen);
        }
        if (historyOpen && history.isVisible() && history.getBounds().contains(onBoard)) return false;
        Point onPage = new Point(cursorScreen);
        SwingUtilities.convertPointFromScreen(onPage, page);
        if (!canvas.isShowing() || onPage.x < 0 || onPage.x >= page.getWidth()
                || onPage.y < 0 || onPage.y >= page.getHeight()) return false;
        // Preserve the pointer's grab offset inside the placed object when there is room.
        Point position = new Point(previewTopLeft);
        SwingUtilities.convertPointFromScreen(position, canvas);
        Point cursor = new Point(cursorScreen);
        SwingUtilities.convertPointFromScreen(cursor, canvas);
        position.x = Math.max(0, Math.min(position.x, cursor.x));
        position.y = Math.max(0, Math.min(position.y, cursor.y));
        if (item.entry().graphDefinition() != null) {
            if (!(page instanceof view.canvas.AnswerCanvasPage livePage)) return false;
            try {
                entity.graph.GraphDefinition definition =
                        interface_adapter.graph.GraphDefinitionCodec.decode(item.entry().graphDefinition());
                return livePage.restoreHistoryGraph(definition, position);
            } catch (IllegalArgumentException invalid) {
                return false; // never silently flatten an interactive graph
            }
        }
        canvas.addHistoryAsset(picture, item.hasImage() ? "" : item.getLatex(),
                item.hasImage() ? "" : item.getAnswer(), position);
        return true;
    }

    /** Compatibility entry point for older graph integrations. */
    public void previewInteractiveGraphDrop(Point cursorOnScreen, double aspectRatio) {
        previewInteractiveGraphDrop(cursorOnScreen, aspectRatio, null);
    }

    /** Draw the current Graph thumbnail with the same bounds as the eventual PDF placement. */
    public void previewInteractiveGraphDrop(Point cursorOnScreen, double aspectRatio,
                                            BufferedImage thumbnail) {
        if (!acceptsInteractiveGraphDrop(cursorOnScreen) || !(pdf instanceof PdfWorkspacePort port)) {
            ghost.clearOutline();
            return;
        }
        Rectangle onScreen = port.interactiveGraphPreviewBounds(cursorOnScreen, aspectRatio);
        Point pointer = new Point(cursorOnScreen);
        SwingUtilities.convertPointFromScreen(pointer, this);
        Rectangle pagePlacement;
        if (onScreen == null) {
            // The PDF view may still be laying out its paper after opening/resizing.
            // Give immediate visual feedback instead of silently dropping the preview.
            double ratio = Double.isFinite(aspectRatio) && aspectRatio > 0 ? aspectRatio : 1;
            int width = Math.max(40, Math.min(180, pdf.getWidth() / 3));
            pagePlacement = new Rectangle(pointer.x, pointer.y, width,
                    Math.max(32, (int) Math.round(width / ratio)));
        } else {
            Point at = onScreen.getLocation();
            SwingUtilities.convertPointFromScreen(at, this);
            pagePlacement = new Rectangle(at.x, at.y, onScreen.width, onScreen.height);
        }
        // A preview over the actual paper must not move or change size after release.
        // When paper is not yet visible, keep a same-sized ghost near the pointer.
        Rectangle preview = onScreen != null && pdf.getBounds().intersects(pagePlacement)
                ? pagePlacement : sameSizeFallbackPreview(pagePlacement, pointer, pdf.getBounds());
        ghost.showGraphPreview(thumbnail, preview, pdf.getBounds());
    }

    static Rectangle sameSizeFallbackPreview(Rectangle placement, Point pointer, Rectangle pdfBounds) {
        int x = Math.max(pdfBounds.x + 4, Math.min(pointer.x - 14,
                pdfBounds.x + Math.max(4, pdfBounds.width - placement.width - 4)));
        int y = Math.max(pdfBounds.y + 4, Math.min(pointer.y - 14,
                pdfBounds.y + Math.max(4, pdfBounds.height - placement.height - 4)));
        return new Rectangle(x, y, placement.width, placement.height);
    }

    /** Keep a drop outline visible next to the pointer even while it crosses the
     * PDF ribbon or Library sidebar before reaching the paper. Once the pointer
     * reaches the actual paper, the outline uses the final placement geometry.
     * This is visual feedback only: it never creates or deletes an item.
     */
    static Rectangle visibleInteractiveGraphPreview(Rectangle pagePlacement,
                                                    Point pointer, Rectangle pdfBounds) {
        Rectangle safe = pdfBounds;
        if (safe.isEmpty()) return pagePlacement;
        // When the page's final placement starts at the pointer (within the
        // document), keep the accurate location instead of following an offset.
        if (Math.abs(pagePlacement.x - pointer.x) <= 36
                && Math.abs(pagePlacement.y - pointer.y) <= 36
                && safe.intersects(pagePlacement)) return pagePlacement;
        int width = Math.max(1, Math.min(pagePlacement.width, safe.width - 8));
        int height = Math.max(1, Math.min(pagePlacement.height, safe.height - 8));
        int x = Math.max(safe.x + 4, Math.min(pointer.x - 14, safe.x + safe.width - width - 4));
        int y = Math.max(safe.y + 4, Math.min(pointer.y - 14, safe.y + safe.height - height - 4));
        return new Rectangle(x, y, width, height);
    }

    public void cancelInteractiveGraphPreview() { ghost.clearOutline(); }

    /** A Graph can be placed only when the mouse is released over the visible PDF panel. */
    public boolean acceptsInteractiveGraphDrop(Point cursorOnScreen) {
        if (!isShowing()) return false;
        Point boardPoint = new Point(cursorOnScreen);
        SwingUtilities.convertPointFromScreen(boardPoint, this);
        return isPdfDropTarget(boardPoint) && pdf instanceof PdfWorkspacePort;
    }

    /** Called by a live graph card only after a header drag crosses the PDF divider. */
    public boolean dropInteractiveGraph(entity.graph.GraphDefinition graph,
                                        BufferedImage preview, Point cursorOnScreen) {
        Point boardPoint = new Point(cursorOnScreen);
        SwingUtilities.convertPointFromScreen(boardPoint, this);
        if (!isPdfDropTarget(boardPoint) || !(pdf instanceof PdfWorkspacePort panel)) return false;
        return panel.dropInteractiveGraph(graph, preview, cursorOnScreen);
    }

    /** Package-private for regression tests: PDF takes precedence over Canvas. */
    boolean isPdfDropTarget(Point boardPoint) {
        return pdfOpen && pdf.isVisible() && pdf.getBounds().contains(boardPoint);
    }

    @Override public void cancelDrag() { ghost.clear(); ghost.clearOutline(); }

    // ---- dragging a history row onto the canvas

    @Override public void draggingAt(HistoryItem item, BufferedImage picture, Point screenTopLeft, Point cursorScreen) {
        Point cursor = new Point(cursorScreen);
        SwingUtilities.convertPointFromScreen(cursor, this);
        if (isPdfDropTarget(cursor) && pdf instanceof PdfWorkspacePort port) {
            Rectangle placement = port.historyPreviewBounds(item, cursorScreen);
            if (placement != null) {
                Point at = placement.getLocation(); SwingUtilities.convertPointFromScreen(at, this);
                placement.setLocation(at);
                ghost.clear();
                ghost.showGraphPreview(picture, pdf.getBounds().intersects(placement) ? placement
                        : sameSizeFallbackPreview(placement, cursor, pdf.getBounds()), pdf.getBounds());
                return;
            }
        }
        ghost.clearOutline();
        dragging(picture, screenTopLeft);
    }

    @Override
    public void dragging(BufferedImage picture, Point screenTopLeft) {
        Point p = new Point(screenTopLeft);
        SwingUtilities.convertPointFromScreen(p, this);
        ghost.showPicture(picture, p);
    }

    @Override
    public boolean dropped(BufferedImage picture, Point screenTopLeft) {
        ghost.clear();
        Point onCanvas = new Point(screenTopLeft);
        SwingUtilities.convertPointFromScreen(onCanvas, canvas);
        Point onPage = new Point(screenTopLeft);
        SwingUtilities.convertPointFromScreen(onPage, page);
        if (!canvas.isShowing() || onPage.x < 0 || onPage.x >= page.getWidth()
                || onPage.y < 0 || onPage.y >= page.getHeight()) {
            return false;  // let go over the panel: nothing happens
        }
        canvas.addPicture(picture, onCanvas);
        return true;
    }

    // picture following the mouse while a history row is dragged
    private static final class Ghost extends JComponent {
        private BufferedImage picture;
        private BufferedImage graphThumbnail;
        private BufferedImage scaledGraphGhost;
        private Rectangle outline;
        private Rectangle previewClip;

        Ghost() { setOpaque(false); }
        private Point at;

        void showOutline(Rectangle bounds) { showGraphPreview(null, bounds, null); }

        void showGraphPreview(BufferedImage image, Rectangle bounds, Rectangle clip) {
            if (graphThumbnail == image && java.util.Objects.equals(outline, bounds)
                    && java.util.Objects.equals(previewClip, clip)) return;
            Rectangle before = outline, oldClip = previewClip;
            if (image != graphThumbnail || bounds == null || before == null
                    || before.width != bounds.width || before.height != bounds.height) {
                scaledGraphGhost = scaledGhost(image, bounds);
            }
            outline = bounds == null ? null : new Rectangle(bounds);
            graphThumbnail = image;
            previewClip = clip == null ? null : new Rectangle(clip);
            repaintOutline(before, oldClip);
            repaintOutline(outline, previewClip);
        }

        void clearOutline() { showGraphPreview(null, null, null); }

        private void repaintOutline(Rectangle bounds, Rectangle clip) {
            if (bounds == null) return;
            Rectangle dirty = new Rectangle(bounds);
            dirty.grow(4, 4);
            if (clip != null) dirty = dirty.intersection(clip);
            if (!dirty.isEmpty()) repaint(dirty.x, dirty.y, dirty.width, dirty.height);
        }

        /** Rasterize the translucent ghost only when its source or dimensions change.
         * Mouse movement at a fixed zoom is then just a clipped image blit.
         */
        private static BufferedImage scaledGhost(BufferedImage source, Rectangle bounds) {
            if (source == null || bounds == null || bounds.isEmpty()) return null;
            double fit = Math.min((double) bounds.width / source.getWidth(),
                    (double) bounds.height / source.getHeight());
            int w = Math.max(1, (int)Math.round(source.getWidth() * fit));
            int h = Math.max(1, (int)Math.round(source.getHeight() * fit));
            // Guard against unbounded allocations while retaining the precise placement outline.
            double cap = Math.min(1.0, 1800.0 / Math.max(w, h));
            w = Math.max(1, (int)Math.round(w * cap));
            h = Math.max(1, (int)Math.round(h * cap));
            BufferedImage raster = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB_PRE);
            Graphics2D g = raster.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.setComposite(AlphaComposite.SrcOver.derive(.55f));
                // Preserve the previous rounded-outline clipping, but do it only once.
                g.clip(new java.awt.geom.RoundRectangle2D.Float(
                        -(bounds.width - w) / 2f, -(bounds.height - h) / 2f,
                        bounds.width, bounds.height, 12, 12));
                g.drawImage(source, 0, 0, w, h, null);
            } finally { g.dispose(); }
            return raster;
        }

        void showPicture(BufferedImage picture, Point at) {
            java.awt.Rectangle previous = dirtyBounds();
            this.picture = picture;
            this.at = at;
            repaintDirty(previous);
            repaintDirty(dirtyBounds());
        }

        void clear() {
            if (picture == null) return;
            java.awt.Rectangle previous = dirtyBounds();
            picture = null;
            at = null;
            repaintDirty(previous);
            if (getParent() != null && previous != null) getParent().repaint(
                    previous.x, previous.y, previous.width, previous.height);
        }

        private java.awt.Rectangle dirtyBounds() {
            if (picture == null || at == null) return null;
            return new java.awt.Rectangle(at.x - 3, at.y - 3,
                    Math.max(1, picture.getWidth() / 2) + 6,
                    Math.max(1, picture.getHeight() / 2) + 6);
        }

        private void repaintDirty(java.awt.Rectangle bounds) {
            if (bounds != null) repaint(bounds.x, bounds.y, bounds.width, bounds.height);
        }

        @Override
        public boolean contains(int x, int y) {
            return false;  // never takes the mouse
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (picture == null && outline == null) return;
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (picture != null) {
                    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    g2.setComposite(AlphaComposite.SrcOver.derive(0.8f));
                    g2.drawImage(picture, at.x, at.y, picture.getWidth() / 2, picture.getHeight() / 2, null);
                }
                if (outline != null) {
                    java.awt.Shape originalClip = g2.getClip();
                    if (previewClip != null) g2.clip(previewClip);
                    java.awt.Shape pdfClip = g2.getClip();
                    if (scaledGraphGhost != null) {
                        int w = scaledGraphGhost.getWidth(), h = scaledGraphGhost.getHeight();
                        g2.setComposite(AlphaComposite.SrcOver);
                        g2.drawImage(scaledGraphGhost, outline.x + (outline.width - w) / 2,
                                outline.y + (outline.height - h) / 2, null);
                        g2.setClip(pdfClip);
                        g2.setComposite(AlphaComposite.SrcOver);
                        g2.setColor(new Color(49, 119, 227, 175));
                        g2.setStroke(new java.awt.BasicStroke(1.2f, java.awt.BasicStroke.CAP_BUTT,
                                java.awt.BasicStroke.JOIN_ROUND, 10f, new float[]{5f, 5f}, 0f));
                        g2.drawRoundRect(outline.x, outline.y, outline.width, outline.height, 12, 12);
                    } else {
                        // Legacy callers without a thumbnail still get visible feedback.
                        g2.setComposite(AlphaComposite.SrcOver);
                        g2.setColor(new Color(49, 119, 227, 180));
                        g2.setStroke(new java.awt.BasicStroke(1.4f, java.awt.BasicStroke.CAP_BUTT,
                                java.awt.BasicStroke.JOIN_ROUND, 10f, new float[]{6f, 4f}, 0f));
                        g2.drawRect(outline.x, outline.y, outline.width, outline.height);
                    }
                    g2.setClip(originalClip);
                }
            } finally {
                g2.dispose();
            }
        }
    }

    // mac: the canvas runs under a clear title bar, this strip moves the window
    private static final class TitleStrip extends JComponent {
        private Point from;
        private Point windowFrom;

        TitleStrip() {
            MouseAdapter m = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    Window w = SwingUtilities.getWindowAncestor(TitleStrip.this);
                    if (!SwingUtilities.isLeftMouseButton(e) || w == null) {
                        return;
                    }
                    if (e.getClickCount() == 2 && w instanceof Frame) {
                        Frame f = (Frame) w;
                        f.setExtendedState(f.getExtendedState() ^ Frame.MAXIMIZED_BOTH);
                        from = null;
                        return;
                    }
                    from = e.getLocationOnScreen();
                    windowFrom = w.getLocation();
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    Window w = SwingUtilities.getWindowAncestor(TitleStrip.this);
                    if (from == null || w == null) {
                        return;
                    }
                    Point now = e.getLocationOnScreen();
                    w.setLocation(windowFrom.x + now.x - from.x, windowFrom.y + now.y - from.y);
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    from = null;
                }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
        }
    }

    // for tests
    Component pageComponent() {
        return page;
    }
}
