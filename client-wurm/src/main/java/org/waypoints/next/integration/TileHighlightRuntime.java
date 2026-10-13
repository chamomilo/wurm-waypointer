package org.waypoints.next.integration;

import com.wurmonline.client.game.World;
import com.wurmonline.client.renderer.effects.TileHighlightOverlay;
import org.waypoints.next.render.TileHighlightPatch;
import org.waypoints.next.tiles.TileHighlightSettings;
import java.util.*;

/** HUD-owned bounded sampling, immediate switch application and world/session cleanup. */
final class TileHighlightRuntime {
    private TileHighlightSettings settings=new TileHighlightSettings(new Properties());
    private World owner;
    private TileHighlightOverlay overlay;
    private long nextSample;
    private int playerX=Integer.MIN_VALUE,playerY=Integer.MIN_VALUE;
    void configure(Properties values){TileHighlightSettings next=new TileHighlightSettings(values);boolean radiusChanged=settings.radius()!=next.radius();settings=next;nextSample=0;if(radiusChanged||!settings.enabled())clear();}
    void tick(World world,long now) {
        if(world!=owner){clear();owner=world;}
        if(world==null||!settings.enabled()||world.getPlayerLayer()<0){if(overlay!=null){overlay.close();overlay=null;}nextSample=0;return;}
        int px=world.getPlayerCurrentTileX(),py=world.getPlayerCurrentTileY();
        if(now<nextSample&&px==playerX&&py==playerY)return;
        playerX=px;playerY=py;nextSample=now+500;
        List<TileHighlightPatch> tiles=new ArrayList<TileHighlightPatch>();
        int radius=settings.radius(),size=world.getWorldSize();
        for(int y=Math.max(0,py-radius);y<=Math.min(size-2,py+radius);y++)for(int x=Math.max(0,px-radius);x<=Math.min(size-2,px+radius);x++) {
            TileHighlightPatch tile=WurmTileHighlightSampler.sample(world.getNearTerrainBuffer(),px,py,size,x,y,settings);
            if(tile!=null)tiles.add(tile);
        }
        if(tiles.isEmpty()){if(overlay!=null)overlay.update(Collections.<TileHighlightPatch>emptyList());return;}
        if(overlay==null)overlay=new TileHighlightOverlay(world,(radius*2+1)*(radius*2+1));
        overlay.update(Collections.unmodifiableList(tiles));
    }
    void clear(){if(overlay!=null)overlay.close();overlay=null;owner=null;nextSample=0;playerX=playerY=Integer.MIN_VALUE;}
}
