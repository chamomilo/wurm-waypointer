package org.waypoints.next.deeds;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable last-known-good catalog plus provider health and data age. */
public final class DeedProviderSnapshot {
    private final String providerKey;
    private final DeedProviderStatus status;
    private final List<DeedRecord> deeds;
    private final Instant dataTimestamp;
    private final Instant checkedAt;
    private final String detail;

    public DeedProviderSnapshot(String providerKey, DeedProviderStatus status,
                                List<DeedRecord> deeds, Instant dataTimestamp,
                                Instant checkedAt, String detail) {
        if (status == null) throw new IllegalArgumentException("status is required");
        this.providerKey = clean(providerKey);
        this.status = status;
        this.deeds = Collections.unmodifiableList(new ArrayList<DeedRecord>(
                deeds == null ? Collections.<DeedRecord>emptyList() : deeds));
        this.dataTimestamp = dataTimestamp;
        this.checkedAt = checkedAt;
        this.detail = clean(detail);
        if (status == DeedProviderStatus.NO_PROVIDER && !this.deeds.isEmpty()) {
            throw new IllegalArgumentException("No provider cannot publish deeds");
        }
        if (!this.deeds.isEmpty() && dataTimestamp == null) {
            throw new IllegalArgumentException("published deeds require a data timestamp");
        }
    }

    public static DeedProviderSnapshot noProvider(Instant now) {
        return new DeedProviderSnapshot("", DeedProviderStatus.NO_PROVIDER,
                Collections.<DeedRecord>emptyList(), null, now, "No provider");
    }

    public String getProviderKey() { return providerKey; }
    public DeedProviderStatus getStatus() { return status; }
    public List<DeedRecord> getDeeds() { return deeds; }
    public Instant getDataTimestamp() { return dataTimestamp; }
    public Instant getCheckedAt() { return checkedAt; }
    public String getDetail() { return detail; }
    public boolean hasData() { return dataTimestamp != null && !deeds.isEmpty(); }
    public long ageMillis(Instant now) {
        if (dataTimestamp == null || now == null) return -1L;
        long value = Duration.between(dataTimestamp, now).toMillis();
        return Math.max(0L, value);
    }
    public boolean isStale(Instant now, Duration maximumAge) {
        if (!hasData() || maximumAge == null || maximumAge.isNegative()) return true;
        return ageMillis(now) > maximumAge.toMillis();
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
