package org.waypoints.next.ui;

import org.waypoints.next.model.WaypointRecord;
import java.util.List;
import java.util.UUID;

/** Native hub port for separate animal and vehicle catalogues. */
public interface TrackingController {
    List<WaypointRecord> records(boolean animals, String search);
    String status();
    String detail(UUID id);
    default boolean canNavigate(UUID id){return false;}
    boolean canLocate(UUID id);
    default boolean canTrack(UUID id){return true;}
    void refresh(boolean animals);
    void setTracked(UUID id, boolean enabled);
    void locate(UUID id);
    void clearLastSeen(UUID id);
    long revision();
}
