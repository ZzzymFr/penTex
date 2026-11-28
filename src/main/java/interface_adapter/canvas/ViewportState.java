package interface_adapter.canvas;

/**
 * Where the view looks at the infinite canvas: {@code screen = world * zoom + origin}.
 * Immutable, so a command can keep the state before and after it changed it.
 */
public record ViewportState(double zoom, double originX, double originY) {

    public static final ViewportState IDENTITY = new ViewportState(1, 0, 0);

    public ViewportState {
        if (!(zoom > 0) || Double.isInfinite(zoom)) {
            throw new IllegalArgumentException("Zoom must be a positive number");
        }
    }

    /**
     * Zooms by {@code factor}, clamped to [{@code min}, {@code max}], keeping the world point under
     * the screen point (screenX, screenY) where it is. Returns this state when the zoom cannot change.
     */
    public ViewportState zoomedAt(double screenX, double screenY, double factor, double min, double max) {
        double next = Math.max(min, Math.min(max, zoom * factor));
        if (next == zoom) {
            return this;
        }
        double worldX = (screenX - originX) / zoom;
        double worldY = (screenY - originY) / zoom;
        return new ViewportState(next, screenX - worldX * next, screenY - worldY * next);
    }

    /** Moved by (dx, dy) screen pixels. */
    public ViewportState pannedBy(double dx, double dy) {
        return new ViewportState(zoom, originX + dx, originY + dy);
    }

    public double toWorldX(double screenX) {
        return (screenX - originX) / zoom;
    }

    public double toWorldY(double screenY) {
        return (screenY - originY) / zoom;
    }

    public double toScreenX(double worldX) {
        return worldX * zoom + originX;
    }

    public double toScreenY(double worldY) {
        return worldY * zoom + originY;
    }
}
