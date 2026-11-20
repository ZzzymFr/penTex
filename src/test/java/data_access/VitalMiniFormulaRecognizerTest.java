package data_access;

import interface_adapter.recognize.FormulaRecognizer;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static data_access.InkPictures.assertSamePixels;
import static data_access.InkPictures.fraction;
import static data_access.InkPictures.oneLine;
import static org.junit.jupiter.api.Assertions.assertEquals;

class VitalMiniFormulaRecognizerTest {
    private final List<BufferedImage> seen = new ArrayList<>();
    private final FormulaRecognizer recognizer = new VitalMiniFormulaRecognizer(image -> {
        seen.add(image);
        return "\\frac{3}{2}";
    });

    @Test
    void handsBackWhatTheModelRead() throws Exception {
        assertEquals("\\frac{3}{2}", recognizer.recognize(fraction()));
    }

    @Test
    void fractionReachesTheModelScaledAsTrainingDid() throws Exception {
        recognizer.recognize(fraction());
        assertSamePixels(FormulaImagePreprocessor.prepare(fraction()), seen.get(0));
    }

    @Test
    void oneLineReachesTheModelAsBefore() throws Exception {
        recognizer.recognize(oneLine());
        assertSamePixels(FormulaImagePreprocessor.prepare(oneLine()), seen.get(0));
    }
}
