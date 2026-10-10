package org.waypoints.next.ui;

import org.waypoints.next.model.WaypointRecord;
import java.util.List;
import java.util.UUID;

/** Native hub port for managed catalogues and friends. */
public interface TrackingController {
    List<WaypointRecord> records(boolean friends, String search);
    String status();
    String detail(UUID id);
    default Boolean online(UUID id){return null;}
    boolean canLocate(UUID id);
    default boolean canTrack(UUID id){return true;}
    void refresh(boolean friends);
    void setTracked(UUID id, boolean enabled);
    void locate(UUID id);
    void clearLastSeen(UUID id);
    long revision();
}
