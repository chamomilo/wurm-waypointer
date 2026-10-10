package org.waypoints.next.tracking;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.waypoints.next.TestWaypoints;
import org.waypoints.next.model.*;
import org.waypoints.next.navigation.*;
import org.waypoints.next.persistence.*;
import org.waypoints.next.service.*;
import org.waypoints.next.validation.WaypointRecordValidator;
import java.time.Instant;
import java.util.*;
import static org.junit.Assert.*;

public class TrackingCatalogTest {
    @Rule public TemporaryFolder temporary=new TemporaryFolder();
    private final Instant now=Instant.parse("2026-10-08T10:00:00Z");
    private final ServerIdentity server=TestWaypoints.server("Novus",3726);
    private TrackingCatalog catalog(){TrackingCatalog catalog=new TrackingCatalog();catalog.bind(server,"Alice",now);return catalog;}
    @Test public void catalogueEntriesStayOutOfWaypointsUntilExplicitlyAdded(){
        TrackingCatalog catalog=catalog();
        for(ManagedKind kind:ManagedKind.values()){
            WaypointRecord r=catalog.candidate(kind,kind.name(),kind.name(),"Catalogue",now);
            assertEquals(WaypointResolution.PENDING,r.getResolution());
            catalog.live(kind,kind.name(),kind.name(),"Catalogue",new WaypointCoordinate(20,40,2d,WaypointLayer.SURFACE),now);
        }
        assertFalse(catalog.current().isEmpty());
        assertTrue(catalog.waypoints().isEmpty());
        WaypointRecord friend=catalog.find(ManagedKind.PLAYER,ManagedKind.PLAYER.name());
        catalog.enabled(friend.getId(),false,now);
        assertTrue(catalog.waypoints().isEmpty());
        catalog.enabled(friend.getId(),true,now);
        assertEquals(1,catalog.waypoints().size());
        catalog.enabled(friend.getId(),false,now.plusSeconds(1));
        assertEquals(friend.getId(),catalog.waypoints().get(0).getId());
        assertFalse(catalog.waypoints().get(0).isEnabled());
    }
    @Test public void removingAWaypointSurvivesCatalogueRefreshMovementAndRestart()throws Exception{
        TrackingCatalog catalog=catalog();
        WaypointRecord r=catalog.candidate(ManagedKind.PLAYER,"Friend","Friend","Online",now);
        catalog.enabled(r.getId(),true,now);
        assertEquals(WaypointResolution.PENDING,catalog.waypoints().get(0).getResolution());
        catalog.removeWaypoint(r.getId(),now.plusSeconds(1));
        catalog.candidate(ManagedKind.PLAYER,"Friend","Friend","Online",now.plusSeconds(2));
        catalog.live(ManagedKind.PLAYER,"Friend","Friend","Online",new WaypointCoordinate(20,40,2d,WaypointLayer.SURFACE),now.plusSeconds(3));
        assertTrue(catalog.waypoints().isEmpty());
        assertEquals(1,catalog.current().size());
        WaypointStore store=new WaypointStore(temporary.getRoot().toPath().resolve("removed.wpt"),new WaypointFormatCodec(new WaypointRecordValidator()));
        store.save(new WaypointDocument(catalog.all(),Collections.<OpaqueWaypointRecord>emptyList()));
        TrackingCatalog restored=catalog();restored.restore(store.load().getRecords());
        assertTrue(restored.waypoints().isEmpty());
        restored.enabled(r.getId(),true,now.plusSeconds(4));
        assertEquals(r.getId(),restored.waypoints().get(0).getId());
        assertEquals(20,restored.waypoints().get(0).getCoordinate().getTileX(),0);
    }
    @Test public void addedButDisabledMembershipSurvivesStorageAndRestart()throws Exception{
        TrackingCatalog catalog=catalog();
        WaypointRecord r=catalog.candidate(ManagedKind.ANIMAL,"42","Mare","Manage horse",now);
        catalog.enabled(r.getId(),true,now);catalog.enabled(r.getId(),false,now.plusSeconds(1));
        WaypointStore store=new WaypointStore(temporary.getRoot().toPath().resolve("disabled.wpt"),new WaypointFormatCodec(new WaypointRecordValidator()));
        store.save(new WaypointDocument(catalog.all(),Collections.<OpaqueWaypointRecord>emptyList()));
        TrackingCatalog restored=catalog();restored.restore(store.load().getRecords());
        assertEquals(r.getId(),restored.waypoints().get(0).getId());
        assertFalse(restored.waypoints().get(0).isEnabled());
    }
    @Test public void legacyCacheKeepsEnabledChoicesAndHidesAutomaticCandidates(){
        TrackingCatalog old=catalog();
        WaypointRecord selected=old.candidate(ManagedKind.ANIMAL,"42","Mare","Manage horse",now);
        WaypointRecord automatic=old.candidate(ManagedKind.PLAYER,"Friend","Friend","Offline",now);
        Map<String,List<String>> selectedExtensions=new LinkedHashMap<String,List<String>>(selected.getExtensions());
        selectedExtensions.remove("tracking.waypoint.added");
        Map<String,List<String>> automaticExtensions=new LinkedHashMap<String,List<String>>(automatic.getExtensions());
        automaticExtensions.remove("tracking.waypoint.added");
        TrackingCatalog restored=catalog();restored.restore(Arrays.asList(
                WaypointRecord.copyOf(selected).enabled(true).extensions(selectedExtensions).build(),
                WaypointRecord.copyOf(automatic).extensions(automaticExtensions).build()));
        assertEquals(2,restored.current().size());
        assertEquals(selected.getId(),restored.waypoints().get(0).getId());
        restored.enabled(selected.getId(),false,now);
        assertEquals(1,restored.waypoints().size());
    }
    @Test public void lastSeenSurvivesRestartAndRemainsQualifiedByAccountAndEndpoint()throws Exception{
        TrackingCatalog catalog=catalog();WaypointRecord record=catalog.live(ManagedKind.ANIMAL,"42","Mare","Manage horse",new WaypointCoordinate(20,40,2d,WaypointLayer.SURFACE),now);catalog.enabled(record.getId(),true,now);
        catalog.vanished(ManagedKind.ANIMAL,"42",now.plusSeconds(10));record=catalog.find(record.getId());assertEquals(WaypointResolution.LAST_SEEN,record.getResolution());assertEquals(now,record.getLastResolvedAt());
        WaypointStore store=new WaypointStore(temporary.getRoot().toPath().resolve("targets.wpt"),new WaypointFormatCodec(new WaypointRecordValidator()));store.save(new WaypointDocument(catalog.all(),Collections.<OpaqueWaypointRecord>emptyList()));
        TrackingCatalog restored=catalog();restored.restore(store.load().getRecords());assertEquals(record.getId(),restored.find(ManagedKind.ANIMAL,"42").getId());assertTrue(restored.find(record.getId()).isEnabled());
        restored.bind(server,"Bob",now);assertTrue(restored.current().isEmpty());restored.bind(TestWaypoints.server("Liberty",3725),"Alice",now);assertTrue(restored.current().isEmpty());restored.bind(server,"Alice",now);assertEquals(1,restored.current().size());
        restored.retentionDays(1);restored.expire(now.plusSeconds(86401));assertNull(restored.find(record.getId()).getCoordinate());assertTrue(restored.find(record.getId()).isEnabled());
    }
    @Test public void livePositionsMoveWhileApproximateReadingsNeverBecomeExactCoordinates(){
        TrackingCatalog catalog=catalog();WaypointRecord record=catalog.live(ManagedKind.PLAYER,"Friend","Friend","Online",new WaypointCoordinate(20,40,2d,WaypointLayer.SURFACE),now);catalog.enabled(record.getId(),true,now);
        StaticNavigationRegistry registry=new StaticNavigationRegistry();NavigationContext context=new NavigationContext(server,"Alice",64);
        NavigationSnapshot first=registry.reconcile(new WaypointRevisionSnapshot(catalog.revision(),catalog.all()),context);assertEquals(20,first.getTargets().get(0).getCoordinate().getTileX(),0);
        catalog.live(ManagedKind.PLAYER,"friend","Friend","Online",new WaypointCoordinate(22,41,2d,WaypointLayer.SURFACE),now.plusSeconds(1));assertEquals(1,catalog.all().size());
        NavigationSnapshot moved=registry.reconcile(new WaypointRevisionSnapshot(catalog.revision(),catalog.all()),context);assertEquals(22,moved.getTargets().get(0).getCoordinate().getTileX(),0);
        catalog.vanished(ManagedKind.PLAYER,"Friend",now.plusSeconds(2));NavigationSnapshot old=registry.reconcile(new WaypointRevisionSnapshot(catalog.revision(),catalog.all()),context);assertTrue(old.getTargets().get(0).getName().contains("Last known"));assertEquals(MarkerStyle.WorldStyle.COMPASS_ONLY,old.getTargets().get(0).getMarkerStyle().getWorldStyle());
        catalog.pending(ManagedKind.PLAYER,"Friend","Other server",now.plusSeconds(3));assertTrue(registry.reconcile(new WaypointRevisionSnapshot(catalog.revision(),catalog.all()),context).getTargets().isEmpty());
    }
    @Test public void bearingsIntersectAndExpireWithoutInventingAPoint(){
        TrackingCatalog catalog=catalog();WaypointRecord record=catalog.candidate(ManagedKind.ANIMAL,"42","Mare","Manage horse",now);
        catalog.bearing(ManagedKind.ANIMAL,"42",AnimalBearing.parse("The Mare is in front of you, some distance away.","Mare",100,100,0),now);
        BearingRegion region=new BearingRegion(catalog.find(record.getId()));assertTrue(region.contains(100,70));assertFalse(region.contains(130,100));
        catalog.bearing(ManagedKind.ANIMAL,"42",AnimalBearing.parse("The Mare is ahead of you to the left, some distance away.","Mare",120,100,0),now.plusSeconds(5));region=new BearingRegion(catalog.find(record.getId()));assertEquals(2,region.readings());assertTrue(region.contains(100,70));assertFalse(region.contains(110,70));assertNull(catalog.find(record.getId()).getCoordinate());
        catalog.expire(now.plusSeconds(126));assertEquals(WaypointResolution.PENDING,catalog.find(record.getId()).getResolution());
        AnimalBearing standing=AnimalBearing.parse("You are practically standing on the Mare!","Mare",10,20,0);assertEquals(180,standing.halfWidth,0);assertNull(AnimalBearing.parse("The Other is in front of you, very close.","Mare",0,0,0));
    }
    @Test public void approximateReadingPreservesThePreviousExactHistoryAcrossRestartAndExpiry(){
        TrackingCatalog catalog=catalog();WaypointRecord record=catalog.live(ManagedKind.ANIMAL,"42","Mare","Manage horse",new WaypointCoordinate(20,40,2d,WaypointLayer.SURFACE),now);catalog.enabled(record.getId(),true,now);catalog.vanished(ManagedKind.ANIMAL,"42",now.plusSeconds(1));
        catalog.bearing(ManagedKind.ANIMAL,"42",AnimalBearing.parse("The Mare is some distance away in front of you.","Mare",30,60,0),now.plusSeconds(10));
        assertNull(catalog.find(record.getId()).getCoordinate());
        assertTrue(new StaticNavigationRegistry().reconcile(new WaypointRevisionSnapshot(catalog.revision(),catalog.all()),new NavigationContext(server,"Alice",64)).getTargets().isEmpty());
        TrackingCatalog restored=catalog();restored.restore(catalog.all());assertEquals(WaypointResolution.LAST_SEEN,restored.find(record.getId()).getResolution());assertEquals(now,restored.find(record.getId()).getLastResolvedAt());assertEquals(20,restored.find(record.getId()).getCoordinate().getTileX(),0);
        catalog.expire(now.plusSeconds(131));assertEquals(WaypointResolution.LAST_SEEN,catalog.find(record.getId()).getResolution());assertEquals(now,catalog.find(record.getId()).getLastResolvedAt());
        catalog.clearLastSeen(record.getId(),now.plusSeconds(132));assertNull(catalog.find(record.getId()).getCoordinate());
    }
    @Test public void aShipSeenAsAGroundObjectAndAsAManageEntryHasOneIdentity(){
        TrackingCatalog catalog=catalog();WaypointRecord nearby=catalog.live(ManagedKind.VEHICLE,"42","Sailboat","Nearby Ships",new WaypointCoordinate(20,40,2d,WaypointLayer.SURFACE),now);catalog.enabled(nearby.getId(),true,now);
        WaypointRecord ship=catalog.candidate(ManagedKind.SHIP,"42","Sailboat","Manage sailing boat",now.plusSeconds(1));assertEquals(nearby.getId(),ship.getId());assertTrue(ship.isEnabled());assertEquals(1,catalog.all().size());assertEquals("tracked:SHIP:42",ship.getSourceKey());
        assertEquals(ship.getId(),catalog.find(ManagedKind.VEHICLE,"42").getId());
    }
}
