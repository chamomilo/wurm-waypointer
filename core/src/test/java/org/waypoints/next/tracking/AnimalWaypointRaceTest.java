package org.waypoints.next.tracking;

import org.junit.Test;
import org.waypoints.next.model.*;
import org.waypoints.next.source.MapBounds;
import org.waypoints.next.TestWaypoints;
import java.time.Instant;
import java.util.UUID;
import static org.junit.Assert.*;

public class AnimalWaypointRaceTest {
    @Test public void checksOnlyInsideTenTilesAndWaitsForOneResponse(){
        AnimalWaypointRace race=new AnimalWaypointRace();race.start(UUID.randomUUID());
        assertTrue(race.needsReading(100,100));race.requested();assertFalse(race.needsReading(100,100));
        race.reading(AnimalBearing.parse("The Mare is some distance away in front of you.","Mare",100,100,0),new MapBounds(4096,4096),2d,WaypointLayer.SURFACE);
        assertFalse(race.needsReading(100,75)); // Destination Y=65; exactly ten tiles does not check.
        assertTrue(race.needsReading(100,74.99));race.requested();assertFalse(race.needsReading(100,65));
        race.retry();assertTrue(race.needsReading(100,65));
    }
    @Test public void newReadingRetargetsMovingAnimalWithoutSavingAnInventedSighting(){
        TrackingCatalog catalog=new TrackingCatalog();Instant now=Instant.now();catalog.bind(TestWaypoints.server("Novus",3726),"Alice",now);
        WaypointRecord animal=catalog.candidate(ManagedKind.ANIMAL,"42","Mare","Manage horse",now);
        AnimalWaypointRace race=new AnimalWaypointRace();race.start(animal.getId());
        race.reading(AnimalBearing.parse("The Mare is some distance away in front of you.","Mare",100,100,0),new MapBounds(4096,4096),2d,WaypointLayer.SURFACE);
        WaypointRecord step=race.project(animal);assertEquals(WaypointResolution.SEARCH_STEP,step.getResolution());assertEquals(65,step.getCoordinate().getTileY(),0);
        assertNull(catalog.find(animal.getId()).getCoordinate());
        race.reading(AnimalBearing.parse("The Mare is some distance away behind you.","Mare",100,65,0),new MapBounds(4096,4096),2d,WaypointLayer.SURFACE);
        assertEquals(100,race.project(animal).getCoordinate().getTileY(),0);
        race.stop();assertSame(animal,race.project(animal));assertFalse(race.needsReading(100,100));
    }
    @Test public void farAndBoundaryReadingsStayInsideCurrentMap(){
        AnimalWaypointRace race=new AnimalWaypointRace();UUID id=UUID.randomUUID();race.start(id);
        race.reading(AnimalBearing.parse("The Mare is very far away to the right of you.","Mare",100,100,0),new MapBounds(1024,1024),null,WaypointLayer.CAVE);
        WaypointRecord r=TestWaypoints.staticRecord(id.toString(),"Mare","Alice",TestWaypoints.server("Novus",3726),100,100);
        WaypointCoordinate c=race.project(r).getCoordinate();assertEquals(1023.5,c.getTileX(),0);assertEquals(WaypointLayer.CAVE,c.getLayer());
    }
}
