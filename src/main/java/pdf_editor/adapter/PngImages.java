package pdf_editor.adapter;

import pdf_editor.domain.PngImage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Objects;

/** Encodes toolkit images for the material use cases (the only place that knows both sides). */
public final class PngImages {
    private PngImages() {
    }

    /**
     * Lossless PNG copy of the image with its size.
     *
     * @throws NullPointerException  if image is null
     * @throws IllegalStateException if no PNG encoder is available
     */
    public static PngImage encode(BufferedImage image) {
        Objects.requireNonNull(image, "image");
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            if (!ImageIO.write(image, "png", buffer)) throw new IOException("PNG encoder unavailable");
            return new PngImage(buffer.toByteArray(), image.getWidth(), image.getHeight());
        } catch (IOException error) {
            throw new IllegalStateException("Could not encode image", error);
        }
    }
}
