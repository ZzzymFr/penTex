package view.board;

import javax.swing.JComponent;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

// picture following the mouse while a history row is dragged
final class Ghost extends JComponent {
    private BufferedImage picture;
    private BufferedImage graphThumbnail;
    private BufferedImage scaledGraphGhost;
    private Rectangle outline;
    private Rectangle previewClip;

    Ghost() { setOpaque(false); }
    private Point at;

    void showOutline(Rectangle bounds) { showGraphPreview(null, bounds, null); }

    void showGraphPreview(BufferedImage image, Rectangle bounds, Rectangle clip) {
        if (graphThumbnail == image && java.util.Objects.equals(outline, bounds)
                && java.util.Objects.equals(previewClip, clip)) return;
        Rectangle before = outline, oldClip = previewClip;
        if (image != graphThumbnail || bounds == null || before == null
                || before.width != bounds.width || before.height != bounds.height) {
            scaledGraphGhost = scaledGhost(image, bounds);
        }
        outline = bounds == null ? null : new Rectangle(bounds);
        graphThumbnail = image;
        previewClip = clip == null ? null : new Rectangle(clip);
        repaintOutline(before, oldClip);
        repaintOutline(outline, previewClip);
    }

    void clearOutline() { showGraphPreview(null, null, null); }

    private void repaintOutline(Rectangle bounds, Rectangle clip) {
        if (bounds == null) return;
        Rectangle dirty = new Rectangle(bounds);
        dirty.grow(4, 4);
        if (clip != null) dirty = dirty.intersection(clip);
        if (!dirty.isEmpty()) repaint(dirty.x, dirty.y, dirty.width, dirty.height);
    }

    /** Rasterize the translucent ghost only when its source or dimensions change.
     * Mouse movement at a fixed zoom is then just a clipped image blit.
     */
    private static BufferedImage scaledGhost(BufferedImage source, Rectangle bounds) {
        if (source == null || bounds == null || bounds.isEmpty()) return null;
        double fit = Math.min((double) bounds.width / source.getWidth(),
                (double) bounds.height / source.getHeight());
        int w = Math.max(1, (int)Math.round(source.getWidth() * fit));
        int h = Math.max(1, (int)Math.round(source.getHeight() * fit));
        // Guard against unbounded allocations while retaining the precise placement outline.
        double cap = Math.min(1.0, 1800.0 / Math.max(w, h));
        w = Math.max(1, (int)Math.round(w * cap));
        h = Math.max(1, (int)Math.round(h * cap));
        BufferedImage raster = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = raster.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setComposite(AlphaComposite.SrcOver.derive(.55f));
            // Preserve the previous rounded-outline clipping, but do it only once.
            g.clip(new java.awt.geom.RoundRectangle2D.Float(
                    -(bounds.width - w) / 2f, -(bounds.height - h) / 2f,
                    bounds.width, bounds.height, 12, 12));
            g.drawImage(source, 0, 0, w, h, null);
        } finally { g.dispose(); }
        return raster;
    }

    void showPicture(BufferedImage picture, Point at) {
        java.awt.Rectangle previous = dirtyBounds();
        this.picture = picture;
        this.at = at;
        repaintDirty(previous);
        repaintDirty(dirtyBounds());
    }

    void clear() {
        if (picture == null) return;
        java.awt.Rectangle previous = dirtyBounds();
        picture = null;
        at = null;
        repaintDirty(previous);
        if (getParent() != null && previous != null) getParent().repaint(
                previous.x, previous.y, previous.width, previous.height);
    }

    private java.awt.Rectangle dirtyBounds() {
        if (picture == null || at == null) return null;
        return new java.awt.Rectangle(at.x - 3, at.y - 3,
                Math.max(1, picture.getWidth() / 2) + 6,
                Math.max(1, picture.getHeight() / 2) + 6);
    }

    private void repaintDirty(java.awt.Rectangle bounds) {
        if (bounds != null) repaint(bounds.x, bounds.y, bounds.width, bounds.height);
    }

    @Override
    public boolean contains(int x, int y) {
        return false;  // never takes the mouse
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (picture == null && outline == null) return;
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (picture != null) {
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2.setComposite(AlphaComposite.SrcOver.derive(0.8f));
                g2.drawImage(picture, at.x, at.y, picture.getWidth() / 2, picture.getHeight() / 2, null);
            }
            if (outline != null) {
                java.awt.Shape originalClip = g2.getClip();
                if (previewClip != null) g2.clip(previewClip);
                java.awt.Shape pdfClip = g2.getClip();
                if (scaledGraphGhost != null) {
                    int w = scaledGraphGhost.getWidth(), h = scaledGraphGhost.getHeight();
                    g2.setComposite(AlphaComposite.SrcOver);
                    g2.drawImage(scaledGraphGhost, outline.x + (outline.width - w) / 2,
                            outline.y + (outline.height - h) / 2, null);
                    g2.setClip(pdfClip);
                    g2.setComposite(AlphaComposite.SrcOver);
                    g2.setColor(new Color(49, 119, 227, 175));
                    g2.setStroke(new java.awt.BasicStroke(1.2f, java.awt.BasicStroke.CAP_BUTT,
                            java.awt.BasicStroke.JOIN_ROUND, 10f, new float[]{5f, 5f}, 0f));
                    g2.drawRoundRect(outline.x, outline.y, outline.width, outline.height, 12, 12);
                } else {
                    // Legacy callers without a thumbnail still get visible feedback.
                    g2.setComposite(AlphaComposite.SrcOver);
                    g2.setColor(new Color(49, 119, 227, 180));
                    g2.setStroke(new java.awt.BasicStroke(1.4f, java.awt.BasicStroke.CAP_BUTT,
                            java.awt.BasicStroke.JOIN_ROUND, 10f, new float[]{6f, 4f}, 0f));
                    g2.drawRect(outline.x, outline.y, outline.width, outline.height);
                }
                g2.setClip(originalClip);
            }
        } finally {
            g2.dispose();
        }
    }
}
