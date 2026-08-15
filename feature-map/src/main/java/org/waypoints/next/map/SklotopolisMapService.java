package org.waypoints.next.map;

import org.waypoints.next.model.ServerIdentity;

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
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Downloads and atomically publishes surface/deed snapshots off the HUD thread. */
public final class SklotopolisMapService {
    private static final int MAXIMUM_SURFACE_BYTES = 16_000_000;
    private static final int MAXIMUM_DEED_BYTES = 2_000_000;

    private final Logger logger;
    private final ScheduledExecutorService worker;
    private volatile ServerMapSnapshot snapshot = ServerMapSnapshot.empty(null);
    private Path cacheRoot;
    private boolean enabled;
    private int syncMinutes;
    private String activeKey = "";
    private ScheduledFuture<?> synchronization;
    private String surfaceEtag = "";
    private String deedsEtag = "";

    public SklotopolisMapService(Logger logger) {
        if (logger == null) throw new IllegalArgumentException("logger is required");
        this.logger = logger;
        this.worker = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "wurm-waypointer-map-sync");
                thread.setDaemon(true);
                return thread;
            }
        });
    }

    public synchronized void configure(boolean nextEnabled, Path nextCacheRoot,
                                       int nextSyncMinutes) {
        cancel();
        if (nextCacheRoot == null) throw new IllegalArgumentException(
                "map cache root is required");
        if (nextSyncMinutes < 1) throw new IllegalArgumentException(
                "map sync minutes must be positive");
        enabled = nextEnabled;
        cacheRoot = nextCacheRoot;
        syncMinutes = nextSyncMinutes;
        activeKey = "";
        snapshot = ServerMapSnapshot.empty(null);
        surfaceEtag = "";
        deedsEtag = "";
    }

    public synchronized void activate(ServerIdentity server) {
        ServerMapProfile profile = SklotopolisMapProfiles.resolve(server);
        String fingerprint = server == null ? "" : server.getEndpointFingerprint();
        String key = profile == null ? "" : fingerprint + "|" + profile.getId();
        if (key.equals(activeKey)) return;
        cancel();
        activeKey = key;
        snapshot = ServerMapSnapshot.empty(profile);
        surfaceEtag = "";
        deedsEtag = "";
        if (!enabled || profile == null || fingerprint.isEmpty()) return;

        final String expectedKey = key;
        final Path directory = cacheRoot.resolve(cacheDirectoryName(
                fingerprint, profile));
        final Path surface = directory.resolve("surface.png");
        final Path deeds = directory.resolve("deeds.snapshot");
        synchronization = worker.scheduleWithFixedDelay(new Runnable() {
            private boolean cacheLoaded;

            @Override public void run() {
                if (!isActive(expectedKey)) return;
                if (!cacheLoaded) {
                    cacheLoaded = true;
                    loadCache(expectedKey, profile, surface, deeds);
                }
                synchronizeSurface(expectedKey, profile, surface);
                synchronizeDeeds(expectedKey, profile, deeds);
            }
        }, 0L, syncMinutes, TimeUnit.MINUTES);
        logger.info("Server map activated: " + profile + ", cache=\""
                + directory.toAbsolutePath().normalize() + "\"");
    }

    public synchronized void deactivate() {
        cancel();
        activeKey = "";
        snapshot = ServerMapSnapshot.empty(null);
        surfaceEtag = "";
        deedsEtag = "";
    }

    public ServerMapSnapshot current() { return snapshot; }

    private void loadCache(String key, ServerMapProfile profile,
                           Path surface, Path deeds) {
        try {
            if (Files.isRegularFile(surface)) {
                byte[] bytes = readFileBounded(surface, MAXIMUM_SURFACE_BYTES);
                validatePng(bytes, profile);
                publishSurface(key, surface, Files.getLastModifiedTime(surface).toMillis(),
                        "cache");
            }
        } catch (Throwable failure) {
            logger.log(Level.WARNING, "Cached server surface was rejected", failure);
        }
        try {
            if (Files.isRegularFile(deeds)) {
                byte[] bytes = readFileBounded(deeds, MAXIMUM_DEED_BYTES);
                List<Deed> parsed = parseDeeds(bytes, profile);
                publishDeeds(key, parsed, Files.getLastModifiedTime(deeds).toMillis(),
                        "cache");
            }
        } catch (Throwable failure) {
            logger.log(Level.WARNING, "Cached deed snapshot was rejected", failure);
        }
    }

    private void synchronizeSurface(String key, ServerMapProfile profile,
                                    Path target) {
        HttpURLConnection connection = null;
        try {
            connection = open(profile.getSurfaceUrl(), target, surfaceEtag,
                    "image/png");
            int status = connection.getResponseCode();
            if (status == HttpURLConnection.HTTP_NOT_MODIFIED) return;
            if (status != HttpURLConnection.HTTP_OK) throw new IOException(
                    "surface HTTP status " + status);
            byte[] bytes = readBounded(connection.getInputStream(),
                    MAXIMUM_SURFACE_BYTES);
            validatePng(bytes, profile);
            if (!isActive(key)) return;
            writeAtomically(target, bytes);
            surfaceEtag = header(connection, "ETag");
            publishSurface(key, target, revision(connection, target), "network");
        } catch (Throwable failure) {
            logger.log(Level.WARNING, "Server surface synchronization failed open",
                    failure);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void synchronizeDeeds(String key, ServerMapProfile profile,
                                  Path target) {
        HttpURLConnection connection = null;
        try {
            connection = open(profile.getDeedsUrl(), target, deedsEtag,
                    "application/json,text/javascript");
            int status = connection.getResponseCode();
            if (status == HttpURLConnection.HTTP_NOT_MODIFIED) return;
            if (status != HttpURLConnection.HTTP_OK) throw new IOException(
                    "deeds HTTP status " + status);
            byte[] bytes = readBounded(connection.getInputStream(), MAXIMUM_DEED_BYTES);
            List<Deed> parsed = parseDeeds(bytes, profile);
            if (!isActive(key)) return;
            writeAtomically(target, bytes);
            deedsEtag = header(connection, "ETag");
            publishDeeds(key, parsed, revision(connection, target), "network");
        } catch (Throwable failure) {
            logger.log(Level.WARNING, "Deed synchronization failed open", failure);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private synchronized void publishSurface(String key, Path surface,
                                             long sourceRevision,
                                             String source) {
        if (!isActive(key)) return;
        ServerMapSnapshot old = snapshot;
        long revision = Math.max(sourceRevision, old.getSurfaceRevision() + 1L);
        snapshot = new ServerMapSnapshot(old.getProfile(), surface,
                revision, old.getDeeds(), old.getDeedsRevision(),
                old.getRevision() + 1L);
        logger.info("Server surface loaded: source=" + source + ", profile="
                + old.getProfile().getId() + ", file=\"" + surface + "\"");
    }

    private synchronized void publishDeeds(String key, List<Deed> deeds,
                                           long sourceRevision,
                                           String source) {
        if (!isActive(key)) return;
        ServerMapSnapshot old = snapshot;
        snapshot = new ServerMapSnapshot(old.getProfile(), old.getSurfaceImage(),
                old.getSurfaceRevision(), deeds, sourceRevision,
                old.getRevision() + 1L);
        logger.info("Deeds loaded: source=" + source + ", profile="
                + old.getProfile().getId() + ", deeds=" + deeds.size());
    }

    private synchronized boolean isActive(String key) {
        return key != null && key.equals(activeKey);
    }

    private static HttpURLConnection open(String url, Path cache, String etag,
                                          String accept) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(20000);
        connection.setUseCaches(false);
        connection.setRequestProperty("Accept", accept);
        connection.setRequestProperty("User-Agent", "Wurm-Waypointer/0.8");
        if (etag != null && !etag.isEmpty()) {
            connection.setRequestProperty("If-None-Match", etag);
        }
        if (Files.isRegularFile(cache)) {
            connection.setIfModifiedSince(Files.getLastModifiedTime(cache).toMillis());
        }
        return connection;
    }

    private static List<Deed> parseDeeds(byte[] bytes, ServerMapProfile profile) {
        return DeedParser.parse(new String(bytes, StandardCharsets.UTF_8),
                profile.getMapWidth(), profile.getMapHeight());
    }

    private static void validatePng(byte[] bytes, ServerMapProfile profile)
            throws IOException {
        if (bytes == null || bytes.length < 24
                || (bytes[0] & 0xff) != 0x89 || bytes[1] != 'P'
                || bytes[2] != 'N' || bytes[3] != 'G') {
            throw new IOException("surface is not a PNG");
        }
        int width = integer(bytes, 16);
        int height = integer(bytes, 20);
        if (width != profile.getMapWidth() || height != profile.getMapHeight()) {
            throw new IOException("surface dimensions " + width + "x" + height
                    + " do not match profile " + profile.getMapWidth() + "x"
                    + profile.getMapHeight());
        }
    }

    private static int integer(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xff) << 24)
                | ((bytes[offset + 1] & 0xff) << 16)
                | ((bytes[offset + 2] & 0xff) << 8)
                | (bytes[offset + 3] & 0xff);
    }

    private static byte[] readFileBounded(Path file, int limit) throws IOException {
        long size = Files.size(file);
        if (size < 1L || size > limit) throw new IOException(
                "cached map payload has invalid size " + size);
        return Files.readAllBytes(file);
    }

    private static byte[] readBounded(InputStream input, int limit)
            throws IOException {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream(65536);
            byte[] buffer = new byte[8192];
            int count;
            int total = 0;
            while ((count = input.read(buffer)) >= 0) {
                total += count;
                if (total > limit) throw new IOException(
                        "downloaded map payload is oversized");
                output.write(buffer, 0, count);
            }
            if (total < 1) throw new IOException("downloaded map payload is empty");
            return output.toByteArray();
        } finally {
            input.close();
        }
    }

    private static void writeAtomically(Path target, byte[] bytes)
            throws IOException {
        Path absolute = target.toAbsolutePath().normalize();
        Path parent = absolute.getParent();
        if (parent == null) throw new IOException("map cache has no parent");
        Files.createDirectories(parent);
        Path temporary = parent.resolve(target.getFileName().toString() + ".tmp");
        Files.write(temporary, bytes);
        try {
            Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static long revision(HttpURLConnection connection, Path target)
            throws IOException {
        long value = connection.getLastModified();
        return value > 0L ? value : Files.getLastModifiedTime(target).toMillis();
    }

    private static String header(HttpURLConnection connection, String name) {
        String value = connection.getHeaderField(name);
        return value == null ? "" : value;
    }

    private static String fingerprintDirectory(String serverKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(serverKey.toLowerCase(Locale.ENGLISH)
                    .getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(24);
            for (int i = 0; i < 12; i++) result.append(String.format(
                    Locale.ENGLISH, "%02x", Integer.valueOf(bytes[i] & 0xff)));
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    static String cacheDirectoryName(String fingerprint,
                                     ServerMapProfile profile) {
        if (profile == null) throw new IllegalArgumentException(
                "map profile is required");
        return fingerprintDirectory(fingerprint + "|" + profile.getId());
    }

    private synchronized void cancel() {
        if (synchronization != null) synchronization.cancel(false);
        synchronization = null;
    }
}
