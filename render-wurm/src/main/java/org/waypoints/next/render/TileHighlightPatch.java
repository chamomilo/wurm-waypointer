package org.waypoints.next.render;

/** One received surface tile with independent colours; -1 means no match. */
public final class TileHighlightPatch {
    public static final int LAYERS=4;
    public final int x,y,nonRideableRgb,flowerRgb,harvestableRgb,overagedRgb;
    public final float nw,ne,se,sw;
    public TileHighlightPatch(int x,int y,int nonRideableRgb,int flowerRgb,int harvestableRgb,int overagedRgb,float nw,float ne,float se,float sw) {
        this.x=x;this.y=y;this.nonRideableRgb=nonRideableRgb;this.flowerRgb=flowerRgb;
        this.harvestableRgb=harvestableRgb;this.overagedRgb=overagedRgb;
        this.nw=nw;this.ne=ne;this.se=se;this.sw=sw;
    }
    public int rgb(int layer){switch(layer){case 0:return nonRideableRgb;case 1:return flowerRgb;case 2:return harvestableRgb;case 3:return overagedRgb;default:throw new IllegalArgumentException("Tile layer: "+layer);}}
    public int layerCount(){int count=0;for(int i=0;i<LAYERS;i++)if(rgb(i)>=0)count++;return count;}
    @Override public boolean equals(Object other) {
        if(this==other)return true;if(!(other instanceof TileHighlightPatch))return false;TileHighlightPatch t=(TileHighlightPatch)other;
        return x==t.x&&y==t.y&&nonRideableRgb==t.nonRideableRgb&&flowerRgb==t.flowerRgb&&harvestableRgb==t.harvestableRgb&&overagedRgb==t.overagedRgb
                &&Float.compare(nw,t.nw)==0&&Float.compare(ne,t.ne)==0&&Float.compare(se,t.se)==0&&Float.compare(sw,t.sw)==0;
    }
    @Override public int hashCode(){return java.util.Objects.hash(x,y,nonRideableRgb,flowerRgb,harvestableRgb,overagedRgb,nw,ne,se,sw);}
}
