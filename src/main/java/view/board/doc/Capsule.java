package view.board.doc;

import javax.swing.*;
import java.awt.*;

/** Floating Apple-like controls: rounded capsules rather than full-width white strips. */
class Capsule extends JPanel {
    Capsule(java.awt.LayoutManager layout) {
        super(layout);
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
    }
    // a pill while it's one row high, a 14px card once it grows taller
    int arc() {
        return getHeight() - 3 <= 48 ? getHeight() - 3 : 16;
    }
    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int arc = arc();
        for (int i = 1; i <= 3; i++) {
            g2.setColor(new Color(0, 0, 0, 6));
            g2.fillRoundRect(0, i, getWidth() - 1, getHeight() - 3, arc, arc);
        }
        g2.setColor(Color.WHITE);
        g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 3, arc, arc);
        g2.setColor(new Color(0, 0, 0, 22));
        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 3, arc, arc);
        g2.dispose();
        super.paintComponent(g);
    }
}
