package org.waypoints.next.validation;

public final class WaypointLimits {
    public static final int MAX_RECORDS = 10_000;
    public static final long MAX_FILE_BYTES = 16L * 1024L * 1024L;
    public static final int MAX_NAME = 120;
    public static final int MAX_DESCRIPTION = 4_096;
    public static final int MAX_USER = 120;
    public static final int MAX_SERVER_NAME = 240;
    public static final int MAX_SOURCE_KEY = 512;
    public static final int MAX_GROUP = 120;
    public static final int MAX_TAGS = 32;
    public static final int MAX_TAG = 80;
    public static final int MAX_ALIASES = 32;
    public static final int MAX_OBSERVATIONS = 32;
    public static final int MAX_EXTENSION_FIELDS = 64;
    public static final int MAX_EXTENSION_VALUE = 4_096;

    private WaypointLimits() { }
}
