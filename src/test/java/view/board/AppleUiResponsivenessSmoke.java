package view.board;

import interface_adapter.history.HistoryViewModel;
import pdf_editor.MaterialStore;
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
public final class AppleUiResponsivenessSmoke {
    private AppleUiResponsivenessSmoke() { }
    public static void main(String[] args) throws Exception {
        verify();
        System.out.println("Apple UI responsiveness smoke passed");
    }
    public static void verify() throws Exception {
        SwingUtilities.invokeAndWait(AppleUiResponsivenessSmoke::controls);
        cacheTest();
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
                        new AutoLayoutInteractor(editor), new PdfExportInteractor((d,p) -> {}, (d,dpi) -> List.of()));
                PdfPanel pdf = new PdfPanel(controller);
                pdf.editor().enablePersistence(workspace, (d,dpi) -> List.of());
                DrawingCanvas canvas = new DrawingCanvas();
                try {
                    var ink = new workspace.domain.CanvasState.Ink(List.of(new workspace.domain.CanvasState.Point(100,140,0),new workspace.domain.CanvasState.Point(200,200,1)),Color.BLACK.getRGB());
                    canvas.restoreCanvasState(new workspace.domain.CanvasState(List.of(ink),List.of(),List.of(),1,0,0));
                } catch (java.io.IOException failure) { throw new AssertionError(failure); }
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
            for (int i=0; i<4; i++) {
                animatedToggle(boardRef.get(),pdfRef.get(),"setPdfOpen",false);
                animatedToggle(boardRef.get(),pdfRef.get(),"setHistoryOpen",false);
                animatedToggle(boardRef.get(),pdfRef.get(),"setHistoryOpen",true);
                animatedToggle(boardRef.get(),pdfRef.get(),"setPdfOpen",true);
            }
            animatedToggle(boardRef.get(),pdfRef.get(),"setPdfOpen",false);
            SwingUtilities.invokeAndWait(() -> {pdfRef.get().close();frame.get().dispose();});
            check(workspace.recover().orElseThrow().zoom()==3.0,"Close while hidden persists zoom");
            SwingUtilities.invokeAndWait(() -> {
                DocumentEditor editor=new DocumentEditor();
                PdfPanel next=new PdfPanel(new PdfEditorController(new MaterialStore(),editor,new AutoLayoutInteractor(editor),new PdfExportInteractor((d,p)->{},(d,dpi)->List.of())));
                next.editor().enablePersistence(workspace,(d,dpi)->List.of());
                DrawingCanvas canvas=new DrawingCanvas();
                BoardWindow board=new BoardWindow(canvas,canvas,h->new HistoryPanel(new HistoryViewModel(),h),next);
                JFrame f=new JFrame("Restart regression"); f.setContentPane(board);f.setSize(1200,760);f.setVisible(true);board.doLayout();
                frame.set(f);boardRef.set(board);pdfRef.set(next);
                activate(board,"pdfBar","PDF");
            });
            await(() -> !running(boardRef.get()) && pdfRef.get().isVisible());
            SwingUtilities.invokeAndWait(() -> check(pdfRef.get().editor().pageView().zoom()==3.0,"New window restores 300%"));
            formula(frame.get(),true);
            formula(frame.get(),false);
            hoverCheck(boardRef.get());
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
    private static void animatedToggle(BoardWindow board,PdfPanel pdf,String method,boolean open) throws Exception {
        java.util.concurrent.atomic.AtomicInteger resizes=new java.util.concurrent.atomic.AtomicInteger();
        var inkBefore=new AtomicReference<java.util.List<workspace.domain.CanvasState.Ink>>();
        java.awt.event.ComponentAdapter listener=new java.awt.event.ComponentAdapter(){@Override public void componentResized(java.awt.event.ComponentEvent e){resizes.incrementAndGet();}};
        SwingUtilities.invokeAndWait(() -> {
            board.canvasPage().addComponentListener(listener);
            inkBefore.set(((DrawingCanvas)field(board,"canvas")).exportCanvasState().ink());
            try {var m=BoardWindow.class.getDeclaredMethod(method,boolean.class,boolean.class);m.setAccessible(true);m.invoke(board,open,true);}
            catch(ReflectiveOperationException ex){throw new AssertionError(ex);}
        });
        await(() -> !running(board));
        SwingUtilities.invokeAndWait(() -> {
            board.canvasPage().removeComponentListener(listener);
            check(resizes.get()<=2,"Canvas resized every frame: "+resizes.get());
            check(pdf.editor().pageView().zoom()==3.0,"Panel toggle resets zoom");
            check(board.canvasPage().isVisible(),"Canvas hidden after animation");
            check(inkBefore.get().equals(((DrawingCanvas)field(board,"canvas")).exportCanvasState().ink()),"Panel transition changes ink");
        });
    }
    private static void hoverCheck(BoardWindow board) throws Exception {
        GlassBar bar=(GlassBar)field(board,"historyBar");
        int x=GlassBar.MARGIN+GlassBar.PAD+18,y=GlassBar.MARGIN+GlassBar.CAP_H/2;
        SwingUtilities.invokeAndWait(()->{
            bar.dispatchEvent(new java.awt.event.MouseEvent(bar,java.awt.event.MouseEvent.MOUSE_EXITED,System.currentTimeMillis(),0,-1,-1,0,false));
            move(bar,x,y);
            check(field(bar,"hoverItem")==null,"Hover must wait for dwell");
        });
        await(()->field(bar,"hoverItem")!=null && !((Timer)field(bar,"timer")).isRunning());
        SwingUtilities.invokeAndWait(()->{
            for(int i=0;i<12;i++)move(bar,x+i%3,y);
            check(!((Timer)field(bar,"timer")).isRunning(),"Small movement restarts spring");
        });
    }
    private static void move(GlassBar bar,int x,int y) {
        bar.dispatchEvent(new java.awt.event.MouseEvent(bar,java.awt.event.MouseEvent.MOUSE_MOVED,System.currentTimeMillis(),0,x,y,0,false));
    }
    private static void cacheTest() throws Exception {
        var captures=new java.util.concurrent.atomic.AtomicInteger();
        var thumbnails=new java.util.concurrent.atomic.AtomicInteger();
        var holder=new AtomicReference<view.glass.AsyncThumbnailCache<String>>();
        SwingUtilities.invokeAndWait(() -> {
            GlassBackdropCache cache=new GlassBackdropCache(()->{});
            java.util.function.Supplier<BufferedImage> capture=()->{captures.incrementAndGet();return new BufferedImage(100,60,BufferedImage.TYPE_INT_ARGB);};
            var first=cache.image(100,60,false,new Point(),1,false,capture);
            for(int i=0;i<100;i++)check(first==cache.image(100+i,60,false,new Point(i,0),2+i,true,capture),"Optical resampling during animation");
            check(captures.get()==1,"100 frames must share backdrop");cache.clear();
            holder.set(new view.glass.AsyncThumbnailCache<>(()->{}));
            for(int i=0;i<30;i++)holder.get().get("one",()->{check(!SwingUtilities.isEventDispatchThread(),"Thumbnail on EDT");thumbnails.incrementAndGet();return new BufferedImage(10,10,BufferedImage.TYPE_INT_ARGB);});
        });
        await(()->holder.get().get("one",()->{throw new AssertionError("Duplicate decode");})!=null);
        SwingUtilities.invokeAndWait(()->{check(thumbnails.get()==1,"Repeated thumbnail decode");holder.get().close();});
    }
    private static void formula(JFrame frame,boolean manual) throws Exception {
        var result=new AtomicReference<pdf_editor.view.FormulaEditDialog.Result>();
        var done=new java.util.concurrent.atomic.AtomicBoolean();var evaluated=new java.util.concurrent.atomic.AtomicBoolean();
        SwingUtilities.invokeLater(()->{
            var item=new MaterialStore().addCalculation("3+2","5");
            result.set(pdf_editor.view.FormulaEditDialog.show(frame,item,latex->{check(!SwingUtilities.isEventDispatchThread(),"Evaluation on EDT");evaluated.set(true);return "7";}));done.set(true);
        });
        await(()->find(frame.getLayeredPane(),"glass-modal-overlay")!=null);
        SwingUtilities.invokeAndWait(()->{
            Container overlay=(Container)find(frame.getLayeredPane(),"glass-modal-overlay");
            component(overlay,JTextArea.class).setText("3+4");
            if(manual){var modes=component(overlay,view.glass.GlassSegmentedControl.class);modes.getActionMap().get("manual").actionPerformed(new java.awt.event.ActionEvent(modes,0,"manual"));}
            JButton save=button(overlay,manual?"Save as Entered":"Recalculate & Save");check(save!=null,"Formula save missing");save.doClick();
        });
        await(done::get);check(result.get()!=null&&result.get().formula().equals("3+4"),"Formula submission");
        check(evaluated.get()!=manual,"Manual/Calculate semantics");if(!manual)check(result.get().result().equals("7"),"Calculation result");
    }
    private static JButton button(Container root,String label){for(Component c:root.getComponents()){if(c instanceof JButton b&&label.equals(b.getText()))return b;if(c instanceof Container t){JButton b=button(t,label);if(b!=null)return b;}}return null;}
    private static <T> T component(Container root,Class<T> type){for(Component c:root.getComponents()){if(type.isInstance(c))return type.cast(c);if(c instanceof Container t){T v=component(t,type);if(v!=null)return v;}}return null;}
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
