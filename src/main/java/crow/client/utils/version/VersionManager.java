package crow.client.utils.version;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.Scanner;

public class VersionManager {
    private final String versionFilePath = "/assets/crow/version";
    private final String branchFilePath = "/assets/crow/branch";
    private final String versionUrl = "";
    private final String branchUrl = "";

    private static final int CONNECT_TIMEOUT_MS = 3000;
    private static final int READ_TIMEOUT_MS = 3000;

    private volatile Version latestVersion;
    private Version clientVersion;

    public VersionManager() {
        createClientVersion();

        this.latestVersion = this.clientVersion;

        Thread fetch = new Thread(this::fetchLatestVersion, "Crow-VersionFetch");
        fetch.setDaemon(true);
        fetch.start();
    }

    private void fetchLatestVersion() {
        String version = clientVersion.getVersion();
        String branch = clientVersion.getBranchName();
        int branchCommit = clientVersion.getBranchCommit();

        String fetched = readUrlLine(versionUrl);
        if (fetched != null) version = fetched;

        String branchLine = readUrlLine(branchUrl);
        if (branchLine != null) {
            String[] parts = branchLine.split("-");
            if (parts.length >= 1) branch = parts[0];
            if (parts.length >= 2) {
                try {
                    branchCommit = Integer.parseInt(parts[1]);
                } catch (NumberFormatException ignored) {
                }
            }
        }

        this.latestVersion = new Version(version, branch, branchCommit);
    }

    private String readUrlLine(String urlStr) {
        if (urlStr == null || urlStr.trim().isEmpty()) {
            return null;
        }

        try {
            URLConnection conn = new URL(urlStr).openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                return reader.readLine();
            }
        } catch (Exception e) {
            return null;
        }
    }

    private void createClientVersion() {
        String version = "1.0.0";
        String branch = "";
        int branchCommit = 0;

        try (InputStream input = VersionManager.class.getResourceAsStream(versionFilePath)) {
            if (input != null) {
                try (Scanner scanner = new Scanner(input)) {
                    if (scanner.hasNextLine()) version = scanner.nextLine();
                }
            }
        } catch (Exception ignored) {
        }

        try (InputStream input = VersionManager.class.getResourceAsStream(branchFilePath)) {
            if (input != null) {
                try (Scanner scanner = new Scanner(input)) {
                    if (scanner.hasNextLine()) {
                        String[] line = scanner.nextLine().split("-");
                        if (line.length >= 1) branch = line[0];
                        if (line.length >= 2) {
                            try {
                                branchCommit = Integer.parseInt(line[1]);
                            } catch (NumberFormatException ignored) {
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }

        this.clientVersion = new Version(version, branch, branchCommit);
    }

    public Version getClientVersion() {
        return clientVersion;
    }

    public Version getLatestVersion() {
        return latestVersion;
    }
}
