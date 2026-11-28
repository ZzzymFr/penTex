package view.board;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

// mac: the canvas runs under a clear title bar, this strip moves the window
final class TitleStrip extends JComponent {
    private Point from;
    private Point windowFrom;

    TitleStrip() {
        MouseAdapter m = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                Window w = SwingUtilities.getWindowAncestor(TitleStrip.this);
                if (!SwingUtilities.isLeftMouseButton(e) || w == null) {
                    return;
                }
                if (e.getClickCount() == 2 && w instanceof Frame) {
                    Frame f = (Frame) w;
                    f.setExtendedState(f.getExtendedState() ^ Frame.MAXIMIZED_BOTH);
                    from = null;
                    return;
                }
                from = e.getLocationOnScreen();
                windowFrom = w.getLocation();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                Window w = SwingUtilities.getWindowAncestor(TitleStrip.this);
                if (from == null || w == null) {
                    return;
                }
                Point now = e.getLocationOnScreen();
                w.setLocation(windowFrom.x + now.x - from.x, windowFrom.y + now.y - from.y);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                from = null;
            }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
    }
}
