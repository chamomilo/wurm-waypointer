package com.wurmonline.client.renderer.gui;

import com.wurmonline.client.renderer.backend.Queue;
import org.waypoints.next.i18n.Messages;
import org.waypoints.next.ui.*;
import org.waypoints.next.surroundings.SurroundingKind;
import org.chamomilo.wurm.ui.v1.UiDensity;
import org.chamomilo.wurm.ui.v1.UiTypography;
import com.wurmonline.client.renderer.gui.text.ChamomiloUiV1Fonts;
import com.wurmonline.client.renderer.gui.text.TextFont;
import java.util.*;

/** The only main window. Feature panels own their data and state; the hub owns chrome and selection. */
final class WaypointerHubWindow extends WaypointerUiWindow implements ButtonListener {
    private final WaypointManagerController controller;
    private final Map<WButton,WaypointerSection> selectors=new LinkedHashMap<WButton,WaypointerSection>();
    private final Map<WaypointerSection,WaypointerContentPanel> panels=new EnumMap<WaypointerSection,WaypointerContentPanel>(WaypointerSection.class);
    private WaypointerSection section=WaypointerSection.ALL_WAYPOINTS;
    private WurmBorderPanel root;
    private long languageRevision;
    private int sidebarWidth=248;
    WaypointerHubWindow(WaypointManagerController controller){super("wurm-waypointer.manager",true);this.controller=controller;setTitle(org.waypoints.next.i18n.Messages.text("Wurm Waypointer"));setHeaderHeight(29);setContentPadding(4);rebuild();}
    private void rebuild(){
        WaypointerContentPanel old=panels.get(section);if(old!=null)old.leaving();panels.clear();selectors.clear();languageRevision=Messages.revision();
        root=new WurmBorderPanel("waypointer.hub");WurmArrayPanel<FlexComponent> sidebar=new WurmArrayPanel<FlexComponent>("hub.selectors",0);
        int selectorHeight=56;
        TextFont normal = ChamomiloUiV1Fonts.caption(24, false, UiDensity.LOW);
        TextFont bold = ChamomiloUiV1Fonts.caption(24, true, UiDensity.LOW);
        sidebarWidth=248;for(WaypointerSection s:WaypointerSection.values()){
            String caption=Messages.text(s.label()).toUpperCase(Locale.ROOT);
            sidebarWidth=Math.max(sidebarWidth,WaypointerButtonGroup.width(caption,24,UiDensity.LOW,true));
        }
        String[] captions=new String[WaypointerSection.values().length];int[] widths=new int[captions.length];int index=0;
        for(WaypointerSection s:WaypointerSection.values()){captions[index]=Messages.text(s.label());widths[index++]=sidebarWidth;}
        WaypointerButtonGroup navigation=new WaypointerButtonGroup("hub.navigation",UiDensity.LOW,selectorHeight,128,true,captions,widths);
        for(WaypointerSection s:WaypointerSection.values()){
            ChamomiloUiV1Button button=WaypointerUi.button(s.label(),this,sidebarWidth,selectorHeight,UiDensity.LOW);navigation.apply(button,sidebarWidth);selectors.put(button,s);sidebar.addComponent(button);
            sidebar.addComponent(spacer(sidebarWidth,5));
        }
        sidebar.sizeFlags=FIXED_WIDTH;
        WurmBorderPanel left=new WurmBorderPanel("hub.left");left.setComponent(sidebar,NORTH);left.setComponent(spacer(8,1),EAST);left.setInitialSize(sidebarWidth+8,350,false);left.sizeFlags=FIXED_WIDTH;
        root.setComponent(left,WEST);
        for(WaypointerSection view:WaypointerSection.values())panels.put(view,create(view));
        WurmBorderPanel padded=new WurmBorderPanel("hub.padding");padded.setComponent(spacer(8,1),WEST);padded.setComponent(spacer(8,1),EAST);padded.setComponent(spacer(1,5),NORTH);padded.setComponent(spacer(1,6),SOUTH);padded.setComponent(root,CENTER);setComponent(padded);select(section);
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
        if(view==WaypointerSection.MY_MANAGED||view==WaypointerSection.MY_FRIENDS)return new TrackedTargetsPanel(controller.tracking(),controller,view==WaypointerSection.MY_FRIENDS);
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
    @Override public void gameTick(){if(languageRevision!=Messages.revision())rebuild();if(width<minimumWindowWidth())setSize(width,height);super.gameTick();}
    int minimumWindowWidth(){if(panels==null)return 1180;int content=900;for(WaypointerContentPanel panel:panels.values())content=Math.max(content,panel.minimumContentWidth());return Math.max(1180,sidebarWidth+content+68);}
    @Override void setSize(int w,int h){super.setSize(Math.max(minimumWindowWidth(),w),minimized?h:Math.max(500,h));}
    @Override public void buttonPressed(WButton button){}
    @Override public void buttonClicked(WButton button){WaypointerSection next=selectors.get(button);if(next!=null&&button.isEnabled())select(next);}
    @Override void closePressed(){WaypointManagerWindowBridge.closed(this);}
    private static FlexComponent spacer(int w,int h){return new FlexComponent("hub.spacer",0,0,w,h){{sizeFlags=FIXED_WIDTH|FIXED_HEIGHT;}};}
}
