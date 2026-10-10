package org.waypoints.next.ui;

import java.util.*;

/** User-facing settings schema shared by validation and the native Settings view. */
public final class SettingSpec {
    public enum Type { BOOLEAN, INTEGER, DECIMAL, TEXT, CHOICE }
    public final String key,label,group,defaultValue;
    public final Type type;
    public final double minimum,maximum;
    public final String[] choices;
    private SettingSpec(String group,String key,String label,Type type,String value,double min,double max,String... choices){this.group=group;this.key=key;this.label=label;this.type=type;defaultValue=value;minimum=min;maximum=max;this.choices=choices;}
    private static SettingSpec bool(String g,String k,String l,boolean v){return new SettingSpec(g,k,l,Type.BOOLEAN,Boolean.toString(v),0,0,"false","true");}
    private static SettingSpec integer(String g,String k,String l,int v,int min,int max){return new SettingSpec(g,k,l,Type.INTEGER,Integer.toString(v),min,max);}
    private static SettingSpec decimal(String g,String k,String l,double v,double min,double max){return new SettingSpec(g,k,l,Type.DECIMAL,Double.toString(v),min,max);}
    private static SettingSpec text(String g,String k,String l,String v){return new SettingSpec(g,k,l,Type.TEXT,v,0,16384);}
    private static SettingSpec choice(String g,String k,String l,String v,String... options){return new SettingSpec(g,k,l,Type.CHOICE,v,0,0,options);}
    public String validate(String value){
        if(value==null)throw new IllegalArgumentException(label);String s=value.trim();
        if(s.indexOf('\n')>=0||s.indexOf('\r')>=0||s.length()>16384)throw new IllegalArgumentException(label);
        if(type==Type.BOOLEAN||type==Type.CHOICE){for(String option:choices)if(option.equalsIgnoreCase(s))return option;throw new IllegalArgumentException(label);}
        if(type==Type.INTEGER||type==Type.DECIMAL){try{double number=type==Type.INTEGER?Integer.parseInt(s):Double.parseDouble(s);if(!Double.isFinite(number)||number<minimum||number>maximum)throw new IllegalArgumentException();}catch(IllegalArgumentException invalid){throw new IllegalArgumentException(label+": "+minimum+".."+maximum);}}
        if(key.endsWith("File")||key.endsWith("Directory")){
            if(s.isEmpty()||s.length()>1024)throw new IllegalArgumentException(label);
            try{java.nio.file.Paths.get(s);}catch(java.nio.file.InvalidPathException invalid){throw new IllegalArgumentException(label);}
        }
        if(key.equals("scannerExcludedNames")){
            String[] fragments=s.split(",");if(fragments.length>32)throw new IllegalArgumentException(label);
            for(String fragment:fragments)if(fragment.trim().length()>80)throw new IllegalArgumentException(label);
        }
        return s;
    }
    public static final List<SettingSpec> ALL=Collections.unmodifiableList(Arrays.asList(
        choice("General","language","Language","en","en","pt-BR","de","ru"),
        bool("General","lootMapEnabled","Loot Map assistance",true),bool("General","archaeologyEnabled","Archaeology assistance",true),
        choice("Navigation","navigationRouteVisualStyle","Navigation signal","PULSE","PULSE","SOLID","MOVING_DASHES"),
        integer("Navigation","navigationPulseMaximumDistanceMetres","Pulse distance (m)",240,16,2000),
        decimal("Navigation","navigationCartMaximumSlopeDirt","Maximum cart slope (dirt)",40,0.1,1000),
        decimal("Navigation","navigationCartMaximumWaterDepthMetres","Maximum cart water depth (m)",0.7,0,100),
        bool("Navigation","navigationHighwaysEnabled","Prefer published highways",true),
        integer("Navigation","navigationHighwaysSyncMinutes","Highway refresh interval (minutes)",15,1,1440),
        bool("Maps","serverMapEnabled","Server map",true),bool("Maps","serverMapShowDeeds","Show deeds",true),bool("Maps","serverMapShowHighways","Show highways",true),
        integer("Maps","serverMapSyncMinutes","Map refresh interval (minutes)",60,5,1440),
        bool("Maps","deedProviderEnabled","Deed providers",true),text("Maps","deedProviderMappings","Custom deed feeds",""),
        choice("Scanner","scannerProfile","Scanner profile","off","off","uniques","treasure","animals"),
        bool("Scanner","scannerNotifications","Appearance notifications",true),bool("Scanner","scannerOutlines","Highlight matching models",true),
        integer("Scanner","scannerMaximumOutlines","Maximum highlighted objects",8,0,32),
        integer("Scanner","scannerOutlineDistanceMetres","Highlight distance (m)",80,4,512),text("Scanner","scannerExcludedNames","Excluded names",""),
        integer("Tracking","trackingRetentionDays","Keep last known positions (days)",90,1,3650),
        integer("Markers","maximumCompassMarkers","Maximum compass markers",64,1,1024),
        integer("Markers","maximumWorldEffects","Maximum world markers",16,0,1024),
        integer("Markers","worldEffectDistanceMetres","World marker distance (m)",12000,1,100000),
        integer("Markers","maximumWorldLabels","Maximum world labels",16,0,1024),
        integer("Markers","worldLabelDistanceMetres","World label distance (m)",12000,1,100000),
        integer("Advanced","waypointMapWidth","Map width (tiles)",4096,1,65536),integer("Advanced","waypointMapHeight","Map height (tiles)",4096,1,65536),
        integer("Advanced","archaeologyHistoryLimit","Archaeology history limit",64,8,1024),
        bool("Advanced","navigationRouteDiagnostics","Write navigation diagnostics",false),
        integer("Advanced","navigationRouteLogTileInterval","Diagnostic movement threshold (tiles)",8,1,512),
        text("Data storage","waypointDataFile","Waypoint file","wurm-waypointer-data/waypoints.wpt"),
        text("Data storage","waypointTransferFile","Import/export file","wurm-waypointer-data/waypoints-transfer.wpt"),
        text("Data storage","dynamicTargetCacheFile","Tracked target file","wurm-waypointer-data/dynamic-targets.wpt"),
        text("Data storage","vanillaLandmarkStateFile","Landmark preferences file","wurm-waypointer-data/vanilla-landmarks.state"),
        text("Data storage","lootMapLogDirectory","Loot Map log folder","mods/wurm-waypointer/lootmap-hunts"),
        text("Data storage","archaeologySessionFile","Archaeology sessions file","mods/wurm-waypointer/archaeology-sessions.properties"),
        text("Data storage","archaeologyKnownLocationsFile","Known archaeology locations file","mods/wurm-waypointer/archaeology-known-locations.properties"),
        text("Data storage","navigationRouteLogDirectory","Navigation log folder","mods/wurm-waypointer/navigation-routes"),
        text("Data storage","navigationHighwaysCacheDirectory","Highway cache folder","mods/wurm-waypointer/maps"),
        text("Data storage","serverMapCacheDirectory","Map cache folder","mods/wurm-waypointer/maps"),
        text("Data storage","deedProviderCacheDirectory","Deed cache folder","mods/wurm-waypointer/deeds")
    ));
}
