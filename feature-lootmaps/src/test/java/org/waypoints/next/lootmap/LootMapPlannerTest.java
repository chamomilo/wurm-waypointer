package org.waypoints.next.lootmap;

import org.junit.Test;
import org.waypoints.next.source.MapBounds;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

public class LootMapPlannerTest {
    private static final Instant NOW = Instant.parse("2026-08-08T12:00:00Z");
    private static final MapBounds BOUNDS = new MapBounds(4096, 4096);

    @Test public void farOpenBandUsesSafeDirectStep() {
        LootMapDecision result = new LootMapPlanner().plan(Collections.singletonList(
                observation(1000, 3000, LootMapDistanceBand.TWO_THOUSAND_PLUS,
                        LootMapRelativeDirection.AHEAD)), BOUNDS);
        assertEquals(LootMapDecision.Mode.DIRECT, result.getMode());
        assertEquals(1000.0d, result.getWaypointY(), 0.0d);
    }

    @Test public void farBandsUseMinimumAndTractableBandsUseOnePointTwo() {
        assertEquals(7.2d, LootMapDistanceBand.SIX_TO_NINE.getDirectStep(),
                0.0d);
        assertEquals(12.0d,
                LootMapDistanceBand.TEN_TO_NINETEEN.getDirectStep(), 0.0d);
        assertEquals(24.0d,
                LootMapDistanceBand.TWENTY_TO_FORTY_NINE.getDirectStep(), 0.0d);
        assertEquals(60.0d,
                LootMapDistanceBand.FIFTY_TO_ONE_NINETY_NINE.getDirectStep(),
                0.0d);
        assertEquals(240.0d,
                LootMapDistanceBand.TWO_HUNDRED_TO_FOUR_NINETY_NINE
                        .getDirectStep(), 0.0d);
        assertEquals(500.0d,
                LootMapDistanceBand.FIVE_HUNDRED_TO_NINE_NINETY_NINE
                        .getDirectStep(), 0.0d);
        assertEquals(1000.0d,
                LootMapDistanceBand.ONE_THOUSAND_TO_NINETEEN_NINETY_NINE
                        .getDirectStep(), 0.0d);
        assertEquals(2000.0d,
                LootMapDistanceBand.TWO_THOUSAND_PLUS.getDirectStep(), 0.0d);
    }

    @Test public void ambiguousBandUsesAreaPriorWithoutPretendingToKnowExactDistance() {
        LootMapPlanner planner = new LootMapPlanner();
        LootMapDecision first = planner.plan(Collections.singletonList(
                observation(1000, 1000,
                        LootMapDistanceBand.FIFTY_TO_ONE_NINETY_NINE,
                        LootMapRelativeDirection.RIGHT)), BOUNDS);
        assertEquals(LootMapDecision.Mode.READ_FIRST, first.getMode());
        assertTrue(first.getProbabilityAtLeast100() > 0.70d);

        LootMapDecision second = planner.plan(Arrays.asList(
                observation(1000, 1000,
                        LootMapDistanceBand.FIFTY_TO_ONE_NINETY_NINE,
                        LootMapRelativeDirection.RIGHT),
                observation(1060, 1000,
                        LootMapDistanceBand.FIFTY_TO_ONE_NINETY_NINE,
                        LootMapRelativeDirection.RIGHT)), BOUNDS);
        assertTrue(second.getMode() == LootMapDecision.Mode.BALANCED
                || second.getMode() == LootMapDecision.Mode.READ_FIRST);
        assertTrue(second.getProbabilityAtLeast100() >= 0.0d);
        assertTrue(second.getProbabilityAtLeast100() <= 1.0d);
    }

    @Test public void finalBandAlwaysReturnsOnePoint() {
        LootMapDecision result = new LootMapPlanner().plan(Collections.singletonList(
                observation(1000, 1000, LootMapDistanceBand.ONE_TO_THREE,
                        LootMapRelativeDirection.LEFT)), BOUNDS);
        assertEquals(LootMapDecision.Mode.FINAL_POINT, result.getMode());
        assertEquals(999.0d, result.getWaypointX(), 0.0d);
        assertTrue(result.getAlternatives().isEmpty());
    }

    @Test public void veryNearNonFinalBandsStayDirect() {
        LootMapDecision result = new LootMapPlanner().plan(Collections.singletonList(
                observation(1000, 1000, LootMapDistanceBand.TEN_TO_NINETEEN,
                        LootMapRelativeDirection.AHEAD)), BOUNDS);
        assertEquals(LootMapDecision.Mode.DIRECT, result.getMode());
        assertEquals(988.0d, result.getWaypointY(), 0.0d);
    }

    @Test public void sameObservableHistoryProducesSameDecision() {
        LootMapObservation first = observation(1000, 1000,
                LootMapDistanceBand.TWENTY_TO_FORTY_NINE,
                LootMapRelativeDirection.AHEAD_RIGHT);
        LootMapDecision left = new LootMapPlanner().plan(
                Collections.singletonList(first), BOUNDS);
        LootMapDecision right = new LootMapPlanner().plan(
                Collections.singletonList(first), BOUNDS);
        assertEquals(left.getMode(), right.getMode());
        assertEquals(left.getWaypointX(), right.getWaypointX(), 0.0d);
        assertEquals(left.getWaypointY(), right.getWaypointY(), 0.0d);
        assertEquals(left.getInformationScore(), right.getInformationScore(), 0.0d);
    }

    private static LootMapObservation observation(double x, double y,
                                                   LootMapDistanceBand band,
                                                   LootMapRelativeDirection direction) {
        return new LootMapObservation(x, y, 0.0d, direction, band, NOW);
    }
}
