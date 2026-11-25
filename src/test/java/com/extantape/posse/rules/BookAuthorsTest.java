package com.extantape.posse.rules;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.extantape.posse.rules.Players.circle;
import static com.extantape.posse.rules.Players.player;
import static org.junit.jupiter.api.Assertions.*;

/** D291: who sees a book's author. Kids, adults, a visitor in both circles, and the cases that fail closed. */
class BookAuthorsTest {
    private final Reach reach = new Reach(List.of("kids", "adults"), Set.of("lobby"), Map.of(), List.of("_nether"));
    private final Subject kid = player("kidwriter", "kids", circle("kids"));
    private final Subject kidFriend = player("kidfriend", "kids", circle("kids"));
    private final Subject adult = player("adultreader", "kids", circle("adults"));
    private final Subject visitor = player("visitor", "kids", circle("kids"), circle("adults"));

    private boolean shows(Subject viewer, Subject author) {
        return BookAuthors.mayShow(true, reach, viewer, author.name(), null, true, author);
    }

    @Test
    void theAuthorAndTheirCircleSeeTheName() {
        assertTrue(shows(kid, kid));
        assertTrue(shows(kidFriend, kid));
    }

    @Test
    void anAdultWithNoSharedCircleSeesNoNameEvenStandingInTheSameWorld() {
        assertFalse(shows(adult, kid));
        assertFalse(shows(kid, adult));
    }

    @Test
    void theVisitorInBothCirclesSeesBoth() {
        assertTrue(shows(visitor, kid));
        assertTrue(shows(visitor, adult));
        assertTrue(shows(kid, visitor));
        assertTrue(shows(adult, visitor));
    }

    @Test
    void bypassSeesAndIsSeenAsInChat() {
        Subject admin = player("admin", "kids", Reach.BYPASS);
        assertTrue(shows(admin, kid));
        assertTrue(shows(adult, admin));
    }

    @Test
    void anAuthorMayerNeverSawIsHiddenButABypassViewerSeesIt() {
        assertFalse(BookAuthors.mayShow(true, reach, kid, "ghostwriter", null, true, null));
        Subject admin = player("admin", "kids", Reach.BYPASS);
        assertTrue(BookAuthors.mayShow(true, reach, admin, "ghostwriter", null, true, null));
    }

    @Test
    void aNameThatIsNoPlayerIsLeftAlone() {
        assertTrue(BookAuthors.mayShow(true, reach, kid, "Pyrotechnician", null, false, null));
    }

    @Test
    void nobodyHoldingItMeansHidden() {
        assertFalse(BookAuthors.mayShow(true, reach, null, "kidwriter", null, true, kid));
    }

    @Test
    void withTheSettingOffOrNoCirclesNothingIsHidden() {
        assertTrue(BookAuthors.mayShow(false, reach, adult, "kidwriter", null, true, kid));
        Reach none = new Reach(List.of(), Set.of(), Map.of(), List.of());
        assertTrue(BookAuthors.mayShow(true, none, adult, "kidwriter", null, true, kid));
    }

    @Test
    void theTaggedIdDecidesWhoIsTheAuthorNotTheName() {
        UUID kidId = kid.id();
        // The book says "adultreader", but it was signed by the kid: only the kid is "self".
        assertTrue(BookAuthors.mayShow(true, reach, kid, "adultreader", kidId, true, kid));
        assertFalse(BookAuthors.mayShow(true, reach, adult, "adultreader", kidId, true, kid));
    }

    @Test
    void whilePausedOnlyTheAuthorSeesTheName() {
        assertTrue(BookAuthors.mayShowPaused(kid, "KidWriter", null));
        assertTrue(BookAuthors.mayShowPaused(kid, "whatever", kid.id()));
        assertFalse(BookAuthors.mayShowPaused(kidFriend, "kidwriter", null));
        assertFalse(BookAuthors.mayShowPaused(visitor, "kidwriter", null));
        assertFalse(BookAuthors.mayShowPaused(null, "kidwriter", null));
    }

    @Test
    void aTitleHoldingTheNameShowsStarsInstead() {
        assertEquals("**** and the Lantern", BookAuthors.maskTitle("KidWriter and the Lantern", "kidwriter"));
        assertEquals("**** journal, ****'s", BookAuthors.maskTitle("kidwriter journal, kidwriter's", "KidWriter"));
        assertEquals("Journal", BookAuthors.maskTitle("Journal", "kidwriter"));
        assertEquals("Journal", BookAuthors.maskTitle("Journal", " "));
        assertNull(BookAuthors.maskTitle(null, "kidwriter"));
    }

    @Test
    void reachNameRuleItselfFailsClosedForAnUnknownAuthor() {
        assertFalse(reach.mayKnowName(kid, null));
        Reach none = new Reach(List.of(), Set.of(), Map.of(), List.of());
        assertTrue(none.mayKnowName(kid, null));
    }
}
