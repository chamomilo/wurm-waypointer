package org.waypoints.next.deeds;

import org.waypoints.next.model.ServerIdentity;

import javax.net.ssl.HttpsURLConnection;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Background HTTPS/local-file provider with bounded IO, conditional requests,
 * exponential retry backoff, and an atomic last-known-good raw cache.
 */
public final class CachingDeedProvider implements DeedProvider {
    private static final long AUTOMATIC_REFRESH_MILLIS = TimeUnit.MINUTES.toMillis(15);
    private static final long MINIMUM_BACKOFF_MILLIS = TimeUnit.SECONDS.toMillis(30);
    private static final long MAXIMUM_BACKOFF_MILLIS = TimeUnit.MINUTES.toMillis(30);

    private final String providerKey;
    private final List<DeedProviderMapping> mappings;
    private final Path cacheDirectory;
    private final boolean localFilesAllowed;
    private final Logger logger;
    private final ExecutorService worker;
    private final Map<String, State> states = new LinkedHashMap<String, State>();
    private final Map<String, CompletableFuture<DeedProviderSnapshot>> inFlight =
            new LinkedHashMap<String, CompletableFuture<DeedProviderSnapshot>>();
    private boolean closed;

    public CachingDeedProvider(String providerKey,
                               List<DeedProviderMapping> mappings,
                               Path cacheDirectory, boolean localFilesAllowed,
                               Logger logger) {
        this.providerKey = required(providerKey, "provider key");
        if (mappings == null) throw new IllegalArgumentException("mappings are required");
        if (cacheDirectory == null) throw new IllegalArgumentException(
                "cache directory is required");
        this.cacheDirectory = cacheDirectory;
        this.localFilesAllowed = localFilesAllowed;
        this.logger = logger == null
                ? Logger.getLogger(CachingDeedProvider.class.getName()) : logger;
        List<DeedProviderMapping> copy = new ArrayList<DeedProviderMapping>();
        for (DeedProviderMapping mapping : mappings) {
            if (mapping == null) continue;
            if (!providerKey.equals(mapping.getProviderKey())) {
                throw new IllegalArgumentException("mapping provider key mismatch");
            }
            validateSource(mapping.getSource(), localFilesAllowed);
            for (DeedProviderMapping old : copy) {
                if (old.getServer().equals(mapping.getServer())) {
                    throw new IllegalArgumentException(
                            "duplicate deed provider mapping for "
                                    + mapping.getServer().externalForm());
                }
            }
            copy.add(mapping);
        }
        this.mappings = Collections.unmodifiableList(copy);
        this.worker = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable,
                        "wurm-waypointer-deed-provider-" + safeThreadName(providerKey));
                thread.setDaemon(true);
                return thread;
            }
        });
    }

    @Override public String getProviderKey() { return providerKey; }

    @Override public boolean supports(ServerIdentity server) {
        return mapping(server) != null;
    }

    @Override public synchronized DeedProviderSnapshot current(ServerIdentity server) {
        DeedProviderMapping mapping = mapping(server);
        if (mapping == null) return DeedProviderSnapshot.noProvider(Instant.now());
        State state = states.get(mapping.getServer().externalForm());
        if (state == null) return new DeedProviderSnapshot(providerKey,
                DeedProviderStatus.LOADING, Collections.<DeedRecord>emptyList(),
                null, null, "Waiting for first refresh");
        return state.snapshot;
    }

    @Override public synchronized CompletableFuture<DeedProviderSnapshot> refresh(
            final ServerIdentity server, boolean manual) {
        final DeedProviderMapping mapping = mapping(server);
        if (mapping == null) return CompletableFuture.completedFuture(
                DeedProviderSnapshot.noProvider(Instant.now()));
        if (closed) return failed(new IllegalStateException("deed provider is closed"));
        final String key = mapping.getServer().externalForm();
        CompletableFuture<DeedProviderSnapshot> active = inFlight.get(key);
        if (active != null && !active.isDone()) return active;
        State state = states.get(key);
        long now = System.currentTimeMillis();
        if (!manual && state != null && now < state.nextAttemptAt) {
            return CompletableFuture.completedFuture(state.snapshot);
        }
        CompletableFuture<DeedProviderSnapshot> future =
                CompletableFuture.supplyAsync(() -> synchronize(mapping), worker);
        inFlight.put(key, future);
        future.whenComplete((snapshot, failure) -> {
            synchronized (CachingDeedProvider.this) {
                if (inFlight.get(key) == future) inFlight.remove(key);
            }
        });
        return future;
    }

    private DeedProviderSnapshot synchronize(DeedProviderMapping mapping) {
        String key = mapping.getServer().externalForm();
        State old;
        synchronized (this) { old = states.get(key); }
        if (old == null) old = loadCache(mapping);
        try {
            Fetch fetch = fetch(mapping, old);
            Instant now = Instant.now();
            if (fetch.notModified) {
                if (old == null || !old.snapshot.hasData()) throw new IOException(
                        "provider returned not-modified without a usable cache");
                DeedProviderSnapshot snapshot = new DeedProviderSnapshot(providerKey,
                        DeedProviderStatus.READY, old.snapshot.getDeeds(), now, now,
                        "Not modified");
                publish(key, new State(snapshot, old.payload, old.etag,
                        old.remoteLastModified, now.toEpochMilli()
                                + AUTOMATIC_REFRESH_MILLIS, 0));
                return snapshot;
            }
            List<DeedRecord> deeds = DeedFeedParser.parse(fetch.payload,
                    mapping.getFormat(), mapping.getMapWidth(), mapping.getMapHeight());
            writeCache(mapping, fetch.payload);
            DeedProviderSnapshot snapshot = new DeedProviderSnapshot(providerKey,
                    DeedProviderStatus.READY, deeds, now, now,
                    fetch.local ? "Imported local file" : "Downloaded");
            publish(key, new State(snapshot, fetch.payload, fetch.etag,
                    fetch.lastModified, now.toEpochMilli()
                            + AUTOMATIC_REFRESH_MILLIS, 0));
            return snapshot;
        } catch (Throwable failure) {
            int failures = old == null ? 1 : Math.min(16, old.failures + 1);
            long delay = Math.min(MAXIMUM_BACKOFF_MILLIS,
                    MINIMUM_BACKOFF_MILLIS << Math.min(10, failures - 1));
            Instant now = Instant.now();
            List<DeedRecord> oldDeeds = old == null
                    ? Collections.<DeedRecord>emptyList() : old.snapshot.getDeeds();
            Instant oldTimestamp = old == null ? null
                    : old.snapshot.getDataTimestamp();
            DeedProviderStatus status = oldDeeds.isEmpty()
                    ? DeedProviderStatus.ERROR : DeedProviderStatus.CACHED;
            DeedProviderSnapshot snapshot = new DeedProviderSnapshot(providerKey,
                    status, oldDeeds, oldTimestamp, now, safeMessage(failure));
            publish(key, new State(snapshot, old == null ? null : old.payload,
                    old == null ? "" : old.etag,
                    old == null ? 0L : old.remoteLastModified,
                    now.toEpochMilli() + delay, failures));
            logger.log(Level.WARNING, "Deed provider refresh failed open: provider="
                    + providerKey + ", server=" + key + ", retained="
                    + oldDeeds.size() + ", retryMillis=" + delay, failure);
            return snapshot;
        }
    }

    private State loadCache(DeedProviderMapping mapping) {
        String key = mapping.getServer().externalForm();
        Path file = cacheFile(mapping);
        try {
            if (!Files.isRegularFile(file)) return null;
            byte[] payload = readFile(file);
            List<DeedRecord> deeds = DeedFeedParser.parse(payload,
                    mapping.getFormat(), mapping.getMapWidth(), mapping.getMapHeight());
            Instant timestamp = Files.getLastModifiedTime(file).toInstant();
            State state = new State(new DeedProviderSnapshot(providerKey,
                    DeedProviderStatus.CACHED, deeds, timestamp, Instant.now(),
                    "Last-known-good cache"), payload, "", 0L, 0L, 0);
            publish(key, state);
            return state;
        } catch (Throwable failure) {
            logger.log(Level.WARNING, "Rejected cached deed feed: " + file, failure);
            return null;
        }
    }

    private Fetch fetch(DeedProviderMapping mapping, State old) throws IOException {
        URI source = mapping.getSource();
        if ("file".equalsIgnoreCase(source.getScheme())) {
            if (!localFilesAllowed) throw new IOException("local deed feeds are disabled");
            Path file;
            try { file = java.nio.file.Paths.get(source); }
            catch (RuntimeException invalid) { throw new IOException("invalid deed file URI", invalid); }
            return new Fetch(readFile(file), "", Files.getLastModifiedTime(file).toMillis(),
                    false, true);
        }
        URLConnection raw = source.toURL().openConnection();
        if (!(raw instanceof HttpsURLConnection)) throw new IOException(
                "deed feed must use HTTPS: " + source);
        HttpsURLConnection connection = (HttpsURLConnection) raw;
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(20000);
        connection.setUseCaches(false);
        connection.setRequestProperty("Accept", mapping.getFormat() == DeedFeedFormat.CSV
                ? "text/csv,text/plain" : "application/json,text/javascript");
        connection.setRequestProperty("User-Agent", "Wurm-Waypointer/1.0 deeds");
        if (old != null && !old.etag.isEmpty()) {
            connection.setRequestProperty("If-None-Match", old.etag);
        }
        if (old != null && old.remoteLastModified > 0L) {
            connection.setIfModifiedSince(old.remoteLastModified);
        }
        try {
            int status = connection.getResponseCode();
            if (status == HttpsURLConnection.HTTP_NOT_MODIFIED) {
                return new Fetch(null, old == null ? "" : old.etag,
                        old == null ? 0L : old.remoteLastModified, true, false);
            }
            if (status != HttpsURLConnection.HTTP_OK) throw new IOException(
                    "deed feed HTTP status " + status);
            byte[] payload = readBounded(connection.getInputStream());
            return new Fetch(payload, header(connection, "ETag"),
                    connection.getLastModified(), false, false);
        } finally {
            connection.disconnect();
        }
    }

    private synchronized void publish(String key, State state) {
        if (!closed) states.put(key, state);
    }

    private DeedProviderMapping mapping(ServerIdentity server) {
        DeedProviderMapping wildcard = null;
        for (DeedProviderMapping value : mappings) {
            if (!value.getServer().matches(server)) continue;
            if (!value.getServer().getWorldName().isEmpty()) return value;
            wildcard = value;
        }
        return wildcard;
    }

    private void writeCache(DeedProviderMapping mapping, byte[] payload)
            throws IOException {
        Path target = cacheFile(mapping).toAbsolutePath().normalize();
        Path parent = target.getParent();
        if (parent == null) throw new IOException("deed cache has no parent");
        Files.createDirectories(parent);
        Path temporary = parent.resolve(target.getFileName().toString() + ".tmp");
        Files.write(temporary, payload);
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Path cacheFile(DeedProviderMapping mapping) {
        return cacheDirectory.resolve(hash(providerKey + "|"
                + mapping.getServer().externalForm()) + ".deeds");
    }

    private static byte[] readFile(Path file) throws IOException {
        long size = Files.size(file);
        if (size < 1L || size > DeedFeedParser.MAXIMUM_BYTES) throw new IOException(
                "deed feed file is empty or oversized");
        return Files.readAllBytes(file);
    }

    private static byte[] readBounded(InputStream input) throws IOException {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream(65536);
            byte[] buffer = new byte[8192];
            int count;
            int total = 0;
            while ((count = input.read(buffer)) >= 0) {
                total += count;
                if (total > DeedFeedParser.MAXIMUM_BYTES) throw new IOException(
                        "downloaded deed feed is oversized");
                output.write(buffer, 0, count);
            }
            if (total < 1) throw new IOException("downloaded deed feed is empty");
            return output.toByteArray();
        } finally {
            input.close();
        }
    }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        worker.shutdownNow();
        inFlight.clear();
    }

    private static void validateSource(URI source, boolean localAllowed) {
        String scheme = source.getScheme() == null ? ""
                : source.getScheme().toLowerCase(Locale.ENGLISH);
        if ("https".equals(scheme)) return;
        if (localAllowed && "file".equals(scheme)) return;
        throw new IllegalArgumentException(
                "deed source must use HTTPS" + (localAllowed ? " or file" : ""));
    }

    private static String header(HttpsURLConnection connection, String name) {
        String value = connection.getHeaderField(name);
        return value == null ? "" : value.trim();
    }

    private static String required(String value, String label) {
        String clean = value == null ? "" : value.trim();
        if (clean.isEmpty()) throw new IllegalArgumentException(label + " is required");
        return clean;
    }

    private static String safeThreadName(String value) {
        return value.toLowerCase(Locale.ENGLISH).replaceAll("[^a-z0-9]+", "-");
    }

    private static String safeMessage(Throwable failure) {
        String value = failure == null ? "Unknown provider failure"
                : failure.getMessage();
        if (value == null || value.trim().isEmpty()) value = failure.getClass().getSimpleName();
        value = value.replace('\r', ' ').replace('\n', ' ').trim();
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    private static String hash(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(
                    value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(24);
            for (int i = 0; i < 12; i++) result.append(String.format(
                    Locale.ENGLISH, "%02x", Integer.valueOf(bytes[i] & 0xff)));
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static <T> CompletableFuture<T> failed(Throwable failure) {
        CompletableFuture<T> result = new CompletableFuture<T>();
        result.completeExceptionally(failure);
        return result;
    }

    private static final class Fetch {
        private final byte[] payload;
        private final String etag;
        private final long lastModified;
        private final boolean notModified;
        private final boolean local;

        private Fetch(byte[] payload, String etag, long lastModified,
                      boolean notModified, boolean local) {
            this.payload = payload;
            this.etag = etag == null ? "" : etag;
            this.lastModified = lastModified;
            this.notModified = notModified;
            this.local = local;
        }
    }

    private static final class State {
        private final DeedProviderSnapshot snapshot;
        private final byte[] payload;
        private final String etag;
        private final long remoteLastModified;
        private final long nextAttemptAt;
        private final int failures;

        private State(DeedProviderSnapshot snapshot, byte[] payload, String etag,
                      long remoteLastModified, long nextAttemptAt, int failures) {
            this.snapshot = snapshot;
            this.payload = payload;
            this.etag = etag == null ? "" : etag;
            this.remoteLastModified = remoteLastModified;
            this.nextAttemptAt = nextAttemptAt;
            this.failures = failures;
        }
    }
}
