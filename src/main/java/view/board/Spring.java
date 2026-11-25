package view.board;

/**
 * Apple-style spring: dampingRatio 1 = settles with no overshoot, below 1 bounces;
 * response = seconds for one undamped cycle (lower = snappier). x in units, v in units/sec,
 * stepped with real elapsed seconds so it runs the same on any timer cadence.
 */
public final class Spring {
    private static final float SUBSTEP = 0.004f;
    private static final float MAX_DT = 0.05f;  // a stalled frame shouldn't fling things

    public float x;
    public float v;
    private final float eps;
    private final boolean opacity;

    /** For pixels. */
    public Spring() {
        this(0.1f);
    }

    /** eps = how close counts as arrived, e.g. 0.002 for a 0..1 amount. */
    public Spring(float eps) {
        this(eps, false);
    }

    /** opacity = true keeps it animating under reduce motion (fades are fine, movement isn't). */
    public Spring(float eps, boolean opacity) {
        this.eps = eps;
        this.opacity = opacity;
    }

    /** Jumps there, no motion. */
    public void snap(float to) {
        x = to;
        v = 0;
    }

    public boolean step(float target, Motion.Feel feel, float dt) {
        return step(target, feel.ratio(), feel.response(), dt);
    }

    // true once it's resting on the target
    public boolean step(float target, float ratio, float response, float dt) {
        if (Motion.reduced()) {
            if (!opacity) {
                snap(target);
                return true;
            }
            ratio = 1f;  // plain cross-fade, no overshoot
        }
        float w = (float) (2 * Math.PI / response);
        float left = Math.min(Math.max(dt, 0f), MAX_DT);
        while (left > 0) {
            float h = Math.min(left, SUBSTEP);
            v += (w * w * (target - x) - 2 * ratio * w * v) * h;  // semi-implicit euler
            x += v * h;
            left -= h;
        }
        if (Math.abs(target - x) < eps && Math.abs(v) < eps * 40) {
            snap(target);
            return true;
        }
        return false;
    }
}
