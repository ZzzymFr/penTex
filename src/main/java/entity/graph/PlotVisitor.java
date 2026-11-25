package entity.graph;

// 2d or 3d without instanceof chains, same idea as NodeVisitor
public interface PlotVisitor<T> {
    T visitCurve(CurvePlot plot);

    T visitSurface(SurfacePlot plot);

    T visitImplicit(ImplicitPlot plot);

    // F(x, y) = 0 on the 2d card; visitors that only ever see the other kinds needn't handle it
    default T visitImplicitCurve(ImplicitCurvePlot plot) {
        throw new UnsupportedOperationException("this visitor doesn't handle 2d implicit curves");
    }
}
