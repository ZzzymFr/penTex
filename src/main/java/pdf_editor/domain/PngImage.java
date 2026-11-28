package pdf_editor.domain;

import java.util.Objects;

/**
 * An image as the PDF material library keeps it: PNG bytes and the pixel size. Plain data, so the
 * use cases never depend on a GUI toolkit's image classes; adapters do the encoding.
 */
public final class PngImage {
    private final byte[] png;
    private final int width;
    private final int height;

    public PngImage(byte[] png, int width, int height) {
        this.png = Objects.requireNonNull(png, "png").clone();
        this.width = width;
        this.height = height;
    }

    /** A copy of the PNG bytes. */
    public byte[] png() {
        return png.clone();
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }
}
