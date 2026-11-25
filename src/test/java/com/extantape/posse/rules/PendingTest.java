package com.extantape.posse.rules;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static com.extantape.posse.rules.Players.circle;
import static com.extantape.posse.rules.Players.pending;
import static com.extantape.posse.rules.Players.player;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A player whose delayed first-join check hasn't run reaches nobody, and nobody reaches them (D297). */
class PendingTest {
    private final Reach reach = new Reach(List.of("kids", "adults"), Set.of(), Map.of(), List.of("_nether"));

    @Test
    void aPendingPlayerReachesNobodyAndNobodyReachesThem() {
        Subject kid = pending("New", "main", circle("kids"));
        Subject same = player("Kay", "main", circle("kids"));
        assertFalse(reach.mayReach(kid, same));
        assertFalse(reach.mayReach(same, kid));
        assertEquals(Reach.Why.PENDING, reach.why(kid, same));
        assertFalse(reach.why(kid, same).reaches);
    }

    @Test
    void pendingBeatsBypassAndTheSameWorld() {
        assertFalse(reach.mayReach(pending("New", "main"), player("Mod", "main", Reach.BYPASS)));
        assertFalse(reach.mayReach(player("Mod", "main", Reach.BYPASS), pending("New", "main")));
    }

    @Test
    void aPendingPlayerStillReachesThemselfAndIsSeenByNobodyInTheList() {
        Subject kid = pending("New", "main", circle("kids"));
        assertTrue(reach.mayReach(kid, kid));
        Subject other = player("Kay", "main", circle("kids"));
        for (boolean byReach : new boolean[] {true, false}) {
            assertFalse(Presence.maySee(other, kid, byReach, reach));
            assertFalse(Presence.maySee(kid, other, byReach, reach));
            assertTrue(Presence.maySee(kid, kid, byReach, reach));
        }
    }

    @Test
    void noJoinLineGoesToAnyoneWhileThePlayerIsPendingAndItGoesOnceTheyAreNot() {
        Subject kid = pending("New", "main", circle("kids"));
        Subject other = player("Kay", "main", circle("kids"));
        AtomicInteger heard = new AtomicInteger();
        assertEquals(0, PresenceMessages.deliver("joined", kid, List.of(other, kid), false, reach, v -> heard.incrementAndGet(), m -> {}));
        assertEquals(0, heard.get());
        Subject placed = player("New", "main", circle("kids"));
        assertEquals(1, PresenceMessages.deliver("joined", placed, List.of(other, placed), false, reach, v -> heard.incrementAndGet(), m -> {}));
    }

    @Test
    void homeWorldsOnlyWaitsForPlayersItCanMove() {
        Map<String, HomeWorlds.Entry> by = new LinkedHashMap<>();
        by.put("kids", new HomeWorlds.Entry(Set.of("kidland"), "kidland", null));
        HomeWorlds home = new HomeWorlds(by, null);
        assertTrue(home.governs(reach, player("Kit", "main", circle("kids"))));
        assertFalse(home.governs(reach, player("Ada", "main", circle("adults"))));
        assertFalse(home.governs(reach, player("Vee", "main", circle("kids"), circle("adults"))));
        assertFalse(home.governs(reach, player("New", "main")));
        HomeWorlds withStation = new HomeWorlds(by, new HomeWorlds.Entry(Set.of(), "station", null));
        assertTrue(withStation.governs(reach, player("New", "main")));
        assertFalse(HomeWorlds.OFF.governs(reach, player("Kit", "main", circle("kids"))));
    }
}
