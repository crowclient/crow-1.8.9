package crow.client.render.aa;

import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;

/**
 * Antialiasing pipeline configuration.
 *
 * <p>The actual mode is read by {@link AAFramebuffer} on every {@code begin()} —
 * changing {@link #current} between frames hot-reloads the pipeline (the FBO is
 * lazily resized/reallocated when its parameters no longer match).
 */
public final class AAConfig {

    /**
     * Pre-defined modes exposed in the UI. Each mode is a pair of:
     * <ul>
     *   <li>{@code ssaa}   — supersampling factor (1 = native render res, 2 = 4× pixels)</li>
     *   <li>{@code samples} — MSAA sample count per pixel (0 = MSAA off; 2/4/8 otherwise)</li>
     * </ul>
     * SSAA × MSAA combine: the FBO is allocated at {@code ssaa × native} resolution
     * with {@code samples} MSAA samples per pixel of that allocation.
     */
    public enum Mode {
        // The MSAA-named entries are kept so saved configs from previous
        // builds load — they map to SSAA equivalents because alpha-FBO
        // compositing makes true MSAA awkward here. See AAFramebuffer
        // for the full reasoning.
        OFF              ("Off",          1, 0),
        MSAA_2X          ("Smooth 2×",    2, 0),
        MSAA_4X          ("Smooth 4×",    2, 0),
        MSAA_8X          ("Smooth 8×",    4, 0),
        SSAA_2X          ("SSAA 2×",      2, 0),
        MSAA_4X_SSAA_2X  ("Ultra",        4, 0);

        public final String label;
        public final int ssaa;
        public final int samples;

        Mode(String label, int ssaa, int samples) {
            this.label = label;
            this.ssaa = ssaa;
            this.samples = samples;
        }

        @Override
        public String toString() { return label; }
    }

    /** Currently selected AA mode. Read on every UI frame. */
    public static volatile Mode current = Mode.OFF;

    private static int cachedMaxSamples = -1;

    /** GL_MAX_SAMPLES, queried lazily once. Returns 0 if MSAA is unsupported. */
    public static int maxSamplesSupported() {
        if (cachedMaxSamples >= 0) return cachedMaxSamples;
        try {
            ContextCapabilities caps = GLContext.getCapabilities();
            if (!caps.OpenGL30 && !caps.GL_EXT_framebuffer_multisample) {
                cachedMaxSamples = 0;
            } else {
                cachedMaxSamples = GL11.glGetInteger(GL30.GL_MAX_SAMPLES);
                if (cachedMaxSamples < 0) cachedMaxSamples = 0;
            }
        } catch (Throwable t) {
            cachedMaxSamples = 0;
        }
        return cachedMaxSamples;
    }

    /** Clamp the requested samples to what the GL context actually supports. */
    public static int clampSamples(int requested) {
        if (requested <= 1) return 0;
        int max = maxSamplesSupported();
        if (max <= 1) return 0;
        return Math.min(requested, max);
    }

    private AAConfig() {}
}
