package org.waypoints.next.surroundings;

import java.util.Locale;

/** Enabled modifier colours override Unique and the animal/monster family colour. */
public final class CreatureHighlightPolicy {
    private CreatureHighlightPolicy() { }
    public static CreatureHighlightRule outline(CreatureHighlightSettings settings,boolean player,
            boolean monster,boolean aggressiveAnimal,boolean unique,CreatureModifier modifier) {
        if(player)return settings.enabled(CreatureHighlightRule.PLAYERS)?CreatureHighlightRule.PLAYERS:null;
        for(CreatureHighlightRule rule:CreatureHighlightRule.values())
            if(rule.modifier()!=null && rule.modifier()==modifier && settings.enabled(rule))return rule;
        if(unique && settings.enabled(CreatureHighlightRule.UNIQUE))return CreatureHighlightRule.UNIQUE;
        CreatureHighlightRule family=monster?CreatureHighlightRule.MONSTERS:
                aggressiveAnimal?CreatureHighlightRule.AGGRESSIVE_ANIMALS:CreatureHighlightRule.ANIMALS;
        return settings.enabled(family)?family:null;
    }
    public static CreatureHighlightRule autoMark(CreatureHighlightSettings settings,SurroundingEntry entry) {
        if(entry==null || entry.getKind()!=SurroundingKind.ANIMAL)return null;
        if(entry.getCreatureModifier()==CreatureModifier.CHAMPION && settings.autoMark(CreatureHighlightRule.CHAMPION))return CreatureHighlightRule.CHAMPION;
        if(entry.getCreatureModifier()==CreatureModifier.DISEASED && settings.autoMark(CreatureHighlightRule.DISEASED))return CreatureHighlightRule.DISEASED;
        return entry.isUniqueCreature() && settings.autoMark(CreatureHighlightRule.UNIQUE)?CreatureHighlightRule.UNIQUE:null;
    }
    /** Fantasy/humanoid monsters are separate from aggressive wild animal species. */
    public static boolean monster(String modelName,boolean unique) {
        String model=normalizedModel(modelName);
        if(unique)return true;
        for(String family:new String[]{"worg","troll","goblin","ogre","cyclops","kyklops","demon",
                "hellhound","hellscorp","hellhorse","lavafiend","dragon","drake","forestgiant",
                "zombie","skeleton","rift","avenger"})
            if(model.contains(" "+family+" "))return true;
        return model.contains(" hell ") || model.contains(" forest giant ");
    }
    /** Species define the two animal groups, independently of current attitude/taming. */
    public static boolean aggressiveAnimal(String modelName) {
        String model=normalizedModel(modelName);
        for(String species:new String[]{"bear","wolf","lion","crocodile","alligator","scorpion","spider",
                "snake","shark","anaconda","wildcat","hyena","cavebug","crab"})
            if(model.contains(" "+species+" "))return true;
        return model.contains(" cave bug ");
    }
    private static String normalizedModel(String modelName) {
        return " "+(modelName==null?"":modelName).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+"," ")+" ";
    }
}
