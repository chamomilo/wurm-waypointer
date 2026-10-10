package com.wurmonline.client.renderer.gui;

import com.wurmonline.client.renderer.backend.Queue;
import com.wurmonline.client.renderer.gui.text.TextFont;


import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.*;
import java.util.*;
import javax.imageio.ImageIO;

/** Offscreen preview uses the production component tree and paint coordinates. */
public final class WaypointerLayoutProbe {
    private static BufferedImage canvas;
    private static final Map<String,Point> paintedLabels = new HashMap<String,Point>();
    private static Graphics2D graphics;
    private static final Map<String, BufferedImage> kitArtwork = new HashMap<String, BufferedImage>();
    private static final Deque<Shape> clips = new LinkedList<Shape>();
    private static int fontSize = 12;
    private static final Set<String> paintedFontFamilies=new HashSet<String>();



    public static Object allocate(Class<?> type) throws Exception {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
        return ((sun.misc.Unsafe) field.get(null)).allocateInstance(type);
    }
    public static void preparePreview(String frame,String buttons,int size)throws Exception{
        fontSize=size;
        canvas=new BufferedImage(2200,1400,BufferedImage.TYPE_INT_ARGB);graphics=canvas.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        com.wurmonline.client.options.RangeOption range=(com.wurmonline.client.options.RangeOption)allocate(com.wurmonline.client.options.RangeOption.class);
        field(com.wurmonline.client.options.RangeOption.class,"defaultValue").setInt(range,12);
        field(com.wurmonline.client.options.RangeOption.class,"value").setInt(range,size);
        field(com.wurmonline.client.options.Options.class,"fontSizeDefault").set(null,range);
        fixtureHud(2200,1400);
    }
    public static void savePreview(WurmComponent component,String file)throws Exception{
        if(!clips.isEmpty())throw new AssertionError("Unbalanced native clipping stack");
        paintedLabels.clear();
        paintedFontFamilies.clear();
        graphics.setClip(null);
        rect(.025f,.023f,.018f,1,0,0,canvas.getWidth(),canvas.getHeight());component.render(null,1);
        for(String family:paintedFontFamilies)check(family.startsWith("Alegreya Sans"),"Every painted Waypointer caption uses the bundled kit: "+family+", preview="+file);
        ImageIO.write(canvas.getSubimage(component.x,component.y,component.width,component.height),"png",new File(file));
    }
    static Point paintedLabel(String value) { return paintedLabels.get(value); }
    static void clearPaintedLabel(String value) { paintedLabels.remove(value); }
    static void verifyStableOpacity(WurmComponent component) {
        NativeUiRenderFixture.beginAlphaFrame(); component.render(null,1f);
        java.util.List<Float> expected=NativeUiRenderFixture.endAlphaFrame();
        check(expected.size()>50,"Opacity audit includes window chrome, controls and captions");
        for(float alpha:new float[]{.15f,.85f,.35f,1f}) {
            NativeUiRenderFixture.beginAlphaFrame(); component.render(null,alpha);
            check(expected.equals(NativeUiRenderFixture.endAlphaFrame()),"HUD transition alpha must not change window opacity: "+alpha);
        }
    }
    static void saveFullMapChrome(WorldMap map,Method frame,Method plate,String file)throws Exception{
        graphics.setClip(null);rect(.025f,.023f,.018f,1,0,0,canvas.getWidth(),canvas.getHeight());
        rect(55f/255,63f/255,111f/255,1,map.x+3,map.y+21,920,620);
        frame.invoke(null,map,null,map.x+3,map.y+21);
        String title="Map of: Sklotopolis-Novus";
        plate.invoke(null,map,null,map.x+4,map.y,map.textBold.getWidth(title)+48,title);
        paintedLabels.clear();
        Class<?> stateType=Class.forName(ServerMapWindowBridge.class.getName()+"$State");
        Constructor<?> constructor=stateType.getDeclaredConstructor(String.class,org.waypoints.next.map.MapViewport.class,org.waypoints.next.map.MapOverlayVisibility.class);constructor.setAccessible(true);
        Object state=constructor.newInstance("sklotopolis-novus",new org.waypoints.next.map.MapViewport(4096,4096,920,620,2000,2000),new org.waypoints.next.map.MapOverlayVisibility(true,true,true));
        for(String methodName:new String[]{"drawAllMapsButton","drawZoomFactorButton","drawCenterButton","drawNavigationLineButton","drawMiniMapButton","drawLayerButtons","drawSearchButton","drawCloseButton"}){
            Method method=ServerMapWindowBridge.class.getDeclaredMethod(methodName,WorldMap.class,Queue.class,stateType,int.class,int.class);method.setAccessible(true);
            method.invoke(null,map,null,state,map.x+3,map.y+21);
        }
        WaypointerButtonGroup group=ServerMapWindowBridge.toolbarGroup();
        check(group.fontPixels>=12,"Main map toolbar remains readable: "+org.waypoints.next.i18n.Messages.language());
        String[] captions={"ALL MAPS ON SERVER","Zoom speed: 1X","CENTER","NAV LINE","MINI MAP","DEEDS","ROADS","MARKS"};
        int[] starts={22,226,358,434,526,618,686,754},widths={200,128,72,88,88,64,64,64};
        for(int i=0;i<captions.length;i++){
            String caption=org.waypoints.next.i18n.Messages.text(captions[i]);Point point=paintedLabels.get(caption);
            check(point!=null,"Main map paints full localized caption: "+caption);
            check(point.y==map.y+21+25+group.baseline,"Map controls share a baseline: "+caption);
            for(boolean bold:new boolean[]{false,true}){
                Rectangle ink=org.chamomilo.wurm.ui.v1.UiTypography.ink(caption,group.fontPixels,bold,org.chamomilo.wurm.ui.v1.UiDensity.HIGH);
                int span=Math.max(org.chamomilo.wurm.ui.v1.UiTypography.width(caption,group.fontPixels,bold,org.chamomilo.wurm.ui.v1.UiDensity.HIGH),ink.x+ink.width)-Math.min(0,ink.x);
                check(span<=widths[i]-8,"Main map caption fits both weights: "+caption);
            }
            if(i+1<starts.length)check(starts[i]+widths[i]+4==starts[i+1],"Map toolbar spacing");
        }
        int water=canvas.getRGB(map.x+50,map.y+70);
        for(int[] point:new int[][]{{10,31},{916,31},{10,631},{916,631}})
            check(canvas.getRGB(map.x+point[0],map.y+point[1])!=water,"All four map frame corners are visible");
        ImageIO.write(canvas.getSubimage(map.x,map.y,map.width,map.height),"png",new File(file));
    }
    public static void input(WurmInputField input)throws Exception{rect(.17f,.17f,.15f,1,input.x,input.y,input.width,input.height);input.text.moveTo(input.x+4,input.y+field(WurmInputField.class,"maxHeight").getInt(input));input.text.paint(null,input.getText().isEmpty()?input.prompt:input.getText(),.95f,.93f,.85f,1);}
    public static void dropdown(WurmDropDown dropdown)throws Exception{String[] options=(String[])field(WurmDropDown.class,"options").get(dropdown);rect(.2f,.2f,.17f,1,dropdown.x,dropdown.y,dropdown.width,dropdown.height);dropdown.text.moveTo(dropdown.x+4,dropdown.y+dropdown.text.getAscent()+4);dropdown.text.paint(null,options[dropdown.getValue()],.95f,.93f,.85f,1);}
    private static HeadsUpDisplay fixtureHud(int w, int h) throws Exception {
        HeadsUpDisplay hud = (HeadsUpDisplay) allocate(HeadsUpDisplay.class);
        field(HeadsUpDisplay.class,"width").setInt(hud,w); field(HeadsUpDisplay.class,"height").setInt(hud,h);
        field(HeadsUpDisplay.class,"components").set(hud,new ArrayList<WurmComponent>());
        field(HeadsUpDisplay.class,"popups").set(hud,new ArrayList<WurmPopup>());
        field(HeadsUpDisplay.class,"dropdownPopups").set(hud,new ArrayList<WurmDropdownPopup>());
        WurmComponent.SCREEN_WIDTH=w; WurmComponent.SCREEN_HEIGHT=h; WurmComponent.hud=hud;
        field(HeadsUpDisplay.class,"mainMenu").set(hud,new MainMenu());
        return hud;
    }
    public static com.wurmonline.client.options.MultiOption guiSkin() throws Exception {
        com.wurmonline.client.options.MultiOption skin=(com.wurmonline.client.options.MultiOption)allocate(com.wurmonline.client.options.MultiOption.class);
        field(com.wurmonline.client.options.MultiOption.class,"options").set(skin,new String[]{"Default"});
        return skin;
    }
    private static Field field(Class<?> type,String name) throws Exception {Field f=type.getDeclaredField(name);f.setAccessible(true);return f;}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static boolean clip(int x,int y,int w,int h){clips.push(graphics.getClip()==null?new Rectangle(0,0,canvas.getWidth(),canvas.getHeight()):graphics.getClip());graphics.clipRect(x,y,w,h);return w>0&&h>0;}
    public static void unclip(){graphics.setClip(clips.pop());}
    public static void rect(float r,float g,float b,float a,int x,int y,int w,int h){graphics.setColor(new Color(r,g,b,a));graphics.fillRect(x,y,w,h);}
    public static void texture(com.wurmonline.client.resources.textures.Texture texture,float tint,float x,float y,float w,float h,float u0,float v0,float u1,float v1){
        throw new AssertionError("Expected SDK primitive texture routing");
    }
    public static boolean kitTexture(String resource, float tint, float alpha, int x, int y,
                                     int w, int h, float u0, float v0, float u1, float v1) throws Exception {
        BufferedImage source = kitArtwork.get(resource);
        if (source == null) {
            source = ImageIO.read(WaypointerLayoutProbe.class.getResource(resource));
            kitArtwork.put(resource, source);
        }
        source=new java.awt.image.RescaleOp(new float[]{tint,tint,tint,1f},new float[4],null).filter(source,null);
        Composite previous = graphics.getComposite();
        graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        graphics.drawImage(source, x, y, x + w, y + h, Math.round(u0 * source.getWidth()),
                Math.round(v0 * source.getHeight()), Math.round(u1 * source.getWidth()),
                Math.round(v1 * source.getHeight()), null);
        graphics.setComposite(previous);
        return true;
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
        public ProbeFont(String name){font=new Font("Verdana",name.equals("bold")||name.equals("compactBold")?Font.BOLD:Font.PLAIN,name.equals("compactBold")||name.equals("staticSizeFont")?12:fontSize);}
        public ProbeFont(String name,int factor){font=new Font("Verdana",name.equals("bold")?Font.BOLD:Font.PLAIN,fontSize*factor);}
        public ProbeFont(Font font){this.font=font;}
        public int fontPixels(){return font.getSize();}
        public Font awtFont(){return font;}
        public void moveTo(int x,int y){this.x=x;this.y=y;}
        public int paint(Queue queue,String value,float r,float g,float b,float a){NativeUiRenderFixture.recordAlpha(a);paintedLabels.put(value,new Point(x,y));paintedFontFamilies.add(font.getFamily());graphics.setFont(font);graphics.setColor(new Color(r,g,b,a));graphics.drawString(value,x,y);return getWidth(value);}
        private FontMetrics metrics(){return graphics.getFontMetrics(font);}
        public int getWidth(String value){return getWidth(value.toCharArray(),0,value.length());}
        public int getWidth(char[] value,int start,int length){int width=0;for(int i=start;i<start+length;i++)width+=metrics().charWidth(value[i]);return width;}
        public int getHeight(){return metrics().getHeight();}
        public int getAscent(){return metrics().getAscent();}
        public int getDescent(){return metrics().getDescent();}
        public int getLeading(){return metrics().getLeading();}
    }
}
