package org.waypoints.next.integration;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.waypoints.next.model.*;
import org.waypoints.next.navigation.*;
import org.waypoints.next.persistence.*;
import org.waypoints.next.service.WaypointRevisionSnapshot;
import org.waypoints.next.surroundings.*;
import org.waypoints.next.tracking.TrackingCatalog;
import org.waypoints.next.validation.WaypointRecordValidator;
import com.wurmonline.client.renderer.CreatureData;
import com.wurmonline.client.renderer.cell.CreatureCellRenderable;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import static org.junit.Assert.*;

public class TrackedCreatureDeathTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private final ServerIdentity server = ServerIdentity.of(
            new ServerEndpoint("127.0.0.1",3724,27016),"Fixture","Fixture",ServerIdentity.Resolution.RESOLVED);
    private TrackedTargetsRuntime runtime;
    private TrackingCatalog catalog;
    private ExecutorService disk;
    private Path path;

    @Before public void setup() throws Exception {
        runtime = new TrackedTargetsRuntime(Logger.getAnonymousLogger());
        path = temporary.getRoot().toPath().resolve("targets.wpt");
        Properties properties = new Properties();properties.setProperty("dynamicTargetCacheFile",path.toString());
        runtime.configure(properties);
        TrackedTargetStore store = (TrackedTargetStore)field(runtime,"persistence");
        disk = (ExecutorService)field(store,"disk");
        disk.submit(()->{}).get(5,TimeUnit.SECONDS);assertTrue(store.loaded());
        catalog = (TrackingCatalog)field(runtime,"catalog");catalog.bind(server,"Tester",Instant.now());
    }
    @After public void finish() throws Exception {
        if(disk!=null){disk.shutdown();assertTrue(disk.awaitTermination(5,TimeUnit.SECONDS));}
    }
    private static Object field(Object value,String name) throws Exception {
        Field field=value.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(value);
    }
    private static SurroundingEntry animal(long id) {
        return SurroundingEntry.builder().kind(SurroundingKind.ANIMAL).wurmId(id)
                .name("Wolf").category("wolf").position(40,80,2).build();
    }
    private NavigationSnapshot reconcile(StaticNavigationRegistry navigation) {
        return navigation.reconcile(new WaypointRevisionSnapshot(runtime.revision(),runtime.records()),
                new NavigationContext(server,"Tester",64));
    }

    @Test public void leavingViewKeepsLastPositionAndActiveNavigation() {
        SurroundingEntry wolf=animal(42);UUID id=runtime.trackNearby(wolf,true);
        StaticNavigationRegistry navigation=new StaticNavigationRegistry();reconcile(navigation);
        NavigationTargetKey key=new NavigationTargetKey(server.getEndpointFingerprint(),id);
        navigation.activateNavigator(key);
        runtime.removed(null,wolf.getKey(),Instant.now());
        assertEquals(WaypointResolution.LAST_SEEN,runtime.find(id).getResolution());
        assertEquals(10,runtime.find(id).getCoordinate().getTileX(),0);
        assertTrue(reconcile(navigation).find(key).isNavigatorActive());
        runtime.observe(null,null,null,null,wolf,Instant.now());
        assertEquals(WaypointResolution.LIVE_EXACT,runtime.find(id).getResolution());
    }

    @Test public void deathDeletesOnlyItsWaypointStopsNavigationAndPersistsDeletion() throws Exception {
        SurroundingEntry dead=animal(42),survivor=animal(43);
        UUID deadId=runtime.trackNearby(dead,true),survivorId=runtime.trackNearby(survivor,true);
        StaticNavigationRegistry navigation=new StaticNavigationRegistry();reconcile(navigation);
        NavigationTargetKey key=new NavigationTargetKey(server.getEndpointFingerprint(),deadId);
        navigation.activateNavigator(key);
        runtime.removed(null,dead.getKey(),Instant.now());
        assertEquals(Collections.singletonList(deadId),runtime.subjectVanished(dead.getKey()));
        assertNull(runtime.find(deadId));assertNotNull(runtime.find(survivorId));
        assertNull(reconcile(navigation).find(key));assertFalse(navigation.snapshot().getTargets().get(0).isNavigatorActive());
        assertTrue(runtime.subjectVanished(dead.getKey()).isEmpty());
        runtime.observe(null,null,null,null,dead,Instant.now());
        SurroundingEntry corpse=SurroundingEntry.builder().kind(SurroundingKind.ITEM).wurmId(99)
                .name("corpse of Wolf").category("corpse").position(40,80,2).build();
        runtime.observe(null,null,null,null,corpse,Instant.now());
        assertEquals(1,runtime.records().size());assertEquals(survivorId,runtime.records().get(0).getId());
        disk.submit(()->{}).get(5,TimeUnit.SECONDS);
        WaypointDocument saved=new WaypointStore(path,new WaypointFormatCodec(new WaypointRecordValidator())).load();
        TrackingCatalog restored=new TrackingCatalog();restored.restore(saved.getRecords());restored.bind(server,"Tester",Instant.now());
        assertNull(restored.find(deadId));assertEquals(1,restored.waypoints().size());
    }

    @Test public void dyingRenderableCannotReappearInSurroundingsDuringItsAnimation() throws Exception {
        CreatureData data=new CreatureData(42,"model.creature.wolf","Wolf",(byte)0,
                40,80,2,0,0,false,(byte)0,0,(byte)0);
        Field unsafeField=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");unsafeField.setAccessible(true);
        CreatureCellRenderable renderable=(CreatureCellRenderable)((sun.misc.Unsafe)unsafeField.get(null)).allocateInstance(CreatureCellRenderable.class);
        Field creature=CreatureCellRenderable.class.getDeclaredField("creature");creature.setAccessible(true);creature.set(renderable,data);
        SurroundingsRuntime surroundings=new SurroundingsRuntime(Logger.getAnonymousLogger());
        assertNotNull(surroundings.creatureMoved(renderable,40,80,2));
        surroundings.removeAuthoritatively(animal(42).getKey());
        assertNull(surroundings.creatureMoved(renderable,40,80,2));
        assertNull(surroundings.find(animal(42).getKey()));
        surroundings.clearRenderables();assertNotNull(surroundings.creatureMoved(renderable,40,80,2));
    }
}
