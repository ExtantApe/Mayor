package com.extantape.posse.rules;

import org.junit.jupiter.api.Test;

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

/** K832: which names Tab may offer. Kids and adults stand in different worlds and share no circle. */
class NameSuggestionsTest {
    private final Reach reach = new Reach(List.of("kids", "adults"), Set.of("station"), Map.of(), List.of("_nether", "_the_end"));
    private final Subject kid = player("Kid", "kidland", circle("kids"));
    private final Subject otherKid = player("OtherKid", "kidland_nether", circle("kids"));
    private final Subject adult = player("Adult", "grownups", circle("adults"));
    private final Subject visitor = player("Visitor", "grownups", circle("adults"), circle("kids"));
    private final Subject moderator = player("Moderator", "grownups", Reach.BYPASS);
    private final List<Subject> online = List.of(kid, otherKid, adult, visitor, moderator);

    @Test
    void anAdultIsOfferedNoKidAndAKidNoAdult() {
        assertEquals(Set.of("kid", "otherkid"), NameSuggestions.withheld(adult, online, reach));
        assertEquals(Set.of("adult"), NameSuggestions.withheld(kid, online, reach));
    }

    @Test
    void theVisitorAndBypassAreOfferedEveryone() {
        assertEquals(Set.of(), NameSuggestions.withheld(visitor, online, reach));
        assertEquals(Set.of(), NameSuggestions.withheld(moderator, online, reach));
    }

    @Test
    void pausedRulesOfferOnlyYourOwnNameUnlessYouHaveBypass() {
        assertEquals(Set.of("otherkid", "adult", "visitor", "moderator"), NameSuggestions.withheld(kid, online, null));
        assertEquals(Set.of(), NameSuggestions.withheld(moderator, online, null));
    }

    @Test
    void aPendingPlayerIsOfferedToNobodyAndOfferedNobody() {
        Subject fresh = pending("Fresh", "kidland", circle("kids"));
        assertTrue(NameSuggestions.withheld(otherKid, List.of(otherKid, fresh), reach).contains("fresh"));
        assertEquals(Set.of("otherkid"), NameSuggestions.withheld(fresh, List.of(otherKid, fresh), reach));
    }

    @Test
    void anAskerPosseCannotWorkOutIsOfferedNobodyElse() {
        Subject broken = new Subject() {
            @Override public UUID id() { return UUID.nameUUIDFromBytes("broken".getBytes()); }
            @Override public String name() { return "Broken"; }
            @Override public String world() { throw new IllegalStateException("no world"); }
            @Override public boolean has(String permission) { throw new IllegalStateException("no permissions"); }
        };
        assertEquals(Set.of("kid", "otherkid", "adult", "visitor", "moderator"), NameSuggestions.withheld(broken, online, reach));
        assertEquals(Set.of("kid", "otherkid", "adult", "visitor", "moderator"), NameSuggestions.withheld(broken, online, null));
    }

    @Test
    void namesAreTakenOutInAnyLetterCaseAndInQuotesAndEverythingElseStays() {
        Set<String> withheld = NameSuggestions.withheld(adult, online, reach);
        List<String> offered = List.of("@a", "Adult", "Kid", "kid", "\"OtherKid\"", "'kid'", "Kidney", "Visitor", "survival");
        assertEquals(List.of("@a", "Adult", "Kidney", "Visitor", "survival"), NameSuggestions.keep(offered, withheld));
        assertFalse(NameSuggestions.isWithheld(null, withheld));
    }

    @Test
    void withNoCirclesNothingIsWithheld() {
        Reach off = new Reach(List.of(), Set.of(), Map.of(), List.of());
        assertEquals(Set.of(), NameSuggestions.withheld(adult, online, off));
    }
}
