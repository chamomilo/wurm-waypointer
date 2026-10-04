package org.waypoints.next.map;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.time.Instant;

/** Immutable cache publication consumed by the Wurm renderer. */
public final class ServerMapSnapshot {
    private final ServerMapProfile profile;
    private final Path surfaceImage;
    private final long surfaceRevision;
    private final List<Deed> deeds;
    private final long deedsRevision;
    private final long revision;
    private final String deedProviderKey;
    private final DeedDataStatus deedProviderStatus;
    private final Instant deedDataTimestamp;
    private final String deedProviderDetail;

    ServerMapSnapshot(ServerMapProfile profile, Path surfaceImage,
                      long surfaceRevision, List<Deed> deeds,
                      long deedsRevision, long revision) {
        this(profile, surfaceImage, surfaceRevision, deeds, deedsRevision,
                revision, "legacy-map", deeds.isEmpty()
                ? DeedDataStatus.LOADING : DeedDataStatus.READY,
                deedsRevision <= 0L ? null : Instant.ofEpochMilli(deedsRevision), "");
    }

    private ServerMapSnapshot(ServerMapProfile profile, Path surfaceImage,
                      long surfaceRevision, List<Deed> deeds,
                      long deedsRevision, long revision,
                      String deedProviderKey, DeedDataStatus deedProviderStatus,
                      Instant deedDataTimestamp, String deedProviderDetail) {
        this.profile = profile;
        this.surfaceImage = surfaceImage;
        this.surfaceRevision = surfaceRevision;
        this.deeds = Collections.unmodifiableList(new ArrayList<Deed>(deeds));
        this.deedsRevision = deedsRevision;
        this.revision = revision;
        this.deedProviderKey = deedProviderKey == null ? "" : deedProviderKey;
        this.deedProviderStatus = deedProviderStatus;
        this.deedDataTimestamp = deedDataTimestamp;
        this.deedProviderDetail = deedProviderDetail == null ? "" : deedProviderDetail;
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
    public String getDeedProviderKey() { return deedProviderKey; }
    public DeedDataStatus getDeedProviderStatus() { return deedProviderStatus; }
    public Instant getDeedDataTimestamp() { return deedDataTimestamp; }
    public String getDeedProviderDetail() { return deedProviderDetail; }

    /** Replaces only the deed layer; surface publication remains independent. */
    public ServerMapSnapshot withDeeds(List<Deed> mapped,
                                       String providerKey,
                                       DeedDataStatus status,
                                       Instant dataTimestamp,
                                       String detail) {
        if (mapped == null || status == null) return this;
        long deedRevision = dataTimestamp == null ? 0L
                : dataTimestamp.toEpochMilli();
        return new ServerMapSnapshot(profile, surfaceImage, surfaceRevision,
                mapped, deedRevision, revision * 31L + deedRevision,
                providerKey, status, dataTimestamp, detail);
    }
}
