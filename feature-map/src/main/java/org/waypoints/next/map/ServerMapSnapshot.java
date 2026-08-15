package org.waypoints.next.map;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable cache publication consumed by the Wurm renderer. */
public final class ServerMapSnapshot {
    private final ServerMapProfile profile;
    private final Path surfaceImage;
    private final long surfaceRevision;
    private final List<Deed> deeds;
    private final long deedsRevision;
    private final long revision;

    ServerMapSnapshot(ServerMapProfile profile, Path surfaceImage,
                      long surfaceRevision, List<Deed> deeds,
                      long deedsRevision, long revision) {
        this.profile = profile;
        this.surfaceImage = surfaceImage;
        this.surfaceRevision = surfaceRevision;
        this.deeds = Collections.unmodifiableList(new ArrayList<Deed>(deeds));
        this.deedsRevision = deedsRevision;
        this.revision = revision;
    }

    public static ServerMapSnapshot empty(ServerMapProfile profile) {
        return new ServerMapSnapshot(profile, null, 0L,
                Collections.<Deed>emptyList(), 0L, 0L);
    }

    public ServerMapProfile getProfile() { return profile; }
    public Path getSurfaceImage() { return surfaceImage; }
    public long getSurfaceRevision() { return surfaceRevision; }
    public List<Deed> getDeeds() { return deeds; }
    public long getDeedsRevision() { return deedsRevision; }
    public long getRevision() { return revision; }
    public boolean hasSurface() { return surfaceImage != null; }
}
