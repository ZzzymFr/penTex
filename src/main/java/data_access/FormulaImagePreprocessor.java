package data_access;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Arrays;

/**
 * Turns a picture of a handwritten formula into the input the model was trained on, the same
 * way as {@code src/neutral network/preprocess.py} ({@code load_formula_image}) with
 * {@code IMAGE_INVERT = False} from {@code train.py}:
 * RGB colours and light/dark kept as they are, cropped to the ink, scaled by the rule in
 * {@link FormulaLayout} (the tallest line of ink 112 px high, so every line of a multi-line formula
 * keeps the size a single line has), centred with an 8 px margin on a canvas whose height (at least
 * 128) and width (at least 64) are multiples of 16, the background filled with the original
 * background colour.
 *
 * <p>{@code vital_pixel.forward} only accepts {@code [B,3,H,W]} with H and W multiples of 16, so an
 * image straight from the canvas must go through here first.
 */
final class FormulaImagePreprocessor {
    private static final int INK_THRESHOLD = 32;  // difference in any RGB channel from black/white background
    // bands thinner than this many pens are just dots / dashes, not a line of chars (BandRule path only)
    static final double MARK_PENS = 3;
    // made-up line height (in pens) for ink thats only marks, about a normal handwritten char
    static final double GLYPH_PENS = 8;

    private FormulaImagePreprocessor() {
    }

    /** {@link #prepare(BufferedImage, CropScale)} with the scale model training used. */
    static BufferedImage prepare(BufferedImage source) {
        return prepare(source, CropScale.defaultModel());
    }

    /**
     * @param cropScale picks the factor m on the tallest band, like {@code band_scale} in training;
     *                  {@link CropScale#RULE} keeps the plain rule
     * @return an RGB image at least 128 px high, height and width multiples of 16,
     *         preserving the source RGB colours like the training images
     * @throws IllegalArgumentException if there is no ink in the image
     */
    static BufferedImage prepare(BufferedImage source, CropScale cropScale) {
        int width = source.getWidth();
        int height = source.getHeight();
        int[] rgb = toRgb(source);

        // A light border means a light background (as on the canvas); the ink is then the dark part.
        boolean lightBackground = median(border(rgb, width, height)) >= 128;
        int left = width;
        int top = height;
        int right = -1;
        int bottom = -1;
        boolean[] rowHasInk = new boolean[height];
        boolean[] isInk = new boolean[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = rgb[y * width + x];
                int foreground = 0;
                for (int shift = 16; shift >= 0; shift -= 8) {
                    int value = (pixel >>> shift) & 0xFF;
                    foreground = Math.max(foreground, lightBackground ? 255 - value : value);
                }
                if (foreground > INK_THRESHOLD) {
                    left = Math.min(left, x);
                    top = Math.min(top, y);
                    right = Math.max(right, x);
                    bottom = Math.max(bottom, y);
                    rowHasInk[y] = true;
                    isInk[y * width + x] = true;
                }
            }
        }
        if (right < 0) {
            throw new IllegalArgumentException("The image has no handwriting in it.");
        }
        int inkWidth = right - left + 1;
        int inkHeight = bottom - top + 1;
        BufferedImage ink = new BufferedImage(inkWidth, inkHeight, BufferedImage.TYPE_INT_RGB);
        ink.setRGB(0, 0, inkWidth, inkHeight, rgb, top * width + left, width);

        // Same scale and canvas as training (formula_layout.py, band_scale from crop_scale.py).
        boolean[] mask = new boolean[inkWidth * inkHeight];
        for (int y = 0; y < inkHeight; y++) {
            System.arraycopy(isInk, (top + y) * width + left, mask, y * inkWidth, inkWidth);
        }
        double tallest = FormulaLayout.tallestBand(
                FormulaLayout.rowSpans(Arrays.copyOfRange(rowHasInk, top, bottom + 1)),
                inkHeight, FormulaLayout.GAP_MIN_PX) * cropScale.bandScale(mask, inkWidth, inkHeight);
        FormulaLayout.Size layout = FormulaLayout.size(inkWidth, inkHeight, tallest);
        int resizedWidth = Math.max(1, FormulaLayout.roundHalfUp(inkWidth * layout.scale()));
        int resizedHeight = Math.max(1, FormulaLayout.roundHalfUp(inkHeight * layout.scale()));
        BufferedImage resized = resize(ink, resizedWidth, resizedHeight);

        int canvasWidth = layout.width();
        int canvasHeight = layout.height();
        BufferedImage canvas = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = canvas.createGraphics();
        int background = lightBackground ? 255 : 0;  // not inverted, so the background keeps its colour
        g.setColor(new java.awt.Color(background, background, background));
        g.fillRect(0, 0, canvasWidth, canvasHeight);
        g.drawImage(resized, (canvasWidth - resizedWidth) / 2, (canvasHeight - resizedHeight) / 2, null);
        g.dispose();
        return canvas;
    }

    /**
     * The plain gray-on-white path with a {@link BandRule} (no scale model): dark backgrounds are
     * flipped, coloured ink turns dark gray, and ink that is only dots / dashes is sized like a normal
     * character instead of being blown up to a full line. The recognizer does not use it: the shipped
     * weights are trained with {@link #prepare(BufferedImage, CropScale)}.
     *
     * @return a gray-on-white RGB image (r=g=b) at least 128 px high, sides multiples of 16
     * @throws IllegalArgumentException if there is no ink in the image
     */
    static BufferedImage prepare(BufferedImage source, BandRule rule) {
        GrayInk gray = GrayInk.of(source);
        Rectangle box = gray.bounds();
        if (box == null) {
            throw new IllegalArgumentException("The image has no handwriting in it.");
        }
        double tallest = lineHeight(gray, box, rule);
        FormulaLayout.Size layout = FormulaLayout.size(box.width, box.height, tallest);
        int resizedWidth = Math.max(1, FormulaLayout.roundHalfUp(box.width * layout.scale()));
        int resizedHeight = Math.max(1, FormulaLayout.roundHalfUp(box.height * layout.scale()));
        BufferedImage resized = resize(gray.crop(box), resizedWidth, resizedHeight);

        BufferedImage canvas = new BufferedImage(layout.width(), layout.height(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = canvas.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, layout.width(), layout.height());
        g.drawImage(resized, (layout.width() - resizedWidth) / 2, (layout.height() - resizedHeight) / 2, null);
        g.dispose();
        return canvas;
    }

    // rule's line height (tallest band by default), unless its all dots/dashes, then a normal char height
    private static double lineHeight(GrayInk gray, Rectangle box, BandRule rule) {
        double tallest = rule.lineHeight(FormulaLayout.tallestBand(
                FormulaLayout.rowSpans(gray.rowsWithInk(box)), box.height, FormulaLayout.GAP_MIN_PX), box.height);
        double pen = gray.penWidth(box);
        return tallest < MARK_PENS * pen ? GLYPH_PENS * pen : tallest;
    }

    /** Composite transparency onto white without discarding RGB colour information. */
    private static int[] toRgb(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        int[] rgb = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = source.getRGB(x, y);
                double alpha = ((argb >>> 24) & 0xFF) / 255.0;
                double r = ((argb >> 16) & 0xFF) * alpha + 255 * (1 - alpha);
                double gr = ((argb >> 8) & 0xFF) * alpha + 255 * (1 - alpha);
                double b = (argb & 0xFF) * alpha + 255 * (1 - alpha);
                rgb[y * width + x] = ((int) Math.round(r) << 16)
                        | ((int) Math.round(gr) << 8) | (int) Math.round(b);
            }
        }
        return rgb;
    }

    private static int[] border(int[] rgb, int width, int height) {
        int[] values = new int[3 * (2 * width + 2 * height)];
        int n = 0;
        for (int x = 0; x < width; x++) {
            for (int shift = 16; shift >= 0; shift -= 8) {
                values[n++] = (rgb[x] >>> shift) & 0xFF;
                values[n++] = (rgb[(height - 1) * width + x] >>> shift) & 0xFF;
            }
        }
        for (int y = 0; y < height; y++) {
            for (int shift = 16; shift >= 0; shift -= 8) {
                values[n++] = (rgb[y * width] >>> shift) & 0xFF;
                values[n++] = (rgb[y * width + width - 1] >>> shift) & 0xFF;
            }
        }
        return values;
    }

    private static double median(int[] values) {
        int[] sorted = values.clone();
        Arrays.sort(sorted);
        int middle = sorted.length / 2;
        return sorted.length % 2 == 1 ? sorted[middle] : (sorted[middle - 1] + sorted[middle]) / 2.0;
    }

    /** Smooth resizing: area averaging when shrinking (close to training's Lanczos), bicubic when enlarging. */
    private static BufferedImage resize(BufferedImage image, int width, int height) {
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        if (width < image.getWidth() || height < image.getHeight()) {
            g.drawImage(image.getScaledInstance(width, height, Image.SCALE_AREA_AVERAGING), 0, 0, null);
        } else {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.drawImage(image, 0, 0, width, height, null);
        }
        g.dispose();
        return out;
    }
}
