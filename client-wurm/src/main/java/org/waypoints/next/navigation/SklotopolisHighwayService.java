package org.waypoints.next.navigation;

import org.waypoints.next.model.ServerIdentity;
import org.waypoints.next.map.ServerMapProfile;
import org.waypoints.next.map.SklotopolisMapProfiles;
import org.waypoints.next.render.WaypointRenderConfiguration;
import org.waypoints.next.render.NavigationHighwaySource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Downloads, validates, caches, and atomically publishes Sklotopolis Highways. */
public final class SklotopolisHighwayService implements NavigationHighwaySource {
    private static final int MAXIMUM_BYTES = 5_000_000;
    private final Logger logger;
    private final ScheduledExecutorService worker;
    private volatile HighwayTileIndex index = HighwayTileIndex.empty();
    private volatile long revision;
    private WaypointRenderConfiguration configuration =
            WaypointRenderConfiguration.defaults();
    private ScheduledFuture<?> synchronization;
    private String activeKey = "";
    private String etag = "";
    private long lastModified;

    public SklotopolisHighwayService(Logger logger) {
        if (logger == null) throw new IllegalArgumentException("logger is required");
        this.logger = logger;
        this.worker = Executors.newSingleThreadScheduledExecutor(
                new ThreadFactory() {
                    @Override public Thread newThread(Runnable runnable) {
                        Thread thread = new Thread(runnable,
                                "wurm-waypointer-highways-sync");
                        thread.setDaemon(true);
                        return thread;
                    }
                });
    }

    public synchronized void configure(WaypointRenderConfiguration value) {
        cancelSynchronization();
        configuration = value == null
                ? WaypointRenderConfiguration.defaults() : value;
        activeKey = "";
        index = HighwayTileIndex.empty();
        revision++;
        etag = "";
        lastModified = 0L;
    }

    public synchronized void activate(ServerIdentity server) {
        ServerMapProfile profile = SklotopolisMapProfiles.resolve(server);
        String url = profile == null ? "" : profile.getHighwaysUrl();
        String serverKey = server == null ? "" : server.getEndpointFingerprint();
        String nextKey = serverKey + "|" + url + "|"
                + (profile == null ? configuration.getMapWidth()
                : profile.getMapWidth()) + "x"
                + (profile == null ? configuration.getMapHeight()
                : profile.getMapHeight());
        if (nextKey.equals(activeKey)) return;
        cancelSynchronization();
        activeKey = nextKey;
        index = HighwayTileIndex.empty();
        revision++;
        etag = "";
        lastModified = 0L;
        if (!configuration.isNavigationHighwaysEnabled() || url.isEmpty()) {
            logger.info("Published Highways unavailable for server \""
                    + oneLine(serverKey) + "\"; terrain-only navigation active");
            return;
        }
        final String expectedKey = nextKey;
        final Path cacheFile = cacheFile(serverKey);
        final int width = profile == null
                ? configuration.getMapWidth() : profile.getMapWidth();
        final int height = profile == null
                ? configuration.getMapHeight() : profile.getMapHeight();
        synchronization = worker.scheduleWithFixedDelay(new Runnable() {
            private boolean cacheLoaded;

            @Override public void run() {
                if (!isActive(expectedKey)) return;
                if (!cacheLoaded) {
                    cacheLoaded = true;
                    try {
                        loadCache(expectedKey, cacheFile, width, height);
                    } catch (Throwable failure) {
                        logger.log(Level.WARNING,
                                "Cached Highways snapshot was rejected; refreshing",
                                failure);
                    }
                }
                try {
                    synchronize(expectedKey, url, cacheFile, width, height);
                } catch (Throwable failure) {
                    logger.log(Level.WARNING,
                            "Published Highways synchronization failed open", failure);
                }
            }
        }, 0L, configuration.getNavigationHighwaysSyncMinutes(),
                TimeUnit.MINUTES);
    }

    public synchronized void deactivate() {
        cancelSynchronization();
        activeKey = "";
        index = HighwayTileIndex.empty();
        revision++;
        etag = "";
        lastModified = 0L;
    }

    @Override public HighwayTileIndex current() { return index; }
    @Override public long revision() { return revision; }

    static String sourceUrl(ServerIdentity server) {
        ServerMapProfile profile = SklotopolisMapProfiles.resolve(server);
        return profile == null ? "" : profile.getHighwaysUrl();
    }

    private synchronized boolean isActive(String expectedKey) {
        return expectedKey.equals(activeKey);
    }

    private void loadCache(String expectedKey, Path cacheFile, int width,
                           int height) throws IOException {
        if (!Files.isRegularFile(cacheFile)) return;
        byte[] bytes = Files.readAllBytes(cacheFile);
        if (bytes.length > MAXIMUM_BYTES) throw new IOException(
                "cached highways snapshot is oversized");
        publish(expectedKey, HighwayTileIndex.parse(
                new String(bytes, StandardCharsets.UTF_8), width, height),
                "cache", cacheFile);
    }

    private void synchronize(String expectedKey, String url, Path cacheFile,
                             int width, int height) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url)
                .openConnection();
        try {
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(15000);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "application/json,text/javascript");
            connection.setRequestProperty("User-Agent", "Wurm-Waypointer/0.4");
            synchronized (this) {
                if (!isActive(expectedKey)) return;
                if (!etag.isEmpty()) connection.setRequestProperty(
                        "If-None-Match", etag);
                if (lastModified > 0L) connection.setIfModifiedSince(lastModified);
            }
            int status = connection.getResponseCode();
            if (status == HttpURLConnection.HTTP_NOT_MODIFIED) return;
            if (status != HttpURLConnection.HTTP_OK) throw new IOException(
                    "highways HTTP status " + status);
            byte[] bytes = readBounded(connection.getInputStream());
            HighwayTileIndex parsed = HighwayTileIndex.parse(
                    new String(bytes, StandardCharsets.UTF_8), width, height);
            if (!isActive(expectedKey)) return;
            writeAtomically(cacheFile, bytes);
            synchronized (this) {
                if (!isActive(expectedKey)) return;
                String receivedEtag = connection.getHeaderField("ETag");
                etag = receivedEtag == null ? "" : receivedEtag;
                lastModified = connection.getLastModified();
            }
            publish(expectedKey, parsed, "network", cacheFile);
        } finally {
            connection.disconnect();
        }
    }

    private synchronized void publish(String expectedKey,
                                      HighwayTileIndex parsed, String source,
                                      Path cacheFile) {
        if (!isActive(expectedKey)) return;
        index = parsed;
        revision++;
        logger.info("Published Highways loaded: source=" + source
                + ", segments=" + parsed.getSegments().size()
                + ", tiles=" + parsed.getTileCount() + ", cache=\""
                + cacheFile + "\"");
    }

    private Path cacheFile(String serverKey) {
        String directory = fingerprintDirectory(serverKey);
        return configuration.getNavigationHighwaysCacheDirectory()
                .resolve(directory).resolve("highways.snapshot");
    }

    private static String fingerprintDirectory(String serverKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(serverKey.toLowerCase(Locale.ENGLISH)
                    .getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(24);
            for (int i = 0; i < 12; i++) {
                result.append(String.format(Locale.ENGLISH, "%02x",
                        Integer.valueOf(bytes[i] & 0xff)));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static byte[] readBounded(InputStream input) throws IOException {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream(65536);
            byte[] buffer = new byte[8192];
            int count;
            int total = 0;
            while ((count = input.read(buffer)) >= 0) {
                total += count;
                if (total > MAXIMUM_BYTES) throw new IOException(
                        "downloaded highways snapshot is oversized");
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        } finally {
            input.close();
        }
    }

    private static void writeAtomically(Path target, byte[] bytes)
            throws IOException {
        Path parent = target.toAbsolutePath().normalize().getParent();
        if (parent == null) throw new IOException("highways cache has no parent");
        Files.createDirectories(parent);
        Path temporary = parent.resolve(target.getFileName().toString() + ".tmp");
        Files.write(temporary, bytes);
        try {
            Files.move(temporary, target.toAbsolutePath().normalize(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary, target.toAbsolutePath().normalize(),
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private synchronized void cancelSynchronization() {
        if (synchronization != null) synchronization.cancel(false);
        synchronization = null;
    }

    private static String oneLine(String value) {
        return value == null ? "" : value.replace('\r', ' ').replace('\n', ' ');
    }
}
