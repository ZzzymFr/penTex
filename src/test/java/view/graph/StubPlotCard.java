package view.graph;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;

/** Lightweight plot shell for tests of view-layer card lifecycle, without computing functions. */
public final class StubPlotCard extends PlotCard {
    @Override boolean hasContent() {return true;}
    @Override void paintCard(Graphics2D g, int hot) { }
    @Override void plotDragged(double dx,double dy) { }
    @Override void plotClicked(Point p) { }
    @Override void hover(Point p) { }
    @Override void hoverEnded() { }
    @Override void wheel(MouseWheelEvent e, double factor) { }
    @Override void resetView() { }
    @Override public BufferedImage snapshot(int w,int h) {return new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);}
}
