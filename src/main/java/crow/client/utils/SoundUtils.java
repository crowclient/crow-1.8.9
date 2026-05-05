package crow.client.utils;

import java.io.InputStream;
import java.net.URL;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;

public class SoundUtils {

    private static final Object LOOP_LOCK = new Object();

    private static Clip loopClip;
    private static String currentLoopName;

    private static final ConcurrentMap<String, CachedSound> CACHE = new ConcurrentHashMap<>();

    public static void playSound(String name) {
        if (name == null) return;
        try {
            CachedSound cached = loadCached(name);
            if (cached == null) return;

            final Clip clip;
            try {
                clip = AudioSystem.getClip();
            } catch (Throwable t) {

                return;
            }

            clip.addLineListener(event -> {
                if (event.getType() == LineEvent.Type.STOP) {
                    try { event.getLine().close(); } catch (Throwable ignored) {}
                }
            });
            try {
                clip.open(cached.format, cached.data, 0, cached.data.length);
                clip.start();
            } catch (Throwable t) {
                try { clip.close(); } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {

        }
    }

    private static CachedSound loadCached(String name) {
        CachedSound cached = CACHE.get(name);
        if (cached != null) return cached;

        URL resource = resolveSound(name);
        if (resource == null) {
            return null;
        }

        try (AudioInputStream in = AudioSystem.getAudioInputStream(resource)) {
            AudioFormat format = in.getFormat();
            byte[] data = readAll(in);
            CachedSound built = new CachedSound(format, data);
            CachedSound existing = CACHE.putIfAbsent(name, built);
            return existing != null ? existing : built;
        } catch (Throwable t) {
            return null;
        }
    }

    private static URL resolveSound(String name) {
        String base = "/assets/crow/sounds/";
        URL r = SoundUtils.class.getResource(base + name + ".wav");
        if (r != null) return r;
        if (name.length() > 0) {
            String capitalized = Character.toUpperCase(name.charAt(0)) + name.substring(1);
            r = SoundUtils.class.getResource(base + capitalized + ".wav");
            if (r != null) return r;
        }

        if (name.startsWith("old") && name.length() > 3) {
            String camel = "old" + Character.toUpperCase(name.charAt(3)) + name.substring(4);
            r = SoundUtils.class.getResource(base + camel + ".wav");
            if (r != null) return r;
        }
        return null;
    }

    private static byte[] readAll(InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toByteArray();
    }

    private static final class CachedSound {
        final AudioFormat format;
        final byte[] data;
        CachedSound(AudioFormat format, byte[] data) { this.format = format; this.data = data; }
    }

    public static void startLoop(String name) {
        if (name == null) return;
        synchronized (LOOP_LOCK) {
            try {
                if (loopClip != null && loopClip.isRunning() && name.equals(currentLoopName)) {
                    return;
                }
            } catch (Throwable ignored) {

            }

            stopLoopLocked();

            try {
                URL resource = resolveSound(name);
                if (resource == null) {
                    return;
                }

                Clip clip = AudioSystem.getClip();
                clip.open(AudioSystem.getAudioInputStream(resource));

                if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                    FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                    gain.setValue(-6.0f);
                }

                clip.loop(Clip.LOOP_CONTINUOUSLY);
                loopClip = clip;
                currentLoopName = name;
            } catch (Throwable t) {
                if (loopClip != null) {
                    try { loopClip.close(); } catch (Throwable ignored) {}
                }
                loopClip = null;
                currentLoopName = null;
            }
        }
    }

    public static void stopLoop() {
        synchronized (LOOP_LOCK) {
            stopLoopLocked();
        }
    }

    private static void stopLoopLocked() {
        if (loopClip != null) {
            try { loopClip.stop(); } catch (Throwable ignored) {}
            try { loopClip.close(); } catch (Throwable ignored) {}
            loopClip = null;
            currentLoopName = null;
        }
    }

    public static void setLoopMuted(boolean muted) {
        synchronized (LOOP_LOCK) {
            if (loopClip == null) return;
            try {
                if (loopClip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                    FloatControl gain = (FloatControl) loopClip.getControl(FloatControl.Type.MASTER_GAIN);
                    gain.setValue(muted ? gain.getMinimum() : -6.0f);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    public static boolean isLooping() {
        synchronized (LOOP_LOCK) {
            try {
                return loopClip != null && loopClip.isRunning();
            } catch (Throwable t) {
                return false;
            }
        }
    }
}
