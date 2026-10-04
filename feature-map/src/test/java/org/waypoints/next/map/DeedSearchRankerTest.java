package org.waypoints.next.map;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public final class DeedSearchRankerTest {
    @Test
    public void exactMayorMatchRanksAboveAlphabeticallyEarlierFuzzyMatches() {
        Deed fuzzy = deed("Almeria", "Someone", "c-h-a-m-o-m-i-l-o");
        Deed exact = deed("Haven Legacy", "Chamomilo", "");

        List<Deed> ranked = DeedSearchRanker.rank(
                Arrays.asList(fuzzy, exact), "chamomilo");

        assertEquals(2, ranked.size());
        assertEquals("Haven Legacy", ranked.get(0).getName());
        assertEquals("Almeria", ranked.get(1).getName());
    }

    @Test
    public void matchQualityWinsBeforeFieldAndAlphabeticOrder() {
        Deed fuzzy = deed("A Fuzzy", "c-h-a-m-o-m-i-l-o", "");
        Deed contained = deed("A Contained", "Port Chamomilo Bay", "");
        Deed prefix = deed("A Prefix", "Chamomilo Harbor", "");
        Deed exact = deed("Z Exact", "Chamomilo", "");

        List<Deed> ranked = DeedSearchRanker.rank(Arrays.asList(
                fuzzy, contained, prefix, exact), "Chamomilo");

        assertEquals("Z Exact", ranked.get(0).getName());
        assertEquals("A Prefix", ranked.get(1).getName());
        assertEquals("A Contained", ranked.get(2).getName());
        assertEquals("A Fuzzy", ranked.get(3).getName());
    }

    @Test
    public void equalRelevanceFallsBackToDeedName() {
        Deed zulu = deed("Zulu", "Chamomilo North", "");
        Deed alpha = deed("Alpha", "Chamomilo South", "");

        List<Deed> ranked = DeedSearchRanker.rank(
                Arrays.asList(zulu, alpha), "Chamomilo");

        assertEquals("Alpha", ranked.get(0).getName());
        assertEquals("Zulu", ranked.get(1).getName());
    }

    private static Deed deed(String name, String mayor, String motto) {
        Map<String, String> metadata = new HashMap<String, String>();
        metadata.put("mayor", mayor);
        metadata.put("motto", motto);
        return Deed.fromProviderData(name.toLowerCase().replace(' ', '-'),
                name, 100, 200, metadata);
    }
}
