package com.wurmonline.client.renderer.gui;

import javassist.*;
import org.junit.Test;
import org.waypoints.next.map.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import javassist.expr.*;
import org.waypoints.next.navigation.HighwayTileIndex;
import static org.junit.Assert.*;

/** Real input dispatch with a fixed connected world; no GPU or game connection. */
public class ServerMapBrowserTest {
    @Test public void gallerySelectsEveryWorldAndReturnsHomeWithoutCreatingForeignWaypoints() throws Throwable {
        ClassPool pool=new ClassPool(true);
        String gui="com.wurmonline.client.renderer.gui.",probe=Probe.class.getName();
        for(String name:new String[]{gui+"WurmComponent",gui+"text.TextFont",gui+"HeadsUpDisplay",gui+"text.WaypointerMiniMapFonts"}) {
            CtClass type=pool.get(name);if(type.getClassInitializer()!=null)type.getClassInitializer().setBody("{}");
        }
        pool.get(gui+"text.WaypointerMiniMapFonts").getDeclaredMethod("healthbarTitle").setBody("{ return null; }");
        CtClass runtime=pool.get("org.waypoints.next.integration.WurmWaypointerRuntime");
        runtime.getClassInitializer().setBody("{}");
        runtime.getDeclaredMethod("serverMapSnapshot").setBody("{ return "+probe+".snapshot(); }");
        runtime.getDeclaredMethod("browsedMapSnapshot").setBody("{ return "+probe+".browsing($1); }");
        runtime.getDeclaredMethod("browsedMapHighways").setBody("{ return "+probe+".roads(); }");
        runtime.getDeclaredMethod("stopMapBrowsing").setBody("{ "+probe+".stopped(); }");
        runtime.getDeclaredMethod("currentPlayerTileX").setBody("{ return 100; }");
        runtime.getDeclaredMethod("currentPlayerTileY").setBody("{ return 200; }");
        for(String name:new String[]{"serverMapShowsDeeds","serverMapShowsHighways"})runtime.getDeclaredMethod(name).setBody("{ return true; }");
        runtime.getDeclaredMethod("serverMapWaypoints").setBody("{ return null; }");
        runtime.getDeclaredMethod("currentServerIdentity").setBody("{ return null; }");
        runtime.getDeclaredMethod("currentPlayerName").setBody("{ return \"Alice\"; }");
        runtime.getDeclaredMethod("serverMapWaypointRequested").setBody("{ throw new AssertionError((Object)\"Foreign waypoint requested\"); }");
        CtClass bridge=pool.get(ServerMapWindowBridge.class.getName());
        bridge.getDeclaredMethod("isTopmostMapTarget").setBody("{ return true; }");
        bridge.getDeclaredMethod("renderMapBrowser").instrument(new ExprEditor(){
            @Override public void edit(MethodCall call)throws CannotCompileException{
                String name=call.getMethodName();
                if(name.equals("pushClip"))call.replace("{ $_ = false; }");
                if(name.equals("popClip")||name.equals("drawWaterBacking")||name.equals("drawSurface")||name.equals("drawMainMapChrome"))call.replace("{}");
                if(name.equals("mapGalleryTexture"))call.replace("{ $_ = null; }");
                if(name.equals("drawDeeds"))call.replace("{ "+probe+".drawnDeeds($6); }");
                if(name.equals("drawHighways"))call.replace("{ "+probe+".drawnRoads($7); }");
            }
        });
        Loader loader=new Loader(pool);loader.delegateLoadingOf("org.junit.");loader.run(probe,new String[0]);
    }
    public static final class Probe {
        private static ServerMapProfile selectedProfile;
        private static String deedName="";
        private static int roadX=-1;
        private static int stops;
        public static void stopped(){stops++;}
        public static ServerMapSnapshot browsing(ServerMapProfile profile) {
            selectedProfile=profile;
            return ServerMapSnapshot.empty(profile).withDeeds(DeedParser.parse("[{\"name\":\""+profile.getId()+"\",\"x\":10,\"y\":20,\"tilesNorth\":1,\"tilesSouth\":1,\"tilesEast\":1,\"tilesWest\":1}]",profile.getMapWidth(),profile.getMapHeight()),"fixture",DeedDataStatus.READY,java.time.Instant.now(),"");
        }
        public static HighwayTileIndex roads(){
            int x=selectedProfile.getBackendId();
            return HighwayTileIndex.parse("[{\"startX\":"+x+",\"startY\":1,\"endX\":"+x+",\"endY\":3,\"type\":2}]",selectedProfile.getMapWidth(),selectedProfile.getMapHeight());
        }
        public static void drawnDeeds(List<Deed> deeds){deedName=deeds.get(0).getName();}
        public static void drawnRoads(HighwayTileIndex roads){roadX=roads.getSegments().get(0).getStartX();}
        public static ServerMapSnapshot snapshot() throws Exception {
            Constructor<ServerMapSnapshot> c=ServerMapSnapshot.class.getDeclaredConstructor(ServerMapProfile.class,Path.class,long.class,List.class,long.class,long.class);
            c.setAccessible(true);return c.newInstance(SklotopolisMapProfiles.NOVUS,Paths.get("surface.png"),1L,Collections.emptyList(),0L,1L);
        }
        private static Object field(Object object,String name)throws Exception {Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
        private static Object state(WorldMap map)throws Exception {Method m=ServerMapWindowBridge.class.getDeclaredMethod("activeState",WorldMap.class);m.setAccessible(true);return m.invoke(null,map);}
        private static void click(WorldMap map,int x,int y){assertTrue(ServerMapWindowBridge.leftPressed(map,x,y));assertTrue(ServerMapWindowBridge.leftReleased(map,x,y));}
        private static void render(WorldMap map,Object state)throws Exception {
            deedName="";roadX=-1;
            Method method=ServerMapWindowBridge.class.getDeclaredMethod("renderMapBrowser",WorldMap.class,com.wurmonline.client.renderer.backend.Queue.class,state.getClass(),ServerMapProfile.class,int.class,int.class);
            method.setAccessible(true);method.invoke(null,map,null,state,SklotopolisMapProfiles.NOVUS,map.x+3,map.y+21);
        }
        public static void main(String[] args)throws Exception {
            WorldMap map=(WorldMap)WaypointerLayoutProbe.allocate(WorldMap.class);map.x=10;map.y=20;
            Object state=state(map);MapViewport home=(MapViewport)field(state,"viewport");
            int toolbarX=map.x+3+22+30,toolbarY=map.y+21+25+16;
            // A release outside the button never opens the gallery.
            ServerMapWindowBridge.leftPressed(map,toolbarX,toolbarY);
            ServerMapWindowBridge.leftReleased(map,map.x+3+225,toolbarY);
            assertFalse((Boolean)field(state,"allMaps"));
            for(int index=0;index<5;index++){
                int beforeStops=stops;
                click(map,toolbarX,toolbarY);assertTrue((Boolean)field(state,"allMaps"));
                assertEquals("Entering gallery stops any remote synchronization",beforeStops+1,stops);
                int count=index<3?3:2,column=index<3?index:index-3;
                int x=map.x+3+(920-164*count-30*(count-1))/2+column*194+82;
                int y=map.y+21+(index<3?116:348)+82;
                // A press on one card and release elsewhere does not switch worlds.
                ServerMapWindowBridge.leftPressed(map,x,y);ServerMapWindowBridge.leftReleased(map,map.x+3+30,map.y+21+100);
                assertTrue((Boolean)field(state,"allMaps"));
                click(map,x,y);assertFalse((Boolean)field(state,"allMaps"));
                MapViewport selected=(MapViewport)field(state,"viewport");
                assertEquals("Connected world is unchanged","sklotopolis-novus",field(state,"profileId"));
                if(index==1){assertSame(home,selected);}else{
                    assertNotSame(home,selected);assertEquals(index,field(state,"browsedGalleryIndex"));
                    assertEquals(index==0?4096:2048,selected.getMapWidth());
                    render(map,state);assertEquals(selectedProfile.getId(),deedName);assertEquals(selectedProfile.getBackendId(),roadX);
                    boolean miniDeeds=MiniMapWindowBridge.areDeedsVisible(),miniRoads=MiniMapWindowBridge.areRoadsVisible();
                    click(map,map.x+3+618+30,toolbarY);render(map,state);assertEquals("",deedName);assertEquals(selectedProfile.getBackendId(),roadX);
                    click(map,map.x+3+686+30,toolbarY);render(map,state);assertEquals(-1,roadX);
                    assertEquals(miniDeeds,MiniMapWindowBridge.areDeedsVisible());assertEquals(miniRoads,MiniMapWindowBridge.areRoadsVisible());
                    click(map,map.x+3+618+30,toolbarY);click(map,map.x+3+686+30,toolbarY);
                    MapPoint deedPoint=selected.mapToScreen(10.5,20.5);
                    ServerMapWindowBridge.mouseMoved(map,map.x+3+(int)Math.round(deedPoint.getX()),map.y+21+(int)Math.round(deedPoint.getY()));
                    assertEquals(selectedProfile.getId(),((Deed)field(state,"hoveredDeed")).getName());
                    selected.centerOn(selected.getMapWidth()/2d,selected.getMapHeight()/2d);
                    double before=selected.getPixelsPerTile();
                    assertTrue(ServerMapWindowBridge.mouseWheeled(map,map.x+463,map.y+331,-3));
                    assertTrue(selected.getPixelsPerTile()>before);
                    ServerMapWindowBridge.leftPressed(map,map.x+463,map.y+331);
                    ServerMapWindowBridge.mouseDragged(map,map.x+483,map.y+351);
                    ServerMapWindowBridge.leftReleased(map,map.x+483,map.y+351);
                    assertEquals("Home viewport is preserved",100.5,home.getCenterX(),0);
                    click(map,map.x+463,map.y+331); // no foreign waypoint
                    assertTrue(ServerMapWindowBridge.rightPressed(map,map.x+463,map.y+331));
                    assertNull(field(state,"hoveredWaypointId"));assertNull(field(state,"hoveredDeed"));
                    beforeStops=stops;ServerMapWindowBridge.visibilityChanged(map,false);
                    assertEquals("Hiding map stops remote synchronization",beforeStops+1,stops);
                }
                click(map,map.x+3+358+36,toolbarY); // CENTER returns to connected map.
                assertSame(home,field(state,"viewport"));assertEquals(-1,field(state,"browsedGalleryIndex"));
                assertEquals(100.5,home.getCenterX(),0);assertEquals(200.5,home.getCenterY(),0);
            }
            ServerMapWindowBridge.reset(map);assertNotSame(state,state(map));
        }
    }
}
