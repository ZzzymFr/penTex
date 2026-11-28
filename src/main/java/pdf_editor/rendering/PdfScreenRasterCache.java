package pdf_editor.rendering;

import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/** Bounded cache of immutable screen rasters, scoped to painting one PDF view.
 * Export and other callers outside the scope always use the original renderers.
 * The view owns the cache; all accesses are confined to its Swing paint thread.
 */
public final class PdfScreenRasterCache {
    private static final long DEFAULT_BYTES = 48L * 1024 * 1024;
    private static final int MAX_ENTRIES = 256;
    private static final ThreadLocal<PdfScreenRasterCache> ACTIVE = new ThreadLocal<>();
    private record Key(Object source, int width, int height) { }
    private final Map<Key, BufferedImage> rasters = new LinkedHashMap<>(16, .75f, true);
    private final long budget;
    private long bytes;

    public PdfScreenRasterCache() { this(DEFAULT_BYTES); }
    /** A cache that keeps at most {@code budget} bytes of rasters. */
    public PdfScreenRasterCache(long budget) { this.budget = budget; }

    /** Nested painting restores the previous scope, even on exceptions. */
    public void duringPaint(Runnable paint) {
        PdfScreenRasterCache previous = ACTIVE.get();
        ACTIVE.set(this);
        try { paint.run(); }
        finally { if (previous == null) ACTIVE.remove(); else ACTIVE.set(previous); }
    }

    public static BufferedImage render(Object source, int width, int height, Supplier<BufferedImage> render) {
        PdfScreenRasterCache cache = ACTIVE.get();
        if (cache == null) return render.get();
        return cache.get(new Key(Objects.requireNonNull(source), width, height), render);
    }

    private BufferedImage get(Key key, Supplier<BufferedImage> render) {
        BufferedImage existing = rasters.get(key);
        if (existing != null) return existing;
        BufferedImage image = Objects.requireNonNull(render.get());
        long size = size(image);
        if (size > budget) return image;
        var oldest = rasters.entrySet().iterator();
        while ((bytes + size > budget || rasters.size() >= MAX_ENTRIES) && oldest.hasNext()) {
            bytes -= size(oldest.next().getValue());
            oldest.remove();
        }
        rasters.put(key, image);
        bytes += size;
        return image;
    }

    private static long size(BufferedImage image) { return 4L * image.getWidth() * image.getHeight(); }
    public void clear() { rasters.clear(); bytes = 0; }
}
