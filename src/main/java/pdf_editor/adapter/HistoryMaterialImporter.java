package pdf_editor.adapter;

import entity.graph.GraphDefinition;
import interface_adapter.graph.GraphDefinitionCodec;
import interface_adapter.history.HistoryItem;
import pdf_editor.MaterialStore;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Objects;

/** Import a durable History record with its structured data and original image bytes. */
public final class HistoryMaterialImporter {
    private HistoryMaterialImporter() { }
    public static MaterialStore.Material importRecord(HistoryItem record, MaterialStore store) {
        Objects.requireNonNull(record, "record"); Objects.requireNonNull(store, "store");
        var entry = record.entry();
        byte[] png = entry.png();
        int width = entry.imageWidth(), height = entry.imageHeight();
        if (png != null) {
            try {
                var image = ImageIO.read(new ByteArrayInputStream(png));
                if (image == null) throw new IOException("Invalid saved image");
                // Older archives may carry stale dimensions; the PNG is authoritative.
                width = image.getWidth(); height = image.getHeight();
            } catch (IOException invalid) { throw new IllegalArgumentException("Invalid History image", invalid); }
        }
        GraphDefinition definition = entry.graphDefinition() == null ? null : GraphDefinitionCodec.decode(entry.graphDefinition());
        return store.rememberArchived(entry.id(), entry.title(), entry.formula(), entry.result(),
                png, width, height, definition);
    }
}
