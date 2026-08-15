package org.waypoints.next.navigation;

import org.waypoints.next.model.MarkerStyle;
import org.waypoints.next.model.UserMarkerStyles;
import org.waypoints.next.model.WaypointCoordinate;
import org.waypoints.next.model.WaypointSourceType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Applies one non-persistent Manager draft to the render-only navigation view. */
public final class NavigationDraftOverlay {
    private NavigationDraftOverlay() {
    }

    public static NavigationSnapshot apply(NavigationSnapshot stored,
                                           String serverFingerprint,
                                           UUID draftId,
                                           UUID replacedId,
                                           String name,
                                           WaypointCoordinate coordinate,
                                           MarkerStyle markerStyle,
                                           long draftRevision) {
        if (stored == null) throw new IllegalArgumentException("stored snapshot is required");
        if (draftId == null) throw new IllegalArgumentException("draft id is required");
        if (coordinate == null) throw new IllegalArgumentException("draft coordinate is required");
        if (markerStyle == null) throw new IllegalArgumentException("draft style is required");
        NavigationTargetKey draftKey = new NavigationTargetKey(serverFingerprint,
                replacedId == null ? draftId : replacedId);
        markerStyle = UserMarkerStyles.editable(markerStyle);
        long expiresAtEpochMillis = 0L;
        boolean navigatorActive = false;
        if (replacedId != null) {
            for (NavigationTarget target : stored.getTargets()) {
                if (replacedId.equals(target.getKey().getWaypointId())) {
                    expiresAtEpochMillis = target.getExpiresAtEpochMillis();
                    navigatorActive = target.isNavigatorActive();
                    break;
                }
            }
        }
        NavigationTarget draft = new NavigationTarget(draftKey,
                clean(name).isEmpty() ? "Live preview" : clean(name),
                coordinate, markerStyle, WaypointSourceType.STATIC, true, true, 0,
                expiresAtEpochMillis, navigatorActive);
        // Live drafts never generate arrival notifications before OK.
        boolean visible = markerStyle.getWorldStyle()
                != MarkerStyle.WorldStyle.HIDDEN;
        List<NavigationTarget> targets = new ArrayList<NavigationTarget>(
                stored.getTargets().size() + 1);
        boolean inserted = false;
        for (NavigationTarget target : stored.getTargets()) {
            boolean replaced = replacedId != null
                    && replacedId.equals(target.getKey().getWaypointId());
            if (replaced) {
                if (!inserted && visible) targets.add(draft);
                inserted = true;
            } else {
                targets.add(target);
            }
        }
        if (!inserted && visible) targets.add(0, draft);
        long generation = Long.MIN_VALUE
                ^ stored.getGeneration() * 1_000_003L ^ draftRevision;
        return new NavigationSnapshot(stored.getSourceRevision(), generation, targets);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
