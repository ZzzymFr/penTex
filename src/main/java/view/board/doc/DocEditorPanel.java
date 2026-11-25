package view.board.doc;

import view.glass.GlassDialogs;
import view.glass.GlassButton;

import history.adapter.HistoryMaterialMapper;
import pdf_editor.adapter.HistoryMaterialImporter;
import interface_adapter.history.HistoryItem;
import pdf_editor.MaterialStore;
import pdf_editor.adapter.PdfEditorController;
import pdf_editor.adapter.FormulaRevisionCoordinator;
import pdf_editor.domain.PdfDocument;
import pdf_editor.domain.PdfMaterial;
import pdf_editor.view.FormulaEditDialog;
import pdf_editor.view.PdfPageCanvas;
import view.board.BoardTheme;
import view.board.GlassContextMenu;
import view.board.Icons;
import view.board.PdfWorkspacePort;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Word-style presentation adapter for the real PDF document use cases.
 * This replaces the *placeholder* page from the one-board prototype, not its visual concept.
 * All mutations and export flow through PdfEditorController; no persistence is done here.
 */
public final class DocEditorPanel extends JPanel implements PdfWorkspacePort {
    private static final Color DIVIDER = new Color(0, 0, 0, 24);
    private static final Color MUTED = new Color(110, 112, 122);
    static final Color DESK_COLOR = new Color(0xE9E9EF);
    private static final Color DESK = DESK_COLOR;
    private static final int MAX_ZOOM = 300;
    private static final int MIN_ZOOM = 35;

    private final PdfEditorController controller;
    private FormulaRevisionCoordinator formulaRevision;
    public void setGraphSessions(Supplier<view.canvas.GraphSession> sessions) {
        paper.setGraphSessions(sessions);
    }
    public void setFormulaEditing(FormulaRevisionCoordinator actions) {
        formulaRevision = Objects.requireNonNull(actions);
    }
    private final MaterialStore library;
    private final PdfPageCanvas paper;
    private final JScrollPane pageScroll;
    private final JPanel ribbonHolder = new JPanel(new BorderLayout());
    private final JLabel pageCount = new JLabel();
    private final JLabel zoomValue = new JLabel("100%");
    private final JLabel title = new JLabel("Untitled document");
    private final JSlider zoomSlider = new JSlider(MIN_ZOOM, MAX_ZOOM, 100);
    private final JPanel workspace = new JPanel(new BorderLayout());
    private final java.util.List<Tab> tabs = new java.util.ArrayList<>();
    private Supplier<BufferedImage> canvasCapture = () -> null;
    private Runnable regionCapture = this::insertCanvasInk;
    private RibbonButton captureButton;
    private boolean capturing;
    private boolean updatingZoom;
    private boolean restoredViewPending;
    private Runnable toggleFullscreen = () -> { };
    public void setFullscreenAction(Runnable callback) { toggleFullscreen = Objects.requireNonNull(callback); }


    public DocEditorPanel(PdfEditorController controller) {
        super(new BorderLayout());
        this.controller = Objects.requireNonNull(controller);
        this.library = controller.materials();
        setBackground(DESK);
        paper = new PdfPageCanvas(controller, new PdfPageCanvas.Listener() {
            @Override public void contextMenu(MouseEvent e) { contextMenu(e); }
            @Override public void editNote() { DocEditorPanel.this.editNote(); }
            @Override public void editFormula() { DocEditorPanel.this.editFormula(); }
            @Override public void typeInDocument() { DocEditorPanel.this.typeOnPage(); }
            @Override public void typeInDocumentAt(float x,float y) {
                controller.clearSelection();
                paper.editBodyInlineAt(x,y);
            }
            @Override public void bodyChanged() { updateStatus(); }
            @Override public void bodyOverflow() {
                // Already on the EDT. Do not queue a second page jump that could
                // run after the user has manually navigated back to an earlier page.
                int next = controller.currentPage() + 1;
                if (next < controller.pageCount()) {
                    changePage(next);
                    paper.editBodyInline();
                }
            }
        });
        paper.setBackground(DESK);
        pageScroll = new JScrollPane(new CenteredDesk(paper));
        pageScroll.setBorder(null);
        pageScroll.getViewport().setBackground(DESK);
        pageScroll.getVerticalScrollBar().setUnitIncrement(22);
        pageScroll.getHorizontalScrollBar().setUnitIncrement(22);
        pageScroll.addMouseWheelListener(e -> {
            if (e.isControlDown()) { zoom(paper.adjustZoom(e.getPreciseWheelRotation())); e.consume(); }
        });
        workspace.setBackground(DESK);
        workspace.add(pageScroll, BorderLayout.CENTER);
        installDeleteShortcut();
        // A decorative ruler misrepresented measurements and did not scroll with the page.
        // Keep the desk clean until a real, scroll-synchronised editing ruler is added.
        add(workspace, BorderLayout.CENTER);
        add(buildHeader(), BorderLayout.NORTH);
        add(buildStatus(), BorderLayout.SOUTH);
        updateStatus();
        tabs.get(0).select();
        addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                ribbonHolder.revalidate();
            }
        });
    }

    /** Canvas ink is captured only on demand, never from an outdated opening screenshot. */
    public void setCanvasCapture(Supplier<BufferedImage> capture) {
        canvasCapture = Objects.requireNonNull(capture);
    }
    /** Request a temporary screenshot selection on the visible Canvas. */
    public void setRegionCapture(Runnable command) { regionCapture = Objects.requireNonNull(command); }
    public void setCaptureAreaActive(boolean active) {
        capturing = active;
        if (captureButton != null) captureButton.setActive(active);
    }
    public boolean isCaptureAreaActive() { return capturing; }
    /** Called only by the actual app composition root, not by headless editor unit tests. */
    public void enablePersistence(pdf_editor.use_case.PdfWorkspace documents,
                                  pdf_editor.use_case.PdfPreviewGateway previewer) {
        if (persistence != null) return;
        persistence = new pdf_editor.view.PdfWorkspaceDialogs(this, controller, paper, documents, previewer,
                saved -> { zoom(saved.zoom()); restoredViewPending = !isShowing(); updateStatus(); }, title::setText);
        for (Component action : persistenceActions.getComponents()) action.setEnabled(true);
        persistence.start();
        revalidate(); repaint();
        int shortcut = GraphicsEnvironment.isHeadless() ? KeyEvent.CTRL_DOWN_MASK
                : java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_S, shortcut), "savePdfDocument");
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_S, shortcut | KeyEvent.SHIFT_DOWN_MASK), "savePdfDocumentAs");
        getActionMap().put("savePdfDocument", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { persistence.save(); }
        });
        getActionMap().put("savePdfDocumentAs", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { persistence.saveAs(); }
        });
    }
    private final JPanel persistenceActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
    private pdf_editor.view.PdfWorkspaceDialogs persistence;
    private void buildPersistenceActions() {
        persistenceActions.setOpaque(false);
        persistenceActions.setVisible(true);
        GlassButton save = new GlassButton("Save");
        save.setName("pdfSaveButton"); save.setEnabled(false);
        save.setToolTipText("Save editable PDF document");
        save.addActionListener(e -> {
            if (persistence == null) return;
            GlassContextMenu menu = new GlassContextMenu();
            menu.addAction("Save", persistence::save);
            menu.addAction("Save As...", persistence::saveAs);
            menu.show(save, 0, save.getHeight());
        });
        GlassButton open = new GlassButton("Open");
        open.setName("pdfOpenButton"); open.setEnabled(false);
        open.setToolTipText("Open a saved editable PDF document");
        open.addActionListener(e -> { if (persistence != null) persistence.open(); });
        persistenceActions.add(save); persistenceActions.add(open);
    }
    public void close() {
        if (persistence != null) persistence.close();
    }
    public PdfPageCanvas pageView() { return paper; }
    public PdfEditorController documentController() { return controller; }
    /** @deprecated The standalone Library has been removed. */
    @Deprecated public boolean isLibraryVisible() { return false; }

    /** Floating Apple-like controls: rounded capsules rather than full-width white strips. */
    private static final class Capsule extends JPanel {
        Capsule(java.awt.LayoutManager layout) {
            super(layout);
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(0,0,0,12));
            g2.fillRoundRect(1, 3, getWidth()-2, getHeight()-3, 28, 28);
            g2.setColor(new Color(255,255,255,252));
            g2.fillRoundRect(0, 0, getWidth()-1, getHeight()-3, 28, 28);
            g2.setColor(new Color(210,216,227));
            g2.drawRoundRect(0, 0, getWidth()-1, getHeight()-3, 28, 28);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private JComponent buildHeader() {
        JPanel top = new JPanel(new BorderLayout());
        top.setBackground(DESK);
        JPanel nameRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 9));
        nameRow.setOpaque(false);
        nameRow.setBorder(BorderFactory.createEmptyBorder(BoardTheme.TOP + 2, 10, 1, 0));
        Capsule document = new Capsule(new FlowLayout(FlowLayout.LEFT, 8, 2));
        title.setFont(BoardTheme.font(14f));
        document.add(DocumentGlyphs.component(23, Icons::document));
        document.add(title);
        nameRow.add(document);
        top.add(nameRow, BorderLayout.NORTH);

        JPanel tabsRow = new JPanel(new WrapLayout(FlowLayout.CENTER, 7, 3));
        tabsRow.setOpaque(false);
        JPanel names = new Capsule(new WrapLayout(FlowLayout.CENTER, 2, 1));
        tabs.add(new Tab("Home", homeRibbon()));
        tabs.add(new Tab("Insert", insertRibbon()));
        tabs.add(new Tab("Layout", layoutRibbon()));
        tabs.add(new Tab("View", viewRibbon()));
        for (Tab tab : tabs) names.add(tab.button);
        tabsRow.add(names);
        JButton export = new RoundedActionButton("Export PDF", this::export, true);
        export.setToolTipText("Save your document as a PDF file");
        tabsRow.add(export);
        // Save/Open occupy the former Library position.
        buildPersistenceActions();
        tabsRow.add(persistenceActions, 0);
        top.add(tabsRow, BorderLayout.CENTER);
        ribbonHolder.setOpaque(false);
        ribbonHolder.setBorder(BorderFactory.createEmptyBorder(3, 9, 5, 9));
        top.add(ribbonHolder, BorderLayout.SOUTH);
        return top;
    }

    private JPanel ribbon() {
        JPanel row = new JPanel(new WrapLayout(FlowLayout.LEFT, 4, 4));
        row.setOpaque(false);
        row.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        return row;
    }
    private RibbonButton command(JPanel row, RibbonButton b, Runnable action) {
        row.add(b.onClick(action));
        return b;
    }
    private JPanel homeRibbon() {
        JPanel row = ribbon();
        command(row, RibbonButton.labeled(DocIcons::comment, "Write"), this::typeOnPage);
        command(row, RibbonButton.icon(Icons::undo, "Undo"), this::undo);
        command(row, RibbonButton.icon(DocIcons::redo, "Redo"), this::redo);
        command(row, RibbonButton.labeled(DocIcons::deletePage, "Delete item"), this::deleteSelectedItem);
        command(row, RibbonButton.letter("B", Font.BOLD, false, false, "Bold selected note"), () -> setNoteStyle("bold"));
        command(row, RibbonButton.letter("I", Font.ITALIC, false, false, "Italic selected note"), () -> setNoteStyle("italic"));
        command(row, RibbonButton.letter("U", Font.PLAIN, true, false, "Underline selected note"), () -> setNoteStyle("underline"));
        JComboBox<Integer> sizes = new JComboBox<>(new Integer[] {8, 10, 12, 14, 16, 18, 20, 24, 28, 32, 36, 48, 60, 72});
        sizes.setSelectedItem(16);
        sizes.setToolTipText("Font size — applies to highlighted text or the selected text box");
        sizes.setFont(BoardTheme.font(12f));
        sizes.setFocusable(false);
        sizes.addActionListener(e -> {
            Integer size = (Integer) sizes.getSelectedItem();
            if (size != null) applyNoteFontSize(size);
        });
        row.add(sizes);
        JComboBox<String> families = new JComboBox<>(new String[]{"Times New Roman", "Arial", "Calibri", "Georgia", "Courier New", "Segoe UI"});
        families.setToolTipText("Font family for document text");
        families.setFont(BoardTheme.font(12f));
        families.setFocusable(false);
        families.addActionListener(e -> {
            String chosen = (String) families.getSelectedItem();
            if (chosen == null) return;
            if (paper.isEditingNote()) paper.setInlineFontFamily(chosen);
            else controller.changeSelectedNoteFont(chosen);
        });
        row.add(families);
        command(row, RibbonButton.labeled(DocIcons::formula, "Edit math"), this::editFormula);
        command(row, RibbonButton.labeled(DocIcons::comment, "Edit note"), this::editNote);
        return row;
    }
    private JPanel insertRibbon() {
        JPanel row = ribbon();
        command(row, RibbonButton.labeled(DocIcons::formula, "Formula"), this::insertFormula);
        command(row, RibbonButton.labeled(DocIcons::comment, "Text box"), this::addNote);
        captureButton = command(row, RibbonButton.labeled(DocIcons::image, "Capture area"), () -> regionCapture.run());
        captureButton.setActive(capturing);
        command(row, RibbonButton.labeled(DocIcons::pageBreak, "New page"), this::addPage);
        return row;
    }
    private JPanel layoutRibbon() {
        JPanel row = ribbon();
        command(row, RibbonButton.labeled(DocIcons::pageBreak, "Add page"), this::addPage);
        command(row, RibbonButton.labeled(DocIcons::deletePage, "Delete page"), this::deletePage);
        command(row, RibbonButton.labeled(DocIcons.align(0), "Auto layout"), this::autoLayout);
        command(row, RibbonButton.labeled(DocIcons::movePage, "Move page"), this::moveToPageMenu);
        command(row, RibbonButton.letter("↑", Font.PLAIN, false, false, "Bring selected object forward one layer"), () -> arrange(1));
        command(row, RibbonButton.letter("↓", Font.PLAIN, false, false, "Send selected object backward one layer"), () -> arrange(-1));
        command(row, RibbonButton.labeled(DocIcons::layers, "Arrange…"), this::arrangeMenu);
        return row;
    }
    private JPanel viewRibbon() {
        JPanel row = ribbon();
        command(row, RibbonButton.labeled(Icons::preview, "Preview"), this::preview);
        command(row, RibbonButton.labeled(Icons::fullScreen, "Full screen"), () -> toggleFullscreen.run());
        command(row, RibbonButton.labeled(DocIcons::fitPage, "Fit page"), this::fitPage);
        command(row, RibbonButton.labeled(DocIcons::layers, "Image Layout…"), this::showImageLayoutMenu);
        command(row, RibbonButton.labeled(Icons::pointer, "Select all"), this::selectAllPdfObjects);
        command(row, RibbonButton.letter("−", Font.PLAIN, false, false, "Zoom out"), () -> zoom(paper.zoom() / 1.15));
        command(row, RibbonButton.letter("+", Font.PLAIN, false, false, "Zoom in"), () -> zoom(paper.zoom() * 1.15));
        return row;
    }
    private JComponent buildStatus() {
        JPanel bar = new JPanel(new BorderLayout(8, 0));
        bar.setBackground(DESK);
        bar.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0),
                BorderFactory.createEmptyBorder(4, 10, 4, 10)));
        pageCount.setFont(BoardTheme.font(12f)); pageCount.setForeground(MUTED);
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0)); left.setOpaque(false);
        left.add(flatButton("‹", () -> changePage(controller.currentPage() - 1)));
        left.add(pageCount);
        left.add(flatButton("›", () -> changePage(controller.currentPage() + 1)));
        bar.add(left, BorderLayout.WEST);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));right.setOpaque(false);
        right.add(flatButton("−", () -> zoom(paper.zoom() / 1.15)));
        zoomSlider.setPreferredSize(new Dimension(85, 24));
        zoomSlider.setOpaque(false);
        zoomSlider.addChangeListener(e -> { if (!updatingZoom) zoom(zoomSlider.getValue() / 100.0); });
        right.add(zoomSlider);
        zoomValue.setForeground(MUTED);zoomValue.setFont(BoardTheme.font(12f));right.add(zoomValue);
        right.add(flatButton("+", () -> zoom(paper.zoom() * 1.15)));
        bar.add(right, BorderLayout.EAST);
        return bar;
    }
    private JButton flatButton(String text, Runnable task) {
        return new RoundedActionButton(text, task, false);
    }
    /** Readable rounded buttons with a real opaque fill; unlike platform LAF translucent defaults. */
    private static final class RoundedActionButton extends JButton {
        private final boolean primary;
        private boolean over;
        RoundedActionButton(String text, Runnable action, boolean primary) {
            super(text);this.primary = primary;
            setFont(BoardTheme.font(12f));
            setFocusable(false);setContentAreaFilled(false);setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(8, 13, 8, 13));
            setForeground(primary ? Color.WHITE : new Color(42, 57, 79));
            addActionListener(e -> action.run());
            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) {over = true;repaint();}
                @Override public void mouseExited(MouseEvent e) {over = false;repaint();}
            });
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            Color top = primary ? (over ? new Color(41, 143, 255) : new Color(0, 122, 255))
                    : (getModel().isSelected() ? new Color(222, 235, 254)
                    : over ? new Color(228, 237, 249, 252) : new Color(247, 249, 253, 242));
            Color end = primary ? new Color(0, 93, 216) : new Color(239, 243, 249);
            g2.setPaint(new GradientPaint(0, 0, top, 0, h, end));
            g2.fillRoundRect(0, 0, w - 1, h - 1, 17, 17);
            g2.setColor(primary ? new Color(255, 255, 255, 95) : new Color(189, 198, 213, 145));
            g2.drawRoundRect(0, 0, w - 1, h - 1, 17, 17);
            g2.dispose();super.paintComponent(g);
        }
    }
    private void updateStatus() {
        pageCount.setText("Page " + (controller.currentPage() + 1) + " of " + controller.pageCount());
        paper.refreshInteractiveGraphs();
        paper.repaint();
    }
    private void changePage(int target) {
        paper.finishInlineEdit();
        controller.switchPage(target);
        paper.clearPageSelection();
        updateStatus();
    }
    private void zoom(double z) {
        double actual = paper.setZoom(z);
        updatingZoom = true;
        try {zoomSlider.setValue((int) Math.round(100 * actual));zoomValue.setText((int) Math.round(100 * actual) + "%");}
        finally {updatingZoom = false;}
        // CenteredDesk computes its preferred size from the zoomed page; invalidate
        // the viewport view too, otherwise its GridBag positions the new page at the
        // old (100%) dimensions and it looks left aligned at Fit Page.
        JComponent desk = (JComponent) pageScroll.getViewport().getView();
        desk.revalidate();
        Dimension desired = desk.getPreferredSize();
        desk.setSize(Math.max(desired.width, pageScroll.getViewport().getWidth()),
                Math.max(desired.height, pageScroll.getViewport().getHeight()));
        desk.doLayout();
        pageScroll.getViewport().revalidate();
        pageScroll.revalidate();
        workspace.repaint();
    }
    @Override public void fitWhenOpened() {
        if (restoredViewPending) { restoredViewPending = false; return; }
        fitPage();
    }
    private void fitPage() {
        int w = pageScroll.getViewport().getWidth();
        int h = pageScroll.getViewport().getHeight();
        if (w > 0 && h > 0) zoom(paper.fitZoom(w, h));
    }
    @Override public Rectangle interactiveGraphPreviewBounds(Point cursorOnScreen, double aspectRatio) {
        if (!paper.isShowing()) return null;
        Point onPage = new Point(cursorOnScreen);
        SwingUtilities.convertPointFromScreen(onPage, paper);
        Rectangle pageBounds = paper.interactiveGraphPreviewBounds(onPage, aspectRatio);
        Point at = pageBounds.getLocation();
        SwingUtilities.convertPointToScreen(at, paper);
        return new Rectangle(at.x, at.y, pageBounds.width, pageBounds.height);
    }

    /** A cross-divider Graph copy publishes one reusable History thumbnail and one live PDF placement. */
    @Override public boolean dropInteractiveGraph(entity.graph.GraphDefinition definition,
                                                   BufferedImage thumbnail, Point cursorOnScreen) {
        Objects.requireNonNull(definition);
        Objects.requireNonNull(thumbnail);
        Point spot = new Point(cursorOnScreen);
        if (paper.isShowing()) SwingUtilities.convertPointFromScreen(spot, paper);
        MaterialStore.Material material = library.addInteractiveGraph(definition, thumbnail);
        addMaterial(material, paper.isShowing() ? Math.max(0, paper.pageX(spot.x)) : 75,
                paper.isShowing() ? Math.max(0, paper.pageY(spot.y)) : 108);
        library.archive(material);
        return true;
    }

    private void addMaterial(PdfMaterial material, float x, float y) {
        // Imported History must be clickable immediately: an active JTextPane
        // otherwise remains above the page and intercepts mouse input.
        paper.finishInlineEdit();
        pdf_editor.view.GraphPlacementSizing.Size graphSize = material.isGraph()
                ? pdf_editor.view.GraphPlacementSizing.forImage(
                        Math.max(1, material.imageWidth()), Math.max(1, material.imageHeight()))
                : null;
        float w = graphSize != null ? graphSize.width() : 320;
        float h = graphSize != null ? graphSize.height() : 90;
        controller.place(material, x, y, w, h);
        updateStatus();
    }
    @Override public void addHistoryItem(HistoryItem item) {
        Objects.requireNonNull(item);
        addMaterial(HistoryMaterialImporter.importRecord(item, library), 75, 108);
    }
    @Override public Rectangle historyPreviewBounds(HistoryItem item, Point cursorOnScreen) {
        if (!paper.isShowing()) return null;
        Point onPage = new Point(cursorOnScreen);
        SwingUtilities.convertPointFromScreen(onPage, paper);
        var size = item.hasImage() ? pdf_editor.view.GraphPlacementSizing.forImage(
                item.entry().imageWidth(), item.entry().imageHeight()) : null;
        Rectangle bounds = paper.materialPreviewBounds(onPage, size == null ? 320f : size.width(),
                size == null ? 90f : size.height());
        Point top = bounds.getLocation(); SwingUtilities.convertPointToScreen(top, paper);
        return new Rectangle(top.x, top.y, bounds.width, bounds.height);
    }
    /** Copy the original archived record, without creating a second History record. */
    @Override public boolean dropHistoryItem(HistoryItem item, Point cursorScreen) {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(cursorScreen, "cursorScreen");
        Point spot = new Point(cursorScreen);
        if (paper.isShowing()) SwingUtilities.convertPointFromScreen(spot, paper);
        float x = paper.isShowing() ? Math.max(0, paper.pageX(spot.x)) : 75;
        float y = paper.isShowing() ? Math.max(0, paper.pageY(spot.y)) : 108;
        // Use the structured original, never the scaled drag thumbnail.
        MaterialStore.Material material = HistoryMaterialImporter.importRecord(item, library);
        addMaterial(material, x, y);
        return true;
    }
    private void addPage() {paper.finishInlineEdit();controller.addPage();updateStatus();}
    private void deletePage() {
        paper.finishInlineEdit();
        if (!controller.deletePage()) GlassDialogs.warning(this, "The document must contain one page.");
        updateStatus();
    }
    /** Delete the selected PDF placement, never the source Canvas or History item. */
    private void deleteSelectedItem() {
        if (paper.isEditingNote()) return; // Editing text must retain normal Delete/Backspace.
        if (controller.deleteSelected()) updateStatus();
    }

    /** Shortcuts are scoped to the PDF workspace, not the whole Math Lab window. */
    private void installDeleteShortcut() {
        InputMap keys = workspace.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        ActionMap actions = workspace.getActionMap();
        String action = "deletePdfSelection";
        keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), action);
        keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), action);
        int shortcut = GraphicsEnvironment.isHeadless() ? KeyEvent.CTRL_DOWN_MASK
                : java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_A, shortcut), "selectAllPdfObjects");
        actions.put("selectAllPdfObjects", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) {
                if (!paper.isEditingNote()) selectAllPdfObjects();
            }
        });
        keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "clearPdfSelection");
        actions.put("clearPdfSelection", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) { paper.clearPageSelection(); }
        });
        actions.put(action, new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) {
                deleteSelectedItem();
            }
        });
    }

    private void undo() {
        paper.finishInlineEdit();
        PdfDocument before = controller.documentSnapshot();
        if (controller.undo()) reconcileFormulaUndo(before);
        updateStatus();
    }
    private void redo() {
        paper.finishInlineEdit();
        PdfDocument before = controller.documentSnapshot();
        if (controller.redo()) reconcileFormulaUndo(before);
        updateStatus();
    }
    private void reconcileFormulaUndo(PdfDocument before) {
        if (formulaRevision == null) return;
        try { formulaRevision.reconcileDocumentUndo(before, controller.documentSnapshot()); }
        catch (java.io.IOException failure) {
            GlassDialogs.error(this, "Could not synchronize formula revision: " + failure.getMessage());
        }
    }
    private void autoLayout() {controller.autoLayout();updateStatus();}
    private void selectAllPdfObjects() {
        paper.finishInlineEdit();
        controller.selectAll();
        updateStatus();
    }
    private void changeImageLayout(PdfDocument.ImageLayer layer) {
        paper.finishInlineEdit();
        if (controller.imageLayoutAndReflow(layer)) updateStatus();
    }

    /** The three options are real layout operations, not placeholder menu states. */
    private void showImageLayoutMenu() {
        if (controller.selectedIds().stream().noneMatch(id -> controller.placements().stream()
                .anyMatch(p -> p.id().equals(id) && !p.isNote()))) {
            GlassDialogs.warning(this, "Select an image, graph or formula first.");
            return;
        }
        GlassContextMenu menu = new GlassContextMenu();
        addImageLayoutActions(menu);
        menu.show(this, Math.max(14, getWidth() / 3), 105);
    }
    private void addImageLayoutActions(GlassContextMenu menu) {
        PdfDocument.Placement chosen = controller.selected();
        PdfDocument.ImageLayer current = chosen == null || chosen.isNote()
                ? PdfDocument.ImageLayer.IN_FRONT_OF_TEXT : chosen.imageLayer();
        item(menu, current == PdfDocument.ImageLayer.IN_FRONT_OF_TEXT
                ? "In Front of Text  (Current)" : "In Front of Text",
                () -> changeImageLayout(PdfDocument.ImageLayer.IN_FRONT_OF_TEXT));
        item(menu, current == PdfDocument.ImageLayer.BEHIND_TEXT
                ? "Behind Text  (Current)" : "Behind Text",
                () -> changeImageLayout(PdfDocument.ImageLayer.BEHIND_TEXT));
        item(menu, current == PdfDocument.ImageLayer.WRAP_TEXT
                ? "Wrap Text  (Current)" : "Wrap Text",
                () -> changeImageLayout(PdfDocument.ImageLayer.WRAP_TEXT));
    }

    /** Word-like body is a separate flowing document layer, never a large text box. */
    public void typeOnPage() {
        controller.clearSelection();
        paper.editBodyInline();
        updateStatus();
    }
    private void addNote() {paper.finishInlineEdit();controller.addNote();updateStatus();editNote();}
    private void insertFormula() {
        String latex = GlassDialogs.input(this, "Insert formula", "LaTeX formula", "", "Insert");
        if (latex == null || latex.isBlank()) return;
        PdfMaterial material = new PdfMaterial("Typed formula", latex, "", null, 0, 0) {};
        addMaterial(material, 90, 120);
    }
    private void insertCanvasInk() {
        BufferedImage image = canvasCapture.get();
        if (image == null) {GlassDialogs.warning(this, "Select some handwriting on the Canvas first.");return;}
        addMaterial(library.addInk("Handwriting", image), 85, 110);
    }
    private void editFormula() {
        PdfDocument.Placement selected = controller.selected();
        if (selected == null || selected.isNote() || selected.material().isGraph()) {
            GlassDialogs.warning(this, "Select a formula on the page first."); return;
        }
        if (formulaRevision == null) {
            GlassDialogs.warning(this, "Formula recalculation is not configured."); return;
        }
        PdfMaterial original = selected.material();
        FormulaEditDialog.Result change = FormulaEditDialog.show(
                SwingUtilities.getWindowAncestor(this), original, formulaRevision::recalculate);
        if (change == null) return;
        try {
            formulaRevision.editPdf(controller, original, change.formula(), change.result());
            updateStatus();
        } catch (java.io.IOException | RuntimeException problem) {
            GlassDialogs.error(this, "Could not save formula revision: " + problem.getMessage());
        }
    }

    /** Existing formula editor, now reached from the sole History archive. */
    public void editHistoryFormula(HistoryItem item) {
        if (item == null || item.hasImage() || formulaRevision == null) return;
        PdfMaterial original = HistoryMaterialMapper.toPdfMaterial(item.entry());
        FormulaEditDialog.Result change = FormulaEditDialog.show(
                SwingUtilities.getWindowAncestor(this), original, formulaRevision::recalculate);
        if (change == null) return;
        try {
            formulaRevision.editHistory(controller, item.entry(), change.formula(), change.result());
            updateStatus();
        } catch (java.io.IOException | RuntimeException problem) {
            GlassDialogs.error(this, "Could not save History revision: " + problem.getMessage());
        }
    }
    private void editNote() {
        if (!paper.editNoteInline()) {
            GlassDialogs.warning(this, "Select a text box to edit it, or click Write to type in the document.");
        }
    }
    private void setNoteStyle(String style) {
        pdf_editor.use_case.NoteFormatting.Style requested = switch (style) {
            case "bold" -> pdf_editor.use_case.NoteFormatting.Style.BOLD;
            case "italic" -> pdf_editor.use_case.NoteFormatting.Style.ITALIC;
            default -> pdf_editor.use_case.NoteFormatting.Style.UNDERLINE;
        };
        if (paper.isEditingNote()) {
            paper.toggleInlineStyle(switch (requested) {
                case BOLD -> javax.swing.text.StyleConstants.Bold;
                case ITALIC -> javax.swing.text.StyleConstants.Italic;
                case UNDERLINE -> javax.swing.text.StyleConstants.Underline;
            });
        } else if (!controller.toggleSelectedNoteStyle(requested)) {
            GlassDialogs.warning(this, "Select or edit a text box first.");
        }
        updateStatus();
    }
    private void applyNoteFontSize(int size) {
        if (paper.isEditingNote()) paper.setInlineFontSize(size);
        else controller.resizeSelectedNote(size);
        updateStatus();
    }
    private void arrange(int direction) {
        paper.finishInlineEdit();
        controller.arrange(direction);
        updateStatus();
    }
    private void arrangeMenu() {
        if (controller.selected() == null) {GlassDialogs.warning(this, "Select a page object first.");return;}
        GlassContextMenu menu = new GlassContextMenu();
        item(menu, "Bring forward", () -> arrange(1));
        item(menu, "Send backward", () -> arrange(-1));
        item(menu, "Bring to front", () -> arrange(2));
        item(menu, "Send to back", () -> arrange(-2));
        item(menu, "Move to page…", this::moveToPageMenu);
        menu.show(this, Math.max(12, getWidth() / 3), 100);
    }
    /** Append native-feeling page targets directly to the shared glass menu, not a legacy JMenu. */
    private void addPageDestinations(GlassContextMenu menu) {
        for (int i = 0; i < controller.pageCount(); i++) {
            if (i == controller.currentPage()) continue;
            final int destination = i;
            item(menu, "Move to Page " + (i + 1), () -> moveSelectedToPage(destination));
        }
        item(menu, "Move to New Page", () -> moveSelectedToPage(-1));
    }
    private void moveSelectedToPage(int destination) {
        paper.finishInlineEdit();
        controller.moveSelectedToPage(destination);
        updateStatus();
    }
    private void moveToPageMenu() {
        if (controller.selected() == null) {GlassDialogs.warning(this, "Select a page object first.");return;}
        GlassContextMenu menu = new GlassContextMenu();
        for (int i = 0; i < controller.pageCount(); i++) {
            if (i == controller.currentPage()) continue;
            final int destination = i;
            menu.addAction("Page " + (i + 1), () -> moveSelectedToPage(destination));
        }
        menu.addAction("New page", () -> moveSelectedToPage(-1));
        menu.show(this, Math.max(12, getWidth() / 3), 106);
    }
    private void contextMenu(MouseEvent event) {
        GlassContextMenu menu = new GlassContextMenu();
        PdfDocument.Placement selected = controller.selected();
        if (selected == null) {
            item(menu, "Write here", () -> {
                controller.clearSelection();
                paper.editBodyInlineAt(paper.pageX(event.getX()), paper.pageY(event.getY()));
            });
            item(menu, "Insert formula", this::insertFormula);
            item(menu, "Capture Canvas area", () -> regionCapture.run());
        } else {
            if (selected.isNote()) item(menu, "Type / edit text in place", this::editNote);
            else if (!selected.material().isGraph()) {
                item(menu, "Edit formula / recalculate or manual...", this::editFormula);
                if (selected.material().hasResult()) item(menu, "Split formula and answer", () -> {controller.splitSelected();updateStatus();});
            }
            item(menu, "Bring forward one layer", () -> arrange(1));
            item(menu, "Send backward one layer", () -> arrange(-1));
            item(menu, "Bring to front", () -> arrange(2));
            item(menu, "Send to back", () -> arrange(-2));
            if (controller.selectedIds().stream().anyMatch(id -> controller.placements().stream()
                    .anyMatch(p -> p.id().equals(id) && !p.isNote()))) addImageLayoutActions(menu);
            addPageDestinations(menu);
            item(menu, controller.selectedIds().size() > 1 ? "Delete selected items" : "Delete selected item",
                    this::deleteSelectedItem);
        }
        menu.show(paper, event.getX(), event.getY());
    }
    private static void item(GlassContextMenu menu, String title, Runnable action) {
        menu.addAction(title, action);
    }
    private void preview() {
        paper.prepareGraphSnapshots(() -> {
            try {
                List<pdf_editor.domain.PdfPreviewPage> previews = controller.preview();
                if (previews.isEmpty()) return;
                java.util.List<BufferedImage> pages = new java.util.ArrayList<>();
                for (var preview : previews) {
                    BufferedImage image = ImageIO.read(new ByteArrayInputStream(preview.png()));
                    if (image != null) pages.add(image);
                }
                if (!pages.isEmpty()) WordPreviewDialog.show(this, pages);
            } catch (IOException bad) { error("Preview failed", bad); }
        }, problem -> GlassDialogs.error(this, "Graph preview failed: " + problem));
    }
    private void export() {
        JFileChooser chooser = new JFileChooser();chooser.setDialogTitle("Export PDF");
        chooser.setSelectedFile(new java.io.File("math-lab.pdf"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path target = chooser.getSelectedFile().toPath();
        if (!target.toString().toLowerCase(java.util.Locale.ROOT).endsWith(".pdf"))
            target = Path.of(target.toString() + ".pdf");
        if (java.nio.file.Files.exists(target) && !GlassDialogs.confirm(this,
                "Confirm export", "Replace existing file?", "Replace")) return;
        final Path output = target;
        paper.prepareGraphSnapshots(() -> {
            try {
                controller.export(output);
                title.setText(output.getFileName().toString());
            } catch (IOException bad) { error("Export failed", bad); }
        }, problem -> GlassDialogs.error(this, "Graph export failed: " + problem));
    }
    private void error(String message, Exception e) {GlassDialogs.error(this, message + ": " + e.getMessage());}
    private final class Tab {
        private final String text;
        private final JPanel ribbon;
        private final JToggleButton button;
        private Tab(String text, JPanel ribbon) {
            this.text = text;this.ribbon = ribbon;
            button = new JToggleButton(text) {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    if (isSelected() || getModel().isRollover()) {
                        g2.setColor(isSelected() ? new Color(225, 237, 254) : new Color(244, 246, 250));
                        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 13, 13);
                    }
                    g2.dispose(); super.paintComponent(g);
                }
            };
            button.setContentAreaFilled(false);
            button.setOpaque(false);
            button.setBorder(BorderFactory.createEmptyBorder(8, 13, 8, 13));
            button.setFocusable(false);
            button.setRolloverEnabled(true);
            button.setFont(BoardTheme.font(12f));
            button.addActionListener(e -> select());
        }
        private void select() {
            for (Tab tab : tabs) tab.button.setSelected(tab == this);
            ribbonHolder.removeAll();
            Capsule capsule = new Capsule(new BorderLayout());
            capsule.add(ribbon, BorderLayout.CENTER);
            ribbonHolder.add(capsule, BorderLayout.CENTER);
            ribbonHolder.revalidate();ribbonHolder.repaint();
        }
    }
}
