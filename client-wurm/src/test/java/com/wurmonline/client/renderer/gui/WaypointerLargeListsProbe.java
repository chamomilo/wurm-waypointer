package com.wurmonline.client.renderer.gui;

import org.waypoints.next.i18n.Messages;
import org.waypoints.next.map.Deed;
import org.waypoints.next.model.*;
import org.waypoints.next.service.*;
import org.waypoints.next.surroundings.*;
import org.waypoints.next.tracking.*;
import org.waypoints.next.ui.*;
import org.waypoints.next.navigation.NavigationTarget;
import org.waypoints.next.navigation.NavigationTargetKey;
import java.lang.reflect.*;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;

/** Dense fixtures for every other unbounded catalogue using the real native UI. */
public final class WaypointerLargeListsProbe {
    static void verify(String output) throws Exception {
        ServerIdentity server=ServerIdentity.of(new ServerEndpoint("127.0.0.1",3724,27016),"Fixture","Fixture",ServerIdentity.Resolution.RESOLVED);
        TrackingCatalog seed=new TrackingCatalog();seed.bind(server,"Alice",Instant.now());
        WaypointRecord template=seed.live(ManagedKind.ANIMAL,"0","Target 0000","Manage horse",new WaypointCoordinate(110,110,2d,WaypointLayer.SURFACE),Instant.now());
        List<WaypointRecord> records=new ArrayList<WaypointRecord>();
        List<Deed> deeds=new ArrayList<Deed>();
        List<NavigationTarget> clusterTargets=new ArrayList<NavigationTarget>();
        Constructor<NavigationTarget> targetConstructor=NavigationTarget.class.getDeclaredConstructor(NavigationTargetKey.class,String.class,WaypointCoordinate.class,MarkerStyle.class,WaypointSourceType.class,boolean.class,boolean.class,int.class);
        targetConstructor.setAccessible(true);
        SurroundingsCatalog nearby=new SurroundingsCatalog();
        for(int index=0;index<2500;index++) {
            records.add(WaypointRecord.copyOf(template).id(UUID.randomUUID()).name(String.format(Locale.ROOT,"Target %04d",index)).sourceKey("tracked:ANIMAL:"+index).build());
            deeds.add(Deed.fromProviderData("deed-"+index,String.format(Locale.ROOT,"Deed %04d",index),index,100,Collections.emptyMap()));
            nearby.upsert(SurroundingEntry.builder().kind(SurroundingKind.ITEM).wurmId(index).name("item-"+index).category("Decorations").position(index,100,0).build());
            clusterTargets.add(targetConstructor.newInstance(new NavigationTargetKey(server.getEndpointFingerprint(),records.get(index).getId()),"Target "+index,template.getCoordinate(),template.getMarkerStyle(),template.getSourceType(),false,true,0));
        }
        final UUID[] acted={null};
        TrackingController tracking=proxy(TrackingController.class,(p,m,a)->{
            switch(m.getName()) {
                case "records":return new ArrayList<WaypointRecord>(records);
                case "detail":return "Manage horse";
                case "status":return "2500 managed targets";
                case "canTrack":case "canNavigate":return true;
                case "setTracked":case "clearLastSeen":acted[0]=(UUID)a[0];return null;
                case "revision":return 1L;
                default:return empty(m);
            }
        });
        WaypointManagerController manager=proxy(WaypointManagerController.class,(p,m,a)->{
            switch(m.getName()) {
                case "context":return new WaypointManagerContext("Alice",server,100,100,2,WaypointLayer.SURFACE);
                case "snapshot":return new WaypointManagerViewService().snapshot(records,(WaypointManagerQuery)a[0]);
                case "revision":return 1L;
                case "setEnabled":case "toggleNavigator":acted[0]=(UUID)a[0];return empty(m);
                case "reportFailure":throw new AssertionError(String.valueOf(a[0]),(Throwable)a[1]);
                default:return empty(m);
            }
        });
        SurroundingsController surroundings=proxy(SurroundingsController.class,(p,m,a)->{
            if(m.getName().equals("snapshot"))return nearby.snapshot((SurroundingsQuery)a[0],400,400);
            if(m.getName().equals("revision"))return nearby.revision();
            if(m.getName().equals("setWaypoint")){nearby.setWaypoint((SurroundingKey)a[0],(Boolean)a[1]);return null;}
            if(m.getName().equals("reportFailure"))throw new AssertionError(String.valueOf(a[0]),(Throwable)a[1]);
            return empty(m);
        });
        for(String language:Messages.CODES) {
            Messages.select(language);
            WaypointerLayoutProbe.preparePreview("standard-kit","standard-kit",12);
            WaypointManagerWindow waypoints=new WaypointManagerWindow(manager);
            waypoints.setSize(1780,480);waypoints.setPosition(10,10);waypoints.gameTick();
            WaypointerVirtualTable waypointTable=field(waypoints,"table");
            check(((List<?>)field(waypoints,"filteredIds")).size()==2500,"All waypoint IDs retained");
            exercise(waypoints,waypointTable,32,output,"waypoints-2500-"+language);
            Map<WButton,?> rowActions=field(waypoints,"rowActions");
            for(Map.Entry<WButton,?> entry:rowActions.entrySet())if(field(entry.getValue(),"kind").toString().equals("TOGGLE")) {
                UUID expected=field(entry.getValue(),"id");waypoints.buttonClicked(entry.getKey());check(expected.equals(acted[0]),"Visible waypoint action uses the current UUID");break;
            }
            for(boolean animals:new boolean[]{true,false}) {
                TrackedTargetsPanel targets=new TrackedTargetsPanel(tracking,manager,animals);
                targets.setSize(targets.minimumContentWidth(),480);targets.setPosition(10,10);targets.gameTick();
                WaypointerVirtualTable targetTable=field(targets,"rows");
                check(((List<?>)field(targets,"displayOrder")).size()==2500,"Full managed catalogue order retained");
                exercise(targets,targetTable,28,output,(animals?"animals":"vehicles")+"-2500-"+language);
                WurmArrayPanel<?> row=(WurmArrayPanel<?>)targetTable.dataRows().get(0);
                WButton track=((WaypointerTableActionCell)row.components.get(0)).button;
                String caption=null;for(FlexComponent cell:row.components)if(cell instanceof WaypointerTableLabel){caption=field(cell,"caption");break;}
                targets.buttonClicked(track);
                WaypointRecord found=null;for(WaypointRecord record:records)if(record.getId().equals(acted[0]))found=record;
                check(found!=null&&caption.equals(found.getName()),"Managed action follows visible target after scrolling");
            }
            SurroundingsMonitoringWindow monitoring=new SurroundingsMonitoringWindow(surroundings,Collections.singletonList(SurroundingsQuery.builder().kind(SurroundingKind.ITEM).build()));
            monitoring.setSize(900,400);monitoring.setPosition(10,10);monitoring.gameTick();
            exercise(monitoring,field(monitoring,"table"),28,output,"monitoring-2500-"+language);
            DeedSearchWindow search=new DeedSearchWindow(deeds);search.setSize(900,400);search.setPosition(10,10);search.gameTick();
            WaypointerVirtualTable deedTable=field(search,"table");
            exercise(search,deedTable,32,output,"deeds-2500-"+language);
            check((Integer)field(search,"filteredCount")==2500,"All deeds participate in filtering");
            WurmInputField input=field(search,"searchInput");input.setTextMoveToEnd("Deed 2499");search.handleInputChanged(input,input.getText());
            check((Integer)field(search,"filteredCount")==1&&((Deed)field(search,"onlyFiltered")).getName().equals("Deed 2499"),"Search reaches an initially offscreen deed");
            check(deedTable.height==32,"Filtered deed list updates its full extent");
            WaypointClusterPickerWindow picker=new WaypointClusterPickerWindow(clusterTargets,400f,400f);
            picker.setInitialSize(picker.preferredWidth(),picker.preferredHeight(),false);picker.setPosition(10,10);picker.gameTick();
            exercise(picker,field(picker,"rows"),32,output,"cluster-2500-"+language);
            check(((Map<?,?>)field(picker,"choices")).size()<20,"Cluster choices only retain visible button bindings");
            System.out.println("LARGE_LISTS_OK: "+language+", 2500 waypoints, animals, vehicles, monitoring detections, deeds and cluster choices");
        }
    }

    private static void exercise(WurmComponent panel,WaypointerVirtualTable table,int stride,String output,String name)throws Exception {
        WurmScrollPanel scroll=field(table,"scroll");
        for(int offset:new int[]{0,stride*1200+7,Integer.MAX_VALUE,0}) {
            if(scroll instanceof ChamomiloUiV1ScrollPanel)((ChamomiloUiV1ScrollPanel)scroll).scrollTo(0,offset);
            else ((SurroundingsScrollPanel)scroll).restoreOffset(offset);
            panel.gameTick();
            WurmComponent viewport=(WurmComponent)(Object)scroll.offs;
            check(table.height==2500*stride,"Full scroll extent retained: "+name);
            check(table.dataRows().size()<=(viewport.height+stride-1)/stride+3,"Native widget count is bounded by viewport: "+name);
            boolean reachable=false;
            for(FlexComponent row:table.dataRows())if(row.y>=viewport.y&&row.y+row.height<=viewport.y+viewport.height){
                check(panel.getComponentAt(row.x+row.width/2,row.y+row.height/2)!=null,"Native hit testing reaches visible row: "+name);reachable=true;break;
            }
            check(reachable,"No blank viewport at any scroll offset: "+name+", offset="+scroll.yo);
        }
        if(!(panel instanceof DeedSearchWindow))WaypointerLayoutProbe.savePreview(panel,Paths.get(output,name+".png").toString());
    }

    @SuppressWarnings("unchecked") private static <T>T proxy(Class<T> type,InvocationHandler handler){return(T)Proxy.newProxyInstance(type.getClassLoader(),new Class[]{type},handler);}
    private static Object empty(Method method){Class<?> type=method.getReturnType();if(type==boolean.class)return false;if(type==int.class)return 0;if(type==long.class)return 0L;return null;}
    @SuppressWarnings("unchecked") private static <T>T field(Object owner,String name)throws Exception{Field field=owner.getClass().getDeclaredField(name);field.setAccessible(true);return(T)field.get(owner);}
    private static void check(boolean valid,String message){if(!valid)throw new AssertionError(message);}
}
