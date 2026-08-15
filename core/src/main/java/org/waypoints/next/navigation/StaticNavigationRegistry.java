package org.waypoints.next.navigation;

import org.waypoints.next.model.MarkerStyle;
import org.waypoints.next.model.ServerIdentity;
import org.waypoints.next.model.UserMarkerStyles;
import org.waypoints.next.model.VanillaLandmarkKind;
import org.waypoints.next.model.WaypointRecord;
import org.waypoints.next.model.WaypointResolution;
import org.waypoints.next.model.WaypointSourceType;
import org.waypoints.next.service.WaypointRevisionSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Central Phase 2 projection/selection state. Reconciliation is revision based
 * and never performs persistence or client work.
 */
public final class StaticNavigationRegistry {
    private final Map<NavigationTargetKey, Boolean> beamVisibility =
            new HashMap<NavigationTargetKey, Boolean>();
    private List<WaypointRecord> eligible = Collections.emptyList();
    private List<WaypointRecord> candidates = Collections.emptyList();
    private NavigationTargetKey selected;
    private NavigationTargetKey navigator;
    private NavigationSnapshot snapshot = NavigationSnapshot.empty();
    private long generation;
    private long sourceRevision = Long.MIN_VALUE;
    private String contextServer = "";
    private String contextUser = "";
    private int markerCap;

    public synchronized NavigationSnapshot reconcile(WaypointRevisionSnapshot source,
                                                      NavigationContext context) {
        if (source == null) throw new IllegalArgumentException("source snapshot is required");
        if (context == null) throw new IllegalArgumentException("navigation context is required");
        String server = context.getCurrentServer().getEndpointFingerprint();
        String user = context.getCurrentUser();
        if (sourceRevision == source.getRevision()
                && server.equalsIgnoreCase(contextServer)
                && user.equalsIgnoreCase(contextUser)
                && markerCap == context.getMarkerCap()) {
            return snapshot;
        }

        Set<NavigationTargetKey> liveRecordKeys = globallyEligibleKeys(source.getRecords());
        for (Iterator<NavigationTargetKey> keys = beamVisibility.keySet().iterator();
             keys.hasNext();) {
            if (!liveRecordKeys.contains(keys.next())) keys.remove();
        }

        List<WaypointRecord> projected = new ArrayList<WaypointRecord>();
        for (WaypointRecord record : source.getRecords()) {
            if (isEligible(record, context)) projected.add(record);
        }
        if (selected != null && (!contains(projected, selected)
                || isSystem(projected, selected))) selected = null;
        if (navigator != null && !contains(projected, navigator)) navigator = null;
        Collections.sort(projected, priorityComparator(selected, navigator));
        List<WaypointRecord> allProjected =
                new ArrayList<WaypointRecord>(projected);
        int systemCount = 0;
        for (WaypointRecord record : projected) {
            if (record.getSourceType() == WaypointSourceType.VANILLA_SYSTEM) {
                systemCount++;
            }
        }
        int effectiveCap = systemCount + Math.max(context.getMarkerCap(),
                navigator == null ? 0 : 1);
        if (projected.size() > effectiveCap) {
            projected = new ArrayList<WaypointRecord>(
                    projected.subList(0, effectiveCap));
        }
        if (selected == null && !projected.isEmpty()) {
            for (WaypointRecord record : projected) {
                if (record.getSourceType() != WaypointSourceType.VANILLA_SYSTEM) {
                    selected = key(record);
                    break;
                }
            }
        }
        candidates = Collections.unmodifiableList(allProjected);
        eligible = Collections.unmodifiableList(projected);
        sourceRevision = source.getRevision();
        contextServer = server;
        contextUser = user;
        markerCap = context.getMarkerCap();
        publish();
        return snapshot;
    }

    /**
     * Starts or stops the one session navigation route. Activating another
     * target atomically replaces the previous owner.
     */
    public synchronized NavigationSnapshot toggleNavigator(
            NavigationTargetKey key) {
        if (key == null || !contains(candidates, key)) return snapshot;
        if (key.equals(navigator)) {
            navigator = null;
        } else {
            navigator = key;
            WaypointRecord chosen = find(candidates, key);
            if (chosen != null
                    && chosen.getSourceType() != WaypointSourceType.VANILLA_SYSTEM) {
                selected = key;
            }
        }
        rebuildEligibleFromCandidates();
        publish();
        return snapshot;
    }

    /**
     * Starts the one session route without toggle semantics. Repeating the
     * request for its current owner keeps navigation active, while a different
     * target atomically replaces it.
     */
    public synchronized NavigationSnapshot activateNavigator(
            NavigationTargetKey key) {
        if (key == null || !contains(candidates, key)) return snapshot;
        WaypointRecord chosen = find(candidates, key);
        boolean selectChosen = chosen != null
                && chosen.getSourceType() != WaypointSourceType.VANILLA_SYSTEM;
        if (key.equals(navigator)
                && (!selectChosen || key.equals(selected))) return snapshot;
        navigator = key;
        if (selectChosen) selected = key;
        rebuildEligibleFromCandidates();
        publish();
        return snapshot;
    }

    /** Stops navigation only when the exact endpoint-qualified target owns it. */
    public synchronized NavigationSnapshot deactivateNavigator(
            NavigationTargetKey key) {
        if (key != null && key.equals(navigator)) {
            navigator = null;
            rebuildEligibleFromCandidates();
            publish();
        }
        return snapshot;
    }

    public synchronized NavigationSnapshot deactivateNavigator(UUID waypointId) {
        if (waypointId != null && navigator != null
                && waypointId.equals(navigator.getWaypointId())) {
            navigator = null;
            rebuildEligibleFromCandidates();
            publish();
        }
        return snapshot;
    }

    public synchronized NavigationTargetKey navigatorKey() {
        return navigator;
    }

    public synchronized NavigationSnapshot selectAndToggleBeam(
            NavigationTargetKey key) {
        if (key == null || !contains(eligible, key)) return snapshot;
        WaypointRecord chosen = find(eligible, key);
        if (chosen != null
                && chosen.getSourceType() == WaypointSourceType.VANILLA_SYSTEM) {
            return snapshot;
        }
        selected = key;
        if (chosen != null
                && chosen.getSourceType() != WaypointSourceType.VANILLA_SYSTEM
                && chosen.getMarkerStyle().getWorldStyle()
                != MarkerStyle.WorldStyle.COMPASS_ONLY) {
            beamVisibility.put(key, Boolean.valueOf(!isBeamVisible(key)));
        }
        Collections.sort(eligible = new ArrayList<WaypointRecord>(eligible),
                priorityComparator(selected, navigator));
        eligible = Collections.unmodifiableList(eligible);
        publish();
        return snapshot;
    }

    public synchronized NavigationSnapshot snapshot() {
        return snapshot;
    }

    public synchronized NavigationTargetKey selectedKey() {
        return selected;
    }

    /** Manager On must restore the world effect even if compass hid it earlier. */
    public synchronized NavigationSnapshot setWorldEffectVisible(UUID waypointId,
                                                                  boolean visible) {
        if (waypointId == null || contextServer.isEmpty()) return snapshot;
        NavigationTargetKey key = new NavigationTargetKey(contextServer, waypointId);
        beamVisibility.put(key, Boolean.valueOf(visible));
        publish();
        return snapshot;
    }

    public synchronized void clearView() {
        selected = null;
        navigator = null;
        // Compass visibility is intentionally session-only. Every fresh HUD,
        // reconnect, or server activation starts enabled records visible.
        beamVisibility.clear();
        eligible = Collections.emptyList();
        candidates = Collections.emptyList();
        sourceRevision = Long.MIN_VALUE;
        contextServer = "";
        contextUser = "";
        markerCap = 0;
        publish();
    }

    private void publish() {
        List<NavigationTarget> targets = new ArrayList<NavigationTarget>(eligible.size());
        for (WaypointRecord record : eligible) {
            NavigationTargetKey key = key(record);
            targets.add(new NavigationTarget(key, navigationName(record),
                    record.getCoordinate(), renderedStyle(record), record.getSourceType(),
                    key.equals(selected), isBeamVisible(key),
                    record.getArrivalRadiusMetres(), record.getExpiresAt() == null
                            ? 0L : record.getExpiresAt().toEpochMilli(),
                    key.equals(navigator), record.getExtensions()));
        }
        snapshot = new NavigationSnapshot(sourceRevision, ++generation, targets);
    }

    private void rebuildEligibleFromCandidates() {
        List<WaypointRecord> projected = new ArrayList<WaypointRecord>(candidates);
        Collections.sort(projected, priorityComparator(selected, navigator));
        int systemCount = 0;
        for (WaypointRecord record : projected) {
            if (record.getSourceType() == WaypointSourceType.VANILLA_SYSTEM) {
                systemCount++;
            }
        }
        int effectiveCap = systemCount + Math.max(markerCap,
                navigator == null ? 0 : 1);
        if (projected.size() > effectiveCap) {
            projected = new ArrayList<WaypointRecord>(
                    projected.subList(0, effectiveCap));
        }
        eligible = Collections.unmodifiableList(projected);
    }

    private boolean isBeamVisible(NavigationTargetKey key) {
        Boolean visible = beamVisibility.get(key);
        return visible == null || visible.booleanValue();
    }

    private static boolean isEligible(WaypointRecord record,
                                      NavigationContext context) {
        if (!globallyEligible(record) || !record.isEnabled()) return false;
        ServerIdentity server = record.getServerIdentity();
        return server.sameServer(context.getCurrentServer())
                && (record.getSourceType() == WaypointSourceType.VANILLA_SYSTEM
                || context.getCurrentUser().equalsIgnoreCase(
                record.getCreatedByUser()));
    }

    private static boolean globallyEligible(WaypointRecord record) {
        if (record == null || (record.getSourceType() != WaypointSourceType.STATIC
                && record.getSourceType() != WaypointSourceType.VANILLA_SYSTEM
                && record.getSourceType() != WaypointSourceType.LOOT_MAP
                && record.getSourceType() != WaypointSourceType.ARCHAEOLOGY_REPORT
                && record.getSourceType() != WaypointSourceType.MANAGED_ANIMAL
                && record.getSourceType() != WaypointSourceType.MANAGED_ITEM)
                || !renderableResolution(record)
                || record.getCoordinate() == null || record.getMarkerStyle() == null
                || record.getMarkerStyle().getWorldStyle() == MarkerStyle.WorldStyle.HIDDEN) {
            return false;
        }
        ServerIdentity server = record.getServerIdentity();
        return server != null && server.isSafeForAutomaticRendering();
    }

    private static boolean renderableResolution(WaypointRecord record) {
        WaypointResolution resolution = record.getResolution();
        if (record.getSourceType() == WaypointSourceType.ARCHAEOLOGY_REPORT) {
            return resolution == WaypointResolution.PENDING
                    || resolution == WaypointResolution.SEARCH_STEP
                    || resolution == WaypointResolution.EXACT_SAVED;
        }
        return resolution == WaypointResolution.STATIC_EXACT;
    }

    private static Set<NavigationTargetKey> globallyEligibleKeys(
            List<WaypointRecord> records) {
        Set<NavigationTargetKey> result = new HashSet<NavigationTargetKey>();
        for (WaypointRecord record : records) {
            if (globallyEligible(record) && record.isEnabled()) result.add(key(record));
        }
        return result;
    }

    private static boolean contains(List<WaypointRecord> records,
                                    NavigationTargetKey expected) {
        for (WaypointRecord record : records) {
            if (expected.equals(key(record))) return true;
        }
        return false;
    }

    private static WaypointRecord find(List<WaypointRecord> records,
                                       NavigationTargetKey expected) {
        for (WaypointRecord record : records) {
            if (expected.equals(key(record))) return record;
        }
        return null;
    }

    private static boolean isSystem(List<WaypointRecord> records,
                                    NavigationTargetKey expected) {
        WaypointRecord record = find(records, expected);
        return record != null
                && record.getSourceType() == WaypointSourceType.VANILLA_SYSTEM;
    }

    private static NavigationTargetKey key(WaypointRecord record) {
        return new NavigationTargetKey(
                record.getServerIdentity().getEndpointFingerprint(), record.getId());
    }

    private static Comparator<WaypointRecord> priorityComparator(
            final NavigationTargetKey selected,
            final NavigationTargetKey navigator) {
        return new Comparator<WaypointRecord>() {
            @Override public int compare(WaypointRecord left, WaypointRecord right) {
                boolean leftSystem = left.getSourceType()
                        == WaypointSourceType.VANILLA_SYSTEM;
                boolean rightSystem = right.getSourceType()
                        == WaypointSourceType.VANILLA_SYSTEM;
                if (leftSystem != rightSystem) return leftSystem ? -1 : 1;
                if (leftSystem) {
                    int systemOrder = Integer.compare(systemOrder(left),
                            systemOrder(right));
                    if (systemOrder != 0) return systemOrder;
                }
                boolean leftNavigator = key(left).equals(navigator);
                boolean rightNavigator = key(right).equals(navigator);
                if (leftNavigator != rightNavigator) return leftNavigator ? -1 : 1;
                boolean leftSelected = key(left).equals(selected);
                boolean rightSelected = key(right).equals(selected);
                if (leftSelected != rightSelected) return leftSelected ? -1 : 1;
                int size = Float.compare(right.getMarkerStyle().getMarkerSize(),
                        left.getMarkerStyle().getMarkerSize());
                if (size != 0) return size;
                int updated = right.getUpdatedAt().compareTo(left.getUpdatedAt());
                if (updated != 0) return updated;
                return key(left).compareTo(key(right));
            }
        };
    }

    private static int systemOrder(WaypointRecord record) {
        VanillaLandmarkKind kind = VanillaLandmarkKind.fromSourceKey(
                record.getSourceKey());
        if (kind == VanillaLandmarkKind.WHITE_LIGHT) return 0;
        if (kind == VanillaLandmarkKind.BLACK_LIGHT) return 1;
        if (kind == VanillaLandmarkKind.RIFT) return 2;
        return 3;
    }

    private static String navigationName(WaypointRecord record) {
        if (record.getSourceType() != WaypointSourceType.VANILLA_SYSTEM) {
            return record.getName();
        }
        VanillaLandmarkKind kind = VanillaLandmarkKind.fromSourceKey(
                record.getSourceKey());
        return kind == null ? record.getName() : kind.getNavigationName();
    }

    private static MarkerStyle renderedStyle(WaypointRecord record) {
        MarkerStyle style = record.getMarkerStyle();
        if (record.getSourceType() == WaypointSourceType.MANAGED_ANIMAL
                || record.getSourceType() == WaypointSourceType.MANAGED_ITEM) {
            // The source owns this shape. Keeping the stored colour/tuning also
            // upgrades marks written by the first Surroundings release.
            return new MarkerStyle(MarkerStyle.WorldStyle.EXCLAMATION,
                    style.getRed(), style.getGreen(), style.getBlue(),
                    style.getAlpha(), style.getMarkerSize(),
                    style.getBeamWidth(), style.isShowLabel(),
                    style.isShowDistance());
        }
        if (record.getSourceType() == WaypointSourceType.LOOT_MAP
                && style.getWorldStyle() == MarkerStyle.WorldStyle.LOOT_MAP_SCROLL) {
            return style;
        }
        if (record.getSourceType() == WaypointSourceType.ARCHAEOLOGY_REPORT
                && style.getWorldStyle()
                == MarkerStyle.WorldStyle.ARCHAEOLOGY_REPORT_SCROLL) {
            return style;
        }
        if (record.getSourceType() != WaypointSourceType.VANILLA_SYSTEM) {
            return UserMarkerStyles.editable(style);
        }
        if (style.getWorldStyle() != MarkerStyle.WorldStyle.RIFT) return style;
        return new MarkerStyle(MarkerStyle.WorldStyle.RIFT,
                1.0f, 0.0f, 0.0f, 1.0f, style.getMarkerSize(),
                style.getBeamWidth(), style.isShowLabel(),
                style.isShowDistance());
    }
}
