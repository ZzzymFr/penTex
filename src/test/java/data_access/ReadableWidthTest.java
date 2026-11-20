package data_access;

import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;

import static data_access.InkPictures.blocks;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReadableWidthTest {
    // one line of ink this wide, 112 tall like the preprocessor makes it
    private static BufferedImage line(int inkWidth) {
        int w = FormulaLayout.snapUp(inkWidth + 2 * FormulaLayout.MARGIN);
        return blocks(w, 128, new int[]{8, 8, inkWidth, 112});
    }

    private static Rectangle ink(BufferedImage image) {
        return GrayInk.of(image).bounds();
    }

    @Test
    void narrowPictureIsLeftAlone() {
        BufferedImage narrow = line(400);
        assertSame(narrow, ReadableWidth.fit(narrow));
    }

    @Test
    void widePictureShrinksToWhatTheModelReads() {
        BufferedImage fitted = ReadableWidth.fit(line(800));
        assertTrue(fitted.getWidth() <= ReadableWidth.MAX_WIDTH, "width " + fitted.getWidth());
        assertEquals(128, fitted.getHeight());
        assertEquals(0, fitted.getWidth() % 16);
        Rectangle box = ink(fitted);
        assertEquals(800.0 / 112, (double) box.width / box.height, 0.2, "same shape, just smaller");
    }

    @Test
    void shrinkStopsAtTheSmallestReadableLine() {
        BufferedImage fitted = ReadableWidth.fit(line(3000));
        assertTrue(ink(fitted).height >= ReadableWidth.MIN_LINE - 1, "ink " + ink(fitted).height);
    }
}
