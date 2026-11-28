package pdf_editor.use_case;

import pdf_editor.MaterialStore;
import java.awt.image.BufferedImage;
import java.util.Objects;

/** A formula is added only by an explicit user selection command, not by background recognition. */
public class SelectionToHistory {
    private final SelectedInkRecognitionGateway recognizer;
    private final MaterialStore library;
    private final java.util.function.BiConsumer<String, String> publisher;
    public SelectionToHistory(SelectedInkRecognitionGateway recognizer, MaterialStore library) {
        this(recognizer, library, (formula, result) -> { });
    }
    public SelectionToHistory(SelectedInkRecognitionGateway recognizer, MaterialStore library,
                              java.util.function.BiConsumer<String, String> publisher) {
        this.publisher = Objects.requireNonNull(publisher);
        this.recognizer = Objects.requireNonNull(recognizer);
        this.library = Objects.requireNonNull(library);
    }
    public String recognize(BufferedImage selectedInk) throws Exception {
        String latex = recognizer.recognize(Objects.requireNonNull(selectedInk));
        if (latex == null || latex.isBlank()) throw new IllegalArgumentException("No formula was recognized");
        return latex.trim();
    }
    public MaterialStore.Material addFormula(String latex) {
        publisher.accept(latex, "");
        return library.addFormula(latex);
    }
    public MaterialStore.Material addCalculation(String latex, String result) {
        publisher.accept(latex, result);
        return library.addCalculation(latex, result);
    }
    public MaterialStore.Material addInk(BufferedImage image) {
        return library.addInk("Selected handwriting", Objects.requireNonNull(image));
    }
}
