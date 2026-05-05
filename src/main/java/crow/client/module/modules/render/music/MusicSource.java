package crow.client.module.modules.render.music;

import java.awt.image.BufferedImage;

public interface MusicSource {

    void start(String token);

    void stop();

    void setToken(String token);

    default void setSecondaryToken(String token) {}

    MusicTrack getCurrentTrack();

    String getStatus();

    BufferedImage takeAlbumImage();

    void sendPlay();
    void sendPause();
    void sendNext();
    void sendPrevious();

    boolean supportsControls();
}
