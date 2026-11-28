package view.drawing;

import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

/**
 * Where things go on screen: a new graph card beside its formula, and how far to scroll so a box
 * is in view. Geometry only; the canvas applies the result.
 */
public final class GraphCardPlacement {
    private static final double CARD_WIDTH = 0.4;   // of the visible canvas width
    private static final int CARD_PADDING = 10;     // white border around the graph, on screen
    private static final int CARD_GAP = 48;         // space between the formula and the card, on screen
    private static final int VIEW_MARGIN_X = 16;    // keep a new card this far inside the view...
    private static final int VIEW_MARGIN_Y = 60;    // ...and clear of the floating controls

    /** A new card: its picture (scaled down if huge), box and white border, world units. */
    public record Card(BufferedImage image, Rectangle2D box, double padding) {
    }

    private GraphCardPlacement() {
    }

    /**
     * A card for this picture: to the right of the formula and centred on it vertically, or in the
     * middle of the view when there is no formula; 40% of the visible width.
     *
     * @param viewWidth  the canvas width in pixels (its preferred width before it is laid out)
     * @param width      the canvas' actual width and height, for centring
     */
    public static Card besideFormula(BufferedImage image, Rectangle2D formula, CanvasViewport viewport,
                                     int viewWidth, int width, int height) {
        double zoom = viewport.zoom();
        double imageWidth = viewWidth / zoom * CARD_WIDTH;
        double imageHeight = imageWidth * image.getHeight() / image.getWidth();
        BufferedImage sharp = downscaled(image, (int) Math.ceil(imageWidth * 2));  // sharp up to 200% (and on HiDPI)
        double padding = CARD_PADDING / zoom;
        double w = imageWidth + 2 * padding;
        double h = imageHeight + 2 * padding;
        double x;
        double y;
        if (formula != null) {
            x = formula.getMaxX() + CARD_GAP / zoom;
            y = formula.getCenterY() - h / 2;
        } else {
            x = (width / 2.0 - viewport.originX()) / zoom - w / 2;
            y = (height / 2.0 - viewport.originY()) / zoom - h / 2;
        }
        return new Card(sharp, new Rectangle2D.Double(x, y, w, h), padding);
    }

    /** Gap kept between stacked cards of the same formula, world units. */
    public static double stackGap(CanvasViewport viewport) {
        return CARD_GAP / viewport.zoom() / 2;
    }

    /**
     * How far to pan (screen pixels) so the world box is in view, clear of the floating controls;
     * when it is too big, the fallback box instead. Zero when nothing needs to move.
     */
    public static Point2D panToShow(Rectangle2D world, Rectangle2D fallback, CanvasViewport viewport,
                                    int width, int height) {
        double left = VIEW_MARGIN_X;
        double right = width - VIEW_MARGIN_X;
        double top = VIEW_MARGIN_Y;
        double bottom = height - VIEW_MARGIN_Y;
        Rectangle2D target = viewport.toScreen(world);
        if (target.getWidth() > right - left || target.getHeight() > bottom - top) {
            target = viewport.toScreen(fallback);  // too big: just show the fallback
        }
        return new Point2D.Double(shiftInto(target.getMinX(), target.getMaxX(), left, right),
                shiftInto(target.getMinY(), target.getMaxY(), top, bottom));
    }

    /** How far to move [min, max] to bring it inside [low, high]; the start wins if it cannot fit. */
    static double shiftInto(double min, double max, double low, double high) {
        if (min < low) {
            return low - min;
        }
        if (max > high) {
            return Math.max(high - max, low - min);
        }
        return 0;
    }

    /** A smooth, smaller copy of a big image, so it draws cleanly. */
    static BufferedImage downscaled(BufferedImage image, int maxWidth) {
        if (image.getWidth() <= maxWidth || maxWidth <= 0) {
            return image;
        }
        int height = Math.max(1, (int) Math.round((double) image.getHeight() * maxWidth / image.getWidth()));
        BufferedImage small = new BufferedImage(maxWidth, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = small.createGraphics();
        g.drawImage(image.getScaledInstance(maxWidth, height, java.awt.Image.SCALE_SMOOTH), 0, 0, null);
        g.dispose();
        return small;
    }
}
