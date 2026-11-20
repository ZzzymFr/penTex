package data_access;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

// shipped weights only read ~600 px of a line, ink right of that just gets dropped, so long ones shrink to fit
final class ReadableWidth {
    static final int MAX_WIDTH = 576;  // probed: 592 read all of it, 640 lost the tail
    static final int MIN_LINE = 32;    // ink shorter than this and f turns into +, so stop shrinking there

    private ReadableWidth() {
    }

    // model-ready picture, narrower if it was too wide; same picture if it fit already
    static BufferedImage fit(BufferedImage prepared) {
        if (prepared.getWidth() <= MAX_WIDTH) {
            return prepared;
        }
        GrayInk gray = GrayInk.of(prepared);
        Rectangle box = gray.bounds();
        if (box == null) {
            return prepared;
        }
        double k = Math.max((MAX_WIDTH - 2.0 * FormulaLayout.MARGIN) / box.width, (double) MIN_LINE / box.height);
        if (k >= 1) {
            return prepared;
        }
        int w = Math.max(1, FormulaLayout.roundHalfUp(box.width * k));
        int h = Math.max(1, FormulaLayout.roundHalfUp(box.height * k));
        int width = Math.max(FormulaLayout.MIN_W, FormulaLayout.snapUp(w + 2 * FormulaLayout.MARGIN));
        int height = Math.max(FormulaLayout.MIN_H, FormulaLayout.snapUp(h + 2 * FormulaLayout.MARGIN));
        BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = canvas.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        g.drawImage(gray.crop(box).getScaledInstance(w, h, Image.SCALE_AREA_AVERAGING), (width - w) / 2,
                (height - h) / 2, null);
        g.dispose();
        return canvas;
    }
}
