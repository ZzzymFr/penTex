package pdf_editor.view;

/** One sizing policy for a Graph PDF thumbnail, its drop preview, and its placement.
 * Values are in PDF page points; the view applies its current zoom afterwards.
 */
public final class GraphPlacementSizing {
    private static final float INITIAL_WIDTH = 280f;
    private static final float MIN_HEIGHT = 65f;
    private static final float MAX_HEIGHT = 600f;

    private GraphPlacementSizing() { }

    public record Size(float width, float height) { }

    public static Size forImage(int pixelWidth, int pixelHeight) {
        if (pixelWidth <= 0 || pixelHeight <= 0)
            throw new IllegalArgumentException("Image dimensions must be positive");
        return forAspect((double) pixelWidth / pixelHeight);
    }

    public static Size forAspect(double ratio) {
        if (!Double.isFinite(ratio) || ratio <= 0) ratio = 1.0;
        double width = INITIAL_WIDTH;
        double height = Math.max(MIN_HEIGHT, width / ratio);
        if (height > MAX_HEIGHT) {
            width *= MAX_HEIGHT / height;
            height = MAX_HEIGHT;
        }
        return new Size((float) width, (float) height);
    }
}
