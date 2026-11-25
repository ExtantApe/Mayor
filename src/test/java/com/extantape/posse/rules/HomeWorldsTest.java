package com.extantape.posse.rules;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import static com.extantape.posse.rules.Players.circle;
import static com.extantape.posse.rules.Players.player;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HomeWorldsTest {
    private static final Set<String> WORLDS = Set.of("main", "kidland", "kidland_nether", "kidland_the_end", "grownups", "station", "cave");
    private static final Predicate<String> EXISTS = WORLDS::contains;
    private static final Reach REACH = new Reach(List.of("kids", "adults"), Set.of(), Map.of(), List.of("_nether", "_the_end"));

    private static HomeWorlds configured() {
        Map<String, HomeWorlds.Entry> by = new LinkedHashMap<>();
        by.put("kids", new HomeWorlds.Entry(Set.of("kidland", "kidland_nether", "kidland_the_end"), "kidland", "Hello kid"));
        return new HomeWorlds(by, new HomeWorlds.Entry(Set.of(), "station", null));
    }

    private static HomeWorlds.Move decide(HomeWorlds h, Subject s) {
        return h.decide(REACH, s, s.world(), EXISTS, "main");
    }

    @Test
    void aKidInAKidsWorldStays() {
        for (String w : List.of("kidland", "kidland_nether", "kidland_the_end")) {
            assertNull(decide(configured(), player("Kit", w, circle("kids"))), w);
        }
    }

    @Test
    void aKidInTheAdultsWorldTheStationOrACaveIsMovedToTheirFallback() {
        for (String w : List.of("grownups", "station", "cave", "main")) {
            HomeWorlds.Move m = decide(configured(), player("Kit", w, circle("kids")));
            assertEquals("kidland", m.world(), w);
            assertFalse(m.degraded());
            assertEquals(HomeWorlds.Kind.CIRCLE, m.kind());
            assertEquals("Hello kid", m.message());
        }
    }

    @Test
    void theVisitorInBothCirclesIsNeverMoved() {
        assertNull(decide(configured(), player("Vee", "grownups", circle("kids"), circle("adults"))));
        assertNull(decide(configured(), player("Vee", "station", circle("kids"), circle("adults"))));
    }

    @Test
    void anAdultIsNeverMoved() {
        assertNull(decide(configured(), player("Ada", "grownups", circle("adults"))));
        assertNull(decide(configured(), player("Ada", "kidland", circle("adults"))));
    }

    @Test
    void someoneWithNoCircleGoesToTheStationUnlessAlreadyThere() {
        HomeWorlds.Move m = decide(configured(), player("New", "grownups"));
        assertEquals("station", m.world());
        assertEquals(HomeWorlds.Kind.NO_CIRCLE, m.kind());
        assertNull(m.message());
        assertNull(decide(configured(), player("New", "station")));
    }

    @Test
    void aMissingFallbackFailsClosedToTheMainWorld() {
        Predicate<String> noKidland = w -> !w.equals("kidland") && EXISTS.test(w);
        HomeWorlds.Move m = configured().decide(REACH, player("Kit", "grownups", circle("kids")), "grownups", noKidland, "main");
        assertEquals("main", m.world());
        assertTrue(m.degraded());
        assertEquals("kidland", m.wanted());
        // Already in the main world, there is nowhere further to go.
        assertNull(configured().decide(REACH, player("Kit", "main", circle("kids")), "main", noKidland, "main"));
        // A missing station does the same for someone with no circle.
        Predicate<String> noStation = w -> !w.equals("station") && EXISTS.test(w);
        assertTrue(configured().decide(REACH, player("New", "grownups"), "grownups", noStation, "main").degraded());
    }

    @Test
    void aKidWhoHoldsOnlyKidsCirclesAcrossTwoEntriesMayBeInEitherSet() {
        Map<String, HomeWorlds.Entry> by = new LinkedHashMap<>();
        by.put("kids", new HomeWorlds.Entry(Set.of("kidland"), "kidland", null));
        by.put("adults", new HomeWorlds.Entry(Set.of("grownups"), "grownups", null));
        HomeWorlds both = new HomeWorlds(by, null);
        assertNull(decide(both, player("Vee", "grownups", circle("kids"), circle("adults"))));
        assertEquals("kidland", decide(both, player("Vee", "cave", circle("kids"), circle("adults"))).world());
    }

    @Test
    void theRespawnPointIsCheckedNotTheCurrentWorld() {
        Subject kid = player("Kit", "kidland", circle("kids"));
        assertEquals("kidland", configured().decide(REACH, kid, "grownups", EXISTS, "main").world());
    }

    @Test
    void offMovesNobody() {
        assertFalse(HomeWorlds.OFF.enabled());
        assertNull(decide(HomeWorlds.OFF, player("Kit", "grownups", circle("kids"))));
        assertNull(decide(HomeWorlds.OFF, player("New", "grownups")));
    }

    @Test
    void withNoCirclesInUseNobodyIsMoved() {
        Reach none = new Reach(List.of(), Set.of(), Map.of(), List.of());
        assertNull(configured().decide(none, player("New", "grownups"), "grownups", EXISTS, "main"));
    }

    @Test
    void namedWorldsListsEveryWorldTheSectionMentions() {
        assertEquals(Set.of("kidland", "kidland_nether", "kidland_the_end", "station"), configured().namedWorlds());
    }
}
