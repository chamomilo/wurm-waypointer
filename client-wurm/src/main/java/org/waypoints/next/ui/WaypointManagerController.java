package org.waypoints.next.ui;

import org.waypoints.next.model.MarkerStyle;
import org.waypoints.next.model.WaypointCoordinate;
import org.waypoints.next.service.WaypointManagerQuery;
import org.waypoints.next.service.WaypointManagerSnapshot;
import org.waypoints.next.source.ParsedCoordinate;

import java.util.List;
import java.util.UUID;

/** Port used by the package-private Wurm GUI bridge. */
public interface WaypointManagerController {
    WaypointManagerContext context();
    WaypointManagerSnapshot snapshot(WaypointManagerQuery query);
    WaypointEditData editData(UUID id);
    ParsedCoordinate preview(String input);
    void livePreview(UUID editingId, String name, WaypointCoordinate coordinate,
                     MarkerStyle markerStyle);
    void clearLivePreview();
    String clipboardText();
    void addHere(String name, MarkerStyle markerStyle, int arrivalRadiusMetres,
                 int lifetimeMinutes);
    void addCoordinates(String name, String input, MarkerStyle markerStyle,
                        int arrivalRadiusMetres, int lifetimeMinutes);
    void editStatic(UUID id, String name, String input, MarkerStyle markerStyle,
                    int arrivalRadiusMetres, int lifetimeMinutes);
    void duplicate(UUID id);
    void share(UUID id);
    void importSharedClipboard();
    boolean isNavigatorActive(UUID id);
    boolean toggleNavigator(UUID id);
    void setEnabled(UUID id, boolean enabled);
    void setEnabled(List<UUID> ids, boolean enabled);
    void delete(UUID id);
    void exportAll();
    void importAll();
    void openSurroundings();
    long revision();
    void reportFailure(String operation, Throwable failure);
}
