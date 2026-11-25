package view.board;

import interface_adapter.history.HistoryItem;
import java.awt.Point;

/** Narrow UI-facing port: the one-board layout never manipulates PDF document internals. */
public interface PdfWorkspacePort {
    void fitWhenOpened();
    void addHistoryItem(HistoryItem item);
    /** Import a saved calculation as structured LaTeX/answer, not as a screenshot. */
    default void addHistoryToLibrary(HistoryItem item) { }
    boolean dropHistoryItem(HistoryItem item, Point screenTopLeft);
    /** Native graph handoff; older PDF implementations can decline it safely. */
    default boolean dropInteractiveGraph(entity.graph.GraphDefinition definition,
                                         java.awt.image.BufferedImage thumbnail, Point cursorOnScreen) {
        return false;
    }

    /** Screen-space bounds of the eventual PDF placement; null if preview is unsupported. */
    default java.awt.Rectangle interactiveGraphPreviewBounds(Point cursorOnScreen,
                                                              double graphAspectRatio) {
        return null;
    }
}
