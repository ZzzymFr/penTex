package pdf_editor.view;

import pdf_editor.domain.PdfDocument;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.List;

/** Turns a pen stroke drawn on a PDF page into a transparent picture and the page box it fills. */
final class PdfInkStrokeRaster {
    /** The picture and where it goes on the page (page units). */
    record Placed(BufferedImage image, float x, float y, float width, float height) {
    }

    private PdfInkStrokeRaster() {
    }

    static Placed render(List<Point2D.Float> points, Color color, float penWidth) {
        float minX=Float.MAX_VALUE,minY=Float.MAX_VALUE,maxX=-Float.MAX_VALUE,maxY=-Float.MAX_VALUE;
        for (var p : points) {minX=Math.min(minX,p.x);minY=Math.min(minY,p.y);
            maxX=Math.max(maxX,p.x);maxY=Math.max(maxY,p.y);}
        float pad=5f;
        float x=Math.max(0,minX-pad), y=Math.max(0,minY-pad);
        float w=Math.min(PdfDocument.PAGE_WIDTH-x, Math.max(10,maxX-x+pad));
        float h=Math.min(PdfDocument.PAGE_HEIGHT-y, Math.max(10,maxY-y+pad));
        int quality=3;
        BufferedImage image=new BufferedImage(Math.max(1,(int)Math.ceil(w*quality)),
                Math.max(1,(int)Math.ceil(h*quality)),BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=image.createGraphics();
        try {
            g.scale(quality,quality);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g.setStroke(new BasicStroke(Math.max(1f, penWidth),BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
            g.setColor(color);
            Path2D.Float path=new Path2D.Float();
            boolean first=true;
            for (var point : points) {if(first){path.moveTo(point.x-x,point.y-y);first=false;}
                else path.lineTo(point.x-x,point.y-y);}
            g.draw(path);
            if(points.size()==1)g.fillOval(Math.round(points.get(0).x-x)-1,
                    Math.round(points.get(0).y-y)-1,3,3);
        } finally {g.dispose();}
        return new Placed(image, x, y, w, h);
    }
}
