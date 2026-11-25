package com.extantape.posse.rules;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.extantape.posse.rules.Players.circle;
import static com.extantape.posse.rules.Players.player;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The rename to Posse keeps no old permission nodes (D196, D292): the old mayor.* nodes grant nothing. */
class OldNodesTest {
    private final Reach reach = new Reach(List.of("kids", "adults"), Set.of("hall"), Map.of(), List.of("_nether"));

    @Test
    void anOldCircleNodePutsNobodyInACircle() {
        Subject old = player("Kit", "hall", "mayor.circle.kids", "mayor.circle.adults");
        assertTrue(reach.circlesOf(old).isEmpty());
        assertFalse(reach.maySpeak(old));
        assertFalse(reach.mayReach(old, player("Kay", "hall", circle("kids"))));
        assertEquals(1, reach.circlesOf(player("Kit", "hall", circle("kids"), "mayor.circle.adults")).size());
    }

    @Test
    void anOldBypassNodeBypassesNothing() {
        Subject old = player("Mod", "forest", "mayor.bypass", "mayor.filter.bypass");
        assertFalse(reach.mayReach(old, player("Kay", "meadow", circle("kids"))));
        assertFalse(reach.mayReach(player("Kay", "meadow", circle("kids")), old));
        assertFalse(reach.maySpeak(player("Mod", "hall", "mayor.bypass")));
        assertFalse(old.has(Reach.BYPASS));
        assertFalse(old.has(WordFilter.BYPASS));
    }

    @Test
    void anOldCircleNodeLeavesAKidInNoCircleForHomeWorlds() {
        Map<String, HomeWorlds.Entry> by = new java.util.LinkedHashMap<>();
        by.put("kids", new HomeWorlds.Entry(Set.of("kidland"), "kidland", null));
        HomeWorlds home = new HomeWorlds(by, new HomeWorlds.Entry(Set.of(), "station", null));
        Subject old = player("Kit", "grownups", "mayor.circle.kids");
        assertEquals("station", home.decide(reach, old, "grownups", w -> true, "main").world());
        assertNull(home.decide(reach, player("Kit", "kidland", circle("kids")), "kidland", w -> true, "main"));
    }
}
