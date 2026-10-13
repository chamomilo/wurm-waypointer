package com.wurmonline.client.renderer.effects;

import com.wurmonline.client.game.World;
import com.wurmonline.client.renderer.Material;
import com.wurmonline.client.renderer.MaterialInstance;
import com.wurmonline.client.renderer.Matrix;
import com.wurmonline.client.renderer.backend.*;
import com.wurmonline.client.util.GLHelper;
import com.wurmonline.client.renderer.shaders.Program;
import com.wurmonline.client.renderer.shaders.ProgramBindings;
import org.waypoints.next.render.*;
import java.nio.FloatBuffer;
import java.util.List;
import java.util.Collections;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;

/** One cached native VBO, rendered only in the main surface pass. */
public final class TileHighlightOverlay implements WaypointLatePassParticipant {
    private static final ConcurrentLinkedQueue<TileHighlightOverlay> UPDATES=new ConcurrentLinkedQueue<TileHighlightOverlay>();
    private static final ConcurrentLinkedQueue<Retired> RETIRED=new ConcurrentLinkedQueue<Retired>();
    private static volatile long frame;
    private final World world;
    private final VertexBuffer vbo;
    private MaterialInstance material;
    private Program program;
    private ProgramBindings bindings;
    private boolean materialReady,queued;
    private final Matrix transform=new Matrix();
    private final int maximumTileCount;
    private List<TileHighlightPatch> pending;
    private volatile Uploaded uploaded;
    private int usedFloats;
    private volatile boolean alive=true;
    public TileHighlightOverlay(World world,int maximumTileCount) {
        if(maximumTileCount<1)throw new IllegalArgumentException("Tile capacity must be positive");
        this.world=world;this.maximumTileCount=maximumTileCount;
        // Native resize can retain a retired VAO when queue construction runs
        // outside the GL thread. Keep one fixed buffer for this overlay's life.
        int vertices=maximumTileCount*(TileHighlightGeometry.FILL_VERTICES+TileHighlightPatch.LAYERS*TileHighlightGeometry.EDGE_VERTICES)+3;
        vbo=VertexBuffer.create(VertexBuffer.Usage.EFFECT,vertices,true,false,false,true,false,0,0,true,false);
        WaypointLatePassBridge.register(this);
    }
    public synchronized void update(List<TileHighlightPatch> tiles){
        if(tiles==null||tiles.size()>maximumTileCount)throw new IllegalArgumentException("Tile capacity exceeded");
        if(!alive||tiles.equals(pending))return;
        pending=Collections.unmodifiableList(new ArrayList<TileHighlightPatch>(tiles));
        if(!queued){queued=true;UPDATES.add(this);}
    }
    public synchronized void close(){if(!alive)return;alive=false;WaypointLatePassBridge.unregister(this);pending=null;RETIRED.add(new Retired(vbo,transform,frame+2));}
    /** Called on the GL thread before Wurm launches any world queue-building jobs. */
    public static void flushUploads(){if(!Backend.isGLThread())return;TileHighlightOverlay owner;while((owner=UPDATES.poll())!=null)owner.upload();}
    /** Keep resources alive until all queued primitives have finished using them. */
    public static void finishFrame(){
        if(!Backend.isGLThread())return;frame++;
        Retired old;while((old=RETIRED.peek())!=null&&old.after<=frame){RETIRED.poll();old.vbo.delete();old.transform.delete();}
    }
    private synchronized void upload(){
        queued=false;if(!alive||pending==null||uploaded!=null&&pending.equals(uploaded.tiles))return;
        if(pending.isEmpty()){uploaded=new Uploaded(pending,0,0);return;}
        if(!materialReady){material=GLHelper.useDeferredShading()?Material.load("material.simple").instance():null;if(material!=null){program=material.getProgram();bindings=material.getProgramBindings();}materialReady=true;}
        int count=pending.size(),layers=0;for(TileHighlightPatch tile:pending)layers+=tile.layerCount();
        FloatBuffer data=vbo.lock();
        try {
            data.clear();
            for(TileHighlightPatch tile:pending)TileHighlightGeometry.fill(data,tile);
            for(TileHighlightPatch tile:pending)TileHighlightGeometry.edges(data,tile);
            for(int i=0;i<21;i++)data.put(0); // Transparent state primer.
            int nextUsed=data.position();while(data.position()<usedFloats)data.put(0);
            // Upload only live geometry, rather than the full worst-case buffer.
            usedFloats=nextUsed;data.limit(nextUsed);data.rewind();
        }finally{vbo.unlock();}
        uploaded=new Uploaded(pending,count,layers);
    }
    @Override public boolean isLatePassAlive(){return alive;}
    @Override public void renderInLateWorldPass(Queue queue) {
        Uploaded snapshot=uploaded;
        if(!alive||snapshot==null||snapshot.count==0||world.getPlayerLayer()<0||queue.isReflection())return;
        int count=snapshot.count,layers=snapshot.layers;
        transform.identity().setTranslation(-world.getRenderOriginX(),0,-world.getRenderOriginY());
        // Prime Wurm's cached effect state before the real alpha-blended primitives.
        queue(queue,count*TileHighlightGeometry.FILL_VERTICES+layers*TileHighlightGeometry.EDGE_VERTICES,1,Primitive.TestFunc.LESSEQUAL,Primitive.BlendMode.ADD);
        queue(queue,0,count*2,Primitive.TestFunc.LESSEQUAL,Primitive.BlendMode.ALPHABLEND);
        // Edge ribbons remain legible through grass; they never write terrain depth.
        queue(queue,count*TileHighlightGeometry.FILL_VERTICES,layers*8,Primitive.TestFunc.ALWAYS,Primitive.BlendMode.ALPHABLEND);
    }
    private void queue(Queue queue,int offset,int triangles,Primitive.TestFunc depth,Primitive.BlendMode blend) {
        Primitive p=queue.reservePrimitive();p.copyStateFrom(RenderState.RENDERSTATE_ALPHABLEND);
        p.blendmode=blend;p.depthtest=depth;p.depthwrite=false;p.type=Primitive.Type.TRIANGLES;
        p.twosided=true;p.nolight=true;p.nofog=true;WaypointWorldTexture.bindWhite(p);
        if(material!=null){p.materialInstance=material;p.program=program;p.bindings=bindings;}
        p.vertex=vbo;p.index=null;p.offset=offset;p.num=triangles;queue.queue(p,transform);
    }
    private static final class Uploaded {
        final List<TileHighlightPatch> tiles;final int count,layers;
        Uploaded(List<TileHighlightPatch> tiles,int count,int layers){this.tiles=tiles;this.count=count;this.layers=layers;}
    }
    private static final class Retired {
        final VertexBuffer vbo;final Matrix transform;final long after;
        Retired(VertexBuffer vbo,Matrix transform,long after){this.vbo=vbo;this.transform=transform;this.after=after;}
    }
}
