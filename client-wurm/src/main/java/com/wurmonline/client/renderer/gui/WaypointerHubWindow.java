package com.wurmonline.client.renderer.gui;

import org.waypoints.next.i18n.Messages;
import org.waypoints.next.ui.*;
import org.waypoints.next.surroundings.SurroundingKind;
import org.chamomilo.wurm.ui.v1.UiDensity;
import java.util.*;

/** The only main window. Feature panels own their data and state; the hub owns chrome and selection. */
final class WaypointerHubWindow extends WaypointerUiWindow implements ButtonListener {
    private final WaypointManagerController controller;
    private final Map<WButton,WaypointerSection> selectors=new LinkedHashMap<WButton,WaypointerSection>();
    private final Map<WaypointerSection,WaypointerContentPanel> panels=new EnumMap<WaypointerSection,WaypointerContentPanel>(WaypointerSection.class);
    private WaypointerSection section=WaypointerSection.ALL_WAYPOINTS;
    private WurmBorderPanel root;
    private long languageRevision;
    private int navigationWidth;
    WaypointerHubWindow(WaypointManagerController controller){super("wurm-waypointer.manager",true);this.controller=controller;setTitle(org.waypoints.next.i18n.Messages.text("Wurm Waypointer"));setHeaderHeight(29);setContentPadding(4);rebuild();}
    private void rebuild(){
        WaypointerContentPanel old=panels.get(section);if(old!=null)old.leaving();panels.clear();selectors.clear();languageRevision=Messages.revision();
        root=new WurmBorderPanel("waypointer.hub");
        WurmArrayPanel<FlexComponent> tabs=new WurmArrayPanel<FlexComponent>("hub.selectors",WurmArrayPanel.DIR_HORIZONTAL);
        tabs.componentWidthOffset=4;
        String[] captions=new String[WaypointerSection.values().length];int[] widths=new int[captions.length];int index=0;
        navigationWidth=4*(captions.length-1);
        for(WaypointerSection s:WaypointerSection.values()){
            captions[index]=Messages.text(compactLabel(s));
            widths[index]=WaypointerButtonGroup.width(captions[index],16,UiDensity.HIGH,false);
            navigationWidth+=widths[index++];
        }
        WaypointerButtonGroup navigation=new WaypointerButtonGroup("hub.navigation",UiDensity.HIGH,28,16,false,captions,widths);
        index=0;
        for(WaypointerSection s:WaypointerSection.values()){
            ChamomiloUiV1Button button=WaypointerUi.button(compactLabel(s),this,widths[index],28,UiDensity.HIGH);
            navigation.apply(button,widths[index++]);button.setHoverString(Messages.text(s.label()));selectors.put(button,s);tabs.addComponent(button);
        }
        WurmBorderPanel navigationBand=new WurmBorderPanel("hub.navigation-band");
        navigationBand.setInitialSize(navigationWidth,34,false);navigationBand.sizeFlags=FIXED_HEIGHT;
        navigationBand.setComponent(tabs,NORTH);navigationBand.setComponent(spacer(1,6),SOUTH);
        root.setComponent(navigationBand,NORTH);
        for(WaypointerSection view:WaypointerSection.values())panels.put(view,create(view));
        WurmBorderPanel padded=new WurmBorderPanel("hub.padding");padded.setComponent(spacer(4,1),WEST);padded.setComponent(spacer(4,1),EAST);padded.setComponent(spacer(1,3),NORTH);padded.setComponent(spacer(1,4),SOUTH);padded.setComponent(root,CENTER);setComponent(padded);select(section);
    }
    void select(WaypointerSection next){
        boolean changed=next!=section;
        WaypointerContentPanel previous=panels.get(section);if(previous!=null&&next!=section)previous.leaving();
        section=next;WaypointerContentPanel panel=panels.get(next);if(panel==null){panel=create(next);panels.put(next,panel);}
        if(previous!=null&&previous!=panel)previous.parent=null;
        root.setComponent(panel,CENTER);for(Map.Entry<WButton,WaypointerSection> tab:selectors.entrySet())tab.getKey().setEnabled(tab.getValue()!=next);
        root.componentResized();
        setSize(width,height);
        if(changed)panel.entered();
    }
    private WaypointerContentPanel create(WaypointerSection view){
        if(view==WaypointerSection.ALL_WAYPOINTS)return new WaypointManagerWindow(controller);
        if(view==WaypointerSection.SETTINGS)return new WaypointerSettingsPanel(controller.settings());
        if(view==WaypointerSection.MY_VEHICLES||view==WaypointerSection.MY_ANIMALS)return new TrackedTargetsPanel(controller.tracking(),controller,view==WaypointerSection.MY_ANIMALS);
        SurroundingsWindow panel=new SurroundingsWindow(controller.surroundings());panel.selectKind(view==WaypointerSection.MOBS_AROUND?SurroundingKind.ANIMAL:view==WaypointerSection.CONTAINERS_AROUND?SurroundingKind.CONTAINER:SurroundingKind.ITEM);return panel;
    }
    void refreshFromController(){WaypointerContentPanel panel=panels.get(section);if(panel instanceof WaypointManagerWindow)((WaypointManagerWindow)panel).refreshFromController();}
    void openEdit(UUID id){select(WaypointerSection.ALL_WAYPOINTS);((WaypointManagerWindow)panels.get(section)).openEdit(id);}
    void openCreateCoordinates(String name,String coordinates){select(WaypointerSection.ALL_WAYPOINTS);((WaypointManagerWindow)panels.get(section)).openCreateCoordinates(name,coordinates);}
    void normalizeListSizeAfterRestore(){setSize(width,height);}
    void prepareDetach(){
        for(WaypointerContentPanel panel:panels.values()) {
            try { panel.leaving(); }
            catch(Throwable failure) { java.util.logging.Logger.getLogger("WurmWaypointer.Manager").log(
                    java.util.logging.Level.WARNING,"Unable to finish panel cleanup",failure); }
        }
    }
    boolean ownsHudComponent(WurmComponent component){
        if(component instanceof WurmDropdownPopup)
            return ownsHudComponent(((WurmDropdownPopup)component).dropDown);
        for(WurmComponent current=component;current!=null;current=current.parent)
            if(current==this||panels.containsValue(current))return true;
        return false;
    }
    boolean mouseWheeledAt(int x,int y,int delta){WaypointerContentPanel panel=panels.get(section);return panel!=null&&panel.mouseWheeledAt(x,y,delta);}
    @Override boolean hasInputField(){WaypointerContentPanel panel=panels.get(section);return panel!=null&&panel.hasInputField();}
    @Override WurmInputField getInputField(){WaypointerContentPanel panel=panels.get(section);return panel==null?null:panel.getInputField();}
    @Override public void gameTick(){if(languageRevision!=Messages.revision())rebuild();super.gameTick();}
    int minimumWindowWidth(){if(panels==null)return 940;int content=navigationWidth;for(WaypointerContentPanel panel:panels.values())content=Math.max(content,panel.minimumContentWidth());return Math.max(940,content+32);}
    @Override void setSize(int w,int h){super.setSize(Math.max(minimumWindowWidth(),w),minimized?h:Math.max(380,h));}
    private static String compactLabel(WaypointerSection section){
        switch(section){case ALL_WAYPOINTS:return "Waypoints";case MOBS_AROUND:return "Mobs";case CONTAINERS_AROUND:return "Containers";case OBJECTS_AROUND:return "Objects / items";case MY_VEHICLES:return "Vehicles";case MY_ANIMALS:return "My animals";default:return "Settings";}
    }
    @Override public void buttonPressed(WButton button){}
    @Override public void buttonClicked(WButton button){WaypointerSection next=selectors.get(button);if(next!=null&&button.isEnabled())select(next);}
    @Override void closePressed(){WaypointManagerWindowBridge.closed(this);}
    private static FlexComponent spacer(int w,int h){return new FlexComponent("hub.spacer",0,0,w,h){{sizeFlags=FIXED_WIDTH|FIXED_HEIGHT;}};}
}
