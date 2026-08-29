package org.waypoints.api;

/** Immutable request from an external mod to create or refresh an object-bound mark. */
public final class ObjectMarkRequest {
    public static final int DEFAULT_LIFETIME_SECONDS = 15 * 60;
    public static final int MAXIMUM_LIFETIME_SECONDS = 24 * 60 * 60;

    private final String ownerId;
    private final String markerKey;
    private final WurmObjectRef subject;
    private final ObjectMarkerType markerType;
    private final NavigationRequest navigation;
    private final int maximumLifetimeSeconds;

    private ObjectMarkRequest(Builder builder) {
        ownerId = required(builder.ownerId, "owner id");
        markerKey = required(builder.markerKey, "marker key");
        if (builder.subject == null) throw new IllegalArgumentException(
                "object subject is required");
        if (builder.markerType == null) throw new IllegalArgumentException(
                "marker type is required");
        if (builder.navigation == null) throw new IllegalArgumentException(
                "navigation request is required");
        if (builder.maximumLifetimeSeconds < 0
                || builder.maximumLifetimeSeconds > MAXIMUM_LIFETIME_SECONDS) {
            throw new IllegalArgumentException("maximum lifetime must be in 0.."
                    + MAXIMUM_LIFETIME_SECONDS + " seconds");
        }
        subject = builder.subject;
        markerType = builder.markerType;
        navigation = builder.navigation;
        maximumLifetimeSeconds = builder.maximumLifetimeSeconds;
    }

    public static Builder builder() { return new Builder(); }
    public String getOwnerId() { return ownerId; }
    public String getMarkerKey() { return markerKey; }
    public WurmObjectRef getSubject() { return subject; }
    public ObjectMarkerType getMarkerType() { return markerType; }
    public NavigationRequest getNavigation() { return navigation; }
    /** Zero means object lifetime only; positive values add a safety expiry. */
    public int getMaximumLifetimeSeconds() { return maximumLifetimeSeconds; }

    private static String required(String value, String label) {
        String clean = value == null ? "" : value.trim();
        if (clean.isEmpty()) throw new IllegalArgumentException(label + " is required");
        if (clean.length() > 120) throw new IllegalArgumentException(label + " is too long");
        if (clean.indexOf('\r') >= 0 || clean.indexOf('\n') >= 0) {
            throw new IllegalArgumentException(label + " must be one line");
        }
        return clean;
    }

    public static final class Builder {
        private String ownerId;
        private String markerKey;
        private WurmObjectRef subject;
        private ObjectMarkerType markerType = ObjectMarkerType.ALERT;
        private NavigationRequest navigation = NavigationRequest.NONE;
        private int maximumLifetimeSeconds = DEFAULT_LIFETIME_SECONDS;

        private Builder() { }
        public Builder ownerId(String value) { ownerId = value; return this; }
        public Builder markerKey(String value) { markerKey = value; return this; }
        public Builder subject(WurmObjectRef value) { subject = value; return this; }
        public Builder markerType(ObjectMarkerType value) {
            markerType = value;
            return this;
        }
        public Builder navigation(NavigationRequest value) {
            navigation = value;
            return this;
        }
        public Builder maximumLifetimeSeconds(int value) {
            maximumLifetimeSeconds = value;
            return this;
        }
        public ObjectMarkRequest build() { return new ObjectMarkRequest(this); }
    }
}
