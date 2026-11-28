package view.drawing;

import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

/**
 * One graph card on the canvas, in world coordinates so it pans and zooms with the ink.
 *
 * @param image   the picture shown on the card
 * @param card    the card's box (world coordinates)
 * @param padding white border around the picture (world units)
 * @param tag     the card's live owner, a {@link SavedGraphDefinition} awaiting reconstruction,
 *                or null for a plain picture
 */
public record GraphSlot(BufferedImage image, Rectangle2D card, double padding, Object tag) {

    public GraphSlot withCard(Rectangle2D next) {
        return new GraphSlot(image, next, padding, tag);
    }

    /** True when a live graph view owns this card (not a plain picture or a saved placeholder). */
    public boolean hasLiveOwner() {
        return tag != null && !(tag instanceof SavedGraphDefinition);
    }
}
