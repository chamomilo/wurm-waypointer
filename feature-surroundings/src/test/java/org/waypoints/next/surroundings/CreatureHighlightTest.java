package org.waypoints.next.surroundings;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class CreatureHighlightTest {
    @Test public void diseasedAutoMarkWorksWithOutlineOffAndOverridesUnique() {
        Properties values=enabled();values.setProperty(CreatureHighlightRule.DISEASED.key("autoMark"),"true");values.setProperty(CreatureHighlightRule.UNIQUE.key("autoMark"),"true");
        CreatureHighlightSettings settings=new CreatureHighlightSettings(values);
        SurroundingEntry entry=SurroundingEntry.builder().kind(SurroundingKind.ANIMAL).category("Animals").wurmId(777).name("diseased dragon").uniqueCreature(true).creatureModifier(CreatureModifier.DISEASED).position(4,4,0).build();
        assertEquals(CreatureHighlightRule.DISEASED,CreatureHighlightPolicy.autoMark(settings,entry));assertFalse(settings.enabled(CreatureHighlightRule.DISEASED));
    }
    @Test public void scaredDefaultMigratesOnceAndKeepsCustomAndLaterExplicitChoices() {
        Properties legacy=new Properties();String key=CreatureHighlightRule.SCARED.key("color");legacy.setProperty(key,"#006699");
        assertTrue(CreatureHighlightSettings.migrateScared(legacy));assertEquals("#FFD54F",legacy.getProperty(key));
        legacy.setProperty(key,"#006699");assertFalse(CreatureHighlightSettings.migrateScared(legacy));assertEquals("#006699",legacy.getProperty(key));
        Properties custom=new Properties();custom.setProperty(key,"#123456");assertFalse(CreatureHighlightSettings.migrateScared(custom));assertEquals("#123456",custom.getProperty(key));
    }
    private static Properties enabled(CreatureHighlightRule... rules) {
        Properties values=new Properties();for(CreatureHighlightRule rule:CreatureHighlightRule.values())values.setProperty(rule.key("enabled"),"false");for(CreatureHighlightRule rule:rules)values.setProperty(rule.key("enabled"),"true");return values;
    }
    @Test public void everyConditionOverridesEnabledFamilyAndFallsBackWhenOff() {
        for(CreatureHighlightRule rule:CreatureHighlightRule.values())if(rule.modifier()!=null) {
            CreatureHighlightSettings settings=new CreatureHighlightSettings(enabled(CreatureHighlightRule.ANIMALS,CreatureHighlightRule.MONSTERS,CreatureHighlightRule.UNIQUE,rule));
            assertEquals(rule,CreatureHighlightPolicy.outline(settings,false,true,false,true,rule.modifier()));
            assertEquals(rule,CreatureHighlightPolicy.outline(settings,false,false,false,false,rule.modifier()));
            settings=new CreatureHighlightSettings(enabled(CreatureHighlightRule.ANIMALS));
            assertEquals(CreatureHighlightRule.ANIMALS,CreatureHighlightPolicy.outline(settings,false,false,false,false,rule.modifier()));
        }
    }
    @Test public void independentPlayerAndUniqueSwitches() {
        CreatureHighlightSettings settings=new CreatureHighlightSettings(enabled(CreatureHighlightRule.UNIQUE,CreatureHighlightRule.ANIMALS));
        assertNull(CreatureHighlightPolicy.outline(settings,true,false,false,false,CreatureModifier.CHAMPION));
        assertEquals(CreatureHighlightRule.UNIQUE,CreatureHighlightPolicy.outline(settings,false,true,false,true,CreatureModifier.NONE));
        assertNull(CreatureHighlightPolicy.outline(settings,false,true,false,false,CreatureModifier.NONE));
    }
    @Test public void defaultsMatchEspPaletteAndClassicGroupsAreEnabledWithoutAutoMark() {
        CreatureHighlightSettings settings=new CreatureHighlightSettings(new Properties());
        String[] palette={"#73FF00","#008CFF","#D200FF","#D200FF","#FF9600","#FFFFFF","#996600","#800000","#FF0032","#CC0000","#000066","#9900FF","#009933","#660066","#333399","#FF3300","#FFD54F","#008CFF","#008CFF","#008CFF"};
        CreatureHighlightRule[] rules=CreatureHighlightRule.values();assertEquals(palette.length,rules.length);
        for(int i=0;i<rules.length;i++) {
            assertEquals(rules[i].name(),palette[i],settings.hex(rules[i]));assertEquals(rules[i].name(),i<17,settings.enabled(rules[i]));assertFalse(settings.autoMark(rules[i]));
        }
        Properties saved=CreatureHighlightSettings.defaults();saved.setProperty(CreatureHighlightRule.CHAMPION.key("enabled"),"false");saved.setProperty(CreatureHighlightRule.CHAMPION.key("color"),"#123456");
        settings=new CreatureHighlightSettings(saved);assertFalse(settings.enabled(CreatureHighlightRule.CHAMPION));assertEquals("#123456",settings.hex(CreatureHighlightRule.CHAMPION));
    }
    @Test public void autoMarkIsIndependentAndOnlySuccessfullyMarkedSubjectsAreDeduplicated() {
        Properties values=enabled();values.setProperty(CreatureHighlightRule.UNIQUE.key("autoMark"),"true");values.setProperty(CreatureHighlightRule.CHAMPION.key("autoMark"),"true");
        CreatureHighlightSettings settings=new CreatureHighlightSettings(values);CreatureAutoMarkSession session=new CreatureAutoMarkSession();
        SurroundingEntry entry=SurroundingEntry.builder().kind(SurroundingKind.ANIMAL).wurmId(42).name("Champion dragon").category("Animals").uniqueCreature(true).creatureModifier(CreatureModifier.CHAMPION).position(4,4,0).build();
        assertEquals(CreatureHighlightRule.CHAMPION,session.candidate(settings,entry));
        assertEquals("Failed creation can retry",CreatureHighlightRule.CHAMPION,session.candidate(settings,entry));
        session.marked(entry.getKey());assertNull("Movement or reappearance must not create duplicates",session.candidate(settings,entry));
        session.clear();assertNotNull("A new server session can restore a mark by its stable ID",session.candidate(settings,entry));
        SurroundingEntry item=SurroundingEntry.builder().kind(SurroundingKind.ITEM).wurmId(43).name("dragon corpse").category("Corpses").uniqueCreature(true).position(4,4,0).build();
        assertNull(session.candidate(settings,item));
        SurroundingEntry normal=SurroundingEntry.builder().kind(SurroundingKind.ANIMAL).wurmId(44).name("Horse").category("Animals").position(4,4,0).build();
        assertNull(session.candidate(settings,normal));
    }
    @Test public void modelsDistinguishAnimalsFromMonstersWithoutMatchingSubstrings() {
        for(String species:new String[]{"sheep","cow","buffalo","bison","chicken","horse","pig","deer"}) {
            String model="model.creature.quadraped."+species;
            assertFalse(model,CreatureHighlightPolicy.monster(model,false));assertFalse(model,CreatureHighlightPolicy.aggressiveAnimal(model));
        }
        for(String model:new String[]{"model.creature.quadraped.wolf","model.creature.quadraped.lion","model.creature.quadraped.bear.brown","model.creature.spider"}) {
            assertFalse(model,CreatureHighlightPolicy.monster(model,false));assertTrue(model,CreatureHighlightPolicy.aggressiveAnimal(model));
        }
        for(String model:new String[]{"model.creature.humanoid.goblin","model.creature.humanoid.troll","model.creature.hell.horse"})assertTrue(model,CreatureHighlightPolicy.monster(model,false));
        assertTrue(CreatureHighlightPolicy.monster("modded.model",true));
    }
    @Test public void aggressiveAndNonAggressiveAnimalsHaveIndependentSwitchesAndColors() {
        Properties values=enabled(CreatureHighlightRule.ANIMALS);values.setProperty(CreatureHighlightRule.ANIMALS.key("color"),"#12AB34");
        CreatureHighlightSettings settings=new CreatureHighlightSettings(values);
        assertEquals(CreatureHighlightRule.ANIMALS,CreatureHighlightPolicy.outline(settings,false,false,false,false,CreatureModifier.NONE));
        assertNull(CreatureHighlightPolicy.outline(settings,false,false,true,false,CreatureModifier.NONE));
        values.setProperty(CreatureHighlightRule.AGGRESSIVE_ANIMALS.key("enabled"),"true");values.setProperty(CreatureHighlightRule.AGGRESSIVE_ANIMALS.key("color"),"#FF7700");settings=new CreatureHighlightSettings(values);
        assertEquals(CreatureHighlightRule.AGGRESSIVE_ANIMALS,CreatureHighlightPolicy.outline(settings,false,false,true,false,CreatureModifier.NONE));
        assertEquals("#12AB34",settings.hex(CreatureHighlightRule.ANIMALS));assertEquals("#FF7700",settings.hex(CreatureHighlightRule.AGGRESSIVE_ANIMALS));
        values.setProperty(CreatureHighlightRule.ANIMALS.key("enabled"),"false");settings=new CreatureHighlightSettings(values);
        assertNull(CreatureHighlightPolicy.outline(settings,false,false,false,false,CreatureModifier.NONE));
        assertEquals(CreatureHighlightRule.AGGRESSIVE_ANIMALS,CreatureHighlightPolicy.outline(settings,false,false,true,false,CreatureModifier.NONE));
    }
    @Test public void validatesColorsAndMergesWithoutDroppingOtherPreferences() {
        Properties values=new Properties();values.setProperty("language","ru");values.setProperty("custom","keep");
        Properties draft=new Properties();draft.setProperty(CreatureHighlightRule.CHAMPION.key("color"),"#123abc");CreatureHighlightSettings.merge(values,draft);
        assertEquals("keep",values.getProperty("custom"));assertEquals("ru",values.getProperty("language"));
        CreatureHighlightSettings settings=new CreatureHighlightSettings(values);assertEquals("#123ABC",settings.hex(CreatureHighlightRule.CHAMPION));
        assertEquals(18/255f,settings.color(CreatureHighlightRule.CHAMPION).getRed(),.0001f);
        assertEquals("#123ABC",CreatureHighlightSettings.hex(18/255f,58/255f,188/255f));
        draft.setProperty(CreatureHighlightRule.CHAMPION.key("color"),"#GGGGGG");
        try{CreatureHighlightSettings.merge(values,draft);fail();}catch(IllegalArgumentException expected){}
        assertEquals("#123ABC",values.getProperty(CreatureHighlightRule.CHAMPION.key("color")));
    }
}
