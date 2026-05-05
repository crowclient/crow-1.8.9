package crow.client.utils.anim;

public final class Animatable {

    private float value;

    private float target;

    private float speed;

    private long lastNanos;

    public Animatable(float initial, float speed) {
        this.value = initial;
        this.target = initial;
        this.speed = speed;
        this.lastNanos = System.nanoTime();
    }

    public void setTarget(float target) {
        this.target = target;
    }

    public float getTarget() { return target; }

    public float get() { return value; }

    public void setSpeed(float speed) { this.speed = speed; }
    public float getSpeed() { return speed; }

    public void snap(float v) {
        this.value = v;
        this.target = v;
        this.lastNanos = System.nanoTime();
    }

    public void jumpTargetTo(float t) { this.target = t; }

    public void update() {
        long now = System.nanoTime();
        float dt = (now - lastNanos) / 1_000_000_000f;
        lastNanos = now;

        if (dt > 0.1f) dt = 0.1f;
        if (dt <= 0f) return;
        value += (target - value) * (1f - (float) Math.exp(-dt * speed));
    }

    public boolean isAtRest(float epsilon) {
        return Math.abs(target - value) <= epsilon;
    }
}
