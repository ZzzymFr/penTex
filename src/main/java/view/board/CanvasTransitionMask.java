package view.board;

import javax.swing.JComponent;
import java.awt.Graphics;
import java.awt.Rectangle;

/** Clips the overscanned, live Canvas without reparenting it or changing its renderer. */
final class CanvasTransitionMask extends JComponent {
    private Rectangle viewport = new Rectangle();

    CanvasTransitionMask() { setOpaque(false); }

    void showViewport(Rectangle visible, int width, int height) {
        viewport = new Rectangle(visible);
        setBounds(0, 0, width, height);
        setVisible(true);
    }

    @Override public boolean contains(int x, int y) {
        return super.contains(x, y) && !viewport.contains(x, y);
    }

    @Override protected void paintComponent(Graphics g) {
        g.setColor(BoardTheme.CANVAS);
        g.fillRect(0, 0, Math.max(0, viewport.x), getHeight());
        int right = viewport.x + viewport.width;
        g.fillRect(right, 0, Math.max(0, getWidth() - right), getHeight());
    }
}
