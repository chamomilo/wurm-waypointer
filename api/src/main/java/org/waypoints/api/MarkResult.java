package org.waypoints.api;

import java.util.UUID;

/** Non-throwing result suitable for optional cross-mod integrations. */
public final class MarkResult {
    public enum Status {
        CREATED_OR_UPDATED,
        WAYPOINTER_NOT_INSTALLED,
        WAYPOINTER_NOT_READY,
        SUBJECT_NOT_FOUND,
        INVALID_REQUEST,
        FAILED
    }

    private final Status status;
    private final UUID markerId;
    private final String message;

    private MarkResult(Status status, UUID markerId, String message) {
        this.status = status;
        this.markerId = markerId;
        this.message = message == null ? "" : message;
    }

    public static MarkResult success(UUID markerId) {
        return new MarkResult(Status.CREATED_OR_UPDATED, markerId, "");
    }

    public static MarkResult failure(Status status, String message) {
        if (status == null || status == Status.CREATED_OR_UPDATED) {
            throw new IllegalArgumentException("failure status is required");
        }
        return new MarkResult(status, null, message);
    }

    public Status getStatus() { return status; }
    public UUID getMarkerId() { return markerId; }
    public String getMessage() { return message; }
    public boolean isSuccess() { return status == Status.CREATED_OR_UPDATED; }
}
