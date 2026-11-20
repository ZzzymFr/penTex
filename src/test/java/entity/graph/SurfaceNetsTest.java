package entity.graph;

import entity.ImplicitSurface;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurfaceNetsTest {
    private static final Viewport FLOOR = new Viewport(-2, 2, -2, 2);

    private static ImplicitSurface surface(ImplicitSurface.Rule rule) {
        return new ImplicitSurface("test", rule);
    }

    @Test
    void sphereCornersSitOnTheSphere() {
        SurfaceMesh mesh = SurfaceNets.mesh(surface((x, y, z) -> x * x + y * y + z * z - 1), FLOOR, -2, 2, 24);
        assertFalse(mesh.isEmpty());
        double[][] quads = mesh.getQuads();
        for (double[] q : quads) {
            for (int c = 0; c < 4; c++) {
                double r = Math.sqrt(q[3 * c] * q[3 * c] + q[3 * c + 1] * q[3 * c + 1] + q[3 * c + 2] * q[3 * c + 2]);
                assertEquals(1, r, 0.1);
            }
        }
        double[] b = mesh.bounds();
        assertEquals(-1, b[0], 0.15);
        assertEquals(1, b[1], 0.15);
        assertEquals(-1, b[4], 0.15);
        assertEquals(1, b[5], 0.15);
    }

    @Test
    void sphereIsClosedEveryEdgeSharedByTwoQuads() {
        SurfaceMesh mesh = SurfaceNets.mesh(surface((x, y, z) -> x * x + y * y + z * z - 1), FLOOR, -2, 2, 16);
        java.util.Map<String, Integer> edges = new java.util.HashMap<>();
        for (double[] q : mesh.getQuads()) {
            for (int c = 0; c < 4; c++) {
                String a = key(q, c);
                String b = key(q, (c + 1) % 4);
                String e = a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
                edges.merge(e, 1, Integer::sum);
            }
        }
        for (int count : edges.values()) {
            assertEquals(2, count);
        }
    }

    private static String key(double[] q, int c) {
        return Math.round(q[3 * c] * 1e6) + "," + Math.round(q[3 * c + 1] * 1e6) + "," + Math.round(q[3 * c + 2] * 1e6);
    }

    @Test
    void planeCornersLieOnThePlane() {
        SurfaceMesh mesh = SurfaceNets.mesh(surface((x, y, z) -> x + y + z - 1), FLOOR, -2, 2, 12);
        assertFalse(mesh.isEmpty());
        for (double[] q : mesh.getQuads()) {
            for (int c = 0; c < 4; c++) {
                assertEquals(1, q[3 * c] + q[3 * c + 1] + q[3 * c + 2], 1e-9, "wall ones too");
            }
        }
    }

    @Test
    void planeRunsRightUpToTheWallsAndNotPast() {
        SurfaceMesh mesh = SurfaceNets.mesh(surface((x, y, z) -> x + y + z - 1), FLOOR, -2, 2, 12);
        double[] b = mesh.bounds();
        for (int i = 0; i < 6; i++) {
            assertEquals(i % 2 == 0 ? -2 : 2, b[i], 1e-9);
        }
    }

    @Test
    void noFlatSkirtPastTheWalls() {
        SurfaceMesh mesh = SurfaceNets.mesh(surface((x, y, z) -> x + y + z - 1), FLOOR, -2, 2, 12);
        for (double[] q : mesh.getQuads()) {
            for (int a = 0; a < 3; a++) {
                for (double wall : new double[]{-2, 2}) {
                    boolean flat = true;
                    for (int c = 0; c < 4; c++) {
                        flat &= q[3 * c + a] == wall;
                    }
                    assertFalse(flat, "quad flattened onto a wall");
                }
            }
        }
    }

    @Test
    void nothingInTheBoxIsEmpty() {
        SurfaceMesh mesh = SurfaceNets.mesh(surface((x, y, z) -> x * x + y * y + z * z - 100), FLOOR, -2, 2, 12);
        assertTrue(mesh.isEmpty());
        assertNull(mesh.bounds());
    }

    @Test
    void poleIsNotASurface() {
        SurfaceMesh mesh = SurfaceNets.mesh(surface((x, y, z) -> 1 / x), new Viewport(-1, 1.1, -1, 1), -1, 1, 12);
        assertTrue(mesh.isEmpty());
    }

    @Test
    void undefinedSpotsLeaveHolesNotJunk() {
        SurfaceMesh mesh = SurfaceNets.mesh(surface((x, y, z) -> Math.sqrt(x) + z), FLOOR, -2, 2, 12);
        for (double[] q : mesh.getQuads()) {
            for (int c = 0; c < 4; c++) {
                assertTrue(Double.isFinite(q[3 * c + 2]));
            }
        }
    }
}
