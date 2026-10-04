package org.waypoints.next.integration;

import com.wurmonline.client.renderer.gui.HeadsUpDisplay;
import org.waypoints.next.deeds.DeedProvider;
import org.waypoints.next.deeds.DeedProviderMapping;
import org.waypoints.next.deeds.DeedProviderMappingParser;
import org.waypoints.next.deeds.DeedProviderRegistry;
import org.waypoints.next.deeds.DeedProviderSnapshot;
import org.waypoints.next.deeds.DeedProviderStatus;
import org.waypoints.next.deeds.DeedRecord;
import org.waypoints.next.deeds.GenericCsvDeedProvider;
import org.waypoints.next.deeds.GenericJsonDeedProvider;
import org.waypoints.next.deeds.SklotopolisDeedMappings;
import org.waypoints.next.deeds.SklotopolisDeedProvider;
import org.waypoints.next.map.Deed;
import org.waypoints.next.map.ServerMapSnapshot;
import org.waypoints.next.map.DeedDataStatus;
import org.waypoints.next.model.ServerIdentity;
import org.waypoints.next.model.WaypointRecord;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Fail-open client adapter around the pure provider/reconciliation module. */
final class DeedProviderRuntime {
    private final Logger logger;
    private final StaticWaypointRuntime waypoints;
    private volatile DeedProviderRegistry registry =
            new DeedProviderRegistry(Collections.<DeedProvider>emptyList());
    private volatile DeedProviderSnapshot snapshot =
            DeedProviderSnapshot.noProvider(Instant.now());
    private volatile ServerIdentity server;
    private volatile String activeServerKey = "";
    private volatile boolean enabled;
    private volatile ServerMapSnapshot cachedOverlay;
    private volatile ServerMapSnapshot cachedOverlayBase;
    private volatile DeedProviderSnapshot cachedOverlayProvider;

    DeedProviderRuntime(Logger logger, StaticWaypointRuntime waypoints) {
        this.logger = logger;
        this.waypoints = waypoints;
    }

    synchronized void configure(WaypointClientConfiguration configuration) {
        registry.close();
        enabled = configuration != null && configuration.isDeedProviderEnabled();
        server = null;
        activeServerKey = "";
        snapshot = DeedProviderSnapshot.noProvider(Instant.now());
        clearOverlayCache();
        if (!enabled) {
            registry = new DeedProviderRegistry(Collections.<DeedProvider>emptyList());
            return;
        }
        List<DeedProviderMapping> custom;
        try {
            custom = DeedProviderMappingParser.parse(
                    configuration.getDeedProviderMappings(),
                    configuration.getMapWidth(), configuration.getMapHeight());
        } catch (RuntimeException invalid) {
            custom = Collections.emptyList();
            logger.log(Level.WARNING,
                    "Custom deed provider mappings were rejected; built-in mappings remain available",
                    invalid);
        }
        List<DeedProviderMapping> json = byProvider(custom,
                GenericJsonDeedProvider.KEY);
        List<DeedProviderMapping> csv = byProvider(custom,
                GenericCsvDeedProvider.KEY);
        List<DeedProvider> providers = new ArrayList<DeedProvider>();
        if (!json.isEmpty()) providers.add(new GenericJsonDeedProvider(json,
                configuration.getDeedProviderCacheDirectory(), logger));
        if (!csv.isEmpty()) providers.add(new GenericCsvDeedProvider(csv,
                configuration.getDeedProviderCacheDirectory(), logger));
        providers.add(new SklotopolisDeedProvider(
                SklotopolisDeedMappings.defaults(),
                configuration.getDeedProviderCacheDirectory(), logger));
        registry = new DeedProviderRegistry(providers);
    }

    void bind(ServerIdentity identity) {
        if (!enabled || identity == null || !identity.isSafeForAutomaticRendering()) {
            server = identity;
            activeServerKey = "";
            snapshot = DeedProviderSnapshot.noProvider(Instant.now());
            return;
        }
        String key = identity.getEndpointFingerprint() + "|" + identity.getFullName();
        if (!key.equals(activeServerKey)) {
            activeServerKey = key;
            server = identity;
            snapshot = registry.current(identity);
        }
        registry.refresh(identity, false);
        DeedProviderSnapshot current = registry.current(identity);
        snapshot = current;
        waypoints.reconcileDeeds(current, identity);
    }

    DeedProviderSnapshot current() { return snapshot; }

    CompletableFuture<DeedProviderSnapshot> refreshNow() {
        ServerIdentity currentServer = server;
        if (!enabled || currentServer == null) return CompletableFuture.completedFuture(
                DeedProviderSnapshot.noProvider(Instant.now()));
        return registry.refresh(currentServer, true);
    }

    WaypointRecord track(Deed selected, HeadsUpDisplay hud,
                         ServerIdentity identity) {
        if (selected == null) throw new IllegalArgumentException("deed is required");
        DeedProviderSnapshot current = snapshot;
        if (current.getStatus() == DeedProviderStatus.NO_PROVIDER
                || !current.hasData()) throw new IllegalStateException(
                "no usable deed provider data is available for this server");
        DeedRecord match = null;
        for (DeedRecord deed : current.getDeeds()) {
            if (deed.getStableKey().equalsIgnoreCase(selected.getStableKey())) {
                match = deed; break;
            }
        }
        if (match == null) match = selectedRecord(selected);
        return waypoints.trackDeed(current, match, hud, identity);
    }

    ServerMapSnapshot overlay(ServerMapSnapshot base) {
        if (base == null) return null;
        DeedProviderSnapshot provider = snapshot;
        ServerMapSnapshot cached = cachedOverlay;
        if (base == cachedOverlayBase && provider == cachedOverlayProvider
                && cached != null) return cached;
        synchronized (this) {
            cached = cachedOverlay;
            if (base == cachedOverlayBase && provider == cachedOverlayProvider
                    && cached != null) return cached;
        List<Deed> mapped = new ArrayList<Deed>();
        for (DeedRecord deed : provider.getDeeds()) mapped.add(
                Deed.fromProviderData(deed.getStableKey(), deed.getName(),
                        deed.getTileX(), deed.getTileY(), deed.getMetadata()));
            cached = base.withDeeds(mapped, provider.getProviderKey(),
                DeedDataStatus.valueOf(provider.getStatus().name()),
                provider.getDataTimestamp(), provider.getDetail());
            cachedOverlayBase = base;
            cachedOverlayProvider = provider;
            cachedOverlay = cached;
            return cached;
        }
    }

    String status() {
        DeedProviderSnapshot value = snapshot;
        String age = value.getDataTimestamp() == null ? "unknown"
                : age(value.ageMillis(Instant.now()));
        return value.getStatus() + ", provider="
                + (value.getProviderKey().isEmpty() ? "none" : value.getProviderKey())
                + ", deeds=" + value.getDeeds().size() + ", age=" + age
                + (value.getDetail().isEmpty() ? "" : ", " + value.getDetail());
    }

    void deactivate() {
        server = null;
        activeServerKey = "";
        snapshot = DeedProviderSnapshot.noProvider(Instant.now());
        clearOverlayCache();
    }

    private static List<DeedProviderMapping> byProvider(
            List<DeedProviderMapping> mappings, String key) {
        List<DeedProviderMapping> result = new ArrayList<DeedProviderMapping>();
        for (DeedProviderMapping mapping : mappings) {
            if (key.equals(mapping.getProviderKey())) result.add(mapping);
        }
        return result;
    }

    private void clearOverlayCache() {
        cachedOverlay = null;
        cachedOverlayBase = null;
        cachedOverlayProvider = null;
    }

    private static DeedRecord selectedRecord(Deed deed) {
        java.util.LinkedHashMap<String, String> metadata =
                new java.util.LinkedHashMap<String, String>();
        metadata.put("type", deed.getType());
        metadata.put("mayor", deed.getMayor());
        metadata.put("allianceName", deed.getAllianceName());
        metadata.put("founderName", deed.getFounderName());
        metadata.put("motto", deed.getMotto());
        metadata.put("lastActive", deed.getLastActive());
        metadata.put("guards", Integer.toString(deed.getGuards()));
        metadata.put("amountOfCitizens", Integer.toString(deed.getCitizens()));
        metadata.put("creationDate", Long.toString(deed.getCreationDate()));
        metadata.put("tilesNorth", Integer.toString(deed.getNorth()));
        metadata.put("tilesSouth", Integer.toString(deed.getSouth()));
        metadata.put("tilesEast", Integer.toString(deed.getEast()));
        metadata.put("tilesWest", Integer.toString(deed.getWest()));
        metadata.put("tilesPerimeter", Integer.toString(deed.getPerimeter()));
        metadata.put("isSpawnPoint", Boolean.toString(deed.isSpawnPoint()));
        return new DeedRecord(deed.getStableKey(), deed.getName(), deed.getX(),
                deed.getY(), metadata);
    }

    private static String age(long millis) {
        if (millis < 60_000L) return "now";
        long minutes = millis / 60_000L;
        if (minutes < 60L) return minutes + "m";
        long hours = minutes / 60L;
        return hours < 48L ? hours + "h" : hours / 24L + "d";
    }
}
