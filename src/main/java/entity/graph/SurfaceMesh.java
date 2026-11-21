package entity.graph;

import java.util.ArrayList;
import java.util.List;

// loose quads in world coords, each row x0 y0 z0 .. x3 y3 z3, what an F(x,y,z)=0 surface gets cut into
public final class SurfaceMesh {
    private final List<double[]> quads;

    SurfaceMesh(List<double[]> quads) {
        this.quads = new ArrayList<>(quads);
    }

    public boolean isEmpty() {
        return quads.isEmpty();
    }

    public int size() {
        return quads.size();
    }

    public double[][] getQuads() {
        double[][] copy = new double[quads.size()][];
        for (int i = 0; i < copy.length; i++) {
            copy[i] = quads.get(i).clone();
        }
        return copy;
    }

    // {xMin, xMax, yMin, yMax, zMin, zMax} of every corner, null when there's nothing
    public double[] bounds() {
        if (quads.isEmpty()) {
            return null;
        }
        double[] b = {Double.MAX_VALUE, -Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE,
                Double.MAX_VALUE, -Double.MAX_VALUE};
        for (double[] q : quads) {
            for (int i = 0; i < q.length; i++) {
                int axis = i % 3;
                b[2 * axis] = Math.min(b[2 * axis], q[i]);
                b[2 * axis + 1] = Math.max(b[2 * axis + 1], q[i]);
            }
        }
        return b;
    }
}
