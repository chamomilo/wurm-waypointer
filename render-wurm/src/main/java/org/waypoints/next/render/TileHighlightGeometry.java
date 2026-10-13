package org.waypoints.next.render;

import java.nio.FloatBuffer;

/** Raised fill and separate nested frames for every matching tile rule. */
public final class TileHighlightGeometry {
    public static final float LIFT=.5f;
    public static final int FILL_VERTICES=6,EDGE_VERTICES=24;
    private static final float EDGE=.08f/4f;
    private static final float FRAME_GAP=.18f/4f;
    private TileHighlightGeometry(){}
    public static void fill(FloatBuffer out,TileHighlightPatch tile) {
        int rgb=-1;for(int i=0;i<TileHighlightPatch.LAYERS;i++)if(tile.rgb(i)>=0)rgb=tile.rgb(i);
        float inset=(tile.layerCount()-1)*FRAME_GAP+EDGE,far=1-inset;
        // One interior fill avoids blending several full-tile colours together.
        quad(out,tile,rgb,inset,inset,far,inset,far,far,inset,far,.16f);
    }
    public static void edges(FloatBuffer out,TileHighlightPatch tile) {
        int rank=0;
        for(int layer=0;layer<TileHighlightPatch.LAYERS;layer++) {
            int rgb=tile.rgb(layer);if(rgb<0)continue;
            float near=rank++*FRAME_GAP,far=1-near;
            quad(out,tile,rgb,near,near,far,near,far,near+EDGE,near,near+EDGE,.85f);
            quad(out,tile,rgb,far-EDGE,near+EDGE,far,near+EDGE,far,far,far-EDGE,far,.85f);
            quad(out,tile,rgb,near,far-EDGE,far-EDGE,far-EDGE,far-EDGE,far,near,far,.85f);
            quad(out,tile,rgb,near,near+EDGE,near+EDGE,near+EDGE,near+EDGE,far-EDGE,near,far-EDGE,.85f);
        }
    }
    private static void quad(FloatBuffer out,TileHighlightPatch t,int rgb,float ax,float ay,float bx,float by,float cx,float cy,float dx,float dy,float alpha) {
        vertex(out,t,rgb,ax,ay,alpha);vertex(out,t,rgb,bx,by,alpha);vertex(out,t,rgb,cx,cy,alpha);
        vertex(out,t,rgb,ax,ay,alpha);vertex(out,t,rgb,cx,cy,alpha);vertex(out,t,rgb,dx,dy,alpha);
    }
    private static void vertex(FloatBuffer out,TileHighlightPatch t,int rgb,float x,float y,float alpha) {
        // Match the native terrain's NW-SE diagonal, including nonplanar corners.
        float height=x>=y?t.nw+(t.ne-t.nw)*x+(t.se-t.ne)*y:t.nw+(t.se-t.sw)*x+(t.sw-t.nw)*y;
        out.put(t.x*4f+x*4f).put(height+LIFT).put(t.y*4f+y*4f);
        out.put(((rgb>>16)&255)/255f).put(((rgb>>8)&255)/255f).put((rgb&255)/255f).put(alpha);
    }
}
