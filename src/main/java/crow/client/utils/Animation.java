package crow.client.utils;

public final class Animation {

    @FunctionalInterface
    public interface EasingFunction {
        float ease(float t);
    }

    private float current;
    private float target;
    private float startValue;
    private long startTime;
    private final int durationMs;
    private final EasingFunction easing;
    private boolean transitioning;

    public Animation(int durationMs, EasingFunction easing) {
        this.durationMs = durationMs;
        this.easing = easing;
    }

    public Animation(int durationMs) {
        this(durationMs, Animation::easeOutCubic);
    }

    public void setTarget(float target) {
        if (this.target == target) return;
        this.startValue = this.current;
        this.target = target;
        this.startTime = System.currentTimeMillis();
        this.transitioning = true;
    }

    public void set(float value) {
        this.current = value;
        this.target = value;
        this.transitioning = false;
    }

    public void update() {
        if (!transitioning) return;
        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed >= durationMs) {
            current = target;
            transitioning = false;
            return;
        }
        float rawT = (float) elapsed / durationMs;
        float easedT = easing.ease(rawT);
        current = startValue + (target - startValue) * easedT;
    }

    public float get() {
        return current;
    }

    public float getTarget() {
        return target;
    }

    public boolean isAnimating() {
        return transitioning;
    }

    public static float easeOutCubic(float t) {
        float t1 = t - 1.0F;
        return 1.0F + t1 * t1 * t1;
    }

    public static float easeInOutQuart(float t) {
        if (t < 0.5F) {
            return 8.0F * t * t * t * t;
        }
        float t1 = t - 1.0F;
        return 1.0F - 8.0F * t1 * t1 * t1 * t1;
    }

    public static float easeOutBack(float t) {
        float c1 = 1.70158F;
        float c3 = c1 + 1.0F;
        float t1 = t - 1.0F;
        return 1.0F + c3 * t1 * t1 * t1 + c1 * t1 * t1;
    }

    public static float easeOutQuad(float t) {
        return 1.0F - (1.0F - t) * (1.0F - t);
    }

    public static float linear(float t) {
        return t;
    }
}
