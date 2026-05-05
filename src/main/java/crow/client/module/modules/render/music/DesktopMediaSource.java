package crow.client.module.modules.render.music;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class DesktopMediaSource implements MusicSource {

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "DesktopMediaSource");
        t.setDaemon(true);
        return t;
    });

    private volatile MusicTrack currentTrack;
    private volatile String status = "No desktop media";
    private ScheduledFuture<?> pollTask;

    @Override
    public void start(String token) {
        if (pollTask != null && !pollTask.isDone()) {
            pollTask.cancel(false);
        }
        pollTask = executor.scheduleAtFixedRate(this::poll, 0L, 1800L, TimeUnit.MILLISECONDS);
    }

    @Override
    public void stop() {
        if (pollTask != null) {
            pollTask.cancel(false);
            pollTask = null;
        }
        currentTrack = null;
        status = "Disconnected";
    }

    @Override
    public void setToken(String token) {
    }

    @Override
    public MusicTrack getCurrentTrack() {
        return currentTrack;
    }

    @Override
    public String getStatus() {
        return status;
    }

    @Override
    public BufferedImage takeAlbumImage() {
        return null;
    }

    @Override
    public void sendPlay() {
    }

    @Override
    public void sendPause() {
    }

    @Override
    public void sendNext() {
    }

    @Override
    public void sendPrevious() {
    }

    @Override
    public boolean supportsControls() {
        return false;
    }

    private void poll() {
        String json = runPowerShell(buildMediaQuery());
        if (json == null || json.trim().isEmpty()) {
            currentTrack = null;
            status = "No desktop media";
            return;
        }

        try {
            JsonObject obj = new JsonParser().parse(json.trim()).getAsJsonObject();
            String title = getString(obj, "title");
            String artist = getString(obj, "artist");
            long duration = getLong(obj, "duration");
            long position = getLong(obj, "position");
            boolean playing = obj.has("playing") && obj.get("playing").getAsBoolean();

            if ((title == null || title.trim().isEmpty()) && obj.has("windowTitle")) {
                title = obj.get("windowTitle").getAsString();
            }

            if ((artist == null || artist.isEmpty()) && title != null && title.contains(" - ")) {
                String[] parts = title.split(" - ", 2);
                if (parts.length == 2) {
                    artist = parts[0].trim();
                    title = parts[1].trim();
                }
            }

            if (title == null || title.trim().isEmpty()) {
                currentTrack = null;
                status = "No desktop media";
                return;
            }

            currentTrack = new MusicTrack(title, artist, "", Math.max(0L, position), Math.max(0L, duration), playing);
            status = playing ? "Playing on desktop" : "Paused on desktop";
        } catch (Exception ignored) {
            currentTrack = null;
            status = "Desktop media unavailable";
        }
    }

    private String buildMediaQuery() {
        return "[Console]::OutputEncoding=[System.Text.Encoding]::UTF8; " +
                "try { " +
                "Add-Type -AssemblyName System.Runtime.WindowsRuntime -ErrorAction SilentlyContinue | Out-Null; " +
                "[void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType=WindowsRuntime]; " +
                "$m=[Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync().GetAwaiter().GetResult(); " +
                "$s=$m.GetCurrentSession(); " +
                "if($s -ne $null){ " +
                "$p=$s.TryGetMediaPropertiesAsync().GetAwaiter().GetResult(); " +
                "$t=$s.GetTimelineProperties(); " +
                "$i=$s.GetPlaybackInfo(); " +
                "[pscustomobject]@{title=[string]$p.Title;artist=[string]$p.Artist;duration=[int64]$t.EndTime.TotalMilliseconds;position=[int64]$t.Position.TotalMilliseconds;playing=($i.PlaybackStatus.ToString() -eq 'Playing')} | ConvertTo-Json -Compress; " +
                "exit } " +
                "} catch {} " +
                "$candidates=Get-Process | Where-Object { $_.MainWindowTitle -and ($_.ProcessName -match 'Spotify|AppleMusic|iTunes|chrome|msedge|firefox') } | Select-Object -First 10 ProcessName,MainWindowTitle; " +
                "foreach($c in $candidates){ " +
                "$title=[string]$c.MainWindowTitle; " +
                "if($title -match ' - (Spotify|YouTube Music|SoundCloud|Apple Music)'){ [pscustomobject]@{title=$title;artist='';duration=0;position=0;playing=$true;windowTitle=$title} | ConvertTo-Json -Compress; break } " +
                "}";
    }

    private String runPowerShell(String script) {
        Process process = null;
        try {
            process = new ProcessBuilder("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", script)
                    .redirectErrorStream(true)
                    .start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder out = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    out.append(line);
                }
                process.waitFor(2500L, TimeUnit.MILLISECONDS);
                return out.toString();
            }
        } catch (Exception ignored) {
            return null;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    private String getString(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
    }

    private long getLong(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsLong() : 0L;
    }
}
