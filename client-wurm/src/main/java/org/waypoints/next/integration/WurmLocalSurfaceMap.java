package org.waypoints.next.integration;

import com.wurmonline.client.game.NearTerrainDataBuffer;
import com.wurmonline.client.game.World;
import com.wurmonline.mesh.Tiles;
import org.waypoints.next.map.LocalSurfaceMap;
import org.waypoints.next.model.ServerIdentity;
import java.util.List;

/** Reads already received tiles on the HUD thread; never requests or downloads terrain. */
final class WurmLocalSurfaceMap {
    private final LocalSurfaceMap map=new LocalSurfaceMap();
    private World owner;
    private String endpoint="";
    private final LocalSurfaceMap.Source source=new LocalSurfaceMap.Source(){
        @Override public LocalSurfaceMap.Tile read(int x,int y){return sample(owner,x,y);}
    };

    void tick(World world,ServerIdentity identity,long nowMillis) {
        String key=identity==null?"":identity.getEndpointFingerprint();
        if(world!=owner||!key.equals(endpoint)){clear();owner=world;endpoint=key;}
        if(world==null||identity==null||world.getPlayerLayer()<0)return;
        map.refresh(nowMillis,world.getWorldSize(),world.getPlayerCurrentTileX(),world.getPlayerCurrentTileY(),source);
    }
    List<LocalSurfaceMap.Chunk> snapshot(){return map.snapshot();}
    String describe(int x,int y){return map.describe(x,y);}
    void clear(){map.clear();owner=null;endpoint="";}

    static LocalSurfaceMap.Tile sample(World world,int x,int y) {
        return world==null?null:sample(world.getNearTerrainBuffer(),world.getPlayerCurrentTileX(),
                world.getPlayerCurrentTileY(),world.getWorldSize(),x,y);
    }
    static LocalSurfaceMap.Tile sample(NearTerrainDataBuffer near,int playerX,int playerY,int worldSize,int x,int y) {
        if(near==null||x<0||y<0||x>=worldSize||y>=worldSize
                ||!WurmSurfaceTileDescription.withinLiveRange(playerX,playerY,x,y))return null;
        try {
            if(!near.isValid(x*4f+2,y*4f+2))return null;
            Tiles.Tile type=Tiles.getTile(near.getRawType(x,y));
            if(type==null||type.getColor()==null)return null;
            float ground=near.getHeight(x,y);
            if(x+1<worldSize)ground=Math.min(ground,near.getHeight(x+1,y));
            if(y+1<worldSize)ground=Math.min(ground,near.getHeight(x,y+1));
            if(x+1<worldSize&&y+1<worldSize)ground=Math.min(ground,near.getHeight(x+1,y+1));
            float water=near.getWaterHeight(x,y)/10f;
            boolean wet=Float.isFinite(ground)&&Float.isFinite(water)&&ground+.001f<water;
            int rgb=wet?0x373f6f:type.getColor().getRGB();
            String description=WurmSurfaceTileDescription.tileName(near.getRawType(x,y),near.getData(x,y));
            return new LocalSurfaceMap.Tile(rgb,wet?"Water ("+description+")":description);
        }catch(RuntimeException receiving){return null;}
    }
}
