package pdf_editor.use_case;

/**
 * Outbound recognition port; US4 does not depend on a concrete US1 model.
 *
 * @param <I> the image type the recognizer reads; the use case only passes it along
 */
@FunctionalInterface
public interface SelectedInkRecognitionGateway<I> {
    String recognize(I selection) throws Exception;
}
