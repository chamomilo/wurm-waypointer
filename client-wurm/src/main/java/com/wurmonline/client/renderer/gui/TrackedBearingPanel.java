package com.wurmonline.client.renderer.gui;

import com.wurmonline.client.renderer.backend.Queue;
import org.waypoints.next.model.WaypointRecord;
import org.waypoints.next.tracking.BearingRegion;
import org.waypoints.next.i18n.Messages;

/** A small north-up uncertainty plot; it is deliberately separate from exact world markers. */
final class TrackedBearingPanel extends FlexComponent {
    private final BearingRegion region;
    TrackedBearingPanel(WaypointRecord record){super("target.bearing",0,0,190,132);sizeFlags=FIXED_WIDTH|FIXED_HEIGHT;region=new BearingRegion(record);}
    @Override protected void renderComponent(Queue queue,float alpha){
        fillRect(queue,.12f,.12f,.1f,1,x,y,width,height);
        int plot=90,cx=x+width/2,cy=y+58;
        for(int py=0;py<plot;py+=2)for(int px=0;px<plot;px+=2){
            double tx=region.centreX+(px-plot/2d)*region.radius*2/plot,ty=region.centreY+(py-plot/2d)*region.radius*2/plot;
            if(region.contains(tx,ty))fillRect(queue,.95f,.7f,.25f,.75f,cx-plot/2+px,cy-plot/2+py,2,2);
        }
        fillRect(queue,.4f,.9f,.4f,1,cx-2,cy-2,4,4);text.moveTo(x+4,y+text.getAscent()+2);text.paint(queue,"N",1,1,1,1);
        text.moveTo(x+4,y+height-22);text.paint(queue,Messages.format("{0} readings",region.readings()),1,1,1,1);
        text.moveTo(x+4,y+height-5);text.paint(queue,Messages.text("Approximate area"),1,1,1,1);
    }
}
