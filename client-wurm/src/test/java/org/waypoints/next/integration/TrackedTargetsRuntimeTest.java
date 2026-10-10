package org.waypoints.next.integration;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.waypoints.next.model.*;
import org.waypoints.next.persistence.*;
import org.waypoints.next.service.*;
import org.waypoints.next.tracking.*;
import org.waypoints.next.surroundings.*;
import org.waypoints.next.validation.WaypointRecordValidator;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.logging.Logger;
import static org.junit.Assert.*;

public class TrackedTargetsRuntimeTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void nearbyMarksFollowTheirObjectAndRemovalSurvivesMovementAndReload() throws Exception {
        Path path=temporary.getRoot().toPath().resolve("nearby.wpt");
        TrackedTargetsRuntime runtime=new TrackedTargetsRuntime(Logger.getAnonymousLogger());
        Properties properties=new Properties();properties.setProperty("dynamicTargetCacheFile",path.toString());runtime.configure(properties);
        java.lang.reflect.Field storeField=TrackedTargetsRuntime.class.getDeclaredField("persistence");storeField.setAccessible(true);
        TrackedTargetStore store=(TrackedTargetStore)storeField.get(runtime);
        long deadline=System.nanoTime()+5_000_000_000L;
        while(!store.loaded()&&System.nanoTime()<deadline)Thread.sleep(10);
        assertTrue(store.loaded());
        java.lang.reflect.Field catalogField=TrackedTargetsRuntime.class.getDeclaredField("catalog");catalogField.setAccessible(true);
        TrackingCatalog catalog=(TrackingCatalog)catalogField.get(runtime);
        catalog.bind(ServerIdentity.of(new ServerEndpoint("127.0.0.1",3724,27016),"Fixture","Fixture",ServerIdentity.Resolution.RESOLVED),"Alice",Instant.now());
        for(SurroundingKind kind:SurroundingKind.values()){
            SurroundingEntry original=SurroundingEntry.builder().kind(kind).wurmId(42+kind.ordinal()).name("same name").category(kind.name()).position(40,80,2).build();
            UUID id=runtime.trackNearby(original,true);
            assertNotNull(id);assertEquals(1,runtime.records().size());
            assertEquals(10d,runtime.find(id).getCoordinate().getTileX(),0d);
            if(kind==SurroundingKind.ANIMAL)assertEquals(Collections.singletonList(original.getKey()),new ArrayList<SurroundingKey>(runtime.markedKeys()));
            SurroundingEntry moved=SurroundingEntry.builder().kind(kind).wurmId(original.getWurmId()).name("same name").category(kind.name()).position(120,160,3).build();
            runtime.observe(null,null,null,null,moved,Instant.now());
            assertEquals(30d,runtime.find(id).getCoordinate().getTileX(),0d);
            assertEquals(40d,runtime.find(id).getCoordinate().getTileY(),0d);
            runtime.trackNearby(moved,false);
            assertTrue(runtime.records().isEmpty());assertTrue(runtime.markedKeys().isEmpty());
            runtime.observe(null,null,null,null,original,Instant.now());
            assertTrue("Movement must not restore removed membership",runtime.records().isEmpty());
            runtime.trackNearby(original,true);runtime.unmark(original.getKey());
            assertTrue(runtime.records().isEmpty());
            runtime.trackNearby(original,true);runtime.unmarkAll();
            assertTrue(runtime.records().isEmpty());assertTrue(runtime.markedKeys().isEmpty());
        }
        WaypointRecord ship=catalog.candidate(ManagedKind.SHIP,"200","Ship","Manage ship",Instant.now());
        SurroundingEntry vessel=SurroundingEntry.builder().kind(SurroundingKind.CONTAINER).wurmId(200).name("Ship").category("Ships").position(208,200,0).build();
        assertEquals("Reuse the Manage ship UUID",ship.getId(),runtime.trackNearby(vessel,true));
        runtime.observe(null,null,null,null,vessel,Instant.now());
        assertEquals(52d,runtime.find(ship.getId()).getCoordinate().getTileX(),0d);
        runtime.setTracked(ship.getId(),false);assertTrue(runtime.records().isEmpty());
        java.lang.reflect.Field diskField=TrackedTargetStore.class.getDeclaredField("disk");diskField.setAccessible(true);
        ((java.util.concurrent.ExecutorService)diskField.get(store)).submit(()->{}).get(5,java.util.concurrent.TimeUnit.SECONDS);
        WaypointDocument saved=new WaypointStore(path,new WaypointFormatCodec(new WaypointRecordValidator())).load();
        TrackingCatalog restored=new TrackingCatalog();restored.restore(saved.getRecords());
        assertEquals(4,restored.all().size());assertTrue(restored.waypoints().isEmpty());
    }

    @Test public void managerAndNavigationReceiveOnlyExplicitSelectionsFromPersistedCatalogue() throws Exception {
        Instant now = Instant.now();
        TrackingCatalog catalog = new TrackingCatalog();
        catalog.bind(ServerIdentity.of(new ServerEndpoint("127.0.0.1",3724,27016),
                "Fixture","Fixture",ServerIdentity.Resolution.RESOLVED),"Alice",now);
        WaypointRecord friend = catalog.candidate(ManagedKind.ANIMAL,"Mare","Mare","Manage horse",now);
        catalog.candidate(ManagedKind.ANIMAL,"42","Mare","Manage horse",now);
        catalog.live(ManagedKind.VEHICLE,"43","Cart","Manage cart",
                new WaypointCoordinate(20,40,2d,WaypointLayer.SURFACE),now);
        Path path = temporary.getRoot().toPath().resolve("targets.wpt");
        new WaypointStore(path,new WaypointFormatCodec(new WaypointRecordValidator()))
                .save(new WaypointDocument(catalog.all(),Collections.<OpaqueWaypointRecord>emptyList()));
        TrackedTargetsRuntime runtime = new TrackedTargetsRuntime(Logger.getAnonymousLogger());
        Properties properties = new Properties();
        properties.setProperty("dynamicTargetCacheFile",path.toString());
        runtime.configure(properties);
        long deadline = System.nanoTime()+5_000_000_000L;
        while(runtime.find(friend.getId())==null&&System.nanoTime()<deadline)Thread.sleep(10);
        assertNotNull("Saved catalogue loaded",runtime.find(friend.getId()));
        WaypointRevisionSnapshot base = new WaypointRevisionSnapshot(1,Collections.<WaypointRecord>emptyList());
        assertTrue(runtime.records().isEmpty());
        assertTrue(runtime.combine(base).getRecords().isEmpty());
        runtime.setTracked(friend.getId(),true);
        assertEquals(friend.getId(),runtime.records().get(0).getId());
        assertEquals(1,runtime.combine(base).getRecords().size());
        assertEquals(WaypointResolution.PENDING,runtime.records().get(0).getResolution());
        runtime.enabled(friend.getId(),false);
        assertEquals(1,runtime.records().size());
        assertFalse(runtime.records().get(0).isEnabled());
        assertTrue(runtime.delete(friend.getId()));
        assertTrue(runtime.records().isEmpty());
        assertTrue(runtime.combine(base).getRecords().isEmpty());
        assertNotNull("Catalogue entry remains available for another explicit Track",runtime.find(friend.getId()));
        runtime.setTracked(friend.getId(),true);
        assertEquals(1,runtime.records().size());
    }
}
