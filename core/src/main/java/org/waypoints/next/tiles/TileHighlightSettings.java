package org.waypoints.next.tiles;

import java.util.*;

/** Surface overlay preferences shared by the hub, registry and renderer. */
public final class TileHighlightSettings {
    public static final String RADIUS="tileHighlight.radiusTiles";
    public static final int DEFAULT_RADIUS=7,MINIMUM_RADIUS=1,MAXIMUM_RADIUS=16;
    public static final String NON_RIDEABLE="tileHighlight.nonRideable.enabled";
    public static final String COLOR="tileHighlight.nonRideable.color";
    public static final String FLOWERS="tileHighlight.flowers.enabled";
    public static final String HARVESTABLE="tileHighlight.harvestable.enabled",HARVESTABLE_COLOR="tileHighlight.harvestable.color";
    public static final String OVERAGED="tileHighlight.overaged.enabled",OVERAGED_COLOR="tileHighlight.overaged.color";
    public static final String DEFAULT_HARVESTABLE_COLOR="#60E060",DEFAULT_OVERAGED_COLOR="#00FFFF";
    public static final String DEFAULT_COLOR="#FF0000";
    public static final String METEORS="tileHighlight.meteors.enabled",METEOR_COLOR="tileHighlight.meteors.color";
    public static final String METEOR_MARK="tileHighlight.meteors.autoMark",METEOR_CHIME="tileHighlight.meteors.chime";
    public static final String DEFAULT_METEOR_COLOR="#FF8A00";
    public static final List<String> KEYS=Collections.unmodifiableList(Arrays.asList(RADIUS,NON_RIDEABLE,COLOR,FLOWERS,HARVESTABLE,HARVESTABLE_COLOR,OVERAGED,OVERAGED_COLOR,METEORS,METEOR_COLOR,METEOR_MARK,METEOR_CHIME));
    private final Properties values=new Properties();
    public TileHighlightSettings(Properties source) {
        int radius;try{radius=Integer.parseInt(source.getProperty(RADIUS,Integer.toString(DEFAULT_RADIUS)).trim());}catch(NumberFormatException invalid){throw new IllegalArgumentException("Tile highlight radius: "+MINIMUM_RADIUS+".."+MAXIMUM_RADIUS,invalid);}
        if(radius<MINIMUM_RADIUS||radius>MAXIMUM_RADIUS)throw new IllegalArgumentException("Tile highlight radius: "+MINIMUM_RADIUS+".."+MAXIMUM_RADIUS);
        values.setProperty(RADIUS,Integer.toString(radius));
        values.setProperty(NON_RIDEABLE,bool(source.getProperty(NON_RIDEABLE,"false")));
        values.setProperty(FLOWERS,bool(source.getProperty(FLOWERS,"false")));
        values.setProperty(HARVESTABLE,bool(source.getProperty(HARVESTABLE,"false")));
        values.setProperty(OVERAGED,bool(source.getProperty(OVERAGED,"false")));
        values.setProperty(METEORS,bool(source.getProperty(METEORS,"true")));
        values.setProperty(METEOR_MARK,bool(source.getProperty(METEOR_MARK,"true")));
        values.setProperty(METEOR_CHIME,bool(source.getProperty(METEOR_CHIME,"true")));
        values.setProperty(COLOR,color(source.getProperty(COLOR,DEFAULT_COLOR)));
        values.setProperty(HARVESTABLE_COLOR,color(source.getProperty(HARVESTABLE_COLOR,DEFAULT_HARVESTABLE_COLOR)));
        values.setProperty(OVERAGED_COLOR,color(source.getProperty(OVERAGED_COLOR,DEFAULT_OVERAGED_COLOR)));
        values.setProperty(METEOR_COLOR,color(source.getProperty(METEOR_COLOR,DEFAULT_METEOR_COLOR)));
    }
    public boolean nonRideable(){return Boolean.parseBoolean(values.getProperty(NON_RIDEABLE));}
    public int radius(){return Integer.parseInt(values.getProperty(RADIUS));}
    public boolean flowers(){return Boolean.parseBoolean(values.getProperty(FLOWERS));}
    public boolean harvestable(){return Boolean.parseBoolean(values.getProperty(HARVESTABLE));}
    public boolean overaged(){return Boolean.parseBoolean(values.getProperty(OVERAGED));}
    public boolean enabled(){return nonRideable()||flowers()||harvestable()||overaged();}
    public String harvestableHex(){return values.getProperty(HARVESTABLE_COLOR);}
    public int harvestableRgb(){return Integer.parseInt(harvestableHex().substring(1),16);}
    public String overagedHex(){return values.getProperty(OVERAGED_COLOR);}
    public int overagedRgb(){return Integer.parseInt(overagedHex().substring(1),16);}
    public String hex(){return values.getProperty(COLOR);}
    public int rgb(){return Integer.parseInt(hex().substring(1),16);}
    public boolean meteors(){return Boolean.parseBoolean(values.getProperty(METEORS));}
    public boolean meteorMark(){return Boolean.parseBoolean(values.getProperty(METEOR_MARK));}
    public boolean meteorChime(){return Boolean.parseBoolean(values.getProperty(METEOR_CHIME));}
    public String meteorHex(){return values.getProperty(METEOR_COLOR);}
    public int meteorRgb(){return Integer.parseInt(meteorHex().substring(1),16);}
    public String value(String key){String value=values.getProperty(key);if(value==null)throw new IllegalArgumentException("Tile setting: "+key);return value;}
    /** Terrain overlays require fresh manual activation after each client launch. */
    public static void prepareStartup(Properties current) {
        for(String key:Arrays.asList(NON_RIDEABLE,FLOWERS,HARVESTABLE,OVERAGED))current.setProperty(key,"false");
        merge(current,current);
    }
    public static void merge(Properties current,Properties draft) {
        Properties next=new Properties();next.putAll(current);
        for(String key:KEYS)if(draft.containsKey(key))next.setProperty(key,draft.getProperty(key));
        current.putAll(new TileHighlightSettings(next).values);
    }
    private static String bool(String value) {
        String normalized=value.trim().toLowerCase(Locale.ROOT);
        if(!normalized.equals("true")&&!normalized.equals("false"))throw new IllegalArgumentException("Tiles Highlight: "+value);
        return normalized;
    }
    private static String color(String value){String color=value.trim().toUpperCase(Locale.ROOT);if(!color.matches("#[0-9A-F]{6}"))throw new IllegalArgumentException("Tiles Highlight color: "+color);return color;}
}
