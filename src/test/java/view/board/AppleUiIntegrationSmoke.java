package view.board;

import pdf_editor.rendering.PdfWrapTextLayout;
import interface_adapter.history.HistoryViewModel;
import pdf_editor.use_case.MaterialStore;
import pdf_editor.adapter.PdfEditorController;
import pdf_editor.data_access.FilePdfDocumentRepository;
import pdf_editor.domain.PdfDocument;
import pdf_editor.use_case.AutoLayoutInteractor;
import pdf_editor.use_case.DocumentEditor;
import pdf_editor.use_case.PdfExportInteractor;
import pdf_editor.use_case.PdfWorkspace;
import view.DrawingCanvas;
import view.glass.AppleLook;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/** Tests integration of the incoming presentation changes with the existing PDF/History behavior.
 * No recognition model, network service, or real user documents are needed. */
public final class AppleUiIntegrationSmoke {
    private AppleUiIntegrationSmoke() { }
    public static void main(String[] args) throws Exception {
        verify();
        System.out.println("Apple UI integration smoke passed");
    }
    public static void verify() throws Exception {
        SwingUtilities.invokeAndWait(AppleUiIntegrationSmoke::controls);
        if (GraphicsEnvironment.isHeadless()) return;
        Path tmp = Files.createTempDirectory("pentex-apple-ui-");
        AtomicReference<JFrame> frame = new AtomicReference<>();
        AtomicReference<BoardWindow> boardRef = new AtomicReference<>();
        AtomicReference<PdfPanel> pdfRef = new AtomicReference<>();
        AtomicReference<FloatingToolbar> toolbarRef = new AtomicReference<>();
        try {
            PdfWorkspace workspace = new PdfWorkspace(new FilePdfDocumentRepository(tmp));
            PdfDocument doc = new PdfDocument();
            doc.setBody(0, List.of(new PdfDocument.NoteSpan("Existing recovered document", true, false, false, 18)));
            workspace.saveRecovery(workspace.snapshot(UUID.randomUUID(), "Recovered", doc, 0, 3.0));
            SwingUtilities.invokeAndWait(() -> {
                // Install the same controls as app.java, without booting the model.
                AppleLook.install();
                DocumentEditor editor = new DocumentEditor();
                PdfEditorController controller = new PdfEditorController(new MaterialStore(), editor,
                        new AutoLayoutInteractor(editor), new PdfExportInteractor((d,p) -> {}, (d,dpi) -> List.of()), PdfWrapTextLayout::reflow);
                PdfPanel pdf = new PdfPanel(controller);
                pdf.editor().enablePersistence(workspace, (d,dpi) -> List.of());
                DrawingCanvas canvas = new DrawingCanvas();
                BoardWindow board = new BoardWindow(canvas, canvas, h -> new HistoryPanel(new HistoryViewModel(), h), pdf);
                FloatingToolbar toolbar = new FloatingToolbar(canvas, board, pdf);
                board.addFloatingToolbar(toolbar.component());
                JFrame f = new JFrame("Apple UI integration test");
                frame.set(f); boardRef.set(board); pdfRef.set(pdf); toolbarRef.set(toolbar);
                f.setContentPane(board); f.setSize(1400, 850); f.setVisible(true);
                board.doLayout();
                check(pdf.editor().pageView().zoom() == 3.0, "Recovery loads saved zoom before panel reveal");
                // Use the actual PDF toolbar action, which takes the animated path.
                activate(board, "pdfBar", "PDF");
            });
            await(() -> !running(boardRef.get()) && pdfRef.get().isVisible());
            SwingUtilities.invokeAndWait(() -> {
                BoardWindow board = boardRef.get(); PdfPanel pdf = pdfRef.get();
                check(pdf.editor().pageView().zoom() == 3.0, "Animated first open preserves recovered 300% zoom");
                check(board.pageBounds().getMaxX() == pdf.getX(), "Canvas and PDF meet at divider");
                check(board.isPdfDropTarget(new Point(pdf.getX()+20, 240)), "PDF drop target follows actual panel");
                check(!board.isPdfDropTarget(new Point(pdf.getX()-20, 240)), "Canvas remains a separate drop target");
                check(find(pdf, "pdfSaveButton") != null && find(pdf,"pdfOpenButton") != null, "Save and Open survive UI integration");
                activate(board, "historyBar", "History");
            });
            await(() -> !running(boardRef.get()) && boardRef.get().pageBounds().x > 0);
            SwingUtilities.invokeAndWait(() -> {
                BoardWindow board = boardRef.get(); PdfPanel pdf = pdfRef.get();
                FloatingToolbar toolbar = toolbarRef.get(); toolbar.relayout();
                Rectangle cap = toolbar.bar().capsuleBounds(); cap.translate(toolbar.bar().getX(), toolbar.bar().getY());
                check(cap.getMaxX() <= pdf.getX(), "Docked toolbar does not cover PDF page footer");
                check(pdf.editor().pageView().zoom() == 3.0, "History animation does not reset PDF zoom");
                check(board.pageBounds().getMaxX() == pdf.getX(), "History/PDF split after animation");
                // Steady-state repaint at 300%: actual Swing view, not a rendering mock.
                BufferedImage image = new BufferedImage(board.getWidth(), board.getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics2D g = image.createGraphics();
                try { board.paint(g); for (int i=0;i<12;i++) { g.setClip(cap); board.paint(g); } }
                finally { g.dispose(); }
                frame.get().setSize(1000, 750);
                board.setSize(frame.get().getContentPane().getSize());
                board.doLayout(); toolbar.relayout();
                Rectangle narrow = toolbar.bar().capsuleBounds();
                narrow.translate(toolbar.bar().getX(), toolbar.bar().getY());
                check(narrow.getMaxX() <= pdf.getX(), "Narrow dock stays within Canvas");
                board.setPdfOpen(false); board.doLayout();
                check(!pdf.isVisible(), "Programmatic close keeps immediate semantics");
                board.setPdfOpen(true); board.doLayout();
                check(pdf.isVisible(), "Programmatic open stays immediate for material imports");
                board.setPdfFullscreen(true); board.doLayout();
                check(pdf.getWidth()==board.getWidth() && !toolbar.component().isVisible(), "Fullscreen keeps existing behavior");
                board.setPdfFullscreen(false); board.doLayout();
                check(toolbar.component().isVisible(), "Toolbar restored after fullscreen");
            });
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (pdfRef.get()!=null) pdfRef.get().close();
                if (frame.get()!=null) frame.get().dispose();
            });
            try (var paths = Files.walk(tmp)) {
                for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
            }
        }
    }
    private static void controls() {
        AppleLook.install();
        JSlider slider = new JSlider(35,300,100);
        check(slider.getUI() instanceof AppleLook.CapsuleSliderUI, "Apple slider installed");
        slider.setValue(300); check(slider.getValue()==300, "Slider retains original value range");
        JComboBox<String> combo = new JComboBox<>(new String[]{"first","second"});
        combo.setSelectedIndex(1); check("second".equals(combo.getSelectedItem()), "Combo selection unchanged");
        check(combo.getUI() instanceof AppleLook.PopUpButtonUI, "Apple combo installed");
        CanvasStatusChip chip = new CanvasStatusChip();
        chip.setState(new SaveMenuState("Recovered",SaveMenuState.Phase.MODIFIED)); chip.setCompact(true);
        check(chip.getPreferredSize().equals(new Dimension(44,44)), "Narrow status remains visible");
        check(chip.getToolTipText().contains("Recovered") && chip.getToolTipText().contains("Edited"), "Compact status tooltip includes name and state");
        chip.setCompact(false); chip.setSize(chip.getPreferredSize());
        BufferedImage image = new BufferedImage(chip.getWidth(),chip.getHeight(),BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics(); chip.paint(g); g.dispose();
        check(image.getRGB(0,0)>>>24 == 0, "Status capsule keeps transparent corners");
    }
    private static void await(BooleanSupplier ready) throws Exception {
        long deadline = System.nanoTime()+5_000_000_000L;
        while (System.nanoTime()<deadline) {
            AtomicReference<Boolean> result = new AtomicReference<>(false);
            SwingUtilities.invokeAndWait(() -> result.set(ready.getAsBoolean()));
            if (result.get()) { SwingUtilities.invokeAndWait(() -> {}); return; }
            Thread.sleep(20);
        }
        throw new AssertionError("Panel animation did not settle");
    }
    private static boolean running(BoardWindow board) {
        return ((Timer)field(board,"panelTimer")).isRunning();
    }
    private static Object field(Object object,String name) {
        try { Field f = object.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(object); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }
    private static void activate(BoardWindow board,String field,String label) {
        GlassBar bar=(GlassBar)field(board,field);
        for(Object raw:(List<?>)field(bar,"items")) {
            GlassBar.Item item=(GlassBar.Item)raw;
            if(label.equals(item.getLabel())) { bar.activate(item); return; }
        }
        throw new AssertionError("Missing toolbar action: "+label);
    }
    private static Component find(Container container,String name) {
        for(Component c:container.getComponents()) {
            if(name.equals(c.getName())) return c;
            if(c instanceof Container nested) { Component found=find(nested,name); if(found!=null)return found; }
        }
        return null;
    }
    private static void check(boolean value,String message) { if(!value) throw new AssertionError(message); }
}
