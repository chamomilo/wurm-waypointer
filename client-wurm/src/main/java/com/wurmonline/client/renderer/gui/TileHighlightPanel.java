package com.wurmonline.client.renderer.gui;

import com.wurmonline.client.renderer.backend.Queue;
import org.chamomilo.wurm.ui.v1.*;
import org.waypoints.next.i18n.Messages;
import org.waypoints.next.surroundings.CreatureHighlightSettings;
import org.waypoints.next.tiles.TileHighlightSettings;
import org.waypoints.next.ui.SettingsController;
import java.util.Properties;

/** Independent tile and meteor rules with on-demand colour editing. */
final class TileHighlightPanel extends WaypointerContentPanel {
    private final SettingsController controller;
    private final WaypointerCompactAction nonRideable,flowers,color,harvestable,harvestableColor,overaged,overagedColor,meteors,meteorColor;
    private final WaypointerFilterCheck autoMark,chime;
    private final WurmLabel meteorName;
    private final UiCompactLayout[] switches;
    private CreatureHighlightColorWindow colorWindow;
    private String draftColor;
    private String colorKey=TileHighlightSettings.COLOR;
    private int popupX=-1,popupY=-1;
    private boolean restorePopup;
    private final Palette swatch,harvestableSwatch,overagedSwatch,meteorSwatch;
    private Properties lastSettings;
    private int lastDetected=-1;
    TileHighlightPanel(SettingsController controller) {
        super("waypointer.tiles-highlight",false);this.controller=controller;
        switches=UiCompactLayout.group(Messages.texts(new String[]{"ON","OFF"}),new int[]{50,50},UiScale.BASE,true);
        TileHighlightSettings settings=new TileHighlightSettings(controller.values());draftColor=settings.hex();
        nonRideable=new WaypointerCompactAction("tiles.switches",Messages.text(settings.nonRideable()?"ON":"OFF"),switches[settings.nonRideable()?0:1],()->toggle(TileHighlightSettings.NON_RIDEABLE));
        flowers=new WaypointerCompactAction("tiles.switches",Messages.text(settings.flowers()?"ON":"OFF"),switches[settings.flowers()?0:1],()->toggle(TileHighlightSettings.FLOWERS));
        UiCompactLayout layout=UiCompactLayout.group(new String[]{Messages.text("Color")},new int[]{64},UiScale.BASE,false)[0];
        color=new WaypointerCompactAction("tiles.colors",Messages.text("Color"),layout,()->chooseColor(TileHighlightSettings.COLOR));
        harvestable=new WaypointerCompactAction("tiles.switches",Messages.text(settings.harvestable()?"ON":"OFF"),switches[settings.harvestable()?0:1],()->toggle(TileHighlightSettings.HARVESTABLE));
        harvestableColor=new WaypointerCompactAction("tiles.colors",Messages.text("Color"),layout,()->chooseColor(TileHighlightSettings.HARVESTABLE_COLOR));
        overaged=new WaypointerCompactAction("tiles.switches",Messages.text(settings.overaged()?"ON":"OFF"),switches[settings.overaged()?0:1],()->toggle(TileHighlightSettings.OVERAGED));
        overaged.setHoverString(Messages.text("Include Overaged and Shrivelled trees."));
        overagedColor=new WaypointerCompactAction("tiles.colors",Messages.text("Color"),layout,()->chooseColor(TileHighlightSettings.OVERAGED_COLOR));
        meteors=new WaypointerCompactAction("tiles.switches",Messages.text(settings.meteors()?"ON":"OFF"),switches[settings.meteors()?0:1],()->toggle(TileHighlightSettings.METEORS));
        meteorColor=new WaypointerCompactAction("tiles.colors",Messages.text("Color"),layout,()->chooseColor(TileHighlightSettings.METEOR_COLOR));
        autoMark=new WaypointerFilterCheck(settings.meteorMark(),()->toggle(TileHighlightSettings.METEOR_MARK),24);
        autoMark.setHoverString(Messages.text("Create one target per detected meteorite."));
        chime=new WaypointerFilterCheck(settings.meteorChime(),()->toggle(TileHighlightSettings.METEOR_CHIME),24);
        chime.setHoverString(Messages.text("Play a chime when a new meteorite is detected."));
        meteorName=new WaypointerLabel(Messages.text("Meteors")+" (0)");meteorName.setInitialSize(240,24,false);meteorName.sizeFlags=FIXED_WIDTH|FIXED_HEIGHT;
        swatch=new Palette(false);
        harvestableSwatch=new Palette(false);overagedSwatch=new Palette(false);
        meteorSwatch=new Palette(false);
        WurmArrayPanel<FlexComponent> rows=new WurmArrayPanel<FlexComponent>("tiles.rows",0,true);
        rows.addComponent(row("Non-rideable tiles",nonRideable,swatch,color));
        addGap(rows);
        rows.addComponent(row("Flower tiles",flowers,new Palette(true),null));
        addGap(rows);rows.addComponent(row("Harvestable areas",harvestable,harvestableSwatch,harvestableColor));
        addGap(rows);rows.addComponent(row("Overaged trees",overaged,overagedSwatch,overagedColor));
        addGap(rows);
        WurmArrayPanel<FlexComponent> meteorRow=new WurmArrayPanel<FlexComponent>("tiles.meteors",1);meteorRow.componentWidthOffset=6;
        meteorRow.addComponent(meteors);meteorRow.addComponent(meteorName);meteorRow.addComponent(meteorSwatch);meteorRow.addComponent(meteorColor);
        meteorRow.addComponent(autoMark);meteorRow.addComponent(caption("Auto-mark"));meteorRow.addComponent(chime);meteorRow.addComponent(caption("Chime"));rows.addComponent(meteorRow);
        setComponent(rows,NORTH);refresh();
    }
    private static void addGap(WurmArrayPanel<FlexComponent> rows){FlexComponent gap=new FlexComponent("tiles.row-gap",0,0,1,8){};gap.sizeFlags=FIXED_HEIGHT;rows.addComponent(gap);}
    private FlexComponent row(String caption,WButton toggle,Palette palette,WButton action) {
        WurmArrayPanel<FlexComponent> row=new WurmArrayPanel<FlexComponent>("tiles."+caption,1);row.componentWidthOffset=6;row.addComponent(toggle);
        WurmLabel label=new WaypointerLabel(Messages.text(caption));label.setInitialSize(240,24,false);label.sizeFlags=FIXED_WIDTH|FIXED_HEIGHT;row.addComponent(label);
        row.addComponent(palette);if(action!=null)row.addComponent(action);return row;
    }
    private static WurmLabel caption(String text){WurmLabel label=new WaypointerLabel(Messages.text(text));label.setInitialSize(label.text.getWidth(Messages.text(text))+8,24,false);label.sizeFlags=FIXED_WIDTH|FIXED_HEIGHT;return label;}
    private void chooseColor(String key){closeColorWindow();colorKey=key;TileHighlightSettings settings=new TileHighlightSettings(controller.values());draftColor=colorHex(settings);popupX=-1;popupY=-1;openColorWindow();}
    private String colorHex(TileHighlightSettings settings){if(colorKey.equals(TileHighlightSettings.HARVESTABLE_COLOR))return settings.harvestableHex();if(colorKey.equals(TileHighlightSettings.OVERAGED_COLOR))return settings.overagedHex();return colorKey.equals(TileHighlightSettings.METEOR_COLOR)?settings.meteorHex():settings.hex();}
    private String colorCaption(){if(colorKey.equals(TileHighlightSettings.HARVESTABLE_COLOR))return "Harvestable areas";if(colorKey.equals(TileHighlightSettings.OVERAGED_COLOR))return "Overaged trees";return colorKey.equals(TileHighlightSettings.METEOR_COLOR)?"Meteors":"Non-rideable tiles";}
    private WButton colorAnchor(){if(colorKey.equals(TileHighlightSettings.HARVESTABLE_COLOR))return harvestableColor;if(colorKey.equals(TileHighlightSettings.OVERAGED_COLOR))return overagedColor;return colorKey.equals(TileHighlightSettings.METEOR_COLOR)?meteorColor:color;}
    private void toggle(String key) {
        boolean next=!Boolean.parseBoolean(new TileHighlightSettings(controller.values()).value(key));save(key,Boolean.toString(next));refresh();
    }
    private void save(String key,String value){Properties draft=new Properties();draft.setProperty(key,value);controller.save(draft);}
    private void refresh() {
        Properties values=controller.values();int count=controller.detectedMeteors();
        if(count!=lastDetected){meteorName.setLabel(Messages.text("Meteors")+" ("+count+")");lastDetected=count;}
        if(values.equals(lastSettings))return;
        lastSettings=values;TileHighlightSettings settings=new TileHighlightSettings(values);
        state(nonRideable,settings.nonRideable());state(flowers,settings.flowers());swatch.rgb=settings.rgb();
        state(harvestable,settings.harvestable());harvestableSwatch.rgb=settings.harvestableRgb();
        state(overaged,settings.overaged());overagedSwatch.rgb=settings.overagedRgb();
        state(meteors,settings.meteors());meteorSwatch.rgb=settings.meteorRgb();autoMark.setChecked(settings.meteorMark());chime.setChecked(settings.meteorChime());
    }
    private void state(WaypointerCompactAction button,boolean active){button.apply(Messages.text(active?"ON":"OFF"),switches[active?0:1]);button.active(active);}
    private void openColorWindow() {
        if(hud==null)return;
        colorWindow=new CreatureHighlightColorWindow("waypointer.tiles-highlight.color",colorCaption(),draftColor,
                (r,g,b)->draftColor=CreatureHighlightSettings.hex(r,g,b),
                ()->{save(colorKey,draftColor);refresh();closeColorWindow();},()->closeColorWindow());
        WButton anchor=colorAnchor();
        int left=popupX<0?anchor.x:popupX,top=popupY<0?anchor.y+anchor.height:popupY;
        colorWindow.setPosition(Math.max(0,Math.min(left,hud.getWidth()-colorWindow.width)),Math.max(0,Math.min(top,hud.getHeight()-colorWindow.height)));
        colorWindow.show(hud);
    }
    private void closeColorWindow(){CreatureHighlightColorWindow old=colorWindow;colorWindow=null;restorePopup=false;if(old!=null){popupX=old.x;popupY=old.y;old.dispose();}}
    boolean colorWindowOpen(){return colorWindow!=null;}
    boolean ownsColorComponent(WurmComponent component){for(WurmComponent c=component;c!=null;c=c.parent)if(c==colorWindow)return true;return false;}
    void restoreDraft(TileHighlightPanel old,boolean reopen){draftColor=old.draftColor;colorKey=old.colorKey;popupX=old.popupX;popupY=old.popupY;restorePopup=reopen;}
    @Override void leaving(){closeColorWindow();}
    @Override public void gameTick(){super.gameTick();refresh();if(restorePopup){restorePopup=false;openColorWindow();}}
    @Override int minimumContentWidth(){return 800;}
    private static final class Palette extends FlexComponent {
        private final boolean flowers;private int rgb;
        Palette(boolean flowers){super("tiles.palette");this.flowers=flowers;setInitialSize(flowers?168:24,24,false);sizeFlags=FIXED_WIDTH|FIXED_HEIGHT;}
        @Override protected void renderComponent(Queue queue,float ignoredAlpha){
            ChamomiloUiV1Canvas canvas=WaypointerUi.canvas(this,queue);
            int[] colors=flowers?new int[]{0xffe233,0xff542c,0xb45aff,0xffffff,0x408cff,0xc4df48,0xf0f0e4}:new int[]{rgb};
            for(int i=0;i<colors.length;i++){canvas.fill(UiColor.rgb(colors[i]),1f,x+i*24+3,y+3,18,18);UiPainter.frame(canvas,3,1f,x+i*24,y,24,24);}
        }
    }
}
