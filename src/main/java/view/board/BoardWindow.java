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
    private final javax.swing.Timer saveStateDebounce = new javax.swing.Timer(150, e -> {
        if (isPanelAnimating() || view.glass.GlassDialogs.isAnimating(this)) scheduleSaveStateRefresh();
        else refreshSaveState();
    });
    // side panels slide in / out (shown width), instead of popping; a divider drag tracks 1:1
    // 1px: the last sub-pixel creep costs full frames and nobody sees it
    private final Spring historyShown = new Spring(1f);
    private final Spring pdfShown = new Spring(1f);
    // springs, not a fixed curve: a reversal mid-way keeps its speed instead of stopping dead
    private final Motion.Clock panelClock = new Motion.Clock();
    private final javax.swing.Timer panelTimer = new FrameTimer(e -> stepPanels());
    private boolean panelsMoving;
    private int historyTarget;
    private int pdfTarget;
    private boolean snapPanels = true;
    // The live page is translated at a stable size while the panels animate. The
    // mask covers the portions outside the true viewport, including transparent History.
    private final CanvasTransitionMask transitionMask = new CanvasTransitionMask();
    // focus mode in / out: the PDF grows from its spot instead of popping
    private final FocusMorph focusMorph = new FocusMorph();
    // while a panel slides it's drawn from one picture, not repainted live every frame
    private final PanelStandIn historyStandIn = new PanelStandIn();
    private final PanelStandIn pdfStandIn = new PanelStandIn();
    private Rectangle transitionVisible;
    private Rectangle transitionDock;
    private int transitionRightLimit;
    private int transitionPan;

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
            snapPanels = true;  // dragging: glued to the pointer
            relayout();
        }, () -> {
            historyRatio = 0.24;
            snapPanels = true;
            relayout();
        });
        pdfDivider = new SplitDivider(x -> {
            pdfRatio = SplitMath.ratioFor(getWidth() - x, getWidth());
            snapPanels = true;
            relayout();
        }, () -> {
            pdfRatio = 0.56;
            snapPanels = true;
            relayout();
        });

        historyBar = bar(15f);
        historyItem = historyBar.segment("History", Icons::history, 0, () -> {
            setHistoryOpen(!historyOpen, true);
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
        historyClose.action(null, Icons::close, () -> setHistoryOpen(false, true), () -> true);
        historyClose.finish();
        pdfBar = bar(15f);
        // Default to real split view, with no second overlay mode in the UI.
        pdfBar.action("PDF", Icons::document, () -> {pdfOverlay = false; setPdfOpen(!pdfOpen, true);}, () -> true);
        pdfBar.finish();
        pdfClose = bar(15f);
        pdfClose.action(null, Icons::close, () -> setPdfOpen(false, true), () -> true);
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
        add(historyStandIn);
        add(this.history);
        add(pdfStandIn);
        add(pdf);
        add(transitionMask);
        add(page);
        transitionMask.setVisible(false);
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

    /** A view-only hint for optional background-work scheduling. */
    public boolean isPanelAnimating() { return panelsMoving; }

    public boolean isPdfOpen() {
        return pdfOpen;
    }

    // Programmatic reveals (imports, restore, etc.) retain immediate layout semantics.
    // Only a direct user toggle animates; business paths must not wait for a timer.
    public void setHistoryOpen(boolean open) { setHistoryOpen(open, false); }

    private void setHistoryOpen(boolean open, boolean animate) {
        if (!animate) snapPanels = true;
        historyOpen = open;
        if (open) {
            historyBar.select(historyItem);
        } else {
            historyBar.deselect(0);
        }
        relayout();
    }

    public void setPdfOpen(boolean open) { setPdfOpen(open, false); }

    private void setPdfOpen(boolean open, boolean animate) {
        if (!animate) snapPanels = true;
        if (open != pdfOpen) { pdfFitGeneration++; }
        if (!open) {
            focusMorph.cancel();
            pdfFullscreen = false; pdfJustOpened = false; ghost.clearOutline();
        }
        boolean firstOpen = open && !pdfOpen;
        pdfOpen = open;
        if (firstOpen) pdfJustOpened = true;
        relayout();
    }

    public boolean isPdfFullscreen() { return pdfFullscreen; }
    /** Full PDF focus mode, escaped with Esc or the View ribbon control. */
    public void setPdfFullscreen(boolean fullscreen) {
        boolean animate = fullscreen != pdfFullscreen && isShowing() && getWidth() > 0 && getHeight() > 0;
        Rectangle morphFrom = null;
        java.awt.image.BufferedImage before = null;
        if (animate) {
            // mid-flight toggle: start from what's on screen, morph included
            morphFrom = focusMorph.isVisible() ? focusMorph.liveRect() : pdfRect();
            before = snapshot();
        }
        if (fullscreen) { pdfOpen = true; ghost.clearOutline(); }
        if (pdfFullscreen != fullscreen) {
            pdfFitGeneration++;
            panelTimer.stop();
            panelClock.reset();
            panelsMoving = false;
            snapPanels = true;
        }
        pdfFullscreen = fullscreen;
        if (fullscreen) pdfJustOpened = true;
        relayout();
        if (animate) morphFocus(before, morphFrom);
    }

    private void morphFocus(java.awt.image.BufferedImage before, Rectangle from) {
        // only in the tree while it runs, on top of everything incl. the floating toolbar
        if (focusMorph.getParent() == this) setComponentZOrder(focusMorph, 0);
        else add(focusMorph, 0);
        focusMorph.setBounds(0, 0, getWidth(), getHeight());
        focusMorph.begin(before, from);
        validate();  // final layout now; its deferred PDF fit is queued before us
        long generation = pdfFitGeneration;
        SwingUtilities.invokeLater(() -> {
            if (generation != pdfFitGeneration || !focusMorph.isVisible()) return;
            validate();
            focusMorph.setCapturing(true);
            java.awt.image.BufferedImage after;
            try { after = snapshot(); }
            finally { focusMorph.setCapturing(false); }
            focusMorph.land(after, pdfRect());
        });
    }

    // where the PDF sits on screen; closed = a sliver at the right edge it comes in from
    private Rectangle pdfRect() {
        if (pdfFullscreen) return new Rectangle(0, 0, getWidth(), getHeight());
        if (!pdf.isVisible()) return new Rectangle(getWidth(), 0, 0, getHeight());
        return pdf.getBounds().intersection(new Rectangle(0, 0, getWidth(), getHeight()));
    }

    // A bounded picture for the short focus transition only. The live PDF stays native DPI.
    private java.awt.image.BufferedImage snapshot() {
        int w = getWidth();
        int h = getHeight();
        java.awt.GraphicsConfiguration gc = getGraphicsConfiguration();
        double nativeScale = gc == null ? 1 : Math.max(1, gc.getDefaultTransform().getScaleX());
        double scale = FocusMorph.snapshotScale(w, h, nativeScale);
        int iw = (int) Math.ceil(w * scale);
        int ih = (int) Math.ceil(h * scale);
        java.awt.image.BufferedImage img = gc == null
                ? new java.awt.image.BufferedImage(iw, ih, java.awt.image.BufferedImage.TYPE_INT_RGB)
                : gc.createCompatibleImage(iw, ih, java.awt.Transparency.OPAQUE);
        Graphics2D g = img.createGraphics();
        try {
            g.scale(iw / (double) w, ih / (double) h);
            g.setColor(BoardTheme.CANVAS);
            g.fillRect(0, 0, w, h);
            paint(g);
        } finally {
            g.dispose();
        }
        return img;
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
        return transitionVisible == null ? page.getBounds() : new Rectangle(transitionVisible);
    }

    /** Minimum usable dock extent during a transition; positioning still follows the viewport. */
    Rectangle toolbarFitBounds() {
        return transitionDock == null ? pageBounds() : new Rectangle(transitionDock);
    }

    // the canvas side, so the toolbar can dock to it and not sit over the PDF
    java.awt.Component canvasPage() {
        return page;
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
        // a resize mid-morph would stretch the pictures: just show the live layout
        if (focusMorph.isVisible() && (focusMorph.getWidth() != w || focusMorph.getHeight() != h)) focusMorph.cancel();
        if (pdfFullscreen) {
            layoutPdfFullscreen(w, h);
            return;
        }
        layoutPanelsAndCanvas(w, h);
        layoutChrome(w);
        ghost.setBounds(0, 0, w, h);
        if (!panelsMoving) finishPdfOpen();
    }

    /** Full-screen PDF: only the PDF panel and its exit button. */
    private void layoutPdfFullscreen(int w, int h) {
        clearPanelTransition();
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
        historyStandIn.drop();
        pdfStandIn.drop();
        finishPdfOpen();
    }

    /** History on the left, PDF on the right (sliding while they open or close), the canvas between. */
    private void layoutPanelsAndCanvas(int w, int h) {
        int half = SplitDivider.HIT / 2;
        page.setVisible(true);
        fullscreenExit.setVisible(false);
        historyBar.setVisible(true);pdfBar.setVisible(true);
        if (floatingToolbar != null) floatingToolbar.setVisible(true);
        if (titleStrip != null) titleStrip.setVisible(true);
        // In split mode the Canvas really owns a smaller viewport: panels never overlap it.
        // Ratio constraints are based on the remaining width after History.
        int fullHw = Math.min(SplitMath.panelWidth(historyRatio, w), Math.max(0, w - 340));
        int hTarget = historyOpen ? fullHw : 0;
        int remaining = Math.max(0, w - hTarget);
        int fullPw = Math.max(0, Math.min(SplitMath.panelWidth(pdfRatio, w),
                Math.max(0, remaining - (pdfOverlay ? 0 : 250))));
        int pTarget = pdfOpen ? fullPw : 0;
        if (w != panelLayoutWidth || h != panelLayoutHeight) {
            snapPanels = true;  // window resize: panels keep up 1:1, only open / close animates
            panelLayoutWidth = w;
            panelLayoutHeight = h;
        }
        settlePanels(hTarget, pTarget);
        int hw = Math.round(historyShown.x);
        int pw = Math.round(pdfShown.x);
        boolean historyOut = hw > 0;
        boolean pdfOut = pw > 0;
        history.setVisible(historyOut);
        historyDivider.setVisible(historyOpen && !panelsMoving);
        pdf.setVisible(pdfOut);
        pdfDivider.setVisible(pdfOpen && !panelsMoving);
        if (historyOut) {
            // slides in at its full width from the left edge, nothing squashes
            int full = Math.max(hw, historyOpen ? fullHw : history.getWidth());
            history.setBounds(hw - full, 0, full, h);
            historyDivider.setBounds(hw - half, 0, SplitDivider.HIT, h);
        }
        if (pdfOut) {
            int full = Math.max(pw, pdfOpen ? fullPw : pdf.getWidth());
            pdf.setBounds(w - pw, 0, full, h);
            pdfDivider.setBounds(w - pw - half, 0, SplitDivider.HIT, h);
        }
        standIn(historyStandIn, history, historyOut);
        standIn(pdfStandIn, pdf, pdfOut);
        int canvasWidth = Math.max(0, w - hw - (pdfOut && !pdfOverlay ? pw : 0));
        Rectangle visibleCanvas = new Rectangle(hw, 0, canvasWidth, h);
        Rectangle targetCanvas = new Rectangle(hTarget, 0,
                Math.max(0, w - hTarget - (pdfOverlay ? 0 : pTarget)), h);
        Rectangle previousDock = pageBounds();
        boolean wasTransitioning = transitionVisible != null;
        int rightEdge = visibleCanvas.x + visibleCanvas.width;
        if (panelsMoving) {
            int targetRight = targetCanvas.x + targetCanvas.width;
            if (transitionVisible == null) {
                transitionRightLimit = Math.max(rightEdge, targetRight);
                transitionPan = 0;
            } else {
                transitionRightLimit = Math.max(transitionRightLimit, targetRight);
            }
            // Choose a right anchor that covers both endpoints. A closing PDF can
            // commit its intended pan at the start; an opening PDF commits it at the end.
            // Moving the page compensates exactly for this pan in screen coordinates.
            int desiredPan = lastCanvasRight < 0 ? 0 : transitionRightLimit - lastCanvasRight;
            transitionVisible = new Rectangle(visibleCanvas);
            int narrowWidth = Math.min(visibleCanvas.width, targetCanvas.width);
            // Keep the smallest fitting extent so the toolbar does not remeasure labels every frame.
            if (transitionDock != null) narrowWidth = Math.min(narrowWidth, transitionDock.width);
            transitionDock = new Rectangle(0, 0, Math.max(0, narrowWidth), h);
            Rectangle stablePage = new Rectangle(hw + rightEdge - transitionRightLimit, 0,
                    transitionRightLimit, h);
            setCanvasBounds(stablePage);
            int delta = desiredPan - transitionPan;
            transitionPan = desiredPan;
            if (delta != 0) canvas.panBy(delta, 0);
            transitionMask.showViewport(visibleCanvas, w, h);
        } else {
            setCanvasBounds(visibleCanvas);
            int delta = lastCanvasRight >= 0 && lastBoardWidth == w ? rightEdge - lastCanvasRight : 0;
            delta -= transitionPan;
            if (delta != 0) canvas.panBy(delta, 0);
            resetPanelTransition();
            lastCanvasRight = rightEdge;
            lastBoardWidth = w;
        }
        Rectangle currentDock = pageBounds();
        if (wasTransitioning != panelsMoving) firePropertyChange("canvasDockBounds", null, currentDock);
        else if (!previousDock.equals(currentDock)) firePropertyChange("canvasDockBounds", previousDock, currentDock);

    }

    /** The floating capsules along the top: save state, History, PDF and their close buttons. */
    private void layoutChrome(int w) {
        int m = GlassBar.MARGIN;
        Dimension hb = historyBar.getPreferredSize();
        // name + save state always shown: a narrow window folds it to a round icon with a badge
        boolean showStatus = !pdfFullscreen;
        saveStatusChip.setVisible(showStatus);
        int historyStart = BoardTheme.HISTORY_LEFT;
        if (showStatus) {
            saveStatusChip.setCompact(w < 1050);
            Dimension state = saveStatusChip.getPreferredSize();
            // mac: just right of the traffic lights; windows has none, so from the edge
            int chipX = BoardTheme.MAC ? BoardTheme.HISTORY_LEFT - 35 : BoardTheme.SIDE;
            // centred on the History / Save / Open capsule next to it
            int chipY = BoardTheme.TOP + (GlassBar.CAP_H - CanvasStatusChip.HEIGHT) / 2;
            saveStatusChip.setBounds(chipX, chipY, state.width, state.height);
            historyStart = chipX + state.width + 10;
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
    }

    private void standIn(PanelStandIn standIn, JComponent panel, boolean out) {
        if (panelsMoving && out) {
            if (!standIn.holds()) {
                standIn.take(panel);
                panelClock.reset();  // the picture took a while: don't count it as motion, no jump
            }
            standIn.follow(panel);
        } else {
            standIn.drop();
        }
    }

    // panels head for their new widths; jump there when not on screen or when a divider drags
    private void settlePanels(int hTarget, int pTarget) {
        historyTarget = hTarget;
        pdfTarget = pTarget;
        boolean snap = snapPanels || !isShowing() || Motion.reduced();
        snapPanels = false;
        if (snap) {
            historyShown.snap(hTarget);
            pdfShown.snap(pTarget);
            panelsMoving = false;
            panelTimer.stop();
            panelClock.reset();
            return;
        }
        boolean moving = historyShown.x != hTarget || pdfShown.x != pTarget
                || historyShown.v != 0 || pdfShown.v != 0;
        if (moving) {
            // new target: the springs just re-aim, speed carries over
            if (!panelTimer.isRunning()) {
                panelClock.reset();
                panelTimer.start();
            }
            panelsMoving = true;
        } else if (panelsMoving) {
            panelTimer.stop();
            panelClock.reset();
            panelsMoving = false;
        }
    }

    private void setCanvasBounds(Rectangle bounds) {
        if (page.getBounds().equals(bounds)) return;
        boolean resize = page.getWidth() != bounds.width || page.getHeight() != bounds.height;
        page.setBounds(bounds);
        if (resize) page.doLayout();
    }

    private void resetPanelTransition() {
        transitionVisible = null;
        transitionDock = null;
        transitionPan = 0;
        transitionRightLimit = 0;
        transitionMask.setVisible(false);
    }

    private void clearPanelTransition() {
        if (transitionVisible == null) return;
        // Fullscreen/removal interrupts the view transition. Undo only its temporary
        // anchor adjustment; the next normal layout applies the final split pan.
        if (transitionPan != 0) canvas.panBy(-transitionPan, 0);
        resetPanelTransition();
    }

    private void stepPanels() {
        if (!isShowing()) {
            panelTimer.stop();
            panelClock.reset();
            panelsMoving = false;
            snapPanels = true;
            return;
        }
        stepPanels(panelClock.tick());
    }

    // dt split out so smoke tests can drive frames
    void stepPanels(float dt) {
        boolean still = historyShown.step(historyTarget, Motion.PANEL, dt);
        still &= pdfShown.step(pdfTarget, Motion.PANEL, dt);
        if (still) {
            panelTimer.stop();
            panelClock.reset();
            panelsMoving = false;
        }
        Rectangle beforeHistory = history.isVisible() ? history.getBounds() : new Rectangle();
        Rectangle beforePdf = pdf.isVisible() ? pdf.getBounds() : new Rectangle();
        Rectangle beforeCanvas = pageBounds();
        doLayout();
        Rectangle canvasDirty = beforeCanvas.union(pageBounds());
        if (!canvasDirty.isEmpty()) repaint(canvasDirty);
        repaintPanelArea(beforeHistory, history);
        repaintPanelArea(beforePdf, pdf);
    }

    private void repaintPanelArea(Rectangle before, JComponent panel) {
        Rectangle dirty = panel.isVisible() ? (before.isEmpty() ? panel.getBounds() : before.union(panel.getBounds())) : before;
        Rectangle visible = dirty.intersection(new Rectangle(0, 0, getWidth(), getHeight()));
        if (!visible.isEmpty()) repaint(visible);
    }

    // Fit only once, after the final layout; a restored document consumes this call
    // without replacing its persisted zoom. Closing before the callback cancels it.
    private void finishPdfOpen() {
        if (!pdfJustOpened || !pdfOpen || !pdf.isVisible()) return;
        pdfJustOpened = false;
        long generation = pdfFitGeneration;
        if (pdf instanceof PdfWorkspacePort port) SwingUtilities.invokeLater(() -> {
            if (generation != pdfFitGeneration || !pdfOpen || !pdf.isVisible()) return;
            if (panelsMoving) { pdfJustOpened = true; return; }
            port.fitWhenOpened();
        });
    }

    @Override public void removeNotify() {
        focusMorph.cancel();
        panelTimer.stop();
        panelClock.reset();
        clearPanelTransition();
        saveStateDebounce.stop();
        panelsMoving = false;
        snapPanels = true;
        super.removeNotify();
    }

    private long pdfFitGeneration;
    private boolean pdfJustOpened;
    private int panelLayoutWidth = -1, panelLayoutHeight = -1;

    // what the top capsules see: panels as flat colour, the canvas content where it is
    private void paintBackdrop(Graphics2D g) {
        if (page.isVisible() && canvas.getWidth() > 0) {
            Point c = SwingUtilities.convertPoint(canvas, 0, 0, this);
            Graphics2D cg = (Graphics2D) g.create();
            cg.clip(pageBounds());
            cg.clipRect(c.x, c.y, canvas.getWidth(), canvas.getHeight());
            cg.translate(c.x, c.y);
            if (cg.getClipBounds() == null || !cg.getClipBounds().isEmpty()) canvas.paintContent(cg);
            cg.dispose();
        }
        if (history.isVisible()) {
            g.setColor(BoardTheme.PANEL);
            g.fill(history.getBounds());
        }
        if (pdf.isVisible()) {
            g.setColor(Color.WHITE);
            g.fill(pdf.getBounds());
        }
    }

    private long backdropVersion() {
        long v = canvas.contentVersion();
        v = v * 31 + (historyOpen ? 1 : 0) + (pdfOpen ? 2 : 0);
        return ((v * 31 + page.getX() * 131L + page.getWidth()) * 31 + history.getX()) * 31 + pdf.getX();
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
        if ((history.isVisible() && history.getBounds().contains(onBoard))
                || (pdf.isVisible() && pdf.getBounds().contains(onBoard))) return false;
        if (!pageBounds().contains(onBoard)) return false;
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
        Point onBoard = new Point(screenTopLeft);
        SwingUtilities.convertPointFromScreen(onBoard, this);
        if (!pageBounds().contains(onBoard)) return false;
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



    // for tests
    Component pageComponent() {
        return page;
    }
}
