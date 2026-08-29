package org.waypoints.api;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/** Stable, Wurm-independent entry point for optional client-mod integrations. */
public final class WaypointerApi {
    public static final int API_VERSION = 1;
    private static volatile WaypointerService runtime;

    private WaypointerApi() { }

    /** True only when the Waypointer runtime has installed a live API provider. */
    public static boolean isInstalled() { return runtime != null; }

    /** Zero when unavailable, otherwise the provider's supported contract version. */
    public static int apiVersion() {
        WaypointerService service = runtime;
        return service == null ? 0 : service.apiVersion();
    }

    public static Set<WaypointerCapability> capabilities() {
        WaypointerService service = runtime;
        if (service == null) return Collections.emptySet();
        Set<WaypointerCapability> supplied = service.capabilities();
        return supplied == null || supplied.isEmpty()
                ? Collections.<WaypointerCapability>emptySet()
                : Collections.unmodifiableSet(EnumSet.copyOf(supplied));
    }

    public static MarkResult markObject(ObjectMarkRequest request) {
        WaypointerService service = runtime;
        if (service == null) return MarkResult.failure(
                MarkResult.Status.WAYPOINTER_NOT_INSTALLED,
                "Waypointer runtime is not installed");
        if (request == null) return MarkResult.failure(
                MarkResult.Status.INVALID_REQUEST, "mark request is required");
        try {
            MarkResult result = service.markObject(request);
            return result == null ? MarkResult.failure(MarkResult.Status.FAILED,
                    "Waypointer returned no result") : result;
        } catch (IllegalArgumentException invalid) {
            return MarkResult.failure(MarkResult.Status.INVALID_REQUEST,
                    invalid.getMessage());
        } catch (Throwable failure) {
            return MarkResult.failure(MarkResult.Status.FAILED,
                    failure.getClass().getSimpleName());
        }
    }

    /** Reflection-friendly command for optional mods that do not link API DTOs. */
    public static boolean markObject(String ownerId, String markerKey,
                                     String objectKind, long wurmId,
                                     String markerType, boolean activateNavigation) {
        try {
            ObjectMarkRequest request = ObjectMarkRequest.builder()
                    .ownerId(ownerId).markerKey(markerKey)
                    .subject(new WurmObjectRef(WurmObjectKind.valueOf(
                            normalized(objectKind, "AUTO")), wurmId))
                    .markerType(ObjectMarkerType.valueOf(
                            normalized(markerType, "ALERT")))
                    .navigation(activateNavigation
                            ? NavigationRequest.ACTIVATE : NavigationRequest.NONE)
                    .build();
            return markObject(request).isSuccess();
        } catch (RuntimeException invalid) {
            return false;
        }
    }

    public static int subjectVanished(WurmObjectRef subject) {
        WaypointerService service = runtime;
        if (service == null || subject == null) return 0;
        try { return Math.max(0, service.subjectVanished(subject)); }
        catch (Throwable ignored) { return 0; }
    }

    /** Reflection-friendly lifecycle command. */
    public static int subjectVanished(String objectKind, long wurmId) {
        try {
            return subjectVanished(new WurmObjectRef(WurmObjectKind.valueOf(
                    normalized(objectKind, "AUTO")), wurmId));
        } catch (RuntimeException invalid) {
            return 0;
        }
    }

    public static boolean removeOwnedMarker(String ownerId, UUID markerId) {
        WaypointerService service = runtime;
        if (service == null || markerId == null) return false;
        try { return service.removeOwnedMarker(ownerId, markerId); }
        catch (Throwable ignored) { return false; }
    }

    public static boolean setNavigation(String ownerId, UUID markerId,
                                        boolean active) {
        WaypointerService service = runtime;
        if (service == null || markerId == null) return false;
        try { return service.setNavigation(ownerId, markerId, active); }
        catch (Throwable ignored) { return false; }
    }

    /** Runtime bootstrap; not an external-mod extension point. */
    public static synchronized void installRuntime(WaypointerService service) {
        if (service == null) throw new IllegalArgumentException(
                "Waypointer runtime service is required");
        if (service.apiVersion() < API_VERSION) throw new IllegalArgumentException(
                "Waypointer runtime API version is too old");
        runtime = service;
    }

    /** Runtime teardown; ignored if another provider currently owns the facade. */
    public static synchronized void uninstallRuntime(WaypointerService service) {
        if (runtime == service) runtime = null;
    }

    private static String normalized(String value, String fallback) {
        String clean = value == null ? "" : value.trim();
        return clean.isEmpty() ? fallback
                : clean.toUpperCase(java.util.Locale.ENGLISH);
    }
}
