package pdf_editor.use_case;

import pdf_editor.MaterialStore;

/** @deprecated Use SelectionToHistory. */
@Deprecated
public final class SelectionToLibrary extends SelectionToHistory {
    public SelectionToLibrary(SelectedInkRecognitionGateway recognizer, MaterialStore store) { super(recognizer, store); }
    public SelectionToLibrary(SelectedInkRecognitionGateway recognizer, MaterialStore store,
                              java.util.function.BiConsumer<String,String> publisher) { super(recognizer, store, publisher); }
}
