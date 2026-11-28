package pdf_editor.adapter;

import interface_adapter.history.HistoryItem;
import pdf_editor.MaterialStore;

/** @deprecated Use HistoryMaterialImporter. Retained for existing integration tests/clients. */
@Deprecated
public final class HistoryLibraryImporter {
    private HistoryLibraryImporter() { }
    public static MaterialStore.Material importRecord(HistoryItem record, MaterialStore store) {
        return HistoryMaterialImporter.importRecord(record, store);
    }
}
