package com.wurmonline.client.resources;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;

/** Shared updater artwork embedded in every participating JAR. */
public final class ChamomiloResourceUrl extends ResourceUrl {
    private final String path;
    public ChamomiloResourceUrl(String path) { super(path); this.path = path; }
    @Override public ResourceUrl derive(String relative) {
        return new ChamomiloResourceUrl(path.substring(0, path.lastIndexOf('/') + 1) + relative);
    }
    @Override public ResourceUrl changeFilePath(String value) { return new ChamomiloResourceUrl(value); }
    @Override public InputStream openStream() throws IOException {
        InputStream input = ChamomiloResourceUrl.class.getResourceAsStream(path);
        if (input == null) throw new IOException("Missing shared artwork: " + path);
        return input;
    }
    @Override public boolean exists() { return ChamomiloResourceUrl.class.getResource(path) != null; }
    @Override public String getFilePath() { return path; }
    @Override public Map<String, String> getOverrides() { return Collections.emptyMap(); }
    @Override long getSize() { return 0; }
    @Override public boolean equals(Object other) {
        return other instanceof ChamomiloResourceUrl && path.equals(((ChamomiloResourceUrl) other).path);
    }
    @Override public String toString() { return "chamomilo-artwork:" + path; }
}
