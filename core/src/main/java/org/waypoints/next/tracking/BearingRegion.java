package org.waypoints.next.tracking;

import org.waypoints.next.model.*;
import java.util.*;

/** Intersection of fresh annular sectors. Coordinates remain unknown even when the region narrows. */
public final class BearingRegion {
    private final List<UncertaintyObservation> observations;
    private final List<double[]> origins=new ArrayList<double[]>();
    public final double centreX,centreY,radius;
    public BearingRegion(WaypointRecord record){
        observations=record.getUncertaintyObservations();List<String> raw=record.getExtensions().get("tracking.bearing.origins");
        if(raw==null||raw.size()!=observations.size()||raw.isEmpty())throw new IllegalArgumentException("Missing bearing origins");
        for(String origin:raw){String[] xy=origin.split(",");if(xy.length!=2)throw new IllegalArgumentException("Invalid bearing origin");double x=Double.parseDouble(xy[0]),y=Double.parseDouble(xy[1]);if(!Double.isFinite(x)||!Double.isFinite(y))throw new IllegalArgumentException("Invalid bearing origin");origins.add(new double[]{x,y});}
        double[] last=origins.get(origins.size()-1);centreX=last[0];centreY=last[1];radius=observations.get(observations.size()-1).getMaximumTiles();
    }
    public boolean contains(double x,double y){
        for(int i=0;i<observations.size();i++){
            UncertaintyObservation observation=observations.get(i);double dx=x-origins.get(i)[0],dy=y-origins.get(i)[1];
            double distance=Math.hypot(dx,dy);if(distance<observation.getMinimumTiles()||distance>observation.getMaximumTiles())return false;
            double bearing=(Math.toDegrees(Math.atan2(dx,-dy))+360)%360;
            double difference=Math.abs((bearing-observation.getBearingDegrees()+540)%360-180);
            if(difference>observation.getHalfWidthDegrees())return false;
        }
        return true;
    }
    public int readings(){return observations.size();}
}
