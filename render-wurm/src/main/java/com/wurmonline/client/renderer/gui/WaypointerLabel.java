package com.wurmonline.client.renderer.gui;
import org.waypoints.next.i18n.Messages;
import com.wurmonline.client.renderer.backend.Queue;
import com.wurmonline.client.renderer.gui.text.WaypointerFonts;
import org.chamomilo.wurm.ui.v1.UiColor;
/** Presentation text, with an explicit raw mode for client-supplied names. */
class WaypointerLabel extends WurmLabel {
    private String presentation="";
    private boolean translate;
    WaypointerLabel(String label){this(label,label,false,true);}
    WaypointerLabel(String label,String hover){this(label,hover,false,true);}
    WaypointerLabel(String label,String hover,boolean wrap){this(label,hover,wrap,true);}
    WaypointerLabel(String label,String hover,boolean wrap,boolean translate){super(translate?Messages.text(label):label,Messages.text(hover),wrap);this.translate=translate;presentation=translate?Messages.text(label):label;text=WaypointerFonts.body();textBold=WaypointerFonts.body(true);setSize(textWidth(),text.getHeight()+8);}
    @Override void setLabel(String label){presentation=translate?Messages.text(label):label;super.setLabel(presentation);}
    @Override void setLabel(String label,String hover){presentation=translate?Messages.text(label):label;super.setLabel(presentation,Messages.text(hover));}
    int textWidth(){return text.getWidth(presentation)+8;}
    @Override protected void renderComponent(Queue queue,float ignoredAlpha) {
        text.moveTo(x+4,y+(height-text.getHeight())/2+text.getAscent());
        text.paint(queue,presentation,UiColor.TEXT.red,UiColor.TEXT.green,UiColor.TEXT.blue,1f);
    }
}
