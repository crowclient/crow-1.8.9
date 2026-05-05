package crow.client.module.modules.render.music;

public final class MusicTrack {

    public final String  title;
    public final String  artist;
    public final String  albumArtUrl;
    public final long    progressMs;
    public final long    durationMs;
    public final boolean isPlaying;

    public MusicTrack(String title, String artist, String albumArtUrl,
                      long progressMs, long durationMs, boolean isPlaying) {
        this.title       = title       != null ? title       : "";
        this.artist      = artist      != null ? artist      : "";
        this.albumArtUrl = albumArtUrl != null ? albumArtUrl : "";
        this.progressMs  = progressMs;
        this.durationMs  = durationMs;
        this.isPlaying   = isPlaying;
    }

    public static String formatMs(long ms) {
        long seconds = Math.max(0, ms) / 1000L;
        return (seconds / 60) + ":" + String.format("%02d", seconds % 60);
    }

    public float getProgress() {
        if (durationMs <= 0) return 0f;
        return (float) Math.min(1.0, (double) progressMs / durationMs);
    }
}
