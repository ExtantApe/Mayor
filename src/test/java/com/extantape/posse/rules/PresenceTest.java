package com.extantape.posse.rules;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.extantape.posse.rules.Players.circle;
import static com.extantape.posse.rules.Players.player;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** D283: the player list and join messages follow chat reach; paused rules show nobody. */
class PresenceTest {
    // Like Leaf Juice after D283: kids and adults circles, the station circle-only.
    private final Reach reach = new Reach(List.of("kids", "adults"), Set.of("station"), Map.of(), List.of("_nether", "_the_end"));
    private final Subject kid = player("kid", "station", circle("kids"));
    private final Subject adult = player("adult", "station", circle("adults"));
    private final Subject visitor = player("visitor", "station", circle("adults"), circle("kids"));
    private final Subject stranger = player("stranger", "station");

    @Test
    void atACircleOnlyStationAKidAndAnAdultDontSeeEachOther() {
        assertFalse(Presence.maySee(adult, kid, true, reach));
        assertFalse(Presence.maySee(kid, adult, true, reach));
    }

    @Test
    void anAdultInTheKidsCircleSeesKidsAndKidsSeeThem() {
        assertTrue(Presence.maySee(visitor, kid, true, reach));
        assertTrue(Presence.maySee(kid, visitor, true, reach));
    }

    @Test
    void aPlayerWithNoCircleAtTheStationSeesNobody() {
        assertFalse(Presence.maySee(stranger, kid, true, reach));
        assertFalse(Presence.maySee(kid, stranger, true, reach));
        assertFalse(Presence.maySee(stranger, adult, true, reach));
    }

    @Test
    void inAnOrdinaryWorldPlayersInTheSameWorldSeeEachOther() {
        Subject a = player("a", "world", circle("adults"));
        Subject b = player("b", "world_nether");
        assertTrue(Presence.maySee(a, b, true, reach));
        assertFalse(Presence.maySee(player("k", "kids_world", circle("kids")), b, true, reach));
    }

    @Test
    void bypassSeesEveryone() {
        Subject mod = player("mod", "elsewhere", Reach.BYPASS);
        assertTrue(Presence.maySee(mod, kid, true, reach));
    }

    @Test
    void presenceEveryoneShowsEveryone() {
        assertTrue(Presence.maySee(adult, kid, false, reach));
    }

    @Test
    void pausedRulesShowNobodyButYourself() {
        assertFalse(Presence.maySee(adult, kid, false, null));
        assertFalse(Presence.maySee(adult, kid, true, null));
        assertTrue(Presence.maySee(kid, kid, true, null));
    }

    @Test
    void aPlayerOutOfSightIsKeptFromTheGameUnlessTheyStandInTheSameWorld() {
        // K832: a game offers every player it knows on Tab, so only someone the viewer may see, or can see walk by, is known.
        assertTrue(Presence.mayKnowOf(true, false));
        assertTrue(Presence.mayKnowOf(true, true));
        assertTrue(Presence.mayKnowOf(false, true));
        assertFalse(Presence.mayKnowOf(false, false));
    }
}
