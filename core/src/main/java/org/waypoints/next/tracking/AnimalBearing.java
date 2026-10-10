package org.waypoints.next.tracking;

import java.util.Locale;

/** One vanilla relative sector plus distance band, with the actual reading origin. */
public final class AnimalBearing {
    public final double originX,originY,bearing,minimum,maximum,halfWidth;
    private AnimalBearing(double x,double y,double b,double min,double max){this(x,y,b,min,max,22.5d);}
    private AnimalBearing(double x,double y,double b,double min,double max,double half){originX=x;originY=y;bearing=(b%360+360)%360;minimum=min;maximum=max;halfWidth=half;}
    public static AnimalBearing parse(String text,String name,double x,double y,double facing) {
        if(text==null||name==null)return null;
        if(text.equals("You are practically standing on the "+name+"!"))return new AnimalBearing(x,y,0,0,1,180);
        if(!text.startsWith("The "+name+" "))return null;
        String s=text.substring(name.length()+4).toLowerCase(Locale.ROOT);
        double offset;
        if(s.contains("ahead of you to the right"))offset=45;
        else if(s.contains("behind you to the right"))offset=135;
        else if(s.contains("behind you to the left"))offset=225;
        else if(s.contains("ahead of you to the left"))offset=315;
        else if(s.contains("in front of you"))offset=0;
        else if(s.contains("to the right of you"))offset=90;
        else if(s.contains("behind you"))offset=180;
        else if(s.contains("to the left of you"))offset=270;
        else return null;
        String[] bands={"very far away","pretty far away","far away","rather a long distance away","quite some distance away","some distance away","fairly close by","pretty close by","very close","a stone's throw away","a few steps away"};
        double[] min={2000,500,1000,200,50,20,10,6,4,1,0};
        double[] max={65536,1000,2000,500,200,50,20,10,6,4,1};
        for(int i=0;i<bands.length;i++)if(s.contains(bands[i]))return new AnimalBearing(x,y,Math.round(facing/45d)*45d+offset,min[i],max[i]);
        return null;
    }
}
