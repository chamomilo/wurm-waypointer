package org.waypoints.next.integration;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.waypoints.next.model.*;
import org.waypoints.next.navigation.*;
import org.waypoints.next.service.WaypointRevisionSnapshot;
import org.waypoints.next.surroundings.*;
import org.waypoints.next.tracking.*;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.*;
import java.util.logging.Logger;
import static org.junit.Assert.*;

public class AnimalWaypointRaceRuntimeTest {
    @Rule public TemporaryFolder temporary=new TemporaryFolder();
    private final Object owner=new Object();
    private long clock=System.currentTimeMillis();
    private double[] pose={100,100,0};
    private int requests;
    private final List<Map<String,String>> replies=new ArrayList<Map<String,String>>();
    private TrackedTargetsRuntime runtime;
    private ManagedCatalogueGateway gateway;
    private TrackingCatalog catalog;
    private UUID animal;
    private final ServerIdentity server=ServerIdentity.of(new ServerEndpoint("127.0.0.1",3724,27016),"Fixture","Fixture",ServerIdentity.Resolution.RESOLVED);
    private static Object field(Object object,String name)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    @Before public void setup()throws Exception{
        runtime=new TrackedTargetsRuntime(Logger.getAnonymousLogger(),new ManagedCatalogueGateway.Transport(){
            public Object owner(){return owner;}
            public void request(ManagedKind kind){assertEquals(ManagedKind.ANIMAL,kind);requests++;}
            public void reply(Map<String,String> reply,String title){replies.add(new HashMap<String,String>(reply));}
            public double[] pose(){return pose.clone();}
        },()->clock);
        Properties properties=new Properties();properties.setProperty("dynamicTargetCacheFile",temporary.getRoot().toPath().resolve("targets.wpt").toString());runtime.configure(properties);
        TrackedTargetStore store=(TrackedTargetStore)field(runtime,"persistence");long deadline=System.nanoTime()+5_000_000_000L;
        while(!store.loaded()&&System.nanoTime()<deadline)Thread.sleep(10);assertTrue(store.loaded());
        catalog=(TrackingCatalog)field(runtime,"catalog");catalog.bind(server,"Alice",Instant.ofEpochMilli(clock));
        gateway=(ManagedCatalogueGateway)field(runtime,"gateway");runtime.refresh(true);gateway.tick(null,clock);
        assertTrue(gateway.intercept(owner,"Alice's List of Animals",ManagedCatalogueGatewayTest.nativeForm(ManagedKind.ANIMAL,"100")));
        animal=catalog.find(ManagedKind.ANIMAL,"42").getId();clock+=1500;
    }
    private void tick(){runtime.tickRace(pose[0],pose[1],2d,WaypointLayer.SURFACE,Instant.ofEpochMilli(clock));gateway.tick(null,clock);}
    private String labelOnlyForm(String id){return ManagedCatalogueGatewayTest.nativeForm(ManagedKind.ANIMAL,id)
            .replace("radio{group=\"sel\";id=\"42\";text=\"\"}","label{text=\"\"}")
            .replace("label{color=\"127,255,127\"text=\"true\"};","harray{label{text=\"Yes\"}button{id=\"uncarefor42\";text=\"X\"}};");}
    @Test public void labelOnlyAnimalRefreshReplacesLegacyPlaceholderAndMatchesStationaryVisibleAnimal(){
        catalog.remove(animal);catalog.candidate(ManagedKind.ANIMAL,"-1","Mare","Manage horse",Instant.ofEpochMilli(clock));
        runtime.seedVisible(Collections.singletonList(SurroundingEntry.builder().kind(SurroundingKind.ANIMAL).wurmId(42).name("Mare").category("horse").position(540,296,3).build()),Instant.ofEpochMilli(clock));
        runtime.refresh(true);gateway.tick(null,clock);
        assertTrue(gateway.intercept(owner,"Alice's List of Animals",labelOnlyForm("101")));
        assertNull(catalog.find(ManagedKind.ANIMAL,"-1"));
        WaypointRecord result=catalog.find(ManagedKind.ANIMAL,"42");assertNotNull(result);
        assertTrue(runtime.canTrack(result.getId()));assertTrue(runtime.canNavigate(result.getId()));
        assertEquals(WaypointResolution.LIVE_EXACT,result.getResolution());assertEquals(135,result.getCoordinate().getTileX(),0);
        assertEquals(1,runtime.records(true,"").size());
    }
    @Test public void labelOnlyAnimalCanStartRaceAndOnlySendsFindAction(){
        runtime.refresh(true);gateway.tick(null,clock);
        assertTrue(gateway.intercept(owner,"Alice's List of Animals",labelOnlyForm("101")));
        assertTrue(runtime.canTrack(animal));assertTrue(runtime.navigate(animal));clock+=1500;tick();
        assertTrue(gateway.intercept(owner,"Alice's List of Animals",labelOnlyForm("102")));
        Map<String,String> reply=replies.get(replies.size()-1);
        assertEquals(new HashSet<String>(Arrays.asList("id","sel","find")),reply.keySet());
        assertEquals("42",reply.get("sel"));assertEquals("true",reply.get("find"));
    }
    @Test public void observationsBeforeCacheConfigurationRemainAvailableToManagedRefresh()throws Exception{
        TrackedTargetsRuntime early=new TrackedTargetsRuntime(Logger.getAnonymousLogger());
        early.observe(null,null,null,null,SurroundingEntry.builder().kind(SurroundingKind.ANIMAL).wurmId(42).name("Mare").category("horse").position(540,296,3).build(),Instant.ofEpochMilli(clock));
        Properties props=new Properties();props.setProperty("dynamicTargetCacheFile",temporary.getRoot().toPath().resolve("early.wpt").toString());early.configure(props);
        TrackedTargetStore store=(TrackedTargetStore)field(early,"persistence");long deadline=System.nanoTime()+5_000_000_000L;
        while(!store.loaded()&&System.nanoTime()<deadline)Thread.sleep(10);assertTrue(store.loaded());
        TrackingCatalog earlyCatalog=(TrackingCatalog)field(early,"catalog");earlyCatalog.bind(server,"Alice",Instant.ofEpochMilli(clock));
        java.lang.reflect.Method accept=TrackedTargetsRuntime.class.getDeclaredMethod("acceptCatalogue",ManagedKind.class,ManagedBmlParser.Result.class);accept.setAccessible(true);
        accept.invoke(early,ManagedKind.ANIMAL,ManagedBmlParser.parse("Manage Animals",labelOnlyForm("103"),ManagedKind.ANIMAL));
        assertEquals(WaypointResolution.LIVE_EXACT,earlyCatalog.find(ManagedKind.ANIMAL,"42").getResolution());
    }
    private void answer(String id,String text){
        assertTrue(gateway.intercept(owner,"Alice's List of Animals",ManagedCatalogueGatewayTest.nativeForm(ManagedKind.ANIMAL,id)));
        runtime.event(":Event",text,Instant.ofEpochMilli(clock));
    }
    @Test public void trackStartsUnknownAnimalRaceAndUntrackCancelsItsFindRequest(){
        runtime.setTracked(animal,true);
        assertTrue(runtime.find(animal).isEnabled());assertTrue(runtime.isRaceActive(animal));
        tick();assertEquals(2,requests);
        answer("101","The Mare is some distance away in front of you.");
        assertEquals(WaypointResolution.SEARCH_STEP,runtime.records().get(0).getResolution());
        assertEquals(animal,runtime.pollNavigationRequest().getWaypointId());
        runtime.setTracked(animal,false);assertFalse(runtime.isRaceActive(animal));
        assertTrue(runtime.records().isEmpty());assertNull(runtime.pollNavigationRequest());
    }
    @Test public void trackVisibleAnimalKeepsLivePositionWithoutStartingRace(){
        runtime.observe(null,null,null,null,SurroundingEntry.builder().kind(SurroundingKind.ANIMAL)
                .wurmId(42).name("Mare").category("horse").position(400,420,2).build(),Instant.ofEpochMilli(clock));
        runtime.setTracked(animal,true);
        assertEquals(WaypointResolution.LIVE_EXACT,runtime.find(animal).getResolution());
        assertTrue(runtime.find(animal).isEnabled());assertFalse(runtime.isRaceActive(animal));
        tick();assertEquals("No additional Find request for visible animal",1,requests);
    }
    @Test public void unknownAnimalNavigatesChecksAtTenTilesThenHandsOffToLiveTarget(){
        assertNull(runtime.find(animal).getCoordinate());assertTrue(runtime.canNavigate(animal));assertTrue(runtime.navigate(animal));
        tick();assertEquals(2,requests);assertTrue(runtime.isRaceActive(animal));
        answer("101","The Mare is some distance away in front of you.");
        assertEquals("42",replies.get(1).get("sel"));assertEquals("true",replies.get(1).get("find"));
        WaypointRecord step=runtime.records().get(0);assertEquals(WaypointResolution.SEARCH_STEP,step.getResolution());assertEquals(65,step.getCoordinate().getTileY(),0);
        assertNull("Search point is not saved as an animal sighting",runtime.find(animal).getCoordinate());
        NavigationTargetKey key=runtime.pollNavigationRequest();assertEquals(animal,key.getWaypointId());
        StaticNavigationRegistry navigation=new StaticNavigationRegistry();NavigationSnapshot snapshot=navigation.reconcile(new WaypointRevisionSnapshot(runtime.revision(),runtime.records()),new NavigationContext(server,"Alice",64));
        assertNotNull(snapshot.find(key));assertTrue(navigation.activateNavigator(key).find(key).isNavigatorActive());runtime.navigationOwner(animal);
        clock+=1500;pose[1]=75;tick();assertEquals("Exactly ten tiles does not check",2,requests);
        pose[1]=74.9;tick();assertEquals(3,requests);tick();assertEquals("Only one pending check",3,requests);
        answer("102","The Mare is some distance away to the right of you.");
        step=runtime.records().get(0);assertEquals(135,step.getCoordinate().getTileX(),0);assertEquals(74.9,step.getCoordinate().getTileY(),0);
        SurroundingEntry visible=SurroundingEntry.builder().kind(SurroundingKind.ANIMAL).wurmId(42).name("Mare").category("horse").position(540,296,3).build();
        runtime.observe(null,null,null,null,visible,Instant.ofEpochMilli(clock));assertFalse(runtime.isRaceActive(animal));
        assertEquals(WaypointResolution.LIVE_EXACT,runtime.records().get(0).getResolution());assertEquals(135,runtime.find(animal).getCoordinate().getTileX(),0);
        assertEquals(animal,runtime.pollNavigationRequest().getWaypointId());
        clock+=5000;tick();assertEquals("Race stops sending checks after live handoff",3,requests);
    }
    @Test public void cancellationAndOtherNavigationOwnersCannotRestartTheRace(){
        assertTrue(runtime.navigate(animal));tick();runtime.stopRace(animal);clock+=5000;tick();assertEquals(2,requests);
        assertFalse(gateway.intercept(owner,"Alice's List of Animals",ManagedCatalogueGatewayTest.nativeForm(ManagedKind.ANIMAL,"101")));
        assertNull(runtime.pollNavigationRequest());
        assertTrue(runtime.navigate(animal));tick();answer("102","The Mare is some distance away in front of you.");
        runtime.pollNavigationRequest();runtime.navigationOwner(animal);runtime.navigationOwner(UUID.randomUUID());assertFalse(runtime.isRaceActive(animal));
        clock+=5000;tick();assertEquals(3,requests);assertNull(runtime.pollNavigationRequest());
    }
    @Test public void unavailableAndTimedOutChecksEndTheSessionWithoutAnExactMarker(){
        assertTrue(runtime.navigate(animal));tick();answer("101","This creature is loaded in a cage, or on another server.");
        assertFalse(runtime.isRaceActive(animal));assertNull(runtime.find(animal).getCoordinate());assertNull(runtime.pollNavigationRequest());
        clock+=1500;assertTrue(runtime.navigate(animal));
        for(int i=0;i<3;i++){tick();clock+=8001;gateway.tick(null,clock);runtime.tickRace(100,100,2d,WaypointLayer.SURFACE,Instant.ofEpochMilli(clock));clock+=3000;}
        assertFalse(runtime.isRaceActive(animal));assertNull(runtime.pollNavigationRequest());
    }
    @Test public void untrackingCancelsPendingDirectionAndDiscardsQueuedLiveNavigation(){
        assertTrue(runtime.navigate(animal));tick();
        assertTrue(runtime.unmark(new SurroundingKey(SurroundingKind.ANIMAL,42)));
        assertFalse(runtime.isRaceActive(animal));
        assertFalse(gateway.intercept(owner,"Alice's List of Animals",ManagedCatalogueGatewayTest.nativeForm(ManagedKind.ANIMAL,"101")));
        assertNull(runtime.pollNavigationRequest());
        catalog.live(ManagedKind.ANIMAL,"42","Mare","Manage horse",new WaypointCoordinate(100,100,2d,WaypointLayer.SURFACE),Instant.ofEpochMilli(clock));
        assertTrue(runtime.navigate(animal));runtime.setTracked(animal,false);
        assertNull(runtime.pollNavigationRequest());
    }
    @Test public void vehicleAndAnimalListsAreSeparateAndLegacyFriendsAreDiscarded(){
        catalog.candidate(ManagedKind.VEHICLE,"44","Cart","Manage cart",Instant.ofEpochMilli(clock));
        assertEquals(WaypointSourceType.MANAGED_ANIMAL,runtime.records(true,"").get(0).getSourceType());
        assertEquals(1,runtime.records(false,"").size());assertEquals(WaypointSourceType.MANAGED_ITEM,runtime.records(false,"").get(0).getSourceType());
        WaypointRecord legacy=WaypointRecord.copyOf(catalog.find(animal)).id(UUID.randomUUID()).sourceType(WaypointSourceType.PLAYER).sourceKey("tracked:PLAYER:Friend").enabled(true).build();
        TrackingCatalog restored=new TrackingCatalog();restored.restore(Collections.singletonList(legacy));assertTrue(restored.all().isEmpty());
    }
    @Test public void confirmedDeathCancelsPendingSearchAndQueuedNavigation(){
        assertTrue(runtime.navigate(animal));tick();
        assertEquals(Collections.singletonList(animal),runtime.subjectVanished(new SurroundingKey(SurroundingKind.ANIMAL,42)));
        assertFalse(runtime.isRaceActive(animal));assertNull(runtime.find(animal));
        assertFalse(gateway.intercept(owner,"Alice's List of Animals",ManagedCatalogueGatewayTest.nativeForm(ManagedKind.ANIMAL,"101")));
        assertNull(runtime.pollNavigationRequest());
        clock+=5000;tick();assertEquals(2,requests);
        SurroundingEntry visible=SurroundingEntry.builder().kind(SurroundingKind.ANIMAL).wurmId(43).name("Wolf").category("wolf").position(400,400,2).build();
        UUID id=runtime.trackNearby(visible,true);assertTrue(runtime.navigate(id));
        runtime.subjectVanished(visible.getKey());assertNull(runtime.pollNavigationRequest());assertNull(runtime.find(id));
    }
}
