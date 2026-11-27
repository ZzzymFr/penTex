package view.glass;

import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** Dialog-local, bounded miniature cache. Decode/rasterize immutable document data away
 * from Swing; the EDT only receives completed BufferedImages and repaints visible rows. */
public final class AsyncThumbnailCache<K> implements AutoCloseable {
    private static final int MAX_ENTRIES = 128;
    private final Map<K, ImageIcon> images = new LinkedHashMap<>(16,.75f,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<K,ImageIcon> oldest) { return size()>MAX_ENTRIES; }
    };
    private final Set<K> pending = new HashSet<>();
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1,1,5,TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(32), task -> {
                Thread thread = new Thread(task,"saved-document-thumbnail"); thread.setDaemon(true); return thread;
            });
    private final Timer repaintTimer;
    private boolean closed;

    public AsyncThumbnailCache(Runnable repaint) {
        repaintTimer = new Timer(32,e -> repaint.run());
        repaintTimer.setRepeats(false);
        worker.allowCoreThreadTimeOut(true);
    }
    /** Call from a visible cell renderer on the EDT. Null means the thumbnail is pending. */
    public ImageIcon get(K key,Supplier<BufferedImage> render) {
        if (images.containsKey(key)) return images.get(key);
        if (closed || !pending.add(key)) return null;
        try {
            worker.execute(() -> {
                BufferedImage image = null;
                try { image = render.get(); } catch (RuntimeException unreadable) { /* keep empty placeholder */ }
                BufferedImage result = image;
                SwingUtilities.invokeLater(() -> {
                    if (closed) return;
                    pending.remove(key);
                    images.put(key,result==null?null:new ImageIcon(result));
                    if (!repaintTimer.isRunning()) repaintTimer.start();
                });
            });
        } catch (RejectedExecutionException full) { pending.remove(key); }
        return null;
    }
    @Override public void close() {
        closed = true; repaintTimer.stop(); worker.shutdownNow(); pending.clear(); images.clear();
    }
}
