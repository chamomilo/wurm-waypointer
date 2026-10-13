package com.wurmonline.client.renderer.gui;

import com.wurmonline.client.renderer.backend.Queue;
import org.chamomilo.wurm.ui.v1.*;
import org.waypoints.next.i18n.Messages;
import org.waypoints.next.surroundings.*;
import org.waypoints.next.ui.SettingsController;
import java.util.*;

/** Dedicated settings view with immediate switches and an on-demand HSV colour window. */
final class CreatureHighlightPanel extends WaypointerContentPanel implements ButtonListener {
    private final SettingsController controller;
    private final Map<WButton,CreatureHighlightRule> toggles=new LinkedHashMap<WButton,CreatureHighlightRule>();
    private final Map<WButton,CreatureHighlightRule> colors=new LinkedHashMap<WButton,CreatureHighlightRule>();
    private final Map<CreatureHighlightRule,WurmLabel> labels=new EnumMap<CreatureHighlightRule,WurmLabel>(CreatureHighlightRule.class);
    private final Map<CreatureHighlightRule,WaypointerFilterCheck> autoMarks=new EnumMap<CreatureHighlightRule,WaypointerFilterCheck>(CreatureHighlightRule.class);
    private final Map<CreatureHighlightRule,ColorSwatch> swatches=new EnumMap<CreatureHighlightRule,ColorSwatch>(CreatureHighlightRule.class);
    private final WurmArrayPanel<FlexComponent> traits,categories;
    private final UiCompactLayout[] switchLayouts;
    private final int contentWidth;
    private CreatureHighlightColorWindow colorWindow;
    private final WButton allOn,allOff;
    private CreatureHighlightRule selected=CreatureHighlightRule.ANIMALS;
    private String draftColor;
    private int popupX=-1,popupY=-1;
    private boolean restorePopup;
    private Map<CreatureHighlightRule,Integer> lastDetected=Collections.emptyMap();
    private Properties lastSettings;

    CreatureHighlightPanel(SettingsController controller) {
        super("waypointer.creatures-highlight",false);this.controller=controller;
        CreatureHighlightSettings settings=new CreatureHighlightSettings(controller.values());
        WurmArrayPanel<FlexComponent> heading=new WurmArrayPanel<FlexComponent>("highlight.heading",0,true);
        allOn=WaypointerUi.button("ALL ON",this,110);allOff=WaypointerUi.button("ALL OFF",this,110);
        WaypointerButtonGroup.peers("highlight.bulk",UiDensity.HIGH,28,allOn,allOff);
        WurmArrayPanel<FlexComponent> bulk=new WurmArrayPanel<FlexComponent>("highlight.bulk",1);bulk.componentWidthOffset=6;
        bulk.addComponent(allOn);bulk.addComponent(allOff);
        WurmBorderPanel commands=new WurmBorderPanel("highlight.commands");commands.setInitialSize(800,34,false);commands.sizeFlags=FIXED_HEIGHT;
        commands.setComponent(bulk,NORTH);heading.addComponent(commands);
        setComponent(heading,NORTH);
        switchLayouts=UiCompactLayout.group(Messages.texts(new String[]{"ON","OFF"}),new int[]{50,50},UiScale.BASE,true);
        UiCompactLayout colorLayout=UiCompactLayout.group(new String[]{Messages.text("Color")},new int[]{64},UiScale.BASE,false)[0];
        int autoWidth=com.wurmonline.client.renderer.gui.text.WaypointerFonts.body().getWidth(Messages.text("Auto-mark"))+8;
        int traitName=0,categoryName=0;
        for(CreatureHighlightRule rule:CreatureHighlightRule.values()) {
            int size=com.wurmonline.client.renderer.gui.text.WaypointerFonts.body().getWidth(Messages.text(rule.label())+" (99999)")+8;
            if(leftColumn(rule))traitName=Math.max(traitName,size);else categoryName=Math.max(categoryName,size);
        }
        int fixed=50+24+colorLayout.width+24+autoWidth+6*6;
        int traitWidth=traitName+fixed,categoryWidth=categoryName+fixed;
        contentWidth=traitWidth+56+categoryWidth;
        traits=new WurmArrayPanel<FlexComponent>("highlight.traits",0,traitWidth,288);traits.sizeFlags=FIXED_WIDTH|FIXED_HEIGHT;
        categories=new WurmArrayPanel<FlexComponent>("highlight.categories",0,categoryWidth,192);categories.sizeFlags=FIXED_WIDTH|FIXED_HEIGHT;
        for(final CreatureHighlightRule rule:CreatureHighlightRule.values()) {
            int rowWidth=leftColumn(rule)?traitWidth:categoryWidth;
            WurmArrayPanel<FlexComponent> row=new WurmArrayPanel<FlexComponent>("highlight."+rule.name(),1,rowWidth,24);row.componentWidthOffset=6;row.sizeFlags=FIXED_HEIGHT;
            WurmLabel name=new WaypointerLabel(Messages.text(rule.label())+" (0)");name.setInitialSize(leftColumn(rule)?traitName:categoryName,24,false);name.sizeFlags=FIXED_WIDTH|FIXED_HEIGHT;labels.put(rule,name);
            final WButton[] toggleRef=new WButton[1];
            WaypointerCompactAction toggle=new WaypointerCompactAction("highlight.switches",Messages.text(settings.enabled(rule)?"ON":"OFF"),switchLayouts[settings.enabled(rule)?0:1],()->buttonClicked(toggleRef[0]));
            toggleRef[0]=toggle;toggle.active(settings.enabled(rule));toggles.put(toggle,rule);
            row.addComponent(toggle);row.addComponent(name);
            ColorSwatch swatch=new ColorSwatch(settings.hex(rule));swatches.put(rule,swatch);row.addComponent(swatch);
            final WButton[] colorRef=new WButton[1];
            WaypointerCompactAction color=new WaypointerCompactAction("highlight.colors",Messages.text("Color"),colorLayout,()->buttonClicked(colorRef[0]));
            colorRef[0]=color;colors.put(color,rule);row.addComponent(color);
            if(rule.supportsAutoMark()) {
                WaypointerFilterCheck check=new WaypointerFilterCheck(settings.autoMark(rule),()->toggleAutoMark(rule),24);
                check.setHoverString(Messages.text("Create a target when this creature appears in the loaded area."));autoMarks.put(rule,check);row.addComponent(check);
                WurmLabel caption=label("Auto-mark",24);caption.setInitialSize(autoWidth,24,false);caption.sizeFlags=FIXED_WIDTH|FIXED_HEIGHT;row.addComponent(caption);
            }
            (leftColumn(rule)?traits:categories).addComponent(row);
        }
        WurmBorderPanel columns=new WurmBorderPanel("highlight.columns");
        WurmBorderPanel left=new WurmBorderPanel("highlight.left");left.setInitialSize(traitWidth,360,false);left.sizeFlags=FIXED_WIDTH;left.setComponent(traits,NORTH);
        // The gap has its own slot so it never consumes a row's measured label width.
        WurmBorderPanel gapAndRight=new WurmBorderPanel("highlight.right");gapAndRight.setComponent(spacer(56),WEST);
        WurmBorderPanel right=new WurmBorderPanel("highlight.categories-body");right.setComponent(categories,NORTH);gapAndRight.setComponent(right,CENTER);
        columns.setComponent(left,WEST);columns.setComponent(gapAndRight,CENTER);
        setComponent(columns,CENTER);draftColor=settings.hex(selected);lastSettings=controller.values();updateDetected();
    }
    private static WurmLabel label(String caption,int height) {
        WurmLabel label=new WaypointerLabel(Messages.text(caption));label.setInitialSize(800,height,false);label.sizeFlags=FIXED_HEIGHT;return label;
    }
    private static FlexComponent spacer(int width) {
        FlexComponent component=new FlexComponent("highlight.auto-mark-space",0,0,width,24){};component.sizeFlags=FIXED_WIDTH|FIXED_HEIGHT;return component;
    }
    private void save(String key,String value) { Properties draft=new Properties();draft.setProperty(key,value);controller.save(draft); }
    private void toggleAutoMark(CreatureHighlightRule rule) {
        WaypointerFilterCheck check=autoMarks.get(rule);boolean next=!check.checked();
        save(rule.key("autoMark"),Boolean.toString(next));check.setChecked(next);
    }
    @Override public void buttonPressed(WButton button) { }
    @Override public void buttonClicked(WButton button) {
        CreatureHighlightRule rule=toggles.get(button);
        if(rule!=null) {
            boolean enabled=!new CreatureHighlightSettings(controller.values()).enabled(rule);
            save(rule.key("enabled"),Boolean.toString(enabled));setSwitch(button,enabled);return;
        }
        rule=colors.get(button);
        if(rule!=null) {
            closeColorWindow();selected=rule;draftColor=new CreatureHighlightSettings(controller.values()).hex(rule);
            openColorWindow(button);return;
        }
        if(button==allOn||button==allOff) {
            Properties draft=new Properties();
            for(CreatureHighlightRule value:CreatureHighlightRule.values())draft.setProperty(value.key("enabled"),Boolean.toString(button==allOn));
            controller.save(draft);refreshSwitches(new CreatureHighlightSettings(controller.values()));return;
        }
    }
    private void refreshSwitches(CreatureHighlightSettings settings) {
        for(Map.Entry<WButton,CreatureHighlightRule> toggle:toggles.entrySet()) {
            setSwitch(toggle.getKey(),settings.enabled(toggle.getValue()));
        }
    }
    private void setSwitch(WButton button,boolean enabled) {
        WaypointerCompactAction action=(WaypointerCompactAction)button;action.apply(Messages.text(enabled?"ON":"OFF"),switchLayouts[enabled?0:1]);action.active(enabled);
    }
    private void openColorWindow(WButton anchor) {
        if(hud==null)return;
        colorWindow=new CreatureHighlightColorWindow(selected,draftColor,
                (r,g,b)->draftColor=CreatureHighlightSettings.hex(r,g,b),
                ()->{save(selected.key("color"),draftColor);swatches.get(selected).hex=draftColor;closeColorWindow();},
                ()->closeColorWindow());
        int left=anchor==null?popupX:anchor.x,top=anchor==null?popupY:anchor.y+anchor.height;
        if(left<0)left=x;if(top<0)top=y;
        colorWindow.setPosition(Math.max(0,Math.min(left,hud.getWidth()-colorWindow.width)),
                Math.max(0,Math.min(top,hud.getHeight()-colorWindow.height)));
        colorWindow.show(hud);
    }
    private void closeColorWindow() {
        CreatureHighlightColorWindow previous=colorWindow;colorWindow=null;restorePopup=false;
        if(previous!=null){popupX=previous.x;popupY=previous.y;previous.dispose();}
    }
    boolean colorWindowOpen() { return colorWindow!=null; }
    boolean ownsColorComponent(WurmComponent component) {
        for(WurmComponent current=component;current!=null;current=current.parent)if(current==colorWindow)return true;
        return false;
    }
    @Override void leaving() { closeColorWindow(); }
    private void updateDetected() {
        Map<CreatureHighlightRule,Integer> next=controller.detectedCreatures();
        if(next==null)next=Collections.emptyMap();
        if(next==lastDetected)return;
        for(CreatureHighlightRule rule:CreatureHighlightRule.values()) {
            int count=next.getOrDefault(rule,0),previous=lastDetected.getOrDefault(rule,0);
            if(count!=previous)labels.get(rule).setLabel(Messages.text(rule.label())+" ("+count+")");
        }
        lastDetected=next;
    }
    @Override public void gameTick() {
        super.gameTick();
        Properties preferences=controller.values();
        if(!preferences.equals(lastSettings)) {
            CreatureHighlightSettings settings=new CreatureHighlightSettings(preferences);refreshSwitches(settings);
            for(CreatureHighlightRule rule:CreatureHighlightRule.values()) {
                swatches.get(rule).hex=settings.hex(rule);
                if(rule.supportsAutoMark())autoMarks.get(rule).setChecked(settings.autoMark(rule));
            }
            lastSettings=preferences;
        }
        updateDetected();
        if(restorePopup){restorePopup=false;openColorWindow(null);}
    }
    void restoreDraft(CreatureHighlightPanel old,boolean reopen) {
        selected=old.selected;draftColor=old.draftColor;popupX=old.popupX;popupY=old.popupY;restorePopup=reopen;
    }
    @Override int minimumContentWidth() { return contentWidth; }
    static boolean leftColumn(CreatureHighlightRule rule){return rule.modifier()!=null&&rule!=CreatureHighlightRule.SMALL&&rule!=CreatureHighlightRule.MINI&&rule!=CreatureHighlightRule.TINY;}
    @Override int minimumContentHeight() { return 322; }
    private static final class ColorSwatch extends FlexComponent {
        private String hex;
        ColorSwatch(String hex) { super("highlight.swatch");this.hex=hex;setInitialSize(24,24,false);sizeFlags=FIXED_WIDTH|FIXED_HEIGHT; }
        @Override protected void renderComponent(Queue queue,float ignoredAlpha) {
            ChamomiloUiV1Canvas canvas=WaypointerUi.canvas(this,queue);canvas.fill(UiColor.rgb(Integer.parseInt(hex.substring(1),16)),1f,x+3,y+3,width-6,height-6);
            UiPainter.frame(canvas,3,1f,x,y,width,height);
        }
    }
}
