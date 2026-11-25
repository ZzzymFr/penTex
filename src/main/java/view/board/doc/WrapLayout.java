package view.board.doc;

import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;

// FlowLayout that grows taller when its row wraps
public class WrapLayout extends FlowLayout {
    private final java.util.Map<Container, Integer> askedAtWidth = new java.util.WeakHashMap<>();
    public WrapLayout(int align, int hgap, int vgap) {
        super(align, hgap, vgap);
    }

    @Override
    public Dimension preferredLayoutSize(Container target) {
        return size(target, true);
    }

    // its height was worked out before it had a real width (e.g. PDF panel opening): once laid out
    // at the real width, ask for the right height again instead of staying a tall empty card
    @Override
    public void layoutContainer(Container target) {
        super.layoutContainer(target);
        if (target.getWidth() <= 0) {
            return;
        }
        int wanted = preferredLayoutSize(target).height;
        // once per width, so a parent that stretches us can't make this loop
        if (wanted != target.getHeight() && !java.util.Objects.equals(askedAtWidth.put(target, target.getWidth()), target.getWidth())) {
            SwingUtilities.invokeLater(() -> {
                if (target.getWidth() > 0 && preferredLayoutSize(target).height != target.getHeight()) {
                    target.revalidate();
                }
            });
        }
    }

    @Override
    public Dimension minimumLayoutSize(Container target) {
        Dimension d = size(target, false);
        d.width -= getHgap() + 1;
        return d;
    }

    private Dimension size(Container target, boolean preferred) {
        synchronized (target.getTreeLock()) {
            int targetWidth = target.getSize().width;
            Container c = target;
            while (c.getSize().width == 0 && c.getParent() != null) {
                c = c.getParent();
            }
            targetWidth = c.getSize().width;
            if (targetWidth == 0) {
                targetWidth = Integer.MAX_VALUE;
            }
            Insets in = target.getInsets();
            int maxWidth = targetWidth - (in.left + in.right + getHgap() * 2);
            Dimension dim = new Dimension(0, 0);
            int rowWidth = 0;
            int rowHeight = 0;
            for (Component m : target.getComponents()) {
                if (!m.isVisible()) {
                    continue;
                }
                Dimension d = preferred ? m.getPreferredSize() : m.getMinimumSize();
                if (rowWidth + d.width > maxWidth) {
                    addRow(dim, rowWidth, rowHeight);
                    rowWidth = 0;
                    rowHeight = 0;
                }
                if (rowWidth != 0) {
                    rowWidth += getHgap();
                }
                rowWidth += d.width;
                rowHeight = Math.max(rowHeight, d.height);
            }
            addRow(dim, rowWidth, rowHeight);
            dim.width += in.left + in.right + getHgap() * 2;
            dim.height += in.top + in.bottom + getVgap() * 2;
            if (SwingUtilities.getAncestorOfClass(JScrollPane.class, target) != null && target.isValid()) {
                dim.width -= getHgap() + 1;
            }
            return dim;
        }
    }

    private void addRow(Dimension dim, int rowWidth, int rowHeight) {
        dim.width = Math.max(dim.width, rowWidth);
        if (dim.height > 0) {
            dim.height += getVgap();
        }
        dim.height += rowHeight;
    }
}
