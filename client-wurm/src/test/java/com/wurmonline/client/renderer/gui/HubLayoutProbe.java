package com.wurmonline.client.renderer.gui;

import org.waypoints.next.ui.*;
import org.waypoints.next.i18n.Messages;
import org.waypoints.next.model.*;
import org.waypoints.next.service.*;
import org.waypoints.next.surroundings.*;
import org.waypoints.next.tracking.*;
import java.time.Instant;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;
import org.chamomilo.wurm.ui.v1.*;

/** Actual native layout and selector actions with offline controller fixtures. */
public final class HubLayoutProbe {
    private static final List<WaypointRecord> records=new ArrayList<WaypointRecord>();
    private static final SurroundingsCatalog surroundings=new SurroundingsCatalog();
    private static final Properties settings=new Properties();
    private static final ServerIdentity server=ServerIdentity.of(new ServerEndpoint("127.0.0.1",3724,27016),"Fixture","Fixture",ServerIdentity.Resolution.RESOLVED);
    private static int saves,clearPreviews,managedRefreshes,animalRefreshes;
    private static long trackingRevision;
    public static void main(String[] args)throws Exception{
        Files.createDirectories(Paths.get(args[1]));Instant now=Instant.now();TrackingCatalog tracking=new TrackingCatalog();tracking.bind(server,"Alice",now);
        WaypointRecord mare=tracking.live(ManagedKind.ANIMAL,"42","Mare Alpha","Manage horse",new WaypointCoordinate(110,90,2d,WaypointLayer.SURFACE),now);tracking.enabled(mare.getId(),true,now);tracking.vanished(ManagedKind.ANIMAL,"42",now);
        tracking.candidate(ManagedKind.ANIMAL,"43","Horse Beta","Manage horse",now);
        tracking.live(ManagedKind.VEHICLE,"44","Cart Gamma","Manage small cart",new WaypointCoordinate(200,100,2d,WaypointLayer.SURFACE),now);
        tracking.bearing(ManagedKind.ANIMAL,"43",AnimalBearing.parse("The Horse Beta is some distance away in front of you.","Horse Beta",100,100,0),now);
        records.addAll(tracking.all());
        for(SurroundingKind kind:SurroundingKind.values())for(int i=0;i<20;i++)surroundings.upsert(SurroundingEntry.builder().kind(kind).wurmId(100+i).name(i%2==0?"young horse":"old wolf").category(kind.name()).material("iron").position(400+i,400,2).build());
        WaypointManagerController controller=proxy(WaypointManagerController.class,(p,m,a)->{
            switch(m.getName()){
                case "context":return new WaypointManagerContext("Alice",server,100,100,2,WaypointLayer.SURFACE);
                case "snapshot":return new WaypointManagerViewService().snapshot(tracking.waypoints(),(WaypointManagerQuery)a[0]);
                case "tracking":return proxy(TrackingController.class,(p2,m2,a2)->{
                    if(m2.getName().equals("refresh")){if((Boolean)a2[0])animalRefreshes++;else managedRefreshes++;return null;}
                    if(m2.getName().equals("revision"))return trackingRevision;
                    if(m2.getName().equals("records")){List<WaypointRecord> list=new ArrayList<WaypointRecord>();for(WaypointRecord r:records)if((r.getSourceType()==WaypointSourceType.MANAGED_ANIMAL)==(Boolean)a2[0])list.add(r);return list;}
                    if(m2.getName().equals("canNavigate")){for(WaypointRecord r:records)if(r.getId().equals(a2[0]))return r.getSourceType()==WaypointSourceType.MANAGED_ANIMAL||r.isEnabled()&&r.getCoordinate()!=null;return false;}
                    if(m2.getName().equals("status"))return "Manage list received.";
                    if(m2.getName().equals("detail")){for(WaypointRecord r:records)if(r.getId().equals(a2[0]))return r.getDescription();return "";}
                    return defaultValue(m2);
                });
                case "surroundings":return proxy(SurroundingsController.class,(p2,m2,a2)->{if(m2.getName().equals("snapshot"))return surroundings.snapshot((SurroundingsQuery)a2[0],400,400);if(m2.getName().equals("setWaypoint")){surroundings.setWaypoint((SurroundingKey)a2[0],(Boolean)a2[1]);return null;}if(m2.getName().equals("revision"))return surroundings.revision();return defaultValue(m2);});
                case "settings":return proxy(SettingsController.class,(p2,m2,a2)->{if(m2.getName().equals("values")){Properties copy=new Properties();copy.putAll(settings);return copy;}if(m2.getName().equals("status"))return "";if(m2.getName().equals("save")){settings.putAll((Properties)a2[0]);saves++;}return null;});
                case "clearLivePreview":clearPreviews++;return null;
                case "reportFailure":throw new AssertionError(String.valueOf(a[0]),(Throwable)a[1]);
                default:return defaultValue(m);
            }
        });
        if(Boolean.getBoolean("waypointer.probe.trackingOnly")){
            Messages.select("en");WaypointerLayoutProbe.preparePreview(args[0],args[2],12);verifyTrackedScroll(controller);
            System.out.println("TRACKED_TABLE_SCROLL_OK: SDK scroll, background order and visible UUID retention");return;
        }
        for(String language:Messages.CODES){Messages.select(language);settings.setProperty("language",language);WaypointerLayoutProbe.preparePreview(args[0],args[2],12);
            WaypointerHubWindow hub=new WaypointerHubWindow(controller);hub.setSize(1120,480);hub.setPosition(10,10);hub.gameTick();
            @SuppressWarnings("unchecked") Map<WButton,WaypointerSection> selectors=(Map<WButton,WaypointerSection>)field(hub,"selectors");check(selectors.size()==7,"Seven selectors");
            WaypointerLayoutProbe.savePreview(hub,Paths.get(args[1],"initial.png").toString());
            WaypointerLayoutProbe.verifyStableOpacity(hub);
            for(Map.Entry<WButton,WaypointerSection> selector:selectors.entrySet()){
                WButton button=selector.getKey();check(button instanceof ChamomiloUiV1Button,"Approved skin");check(((ChamomiloUiV1Button)button).density()==UiDensity.HIGH,"Navigation uses compact captions");check(!((ChamomiloUiV1Button)button).captionShortened(),"Navigation preserves full caption");check(button.height==28,"Selector text fits: "+language+" "+button.getLabel()+" width="+button.width+" text="+button.text.getWidth(button.getLabel()));if(button.isEnabled())hub.buttonClicked(button);hub.gameTick();
                check(button.y>=hub.y&&button.y+button.height<=hub.y+hub.height,"Selector inside window");
                for(WurmComponent c=button;c!=null;c=c.parent)check(c.width>0&&c.height>0,"Selector has visible parents");
                check(hub.getComponentAt(button.x+button.width/2,button.y+button.height/2)==button,"Selector reachable by native hit testing");
                check(field(hub,"section")==selector.getValue(),"Selector routes to its section");
                WaypointerContentPanel panel=panels(hub).get(selector.getValue());check(!((Object)panel instanceof WWindow),"Feature is a panel");
                hub.setSize(1,480);hub.gameTick();
                check(hub.width==hub.minimumWindowWidth(),"Minimum width is enforced");
                try { verifyScrollWidths(panel); }
                catch (AssertionError failure) {
                    String geometry="";
                    if(panel instanceof WaypointManagerWindow){Method minimum=WaypointManagerWindow.class.getDeclaredMethod("minimumTableColumns");minimum.setAccessible(true);geometry=Arrays.toString((int[])minimum.invoke(panel));}
                    throw new AssertionError(language+" "+selector.getValue()+" hub="+hub.width+"x"+hub.height+" columns="+geometry,failure);
                }
                if(panel instanceof SurroundingsWindow)verifySurroundings((SurroundingsWindow)panel,selector.getValue());
                if(panel instanceof SurroundingsWindow){
                    SurroundingsMonitoringWindow monitor=new SurroundingsMonitoringWindow(((SurroundingsWindow)panel).controller(),Collections.singletonList(SurroundingsQuery.builder().kind(selector.getValue()==WaypointerSection.MOBS_AROUND?SurroundingKind.ANIMAL:selector.getValue()==WaypointerSection.CONTAINERS_AROUND?SurroundingKind.CONTAINER:SurroundingKind.ITEM).build()));
                    monitor.setSize(1,300);monitor.setPosition(10,10);monitor.gameTick();verifyScrollWidths(monitor.getComponent());
                    verifyMonitorFooter(monitor);
                    WaypointerLayoutProbe.savePreview(monitor,Paths.get(args[1],"monitor-"+language+"-"+selector.getValue()+".png").toString());
                    WaypointerLayoutProbe.verifyStableOpacity(monitor);
                    SurroundingsScrollPanel monitorScroll=(SurroundingsScrollPanel)field(monitor,"scrollPanel");
                    int headingY=((WurmArrayPanel<?>)field(monitor,"tableHeader")).y;
                    for(int offset:new int[]{7,96,103,Integer.MAX_VALUE,32,0}){
                        monitorScroll.restoreOffset(offset);verifyScrolledActions(monitorScroll.content);
                        TableHeaderProbe.verifyFixed((WurmArrayPanel<?>)field(monitor,"tableHeader"),monitorScroll,headingY);
                        verifyWholeMonitorRows(monitor);
                    }
                    verifyMonitorMark(monitor,Paths.get(args[1],"monitor-"+language+"-"+selector.getValue()+"-marked.png").toString());
                    if(selector.getValue()==WaypointerSection.MOBS_AROUND){
                        monitorScroll.restoreOffset(103);
                        WaypointerLayoutProbe.savePreview(monitor,Paths.get(args[1],"monitor-"+language+"-scrolled.png").toString());
                        monitorScroll.restoreOffset(0);
                    }
                }
                verifyInputRouting(hub,button,panel);
                for(WaypointerContentPanel other:panels(hub).values())if(other!=panel)check(other.parent==null,"Inactive panel detached");
                WaypointerLayoutProbe.savePreview(hub,Paths.get(args[1],"waypointer-"+language+"-"+selector.getValue()+".png").toString());
                if(selector.getValue()==WaypointerSection.MOBS_AROUND){
                    SurroundingsScrollPanel aroundScroll=(SurroundingsScrollPanel)field(panel,"scrollPanel");
                    aroundScroll.restoreOffset(103);verifyScrolledActions(aroundScroll.content);
                    WaypointerLayoutProbe.savePreview(hub,Paths.get(args[1],"waypointer-"+language+"-scrolled.png").toString());
                    aroundScroll.restoreOffset(0);
                }
                for(WButton tab:selectors.keySet())verifyPaintedCaption(tab,tab.getLabel(),tab.text,0,1);
                if(panel instanceof TrackedTargetsPanel)verifyTrackedTargets((TrackedTargetsPanel)panel);
                if(panel instanceof SurroundingsWindow){
                    WButton monitor=(WButton)field(panel,"monitoringButton");
                    verifyMonitorCaption(monitor);
                    if(selector.getValue()==WaypointerSection.MOBS_AROUND){
                        monitor.hovered=true;
                        WaypointerLayoutProbe.savePreview(hub,Paths.get(args[1],"waypointer-"+language+"-monitor-hover.png").toString());
                        verifyMonitorCaption(monitor);
                        monitor.hovered=false;
                    }
                }
                check(panel.y>=button.y+button.height,"Navigation is above the content");
            }
            hub.select(WaypointerSection.ALL_WAYPOINTS);WaypointManagerWindow manager=(WaypointManagerWindow)panels(hub).get(WaypointerSection.ALL_WAYPOINTS);
            check(!manager.hasInputField()&&manager.getInputField()==null,"Waypoint list has no text input");
            Map<WButton,?> rowActions=(Map<WButton,?>)field(manager,"rowActions");
            boolean canRemove=false;
            for(Map.Entry<WButton,?> entry:rowActions.entrySet()){
                check(mare.getId().equals(field(entry.getValue(),"id")),"Unselected managed targets stay outside ALL WAYPOINTS");
                if(field(entry.getValue(),"kind").toString().equals("REMOVE_TRACKED")){
                    canRemove=true;check(entry.getKey().isEnabled(),"Tracked waypoint has an enabled Delete button");
                    check(entry.getKey().width>=WaypointerUi.captionWidth(entry.getKey()),"Delete label fits the selected density: "+language);
                }
            }
            check(canRemove,"Explicitly added targets can be removed from ALL WAYPOINTS");
            WurmDropDown type=(WurmDropDown)field(manager,"typeFilter");type.setValue(1);
            hub.select(WaypointerSection.SETTINGS);hub.select(WaypointerSection.ALL_WAYPOINTS);check(panels(hub).get(WaypointerSection.ALL_WAYPOINTS)==manager,"Switch retains panel");check(type.getValue()==1,"Structured filters survive section switch");
            for(WaypointerSection from:WaypointerSection.values()){
                hub.select(from);hub.gameTick();
                for(Map.Entry<WButton,WaypointerSection> selector:selectors.entrySet()){
                    hub.select(from);
                    WButton button=selector.getKey();
                    verifyInputRouting(hub,button,panels(hub).get(from));
                    int bx=button.x+button.width/2,by=button.y+button.height/2;
                    button.leftPressed(bx,by,0);button.leftReleased(bx,by);hub.gameTick();
                    check(field(hub,"section")==selector.getValue(),"Native click switches "+from+" to "+selector.getValue());
                    verifyInputRouting(hub,button,panels(hub).get(selector.getValue()));
                }
            }
            hub.select(WaypointerSection.SETTINGS);WaypointerSettingsPanel panel=(WaypointerSettingsPanel)panels(hub).get(WaypointerSection.SETTINGS);
            for(SettingSpec spec:((Map<SettingSpec,FlexComponent>)field(panel,"editors")).keySet())check(!spec.key.equals("language"),"Only the updater offers language selection");
            panel.buttonClicked((WButton)field(panel,"save"));check(saves>0,"Settings save action");
            check(hub instanceof ChamomiloUiV1Window,"Standard window");
            verifyAuxiliaryUi(hub,language,args[1]);
            FilterListProbe.verify(hub,args[1],language);
            int normalWidth=hub.width,normalHeight=hub.height;
            hub.toggleCollapsed();check(hub.minimized&&hub.height<80,"Standard collapse works with minimum dimensions");
            hub.toggleCollapsed();check(!hub.minimized&&hub.height==normalHeight,"Standard restore keeps full height");
            hub.toggleMaximized();check(hub.width==WurmComponent.hud.getWidth()-20,"Standard maximize uses the current HUD");
            hub.toggleMaximized();check(hub.width==normalWidth&&hub.height==normalHeight,"Standard maximize restores dimensions");
        }
        for(int size:new int[]{10,18}){WaypointerLayoutProbe.preparePreview(args[0],args[2],size);Messages.select("ru");WaypointerHubWindow hub=new WaypointerHubWindow(controller);hub.setSize(1,480);hub.setPosition(10,10);for(WaypointerSection view:WaypointerSection.values()){hub.select(view);hub.gameTick();verifyScrollWidths(panels(hub).get(view));WaypointerLayoutProbe.savePreview(hub,Paths.get(args[1],"waypointer-ru-font-"+size+"-"+view+".png").toString());}}
        WaypointerLayoutProbe.preparePreview(args[0],args[2],12);Messages.select("en");verifyTrackedScroll(controller);WaypointManagerPanelProbe.verify(args[1]);check(clearPreviews>0,"Editor previews cleaned on leave");NativeUiRenderFixture.verified();Messages.select("en");System.out.println("WAYPOINTER_UI_OK: native SDK UV crops, HUD blend/depth state, seven panels, four languages, clicks/input routing, typography groups, fonts 10/12/18");
    }
    private static void verifyTrackedTargets(TrackedTargetsPanel panel)throws Exception {
        WButton refresh=(WButton)field(panel,"refreshButton");
        check(refresh.getLabel().equals(Messages.text("Refresh"))&&refresh.height==28,"Refresh keeps a complete caption and standard height");
        check(WaypointerButtonGroup.id(refresh.text).equals("tracked-targets.refresh"),"Refresh uses explicit caption metrics");
        verifyTrackedCaption(refresh);
        int pixels=-1,ascent=-1;
        Set<?> fixedActions=(Set<?>)field(panel,"fixedActions");
        for(WButton button:((Map<WButton,?>)field(panel,"actions")).keySet()) {
            if(fixedActions.contains(button))continue;
            check(!button.getLabel().equals(Messages.text("Direction")),"MY ANIMALS has no separate Direction action");
            check(WaypointerButtonGroup.id(button.text).equals("tracked-targets.actions"),"All catalogue row actions belong to one group");
            int current=WaypointerButtonGroup.fontPixels(button.text);
            if(pixels<0){pixels=current;ascent=button.text.getAscent();}
            check(button.height==24&&current==pixels&&WaypointerButtonGroup.fontPixels(button.textBold)==pixels
                    &&button.text.getAscent()==ascent&&button.textBold.getAscent()==ascent,"Catalogue row actions share font, baseline and height");
            verifyTableAction(button);
            verifyTrackedCaption(button);
        }
        check(pixels>=16,"Catalogue actions retain readable text");
        WurmArrayPanel<?> rows=(WurmArrayPanel<?>)field(panel,"rows"),headers=(WurmArrayPanel<?>)field(panel,"headers");
        boolean animals=(Boolean)field(panel,"animals");
        List<UUID> displayOrder=(List<UUID>)field(panel,"displayOrder");
        for(int i=0;i<displayOrder.size();i++){
            WaypointRecord record=null;for(WaypointRecord r:records)if(r.getId().equals(displayOrder.get(i)))record=r;
            check(record!=null&&(record.getSourceType()==WaypointSourceType.MANAGED_ANIMAL)==animals,"Catalogue rows belong to the selected section");
            if(animals&&record.getCoordinate()==null){
                WButton nav=findButton((FlexComponent)rows.components.get(i),Messages.text("Nav"));
                check(nav!=null&&nav.isEnabled(),"Unknown animal position keeps Nav available without tracking first");
            }
        }
        TableHeaderProbe.verify(headers);
        for(Object row:rows.components)check(row instanceof WurmArrayPanel&&((WurmArrayPanel<?>)row).height==28,"Targets use one compact table row without cards");
        WurmScrollPanel scroll=(WurmScrollPanel)field(panel,"scroll");
        check(headers.parent==scroll.parent&&headers.y+headers.height<=scroll.y,"Headers are fixed above the scroll area");
        List<Object> columns=new ArrayList<Object>(((Map<WButton,?>)field(panel,"sortActions")).values());
        for(Object column:columns)for(int click=0;click<3;click++){
            WButton header=null;for(Map.Entry<WButton,?> entry:((Map<WButton,?>)field(panel,"sortActions")).entrySet())if(entry.getValue()==column)header=entry.getKey();
            check(header instanceof WaypointerTableHeader&&header.height==28&&header.text==header.textBold,"All sortable headings use common Bold typography");
            header.leftPressed(header.x+8,header.y+8,0);header.leftReleased(header.x+8,header.y+8);
            Object active=field(panel,"sortColumn");check(click==2?active==null:active==column,"Native heading click cycles sort column");
            check(click!=1||!(Boolean)field(panel,"ascending"),"Second click sorts descending");
            for(Map.Entry<WButton,?> entry:((Map<WButton,?>)field(panel,"sortActions")).entrySet())check(((WaypointerTableHeader)entry.getKey()).order()==(click==2||entry.getValue()!=column?0:click==0?1:-1),"Chevron state matches actual sort");
            if(column.toString().equals("DISTANCE")&&click<2){List<UUID> order=(List<UUID>)field(panel,"displayOrder");boolean missing=false;Integer previous=null;for(UUID id:order){WaypointRecord found=null;for(WaypointRecord r:records)if(r.getId().equals(id))found=r;check(!missing||found.getCoordinate()==null,"Unknown distance remains last in both directions");missing|=found.getCoordinate()==null;if(!missing){int distance=WaypointDistance.metres(found.getCoordinate().getTileX(),found.getCoordinate().getTileY(),100,100);check(previous==null||(click==0?previous<=distance:previous>=distance),"Distance uses numeric ascending/descending order");previous=distance;}}}
        }
    }
    private static void verifyTrackedScroll(WaypointManagerController controller)throws Exception {
        List<WaypointRecord> saved=new ArrayList<WaypointRecord>(records);
        try{
            WaypointRecord template=records.get(0);records.clear();
            for(int i=0;i<80;i++)records.add(WaypointRecord.copyOf(template).id(UUID.randomUUID()).name(String.format(Locale.ROOT,"Target %02d",i)).build());
            WaypointerHubWindow hub=new WaypointerHubWindow(controller);hub.setSize(hub.minimumWindowWidth(),500);hub.select(WaypointerSection.MY_ANIMALS);
            TrackedTargetsPanel panel=(TrackedTargetsPanel)panels(hub).get(WaypointerSection.MY_ANIMALS);
            WurmScrollPanel scroll=(WurmScrollPanel)field(panel,"scroll");((ChamomiloUiV1ScrollPanel)scroll).scrollTo(0,28*10+7);
            WurmArrayPanel<?> headers=(WurmArrayPanel<?>)field(panel,"headers");int headingY=headers.y;
            TableHeaderProbe.verifyFixed(headers,scroll,headingY);
            check(scroll.yo==28*10+7,"Fixture scrolls inside a long catalogue through the SDK bar: offset="+scroll.yo+", bar="+((ChamomiloUiV1ScrollPanel)scroll).verticalBar().value()+", max="+((ChamomiloUiV1ScrollPanel)scroll).verticalBar().maximum()+", content="+scroll.content.height+", viewport="+((FlexComponent)field(scroll,"offs")).height+", rows="+((WurmArrayPanel<?>)field(panel,"rows")).components.size());
            int offset=scroll.yo;List<UUID> order=new ArrayList<UUID>((List<UUID>)field(panel,"displayOrder"));UUID visible=order.get(offset/28);
            verifyScrolledActions(scroll.content);
            records.remove(0);Collections.reverse(records);trackingRevision++;Field nextRefresh=TrackedTargetsPanel.class.getDeclaredField("nextRefresh");nextRefresh.setAccessible(true);nextRefresh.setLong(panel,0L);panel.gameTick();
            List<UUID> after=(List<UUID>)field(panel,"displayOrder");check(after.get(scroll.yo/28).equals(visible)&&scroll.yo%28==offset%28,"Background updates preserve visible UUID and intra-row scroll offset");
            verifyScrolledActions(scroll.content);
            List<UUID> expected=new ArrayList<UUID>(order);expected.remove(order.get(0));check(expected.equals(after),"Background catalogue order cannot reshuffle the table");
            TableHeaderProbe.verifyFixed(headers,scroll,headingY);
        }finally{records.clear();records.addAll(saved);trackingRevision++;}
    }
    private static void verifyTrackedCaption(WButton button)throws Exception {
        ChamomiloUiV1Button nativeButton=(ChamomiloUiV1Button)button;nativeButton.setAnimationsEnabled(false);
        int baseline=-1;
        for(boolean hover:new boolean[]{false,true}) {
            WaypointerLayoutProbe.clearPaintedLabel(button.getLabel());
            button.hovered=hover;button.render(null,1f);
            java.awt.Point point=WaypointerLayoutProbe.paintedLabel(button.getLabel());
            check(point!=null,"Catalogue caption is painted: "+button.getLabel());
            com.wurmonline.client.renderer.gui.text.TextFont font=hover&&button.isEnabled()?button.textBold:button.text;
            check(point.x==button.x+(button.width-font.getWidth(button.getLabel()))/2,"Catalogue caption stays centered");
            check(point.y-button.y==(button.height-button.text.getHeight())/2+button.text.getAscent(),"Catalogue caption stays inside its button");
            check(button.x>=button.parent.x&&button.y>=button.parent.y&&button.x+button.width<=button.parent.x+button.parent.width&&button.y+button.height<=button.parent.y+button.parent.height,"Catalogue actions fit their visible row");
            if(baseline>=0)check(point.y-button.y==baseline,"Catalogue hover preserves baseline");baseline=point.y-button.y;
            java.awt.Rectangle ink=UiTypography.ink(button.getLabel(),WaypointerButtonGroup.fontPixels(button.text),hover&&button.isEnabled(),UiDensity.HIGH);
            check(point.y+ink.y>=button.y+3&&point.y+ink.y+ink.height<=button.y+button.height-3,"Catalogue caption has standard clearance");
        }
        button.hovered=false;
    }
    private static void verifyAuxiliaryUi(WaypointerHubWindow hub,String language,String output)throws Exception{
        CustomMapMarkWindow mark=new CustomMapMarkWindow(123,456);
        mark.setInitialSize(390,155,false);mark.setPosition(10,10);mark.gameTick();
        WButton cancel=(WButton)field(mark,"cancelButton");
        WaypointerLayoutProbe.savePreview(mark,Paths.get(output,"custom-mark-"+language+".png").toString());
        check(cancel.y+cancel.height<=mark.y+mark.height-8,"Custom editor actions fit standard header: window="+mark.height+" bottom="+(cancel.y+cancel.height-mark.y));
        check(mark.getInputField().parent instanceof ChamomiloUiV1TextField,"Custom editor uses standard text wrapper");
        WaypointerLayoutProbe.savePreview(mark,Paths.get(output,"custom-mark-"+language+".png").toString());
        DeedSearchWindow search=new DeedSearchWindow(Collections.<org.waypoints.next.map.Deed>emptyList());
        search.setInitialSize(600,340,false);search.setPosition(10,10);search.gameTick();verifyScrollWidths(search.getComponent());
        check(((WButton)field(search,"clearMinus")).x+((WButton)field(search,"clearMinus")).width<=search.x+search.width,"Deed filters fit minimum width");
        WaypointerLayoutProbe.savePreview(search,Paths.get(output,"deed-search-"+language+".png").toString());
        final int[] confirmations={0,0};
        WaypointerConfirmWindow confirm=new WaypointerConfirmWindow(new ConfirmListener(){public void confirmed(){confirmations[0]++;}public void cancelled(){confirmations[1]++;}},"Remove fixture?","You can add it again with Track in its catalogue.");
        confirm.gameTick();
        WButton delete=findButton(confirm.getComponent(),Messages.text("Delete"));check(delete instanceof ChamomiloUiV1Button,"Standard confirmation action");
        delete.leftPressed(delete.x+5,delete.y+5,0);delete.leftReleased(delete.x+5,delete.y+5);check(confirmations[0]==1,"Native confirmation click routes once");
        confirm.closePressed();check(confirmations[1]==1,"Standard dialog close cancels");confirm.close();
        hub.select(WaypointerSection.MOBS_AROUND);hub.gameTick();
        SurroundingsWindow nearby=(SurroundingsWindow)panels(hub).get(WaypointerSection.MOBS_AROUND);
        nearby.buttonClicked((WButton)field(nearby,"modifierFilter"));
        WaypointerUiWindow filter=(WaypointerUiWindow)field(nearby,"filterWindow");check(filter!=null,"Standard filter window opens");filter.gameTick();verifyScrollWidths(filter.getComponent());
        WButton clear=findButton(filter.getComponent(),Messages.text("All (clear selection)"));check(clear instanceof ChamomiloUiV1Button,"Standard filter buttons");
        WButton option=findButton(filter.getComponent(),Messages.text("Normal"));check(option!=null,"Condition choice exists");
        WurmArrayPanel<?> choiceRow=(WurmArrayPanel<?>)option.parent;
        WaypointerFilterCheck checkbox=(WaypointerFilterCheck)choiceRow.components.get(0);
        check(!checkbox.checked()&&checkbox.width==28&&checkbox.height==28,"Separate square checkbox: "+checkbox.width+"x"+checkbox.height+", checked="+checkbox.checked());
        verifyFilterChoices(filter.getComponent());
        option.leftPressed(option.x+5,option.y+5,0);option.leftReleased(option.x+5,option.y+5);
        check(checkbox.checked()&&option.getLabel().equals(Messages.text("Normal")),"Caption click updates separate checkbox without changing text");
        verifySurroundingsGroups(nearby);
        checkbox.leftPressed(checkbox.x+16,checkbox.y+16,0);checkbox.leftReleased(checkbox.x+16,checkbox.y+16);
        check(!checkbox.checked(),"Square is independently clickable");
        option.leftPressed(option.x+5,option.y+5,0);option.leftReleased(option.x+5,option.y+5);
        clear.leftPressed(clear.x+5,clear.y+5,0);clear.leftReleased(clear.x+5,clear.y+5);check(!checkbox.checked(),"Clear filter resets selection");
        verifySurroundingsGroups(nearby);
        WaypointerLayoutProbe.savePreview(filter,Paths.get(output,"condition-filter-"+language+".png").toString());
        WaypointerLayoutProbe.verifyStableOpacity(filter);
        hub.select(WaypointerSection.SETTINGS);check(field(nearby,"filterWindow")==null,"Switching view disposes the filter window");
        org.waypoints.next.map.MiniMapState miniState=new org.waypoints.next.map.MiniMapState();
        MiniMapWindow mini=new MiniMapWindow(miniState);mini.setPosition(10,10);
        WaypointerLayoutProbe.savePreview(mini,Paths.get(output,"mini-map-"+language+".png").toString());
        verifyMiniMapCaptions(mini,"GROUND");
        MiniMapControlsLayout controls=(MiniMapControlsLayout)field(mini,"controls");
        int mx=controls.modeButtonLeft(mini.x,mini.width)+controls.modeButtonWidth/2;
        int my=MiniMapControlsLayout.buttonTop(mini.y,mini.height)+MiniMapControlsLayout.BUTTON_HEIGHT/2;
        mini.pick(null,mx,my);
        WaypointerLayoutProbe.savePreview(mini,Paths.get(output,"mini-map-hover-"+language+".png").toString());
        verifyMiniMapCaptions(mini,"GROUND");
        mini.leftPressed(mx,my,0);mini.leftReleased(mx,my);check(miniState.isCaveView(),"Compact mode button switches map layer");
        WaypointerLayoutProbe.savePreview(mini,Paths.get(output,"mini-map-cave-"+language+".png").toString());
        verifyMiniMapCaptions(mini,"CAVE");
        WurmInputField topo=(WurmInputField)field(mini,"topographicInput");
        topo.setText("99");mini.handleInputChanged(topo,"99");check(miniState.getTopographicIntervalMetres()==99,"Compact contour field retains input routing");
        check(topo.text.getWidth("99")+8<=topo.width,"Two-digit contour value fits the compact field");
        Messages.select("en");mini.gameTick();Messages.select(language);mini.gameTick();
        WaypointerLayoutProbe.savePreview(mini,Paths.get(output,"mini-map-language-change-"+language+".png").toString());
        verifyMiniMapCaptions(mini,"CAVE");
        verifyFullMapChrome(language,output);
    }
    private static void verifyFullMapChrome(String language,String output)throws Exception{
        WorldMap map=(WorldMap)WaypointerLayoutProbe.allocate(WorldMap.class);
        map.x=10;map.y=10;map.width=926;map.height=644;
        map.text=new WaypointerLayoutProbe.ProbeFont("normal");map.textBold=new WaypointerLayoutProbe.ProbeFont("bold");
        Class<?> bridge=ServerMapWindowBridge.class;
        Method frame=bridge.getDeclaredMethod("drawMainMapFrame",WorldMap.class,com.wurmonline.client.renderer.backend.Queue.class,int.class,int.class);
        Method plate=bridge.getDeclaredMethod("drawNameplate",WorldMap.class,com.wurmonline.client.renderer.backend.Queue.class,int.class,int.class,int.class,String.class);
        frame.setAccessible(true);plate.setAccessible(true);
        WaypointerLayoutProbe.saveFullMapChrome(map,frame,plate,Paths.get(output,"full-map-chrome-"+language+".png").toString());
    }
    private static void verifyMiniMapCaptions(MiniMapWindow mini,String mode)throws Exception{
        MiniMapControlsLayout controls=(MiniMapControlsLayout)field(mini,"controls");
        check(mini.width==mini.height&&mini.width>=controls.minimumSize(),"Localized mini map remains square and fits the full footer");
        check(mini.width==Math.max(MiniMapWindow.MAP_SIZE,controls.minimumSize()),"Mini map grows only to fit its localized footer: "+Messages.language());
        check(controls.topographicBlockLeft(mini.x,mini.width)>=mini.x+22,"Footer starts after the frame corner");
        check(controls.openButtonLeft(mini.x,mini.width)+controls.openButtonWidth<=mini.x+mini.width-22,"Footer ends before the frame corner");
        for(String source:new String[]{"FULL MAP",mode}){
            String caption=Messages.text(source);java.awt.Point point=WaypointerLayoutProbe.paintedLabel(caption);
            check(point!=null,"Full caption is painted without truncation: "+caption);
            boolean full=source.equals("FULL MAP");
            boolean bold=(Boolean)field(mini,full?"openMapHover":"modeHover");
            int left=full?controls.openButtonLeft(mini.x,mini.width):controls.modeButtonLeft(mini.x,mini.width);
            int width=full?controls.openButtonWidth:controls.modeButtonWidth;
            WaypointerButtonGroup group=(WaypointerButtonGroup)field(mini,"footerTypography");
            java.awt.Rectangle ink=UiTypography.ink(caption,group.fontPixels,bold,UiDensity.HIGH);
            check(point.x+ink.x>=left+2&&point.x+ink.x+ink.width<=left+width-2,"Caption fits button width: "+caption);
            check(point.y==MiniMapControlsLayout.buttonTop(mini.y,mini.height)+group.baseline,"Footer captions share one baseline");
            check(point.y+ink.y>=mini.y+mini.height-MiniMapControlsLayout.FOOTER_HEIGHT&&point.y+ink.y+ink.height<=mini.y+mini.height,"Caption stays inside the lower frame: "+caption);
        }
        FlexComponent input=(FlexComponent)field(mini,"topographicField");
        check(input.y>=mini.y+mini.height-MiniMapControlsLayout.FOOTER_HEIGHT&&input.y+input.height<=mini.y+mini.height,"Contour field stays inside the lower frame");
        WurmInputField field=(WurmInputField)field(mini,"topographicInput");
        WaypointerButtonGroup group=(WaypointerButtonGroup)field(mini,"footerTypography");
        java.awt.Point number=WaypointerLayoutProbe.paintedLabel(field.getText());
        java.awt.Rectangle digits=UiTypography.ink(field.getText(),group.fontPixels,false,UiDensity.HIGH);
        check(number!=null&&number.y==MiniMapControlsLayout.buttonTop(mini.y,mini.height)+group.baseline,"Topographic input shares the footer baseline");
        check(digits.height>=input.height/2,"Topographic digits occupy at least half the field height: font="+group.fontPixels+", ink="+digits+", field="+input.height+", language="+Messages.language());
        for(char digit='0';digit<='9';digit++)check(UiTypography.ink(String.valueOf(digit),group.fontPixels,false,UiDensity.HIGH).height>=input.height/2,"Every topographic digit has the requested minimum height");
        check(((WaypointerLayoutProbe.ProbeFont)field.text).awtFont().equals(UiTypography.font(group.fontPixels,false,UiDensity.HIGH)),"Editable topographic digits use the footer font");
        check(number.y+digits.y>=input.y+2&&number.y+digits.y+digits.height<=input.y+input.height-2,"Topographic digits clear both field rails");
    }
    private static void verifyMonitorFooter(SurroundingsMonitoringWindow monitor)throws Exception{
        WurmArrayPanel<?> table=(WurmArrayPanel<?>)field(monitor,"table");
        TableHeaderProbe.verify((WurmArrayPanel<?>)field(monitor,"tableHeader"));
        for(WButton button:((Map<WButton,?>)field(monitor,"rowActions")).keySet())verifyTableAction(button);
        WaypointerLabel count=(WaypointerLabel)field(monitor,"countLabel");
        WButton back=(WButton)field(monitor,"surroundingsButton"),refresh=(WButton)field(monitor,"refreshButton");
        check(back.getLabel().equals(Messages.text("Back to Waypointer")),"Monitoring offers Back to Waypointer");
        verifyTypographyGroup("monitoring.footer",back,refresh);
        check(((WaypointerLayoutProbe.ProbeFont)count.text).awtFont().equals(UiTypography.font(WaypointerButtonGroup.fontPixels(back.text),false,UiDensity.HIGH)),"Counter shares the footer font size and family");
        FlexComponent title=(FlexComponent)field(monitor,"titleHeader");
        check(((WaypointerLayoutProbe.ProbeFont)title.text).awtFont().equals(UiTypography.font(16,true,UiDensity.HIGH)),"Monitoring title uses branded Bold typography");
        check(count.width>=count.textWidth()&&count.x+count.width<=monitor.x+monitor.width-8,"Monitoring count fits the footer");
        for(String name:new String[]{"surroundingsButton","refreshButton"}){
            WButton button=(WButton)field(monitor,name);
            check(button.width>=WaypointerUi.captionWidth(button),"Monitoring action fits normal and bold captions");
            check(button.y==count.y,"Monitoring footer stays in one row");
        }
    }
    private static void verifyWholeMonitorRows(SurroundingsMonitoringWindow monitor)throws Exception {
        SurroundingsScrollPanel scroll=(SurroundingsScrollPanel)field(monitor,"scrollPanel");
        FlexComponent viewport=(FlexComponent)(Object)scroll.offs;
        WButton back=(WButton)field(monitor,"surroundingsButton");
        check(viewport.height%28==0&&scroll.yo%28==0,"Monitoring viewport and scrolling fit complete rows");
        check(back.y-viewport.y-viewport.height>=8,"Monitoring reserves space before the footer");
        for(FlexComponent row:((WurmArrayPanel<?>)scroll.content).components) {
            if(row.y<viewport.y+viewport.height&&row.y+row.height>viewport.y)
                check(row.y>=viewport.y&&row.y+row.height<=viewport.y+viewport.height,"Monitoring never shows part of a row");
        }
    }
    private static void verifyMonitorMark(SurroundingsMonitoringWindow monitor,String preview)throws Exception {
        WurmArrayPanel<?> first=(WurmArrayPanel<?>)((WurmArrayPanel<?>)field(monitor,"table")).components.get(0);
        WButton mark=((WaypointerTableActionCell)first.components.get(0)).button;
        SurroundingKey key=(SurroundingKey)field(((Map<WButton,?>)field(monitor,"rowActions")).get(mark),"key");
        monitor.buttonClicked(mark);
        verifyMonitorActiveState(monitor,key,true);
        monitor.refreshFromController();verifyMonitorActiveState(monitor,key,true);
        WaypointerLayoutProbe.savePreview(monitor,preview);
        for(Map.Entry<WButton,?> action:((Map<WButton,?>)field(monitor,"rowActions")).entrySet())
            if(key.equals(field(action.getValue(),"key"))){monitor.buttonClicked(action.getKey());break;}
        verifyMonitorActiveState(monitor,key,false);
        int savedHeight=monitor.height;
        for(int height:new int[]{301,317,340,savedHeight}) {
            monitor.setSize(monitor.width,height);monitor.gameTick();
            SurroundingsScrollPanel scroll=(SurroundingsScrollPanel)field(monitor,"scrollPanel");
            scroll.restoreOffset(Integer.MAX_VALUE);verifyWholeMonitorRows(monitor);
            int headingY=((WurmArrayPanel<?>)field(monitor,"tableHeader")).y;
            ChamomiloUiV1ScrollBar bar=(ChamomiloUiV1ScrollBar)field(scroll,"bar");
            bar.leftPressed(bar.x+bar.width/2,bar.y+bar.height/2,0);
            bar.mouseDragged(bar.x+bar.width/2,bar.y+bar.height/2-7);
            bar.leftReleased(bar.x+bar.width/2,bar.y+bar.height/2-7);
            verifyWholeMonitorRows(monitor);
            TableHeaderProbe.verifyFixed((WurmArrayPanel<?>)field(monitor,"tableHeader"),scroll,headingY);
        }
        ((SurroundingsScrollPanel)field(monitor,"scrollPanel")).restoreOffset(0);
    }
    private static void verifyMonitorActiveState(SurroundingsMonitoringWindow monitor,SurroundingKey key,boolean enabled)throws Exception {
        for(Map.Entry<WButton,?> action:((Map<WButton,?>)field(monitor,"rowActions")).entrySet())if(key.equals(field(action.getValue(),"key"))) {
            WButton button=action.getKey();
            check((Boolean)field(button.text,"active")==enabled&&(Boolean)field(button.textBold,"active")==enabled,"Monitoring Mark/Clear uses green active state in both font weights");
            check(button.getLabel().equals(Messages.text(enabled?"Clear":"Mark")),"Monitoring caption matches waypoint membership");
            return;
        }
        throw new AssertionError("Monitoring lost the marked object");
    }
    private static WButton findButton(FlexComponent component,String caption)throws Exception{
        if(component instanceof WurmScrollPanel)return findButton(((WurmScrollPanel)component).content,caption);
        if(component instanceof WButton&&((WButton)component).getLabel().equals(caption))return (WButton)component;
        List<FlexComponent> children=new ArrayList<FlexComponent>();
        if(component instanceof WurmArrayPanel)children.addAll(((WurmArrayPanel<?>)component).components);
        else if(component instanceof WurmBorderPanel){Field f=WurmBorderPanel.class.getDeclaredField("components");f.setAccessible(true);children.addAll(Arrays.asList((FlexComponent[])f.get(component)));}
        for(FlexComponent child:children)if(child!=null){WButton result=findButton(child,caption);if(result!=null)return result;}
        return null;
    }
    private static void verifyInputRouting(WaypointerHubWindow hub,WButton selector,WaypointerContentPanel panel){
        WurmInputField input=panel.getInputField();boolean hasInput=input!=null;
        check(panel.hasInputField()==hasInput,"Panel input availability matches its field");
        check(hub.hasInputField()==hasInput&&hub.getInputField()==input,"Hub resolves the selected panel's input");
        check(selector.hasInputField()==hasInput&&selector.getInputField()==input,"Selector input routes through the hub without recursion");
        if(panel instanceof SurroundingsWindow)check(input!=null,"Surroundings exposes its own search input");
    }
    private static void verifyScrollWidths(FlexComponent component)throws Exception{
        if(component instanceof WurmScrollPanel){
            WurmScrollPanel scroll=(WurmScrollPanel)component;
            FlexComponent viewport=(FlexComponent)(Object)scroll.offs;
            check(scroll.horizontalScrollBar==null,"No horizontal scrollbar: "+component.getClass().getSimpleName());
            check(scroll.content.width<=viewport.width,"Content fits viewport: "+component.getClass().getSimpleName()+" content="+scroll.content.width+" viewport="+viewport.width+" children="+scrollChildren(scroll.content));
        }
        if(component instanceof WurmArrayPanel)for(FlexComponent child:((WurmArrayPanel<?>)component).components)verifyScrollWidths(child);
        else if(component instanceof WurmBorderPanel){Field f=WurmBorderPanel.class.getDeclaredField("components");f.setAccessible(true);for(FlexComponent child:(FlexComponent[])f.get(component))if(child!=null)verifyScrollWidths(child);}
        else if(component instanceof WurmDecorator)verifyScrollWidths(((WurmDecorator)component).component);
    }
    private static String scrollChildren(FlexComponent content){
        StringBuilder result=new StringBuilder();
        if(content instanceof WurmArrayPanel)for(FlexComponent row:((WurmArrayPanel<?>)content).components){
            result.append(row.getClass().getSimpleName()).append('=').append(row.width).append('[');
            if(row instanceof WurmArrayPanel)for(FlexComponent cell:((WurmArrayPanel<?>)row).components)result.append(cell.width).append(',');
            result.append(']');
        }
        return result.toString();
    }
    private static void verifySurroundings(SurroundingsWindow panel,WaypointerSection view)throws Exception{
        Method activeQuery=SurroundingsWindow.class.getDeclaredMethod("query");activeQuery.setAccessible(true);
        check(((SurroundingsQuery)activeQuery.invoke(panel)).getShortName().isEmpty(),"No hidden Short name filter in any surroundings view");
        verifySurroundingsGroups(panel);
        WButton monitor=(WButton)field(panel,"monitoringButton");
        check(monitor.height==28&&monitor.width>monitor.height,"Monitor uses one compact action row");
        check(monitor.getLabel().equals(Messages.text("ADD TO MONITOR")),"Requested monitor caption");
        check(((ChamomiloUiV1Button)monitor).density()==UiDensity.HIGH,"Monitor has toolbar density");
        check(!((ChamomiloUiV1Button)monitor).captionShortened(),"Primary action preserves three full rows");
        WurmArrayPanel<?> pinnedHeader=(WurmArrayPanel<?>)field(panel,"tableHeader");
        check(monitor.x+monitor.width<=panel.x+panel.width&&monitor.y+monitor.height<=pinnedHeader.y,"Monitor stays in the summary above the table");
        WButton previous=null;for(String name:new String[]{"waypointFiltered","clearFiltered","clearAll","refreshButton"}){
            WButton button=(WButton)field(panel,name);check(button.x+button.width<=panel.x+panel.width,"Footer inside panel");
            check(((ChamomiloUiV1Button)button).density()==UiDensity.HIGH,"Crowded footer action has high density");
            if(previous!=null)check(button.y==previous.y&&button.x==previous.x+previous.width+8,"Footer buttons have one row and equal gaps");previous=button;
        }
        WurmArrayPanel<?> table=(WurmArrayPanel<?>)field(panel,"table");
        WurmArrayPanel<?> header=(WurmArrayPanel<?>)field(panel,"tableHeader");
        String[] labels=view==WaypointerSection.MOBS_AROUND?new String[]{"Mark","Name","Condition","Hostility","Unique","Deed","Distance"}
                :new String[]{"Mark","Name","Category","Short name","Material","Rarity","Deed","Distance"};
        check(header.components.size()==labels.length,"Section columns exclude unavailable Traits");
        for(int i=0;i<labels.length;i++)check(((WButton)header.components.get(i)).getLabel().equals(Messages.text(labels[i])),"Active header: "+labels[i]);
        for(int row=0;row<table.components.size();row++){
            WurmArrayPanel<?> values=(WurmArrayPanel<?>)table.components.get(row);
            verifyTableAction(((WaypointerTableActionCell)values.components.get(0)).button);
            for(int column=0;column<labels.length;column++){
                FlexComponent heading=header.components.get(column),value=values.components.get(column);
                check(heading.x==value.x&&heading.width==value.width,"Header/body boundaries match: "+labels[column]+", "+Messages.language());
            }
        }
        TableHeaderProbe.verify(header);
        for(String label:labels){
            for(int click=0;click<3;click++){
                WurmArrayPanel<?> current=(WurmArrayPanel<?>)field(panel,"tableHeader");
                WButton sort=null;for(FlexComponent component:current.components)if(((WButton)component).getLabel().startsWith(Messages.text(label)))sort=(WButton)component;
                panel.buttonClicked(sort);
                Method query=SurroundingsWindow.class.getDeclaredMethod("query");query.setAccessible(true);
                SurroundingsQuery state=(SurroundingsQuery)query.invoke(panel);
                check(click==2?state.getSort()==SurroundingsQuery.SortColumn.NONE:state.getSort()!=SurroundingsQuery.SortColumn.NONE&&state.isAscending()==(click==0),"Three-way sorting: "+label);
            }
        }
        SurroundingsScrollPanel scroll=(SurroundingsScrollPanel)field(panel,"scrollPanel");
        int headingY=header.y,monitorY=monitor.y;
        int old=scroll.yo;scroll.scrollWheel(1);check(scroll.yo>old,"Vertical wheel scrolls");
        verifyScrolledActions(scroll.content);
        int offset=scroll.yo;panel.refreshFromController();check(scroll.yo==offset,"Refresh preserves scroll");
        for(int target:new int[]{7,96,103,Integer.MAX_VALUE,32,offset}){
            scroll.restoreOffset(target);verifyScrolledActions(scroll.content);
            TableHeaderProbe.verifyFixed((WurmArrayPanel<?>)field(panel,"tableHeader"),scroll,headingY);
            check(monitor.y==monitorY,"Primary monitor action stays fixed while rows scroll");
        }
        scroll.restoreOffset(0);
        ChamomiloUiV1ScrollBar bar=(ChamomiloUiV1ScrollBar)field(scroll,"bar");
        bar.leftPressed(bar.x+bar.width/2,bar.y+bar.height-4,0);bar.leftReleased(bar.x+bar.width/2,bar.y+bar.height-4);
        check(scroll.yo>0,"Kit scrollbar arrow scrolls the live table");
        scroll.restoreOffset(0);
    }
    private static void verifySurroundingsGroups(SurroundingsWindow panel)throws Exception{
        WurmArrayPanel<?> filterRow=(WurmArrayPanel<?>)field(panel,"fieldFilters");List<WButton> filters=new ArrayList<WButton>();
        for(FlexComponent component:filterRow.components)if(component instanceof WButton)filters.add((WButton)component);
        verifyTypographyGroup("surroundings.filters",filters.toArray(new WButton[0]));
        verifyTypographyGroup("surroundings.footer",(WButton)field(panel,"waypointFiltered"),(WButton)field(panel,"clearFiltered"),(WButton)field(panel,"clearAll"),(WButton)field(panel,"refreshButton"));
    }
    static void verifyTableAction(WButton button) {
        check(button.parent instanceof WaypointerTableActionCell,"Action has a padded table cell");
        WaypointerTableActionCell cell=(WaypointerTableActionCell)button.parent;
        check(cell.height==28&&button.height==24&&button.y==cell.y+2
                &&button.y+button.height==cell.y+cell.height-2,"Table actions leave a 4 px gap between rows: "+button.getLabel()+" cell="+cell.y+"/"+cell.height+" button="+button.y+"/"+button.height);
        int center=button.x+button.width/2;
        check(cell.getComponentAt(center,button.y+button.height/2)==button,"Native action hit testing survives padding");
        check(cell.getComponentAt(center,cell.y)!=button&&cell.getComponentAt(center,cell.y+27)!=button,
                "Padding cannot activate a neighbouring action");
    }
    static void verifyScrolledActions(FlexComponent component) {
        if(component instanceof WaypointerTableActionCell) {
            verifyTableAction(((WaypointerTableActionCell)component).button);
        } else if(component instanceof WurmArrayPanel) {
            for(FlexComponent child:((WurmArrayPanel<?>)component).components)verifyScrolledActions(child);
        }
    }
    private static void verifyTypographyGroup(String id,WButton...buttons)throws Exception{
        check(buttons.length>1,"Complete peer group: "+id);int pixels=WaypointerButtonGroup.fontPixels(buttons[0].text),baseline=-1;
        for(WButton button:buttons){
            check(WaypointerButtonGroup.id(button.text).equals(id)&&WaypointerButtonGroup.id(button.textBold).equals(id),"Recorded group membership: "+id);
            check(WaypointerButtonGroup.fontPixels(button.text)==pixels&&WaypointerButtonGroup.fontPixels(button.textBold)==pixels,"One shared size in normal/hover: "+id);
            check(button.height==28,"Standard group height: "+id+" "+button.getLabel()+" actual="+button.height+" font="+button.text.getHeight());
            ChamomiloUiV1Button nativeButton=(ChamomiloUiV1Button)button;nativeButton.setAnimationsEnabled(false);
            String[] rows=(String[])field(button,"captionRows");String caption=rows==null?button.getLabel():rows[0];
            for(boolean hover:new boolean[]{false,true}){
                button.hovered=hover;button.render(null,1f);java.awt.Point painted=WaypointerLayoutProbe.paintedLabel(caption);
                check(painted!=null,"Actual group caption is painted: "+caption);
                if(baseline<0)baseline=painted.y-button.y;
                check(painted.y-button.y==baseline,"One actual baseline across all peers and weights: "+id);
            }
            button.hovered=false;
        }
    }
    private static void verifyMonitorCaption(WButton monitor)throws Exception{
        com.wurmonline.client.renderer.gui.text.TextFont font=monitor.hovered?monitor.textBold:monitor.text;
        String[] rows={monitor.getLabel()};
        for(int i=0;i<rows.length;i++)verifyPaintedCaption(monitor,rows[i],font,i,rows.length);
    }
    private static void verifyPaintedCaption(WButton button,String caption,com.wurmonline.client.renderer.gui.text.TextFont font,int row,int rows)throws Exception{
        caption = UiTypography.caption(caption,((ChamomiloUiV1Button)button).density());
        java.awt.Point point=WaypointerLayoutProbe.paintedLabel(caption);
        check(point!=null,"Enlarged caption is painted: "+caption);
        ChamomiloUiV1Button nativeButton=(ChamomiloUiV1Button)button;
        UiButtonLayout layout=(UiButtonLayout)field(button,"captionLayout");
        boolean bold=button.isEnabled()&&button.hovered;
        if(layout==null){
            int pixels=WaypointerButtonGroup.fontPixels(font);
            check(pixels>=16&&WaypointerButtonGroup.fontPixels(button.textBold)==pixels,"Shared regular/bold font size");
            check((WaypointerButtonGroup.id(font).equals("hub.navigation")||WaypointerButtonGroup.id(font).equals("surroundings.monitor")),"Recorded typography group");
            check(button.text.getAscent()==button.textBold.getAscent(),"Selector baseline is shared across weights");
            check(point.x==button.x+(button.width-font.getWidth(caption))/2,"Shared caption is horizontally centered: "+caption);
            check(point.y==button.y+(button.height-rows*font.getHeight())/2+row*font.getHeight()+font.getAscent(),"Shared caption baseline: "+caption);
            java.awt.Rectangle ink=UiTypography.ink(caption,pixels,bold,nativeButton.density());
            check(point.x+ink.x>=button.x+12&&point.x+ink.x+ink.width<=button.x+button.width-12,"Caption side spacing: "+caption);
            check(point.y+ink.y>=button.y+3&&point.y+ink.y+ink.height<=button.y+button.height-3,"Caption vertical spacing: "+caption);
            return;
        }
        java.awt.Rectangle ink=UiTypography.ink(caption,layout.fontPixels,bold,((ChamomiloUiV1Button)button).density());
        check(point.x==button.x+layout.textX(row,bold),"Caption centered by visible ink: "+caption);
        check(point.y==button.y+layout.baseline(row),"Caption block centered by visible ink: "+caption);
        check(point.x+ink.x>=button.x+layout.insetX&&point.x+ink.x+ink.width<=button.x+button.width-layout.insetX,"Regular/bold ink clears side rails: "+caption);
        check(point.y+ink.y>=button.y+layout.insetY&&point.y+ink.y+ink.height<=button.y+button.height-layout.insetY,"Regular/bold ink clears top/bottom rails: "+caption);
        check(nativeButton.captionFontPixels()>0,"Caption uses bundled automatic fonts");
    }
    private static void verifyFilterChoices(FlexComponent component)throws Exception{
        check(!(component instanceof WurmScrollPanel),"Complete filter choice list has no internal scrolling");
        if(component instanceof ChamomiloUiV1Button){
            WButton button=(WButton)component;
            check(button.height==28&&WaypointerButtonGroup.id(button.text).equals("surroundings.filter-choices"),"Every choice/action has standard height and group");
            check(WaypointerButtonGroup.fontPixels(button.text)==16&&button.text.getAscent()==button.textBold.getAscent(),"Filter choices have shared size and baseline");
        }
        if(component instanceof WurmBorderPanel){for(FlexComponent child:(FlexComponent[])field(component,"components"))if(child!=null)verifyFilterChoices(child);}
        else if(component instanceof WurmArrayPanel){for(FlexComponent child:((WurmArrayPanel<?>)component).components)verifyFilterChoices(child);}
    }
    private static Map<WaypointerSection,WaypointerContentPanel> panels(Object hub)throws Exception{return (Map<WaypointerSection,WaypointerContentPanel>)field(hub,"panels");}
    private static Object field(Object object,String name)throws Exception{
        for(Class<?> type=object.getClass();type!=null;type=type.getSuperclass()){
            try{Field field=type.getDeclaredField(name);field.setAccessible(true);return field.get(object);}catch(NoSuchFieldException ignored){ }
        }
        throw new NoSuchFieldException(name);
    }
    private static <T>T proxy(Class<T> type,InvocationHandler handler){return type.cast(Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},handler));}
    private static Object defaultValue(Method method){if(method.getReturnType()==boolean.class)return false;if(method.getReturnType()==long.class)return 0L;if(method.getReturnType()==int.class)return 0;return null;}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
