package com.wurmonline.client.renderer.gui;

import com.wurmonline.client.renderer.backend.Queue;
import com.wurmonline.client.renderer.gui.text.ChamomiloUiV1Fonts;
import com.wurmonline.client.renderer.gui.text.TextFont;
import java.awt.Rectangle;
import java.util.Locale;
import org.chamomilo.wurm.ui.v1.*;

/** Explicit consumer coordination for the SDK's single-button native wrapper. */
final class WaypointerButtonGroup {
    final String id;
    final UiDensity density;
    final int height, fontPixels, baseline;
    private final boolean uppercase;
    private final TextFont normal, bold;
    private int lineHeight, lineAscent;

    WaypointerButtonGroup(String id, UiDensity density, int height, String[] captions, int[] widths) {
        this(id,density,height,16,false,captions,widths);
    }
    WaypointerButtonGroup(String id, UiDensity density, int height, int ceiling, boolean uppercase, String[] captions, int[] widths) {
        this(id,density,height,ceiling,uppercase,captions,widths,UiScale.BASE,true);
    }
    static WaypointerButtonGroup compact(String id,int height,String[] captions,int[] widths) {
        return new WaypointerButtonGroup(id,UiDensity.HIGH,height,20,false,captions,widths,new UiScale(.5f),false);
    }
    static WaypointerButtonGroup painted(String id,int height,String[] captions,int[] widths) {
        return new WaypointerButtonGroup(id,UiDensity.HIGH,height,16,false,captions,widths,UiScale.BASE,false);
    }
    static int compactWidth(String caption,int height) {
        UiButtonLayout policy=UiButtonLayout.fit(new String[]{"Ag"},1024,height,UiDensity.HIGH,new UiScale(.5f),true);
        int span=0;
        for(boolean weight:new boolean[]{false,true}) {
            Rectangle ink=UiTypography.ink(caption,20,weight,UiDensity.HIGH);
            span=Math.max(span,Math.max(UiTypography.width(caption,20,weight,UiDensity.HIGH),ink.x+ink.width)-Math.min(0,ink.x));
        }
        return span+2*policy.insetX;
    }
    private WaypointerButtonGroup(String id, UiDensity density, int height, int ceiling, boolean uppercase, String[] captions, int[] widths,UiScale scale,boolean nativeButton) {
        if(captions.length==0||captions.length!=widths.length)throw new IllegalArgumentException("Group members/widths");
        this.id=id;this.density=density;this.height=height;this.uppercase=uppercase;
        // The public custom-font wrapper reserves 12 px per side. Low additionally
        // retains its 18 px safe inset. Account for this before selecting a size.
        // Reuse the kit's safe insets; actual ink and native custom-font metrics
        // below also support the deliberately compact 24 px table actions.
        int policyHeight=Math.max(height,scale.pixels(density==UiDensity.HIGH?28:32));
        UiButtonLayout policy=UiButtonLayout.fit(new String[]{"Ag"},1024,policyHeight,density,scale,true);
        int insetX=nativeButton?Math.max(12,policy.insetX):policy.insetX,insetY=policy.insetY;
        int maximum=Math.min(ceiling,density==UiDensity.LOW?Math.min(32,Math.round(height*.45f)):128);
        int chosen=0, top=0, bottom=0;
        int[] sizes=UiTypography.sizes();
        for(int index=sizes.length-1;index>=0;index--){
            int size=sizes[index];if(size>maximum||size<(nativeButton?12:8))continue;
            int candidateTop=0,candidateBottom=0;
            boolean fits=true;
            for(int member=0;member<captions.length;member++)for(boolean weight:new boolean[]{false,true}){
                String caption=display(captions[member]);
                Rectangle ink=UiTypography.ink(caption,size,weight,density);
                int span=Math.max(UiTypography.width(caption,size,weight,density),ink.x+ink.width)-Math.min(0,ink.x);
                fits&=span<=widths[member]-2*insetX;
                candidateTop=Math.min(candidateTop,ink.y);candidateBottom=Math.max(candidateBottom,ink.y+ink.height);
            }
            if(fits&&candidateBottom-candidateTop<=height-2*insetY){chosen=size;top=candidateTop;bottom=candidateBottom;break;}
        }
        if(chosen==0)throw new IllegalArgumentException("Increase group geometry: "+id);
        fontPixels=chosen;baseline=(height-(bottom-top))/2-top;
        lineHeight=height-8;lineAscent=baseline-4;
        normal=ChamomiloUiV1Fonts.caption(chosen,false,density);
        bold=ChamomiloUiV1Fonts.caption(chosen,true,density);
    }
    void paintCaption(Queue queue,String caption,boolean hover,int x,int y,int width,int shift,UiColor color) {
        String value=display(caption);
        TextFont font=hover?bold:normal;
        Rectangle ink=UiTypography.ink(value,fontPixels,hover,density);
        int left=Math.min(0,ink.x),right=Math.max(font.getWidth(value),ink.x+ink.width);
        font.moveTo(x+(width-(right-left))/2-left+shift,y+baseline+shift);
        font.paint(queue,value,color.red,color.green,color.blue,1f);
    }

    void apply(ChamomiloUiV1Button button,int width){
        button.setDensity(density);button.setCaptionFonts(new CaptionFont(normal,this,button),new CaptionFont(bold,this,button));
        button.resize(width,height);button.setFixedWidth(true);
    }
    static int fontPixels(TextFont font){return ((CaptionFont)font).group.fontPixels;}
    static String id(TextFont font){return ((CaptionFont)font).group.id;}
    static void active(WButton button,boolean active){
        ((CaptionFont)button.text).active=active;((CaptionFont)button.textBold).active=active;
    }
    private String display(String caption){return uppercase?caption.toUpperCase(Locale.ROOT):UiTypography.caption(caption,density);}
    static int width(String caption,int pixels,UiDensity density,boolean uppercase){
        String value=uppercase?caption.toUpperCase(Locale.ROOT):UiTypography.caption(caption,density);int span=0;
        for(boolean bold:new boolean[]{false,true}){
            Rectangle ink=UiTypography.ink(value,pixels,bold,density);
            span=Math.max(span,Math.max(UiTypography.width(value,pixels,bold,density),ink.x+ink.width)-Math.min(0,ink.x));
        }
        return span+2*(density==UiDensity.LOW?18:12);
    }
    static void peers(String id,UiDensity density,int height,WButton... buttons){
        String[] captions=new String[buttons.length];int[] widths=new int[buttons.length];
        for(int i=0;i<buttons.length;i++){captions[i]=buttons[i].getLabel();widths[i]=Math.max(buttons[i].width,width(captions[i],16,density,false));}
        WaypointerButtonGroup group=new WaypointerButtonGroup(id,density,height,captions,widths);
        for(int i=0;i<buttons.length;i++)group.apply((ChamomiloUiV1Button)buttons[i],widths[i]);
    }

    /** Reuse the native atlas; only caption block metrics belong to the group. */
    private static final class CaptionFont extends TextFont {
        private final TextFont delegate;
        private final WaypointerButtonGroup group;
        private final WButton owner;
        private boolean active;
        CaptionFont(TextFont delegate,WaypointerButtonGroup group,WButton owner){this.delegate=delegate;this.group=group;this.owner=owner;}
        public void moveTo(int x,int y){delegate.moveTo(x,y);}
        public int paint(Queue q,String value,float r,float g,float b,float a){
            if(active&&owner.isEnabled()){
                ChamomiloUiV1Canvas canvas=new ChamomiloUiV1Canvas(owner).begin(q);
                canvas.fill(UiColor.rgb(0x61bd70),.18f,owner.x+5,owner.y+5,owner.width-10,owner.height-10);
                canvas.fill(UiColor.rgb(0x84e397),1f,owner.x+5,owner.y+owner.height-4,owner.width-10,2);
                if(!owner.hovered){r=.52f;g=.89f;b=.59f;}
            }
            return delegate.paint(q,group.display(value),r,g,b,1f);
        }
        public int getWidth(String value){return delegate.getWidth(group.display(value));}
        public int getWidth(char[] value,int start,int count){return getWidth(new String(value,start,count));}
        public int getHeight(){return group.lineHeight;}
        public int getAscent(){return group.lineAscent;}
        public int getDescent(){return getHeight()-getAscent();}
        public int getLeading(){return 0;}
    }
}
