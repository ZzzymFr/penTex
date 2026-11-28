package view.drawing;

import java.awt.image.BufferedImage;

/** Typesets a History formula and its result into an image of about the given size (white paper). */
@FunctionalInterface
public interface MathAssetRenderer {
    BufferedImage render(String latex, String result, int width, int height);
}
