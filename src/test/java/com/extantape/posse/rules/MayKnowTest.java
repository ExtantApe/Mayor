package com.extantape.posse.rules;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static com.extantape.posse.rules.Players.circle;
import static com.extantape.posse.rules.Players.player;
import static org.junit.jupiter.api.Assertions.*;

/** D231: who may know whose grave it is. Kids and adults in separate worlds, a mixed circle, and no rules at all. */
class MayKnowTest {
    // kids play in "kids" (with its nether), adults in "survival"; "family" is the circle parents and kids share.
    private final Reach reach = new Reach(List.of("family", "adults"), Set.of("lobby"), Map.of(), List.of("_nether"));

    @Test
    void noCirclesMeansOnlyTheOwnerKnows() {
        Reach off = new Reach(List.of(), Set.of(), Map.of(), List.of());
        assertFalse(off.mayKnow(player("kid", "kids"), player("stranger", "survival"), "survival", true));
        Subject kid = player("kid", "kids");
        assertTrue(off.mayKnow(kid, kid, "kids", false));
    }

    @Test
    void ownerAlwaysKnowsTheirOwn() {
        Subject kid = player("kid", "survival");
        assertTrue(reach.mayKnow(kid, kid, "kids", true));
    }

    @Test
    void bothInTheGravesWorldGroupNowKnow() {
        assertTrue(reach.mayKnow(player("kid2", "kids_nether"), player("kid", "kids"), "kids", true));
    }

    @Test
    void ownerWhoLeftTheWorldOrIsOfflineCountsOnlyByCircle() {
        // An adult stranger who died in the kids' world and went back, or logged off: the kids don't see the name.
        assertFalse(reach.mayKnow(player("kid", "kids"), player("stranger", "survival", circle("adults")), "kids", true));
        assertFalse(reach.mayKnow(player("kid", "kids"), player("stranger", "kids", circle("adults")), "kids", false));
    }

    @Test
    void kidElsewhereDoesNotKnowAnAdultsGrave() {
        assertFalse(reach.mayKnow(player("kid", "kids"), player("stranger", "survival", circle("adults")), "survival", true));
    }

    @Test
    void adultInAnotherWorldDoesNotKnowAKidsGrave() {
        assertFalse(reach.mayKnow(player("stranger", "survival", circle("adults")), player("kid", "kids"), "kids", true));
    }

    @Test
    void sharedCircleKnowsAcrossWorlds() {
        assertTrue(reach.mayKnow(player("mum", "survival", circle("family")), player("kid", "kids", circle("family")), "kids", true));
    }

    @Test
    void circleOnlyWorldNeedsASharedCircle() {
        assertFalse(reach.mayKnow(player("a", "lobby"), player("b", "lobby"), "lobby", true));
        assertTrue(reach.mayKnow(player("a", "lobby", circle("family")), player("b", "lobby", circle("family")), "lobby", true));
    }

    @Test
    void bypassOnEitherSideKnows() {
        assertTrue(reach.mayKnow(player("admin", "survival", Reach.BYPASS), player("kid", "kids"), "kids", true));
        assertTrue(reach.mayKnow(player("kid", "kids_nether"), player("admin", "survival", Reach.BYPASS), "survival", true));
    }

    @Test
    void unknownOwnerFailsClosed() {
        assertFalse(reach.mayKnow(player("kid", "kids"), null, "kids", true));
        assertFalse(reach.mayKnow(player("kid", "kids"), player("x", "kids"), null, true));
    }

    @Test
    void offlineOwnerIsRecalledFromWhatPosseSaw(@TempDir Path dir) {
        KnownPlayers known = new KnownPlayers(dir, Logger.getAnonymousLogger());
        Subject kid = player("kid", "kids", circle("family"));
        assertTrue(known.remember(kid, reach.circles()));
        known.save();
        KnownPlayers again = new KnownPlayers(dir, Logger.getAnonymousLogger());
        again.load();
        Subject recalled = again.recall(kid.id());
        assertNotNull(recalled);
        assertTrue(reach.mayKnow(player("mum", "survival", circle("family")), recalled, "kids", false));
        assertFalse(reach.mayKnow(player("stranger", "survival", circle("adults")), recalled, "kids", false));
        assertFalse(reach.mayKnow(player("kid2", "kids"), recalled, "kids", false), "offline, no shared circle: no");
        assertNull(again.recall(UUID.randomUUID()));
    }

    @Test
    void rememberReportsOnlyChanges(@TempDir Path dir) {
        KnownPlayers known = new KnownPlayers(dir, Logger.getAnonymousLogger());
        Subject kid = player("kid", "kids", circle("family"));
        assertTrue(known.remember(kid, reach.circles()));
        assertFalse(known.remember(kid, reach.circles()));
    }

    @Test
    void brokenFileIsMovedAsideAndNobodyIsKnown(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve(KnownPlayers.FILE), "- a list, not a map\n");
        KnownPlayers known = new KnownPlayers(dir, Logger.getAnonymousLogger());
        known.load();
        assertTrue(Files.exists(dir.resolve(KnownPlayers.FILE + ".bak")));
        assertNull(known.recall(UUID.nameUUIDFromBytes("kid".getBytes())));
    }
}
