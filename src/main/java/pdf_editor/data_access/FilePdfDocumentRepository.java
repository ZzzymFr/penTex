package pdf_editor.data_access;

import pdf_editor.domain.SavedPdfDocument;
import pdf_editor.use_case.PdfDocumentRepository;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Local storage with atomic replacement, separate recovery draft, and header-only listing. */
public final class FilePdfDocumentRepository implements PdfDocumentRepository {
    private static final long MAX_FILE_BYTES = 256L * 1024 * 1024;
    private final Path directory;
    private final PdfDocumentCodec codec = new PdfDocumentCodec();
    public FilePdfDocumentRepository(Path directory) { this.directory = Objects.requireNonNull(directory); }
    public static Path defaultDirectory() { return Path.of(System.getProperty("user.home"), ".mathlab", "pdf-documents"); }
    private Path named(UUID id) { return directory.resolve(Objects.requireNonNull(id) + ".mlpdf"); }
    private Path recovery() { return directory.resolve("last-session.recovery"); }
    @Override public synchronized List<Summary> list() throws IOException {
        if (!Files.isDirectory(directory)) return List.of();
        List<Summary> results = new ArrayList<>();
        try (var paths = Files.newDirectoryStream(directory, "*.mlpdf")) {
            for (Path file : paths) {
                try (DataInputStream in = input(file)) {
                    var h = codec.header(in);
                    if (!file.getFileName().equals(named(h.id()).getFileName())) continue;
                    results.add(new Summary(h.id(), h.name(), h.modified(), h.pages(), h.thumbnail()));
                } catch (IOException | IllegalArgumentException malformed) {
                    // Corrupt saves cannot prevent other documents from appearing.
                }
            }
        }
        return List.copyOf(results);
    }
    @Override public synchronized SavedPdfDocument open(UUID id) throws IOException {
        SavedPdfDocument saved = read(named(id));
        if (!id.equals(saved.id())) throw new IOException("PDF document ID mismatch"); return saved;
    }
    @Override public synchronized void save(SavedPdfDocument doc, byte[] thumbnail) throws IOException {
        write(named(doc.id()), doc, thumbnail);
    }
    @Override public synchronized Optional<SavedPdfDocument> recover() throws IOException {
        return Files.exists(recovery()) ? Optional.of(read(recovery())) : Optional.empty();
    }
    @Override public synchronized void saveRecovery(SavedPdfDocument doc) throws IOException { write(recovery(), doc, null); }
    @Override public synchronized SavedPdfDocument rename(UUID id, String name, java.time.Instant modifiedAt) throws IOException {
        SavedPdfDocument old = open(id);
        byte[] thumbnail;
        try (DataInputStream in = input(named(id))) { thumbnail = codec.header(in).thumbnail(); }
        SavedPdfDocument renamed = new SavedPdfDocument(id, name, modifiedAt, old.document(), old.page(), old.zoom());
        write(named(id), renamed, thumbnail);
        return renamed;
    }
    @Override public synchronized void delete(UUID id) throws IOException { Files.delete(named(id)); }
    private DataInputStream input(Path file) throws IOException {
        if (Files.size(file) > MAX_FILE_BYTES) throw new IOException("PDF save exceeds storage limit");
        return new DataInputStream(new BufferedInputStream(Files.newInputStream(file)));
    }
    private SavedPdfDocument read(Path file) throws IOException {
        try (DataInputStream in = input(file)) { return codec.read(in); }
    }
    private void write(Path target, SavedPdfDocument document, byte[] thumbnail) throws IOException {
        Files.createDirectories(directory);
        Path temp = Files.createTempFile(directory, "pdf-", ".tmp");
        try {
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(temp)))) {
                codec.write(out, document, thumbnail);
            }
            if (Files.size(temp) > MAX_FILE_BYTES) throw new IOException("PDF save exceeds storage limit");
            try { Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException unsupported) { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
}
