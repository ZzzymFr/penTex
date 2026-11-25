package history.domain;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

/** Immutable, portable calculation/graph history; does not depend on Swing or storage. */
public record HistoryEntry(String id, Kind kind, String title, String formula, String result,
                           byte[] png, int imageWidth, int imageHeight, Instant createdAt, String graphDefinition) {
    /** Legacy archive records have no graph definition; their PNG remains viewable. */
    public HistoryEntry(String id, Kind kind, String title, String formula, String result,
                        byte[] png, int imageWidth, int imageHeight, Instant createdAt) {
        this(id, kind, title, formula, result, png, imageWidth, imageHeight, createdAt, null);
    }
    public enum Kind { FORMULA, CALCULATION, GRAPH, INK }
    public static final int MAX_PNG_BYTES = 12 * 1024 * 1024;

    public HistoryEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(formula, "formula");
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(createdAt, "createdAt");
        if (id.isBlank() || id.length() > 100) throw new IllegalArgumentException("Invalid history ID");
        if (title.isBlank() || title.length() > 200 || formula.length() > 32768 || result.length() > 32768)
            throw new IllegalArgumentException("History text is empty or too long");
        if (kind == Kind.GRAPH || kind == Kind.INK) {
            if (png == null || png.length == 0 || png.length > MAX_PNG_BYTES
                    || imageWidth <= 0 || imageHeight <= 0 || imageWidth > 16384 || imageHeight > 16384)
                throw new IllegalArgumentException("Invalid image PNG or dimensions");
            // At minimum, refuse arbitrary bytes mislabeled as a PNG image.
            byte[] signature = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
            if (png.length < signature.length ||
                    !Arrays.equals(Arrays.copyOf(png, signature.length), signature))
                throw new IllegalArgumentException("Image bytes must have a PNG signature");
        } else if (png != null || imageWidth != 0 || imageHeight != 0) {
            throw new IllegalArgumentException("Only graphs and ink may contain image bytes");
        }
        if (kind == Kind.INK && (!formula.isBlank() || !result.isBlank()))
            throw new IllegalArgumentException("Ink must not contain calculation metadata");
        if (kind == Kind.FORMULA && (formula.isBlank() || !result.isBlank()))
            throw new IllegalArgumentException("Formula entry requires LaTeX without a result");
        if (kind == Kind.CALCULATION && formula.isBlank())
            throw new IllegalArgumentException("Calculation requires a formula");
        if (graphDefinition != null) {
            if (kind != Kind.GRAPH || graphDefinition.length() > 32768)
                throw new IllegalArgumentException("Invalid graph definition for History record");
            if (graphDefinition.isBlank())
                throw new IllegalArgumentException("Empty graph definition");
        }
        png = png == null ? null : png.clone();
    }

    @Override public byte[] png() { return png == null ? null : png.clone(); }

    /** Preserve identity/content when a repeated action makes this record recent again. */
    public HistoryEntry revisitedAt(Instant time) {
        return new HistoryEntry(id, kind, title, formula, result, png, imageWidth, imageHeight,
                time, graphDefinition);
    }

    /** Same mathematical content = same key, even after restarting the application. */
    public String contentKey() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, kind.name().getBytes(StandardCharsets.UTF_8));
            update(digest, formula.trim().getBytes(StandardCharsets.UTF_8));
            update(digest, result.trim().getBytes(StandardCharsets.UTF_8));
            if (graphDefinition != null) update(digest, graphDefinition.getBytes(StandardCharsets.UTF_8));
            if (png != null) {
                update(digest, ByteBuffer.allocate(8).putInt(imageWidth).putInt(imageHeight).array());
                update(digest, png);
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static void update(MessageDigest digest, byte[] bytes) {
        digest.update(ByteBuffer.allocate(4).putInt(bytes.length).array());
        digest.update(bytes);
    }
}
