package view.drawing;

import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

/**
 * A picture placed on the canvas: an item dragged in from History (with its formula and result)
 * or a plain image. Immutable apart from a view-only resolution cache; moving or resizing makes a
 * new picture with {@link #withBox}. Identity matters: selections hold pictures by reference.
 */
public final class CanvasPicture {
    private final BufferedImage image;
    private final Rectangle2D box;
    private final String latex;
    private final String result;
    private BufferedImage rendered; // view-only resolution cache, never persisted

    public CanvasPicture(BufferedImage image, Rectangle2D box, String latex, String result) {
        this.image = image;
        this.box = box;
        this.latex = latex;
        this.result = result;
    }

    public BufferedImage image() {
        return image;
    }

    /** Where it is, in world coordinates. */
    public Rectangle2D box() {
        return box;
    }

    /** The History formula, or "" for a plain image. */
    public String latex() {
        return latex;
    }

    public String result() {
        return result;
    }

    /** True for a History formula that can be re-typeset and edited. */
    public boolean isFormula() {
        return !latex.isBlank();
    }

    public CanvasPicture withBox(Rectangle2D next) {
        return new CanvasPicture(image, next, latex, result);
    }

    /** The image last typeset at screen resolution, or null. */
    public BufferedImage rendered() {
        return rendered;
    }

    public void cacheRendered(BufferedImage image) {
        rendered = image;
    }
}
