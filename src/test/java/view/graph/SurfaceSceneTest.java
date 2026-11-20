package view.graph;

import interface_adapter.graph.GraphState;
import org.junit.jupiter.api.Test;

import java.awt.geom.Rectangle2D;

import static org.junit.jupiter.api.Assertions.*;

class SurfaceSceneTest {
    private static GraphState plane() {
        int n = 11;
        double[][] z = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                z[i][j] = -1 + 0.2 * i;   // z = x on [-1, 1]
            }
        }
        GraphState s = new GraphState();
        s.setKind(GraphState.Kind.SURFACE);
        s.setWindow(-1, 1, -1, 1);
        s.setZRange(-1, 1);
        s.setZ(z);
        return s;
    }

    private final Rectangle2D area = new Rectangle2D.Double(0, 0, 400, 300);

    @Test
    void pickingAProjectedPointGivesItBack() {
        SurfaceScene scene = SurfaceScene.build(plane(), new OrbitCamera(), area);
        double[] p = scene.project(0.3, -0.4, 0.3);
        double[] xy = scene.pick(p[0], p[1]);
        assertNotNull(xy);
        assertEquals(0.3, xy[0], 0.02);
        assertEquals(-0.4, xy[1], 0.02);
    }

    @Test
    void offTheSurfaceIsNull() {
        SurfaceScene scene = SurfaceScene.build(plane(), new OrbitCamera(), area);
        assertNull(scene.pick(2, 2));
    }

    @Test
    void quadsAreSortedFarToNearAndHolesSkipped() {
        GraphState s = plane();
        double[][] z = s.getZ();
        z[5][5] = Double.NaN;
        SurfaceScene scene = SurfaceScene.build(s, new OrbitCamera(), area);
        assertEquals(100 - 4, scene.quads().size(), "one NaN corner knocks out 4 cells");
        for (int k = 1; k < scene.quads().size(); k++) {
            assertTrue(scene.quads().get(k - 1).depth <= scene.quads().get(k).depth);
        }
    }

    // z = 0 as 10 x 10 loose quads, no grid
    private static GraphState mesh() {
        double[][] quads = new double[100][];
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 10; j++) {
                double x = -1 + 0.2 * i;
                double y = -1 + 0.2 * j;
                quads[10 * i + j] = new double[]{x, y, 0, x + 0.2, y, 0, x + 0.2, y + 0.2, 0, x, y + 0.2, 0};
            }
        }
        GraphState s = new GraphState();
        s.setKind(GraphState.Kind.SURFACE);
        s.setWindow(-1, 1, -1, 1);
        s.setZRange(-1, 1);
        s.setMesh(quads);
        return s;
    }

    @Test
    void meshQuadsAreCutAndPickable() {
        SurfaceScene scene = SurfaceScene.build(mesh(), new OrbitCamera(), area);
        assertEquals(100, scene.quads().size());
        double[] p = scene.project(0.3, -0.4, 0);
        double[] xy = scene.pick(p[0], p[1]);
        assertNotNull(xy);
        assertEquals(0.3, xy[0], 0.02);
        assertEquals(-0.4, xy[1], 0.02);
        assertEquals(0.5, scene.quads().get(0).level, 1e-9, "z = 0 is mid box");
    }

    @Test
    void cameraPitchIsClamped() {
        OrbitCamera cam = new OrbitCamera();
        cam.rotate(0, 100000);
        assertTrue(cam.pitch() <= Math.toRadians(89) + 1e-9);
        cam.reset();
        assertEquals(OrbitCamera.DEFAULT_PITCH, cam.pitch(), 1e-12);
    }
}
