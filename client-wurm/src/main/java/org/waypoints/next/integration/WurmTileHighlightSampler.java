package org.waypoints.next.integration;

import com.wurmonline.client.game.NearTerrainDataBuffer;
import com.wurmonline.mesh.GrassData;
import com.wurmonline.mesh.FieldData;
import com.wurmonline.mesh.FoliageAge;
import com.wurmonline.mesh.TreeData;
import com.wurmonline.mesh.Tiles;
import org.waypoints.next.render.TileHighlightPatch;
import org.waypoints.next.tiles.TileHighlightSettings;

/** Uses received native terrain and GrassData; wrapped/unloaded coordinates are rejected. */
final class WurmTileHighlightSampler {
    // Standard land mount limits in the pinned Wurm client/server (40 dirt, 0.7 m).
    static final float MAXIMUM_HEIGHT_DIFFERENCE=4f,MAXIMUM_WATER_DEPTH=.7f;
    static TileHighlightPatch sample(NearTerrainDataBuffer near,int playerX,int playerY,int worldSize,
                                     int x,int y,TileHighlightSettings settings) {
        if(near==null||x<0||y<0||x+1>=worldSize||y+1>=worldSize
                ||Math.abs((long)x-playerX)>settings.radius()||Math.abs((long)y-playerY)>settings.radius()
                ||!WurmSurfaceTileDescription.withinLiveRange(playerX,playerY,x+1,y+1)
                ||!WurmSurfaceTileDescription.withinLiveRange(playerX,playerY,x,y))return null;
        try {
            for(int dx=0;dx<=1;dx++)for(int dy=0;dy<=1;dy++)if(!near.isValid((x+dx)*4f,(y+dy)*4f))return null;
            Tiles.Tile tile=Tiles.getTile(near.getRawType(x,y));if(tile==null||tile.isCave())return null;
            float nw=near.getHeight(x,y),ne=near.getHeight(x+1,y),se=near.getHeight(x+1,y+1),sw=near.getHeight(x,y+1);
            if(!height(nw)||!height(ne)||!height(se)||!height(sw))return null;
            float water=near.getWaterHeight(x,y)/10f;
            boolean blocked=nonRideable(tile,nw,ne,se,sw,water);
            byte data=near.getData(x,y);
            int nonRideable=settings.nonRideable()&&blocked?settings.rgb():-1;
            int flowers=settings.flowers()?flowerColor(tile,data):-1;
            int harvestable=settings.harvestable()&&harvestable(tile,data)?settings.harvestableRgb():-1;
            int overaged=settings.overaged()&&overaged(tile,data)?settings.overagedRgb():-1;
            if(nonRideable<0&&flowers<0&&harvestable<0&&overaged<0)return null;
            // Water overlays sit above its surface rather than disappearing below it.
            return new TileHighlightPatch(x,y,nonRideable,flowers,harvestable,overaged,Math.max(nw,water),Math.max(ne,water),Math.max(se,water),Math.max(sw,water));
        }catch(RuntimeException receiving){return null;}
    }
    static boolean nonRideable(Tiles.Tile tile,float nw,float ne,float se,float sw,float water) {
        float low=Math.min(Math.min(nw,ne),Math.min(se,sw));
        float high=Math.max(Math.max(nw,ne),Math.max(se,sw));
        return tile==Tiles.Tile.TILE_HOLE||tile==Tiles.Tile.TILE_LAVA
                ||high-low>MAXIMUM_HEIGHT_DIFFERENCE||water-low>MAXIMUM_WATER_DEPTH;
    }
    static int flowerColor(Tiles.Tile tile,byte data) {
        if(tile!=Tiles.Tile.TILE_GRASS&&tile!=Tiles.Tile.TILE_ENCHANTED_GRASS&&tile!=Tiles.Tile.TILE_LAWN)return -1;
        switch(GrassData.FlowerType.decodeTileData(data)) {
            case FLOWER_1:return 0xffe233; // Native Yellow flowers.
            case FLOWER_2:return 0xff542c; // Orange-red.
            case FLOWER_3:return 0xb45aff; // Purple.
            case FLOWER_4:return 0xffffff; // White.
            case FLOWER_5:return 0x408cff; // Blue.
            case FLOWER_6:return 0xc4df48; // Greenish-yellow.
            case FLOWER_7:return 0xf0f0e4; // White-dotted.
            default:return -1; // None and unknown/reserved flower codes.
        }
    }
    static boolean harvestable(Tiles.Tile tile,byte data) {
        if(tile==Tiles.Tile.TILE_FIELD||tile==Tiles.Tile.TILE_FIELD2){int age=FieldData.getAge(data);return age==5||age==6;}
        // The native picker calls this received fruit bit "harvestable". Legacy
        // tree data uses these bits for species, so it cannot supply that flag.
        if(!(tile.isTree()||tile.isBush())||!tile.usesNewData()||!tile.isNormal()||!tile.canBearFruit()||!TreeData.hasFruit(data))return false;
        int age=FoliageAge.getFoliageAge(data).getAgeId();
        return age>FoliageAge.YOUNG_FOUR.getAgeId()&&age<FoliageAge.OVERAGED.getAgeId();
    }
    static boolean overaged(Tiles.Tile tile,byte data) {
        if(!tile.isTree())return false;
        FoliageAge age=FoliageAge.getFoliageAge(data);
        return age==FoliageAge.OVERAGED||age==FoliageAge.SHRIVELLED;
    }
    private static boolean height(float value){return Float.isFinite(value)&&value>-3000f;}
}
