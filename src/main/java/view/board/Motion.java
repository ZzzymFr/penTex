package view.board;

import java.util.prefs.Preferences;

/** One place for how things move: Apple damping ratio + response, real-time clock, reduce motion. */
public final class Motion {
    /** dampingRatio 1 = no overshoot; response in seconds. */
    public record Feel(float ratio, float response) { }

    /** Pressing down / letting go: instant, no wobble. */
    public static final Feel PRESS = new Feel(1f, 0.15f);
    /** Hover lens following the mouse. */
    public static final Feel HOVER = new Feel(1f, 0.20f);
    /** Selection platter / segmented lens: a hint of liquid overshoot. */
    public static final Feel SELECT = new Feel(0.85f, 0.30f);
    /** Settling after a throw: carries the flick, a little bounce. */
    public static final Feel THROW = new Feel(0.8f, 0.40f);
    /** Things arriving / leaving (notices, popups). */
    public static final Feel ENTER = new Feel(1f, 0.32f);

    public static final int FRAME_MS = 16;

    private static final String KEY = "reduceMotion";
    private static final Preferences PREFS = Preferences.userNodeForPackage(Motion.class);
    private static volatile Boolean reduced;

    private Motion() {
    }

    /** -Dpentex.reduceMotion=true or the in-app toggle; then springs snap and fades stay short. */
    public static boolean reduced() {
        Boolean r = reduced;
        if (r == null) {
            String prop = System.getProperty("pentex.reduceMotion");
            r = prop != null ? Boolean.parseBoolean(prop) : readPref();
            reduced = r;
        }
        return r;
    }

    public static void setReduced(boolean on) {
        reduced = on;
        try {
            PREFS.putBoolean(KEY, on);
        } catch (RuntimeException ignored) {
            // no prefs store, just keep it for this run
        }
    }

    private static boolean readPref() {
        try {
            return PREFS.getBoolean(KEY, false);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Frame clock: seconds since the last tick, 1/60 on the first one. Call reset() when a timer stops. */
    public static final class Clock {
        private long last;

        public float tick() {
            long now = System.nanoTime();
            float dt = last == 0 ? FRAME_MS / 1000f : (now - last) / 1e9f;
            last = now;
            return Math.max(0.001f, Math.min(dt, 0.05f));
        }

        public void reset() {
            last = 0;
        }
    }

    /** Where a flick would come to rest, Apple's scroll-deceleration projection. v in px/s. */
    public static float project(float velocity, float decelerationRate) {
        return velocity / 1000f * decelerationRate / (1f - decelerationRate);
    }

    /** Resistance past an edge: follows less the further it goes. */
    public static float rubberband(float overshoot, float dimension) {
        float c = 0.55f;
        float sign = Math.signum(overshoot);
        float o = Math.abs(overshoot);
        return sign * (o * dimension * c) / (dimension + c * o);
    }
}
