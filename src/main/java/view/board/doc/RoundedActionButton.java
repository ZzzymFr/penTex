package view.board.doc;

import view.board.BoardTheme;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

/** Readable rounded buttons with a real opaque fill; unlike platform LAF translucent defaults. */
final class RoundedActionButton extends JButton {
    private final boolean primary;
    boolean quiet;
    private boolean over;
    RoundedActionButton(String text, Runnable action, boolean primary) {
        super(text);this.primary = primary;
        setFont(BoardTheme.font(12.5f));
        setFocusable(false);setContentAreaFilled(false);setOpaque(false);setBorderPainted(false);
        setBorder(BorderFactory.createEmptyBorder(8, 14, 8, 14));
        setRolloverEnabled(true);
        addActionListener(e -> action.run());
        addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {over = true;repaint();}
            @Override public void mouseExited(MouseEvent e) {over = false;repaint();}
        });
    }
    private view.glass.AppleLook.Tone tone() {
        if (primary) return view.glass.AppleLook.Tone.PRIMARY;
        if (getModel().isSelected()) return view.glass.AppleLook.Tone.TINTED;
        return quiet ? view.glass.AppleLook.Tone.QUIET : view.glass.AppleLook.Tone.SECONDARY;
    }
    @Override protected void paintComponent(Graphics g) {
        int w = getWidth(), h = getHeight();
        boolean pressed = getModel().isArmed() && getModel().isPressed();
        view.glass.AppleLook.paintButton((Graphics2D) g, w, h, h, tone(), over, pressed, isEnabled());
        setForeground(view.glass.AppleLook.buttonText(tone(), isEnabled()));
        super.paintComponent(g);
    }
}
