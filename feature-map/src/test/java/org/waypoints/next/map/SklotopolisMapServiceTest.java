package org.waypoints.next.map;

import org.junit.Test;

import static org.junit.Assert.assertNotEquals;

public final class SklotopolisMapServiceTest {
    @Test public void sharedGamePortUsesSeparateCacheForEachWorld() {
        String endpoint = "176.9.149.249:3724";
        assertNotEquals(SklotopolisMapService.cacheDirectoryName(
                        endpoint, SklotopolisMapProfiles.CAZA),
                SklotopolisMapService.cacheDirectoryName(
                        endpoint, SklotopolisMapProfiles.INFINITY));
    }

    @Test public void infinityRoundsCannotReuseEachOthersSurfaceCache() {
        String endpoint = "176.9.149.249:3724";
        assertNotEquals(SklotopolisMapService.cacheDirectoryName(
                        endpoint, SklotopolisMapProfiles.OLD_INFINITY),
                SklotopolisMapService.cacheDirectoryName(
                        endpoint, SklotopolisMapProfiles.INFINITY));
    }
}
