package view.board;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.Paint;
import java.awt.Point;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Floating glass capsule from the glass demo: segments, actions, swatches, separators, plus a grip
 * to drag the whole bar and an icon-only compact mode.
 *
 * <p>The backdrop is painted in {@code reference}'s coordinates; {@code version} changes whenever
 * it looks different, so cached renders know when they are stale.
 */
public class GlassBar extends JComponent {
    public static final int SEG_H = 40;
    public static final int PAD = 6;
    public static final int MARGIN = 28;   // room for shadow and hover lens
    public static final int CAP_H = SEG_H + 2 * PAD;
    static final int ICON = 18;
    static final int ICON_GAP = 7;
    static final int ITEM_PAD_X = 16;
    static final int COMPACT_W = 48;
    static final int GRIP_W = 20;
    private static final int SPLIT_CHEVRON_W = 24;
    private static final int SPLIT_LABEL_PADDING = 12;

    private static final float HOVER_MAG = 1.28f;
    private static final float HOVER_GROW_X = 24f;
    private static final float HOVER_GROW_Y = 8f;
    private static final int FRAME_MS = Motion.FRAME_MS;

    public enum Kind { SEGMENT, ACTION, SWATCH, SEPARATOR, GRIP }

    /** One thing in the bar. */
    public static final class Item {
        final Kind kind;
        String label;
        Icons.Glyph icon;
        final int group;
        final float base;   // natural width for fixed-size items
        float w;
        float x;
        Color color;
        boolean custom;
        int splitGroup = -1;
        BooleanSupplier onActivate = () -> true;  // false keeps the old selection
        BooleanSupplier enabled = () -> true;
        // hover-label mode: 0 = icon only, 1 = label shown; grows on a spring instead of popping
        final Spring reveal = new Spring(0.002f);
        float compactW;
        float labelW;

        Item(Kind kind, String label, Icons.Glyph icon, int group, float w) {
            this.kind = kind;
            this.label = label;
            this.icon = icon;
            this.group = group;
            this.base = w;
            this.w = w;
        }

        float center() {
            return x + w / 2f;
        }

        /** Visually connect related actions while retaining separate hit targets. */
        public void setSplitGroup(int group) { this.splitGroup = group; }

        public String getLabel() {
            return label;
        }

        /** View-only display update; call GlassBar.finish() to remeasure the capsule. */
        public void setDisplay(String label, Icons.Glyph icon) {
            this.label = label;
            this.icon = icon;
        }

        public Color getColor() {
            return color;
        }

        public void setColor(Color color) {
            this.color = color;
        }

        public void setEnabled(BooleanSupplier enabled) {
            this.enabled = enabled;
        }

        public boolean isEnabled() {
            return enabled.getAsBoolean();
        }
    }

    /** Drag from the grip, screen coords. */
    public interface GripListener {
        void pressed(Point screen);

        void dragged(Point screen);

        void released(Point screen);
    }

    private static final class Platter {
        final Spring x = new Spring();
        final Spring w = new Spring();
    }

    private final Supplier<Point> origin;
    private final Consumer<Graphics2D> backdrop;
    private final LongSupplier version;
    private final Font font;
    private final List<Item> items = new ArrayList<>();
    private final Map<Integer, Item> selected = new HashMap<>();
    private final Map<Integer, Platter> platters = new HashMap<>();
    private float capW;
    private final float capH = CAP_H;
    private boolean compact;
    private float squeeze = 1f;   // < 1 when even icons-only doesn't fit
    private boolean vertical;     // standing up on a side edge; drawn as a flipped horizontal bar
    private static final AffineTransform FLIP = new AffineTransform(0, 1, 1, 0, 0, 0);  // swaps x and y
    private GripListener gripListener;
    private boolean hoverToolLabels;
    private float hoverGrowthBudget = Float.MAX_VALUE;
    private Runnable hoverLayoutListener = () -> { };

    /** Compact tool icons expand their own label only while hovered. */
    public void setHoverToolLabels(boolean enabled, Runnable onLayout) {
        hoverToolLabels = enabled;
        hoverLayoutListener = Objects.requireNonNull(onLayout);
        finish();
    }


    private Item hoverItem;
    private Item pressItem;
    private boolean dragSwitched;
    private final Spring press = new Spring(0.002f);
    private float pressTarget;
    private float glow;
    private final Point2D.Float glowAt = new Point2D.Float();
    private float appear = 1f;
    private float appearTarget = 1f;
    private final Spring lensX = new Spring();
    private final Spring lensW = new Spring();
    private float lensAlpha;

    private final Timer timer = new Timer(FRAME_MS, e -> step());
    private final Motion.Clock clock = new Motion.Clock();
    private final GlassBackdropCache opticalBackdrop = new GlassBackdropCache(this::repaint);
    private final GlassLensRaster lensRaster = new GlassLensRaster();
    private Item pendingHover;
    private float hoverWait;
    private static final float HOVER_DWELL = .12f;

    public GlassBar(Font font, JComponent reference, Consumer<Graphics2D> backdrop, LongSupplier version) {
        this(font, (Supplier<Point>) null, backdrop, version, reference);
    }

    // origin = where this bar's top-left is in the backdrop's coords
    public GlassBar(Font font, Supplier<Point> origin, Consumer<Graphics2D> backdrop, LongSupplier version) {
        this(font, origin, backdrop, version, null);
    }

    private GlassBar(Font font, Supplier<Point> origin, Consumer<Graphics2D> backdrop, LongSupplier version,
                     JComponent reference) {
        this.font = font;
        this.origin = origin != null ? origin
                : () -> SwingUtilities.convertPoint(this, 0, 0, reference);
        this.backdrop = backdrop;
        this.version = version;
        setOpaque(false);
        setToolTipText("");
        MouseAdapter m = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) {
                    return;
                }
                pressItem = itemAt(mainOf(e));
                dragSwitched = false;
                glowAt.setLocation(mainOf(e), crossOf(e));
                glow = 1f;
                pressTarget = 1f;
                timer.start();
                if (pressItem != null && pressItem.kind == Kind.GRIP && gripListener != null) {
                    gripListener.pressed(e.getLocationOnScreen());
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                Item it = itemAt(mainOf(e));
                Item pressed = pressItem;
                pressItem = null;
                pressTarget = 0f;
                timer.start();
                if (pressed != null && pressed.kind == Kind.GRIP) {
                    if (gripListener != null) {
                        gripListener.released(e.getLocationOnScreen());
                    }
                    return;
                }
                if (!dragSwitched && it != null && it == pressed && contains(e.getPoint())) {
                    activate(it);
                }
                dragSwitched = false;
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (pressItem != null && pressItem.kind == Kind.GRIP) {
                    if (gripListener != null) {
                        gripListener.dragged(e.getLocationOnScreen());
                    }
                    return;
                }
                glowAt.setLocation(mainOf(e), crossOf(e));
                // drag along a segmented control switches live
                if (pressItem != null && pressItem.kind == Kind.SEGMENT) {
                    Item target = segmentNear(pressItem.group, mainOf(e));
                    if (target != null) {
                        hoverItem = pendingHover = target;
                        if (selected.get(target.group) != target && target.enabled.getAsBoolean()) {
                            activate(target);
                            dragSwitched = true;
                        }
                    }
                }
                timer.start();
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                int along = mainOf(e);
                Item next = itemAt(along);
                if (hoverItem != null && hoverItem == pendingHover && next != hoverItem) {
                    float local = along - MARGIN - PAD;
                    if (local >= hoverItem.x - 4 && local < hoverItem.x + hoverItem.w + 4) next = hoverItem;
                }
                if (next != pendingHover) {
                    pendingHover = next;
                    hoverWait = 0;
                    timer.start();
                }
                boolean onGrip = next != null && next.kind == Kind.GRIP;
                setCursor(Cursor.getPredefinedCursor(onGrip ? Cursor.MOVE_CURSOR : Cursor.DEFAULT_CURSOR));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                pendingHover = null;
                hoverItem = null;
                hoverWait = 0;
                timer.start();
            }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
    }

    // ---- building

    public Item segment(String label, Icons.Glyph icon, int group, BooleanSupplier onActivate) {
        Item it = new Item(Kind.SEGMENT, label, icon, group, 0);
        it.onActivate = onActivate;
        items.add(it);
        return it;
    }

    public Item action(String label, Icons.Glyph icon, Runnable r, BooleanSupplier enabled) {
        Item it = new Item(Kind.ACTION, label, icon, -1, 0);
        it.onActivate = () -> {
            r.run();
            return true;
        };
        it.enabled = enabled;
        items.add(it);
        return it;
    }

    public Item swatch(Color c, int group, BooleanSupplier onActivate) {
        Item it = new Item(Kind.SWATCH, null, null, group, 36);
        it.color = c;
        it.onActivate = onActivate;
        items.add(it);
        return it;
    }

    public Item customSwatch(int group, BooleanSupplier onActivate) {
        Item it = swatch(null, group, onActivate);
        it.custom = true;
        return it;
    }

    public void separator() {
        items.add(new Item(Kind.SEPARATOR, null, null, -1, 13));
    }

    public void grip(GripListener listener) {
        gripListener = listener;
        items.add(new Item(Kind.GRIP, null, Icons::grip, -1, GRIP_W));
    }

    // label shown only while hovered, and there's room for labels at all
    private boolean revealsLabel(Item it) {
        return hoverToolLabels && !compact && !vertical && it.label != null && it.splitGroup < 0
                && (it.kind == Kind.SEGMENT || it.kind == Kind.ACTION);
    }

    private float revealTarget(Item it) {
        return hoverItem == it && it.enabled.getAsBoolean() ? 1f : 0f;
    }

    /** Measures labels and lays the items out left to right. */
    public void finish() {
        FontRenderContext frc = new FontRenderContext(null, true, true);
        float x = 0;
        float growthScale = hoverGrowthScale(frc);
        boolean revealing = false;
        for (Item it : items) {
            if (revealsLabel(it)) {
                float text = (float) font.getStringBounds(it.label, frc).getWidth();
                it.compactW = Math.max(minWidth(it), COMPACT_W * squeeze);
                it.labelW = Math.max(73, 23 + ICON + ICON_GAP + text);
                if (!isShowing()) {
                    it.reveal.snap(revealTarget(it));
                }
                revealing |= it.reveal.x != revealTarget(it);
                it.w = it.compactW + (it.labelW - it.compactW) * clamp01(it.reveal.x) * growthScale;
            } else if (it.kind == Kind.SEGMENT || it.kind == Kind.ACTION) {
                it.reveal.snap(0f);
                if ((hoverToolLabels && hoverItem != it) || compact || vertical || it.label == null) {
                    it.w = Math.max(minWidth(it), (it.splitGroup >= 0 && it.label == null ? SPLIT_CHEVRON_W : COMPACT_W) * squeeze);
                } else {
                    float text = (float) font.getStringBounds(it.label, frc).getWidth();
                    it.w = it.splitGroup >= 0
                            ? ICON + ICON_GAP + text + SPLIT_LABEL_PADDING
                            : Math.max(hoverToolLabels ? 73 : 84,
                                    (hoverToolLabels ? 23 : ITEM_PAD_X * 2) + ICON + ICON_GAP + text);
                }
            } else {
                it.w = Math.max(minWidth(it), it.base * squeeze);
            }
            it.x = x;
            x += it.w;
        }
        capW = x + 2 * PAD;
        for (Map.Entry<Integer, Platter> e : platters.entrySet()) {
            Item t = selected.get(e.getKey());
            e.getValue().x.x = t.x;
            e.getValue().w.x = t.w;
        }
        setSize(getPreferredSize());
        if (revealing) {
            timer.start();
        }
        repaint();
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    // one frame of labels growing / shrinking: re-lay the items, platters ride along
    private boolean stepReveal(float dt) {
        boolean changed = false;
        boolean settled = true;
        for (Item it : items) {
            if (revealsLabel(it)) {
                float old = it.reveal.x;
                settled &= it.reveal.step(revealTarget(it), Motion.HOVER, dt);
                changed |= old != it.reveal.x;
            }
        }
        if (!changed) return settled;
        Map<Integer, float[]> before = new HashMap<>();
        for (Map.Entry<Integer, Platter> e : platters.entrySet()) {
            Item t = selected.get(e.getKey());
            before.put(e.getKey(), new float[]{t.x, t.w});
        }
        float x = 0;
        float growthScale = hoverGrowthScale(new FontRenderContext(null, true, true));
        for (Item it : items) {
            if (revealsLabel(it)) {
                it.w = it.compactW + (it.labelW - it.compactW) * clamp01(it.reveal.x) * growthScale;
            }
            it.x = x;
            x += it.w;
        }
        float newCap = x + 2 * PAD;
        for (Map.Entry<Integer, Platter> e : platters.entrySet()) {
            Item t = selected.get(e.getKey());
            float[] was = before.get(e.getKey());
            e.getValue().x.x += t.x - was[0];
            e.getValue().w.x += t.w - was[1];
        }
        if (Math.abs(newCap - capW) > 0.01f) {
            capW = newCap;
            setSize(getPreferredSize());
            hoverLayoutListener.run();
        }
        return settled;
    }

    // The resting capsule is centered; all hover expansion must fit on its right.
    // During a fast hover transition more than one spring may still be open.
    private float hoverGrowthScale(FontRenderContext frc) {
        float growth = 0;
        for (Item it : items) {
            if (revealsLabel(it)) {
                float label = Math.max(73, 23 + ICON + ICON_GAP
                        + (float) font.getStringBounds(it.label, frc).getWidth());
                float fraction = isShowing() ? clamp01(it.reveal.x) : revealTarget(it);
                growth += Math.max(0, label - Math.max(minWidth(it), COMPACT_W * squeeze)) * fraction;
            }
        }
        return growth > hoverGrowthBudget ? hoverGrowthBudget / growth : 1f;
    }

    // narrowest an item gets when squeezed
    private static float minWidth(Item it) {
        switch (it.kind) {
            case SEGMENT:
            case ACTION:
                return it.splitGroup >= 0 && it.label == null ? SPLIT_CHEVRON_W : ICON + 10;
            case SWATCH:
                return 20;
            case SEPARATOR:
                return 7;
            default:
                return 14;
        }
    }

    /**
     * Fits the capsule into maxCapW: labels first, then icons only, then every item narrower
     * together (height and icon size stay).
     */
    public void fitTo(int maxCapW) {
        boolean c = vertical || capsuleWidth(false) > maxCapW;  // standing up is icons only
        float previousBudget = hoverGrowthBudget;
        if (hoverToolLabels) {
            int resting = capsuleWidth(true);
            hoverGrowthBudget = Math.max(0f, (maxCapW - resting) / 2f);
            FontRenderContext frc = new FontRenderContext(null, true, true);
            float maxGrowth = 0;
            for (Item it : items) {
                if (it.label != null && it.splitGroup < 0 && (it.kind == Kind.SEGMENT || it.kind == Kind.ACTION)) {
                    float label = Math.max(73, 23 + ICON + ICON_GAP
                            + (float) font.getStringBounds(it.label, frc).getWidth());
                    maxGrowth = Math.max(maxGrowth, label - COMPACT_W);
                }
            }
            c = vertical || maxGrowth > hoverGrowthBudget;
        }
        float s = 1f;
        if (c) {
            float natural = capsuleWidth(true) - 2 * PAD;
            s = Math.max(0f, Math.min(1f, (maxCapW - 2 * PAD) / natural));
        }
        if (c != compact || Math.abs(s - squeeze) > 0.002f
                || (hoverToolLabels && previousBudget != hoverGrowthBudget)) {
            compact = c;
            squeeze = s;
            finish();
        }
    }

    public float getSqueeze() {
        return squeeze;
    }

    /** Icons only, labels go to the tooltip. */
    public void setCompact(boolean compact) {
        if (this.compact != compact) {
            this.compact = compact;
            finish();
        }
    }

    public boolean isCompact() {
        return compact;
    }

    /** Capsule width with or without labels, whatever the current mode. */
    public int capsuleWidth(boolean compactMode) {
        boolean was = compact;
        compact = compactMode;
        float x = 0;
        FontRenderContext frc = new FontRenderContext(null, true, true);
        for (Item it : items) {
            if (it.kind == Kind.SEGMENT || it.kind == Kind.ACTION) {
                x += (hoverToolLabels && hoverItem != it) || compact || it.label == null
                        ? (it.splitGroup >= 0 && it.label == null ? SPLIT_CHEVRON_W : COMPACT_W)
                        : (it.splitGroup >= 0
                        ? ICON + ICON_GAP + SPLIT_LABEL_PADDING
                                + (float) font.getStringBounds(it.label, frc).getWidth()
                        : Math.max(hoverToolLabels ? 73 : 84,
                                (hoverToolLabels ? 23 : ITEM_PAD_X * 2) + ICON + ICON_GAP
                                + (float) font.getStringBounds(it.label, frc).getWidth()));
            } else {
                x += it.base;  // natural width, not squeezed
            }
        }
        compact = was;
        return (int) Math.ceil(x + 2 * PAD);
    }

    public int capsuleWidth() {
        return (int) Math.ceil(capW);
    }

    /** Capsule width with every hover label folded away: where the bar rests. */
    public int restingCapsuleWidth() {
        float x = 0;
        for (Item it : items) {
            x += revealsLabel(it) ? it.compactW : it.w;
        }
        return (int) Math.ceil(x + 2 * PAD);
    }

    /** The visible capsule in this component's coords (standing up when vertical). */
    public java.awt.Rectangle capsuleBounds() {
        int len = (int) Math.ceil(capW);
        int thick = (int) capH;
        return vertical ? new java.awt.Rectangle(MARGIN, MARGIN, thick, len)
                : new java.awt.Rectangle(MARGIN, MARGIN, len, thick);
    }

    /** Centre of an item in this component's coords. */
    public Point centerOf(Item it) {
        int along = Math.round(MARGIN + PAD + it.center());
        int across = MARGIN + (int) capH / 2;
        return vertical ? new Point(across, along) : new Point(along, across);
    }

    @Override
    public Dimension getPreferredSize() {
        int along = (int) Math.ceil(capW) + 2 * MARGIN;
        int across = (int) capH + 2 * MARGIN;
        return vertical ? new Dimension(across, along) : new Dimension(along, across);
    }

    /** Stands the bar up (icons only, still upright) or lays it back down. */
    public void setVertical(boolean vertical) {
        if (this.vertical != vertical) {
            this.vertical = vertical;
            hoverItem = null;
            pendingHover = null;
            hoverWait = 0;
            finish();
        }
    }

    public boolean isVertical() {
        return vertical;
    }

    // along the bar / across it, in the flat layout's terms
    private int mainOf(MouseEvent e) {
        return vertical ? e.getY() : e.getX();
    }

    private int crossOf(MouseEvent e) {
        return vertical ? e.getX() : e.getY();
    }

    // size as if lying flat
    private int lw() {
        return vertical ? getHeight() : getWidth();
    }

    private int lh() {
        return vertical ? getWidth() : getHeight();
    }

    @Override
    public String getToolTipText(MouseEvent e) {
        Item it = itemAt(mainOf(e));
        if (it == null || it.label == null || !contains(e.getPoint())) {
            return null;
        }
        return compact || vertical || (hoverToolLabels && hoverItem != it) ? it.label : null;
    }

    // ---- interaction

    /** Same as a click on the item. */
    public void activate(Item it) {
        if (!it.enabled.getAsBoolean()) {
            return;
        }
        if (it.kind == Kind.SEGMENT || it.kind == Kind.SWATCH) {
            if (it.onActivate.getAsBoolean()) {
                select(it);
            }
        } else if (it.kind == Kind.ACTION) {
            it.onActivate.getAsBoolean();
        }
        repaint();
    }

    public void select(Item it) {
        selected.put(it.group, it);
        if (it.kind == Kind.SEGMENT && !platters.containsKey(it.group)) {
            Platter p = new Platter();
            p.x.x = it.x;
            p.w.x = it.w;
            platters.put(it.group, p);
        }
        timer.start();
    }

    public void deselect(int group) {
        selected.remove(group);
        platters.remove(group);
        timer.start();
        repaint();
    }

    public boolean isSelected(Item it) {
        return selected.get(it.group) == it;
    }

    public void setShown(boolean shown) {
        appearTarget = shown ? 1f : 0f;
        if (!shown) {
            hoverItem = pendingHover = null;
            hoverWait = 0;
        }
        timer.start();
    }

    private Item itemAt(int x) {
        float lx = x - MARGIN - PAD;
        for (Item it : items) {
            if (it.kind != Kind.SEPARATOR && lx >= it.x && lx < it.x + it.w) {
                return it;
            }
        }
        return null;
    }

    private Item segmentNear(int group, int x) {
        float lx = x - MARGIN - PAD;
        Item best = null;
        float bestDist = Float.MAX_VALUE;
        for (Item it : items) {
            if (it.kind != Kind.SEGMENT || it.group != group) {
                continue;
            }
            float d = lx < it.x ? it.x - lx : lx >= it.x + it.w ? lx - (it.x + it.w) : 0f;
            if (d < bestDist) {
                bestDist = d;
                best = it;
            }
        }
        return best;
    }

    // only the capsule takes clicks, the margin passes through
    @Override
    public boolean contains(int x, int y) {
        return appearTarget > 0f && (vertical ? capsule(0).contains(y, x) : capsule(0).contains(x, y));
    }

    private RoundRectangle2D.Float capsule(float grow) {
        float w = capW + 2 * grow;
        float h = capH + 2 * grow;
        return new RoundRectangle2D.Float(MARGIN - grow, MARGIN - grow, w, h, h, h);
    }

    private void step() {
        float dt = clock.tick();
        float f = dt * 1000f / FRAME_MS;  // in 16ms frames, for the plain fades below
        boolean settled = true;
        if (pendingHover != hoverItem && pressItem == null) {
            hoverWait += dt;
            if (pendingHover == null || !isShowing() || Motion.reduced() || hoverWait >= HOVER_DWELL) {
                hoverItem = pendingHover;
            } else {
                settled = false;
            }
        }
        for (Map.Entry<Integer, Platter> e : platters.entrySet()) {
            Item t = selected.get(e.getKey());
            settled &= e.getValue().x.step(t.x, Motion.SELECT, dt);
            settled &= e.getValue().w.step(t.w, Motion.SELECT, dt);
        }
        settled &= press.step(pressTarget, Motion.PRESS, dt);
        settled &= stepReveal(dt);
        if (pressTarget == 0f) {
            glow *= (float) Math.pow(0.86, f);
            if (glow < 0.01f) {
                glow = 0;
            } else {
                settled = false;
            }
        }
        appear += (appearTarget - appear) * ease(0.22f, f);
        if (Math.abs(appearTarget - appear) < 0.01f) {
            appear = appearTarget;
        } else {
            settled = false;
        }

        Item h = hoverItem != null && hoverItem.enabled.getAsBoolean() && hoverItem.kind != Kind.GRIP
                && appearTarget > 0 ? hoverItem : null;
        float alphaTarget = h != null ? 1f : 0f;
        if (h != null) {
            float cx = h.center();
            float w = h.w + HOVER_GROW_X;
            if (h.splitGroup >= 0) {
                float left = h.x, right = h.x + h.w;
                for (Item other : items) if (other.splitGroup == h.splitGroup) {
                    left = Math.min(left, other.x);
                    right = Math.max(right, other.x + other.w);
                }
                cx = (left + right) / 2f;
                w = right - left + HOVER_GROW_X + 10f; // Keep icons clear of the refracting rim.
            }
            if (lensAlpha < 0.05f) {
                lensX.x = cx;
                lensW.x = w;
                lensX.v = 0;
                lensW.v = 0;
            }
            settled &= lensX.step(cx, Motion.HOVER, dt);
            settled &= lensW.step(w, Motion.HOVER, dt);
        }
        lensAlpha += (alphaTarget - lensAlpha) * ease(0.25f, f);
        if (Math.abs(alphaTarget - lensAlpha) < 0.01f) {
            lensAlpha = alphaTarget;
        } else {
            settled = false;
        }

        if (settled) {
            timer.stop();
            clock.reset();
        }
        repaint();
    }

    @Override public void removeNotify() {
        timer.stop(); clock.reset(); opticalBackdrop.clear(); lensRaster.clear();
        hoverItem = pendingHover = null; hoverWait = 0; lensAlpha = 0;
        super.removeNotify();
    }

    static float ease(float k, float f) {
        return 1f - (float) Math.pow(1f - k, f);
    }

    // ---- painting

    @Override
    protected void paintComponent(Graphics g) {
        if (appear <= 0f || capW <= 0) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        if (vertical) g2.transform(FLIP);
        hints(g2);
        if (appear < 1f) g2.setComposite(AlphaComposite.SrcOver.derive(appear));
        RoundRectangle2D.Float cap = capsule(press.x * 3f);
        double deviceScale = Math.hypot(g2.getTransform().getScaleX(), g2.getTransform().getShearY());
        GlassBackdropCache.Sample sample = opticalBackdrop.region(lw(), lh(), vertical, origin(),
                version.getAsLong(), timer.isRunning(), deviceScale, this::captureBackdrop);
        paintFace(g2, sample, cap);
        float scale = cap.width / capW;
        paintForeground(g2, cap, scale);
        if (lensAlpha > .01f) {
            RoundRectangle2D.Float lens = lensShape();
            // Restore the original magnifying, dispersive droplet. Only its small
            // local optical region is rasterized; the rest of the bar stays vector.
            Graphics2D optical = (Graphics2D) g2.create();
            float base = optical.getComposite() instanceof AlphaComposite ac ? ac.getAlpha() : 1f;
            optical.setComposite(AlphaComposite.SrcOver.derive(base * lensAlpha));
            lensRaster.paint(optical, lens, sample, sceneVersion(), deviceScale, HOVER_MAG,
                    scene -> {
                        sample.paint(scene);
                        paintFace(scene, sample, cap);
                        paintForeground(scene, cap, scale);
                    });
            optical.setPaint(new GradientPaint(0, lens.y, new Color(255,255,255,40),
                    0, lens.y + lens.height * .5f, new Color(255,255,255,0)));
            optical.fill(lens);
            optical.setStroke(new BasicStroke(1.6f));
            optical.setPaint(rimPaint(lens, 255, 170));
            optical.draw(inset(lens, 1.3f));
            optical.dispose();
        }
        g2.dispose();
    }

    private void paintFace(Graphics2D g2, GlassBackdropCache.Sample sample, RoundRectangle2D.Float cap) {
        Graphics2D face = (Graphics2D) g2.create();
        face.clip(cap);
        sample.paint(face);
        face.setColor(BoardTheme.TINT);
        face.fill(cap);
        face.setPaint(new GradientPaint(0, cap.y, new Color(255, 255, 255, BoardTheme.GLOSS),
                0, cap.y + cap.height * .55f, new Color(255, 255, 255, 0)));
        face.fill(cap);
        float scale = cap.width / capW;
        for (Platter p : platters.values()) {
            face.setColor(BoardTheme.PLATTER);
            face.fill(slot(p.x.x, p.w.x, cap, scale));
        }
        if (glow > .01f) {
            face.setPaint(new RadialGradientPaint(glowAt, 70f, new float[]{0f,1f},
                    new Color[]{new Color(1f,1f,1f,.20f*glow), new Color(1f,1f,1f,0f)}));
            face.fill(cap);
        }
        face.dispose();
        g2.setStroke(new BasicStroke(1.4f));
        g2.setPaint(rimPaint(cap, BoardTheme.RIM_TOP, BoardTheme.RIM_BOTTOM));
        g2.draw(inset(cap, 1.2f));
    }

    /** Only actual local scene changes invalidate the lens raster; alpha does not. */
    private long sceneVersion() {
        long h = Objects.hash(capW, press.x, glow, glowAt.x, glowAt.y, squeeze, vertical, compact);
        for (Platter p : platters.values()) h = h * 31 + Objects.hash(p.x.x, p.w.x);
        for (Item it : items) h = h * 31 + Objects.hash(it.x, it.w, it.reveal.x, it.label,
                it.icon, it.color, it.custom, it.enabled.getAsBoolean(), selected.get(it.group) == it,
                hoverItem == it);
        return h;
    }

    // where this bar sits in the backdrop's coords
    private Point origin() { return origin.get(); }

    static void hints(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    }

    // Capture a fixed reference-space rectangle, not the currently animated size.
    private BufferedImage captureBackdrop(Rectangle bounds, double scale) {
        BufferedImage raw = new BufferedImage(Math.max(1, (int)Math.ceil(bounds.width * scale)),
                Math.max(1, (int)Math.ceil(bounds.height * scale)), BufferedImage.TYPE_INT_ARGB);
        Graphics2D bg = raw.createGraphics();
        bg.scale(scale, scale);
        bg.setColor(BoardTheme.CANVAS);
        bg.fillRect(0, 0, bounds.width, bounds.height);
        bg.translate(-bounds.x, -bounds.y);
        bg.clip(bounds);
        backdrop.accept(bg);
        bg.dispose();
        if (!vertical) return raw;
        BufferedImage flipped = new BufferedImage(raw.getHeight(), raw.getWidth(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D fg = flipped.createGraphics();
        fg.drawImage(raw, FLIP, null);
        fg.dispose();
        return flipped;
    }

    private static Paint rimPaint(RoundRectangle2D.Float r, int top, int bottom) {
        return new LinearGradientPaint(r.x, r.y, r.x + r.width * 0.35f, r.y + r.height * 2.2f,
                new float[]{0f, 0.35f, 0.7f, 1f},
                new Color[]{new Color(255, 255, 255, top), new Color(255, 255, 255, 0),
                    new Color(255, 255, 255, 0), new Color(255, 255, 255, bottom)});
    }

    private void paintForeground(Graphics2D g2, RoundRectangle2D.Float cap, float scale) {
        g2.setFont(font);
        FontMetrics fm = g2.getFontMetrics();
        for (Item it : items) {
            paintItemForeground(g2, cap, scale, fm, it);
        }
        // Split actions share a single glass hover lens and have adjacent hit areas.
        // A hairline at their common boundary is decorative only, never a click target.
        g2.setColor(BoardTheme.LABEL_DISABLED);
        g2.setStroke(new BasicStroke(Math.max(0.8f, scale)));
        for (int i = 1; i < items.size(); i++) {
            Item left = items.get(i - 1);
            Item right = items.get(i);
            if (left.splitGroup >= 0 && left.splitGroup == right.splitGroup) {
                Rectangle2D boundary = slot(right.x, right.w, cap, scale).getBounds2D();
                double x = boundary.getMinX();
                double cy = boundary.getCenterY();
                g2.draw(new Line2D.Double(x, cy - 9 * scale, x, cy + 9 * scale));
            }
        }
    }

    private void paintItemForeground(Graphics2D g2, RoundRectangle2D.Float cap, float scale,
                                     FontMetrics fm, Item it) {
        Rectangle2D b = slot(it.x, it.w, cap, scale).getBounds2D();
            if (it.kind == Kind.SEGMENT || it.kind == Kind.ACTION) {
                Color c = !it.enabled.getAsBoolean() ? BoardTheme.LABEL_DISABLED
                        : selected.get(it.group) == it ? BoardTheme.LABEL_SELECTED : BoardTheme.LABEL;
                g2.setColor(c);
                float r = revealsLabel(it) ? clamp01(it.reveal.x) : -1f;
                boolean iconOnly = r >= 0 ? r < 0.01f
                        : (hoverToolLabels && hoverItem != it) || compact || vertical || it.label == null;
                float textW = iconOnly ? 0 : fm.stringWidth(it.label);
                double total = iconOnly ? ICON * scale
                        : (ICON + ICON_GAP) * scale + textW;
                if (r >= 0 && !iconOnly) {
                    total = ICON * scale + ((ICON_GAP * scale) + textW) * r;  // icon eases left as the word arrives
                }
                double x0 = b.getCenterX() - total / 2;
                AffineTransform t = g2.getTransform();
                g2.translate(x0 + ICON * scale / 2, b.getCenterY());
                if (vertical) {
                    g2.transform(FLIP);  // flipped twice = upright
                }
                g2.scale(scale * ICON / 20.0, scale * ICON / 20.0);
                it.icon.paint(g2);
                g2.setTransform(t);
                if (!iconOnly) {
                    Graphics2D tg = (Graphics2D) g2.create();
                    if (r >= 0) {
                        tg.clip(b);
                        java.awt.Composite base = tg.getComposite();
                        float baseAlpha = base instanceof AlphaComposite ac ? ac.getAlpha() : 1f;
                        tg.setComposite(AlphaComposite.SrcOver.derive(baseAlpha * r * r));
                    }
                    tg.drawString(it.label, (float) (x0 + (ICON + ICON_GAP) * scale),
                            (float) (b.getCenterY() - fm.getHeight() / 2.0 + fm.getAscent()));
                    tg.dispose();
                }
            } else if (it.kind == Kind.SWATCH) {
                paintSwatch(g2, it, b.getCenterX(), b.getCenterY(), scale);
            } else if (it.kind == Kind.SEPARATOR) {
                g2.setColor(BoardTheme.EDGE);
                g2.setStroke(new BasicStroke(1f));
                double h = 20 * scale;
                g2.draw(new Line2D.Double(b.getCenterX(), b.getCenterY() - h / 2,
                        b.getCenterX(), b.getCenterY() + h / 2));
            } else if (it.kind == Kind.GRIP) {
                g2.setColor(BoardTheme.LABEL_DISABLED);
                AffineTransform t = g2.getTransform();
                g2.translate(b.getCenterX() + 2 * scale, b.getCenterY());
                g2.scale(scale, scale);
                it.icon.paint(g2);
                g2.setTransform(t);
            }
    }

    private void paintSwatch(Graphics2D g2, Item it, double cx, double cy, float scale) {
        double d = Math.min(22, it.w - 8) * scale;  // dot shrinks with a squeezed bar
        Ellipse2D dot = new Ellipse2D.Double(cx - d / 2, cy - d / 2, d, d);
        if (it.custom) {
            for (int i = 0; i < 24; i++) {
                g2.setColor(Color.getHSBColor(i / 24f, 0.75f, 0.95f));
                g2.fill(new Arc2D.Double(dot.getBounds2D(), 90 - i * 15, -15.5, Arc2D.PIE));
            }
            double di = d - 7 * scale;
            g2.setColor(it.color != null ? it.color : BoardTheme.CANVAS);
            g2.fill(new Ellipse2D.Double(cx - di / 2, cy - di / 2, di, di));
        } else {
            g2.setColor(it.color);
            g2.fill(dot);
        }
        g2.setColor(BoardTheme.EDGE);
        g2.setStroke(new BasicStroke(1f));
        g2.draw(dot);
        if (selected.get(it.group) == it) {
            double dr = d + 8 * scale;
            g2.setColor(BoardTheme.LABEL_SELECTED);
            g2.setStroke(new BasicStroke(2f));
            g2.draw(new Ellipse2D.Double(cx - dr / 2, cy - dr / 2, dr, dr));
        }
    }

    private RoundRectangle2D.Float lensShape() {
        float lh = capH + 2 * HOVER_GROW_Y + press.x * 4f;
        float lw = Math.max(lh, lensW.x + Math.min(Math.abs(lensX.v) * 1.2f, 28f) + press.x * 4f);
        float lcx = MARGIN + PAD + lensX.x;
        float lcy = MARGIN + capH / 2f;
        return new RoundRectangle2D.Float(lcx - lw / 2, lcy - lh / 2, lw, lh, lh, lh);
    }

    private RoundRectangle2D.Float slot(float x, float w, RoundRectangle2D.Float cap, float scale) {
        float sx = cap.x + (PAD + x) * scale;
        float sy = cap.y + PAD * scale;
        float sw = w * scale;
        float sh = SEG_H * scale;
        return new RoundRectangle2D.Float(sx, sy, sw, sh, sh, sh);
    }

    private static RoundRectangle2D.Float inset(RoundRectangle2D.Float r, float d) {
        return new RoundRectangle2D.Float(r.x + d, r.y + d, r.width - 2 * d, r.height - 2 * d,
                r.arcwidth - 2 * d, r.archeight - 2 * d);
    }

}
