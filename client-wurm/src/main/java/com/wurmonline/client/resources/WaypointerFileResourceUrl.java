package com.wurmonline.client.resources;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;

/** File-backed ResourceUrl with a revision-sensitive loader identity. */
public final class WaypointerFileResourceUrl extends ResourceUrl {
    private final Path path;
    private final String key;

    public WaypointerFileResourceUrl(Path path, long revision) {
        this(require(path), require(path).toAbsolutePath().normalize().toString()
                + "#" + revision);
    }

    private WaypointerFileResourceUrl(Path path, String key) {
        super(key);
        this.path = path.toAbsolutePath().normalize();
        this.key = key;
    }

    @Override public ResourceUrl derive(String relative) {
        Path parent = path.getParent();
        return new WaypointerFileResourceUrl(
                (parent == null ? Paths.get(relative) : parent.resolve(relative)),
                key + "/" + relative);
    }

    @Override public ResourceUrl changeFilePath(String filePath) {
        return new WaypointerFileResourceUrl(Paths.get(filePath),
                key + "->" + filePath);
    }

    @Override public InputStream openStream() throws IOException {
        return Files.newInputStream(path);
    }

    @Override public boolean exists() { return Files.isRegularFile(path); }
    @Override public String getFilePath() { return path.toString(); }
    @Override public Map<String, String> getOverrides() {
        return Collections.emptyMap();
    }
    @Override long getSize() {
        try { return Files.size(path); }
        catch (IOException ignored) { return 0L; }
    }

    @Override public boolean equals(Object other) {
        return other instanceof WaypointerFileResourceUrl
                && key.equals(((WaypointerFileResourceUrl) other).key);
    }

    @Override public String toString() { return key; }

    private static Path require(Path value) {
        if (value == null) throw new IllegalArgumentException("file path is required");
        return value;
    }
}
