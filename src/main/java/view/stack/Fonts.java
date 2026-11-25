package view.stack;

import view.board.BoardTheme;

import java.awt.Font;
import java.awt.font.TextAttribute;
import java.util.Map;

/** The app's ui face (SF / Helvetica Neue on macOS, Segoe UI on Windows), tracked by size. */
public final class Fonts {
    private Fonts() {
    }

    public static Font ui(int style, float size) {
        Font font = new Font(BoardTheme.family(), style, 1);
        if (font.getFamily().equals(Font.DIALOG) && !BoardTheme.family().equals(Font.DIALOG)) {
            font = new Font(Font.SANS_SERIF, style, 1);  // asked-for face is missing
        }
        return font.deriveFont(Map.of(TextAttribute.SIZE, size,
                TextAttribute.TRACKING, BoardTheme.tracking(size)));
    }
}
