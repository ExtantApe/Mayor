package com.extantape.mayor.rules;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.extantape.mayor.rules.Players.circle;
import static com.extantape.mayor.rules.Players.player;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** D283: which viewers get a join or leave line under presence: reach. */
class PresenceMessagesTest {
    private final Reach reach = new Reach(List.of("kids", "adults"), Set.of("station"), Map.of(), List.of("_nether", "_the_end"));
    private final Subject kid = player("kid", "station", circle("kids"));
    private final Subject otherKid = player("otherKid", "station", circle("kids"));
    private final Subject adult = player("adult", "station", circle("adults"));
    private final Subject otherAdult = player("otherAdult", "station", circle("adults"));
    private final Subject visitor = player("visitor", "station", circle("adults"), circle("kids"));
    private final List<Subject> online = List.of(kid, otherKid, adult, otherAdult, visitor);

    private final List<String> console = new ArrayList<>();

    private List<String> receivers(String message, Subject source, boolean byReach, Reach rules) {
        List<String> got = new ArrayList<>();
        PresenceMessages.deliver(message, source, online, byReach, rules, v -> got.add(v.name()), console::add);
        got.sort(null);
        return got;
    }

    @Test
    void aKidsJoinReachesTheirCircleAndTheVisitorOnly() {
        assertEquals(List.of("otherKid", "visitor"), receivers("kid joined", kid, true, reach));
    }

    @Test
    void anAdultsJoinReachesTheirCircleAndTheVisitorOnly() {
        assertEquals(List.of("otherAdult", "visitor"), receivers("adult joined", adult, true, reach));
    }

    @Test
    void theVisitorInBothCirclesIsSeenByEveryone() {
        assertEquals(List.of("adult", "kid", "otherAdult", "otherKid"), receivers("visitor left", visitor, true, reach));
    }

    @Test
    void theConsoleAlwaysGetsTheLineEvenWhenNoPlayerDoes() {
        Subject loner = player("loner", "station", circle("kids"));
        List<Subject> alone = List.of(loner, adult);
        List<String> got = new ArrayList<>();
        int sent = PresenceMessages.deliver("loner joined", loner, alone, true, reach, v -> got.add(v.name()), console::add);
        assertEquals(0, sent);
        assertEquals(List.of(), got);
        assertEquals(List.of("loner joined"), console);
    }

    @Test
    void theJoiningPlayerIsNotSentTheirOwnLine() {
        assertEquals(false, receivers("kid joined", kid, true, reach).contains("kid"));
    }

    @Test
    void aNullMessageSendsNothingAnywhere() {
        // Essentials with custom-join-message: none sets the message to null.
        assertEquals(List.of(), receivers(null, kid, true, reach));
        assertEquals(List.of(), console);
    }

    @Test
    void everyoneSeesEveryoneWhenReachIsNotSet() {
        assertEquals(List.of("adult", "otherAdult", "otherKid", "visitor"), receivers("kid joined", kid, false, reach));
    }

    @Test
    void pausedRulesSendToTheConsoleOnly() {
        assertEquals(List.of(), receivers("kid joined", kid, true, null));
        assertEquals(List.of("kid joined"), console);
    }
}
