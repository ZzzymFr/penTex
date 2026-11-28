package pdf_editor.view;

import pdf_editor.domain.PdfDocument;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Path2D;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Which placements a lasso loop or a selection box on the page covers (screen coordinates). */
final class PdfPageRegionSelection {
    private PdfPageRegionSelection() {
    }

    /** The lasso loop when it has at least three points, otherwise the box from start to end. */
    static Shape region(boolean lasso, List<Point> path, Point start, Point end) {
        if (lasso && path.size() >= 3) {
            Path2D.Float outline = new Path2D.Float();
            outline.moveTo(path.get(0).x, path.get(0).y);
            for (int i = 1; i < path.size(); i++) outline.lineTo(path.get(i).x, path.get(i).y);
            outline.closePath();
            return outline;
        }
        return new Rectangle(Math.min(start.x, end.x), Math.min(start.y, end.y),
                Math.abs(start.x - end.x), Math.abs(start.y - end.y));
    }

    /** Ids of the placements whose centre is inside the region or whose box touches it, in page order. */
    static Set<String> covered(Shape region, List<PdfDocument.Placement> placements, double zoom, int margin) {
        Set<String> chosen = new LinkedHashSet<>();
        for (PdfDocument.Placement item : placements) {
            int cx = Math.round((item.x() + item.width() / 2) * (float) zoom) + margin;
            int cy = Math.round((item.y() + item.height() / 2) * (float) zoom) + margin;
            int left = Math.round(item.x() * (float) zoom) + margin;
            int top = Math.round(item.y() * (float) zoom) + margin;
            int width = Math.max(1, Math.round(item.width() * (float) zoom));
            int height = Math.max(1, Math.round(item.height() * (float) zoom));
            if (region.contains(cx, cy) || region.intersects(left, top, width, height)) chosen.add(item.id());
        }
        return chosen;
    }
}
