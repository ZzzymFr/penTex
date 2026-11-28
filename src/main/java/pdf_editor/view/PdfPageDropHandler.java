package pdf_editor.view;

import interface_adapter.history.CanvasHistorySelection;
import pdf_editor.adapter.CanvasHistoryImporter;
import pdf_editor.adapter.PdfEditorController;
import pdf_editor.adapter.PngImages;
import pdf_editor.use_case.MaterialStore;

import javax.swing.TransferHandler;
import java.awt.Point;
import java.awt.datatransfer.DataFlavor;
import java.awt.image.BufferedImage;
import java.util.Objects;

/** Drops onto a PDF page: History items keep their structure, other images become ink pictures. */
final class PdfPageDropHandler extends TransferHandler {
    private final PdfEditorController controller;
    private final PdfPageCanvas page;

    PdfPageDropHandler(PdfEditorController controller, PdfPageCanvas page) {
        this.controller = Objects.requireNonNull(controller);
        this.page = Objects.requireNonNull(page);
    }

    @Override public boolean canImport(TransferSupport support) {
        return support.isDataFlavorSupported(CanvasPdfDragController.HISTORY_SELECTION_FLAVOR)
                || support.isDataFlavorSupported(DataFlavor.imageFlavor);
    }

    @Override public boolean importData(TransferSupport support) {
        if (!canImport(support)) return false;
        try {
            Point location = support.getDropLocation().getDropPoint();
            // A live inline note editor is a Swing child covering the page.
            // Commit/remove it before inserting the dropped History material.
            page.finishInlineEdit();
            if (support.isDataFlavorSupported(CanvasPdfDragController.HISTORY_SELECTION_FLAVOR)) {
                CanvasHistorySelection selection = (CanvasHistorySelection) support.getTransferable()
                        .getTransferData(CanvasPdfDragController.HISTORY_SELECTION_FLAVOR);
                CanvasHistoryImporter.importAt(selection, controller,
                        page.pageX(location.x), page.pageY(location.y));
                page.repaint();
                return true;
            }
            if (support.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                java.awt.Image source = (java.awt.Image) support.getTransferable()
                        .getTransferData(DataFlavor.imageFlavor);
                BufferedImage image = imageAsBuffered(source);
                MaterialStore.Material region = controller.materials().addInk("Canvas selection", PngImages.encode(image));
                float width = Math.min(440, Math.max(85, image.getWidth()));
                float height = width * image.getHeight() / Math.max(1f, image.getWidth());
                if (height > 575) { width *= 575f / height; height = 575f; }
                controller.place(region, page.pageX(location.x), page.pageY(location.y), width, height);
                page.repaint(); return true;
            }
            return false;
        } catch (Exception ignored) { return false; }
    }

    private static BufferedImage imageAsBuffered(java.awt.Image image) {
        if (image instanceof BufferedImage buffered) return buffered;
        if (image == null || image.getWidth(null) < 1 || image.getHeight(null) < 1)
            throw new IllegalArgumentException("Unsupported canvas image");
        BufferedImage copy = new BufferedImage(image.getWidth(null),
                image.getHeight(null), BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D graphics = copy.createGraphics();
        graphics.drawImage(image, 0, 0, null); graphics.dispose();
        return copy;
    }
}
