package com.wurmonline.client.renderer.gui;

import com.wurmonline.client.renderer.backend.Queue;
import com.wurmonline.client.renderer.gui.text.TextFont;
import org.chamomilo.wurm.update.ModUpdate;
import org.chamomilo.wurm.update.UpdatePreferences;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.*;
import java.util.*;
import javax.imageio.ImageIO;

/** Offscreen preview uses the production component tree and paint coordinates. */
public final class UpdaterLayoutProbe {
    private static BufferedImage canvas, artwork, buttonArtwork;
    private static com.wurmonline.client.resources.textures.ResourceTexture buttonTexture;
    private static final Map<Integer,BufferedImage> tintedButtons = new HashMap<Integer,BufferedImage>();
    private static Graphics2D graphics;
    private static final Deque<Shape> clips = new LinkedList<Shape>();
    private static int fontSize = 12;
    private static UpdatePreferences preferences;
    private static Path preferenceFile;

    public static Object allocate(Class<?> type) throws Exception {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
        return ((sun.misc.Unsafe) field.get(null)).allocateInstance(type);
    }
    public static void main(String[] args) throws Exception {
        artwork = ImageIO.read(new File(args[0]));
        buttonArtwork = ImageIO.read(new File(args[2]));
        canvas = new BufferedImage(1000, 900, BufferedImage.TYPE_INT_ARGB);
        graphics = canvas.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        preferenceFile = Paths.get(args[1]).getParent().resolve("updater-preference-fixture/settings.properties");
        Files.deleteIfExists(preferenceFile);
        Path user = preferenceFile.getParent().resolve("user");
        Files.deleteIfExists(user.resolve(".chamomilo/updater.properties"));
        System.setProperty("user.home", user.toAbsolutePath().toString());
        preferences = new UpdatePreferences(preferenceFile);
        java.util.List<ModUpdate> rows = rows();
        final int[] downloads = {0};
        ChamomiloUpdateWindow window = window(rows, 1280, 1080, downloads);
        WurmScrollPanel scroll = scroll(window);
        window.gameTick();
        check(window.getTitle().equals("Mods Registry by Chamomilo"), "Registry heading");
        check(!children(scroll).contains(scroll.verticalScrollBar), "Scrollbar has no visible or clickable child");
        check(scroll.content.height <= ((WurmComponent) scroll.offs).height, "Full list must fit without scrolling on a tall screen: "
                + scroll.content.height + "/" + ((WurmComponent) scroll.offs).height);
        check(window.height < 850, "Compact eight-card window");
        WurmArrayPanel<?> content = (WurmArrayPanel<?>) scroll.content;
        int previousBottom = Integer.MIN_VALUE;
        for (FlexComponent card : content.components) {
            if (card.height == 6) continue;
            check(card.y > previousBottom, "Card spacing"); previousBottom = card.y + card.height;
            check(card.height >= 64 && card.height <= 76, "Compact card height: " + card.height);
            WButton button = action(card);
            check(button.width == 108, "Fixed action width: " + button.width);
            check(button.height == new WButton(button.getLabel()).height + 1, "Button gains exactly one bottom pixel");
            check(button.height < card.height / 2, "Action button must not stretch to card height");
            if (button.getLabel().equals("LATEST")) {
                check(!button.isEnabled(), "LATEST is disabled"); window.buttonClicked(button);
            }
        }
        check(downloads[0] == 0, "Disabled actions do not download");
        WButton firstAction = action(content.components.get(0));
        window.buttonClicked(firstAction);
        check(downloads[0] == 1, "DOWNLOAD routes to the host");

        window.setPosition(30, 30);
        rect(.025f,.023f,.018f,1f,0,0,canvas.getWidth(),canvas.getHeight());
        window.render(null, .2f);
        for(FlexComponent card:content.components)if(card.height!=6)verifyButtonRim(action(card));
        verifyButtonRim((WButton)field(ChamomiloUpdateWindow.class,"laterButton").get(window));
        window.render(null,.2f);
        ImageIO.write(canvas, "png", new File(args[1]));

        WButton skip = checkbox(window.getComponent());
        check(!checked(skip), "First launch offers an unchecked opt-out");
        verifyFooter(window);
        click(skip,skip.x+30,skip.y+skip.height/2);
        window.gameTick();
        check(new UpdatePreferences(preferenceFile).isSkipNextStart(), "Native checkbox saves on tick");
        preferences = new UpdatePreferences(preferenceFile);
        ChamomiloUpdateWindow nextStart = window(rows.subList(0, 3), 1280, 1080, downloads);
        WButton nextSkip = checkbox(nextStart.getComponent());
        check(checked(nextSkip), "Saved opt-out survives restart and a changed mod list");
        click(nextSkip,nextSkip.x+5,nextSkip.y+nextSkip.height/2);
        nextStart.closePressed();
        check(!new UpdatePreferences(preferenceFile).isSkipNextStart(), "Close saves an unticked box before hiding");
        preferences = new UpdatePreferences(preferenceFile);

        ChamomiloUpdateWindow small = window(rows, 1024, 480, downloads);
        WurmScrollPanel smallScroll = scroll(small); small.gameTick();
        check(small.height <= 460, "Small-screen window stays within HUD");
        check(!children(smallScroll).contains(smallScroll.verticalScrollBar), "Overflow has no visible scrollbar");
        check(smallScroll.content.height > ((WurmComponent) smallScroll.offs).height, "Overflow scrolls");
        int oldOffset = smallScroll.yo;
        smallScroll.mouseWheeled(100,100,4);
        check(smallScroll.yo != oldOffset, "Mouse wheel scrolls");
        smallScroll.scrollDownToBottom();
        check(smallScroll.yo > 0, "Last card reachable");
        small.setSize(700, 260); small.gameTick();
        check(small.height == 260, "Native resize remains available");
        verifyFooter(small);

        fontSize = 10;
        ChamomiloUpdateWindow smallFont = window(rows, 1280, 1080, downloads);
        smallFont.gameTick();
        verifyFooter(smallFont);

        fontSize = 18;
        ChamomiloUpdateWindow largeFont = window(rows, 1280, 1080, downloads);
        largeFont.gameTick();
        verifyFooter(largeFont);
        check(scroll(largeFont).content.height <= ((WurmComponent) scroll(largeFont).offs).height, "Large font layout fits");
        check(ChamomiloUpdateWindow.FRAME_PIXELS == 5, "Frame thickness");
        verifyMenuLifecycle(rows);
        System.out.println("UPDATER_UI_OK: native layout, full list, overflow, wheel, resize, actions and large fonts; preview=" + args[1]);
        graphics.dispose();
    }

    private static ChamomiloUpdateWindow window(java.util.List<ModUpdate> rows, int w, int h, final int[] count) throws Exception {
        HeadsUpDisplay hud = fixtureHud(w,h);
        return new ChamomiloUpdateWindow(hud, rows,"","","","","Close","Close", row -> count[0]++, () -> {}, preferences);
    }
    private static HeadsUpDisplay fixtureHud(int w, int h) throws Exception {
        HeadsUpDisplay hud = (HeadsUpDisplay) allocate(HeadsUpDisplay.class);
        field(HeadsUpDisplay.class,"width").setInt(hud,w); field(HeadsUpDisplay.class,"height").setInt(hud,h);
        field(HeadsUpDisplay.class,"components").set(hud,new ArrayList<WurmComponent>());
        WurmComponent.SCREEN_WIDTH=w; WurmComponent.SCREEN_HEIGHT=h; WurmComponent.hud=hud;
        field(HeadsUpDisplay.class,"mainMenu").set(hud,new MainMenu());
        return hud;
    }
    public static com.wurmonline.client.options.MultiOption guiSkin() throws Exception {
        com.wurmonline.client.options.MultiOption skin=(com.wurmonline.client.options.MultiOption)allocate(com.wurmonline.client.options.MultiOption.class);
        field(com.wurmonline.client.options.MultiOption.class,"options").set(skin,new String[]{"Default"});
        return skin;
    }
    private static void verifyMenuLifecycle(java.util.List<ModUpdate> rows) throws Exception {
        resetSharedHost();
        deliver(rows); HeadsUpDisplay hud=fixtureHud(1280,1080);
        ChamomiloUpdateBridge.hudReady(hud);
        ChamomiloUpdateWindow window=sharedWindow();
        check(window!=null && hud.isComponentEnabled(window),"First launch automatically shows Mod updates");
        check(hud.mainMenu.getMenuSet().size()==1,"Exactly one menu entry");
        WButton menuButton=hud.mainMenu.getMenuSet().iterator().next().getKey();
        check(menuButton.getLabel().equals("Mod updates"),"Exact main menu label");
        window.closePressed();
        check(!hud.isComponentEnabled(window) && menuButton.isEnabled(),"Closed window stays available in Main menu");
        hud.mainMenu.buttonClicked(menuButton);
        check(hud.isComponentEnabled(window),"Main menu reopens a closed window");
        WButton skip=checkbox(window.getComponent());click(skip,skip.x+5,skip.y+skip.height/2);window.gameTick();
        check(UpdatePreferences.shared().isSkipNextStart(),"Host window saves startup opt-out");
        window.closePressed();

        resetSharedHost(); HeadsUpDisplay next=fixtureHud(1280,1080);
        ChamomiloUpdateBridge.hudReady(next);
        check(sharedWindow()==null,"HUD can precede the background catalogue result");
        deliver(rows.subList(0,3)); ChamomiloUpdateBridge.tick(next);
        ChamomiloUpdateWindow hidden=sharedWindow();
        check(hidden!=null && !next.isComponentEnabled(hidden),"Opted-out startup registers a hidden window with a changed catalogue");
        ChamomiloUpdateBridge.hudReady(next); ChamomiloUpdateBridge.tick(next);
        check(next.mainMenu.getMenuSet().size()==1,"Repeated HUD callbacks do not duplicate the entry");
        WButton nextButton=next.mainMenu.getMenuSet().iterator().next().getKey();next.mainMenu.buttonClicked(nextButton);
        check(next.isComponentEnabled(hidden),"Main menu works with startup display disabled");
        WButton remembered=checkbox(hidden.getComponent());check(checked(remembered),"Reopened box retains checked state");
        click(remembered,remembered.x+30,remembered.y+remembered.height/2);hidden.closePressed();
        check(!UpdatePreferences.shared().isSkipNextStart(),"Unticking and closing re-enables startup");
        resetSharedHost(); HeadsUpDisplay last=fixtureHud(1280,1080);
        deliver(rows); ChamomiloUpdateBridge.hudReady(last);
        check(last.isComponentEnabled(sharedWindow()),"Following startup shows the window again");
        HeadsUpDisplay replacement=fixtureHud(1280,1080);
        ChamomiloUpdateBridge.hudReady(replacement);
        check(replacement.mainMenu.getMenuSet().size()==1 && !replacement.isComponentEnabled(sharedWindow()),
                "A replacement HUD registers one hidden menu entry without repeating the startup popup");
        System.out.println("UPDATER_MENU_OK: first startup, persistent checkbox, changed catalogue, hidden registration, close/reopen and duplicate prevention");
    }
    private static void resetSharedHost() throws Exception {
        field(ChamomiloUpdateBridge.class,"readyHud").set(null,null);
        field(ChamomiloUpdateBridge.class,"window").set(null,null);
        field(ChamomiloUpdateBridge.class,"startupHandled").setBoolean(null,false);
        deliver(null);
    }
    private static void deliver(java.util.List<ModUpdate> rows) throws Exception {
        field(org.chamomilo.wurm.update.SharedUpdateHooks.class,"pending").set(null,rows);
    }
    private static ChamomiloUpdateWindow sharedWindow() throws Exception {
        return (ChamomiloUpdateWindow)field(ChamomiloUpdateBridge.class,"window").get(null);
    }
    private static java.util.List<ModUpdate> rows() throws Exception {
        String[] ids={"avatar2","wurm-highres","highres-hud","highres-startup","idleanimations","keybinder","armor-material-colors","wurm-waypointer"};
        String[] names={"Avatar 2.0","High Res Icons","HighRes HUD","HighRes Startup","Idle Animations","Keybinder","Material Colors","Waypointer"};
        String[] repos={"Wurm-avatar-2.0","wurm-high-res-icons","Wurm-HighRes-HUD","wurm-highres-startup","wurm-idle-animations","wurm-keybinder","wurm-material-colors","wurm-waypointer"};
        Constructor<ModUpdate> constructor=ModUpdate.class.getDeclaredConstructor(String.class,String.class,String.class,String.class,String.class,boolean.class,String.class);
        constructor.setAccessible(true);
        java.util.List<ModUpdate> rows=new ArrayList<ModUpdate>();
        for(int i=0;i<ids.length;i++) rows.add(constructor.newInstance(ids[i],names[i],i==0||i==3?"":i==5?"0.9.9":"1.0.0",i==5?"0.10.1":"1.0.0",
                "https://github.com/chamomilo/"+repos[i]+"/releases/latest",i==5,""));
        return rows;
    }
    private static Field field(Class<?> type,String name) throws Exception {Field f=type.getDeclaredField(name);f.setAccessible(true);return f;}
    private static java.util.List<FlexComponent> children(WurmComponent parent) throws Exception {
        if(parent instanceof WurmArrayPanel) return new ArrayList<FlexComponent>(((WurmArrayPanel<?>)parent).components);
        if(parent instanceof WurmBorderPanel) return Arrays.asList((FlexComponent[])field(WurmBorderPanel.class,"components").get(parent));
        return Collections.emptyList();
    }
    private static WurmScrollPanel scroll(WurmComponent parent) throws Exception {
        if(parent instanceof WWindow) return scroll(((WWindow)parent).getComponent());
        if(parent instanceof WurmScrollPanel) return (WurmScrollPanel)parent;
        for(FlexComponent child:children(parent))if(child!=null){WurmScrollPanel result=scroll(child);if(result!=null)return result;}
        return null;
    }
    private static WButton action(WurmComponent parent) throws Exception {
        if(parent instanceof WButton) return (WButton)parent;
        for(FlexComponent child:children(parent))if(child!=null){WButton result=action(child);if(result!=null)return result;}
        return null;
    }
    private static WButton checkbox(WurmComponent parent) throws Exception {
        if(parent instanceof WButton && ((WButton)parent).getLabel().equals("Don't show on next start")) return (WButton)parent;
        for(FlexComponent child:children(parent))if(child!=null){WButton result=checkbox(child);if(result!=null)return result;}
        return null;
    }
    private static boolean checked(WButton checkbox) throws Exception {return field(checkbox.getClass(),"checked").getBoolean(checkbox);}
    private static void click(WButton button,int x,int y){button.leftPressed(x,y,0);button.leftReleased(x,y);}
    private static void verifyFooter(ChamomiloUpdateWindow window) throws Exception {
        WButton skip=checkbox(window.getComponent());
        WButton close=(WButton)field(ChamomiloUpdateWindow.class,"laterButton").get(window);
        check(skip.height==close.height && skip.y==close.y,"Footer controls share one compact row");
        check(skip.height>=skip.text.getHeight()+1,"Footer label fits with extra bottom space");
        check(window.y+window.height-(skip.y+skip.height)<=24,"Footer has no excess empty area");
    }
    private static void verifyButtonRim(WButton button) {
        for(int state=0;state<3;state++) {
            button.hovered=state==1;
            button.isDown=state==2;
            button.render(null,1f);
            // Compare the visible outer rail; inner rows can also contain label antialiasing.
            for(int row=0;row<2;row++) for(int x=button.x;x<button.x+button.width;x++) {
                Color top=new Color(canvas.getRGB(x,button.y+row),true);
                Color bottom=new Color(canvas.getRGB(x,button.y+button.height-1-row),true);
                check(Math.abs(top.getRed()-bottom.getRed())<=3 && Math.abs(top.getGreen()-bottom.getGreen())<=3
                        && Math.abs(top.getBlue()-bottom.getBlue())<=3 && top.getAlpha()==bottom.getAlpha(),
                        "Matching upper/lower iron rails: " + button.getLabel() + ", state=" + state + ", x=" + x + ", row=" + row);
            }
        }
        button.hovered=false;button.isDown=false;
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static boolean clip(int x,int y,int w,int h){clips.push(graphics.getClip()==null?new Rectangle(0,0,1000,900):graphics.getClip());graphics.clipRect(x,y,w,h);return w>0&&h>0;}
    public static void unclip(){graphics.setClip(clips.pop());}
    public static void rect(float r,float g,float b,float a,int x,int y,int w,int h){graphics.setColor(new Color(r,g,b,a));graphics.fillRect(x,y,w,h);}
    public static com.wurmonline.client.resources.textures.ResourceTexture buttonTexture() throws Exception {
        if(buttonTexture==null)buttonTexture=(com.wurmonline.client.resources.textures.ResourceTexture)allocate(com.wurmonline.client.resources.textures.ResourceTexture.class);
        return buttonTexture;
    }
    public static void texture(com.wurmonline.client.resources.textures.Texture texture,float tint,float x,float y,float w,float h,float u0,float v0,float u1,float v1){
        BufferedImage source=artwork;
        if(texture==buttonTexture){
            int key=Math.round(tint*100);
            source=tintedButtons.get(key);
            if(source==null){source=new java.awt.image.RescaleOp(new float[]{tint,tint,tint,1f},new float[4],null).filter(buttonArtwork,null);tintedButtons.put(key,source);}
        }
        graphics.drawImage(source,(int)x,(int)y,(int)(x+w),(int)(y+h),(int)(u0*source.getWidth()),(int)(v0*source.getHeight()),(int)(u1*source.getWidth()),(int)(v1*source.getHeight()),null);
    }
    public static void button(WButton button){
        rect(.27f,.24f,.18f,1f,button.x,button.y,button.width,button.height);
        rect(.15f,.14f,.11f,1f,button.x+1,button.y+1,button.width-2,button.height-2);
        button.text.moveTo(button.x+(button.width-button.text.getWidth(button.label))/2,button.y+(button.height-button.text.getHeight())/2+button.text.getAscent());
        button.text.paint(null,button.label,button.isEnabled()?.94f:.46f,button.isEnabled()?.88f:.46f,button.isEnabled()?.70f:.42f,1f);
    }
    public static final class ProbeFont extends TextFont {
        private final Font font;
        private int x,y;
        public ProbeFont(String name){font=new Font("Verdana",name.equals("bold")?Font.BOLD:Font.PLAIN,fontSize);}
        public void moveTo(int x,int y){this.x=x;this.y=y;}
        public int paint(Queue queue,String value,float r,float g,float b,float a){graphics.setFont(font);graphics.setColor(new Color(r,g,b,a));graphics.drawString(value,x,y);return getWidth(value);}
        private FontMetrics metrics(){return graphics.getFontMetrics(font);}
        public int getWidth(String value){return metrics().stringWidth(value);}
        public int getWidth(char[] value,int start,int length){return metrics().charsWidth(value,start,length);}
        public int getHeight(){return metrics().getHeight();}
        public int getAscent(){return metrics().getAscent();}
        public int getDescent(){return metrics().getDescent();}
        public int getLeading(){return metrics().getLeading();}
    }
}
