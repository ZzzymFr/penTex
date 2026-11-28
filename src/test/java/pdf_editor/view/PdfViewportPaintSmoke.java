package pdf_editor.view;

import pdf_editor.rendering.PdfWrapTextLayout;
import pdf_editor.use_case.MaterialStore;
import pdf_editor.adapter.PdfEditorController;
import pdf_editor.domain.PdfDocument;
import pdf_editor.domain.PdfMaterial;
import pdf_editor.use_case.AutoLayoutInteractor;
import pdf_editor.use_case.DocumentEditor;
import pdf_editor.use_case.PdfExportInteractor;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Real Java2D rendering and viewport invalidation, runnable with or without a display. */
public final class PdfViewportPaintSmoke {
    private PdfViewportPaintSmoke() { }
    public static void main(String[] args) throws Exception {
        verify();
        if (!GraphicsEnvironment.isHeadless()) verifyWindow();
        System.out.println("PDF viewport cache regression passed");
    }

    public static void verify() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                cacheContract();
                pageContract();
            } catch (Exception e) { throw new IllegalStateException(e); }
        });
    }

    private static void cacheContract() {
        var cache = new PdfViewportPaintCache();
        var target = new BufferedImage(800, 500, BufferedImage.TYPE_INT_RGB);
        var count = new AtomicInteger();
        Graphics2D g = target.createGraphics();
        Rectangle area = new Rectangle(20, 30, 720, 420);
        try {
            var painter = (java.util.function.Consumer<Graphics2D>) layer -> {
                count.incrementAndGet(); layer.setColor(Color.RED); layer.fill(area);
            };
            check(cache.paint(g, area, "first", painter), "Screen cache supported");
            check(target.getRGB(30, 40) == Color.RED.getRGB(), "Viewport origin preserved");
            for (int i = 0; i < 100; i++) {
                g.setClip(i, i, 300, 100);
                check(cache.paint(g, area, "first", painter), "Dirty-region paint uses viewport backing");
            }
            check(count.get() == 1, "100 changing dirty regions composite the stable viewport only once");
            g.setClip(null);
            check(cache.paint(g, area, "edit", painter) && count.get() == 2, "Content change invalidates viewport");
            Rectangle scrolled = new Rectangle(30, 40, 720, 420);
            check(cache.paint(g, scrolled, "edit", painter) && count.get() == 3, "Scroll invalidates viewport origin");
            cache.clear();
            check(cache.paint(g, area, "edit", painter) && count.get() == 4, "Document reset invalidates surface");
            g.scale(2, 2);
            check(cache.paint(g, area, "edit", painter), "Non-native transform uses device-pixel software backing");
            check(count.get() == 5, "Non-native transform changes raster resolution exactly once");
            for (int i = 0; i < 20; i++)
                check(cache.paint(g, area, "edit", painter), "Software backing retained");
            check(count.get() == 5, "Software backing does not re-render each frame");
        } finally { g.dispose(); cache.clear(); }
    }

    private static void pageContract() throws Exception {
        Fixture f = fixture();
        var viewport = new JViewport();
        viewport.setSize(720, 480); viewport.setView(f.page());
        viewport.setViewSize(f.page().getPreferredSize());
        viewport.setViewPosition(new Point(25, 35));
        check(f.page().getVisibleRect().width == 720 && f.page().getVisibleRect().height == 480,
                "Backing region is the viewport, not the 300% A4 sheet");
        compare(f.page(), viewport, "Initial 300% content");
        compare(f.page(), viewport, "Stable repaint");
        f.controller().select(f.controller().placements().get(0).id());
        compare(f.page(), viewport, "Selection invalidates cache");
        f.controller().clearSelection();
        compare(f.page(), viewport, "Selection removal invalidates cache");
        viewport.setViewPosition(new Point(230, 220));
        compare(f.page(), viewport, "Scroll renders newly visible content");
        f.editor().updateFlowingBody(0, List.of(List.of(new PdfDocument.NoteSpan("Changed body", true, false, false, 21))));
        compare(f.page(), viewport, "Body edit invalidates cache");
        f.controller().switchPage(1);
        compare(f.page(), viewport, "Page switch invalidates cache");
        f.controller().switchPage(0);
        f.page().setZoom(2); viewport.setViewSize(f.page().getPreferredSize());
        viewport.setViewPosition(new Point(40, 30));
        compare(f.page(), viewport, "Zoom change invalidates cache");
        f.page().resetInteractiveGraphs();
        compare(f.page(), viewport, "Document reset preserves painting");
    }

    private static void compare(PdfPageCanvas page, JViewport view, String message) {
        BufferedImage cached = render(page, view, false);
        BufferedImage original = render(page, view, true);
        int[] first = cached.getRGB(0, 0, cached.getWidth(), cached.getHeight(), null, 0, cached.getWidth());
        int[] second = original.getRGB(0, 0, original.getWidth(), original.getHeight(), null, 0, original.getWidth());
        check(Arrays.equals(first, second), message + ": screen pixels equal direct print path");
    }

    private static BufferedImage render(PdfPageCanvas page, JViewport view, boolean direct) {
        BufferedImage image = new BufferedImage(view.getWidth(), view.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setClip(0, 0, image.getWidth(), image.getHeight());
            Point position = view.getViewPosition(); g.translate(-position.x, -position.y);
            if (direct) page.print(g); else page.paint(g);
        } finally { g.dispose(); }
        return image;
    }

    /** An actual Swing window checks the native VolatileImage path, including native HiDPI. */
    private static void verifyWindow() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JFrame frame = new JFrame("PDF rendering regression");
            try {
                Fixture f = fixture();
                var viewport = new JViewport(); viewport.setView(f.page());
                frame.setContentPane(viewport); frame.setSize(800, 550); frame.setVisible(true);
                viewport.setViewSize(f.page().getPreferredSize());
                viewport.setViewPosition(new Point(80, 90));
                Graphics2D screen = (Graphics2D) f.page().getGraphics();
                check(screen != null, "Actual native graphics obtained");
                try {
                    Rectangle visible = f.page().getVisibleRect();
                    var cache = new PdfViewportPaintCache();
                    var renders = new AtomicInteger();
                    var paint = (java.util.function.Consumer<Graphics2D>) g -> {
                        renders.incrementAndGet(); g.setColor(Color.WHITE); g.fill(visible);
                    };
                    check(cache.paint(screen, visible, "state", paint), "Native-resolution backing surface supported");
                    for (int i = 0; i < 20; i++) check(cache.paint(screen, visible, "state", paint), "Native cache reuse");
                    check(renders.get() <= 2, "Native backing surface reused; one restore allowed");
                    cache.clear();
                    long start = System.nanoTime();
                    for (int i = 0; i < 40; i++) {
                        f.page().paintImmediately(visible.x + i, visible.y + i, 300, 160);
                    }
                    System.out.printf("Native scale %.1f, 40 clipped repaint calls: %.1f ms%n",
                            screen.getTransform().getScaleX(), (System.nanoTime() - start) / 1e6);
                } finally { screen.dispose(); }
            } catch (Exception e) { throw new IllegalStateException(e); }
            finally { frame.dispose(); }
        });
    }

    static Fixture fixture() throws Exception {
        var editor = new DocumentEditor();
        var controller = new PdfEditorController(new MaterialStore(), editor, new AutoLayoutInteractor(editor),
                new PdfExportInteractor((doc, path) -> {}, (doc, dpi) -> List.of()), PdfWrapTextLayout::reflow);
        PdfDocument doc = new PdfDocument(); doc.addPage();
        doc.setBody(0, List.of(new PdfDocument.NoteSpan("Rich text on a zoomed page. ".repeat(65),
                false, false, true, 16, "Dialog")));
        var source = new BufferedImage(240, 120, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = source.createGraphics();
        try {
            graphics.setColor(Color.BLUE); graphics.fillOval(10, 10, 220, 100);
            graphics.setColor(Color.RED); graphics.drawLine(10, 10, 230, 110);
        } finally { graphics.dispose(); }
        var bytes = new ByteArrayOutputStream(); ImageIO.write(source, "png", bytes);
        var material = PdfMaterial.restore("ink", "Ink", "", "", bytes.toByteArray(), 240, 120, null);
        doc.add(0, PdfDocument.Placement.material(material, 30, 30, 180, 90)
                .withLayout(PdfDocument.ImageLayer.WRAP_TEXT, PdfDocument.AnchorMode.FIXED_ON_PAGE, -1));
        doc.add(0, PdfDocument.Placement.material(material, 80, 170, 240, 120)
                .withLayout(PdfDocument.ImageLayer.BEHIND_TEXT, PdfDocument.AnchorMode.FIXED_ON_PAGE, -1));
        doc.add(0, PdfDocument.Placement.note("Floating note", 80, 80, 140, 100));
        doc.add(1, PdfDocument.Placement.note("Second page", 90, 90, 120, 90));
        controller.loadDocument(doc, 0);
        var page = new PdfPageCanvas(controller, new PdfPageCanvas.Listener() {
            public void contextMenu(MouseEvent event) { }
            public void editNote() { }
            public void editFormula() { }
        });
        page.setZoom(3); page.setSize(page.getPreferredSize());
        return new Fixture(page, controller, editor);
    }
    record Fixture(PdfPageCanvas page, PdfEditorController controller, DocumentEditor editor) { }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
