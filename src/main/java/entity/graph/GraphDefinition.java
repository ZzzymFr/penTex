package entity.graph;

import java.util.List;
import java.util.Objects;

/** Portable graph definition, independent of Swing and PNG rendering. */
public record GraphDefinition(List<String> functions, Range x, Range y, Range z, Camera camera) {
    public record Camera(double yaw, double pitch) {
        public Camera {
            if (!Double.isFinite(yaw) || !Double.isFinite(pitch))
                throw new IllegalArgumentException("Invalid 3D camera angles");
        }
    }
    public GraphDefinition(List<String> functions, Range x, Range y, Range z) {
        this(functions, x, y, z, null);
    }
    public GraphDefinition withCamera(Camera view) {
        return new GraphDefinition(functions, x, y, z, view);
    }
    public record Range(double min, double max) {
        public Range {
            if (!Double.isFinite(min) || !Double.isFinite(max) || min >= max)
                throw new IllegalArgumentException("Invalid graph axis range");
        }
        public double[] pair() { return new double[]{min, max}; }
    }
    public GraphDefinition {
        functions = List.copyOf(Objects.requireNonNull(functions, "functions"));
        if (functions.isEmpty() || functions.stream().anyMatch(f -> f == null || f.isBlank()))
            throw new IllegalArgumentException("Graph requires at least one formula");
    }
    public static GraphDefinition of(String latex) {
        return new GraphDefinition(List.of(Objects.requireNonNull(latex).trim()), null, null, null);
    }
    public double[] xPair() { return x == null ? null : x.pair(); }
    public double[] yPair() { return y == null ? null : y.pair(); }
    public double[] zPair() { return z == null ? null : z.pair(); }
}
