package org.waypoints.next.deeds;

import org.junit.Test;
import org.waypoints.next.model.ServerEndpoint;
import org.waypoints.next.model.ServerIdentity;
import org.waypoints.next.model.WaypointRecord;
import org.waypoints.next.model.WaypointResolution;
import org.waypoints.next.model.WaypointSourceType;
import org.waypoints.next.validation.WaypointRecordValidator;
import org.waypoints.next.persistence.WaypointDocument;
import org.waypoints.next.persistence.WaypointFormatCodec;
import org.waypoints.next.persistence.OpaqueWaypointRecord;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class DeedWaypointServiceTest {
    private final DeedWaypointService service = new DeedWaypointService();
    private final ServerIdentity server = ServerIdentity.of(
            ServerEndpoint.direct("example.test", 3724), "Test", "Test",
            ServerIdentity.Resolution.RESOLVED);

    @Test public void selectionCreatesRealDeedAndMovementKeepsUuid() {
        Instant first = Instant.parse("2026-01-01T00:00:00Z");
        DeedProviderSnapshot initial = snapshot(DeedProviderStatus.READY, first,
                deed("stable-1", 10, 20));
        WaypointRecord tracked = service.track(Collections.<WaypointRecord>emptyList(),
                initial, initial.getDeeds().get(0), server, "Fedor", first);
        assertEquals(WaypointSourceType.DEED, tracked.getSourceType());
        assertEquals(WaypointResolution.LIVE_EXACT, tracked.getResolution());
        assertTrue(tracked.isEnabled());
        assertEquals("generic-json|stable-1", tracked.getSourceKey());
        assertEquals(10.5d, tracked.getCoordinate().getTileX(), 0.0d);
        new WaypointRecordValidator().validate(tracked);

        Instant movedAt = first.plusSeconds(60);
        DeedWaypointService.Reconciliation moved = service.reconcile(
                Collections.singletonList(tracked),
                snapshot(DeedProviderStatus.READY, movedAt,
                        deed("stable-1", 30, 40)), server, movedAt);
        assertEquals(1, moved.getMoved());
        WaypointRecord changed = moved.getUpdates().get(0);
        assertEquals(tracked.getId(), changed.getId());
        assertEquals(30.5d, changed.getCoordinate().getTileX(), 0.0d);
        assertEquals(movedAt, changed.getLastResolvedAt());
    }

    @Test public void explicitRetrackingEnablesAnExistingDisabledDeed() {
        Instant first = Instant.parse("2026-01-01T00:00:00Z");
        DeedProviderSnapshot initial = snapshot(DeedProviderStatus.READY, first,
                deed("stable-1", 10, 20));
        WaypointRecord tracked = service.track(Collections.<WaypointRecord>emptyList(),
                initial, initial.getDeeds().get(0), server, "Fedor", first);
        WaypointRecord disabled = WaypointRecord.copyOf(tracked)
                .enabled(false).build();

        WaypointRecord retracked = service.track(Collections.singletonList(disabled),
                initial, initial.getDeeds().get(0), server, "Fedor",
                first.plusSeconds(10));

        assertEquals(disabled.getId(), retracked.getId());
        assertTrue(retracked.isEnabled());
    }

    @Test public void validDisappearanceIsStaleAndReappearanceRestores() {
        Instant first = Instant.parse("2026-01-01T00:00:00Z");
        DeedProviderSnapshot initial = snapshot(DeedProviderStatus.READY, first,
                deed("stable-1", 10, 20));
        WaypointRecord tracked = service.track(Collections.<WaypointRecord>emptyList(),
                initial, initial.getDeeds().get(0), server, "Fedor", first);
        Instant later = first.plusSeconds(3600);
        DeedWaypointService.Reconciliation missing = service.reconcile(
                Collections.singletonList(tracked), snapshot(
                        DeedProviderStatus.READY, later, deed("other", 1, 1)),
                server, later);
        assertEquals(1, missing.getStale());
        WaypointRecord stale = missing.getUpdates().get(0);
        assertEquals(WaypointResolution.STALE, stale.getResolution());
        assertEquals(tracked.getCoordinate(), stale.getCoordinate());
        assertEquals(first, stale.getLastResolvedAt());

        DeedProviderSnapshot cachedWithoutIt = snapshot(
                DeedProviderStatus.CACHED, later.plusSeconds(60), deed("other", 1, 1));
        assertFalse(service.reconcile(Collections.singletonList(tracked),
                cachedWithoutIt, server, later.plusSeconds(60)).isChanged());

        Instant returnedAt = later.plusSeconds(120);
        DeedWaypointService.Reconciliation returned = service.reconcile(
                Collections.singletonList(stale), snapshot(
                        DeedProviderStatus.READY, returnedAt,
                        deed("stable-1", 11, 21)), server, returnedAt);
        assertEquals(1, returned.getRestored());
        assertEquals(WaypointResolution.LIVE_EXACT,
                returned.getUpdates().get(0).getResolution());
        assertEquals(stale.getId(), returned.getUpdates().get(0).getId());
    }

    @Test public void reportsPersistedDataAge() {
        Instant first = Instant.parse("2026-01-01T00:00:00Z");
        DeedProviderSnapshot initial = snapshot(DeedProviderStatus.READY, first,
                deed("stable-1", 10, 20));
        WaypointRecord tracked = service.track(Collections.<WaypointRecord>emptyList(),
                initial, initial.getDeeds().get(0), server, "Fedor", first);
        assertEquals("2h", DeedWaypointService.describeAge(
                tracked, first.plusSeconds(2 * 3600 + 10)));
        assertTrue(DeedWaypointService.dataAgeMillis(tracked,
                first.plusSeconds(10)) >= 10_000L);
    }

    @Test public void sameDeedIsAccountScopedOnOneServer() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        DeedProviderSnapshot initial = snapshot(DeedProviderStatus.READY, now,
                deed("stable-1", 10, 20));
        WaypointRecord fedor = service.track(Collections.<WaypointRecord>emptyList(),
                initial, initial.getDeeds().get(0), server, "Fedor", now);
        WaypointRecord alice = service.track(Collections.singletonList(fedor),
                initial, initial.getDeeds().get(0), server, "Alice", now);
        assertFalse(fedor.getId().equals(alice.getId()));
        assertEquals("Alice", alice.getCreatedByUser());
    }

    @Test public void providerIdentityAndTimestampSurvivePersistence() throws Exception {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        DeedProviderSnapshot initial = snapshot(DeedProviderStatus.READY, now,
                deed("stable-1", 10, 20));
        WaypointRecord tracked = service.track(Collections.<WaypointRecord>emptyList(),
                initial, initial.getDeeds().get(0), server, "Fedor", now);
        WaypointFormatCodec codec = new WaypointFormatCodec(
                new WaypointRecordValidator());
        WaypointDocument restored = codec.decode(codec.encode(new WaypointDocument(
                Collections.singletonList(tracked),
                Collections.<OpaqueWaypointRecord>emptyList())));
        WaypointRecord record = restored.getRecords().get(0);
        assertEquals(WaypointSourceType.DEED, record.getSourceType());
        assertEquals("generic-json", record.getExtensions().get(
                DeedWaypointService.PROVIDER_EXTENSION).get(0));
        assertEquals("stable-1", record.getExtensions().get(
                DeedWaypointService.STABLE_KEY_EXTENSION).get(0));
        assertEquals(now, record.getLastResolvedAt());
    }

    private static DeedRecord deed(String key, int x, int y) {
        return new DeedRecord(key, "Haven", x, y,
                Collections.singletonMap("mayor", "Mayor"));
    }

    private static DeedProviderSnapshot snapshot(DeedProviderStatus status,
                                                  Instant at,
                                                  DeedRecord... deeds) {
        return new DeedProviderSnapshot("generic-json", status,
                Arrays.asList(deeds), at, at, "test");
    }
}
