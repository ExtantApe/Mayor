package com.extantape.posse.rules;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.extantape.posse.rules.Players.circle;
import static com.extantape.posse.rules.Players.pending;
import static com.extantape.posse.rules.Players.player;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** K832: who gets a death or advancement line. Kids and adults stand in different worlds and share no circle. */
class AnnouncementsTest {
    private final Reach reach = new Reach(List.of("kids", "adults"), Set.of("station"), Map.of(), List.of("_nether", "_the_end"));
    private final Subject kid = player("kid", "kidland", circle("kids"));
    private final Subject otherKid = player("otherKid", "kidland_nether", circle("kids"));
    private final Subject adult = player("adult", "grownups", circle("adults"));
    private final Subject otherAdult = player("otherAdult", "grownups", circle("adults"));
    private final Subject visitor = player("visitor", "grownups", circle("adults"), circle("kids"));
    private final Subject moderator = player("moderator", "grownups", Reach.BYPASS);
    private final Subject nobody = player("nobody", "station");
    private final List<Subject> online = List.of(kid, otherKid, adult, otherAdult, visitor, moderator, nobody);

    private final List<String> console = new ArrayList<>();

    private List<String> receivers(Subject about, Subject also, Reach rules) {
        List<String> got = new ArrayList<>();
        Announcements.deliver(about.name() + " died", about, also, online, rules, v -> got.add(v.name()), console::add);
        got.sort(null);
        return got;
    }

    @Test
    void aKidsDeathLineReachesTheirCircleTheVisitorAndBypassButNoAdult() {
        assertEquals(List.of("kid", "moderator", "otherKid", "visitor"), receivers(kid, null, reach));
    }

    @Test
    void anAdultsDeathLineReachesNoKid() {
        assertEquals(List.of("adult", "moderator", "otherAdult", "visitor"), receivers(adult, null, reach));
    }

    @Test
    void theConsoleAlwaysGetsTheLineOnce() {
        receivers(kid, null, reach);
        assertEquals(List.of("kid died"), console);
    }

    @Test
    void pausedRulesReachOnlyThePlayerAndBypass() {
        assertEquals(List.of("kid", "moderator"), receivers(kid, null, null));
        assertEquals(List.of("kid died"), console);
    }

    @Test
    void aNullMessageSendsNothingAnywhere() {
        List<String> got = new ArrayList<>();
        assertEquals(0, Announcements.<String>deliver(null, kid, null, online, reach, v -> got.add(v.name()), console::add));
        assertEquals(List.of(), got);
        assertEquals(List.of(), console);
    }

    @Test
    void aLineThatNamesAKillerNeedsBothToBeInReach() {
        // The visitor is slain by an adult. The kids share a circle with the visitor, but may not learn the adult's name.
        assertEquals(List.of("adult", "moderator", "otherAdult", "visitor"), receivers(visitor, adult, reach));
        // The player it is about always gets it, even when they can't reach whoever it names.
        assertTrue(Announcements.mayHear(kid, reach, kid, adult));
        assertTrue(Announcements.mayHear(adult, reach, visitor, adult));
    }

    @Test
    void aPendingPlayerHearsOnlyTheirOwnLineAndNobodyHearsTheirs() {
        Subject fresh = pending("fresh", "kidland", circle("kids"));
        assertFalse(Announcements.mayHear(fresh, reach, kid, null));
        assertFalse(Announcements.mayHear(otherKid, reach, fresh, null));
        assertTrue(Announcements.mayHear(fresh, reach, fresh, null));
    }

    @Test
    void aViewerPosseCannotWorkOutGetsNothing() {
        Subject broken = new Subject() {
            @Override public UUID id() { return UUID.nameUUIDFromBytes("broken".getBytes()); }
            @Override public String name() { return "broken"; }
            @Override public String world() { throw new IllegalStateException("no world"); }
            @Override public boolean has(String permission) { throw new IllegalStateException("no permissions"); }
        };
        assertFalse(Announcements.mayHear(broken, reach, kid, null));
        assertFalse(Announcements.mayHear(broken, null, kid, null));
        // The others still get theirs.
        List<String> got = new ArrayList<>();
        Announcements.deliver("kid died", kid, null, List.of(broken, otherKid), reach, v -> got.add(v.name()), console::add);
        assertEquals(List.of("otherKid"), got);
    }

    @Test
    void aLinePosseCannotWorkOutStillReachesThePlayerAndBypassOnly() {
        // The player the line is about can't be read: nobody in their circle can be sure, so only they and bypass get it.
        Subject unreadable = new Subject() {
            @Override public UUID id() { return UUID.nameUUIDFromBytes("unreadable".getBytes()); }
            @Override public String name() { return "unreadable"; }
            @Override public String world() { throw new IllegalStateException("no world"); }
            @Override public boolean has(String permission) { throw new IllegalStateException("no permissions"); }
        };
        assertTrue(Announcements.mayHear(unreadable, reach, unreadable, null));
        assertTrue(Announcements.mayHear(moderator, reach, unreadable, null));
        assertFalse(Announcements.mayHear(otherKid, reach, unreadable, null));
        assertFalse(Announcements.mayHear(adult, reach, unreadable, null));
    }

    @Test
    void withNoCirclesEveryoneHearsAsInVanilla() {
        Reach off = new Reach(List.of(), Set.of(), Map.of(), List.of());
        assertEquals(7, receivers(kid, null, off).size());
    }
}
