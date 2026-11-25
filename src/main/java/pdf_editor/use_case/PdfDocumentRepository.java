package pdf_editor.use_case;

import pdf_editor.domain.SavedPdfDocument;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Storage port for named editable documents and the separate recovery draft. */
public interface PdfDocumentRepository {
    record Summary(UUID id, String name, java.time.Instant modifiedAt, int pages, byte[] thumbnail) {
        public Summary { thumbnail = thumbnail == null ? null : thumbnail.clone(); }
        @Override public byte[] thumbnail() { return thumbnail == null ? null : thumbnail.clone(); }
    }
    List<Summary> list() throws IOException;
    SavedPdfDocument open(UUID id) throws IOException;
    void save(SavedPdfDocument document, byte[] thumbnail) throws IOException;
    Optional<SavedPdfDocument> recover() throws IOException;
    void saveRecovery(SavedPdfDocument document) throws IOException;
    SavedPdfDocument rename(UUID id, String name, java.time.Instant modifiedAt) throws IOException;
    void delete(UUID id) throws IOException;
}
