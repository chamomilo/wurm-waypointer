package org.waypoints.next.integration;

import com.wurmonline.client.renderer.CreatureData;
import com.wurmonline.client.renderer.PickableUnit;
import com.wurmonline.client.renderer.cell.CreatureCellRenderable;
import com.wurmonline.client.renderer.cell.PlayerCellRenderable;
import org.junit.Test;
import org.waypoints.next.surroundings.*;
import java.lang.reflect.Field;
import java.util.*;
import static org.junit.Assert.*;

public class CreatureHighlightRuntimeTest {
    private static Properties disabled() {
        Properties settings=new Properties();for(CreatureHighlightRule rule:CreatureHighlightRule.values())settings.setProperty(rule.key("enabled"),"false");return settings;
    }
    @Test public void livestockWildPredatorsAndMonstersUseThreeIndependentCategories() throws Exception {
        CreatureHighlightRuntime runtime=new CreatureHighlightRuntime();Properties settings=disabled();
        for(CreatureHighlightRule rule:new CreatureHighlightRule[]{CreatureHighlightRule.ANIMALS,CreatureHighlightRule.AGGRESSIVE_ANIMALS,CreatureHighlightRule.MONSTERS})settings.setProperty(rule.key("enabled"),"true");
        settings.setProperty(CreatureHighlightRule.ANIMALS.key("color"),"#00FF00");settings.setProperty(CreatureHighlightRule.AGGRESSIVE_ANIMALS.key("color"),"#FF7700");settings.setProperty(CreatureHighlightRule.MONSTERS.key("color"),"#FF0000");runtime.configure(settings);
        runtime.upsert(creature(45,"model.creature.quadraped.cow","cow",false,0));
        runtime.upsert(creature(46,"model.creature.quadraped.wolf","wolf",false,0));
        runtime.upsert(creature(47,"model.creature.humanoid.troll","troll",false,0));
        List<PickableUnit> outlines=runtime.outlines();assertEquals(3,outlines.size());
        assertEquals(1f,outlines.get(0).getOutlineColor().g,0f);assertEquals(119/255f,outlines.get(1).getOutlineColor().g,.0001f);assertEquals(0f,outlines.get(2).getOutlineColor().g,0f);
        settings.setProperty(CreatureHighlightRule.AGGRESSIVE_ANIMALS.key("enabled"),"false");runtime.configure(settings);outlines=runtime.outlines();assertEquals(2,outlines.size());assertEquals(45,outlines.get(0).getId());assertEquals(47,outlines.get(1).getId());
    }
    private static CreatureCellRenderable creature(long id,String model,String name,boolean player,int layer) throws Exception {
        Field unsafeField=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");unsafeField.setAccessible(true);
        sun.misc.Unsafe unsafe=(sun.misc.Unsafe)unsafeField.get(null);
        CreatureCellRenderable renderable=(CreatureCellRenderable)unsafe.allocateInstance(player?PlayerCellRenderable.class:CreatureCellRenderable.class);
        CreatureData data=new CreatureData(id,model,name,(byte)0,128,256,4,0,layer,false,(byte)0,0L,(byte)0);
        Field field=CreatureCellRenderable.class.getDeclaredField("creature");field.setAccessible(true);field.set(renderable,data);
        return renderable;
    }
    @Test public void playersAreHighlightedWithoutEnteringMobCatalogAndUseTheirOwnColor() throws Exception {
        CreatureHighlightRuntime runtime=new CreatureHighlightRuntime();Properties settings=disabled();
        settings.setProperty(CreatureHighlightRule.PLAYERS.key("enabled"),"true");settings.setProperty(CreatureHighlightRule.PLAYERS.key("color"),"#123ABC");runtime.configure(settings);
        CreatureCellRenderable player=creature(42,"model.creature.humanoid.human.player","Alice",true,0);runtime.upsert(player);
        assertNull(SurroundingsRenderableAdapter.project(player,java.time.Instant.now()));
        List<PickableUnit> outlines=runtime.outlines();assertEquals(1,outlines.size());assertEquals(42,outlines.get(0).getId());
        assertEquals(18/255f,outlines.get(0).getOutlineColor().r,.0001f);
        runtime.remove(player);assertTrue(runtime.outlines().isEmpty());
    }
    @Test public void stationaryRenameReclassifiesAndDeathCannotReinsertAnOutline() throws Exception {
        CreatureHighlightRuntime runtime=new CreatureHighlightRuntime();Properties settings=disabled();
        settings.setProperty(CreatureHighlightRule.CHAMPION.key("enabled"),"true");runtime.configure(settings);
        CreatureCellRenderable horse=creature(43,"model.creature.quadraped.horse","horse",false,0);
        runtime.upsert(horse);assertTrue(runtime.outlines().isEmpty());
        for(String fieldName:new String[]{"name","displayName"}) {
            Field name=Class.forName("com.wurmonline.client.renderer.ObjectData").getDeclaredField(fieldName);name.setAccessible(true);name.set(horse.getCreatureData(),new com.wurmonline.client.util.SecureStrings("champion horse"));
        }
        runtime.upsert(horse);assertEquals(1,runtime.outlines().size());
        runtime.died(43);runtime.upsert(horse);assertTrue(runtime.outlines().isEmpty());
        runtime.endSession();runtime.upsert(horse);assertEquals(1,runtime.outlines().size());
    }
    @Test public void crossLayerSurfaceAndCaveTargetsSurviveUntilUnloadAndIgnoreMobileItems() throws Exception {
        CreatureHighlightRuntime runtime=new CreatureHighlightRuntime();Properties settings=disabled();
        settings.setProperty(CreatureHighlightRule.ANIMALS.key("enabled"),"true");runtime.configure(settings);
        CreatureCellRenderable surface=creature(44,"model.creature.horse","horse",false,0);runtime.upsert(surface);
        long revision=runtime.revision();runtime.upsert(surface);assertEquals("Movement must not rebuild unchanged outlines",revision,runtime.revision());
        CreatureCellRenderable cave=creature(48,"model.creature.horse","cave horse",false,-1);runtime.upsert(cave);
        assertEquals(0,surface.getLayer());assertEquals(-1,cave.getLayer());assertEquals("Both layers must reach the through-terrain outline pass",2,runtime.outlines().size());
        runtime.remove(cave);assertEquals(1,runtime.outlines().size());
        runtime.remove(surface);assertTrue(runtime.outlines().isEmpty());runtime.upsert(surface);assertEquals(1,runtime.outlines().size());
        surface.setItem(true);runtime.upsert(surface);assertTrue(runtime.outlines().isEmpty());
        surface.setItem(false);runtime.upsert(surface);runtime.clearLoaded();assertTrue(runtime.outlines().isEmpty());
    }
    @Test public void detectionCountsLoadedMatchesIndependentlyOfOutlinePriorityAndTracksLifecycle() throws Exception {
        CreatureHighlightRuntime runtime=new CreatureHighlightRuntime();runtime.configure(disabled());
        CreatureCellRenderable cow=creature(51,"model.creature.quadraped.cow","cow",false,0);
        CreatureCellRenderable champion=creature(52,"model.creature.quadraped.horse","champion horse",false,-1);
        CreatureCellRenderable wolf=creature(53,"model.creature.quadraped.wolf","wolf",false,0);
        CreatureCellRenderable bear=creature(54,"model.creature.quadraped.bear","diseased bear",false,-1);
        CreatureCellRenderable dragon=creature(55,"model.creature.dragon.red","red dragon",false,0);
        CreatureCellRenderable player=creature(56,"model.creature.humanoid.human.player","Alice",true,0);
        for(CreatureCellRenderable creature:Arrays.asList(cow,champion,wolf,bear,dragon,player))runtime.upsert(creature);
        Map<CreatureHighlightRule,Integer> counts=runtime.detectedCounts();
        assertEquals(Integer.valueOf(1),counts.get(CreatureHighlightRule.PLAYERS));
        assertEquals(Integer.valueOf(2),counts.get(CreatureHighlightRule.ANIMALS));
        assertEquals(Integer.valueOf(2),counts.get(CreatureHighlightRule.AGGRESSIVE_ANIMALS));
        for(CreatureHighlightRule rule:Arrays.asList(CreatureHighlightRule.MONSTERS,CreatureHighlightRule.UNIQUE,CreatureHighlightRule.CHAMPION,CreatureHighlightRule.DISEASED))assertEquals(rule.name(),Integer.valueOf(1),counts.get(rule));
        assertTrue("Disabled outlines do not suppress detection",runtime.outlines().isEmpty());
        runtime.upsert(cow);assertSame("Movement/technical upsert retains the cached count snapshot",counts,runtime.detectedCounts());
        try{counts.put(CreatureHighlightRule.PLAYERS,99);fail("Snapshot must be immutable");}catch(UnsupportedOperationException expected){ }
        runtime.remove(champion);assertEquals(Integer.valueOf(1),runtime.detectedCounts().get(CreatureHighlightRule.ANIMALS));assertEquals(Integer.valueOf(0),runtime.detectedCounts().get(CreatureHighlightRule.CHAMPION));
        assertEquals("Earlier snapshot stays unchanged",Integer.valueOf(2),counts.get(CreatureHighlightRule.ANIMALS));
        for(String fieldName:new String[]{"name","displayName"}) {
            Field name=Class.forName("com.wurmonline.client.renderer.ObjectData").getDeclaredField(fieldName);name.setAccessible(true);name.set(bear.getCreatureData(),new com.wurmonline.client.util.SecureStrings("alert bear"));
        }
        runtime.upsert(bear);assertEquals(Integer.valueOf(0),runtime.detectedCounts().get(CreatureHighlightRule.DISEASED));assertEquals(Integer.valueOf(1),runtime.detectedCounts().get(CreatureHighlightRule.ALERT));
        runtime.died(55);runtime.upsert(dragon);assertEquals(Integer.valueOf(0),runtime.detectedCounts().get(CreatureHighlightRule.UNIQUE));assertEquals(Integer.valueOf(0),runtime.detectedCounts().get(CreatureHighlightRule.MONSTERS));
        runtime.endSession();for(Integer count:runtime.detectedCounts().values())assertEquals(Integer.valueOf(0),count);
    }
}
