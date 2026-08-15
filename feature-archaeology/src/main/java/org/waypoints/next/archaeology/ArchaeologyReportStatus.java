package org.waypoints.next.archaeology;

public enum ArchaeologyReportStatus {
    REPORT_READY,
    TRACKING,
    VERY_CLOSE,
    KNOWN_LOCATION,
    CACHE_FOUND,
    DISMISSED;

    public boolean isActive() {
        return this != CACHE_FOUND && this != DISMISSED;
    }
}
