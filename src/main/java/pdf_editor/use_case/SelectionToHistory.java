package pdf_editor.use_case;

import pdf_editor.domain.PdfMaterial;
import pdf_editor.domain.PngImage;
import java.util.Objects;

/**
 * A formula is added only by an explicit user selection command, not by background recognition.
 *
 * @param <I> the image type of the selected ink, opaque to this use case
 */
public class SelectionToHistory<I> {
    private final SelectedInkRecognitionGateway<I> recognizer;
    private final MaterialArchive library;
    private final java.util.function.BiConsumer<String, String> publisher;
    public SelectionToHistory(SelectedInkRecognitionGateway<I> recognizer, MaterialArchive library) {
        this(recognizer, library, (formula, result) -> { });
    }
    public SelectionToHistory(SelectedInkRecognitionGateway<I> recognizer, MaterialArchive library,
                              java.util.function.BiConsumer<String, String> publisher) {
        this.publisher = Objects.requireNonNull(publisher);
        this.recognizer = Objects.requireNonNull(recognizer);
        this.library = Objects.requireNonNull(library);
    }
    public String recognize(I selectedInk) throws Exception {
        String latex = recognizer.recognize(Objects.requireNonNull(selectedInk));
        if (latex == null || latex.isBlank()) throw new IllegalArgumentException("No formula was recognized");
        return latex.trim();
    }
    public PdfMaterial addFormula(String latex) {
        publisher.accept(latex, "");
        return library.addFormula(latex);
    }
    public PdfMaterial addCalculation(String latex, String result) {
        publisher.accept(latex, result);
        return library.addCalculation(latex, result);
    }
    public PdfMaterial addInk(PngImage image) {
        return library.addInk("Selected handwriting", Objects.requireNonNull(image));
    }
}
