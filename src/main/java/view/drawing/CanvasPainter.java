package view.drawing;

import entity.InkPoint;
import entity.Stroke;
import view.answer.AnswerInk;
import view.answer.CanvasAnswer;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Draws the canvas layers: grid, graph cards and their close buttons, pictures, ink, answers,
 * the selection with its handles, the lasso loop and the eraser outline.
 *
 * <p>Only drawing (Single Responsibility): each method gets what to draw as arguments and keeps no
 * state, so the canvas component decides the order and what is selected. "World" graphics already
 * have the viewport transform applied; the others draw in screen pixels.
 */
public final class CanvasPainter {
    public static final Color SELECTION = new Color(37, 99, 235);
    public static final Color SELECTION_HALO = new Color(37, 99, 235, 60);
    public static final Color ANSWER_ERROR = new Color(220, 38, 38);
    private static final Color ERASER_OUTLINE = new Color(100, 116, 139);
    private static final Color STROKE_ERASER_OUTLINE = new Color(220, 38, 38);
    private static final Color GRID_DOT = new Color(203, 213, 225);
    private static final Color GRAPH_CARD_BORDER = new Color(203, 213, 225);
    private static final int GRID_SPACING = 24;      // world units between grid dots
    private static final int HANDLE_SIZE = 8;        // selection corner handles, on screen

    private CanvasPainter() {
    }

    // ---------------------------------------------------------------- ink

    /** Round, antialiased, default black pen. */
    public static void setUpInk(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2.setStroke(new BasicStroke(InkModel.DEFAULT_WIDTH, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(InkModel.DEFAULT_COLOR);
    }

    public static void drawStroke(Graphics2D g2, Stroke stroke) {
        FormulaImageRenderer.trace(g2, stroke);
    }

    /** The round pen a stroke was written with, plus extra (for the selection halo). */
    public static BasicStroke penOf(InkModel ink, Stroke stroke, float extra) {
        return new BasicStroke(ink.widthOf(stroke) + extra, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    }

    /** Every stroke in its own colour and width. */
    public static void drawStrokes(Graphics2D g2, InkModel ink) {
        setUpInk(g2);
        for (Stroke stroke : ink.strokes()) {
            g2.setColor(ink.colorOf(stroke));
            g2.setStroke(penOf(ink, stroke, 0));
            drawStroke(g2, stroke);
        }
    }

    /** Every stroke except the skipped ones (the selection is drawn separately, on top). */
    public static void drawStrokesExcept(Graphics2D world, InkModel ink, Predicate<Stroke> skip) {
        setUpInk(world);
        for (Stroke stroke : ink.strokes()) {
            if (!skip.test(stroke)) {
                world.setColor(ink.colorOf(stroke));
                world.setStroke(penOf(ink, stroke, 0));  // each stroke at the pen width it was written with
                drawStroke(world, stroke);
            }
        }
    }

    public static void drawAnswer(Graphics2D world, CanvasAnswer a) {
        AnswerInk.paint(world, a, a.isError() ? ANSWER_ERROR : a.getColor(), a.getPenWidth());
    }

    public static void drawAnswers(Graphics2D world, Iterable<CanvasAnswer> answers) {
        for (CanvasAnswer a : answers) {
            drawAnswer(world, a);
        }
    }

    // ---------------------------------------------------------------- grid

    /** Light dots that move and zoom with the world, so you can see the canvas moving. */
    public static void paintGrid(Graphics2D g2, CanvasViewport viewport, int width, int height) {
        double spacing = GRID_SPACING * viewport.zoom();
        while (spacing < 14) {
            spacing *= 2;  // zoomed far out: fewer dots
        }
        double startX = floorMod(viewport.originX(), spacing);
        double startY = floorMod(viewport.originY(), spacing);
        g2.setColor(GRID_DOT);
        for (double x = startX; x < width; x += spacing) {
            for (double y = startY; y < height; y += spacing) {
                g2.fillRect((int) Math.round(x) - 1, (int) Math.round(y) - 1, 2, 2);
            }
        }
    }

    private static double floorMod(double a, double m) {
        return ((a % m) + m) % m;
    }

    // ---------------------------------------------------------------- graph cards

    /**
     * Each graph card in world coordinates: a soft shadow, a white rounded card, the picture inside.
     *
     * @param onScreen skip what a live card hides anyway (an export always draws the real picture)
     */
    public static void paintGraphCards(Graphics2D world, GraphCardLayer graphs, double zoom, boolean onScreen) {
        for (GraphSlot slot : graphs.all()) {
            paintGraphCard(world, slot, zoom, onScreen && graphs.isCovered(slot.tag()));
        }
    }

    private static void paintGraphCard(Graphics2D world, GraphSlot slot, double zoom, boolean covered) {
        Rectangle2D graphCard = slot.card();
        BufferedImage graphImage = slot.image();
        double graphPadding = slot.padding();
        Graphics2D g = (Graphics2D) world.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        double unit = 1 / zoom;  // one screen pixel in world units
        double arc = 16 * unit;
        for (int i = 1; i <= 5; i++) {
            g.setColor(new Color(15, 23, 42, 7));
            g.fill(new RoundRectangle2D.Double(graphCard.getX() - i * 0.6 * unit, graphCard.getY() + i * 0.8 * unit,
                    graphCard.getWidth() + i * 1.2 * unit, graphCard.getHeight() + i * 0.6 * unit, arc + i * unit, arc + i * unit));
        }
        if (covered) {
            g.dispose();
            return;   // shadow done, the live card draws the rest right on top
        }
        RoundRectangle2D card = new RoundRectangle2D.Double(graphCard.getX(), graphCard.getY(),
                graphCard.getWidth(), graphCard.getHeight(), arc, arc);
        g.setColor(Color.WHITE);
        g.fill(card);

        Graphics2D picture = (Graphics2D) g.create();
        picture.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        picture.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        picture.translate(graphCard.getX() + graphPadding, graphCard.getY() + graphPadding);
        picture.scale((graphCard.getWidth() - 2 * graphPadding) / graphImage.getWidth(),
                (graphCard.getHeight() - 2 * graphPadding) / graphImage.getHeight());
        picture.drawImage(graphImage, 0, 0, null);
        picture.dispose();

        g.setStroke(new BasicStroke((float) unit));
        g.setColor(GRAPH_CARD_BORDER);
        g.draw(card);
        g.dispose();
    }

    /** Each card's × button, on screen so it stays the same size at every zoom. */
    public static void paintGraphCloseButtons(Graphics2D g2, GraphCardLayer graphs, CanvasViewport viewport,
                                              GraphSlot hot) {
        for (GraphSlot slot : graphs.all()) {
            paintGraphClose(g2, graphs.closeOf(slot, viewport), slot == hot);
        }
    }

    private static void paintGraphClose(Graphics2D g2, Point2D c, boolean hot) {
        Graphics2D g = (Graphics2D) g2.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int r = GraphCardLayer.CLOSE_RADIUS;
        Ellipse2D circle = new Ellipse2D.Double(c.getX() - r, c.getY() - r, 2 * r, 2 * r);
        g.setColor(hot ? new Color(254, 226, 226) : new Color(255, 255, 255, 235));
        g.fill(circle);
        g.setStroke(new BasicStroke(1f));
        g.setColor(GRAPH_CARD_BORDER);
        g.draw(circle);
        g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(hot ? new Color(185, 28, 28) : new Color(71, 85, 105));
        double s = 3.8;
        g.draw(new Line2D.Double(c.getX() - s, c.getY() - s, c.getX() + s, c.getY() + s));
        g.draw(new Line2D.Double(c.getX() - s, c.getY() + s, c.getX() + s, c.getY() - s));
        g.dispose();
    }

    // ---------------------------------------------------------------- pictures

    /**
     * Pictures in world coordinates, each in the box {@code bounds} gives (a drag in progress
     * included). A History formula is typeset again at screen resolution when a typesetter is given.
     */
    public static void paintPictures(Graphics2D world, List<CanvasPicture> pictures,
                                     Function<CanvasPicture, Rectangle2D> bounds, double zoom,
                                     MathAssetRenderer typesetter) {
        for (CanvasPicture p : pictures) {
            Graphics2D g = (Graphics2D) world.create();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            Rectangle2D r = bounds.apply(p);
            BufferedImage source = p.image();
            if (typesetter != null && !p.latex().isBlank()) {
                int rasterW = Math.min(2400, Math.max(20, (int) Math.round(r.getWidth() * zoom)));
                int rasterH = Math.min(800, Math.max(16, (int) Math.round(r.getHeight() * zoom)));
                if (p.rendered() == null || Math.abs(p.rendered().getWidth() - rasterW) > 12
                        || Math.abs(p.rendered().getHeight() - rasterH) > 12) {
                    try {
                        // The PDF renderer intentionally paints on white paper; a Canvas
                        // material is an overlay and must keep its transparent background.
                        p.cacheRendered(transparentPaper(typesetter.render(p.latex(), p.result(),
                                rasterW, rasterH)));
                    } catch (RuntimeException badSource) {
                        p.cacheRendered(p.image());
                    }
                }
                source = p.rendered();
            }
            g.drawImage(source, (int) Math.round(r.getX()), (int) Math.round(r.getY()),
                    (int) Math.round(r.getWidth()), (int) Math.round(r.getHeight()), null);
            g.dispose();
        }
    }

    /**
     * Makes a white-backed formula image an antialiased transparent Canvas overlay.
     * Does not modify the PDF renderer or the source/serialized image.
     */
    public static BufferedImage transparentPaper(BufferedImage input) {
        if (input == null) throw new IllegalArgumentException("Rendered image required");
        int w = input.getWidth(), h = input.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int pixel = input.getRGB(x, y);
                int oldAlpha = (pixel >>> 24) & 255;
                int r = (pixel >>> 16) & 255, g = (pixel >>> 8) & 255, b = pixel & 255;
                int darkness = 255 - Math.min(r, Math.min(g, b));
                int a = oldAlpha * darkness / 255;
                // Un-premultiply the ink against white to keep antialiased edges dark.
                int rr = a == 0 ? 0 : Math.max(0, 255 - ((255 - r) * 255 / Math.max(1, darkness)));
                int gg = a == 0 ? 0 : Math.max(0, 255 - ((255 - g) * 255 / Math.max(1, darkness)));
                int bb = a == 0 ? 0 : Math.max(0, 255 - ((255 - b) * 255 / Math.max(1, darkness)));
                out.setRGB(x, y, (a << 24) | (rr << 16) | (gg << 8) | bb);
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- selection, lasso, eraser

    /** A dashed line that looks the same at every zoom. */
    private static BasicStroke dashed(double zoom) {
        float unit = (float) (1 / zoom);
        return new BasicStroke(1.2f * unit, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10f,
                new float[]{5f * unit, 4f * unit}, 0f);
    }

    /**
     * The selection as it looks right now: a halo behind each stroke and answer, the dashed outline
     * and the four resize handles.
     *
     * @param preview the selected strokes as they look mid-drag
     * @param picked  the same strokes as stored, in the same order (their pen widths live on these)
     * @param bounds  the selection outline, world coordinates
     */
    public static void paintSelection(Graphics2D world, InkModel ink, List<Stroke> preview, List<Stroke> picked,
                                      List<CanvasAnswer> answers, Rectangle bounds, double zoom) {
        Graphics2D halo = (Graphics2D) world.create();
        halo.setColor(SELECTION_HALO);
        for (int i = 0; i < preview.size(); i++) {
            halo.setStroke(penOf(ink, picked.get(i), 6));
            drawStroke(halo, preview.get(i));
        }
        setUpInk(halo);
        for (int i = 0; i < preview.size(); i++) {
            halo.setColor(ink.colorOf(preview.get(i)));
            halo.setStroke(penOf(ink, picked.get(i), 0));
            drawStroke(halo, preview.get(i));
        }
        halo.dispose();
        for (CanvasAnswer a : answers) {
            Rectangle2D box = a.getBox();
            Graphics2D glow = (Graphics2D) world.create();
            glow.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            glow.setColor(SELECTION_HALO);
            glow.fill(new RoundRectangle2D.Double(box.getX() - 4, box.getY() - 4, box.getWidth() + 8, box.getHeight() + 8, 8, 8));
            glow.dispose();
            drawAnswer(world, a);
        }

        world.setStroke(dashed(zoom));
        world.setColor(SELECTION);
        world.drawRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 8, 8);

        // corner handles for resizing, the same size on screen at every zoom
        double size = HANDLE_SIZE / zoom;
        world.setStroke(new BasicStroke((float) (1.5 / zoom)));
        for (int i = 0; i < 4; i++) {
            Point2D c = corner(bounds, i);
            Rectangle2D handle = new Rectangle2D.Double(c.getX() - size / 2, c.getY() - size / 2, size, size);
            world.setColor(Color.WHITE);
            world.fill(handle);
            world.setColor(SELECTION);
            world.draw(handle);
        }
    }

    /** Corner i of a box: 0 top-left, 1 top-right, 2 bottom-right, 3 bottom-left. */
    public static Point2D corner(Rectangle box, int i) {
        double x = i == 0 || i == 3 ? box.getMinX() : box.getMaxX();
        double y = i < 2 ? box.getMinY() : box.getMaxY();
        return new Point2D.Double(x, y);
    }

    /** The lasso loop being drawn, world coordinates. */
    public static void paintLasso(Graphics2D world, List<InkPoint> lassoPath, double zoom) {
        Path2D loop = new Path2D.Double();
        loop.moveTo(lassoPath.get(0).getX(), lassoPath.get(0).getY());
        for (InkPoint p : lassoPath) {
            loop.lineTo(p.getX(), p.getY());
        }
        loop.closePath();
        world.setColor(SELECTION_HALO);
        world.fill(loop);
        world.setStroke(dashed(zoom));
        world.setColor(SELECTION);
        world.draw(loop);
    }

    /** The eraser's outline around the pointer, on screen. */
    public static void paintEraser(Graphics2D g2, Point at, int radius, boolean precise) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setStroke(new BasicStroke(1.2f));
        g2.setColor(precise ? ERASER_OUTLINE : STROKE_ERASER_OUTLINE);
        g2.draw(new Ellipse2D.Double(at.x - radius, at.y - radius, 2 * radius, 2 * radius));
    }
}
