package org.waypoints.next.model;

/** Stable identities for the three server-supplied vanilla navigation lights. */
public enum VanillaLandmarkKind {
    WHITE_LIGHT("Vanilla White Light", "White Light",
            MarkerStyle.WorldStyle.WHITE_LIGHT),
    BLACK_LIGHT("Vanilla Black Light", "Black Light",
            MarkerStyle.WorldStyle.BLACK_LIGHT),
    RIFT("Vanilla Rift", "Rift", MarkerStyle.WorldStyle.RIFT);

    private final String displayName;
    private final String navigationName;
    private final MarkerStyle.WorldStyle worldStyle;

    VanillaLandmarkKind(String displayName, String navigationName,
                        MarkerStyle.WorldStyle worldStyle) {
        this.displayName = displayName;
        this.navigationName = navigationName;
        this.worldStyle = worldStyle;
    }

    public String getDisplayName() { return displayName; }
    public String getNavigationName() { return navigationName; }
    public MarkerStyle.WorldStyle getWorldStyle() { return worldStyle; }

    public static VanillaLandmarkKind fromSourceKey(String sourceKey) {
        if (sourceKey == null) return null;
        try { return valueOf(sourceKey.trim()); }
        catch (IllegalArgumentException invalid) { return null; }
    }
}
