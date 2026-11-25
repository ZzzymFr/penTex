package pdf_editor.use_case;

import pdf_editor.domain.PdfDocument;
import pdf_editor.domain.SavedPdfDocument;
import java.io.IOException;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Independent PDF persistence use case; named saves and recovery never overwrite one another. */
public final class PdfWorkspace {
    private final PdfDocumentRepository repository;
    private final Clock clock;
    public PdfWorkspace(PdfDocumentRepository repository) { this(repository, Clock.systemUTC()); }
    public PdfWorkspace(PdfDocumentRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository);
        this.clock = Objects.requireNonNull(clock);
    }
    public SavedPdfDocument snapshot(UUID id, String name, PdfDocument document, int page, double zoom) {
        return new SavedPdfDocument(id, name, clock.instant(), document, page, zoom);
    }
    public List<PdfDocumentRepository.Summary> list() throws IOException {
        return repository.list().stream().sorted(Comparator.comparing(PdfDocumentRepository.Summary::modifiedAt)
                .reversed().thenComparing(PdfDocumentRepository.Summary::id)).toList();
    }
    public SavedPdfDocument open(UUID id) throws IOException { return repository.open(id); }
    public void save(SavedPdfDocument document, byte[] thumbnail) throws IOException { repository.save(document, thumbnail); }
    public void saveRecovery(SavedPdfDocument document) throws IOException { repository.saveRecovery(document); }
    public Optional<SavedPdfDocument> recover() throws IOException { return repository.recover(); }
    public SavedPdfDocument rename(UUID id, String name) throws IOException {
        String clean = Objects.requireNonNull(name).strip();
        if (clean.isBlank() || clean.length() > 200) throw new IllegalArgumentException("Enter a name (1–200 characters).");
        return repository.rename(id, clean, clock.instant());
    }
    public void delete(UUID id) throws IOException { repository.delete(id); }
}
