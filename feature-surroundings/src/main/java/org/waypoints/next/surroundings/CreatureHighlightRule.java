package org.waypoints.next.surroundings;

import java.util.Locale;

/** Independent outline preferences; stable keys never depend on UI language. */
public enum CreatureHighlightRule {
    // Palette from WurmEspRevisited's standard wurmesp.properties (0..255 RGB).
    PLAYERS("Players", 0x73ff00), ANIMALS("Non-aggressive animals", 0x008cff),
    AGGRESSIVE_ANIMALS("Aggressive animals", 0xd200ff),
    MONSTERS("Monsters", 0xd200ff), UNIQUE("Unique", 0xff9600),
    CHAMPION(CreatureModifier.CHAMPION, 0xffffff),
    DISEASED(CreatureModifier.DISEASED, 0x996600),
    FIERCE(CreatureModifier.FIERCE, 0x800000),
    ANGRY(CreatureModifier.ANGRY, 0xff0032),
    RAGING(CreatureModifier.RAGING, 0xcc0000),
    SLOW(CreatureModifier.SLOW, 0x000066),
    ALERT(CreatureModifier.ALERT, 0x9900ff),
    GREENISH(CreatureModifier.GREENISH, 0x009933),
    LURKING(CreatureModifier.LURKING, 0x660066),
    SLY(CreatureModifier.SLY, 0x333399),
    HARDENED(CreatureModifier.HARDENED, 0xff3300),
    // Warm yellow separates Scared from native tower-guard blue.
    SCARED(CreatureModifier.SCARED, 0xffd54f),
    // ESP has no separate size-condition rules: inherit its animal blue, off.
    SMALL(CreatureModifier.SMALL, 0x008cff),
    MINI(CreatureModifier.MINI, 0x008cff),
    TINY(CreatureModifier.TINY, 0x008cff);

    private final String label;
    private final CreatureModifier modifier;
    private final int rgb;
    CreatureHighlightRule(String label, int rgb) { this.label=label; this.rgb=rgb; modifier=null; }
    CreatureHighlightRule(CreatureModifier modifier, int rgb) { this.label=modifier.getLabel(); this.modifier=modifier; this.rgb=rgb; }
    public String label() { return label; }
    public CreatureModifier modifier() { return modifier; }
    public boolean supportsAutoMark() { return this==UNIQUE || this==CHAMPION || this==DISEASED; }
    public boolean defaultEnabled() { return modifier==null || modifier.getWurmCode()>0; }
    public String key(String setting) { return "creatureHighlight."+name().toLowerCase(Locale.ROOT)+"."+setting; }
    public String defaultColor() { return String.format(Locale.ROOT,"#%06X",rgb); }
}
