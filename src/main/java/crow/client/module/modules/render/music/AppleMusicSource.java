package crow.client.module.modules.render.music;

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

public class AppleMusicSource implements MusicSource {

    private static final String BASE_URL           = "https://api.music.apple.com/v1/";
    private static final int    CONNECT_TIMEOUT_MS  = 5000;
    private static final int    READ_TIMEOUT_MS     = 5000;

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "AppleMusicSource");
        t.setDaemon(true);
        return t;
    });

    private volatile String devToken  = "";

    private volatile String userToken = "";

    private volatile MusicTrack currentTrack = null;
    private volatile String     status       = "No tokens set";

    private final AtomicReference<BufferedImage> pendingAlbumImage = new AtomicReference<>(null);
    private volatile String lastAlbumUrl = "";

    private ScheduledFuture<?> pollTask;

    @Override
    public void start(String token) {
        setToken(token);
        if (pollTask != null && !pollTask.isDone()) pollTask.cancel(false);
        pollTask = executor.scheduleAtFixedRate(this::poll, 0L, 5000L, TimeUnit.MILLISECONDS);
    }

    @Override
    public void stop() {
        if (pollTask != null) { pollTask.cancel(false); pollTask = null; }
        currentTrack = null;
        status = "Disconnected";
    }

    @Override
    public void setToken(String token) {
        this.userToken = (token == null) ? "" : token.trim();
    }

    @Override
    public void setSecondaryToken(String token) {
        this.devToken = (token == null) ? "" : token.trim();
    }

    @Override
    public MusicTrack getCurrentTrack() { return currentTrack; }

    @Override
    public String getStatus() { return status; }

    @Override
    public BufferedImage takeAlbumImage() {
        return pendingAlbumImage.getAndSet(null);
    }

    @Override
    public void sendPlay()     {  }

    @Override
    public void sendPause()    {  }

    @Override
    public void sendNext()     {  }

    @Override
    public void sendPrevious() {  }

    @Override
    public boolean supportsControls() { return false; }

    private void poll() {
        if (devToken.isEmpty() || userToken.isEmpty()) {
            currentTrack = null;
            status = devToken.isEmpty() ? "No dev token" : "No user token";
            return;
        }
        try {

            HttpURLConnection c = open(BASE_URL + "me/recent/played/tracks?limit=1", "GET");
            int code = c.getResponseCode();

            if (code == 401) {
                currentTrack = null;
                status = "Token expired";
                c.disconnect();
                return;
            }
            if (code == 403) {
                currentTrack = null;
                status = "Unauthorized";
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
            parseRecentTrack(body);

        } catch (Exception e) {
            status = "Offline";
        }
    }

    private void parseRecentTrack(String json) {
        try {
            JsonObject root = new JsonParser().parse(json).getAsJsonObject();
            JsonArray  data = root.getAsJsonArray("data");

            if (data == null || data.size() == 0) {
                currentTrack = null;
                status = "Nothing played";
                return;
            }

            JsonObject track      = data.get(0).getAsJsonObject();
            JsonObject attributes = track.getAsJsonObject("attributes");

            String title  = attributes.get("name").getAsString();
            String artist = attributes.has("artistName")
                    ? attributes.get("artistName").getAsString() : "";
            long   durMs  = attributes.has("durationInMillis")
                    ? attributes.get("durationInMillis").getAsLong() : 0L;

            String artUrl = "";
            try {
                JsonObject artwork = attributes.getAsJsonObject("artwork");
                if (artwork != null && artwork.has("url")) {
                    String template = artwork.get("url").getAsString();

                    artUrl = template.replace("{w}", "300").replace("{h}", "300");
                }
            } catch (Exception ignored) {}

            currentTrack = new MusicTrack(title, artist, artUrl, 0L, durMs, false);
            status = "Recently played";

            if (!artUrl.isEmpty() && !artUrl.equals(lastAlbumUrl)) {
                lastAlbumUrl = artUrl;
                final String urlToFetch = artUrl;
                executor.submit(() -> downloadAlbumArt(urlToFetch));
            }

        } catch (Exception ignored) {
            status = "Parse error";
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
            if (img != null) pendingAlbumImage.set(img);
        } catch (Exception ignored) {}
    }

    private HttpURLConnection open(String urlStr, String method) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
        c.setRequestMethod(method);
        c.setRequestProperty("Authorization",    "Bearer " + devToken);
        c.setRequestProperty("Music-User-Token", userToken);
        c.setRequestProperty("Accept",           "application/json");
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
