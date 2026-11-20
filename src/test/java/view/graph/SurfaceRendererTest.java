package view.graph;

import data_access.InMemoryGraphSessionDataAccessObject;
import entity.LatexFunctionParser;
import interface_adapter.graph.GraphExploreController;
import interface_adapter.graph.GraphPresenter;
import interface_adapter.graph.GraphState;
import interface_adapter.graph.GraphViewModel;
import org.junit.jupiter.api.Test;
import use_case.explore_graph.ExploreGraphInteractor;
import use_case.graph_function.GraphFunctionInputData;
import use_case.graph_function.GraphFunctionInteractor;

import javax.swing.SwingUtilities;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// 3d mesh gets drawn on its own thread, the edt only ever blits
class SurfaceRendererTest {
    private static final Rectangle2D PLOT = new Rectangle2D.Double(0, 34, 440, 266);

    private final GraphViewModel viewModel = new GraphViewModel();
    private final InMemoryGraphSessionDataAccessObject session = new InMemoryGraphSessionDataAccessObject();
    private final GraphPresenter presenter = new GraphPresenter(viewModel);

    private GraphState surface() throws Exception {
        new GraphFunctionInteractor(new LatexFunctionParser(), session, presenter)
                .execute(new GraphFunctionInputData("z=\\sin x \\cos y", -5, 5, -5, 5));
        GraphState s = viewModel.getState();
        assertEquals(GraphState.Kind.SURFACE, s.getKind());
        return s;
    }

    private static void onEdt(Runnable r) throws Exception {
        SwingUtilities.invokeAndWait(r);
    }

    @Test
    void edtNeverDrawsTheMeshItself() throws Exception {
        GraphState s = surface();
        OrbitCamera camera = new OrbitCamera();
        CountDownLatch ready = new CountDownLatch(1);
        AtomicReference<String> repaintThread = new AtomicReference<>();
        SurfaceRenderer renderer = new SurfaceRenderer(() -> {
            repaintThread.set(Thread.currentThread().getName());
            ready.countDown();
        });
        AtomicReference<BufferedImage> first = new AtomicReference<>();
        AtomicReference<Long> took = new AtomicReference<>();
        onEdt(() -> {
            long t0 = System.nanoTime();
            first.set(renderer.picture(s, camera, PLOT, 1f, 2));
            took.set((System.nanoTime() - t0) / 1_000_000);
        });
        assertNull(first.get(), "nothing yet, the edt just asked for it");
        assertTrue(took.get() < 20, "asking is instant, took " + took.get() + " ms");

        assertTrue(ready.await(10, TimeUnit.SECONDS), "render thread never finished");
        assertTrue(SwingUtilities.isEventDispatchThread() || repaintThread.get().startsWith("AWT-EventQueue"),
                "repaint comes back on the edt, was " + repaintThread.get());
        AtomicReference<BufferedImage> now = new AtomicReference<>();
        onEdt(() -> now.set(renderer.picture(s, camera, PLOT, 1f, 2)));
        assertNotNull(now.get());
        assertEquals(880, now.get().getWidth(), "drawn at 2x for retina");
        assertEquals(532, now.get().getHeight());
        assertTrue(paintedPixels(now.get()) > 10_000, "the mesh is actually in there");
    }

    @Test
    void fastSpinSkipsFramesNobodyNeeds() throws Exception {
        GraphState s = surface();
        OrbitCamera camera = new OrbitCamera();
        AtomicInteger repaints = new AtomicInteger();
        SurfaceRenderer renderer = new SurfaceRenderer(repaints::incrementAndGet);
        int moves = 12;
        onEdt(() -> {
            for (int i = 0; i < moves; i++) {
                camera.rotate(8, 2);
                renderer.picture(s, camera, PLOT, 1f, 1);
            }
        });
        long deadline = System.currentTimeMillis() + 10_000;
        AtomicReference<Boolean> done = new AtomicReference<>(false);
        while (!done.get() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
            onEdt(() -> done.set(renderer.upToDate(s, camera, PLOT, 1f, 1)));
        }
        assertTrue(done.get(), "ends up on the last angle");
        assertTrue(repaints.get() < moves, "stale angles got skipped, drew " + repaints.get() + " of " + moves);
    }

    @Test
    void savingA3dCardHappensOffTheEdtToo() throws Exception {
        surface();
        GraphCard card = new GraphCard(viewModel,
                new GraphExploreController(new ExploreGraphInteractor(session, presenter)));
        card.setSize(440, 300);
        CountDownLatch saved = new CountDownLatch(1);
        AtomicReference<BufferedImage> image = new AtomicReference<>();
        AtomicReference<Boolean> onEdt = new AtomicReference<>();
        onEdt(() -> card.snapshotLater(880, 600, img -> {
            onEdt.set(SwingUtilities.isEventDispatchThread());
            image.set(img);
            saved.countDown();
        }));
        assertTrue(saved.await(10, TimeUnit.SECONDS));
        assertTrue(onEdt.get(), "picture lands back on the edt");
        assertEquals(880, image.get().getWidth());
        assertTrue(paintedPixels(image.get()) > 10_000);
    }

    private static int paintedPixels(BufferedImage img) {
        int n = 0;
        for (int y = 0; y < img.getHeight(); y += 2) {
            for (int x = 0; x < img.getWidth(); x += 2) {
                if ((img.getRGB(x, y) >>> 24) > 0) {
                    n++;
                }
            }
        }
        return n;
    }
}
