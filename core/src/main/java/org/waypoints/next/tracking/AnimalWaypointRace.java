package org.waypoints.next.tracking;

import org.waypoints.next.model.*;
import org.waypoints.next.source.MapBounds;
import java.util.UUID;

/** One session-only search. A search destination is never saved as an animal sighting. */
public final class AnimalWaypointRace {
    public static final double CHECK_RADIUS_TILES = 10d;
    private UUID target;
    private WaypointCoordinate destination;
    private boolean waiting;

    public void start(UUID id) { target=id;destination=null;waiting=false; }
    public void stop() { target=null;destination=null;waiting=false; }
    public UUID target() { return target; }
    public boolean active(UUID id) { return target!=null&&target.equals(id); }
    public boolean waiting() { return waiting; }
    public void requested() { waiting=true; }
    public void retry() { waiting=false; }
    public boolean needsReading(double x,double y) {
        return target!=null&&!waiting&&(destination==null
                ||Math.hypot(x-destination.getTileX(),y-destination.getTileY())<CHECK_RADIUS_TILES);
    }
    public void reading(AnimalBearing bearing,MapBounds bounds,Double height,WaypointLayer layer) {
        if(target==null||bearing==null)return;
        // Each new response describes a possibly moving animal. Do not intersect stale readings.
        double distance=bearing.maximum>=65536?bearing.minimum+500d
                :(bearing.minimum+bearing.maximum)/2d;
        double angle=Math.toRadians(bearing.bearing);
        double x=bearing.originX+Math.sin(angle)*distance;
        double y=bearing.originY-Math.cos(angle)*distance;
        x=Math.max(.5d,Math.min(bounds.getWidth()-.5d,x));
        y=Math.max(.5d,Math.min(bounds.getHeight()-.5d,y));
        destination=new WaypointCoordinate(x,y,height,layer);
        waiting=false;
    }
    public WaypointRecord project(WaypointRecord record) {
        if(record==null||!active(record.getId())||destination==null)return record;
        return WaypointRecord.copyOf(record).coordinate(destination)
                .resolution(WaypointResolution.SEARCH_STEP)
                .arrivalRadiusMetres(WaypointArrival.DISABLED).build();
    }
}
