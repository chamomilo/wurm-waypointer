package org.waypoints.next.surroundings;

import java.util.*;

/** Validated immutable preferences shared by UI, classification and auto-marking. */
public final class CreatureHighlightSettings {
    private final Properties values=new Properties();
    public CreatureHighlightSettings(Properties source) {
        for(CreatureHighlightRule rule:CreatureHighlightRule.values()) {
            values.setProperty(rule.key("enabled"), bool(source.getProperty(rule.key("enabled"),Boolean.toString(rule.defaultEnabled()))));
            values.setProperty(rule.key("color"), color(source.getProperty(rule.key("color"),rule.defaultColor())));
            if(rule.supportsAutoMark()) values.setProperty(rule.key("autoMark"),bool(source.getProperty(rule.key("autoMark"),"false")));
        }
    }
    public boolean enabled(CreatureHighlightRule rule) { return Boolean.parseBoolean(values.getProperty(rule.key("enabled"))); }
    public boolean autoMark(CreatureHighlightRule rule) { return rule.supportsAutoMark() && Boolean.parseBoolean(values.getProperty(rule.key("autoMark"))); }
    public String hex(CreatureHighlightRule rule) { return values.getProperty(rule.key("color")); }
    public static Properties defaults() {
        Properties result=new Properties();result.putAll(new CreatureHighlightSettings(new Properties()).values);return result;
    }
    public ScannerColor color(CreatureHighlightRule rule) {
        int rgb=Integer.parseInt(hex(rule).substring(1),16);
        return new ScannerColor(((rgb>>16)&255)/255f,((rgb>>8)&255)/255f,(rgb&255)/255f,1f);
    }
    public static List<String> keys() {
        List<String> keys=new ArrayList<String>();
        for(CreatureHighlightRule rule:CreatureHighlightRule.values()) {
            keys.add(rule.key("enabled"));keys.add(rule.key("color"));
            if(rule.supportsAutoMark())keys.add(rule.key("autoMark"));
        }
        keys.add("creatureHighlight.scared.paletteVersion");
        return keys;
    }
    /** One-time default migration; a later deliberate blue selection remains valid. */
    public static boolean migrateScared(Properties values) {
        String marker="creatureHighlight.scared.paletteVersion",color=CreatureHighlightRule.SCARED.key("color");
        if(values.containsKey(marker))return false;
        boolean legacy="#006699".equalsIgnoreCase(values.getProperty(color,""));
        if(legacy)values.setProperty(color,CreatureHighlightRule.SCARED.defaultColor());
        values.setProperty(marker,"2");return legacy;
    }
    /** Merge only the fields provided by this view, preserving other preferences. */
    public static void merge(Properties current, Properties draft) {
        Properties next=new Properties();next.putAll(current);
        for(String key:keys())if(draft.containsKey(key))next.setProperty(key,draft.getProperty(key));
        CreatureHighlightSettings validated=new CreatureHighlightSettings(next);
        current.putAll(validated.values);
    }
    private static String bool(String value) {
        String normalized=value.trim().toLowerCase(Locale.ROOT);
        if(!normalized.equals("true")&&!normalized.equals("false"))throw new IllegalArgumentException("Creatures Highlight: "+value);
        return normalized;
    }
    private static String color(String value) {
        String normalized=value.trim().toUpperCase(Locale.ROOT);
        if(!normalized.matches("#[0-9A-F]{6}"))throw new IllegalArgumentException("Creatures Highlight color: "+value);
        return normalized;
    }
    public static String hex(float red,float green,float blue) {
        return String.format(Locale.ROOT,"#%02X%02X%02X",Math.round(red*255),Math.round(green*255),Math.round(blue*255));
    }
}
