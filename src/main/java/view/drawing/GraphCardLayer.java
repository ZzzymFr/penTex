package view.drawing;

import java.awt.Point;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * The graph cards on the canvas, oldest first so the newest is painted on top, plus which live
 * cards are currently covered by their interactive view. Pure bookkeeping and hit-testing:
 * the canvas decides when to repaint and whom to notify.
 */
public final class GraphCardLayer {
    /** Radius of a card's close button, on screen. */
    public static final int CLOSE_RADIUS = 11;

    private final List<GraphSlot> slots = new ArrayList<>();
    // tags whose live card sits right on top of the slot; the screen skips their picture (export never does)
    private final Set<Object> covered = Collections.newSetFromMap(new IdentityHashMap<>());

    /** The cards, oldest first (read-only view). */
    public List<GraphSlot> all() {
        return Collections.unmodifiableList(slots);
    }

    public int size() {
        return slots.size();
    }

    public boolean isEmpty() {
        return slots.isEmpty();
    }

    public void add(GraphSlot slot) {
        slots.add(slot);
    }

    /** The card shown with this tag (by identity), or null. */
    public GraphSlot slotOf(Object tag) {
        if (tag == null) {
            return null;
        }
        for (GraphSlot slot : slots) {
            if (slot.tag() == tag) {
                return slot;
            }
        }
        return null;
    }

    /** The newest card, or null when there is none. */
    public GraphSlot newest() {
        return slots.isEmpty() ? null : slots.get(slots.size() - 1);
    }

    /** Swaps a card for its changed copy in place; false if the card is gone. */
    public boolean replace(GraphSlot old, GraphSlot next) {
        int at = slots.indexOf(old);
        if (at < 0) {
            return false;
        }
        slots.set(at, next);
        return true;
    }

    /** False if the card was not there. */
    public boolean remove(GraphSlot slot) {
        return slot != null && slots.remove(slot);
    }

    /**
     * Turns the saved placeholder with this definition and exact bounds into a live card.
     * False when it is gone (removed while the graph was being rebuilt): a deleted graph is never resurrected.
     */
    public boolean replaceSavedPlaceholder(java.awt.image.BufferedImage image, Rectangle2D bounds,
                                           String encodedDefinition, Object liveTag) {
        for (int i = 0; i < slots.size(); i++) {
            GraphSlot old = slots.get(i);
            if (old.tag() instanceof SavedGraphDefinition saved
                    && encodedDefinition.equals(saved.encoded()) && sameRectangle(old.card(), bounds)) {
                slots.set(i, new GraphSlot(image, (Rectangle2D) bounds.clone(), 0, liveTag));
                return true;
            }
        }
        return false;
    }

    private static boolean sameRectangle(Rectangle2D a, Rectangle2D b) {
        return Double.compare(a.getX(), b.getX()) == 0
                && Double.compare(a.getY(), b.getY()) == 0
                && Double.compare(a.getWidth(), b.getWidth()) == 0
                && Double.compare(a.getHeight(), b.getHeight()) == 0;
    }

    /** A second graph of the same formula would land exactly on the first: stack it underneath instead. */
    public Rectangle2D clearOfOtherCards(Rectangle2D card, double gap) {
        for (int tries = 0; tries < slots.size() + 1; tries++) {
            GraphSlot hit = null;
            for (GraphSlot other : slots) {
                if (other.card().intersects(card)) {
                    hit = other;
                    break;
                }
            }
            if (hit == null) {
                return card;
            }
            card = new Rectangle2D.Double(card.getX(), hit.card().getMaxY() + gap, card.getWidth(), card.getHeight());
        }
        return card;
    }

    /** Tag of the topmost live card at this screen point, or null. */
    public Object liveTagAt(Point screen, CanvasViewport viewport) {
        for (int i = slots.size() - 1; i >= 0; i--) {
            GraphSlot slot = slots.get(i);
            if (slot.hasLiveOwner() && viewport.toScreen(slot.card()).contains(screen)) {
                return slot.tag();
            }
        }
        return null;
    }

    /** Centre of a card's close button on screen, or null for no card. */
    public Point2D closeOf(GraphSlot slot, CanvasViewport viewport) {
        if (slot == null) {
            return null;
        }
        Rectangle2D card = viewport.toScreen(slot.card());
        return new Point2D.Double(card.getMaxX() - CLOSE_RADIUS - 5, card.getMinY() + CLOSE_RADIUS + 5);
    }

    /** The topmost card whose close button is at this screen point, or null. */
    public GraphSlot closeButtonAt(Point screen, CanvasViewport viewport) {
        for (int i = slots.size() - 1; i >= 0; i--) {
            Point2D close = closeOf(slots.get(i), viewport);
            if (close.distance(screen) <= CLOSE_RADIUS + 2) {
                return slots.get(i);
            }
        }
        return null;
    }

    /** Marks whether a live card covers this tag's slot; true if that changed anything. */
    public boolean setCovered(Object tag, boolean isCovered) {
        if (tag == null) {
            return false;
        }
        return isCovered ? covered.add(tag) : covered.remove(tag);
    }

    public boolean isCovered(Object tag) {
        return tag != null && covered.contains(tag);
    }

    /** The cards as they are now, for a memento. */
    public List<GraphSlot> snapshot() {
        return new ArrayList<>(slots);
    }

    /** Puts back cards from a memento (covered marks stay: they follow the live views). */
    public void restore(List<GraphSlot> saved) {
        slots.clear();
        slots.addAll(saved);
    }

    /** Replaces every card, e.g. when a saved canvas is opened; nothing is covered any more. */
    public void replaceAll(List<GraphSlot> next) {
        slots.clear();
        slots.addAll(next);
        covered.clear();
    }

    public void clear() {
        slots.clear();
    }
}
