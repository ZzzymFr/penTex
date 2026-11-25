package view.board;

import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.font.TextAttribute;
import java.util.List;
import java.util.Map;

// light theme from the glass demo; the one place for ui colours + type, glass and board share it
public final class BoardTheme {
    /** System blue: selection, primary buttons, focus. */
    public static final Color ACCENT = new Color(0x007AFF);
    public static final Color CANVAS = new Color(0xF2F2F7);
    public static final Color LABEL = new Color(0, 0, 0, 220);
    /** Secondary text; kept darker than Apple's grey since it often sits on clear glass. */
    public static final Color LABEL_SECONDARY = new Color(51, 65, 85);
    public static final Color LABEL_DISABLED = new Color(0, 0, 0, 70);
    public static final Color LABEL_SELECTED = ACCENT;
    public static final Color PLATTER = new Color(0, 0, 0, 18);
    public static final Color TINT = new Color(255, 255, 255, 38);
    public static final int GLOSS = 28;
    public static final int RIM_TOP = 230;
    public static final int RIM_BOTTOM = 110;
    public static final Color EDGE = new Color(0, 0, 0, 24);
    public static final float SHADOW = 0.12f;
    public static final Color HALO = new Color(242, 242, 247, 170);
    public static final Color PANEL = new Color(0xE9E9EF);
    public static final Color[] SWATCHES = {new Color(0x1C1C1E), new Color(255, 59, 48), new Color(0x007AFF),
        new Color(52, 199, 89), new Color(255, 149, 0)};

    public static final boolean MAC = System.getProperty("os.name", "").toLowerCase().startsWith("mac");
    public static final int TITLEBAR_H = 28;  // drag strip on mac
    public static final int TOP = 18;         // capsules from the top edge
    public static final int BOTTOM = 22;      // toolbar from the bottom edge
    public static final int SIDE = 18;
    public static final int HISTORY_LEFT = 80; // clear of the traffic lights

    private static final String FAMILY = pickFamily();

    private BoardTheme() {
    }

    private static String pickFamily() {
        String[] candidates = {"SF Pro Text", "SF Pro", "Helvetica Neue", "Segoe UI", "Inter"};
        try {
            List<String> installed = List.of(GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getAvailableFontFamilyNames());
            for (String c : candidates) {
                if (installed.contains(c)) {
                    return c;
                }
            }
        } catch (RuntimeException | Error ignored) {
            // no font list, use the default
        }
        return "Dialog";
    }

    public static Font font(float size) {
        return new Font(Map.of(TextAttribute.FAMILY, FAMILY, TextAttribute.SIZE, size,
                TextAttribute.WEIGHT, TextAttribute.WEIGHT_MEDIUM,
                TextAttribute.TRACKING, tracking(size)));
    }

    /**
     * Emphasis for titles and row names (Apple's emphasis is weight, not size). Java only maps
     * the bold face reliably on both platforms, semibold falls back to regular, so ask for bold.
     */
    public static Font semibold(float size) {
        return new Font(Map.of(TextAttribute.FAMILY, FAMILY, TextAttribute.SIZE, size,
                TextAttribute.WEIGHT, TextAttribute.WEIGHT_BOLD,
                TextAttribute.TRACKING, tracking(size)));
    }

    public static String family() {
        return FAMILY;
    }

    /**
     * Letter spacing by size, as a fraction of the point size: big text pulls in, small text
     * opens up a little to stay legible, body sits at 0.
     */
    public static float tracking(float size) {
        if (size >= 28f) return -0.02f;
        if (size >= 20f) return -0.012f;
        if (size >= 17f) return -0.005f;
        if (size <= 11f) return 0.012f;
        if (size <= 12.5f) return 0.006f;
        return 0f;
    }
}
