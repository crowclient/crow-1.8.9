package crow.client.module.modules.render.spotify;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class SpotifyPoller {

    private static final String BASE_URL = "https://api.spotify.com/v1/me/player/";
    private static final int    CONNECT_TIMEOUT_MS = 4000;
    private static final int    READ_TIMEOUT_MS    = 4000;

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "SpotifyPoller");
        t.setDaemon(true);
        return t;
    });

    private volatile String token = "";
    private volatile SpotifyTrack currentTrack = null;
    private volatile String  status = "No token set";

    private final AtomicReference<BufferedImage> pendingAlbumImage = new AtomicReference<>(null);

    private volatile String lastAlbumUrl = "";

    private ScheduledFuture<?> pollTask;

    public void start(String token) {
        setToken(token);
        if (pollTask != null && !pollTask.isDone()) pollTask.cancel(false);
        pollTask = executor.scheduleAtFixedRate(this::poll, 0L, 1500L, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        if (pollTask != null) { pollTask.cancel(false); pollTask = null; }
        currentTrack = null;
        status = "Disconnected";
    }

    public void setToken(String token) {
        this.token = (token == null) ? "" : token.trim();
    }

    public SpotifyTrack getCurrentTrack() { return currentTrack; }
    public String       getStatus()       { return status; }

    public BufferedImage takeAlbumImage() {
        return pendingAlbumImage.getAndSet(null);
    }

    public void sendPlay()     { sendCommand("PUT",  "play"); }
    public void sendPause()    { sendCommand("PUT",  "pause"); }
    public void sendNext()     { sendCommand("POST", "next"); }
    public void sendPrevious() { sendCommand("POST", "previous"); }

    private void sendCommand(final String method, final String endpoint) {
        executor.submit(() -> {
            try {
                HttpURLConnection c = open(BASE_URL + endpoint, method);
                if ("PUT".equals(method) || "POST".equals(method)) {
                    c.setDoOutput(true);
                    c.setRequestProperty("Content-Length", "0");
                    c.getOutputStream().close();
                }
                int code = c.getResponseCode();
                c.disconnect();

                if (code == 403) status = "Premium required";
            } catch (Exception ignored) {}
        });

        executor.schedule(this::poll, 350L, TimeUnit.MILLISECONDS);
    }

    private void poll() {
        if (token.isEmpty()) {
            currentTrack = null;
            status = "No token set";
            return;
        }
        try {
            HttpURLConnection c = open(BASE_URL + "currently-playing", "GET");
            int code = c.getResponseCode();

            if (code == 204) {
                currentTrack = null;
                status = "Nothing playing";
                c.disconnect();
                return;
            }
            if (code == 401) {
                currentTrack = null;
                status = "Token expired";
                c.disconnect();
                return;
            }
            if (code != 200) {
                status = "HTTP " + code;
                c.disconnect();
                return;
            }

            String body = readStream(c.getInputStream());
            c.disconnect();
            parseTrack(body);

        } catch (Exception e) {

            status = "Offline";
        }
    }

    private void parseTrack(String json) {
        try {
            JsonObject root = new JsonParser().parse(json).getAsJsonObject();

            if (!root.has("item") || root.get("item").isJsonNull()) {
                currentTrack = null;
                status = "Nothing playing";
                return;
            }

            JsonObject item    = root.get("item").getAsJsonObject();
            String     title   = item.get("name").getAsString();
            boolean    playing = root.get("is_playing").getAsBoolean();
            long       progMs  = root.get("progress_ms").getAsLong();
            long       durMs   = item.get("duration_ms").getAsLong();

            JsonArray artistArr = item.get("artists").getAsJsonArray();
            StringBuilder artistSb = new StringBuilder();
            for (int i = 0; i < artistArr.size(); i++) {
                if (i > 0) artistSb.append(", ");
                artistSb.append(artistArr.get(i).getAsJsonObject().get("name").getAsString());
            }

            String artUrl = "";
            try {
                JsonArray images = item.get("album").getAsJsonObject().get("images").getAsJsonArray();
                if (images.size() > 0) {
                    int idx = Math.min(1, images.size() - 1);
                    artUrl = images.get(idx).getAsJsonObject().get("url").getAsString();
                }
            } catch (Exception ignored) {}

            currentTrack = new SpotifyTrack(title, artistSb.toString(), artUrl, progMs, durMs, playing);
            status = playing ? "Playing" : "Paused";

            if (!artUrl.isEmpty() && !artUrl.equals(lastAlbumUrl)) {
                lastAlbumUrl = artUrl;
                final String urlToFetch = artUrl;
                executor.submit(() -> downloadAlbumArt(urlToFetch));
            }

        } catch (Exception ignored) {

        }
    }

    private void downloadAlbumArt(String urlStr) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
            c.setConnectTimeout(6000);
            c.setReadTimeout(6000);
            c.setRequestProperty("User-Agent", "CrowMC/1.0");
            BufferedImage img = ImageIO.read(c.getInputStream());
            c.disconnect();
            if (img != null) {
                pendingAlbumImage.set(img);
            }
        } catch (Exception ignored) {}
    }

    private HttpURLConnection open(String urlStr, String method) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
        c.setRequestMethod(method);
        c.setRequestProperty("Authorization", "Bearer " + token);
        c.setRequestProperty("Accept", "application/json");
        c.setConnectTimeout(CONNECT_TIMEOUT_MS);
        c.setReadTimeout(READ_TIMEOUT_MS);
        return c;
    }

    private String readStream(InputStream is) throws Exception {
        BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line);
        return sb.toString();
    }
}
