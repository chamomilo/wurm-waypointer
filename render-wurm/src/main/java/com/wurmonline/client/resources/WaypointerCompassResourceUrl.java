package com.wurmonline.client.resources;

import org.gotti.wurmunlimited.modloader.classhooks.HookManager;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Collections;
import java.util.Map;

/** Compass layers read directly from the JAR that supplies this bridge. */
public final class WaypointerCompassResourceUrl extends ResourceUrl {
    private final String path;

    public WaypointerCompassResourceUrl(String path) {
        super(path);
        if (path == null || !path.startsWith("/org/waypoints/next/compass/")
                || path.contains("..")) {
            throw new IllegalArgumentException("Invalid embedded compass path: " + path);
        }
        this.path = path;
    }

    @Override public ResourceUrl derive(String relative) {
        return changeFilePath(path.substring(0, path.lastIndexOf('/') + 1) + relative);
    }

    @Override public ResourceUrl changeFilePath(String value) {
        return new WaypointerCompassResourceUrl(value);
    }

    @Override public InputStream openStream() throws IOException {
        // Javassist loads mod classes from its ClassPool, but its Loader does not
        // expose the same JARs through getResource. Use the actual class origin,
        // so neither loose PNGs nor an old unreferenced mod JAR can supply artwork.
        URL origin = HookManager.getInstance().getClassPool().find(
                WaypointerCompassResourceUrl.class.getName());
        URL resource = origin != null && "jar".equals(origin.getProtocol())
                ? new URL(origin, path)
                : WaypointerCompassResourceUrl.class.getResource(path);
        if (resource == null) throw new IOException("Missing embedded compass: " + path);
        URLConnection connection = resource.openConnection();
        // Closing a texture stream must also release the JAR handle on Windows.
        connection.setUseCaches(false);
        return connection.getInputStream();
    }

    @Override public boolean exists() {
        try (InputStream input = openStream()) { return true; }
        catch (IOException failure) { return false; }
    }

    @Override public String getFilePath() { return path; }
    @Override public Map<String, String> getOverrides() { return Collections.emptyMap(); }
    @Override long getSize() { return 0L; }

    @Override public boolean equals(Object other) {
        return other instanceof WaypointerCompassResourceUrl
                && path.equals(((WaypointerCompassResourceUrl) other).path);
    }

    @Override public String toString() { return "waypointer-compass:" + path; }
}
