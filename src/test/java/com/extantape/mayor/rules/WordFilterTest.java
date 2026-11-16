package com.extantape.mayor.rules;

import com.extantape.mayor.config.SettingsLoader;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WordFilterTest {
    private final WordFilter small = WordFilter.of(List.of("ass", "darn*", "go away now", "heck"), List.of("darnation"));

    @Test
    void wholeWordsOnly() {
        assertTrue(small.flags("you ass"));
        assertFalse(small.flags("first class pass"));
        assertFalse(small.flags("assassin"));
        assertFalse(small.flags("as you like"));
        assertFalse(small.flags("shecky"));
    }

    @Test
    void seesThroughDisguises() {
        assertTrue(small.flags("A$$"));
        assertTrue(small.flags("@sssss"));
        assertTrue(small.flags("HECK!"));
        assertTrue(small.flags("h3ck"));
        assertTrue(small.flags("hééck"));
        assertTrue(small.flags("darned it"));
    }

    @Test
    void allowListWins() {
        assertFalse(small.flags("darnation"));
    }

    @Test
    void phrases() {
        assertTrue(small.flags("please GO away... now"));
        assertFalse(small.flags("go away"));
        assertFalse(small.flags("go now away"));
    }

    /** The shipped list against ordinary words that contain a blocked one (the Scunthorpe problem). */
    @Test
    void defaultListFalsePositives() throws Exception {
        WordFilter filter = defaults();
        for (String fine : List.of("Scunthorpe", "class", "assume", "bass", "passage", "assassin", "Essex", "Sussex",
                "cocktail", "peacock", "Hancock", "Dickens", "dictionary", "shitake", "Shiitake", "therapist",
                "analysis", "grape", "hello", "titanic", "titmouse", "button", "cumulus", "sextant", "pass the shell",
                "spicy", "spice", "prickly", "Pissarro", "matchbox", "classic", "Cockburn", "assistant", "basement",
                "go diamond mining", "I will kill you in a duel", "skill yourself up", "wanker", "a wankel engine")) {
            boolean expected = fine.equals("wanker");
            if (expected) assertTrue(filter.flags(fine), fine);
            else assertFalse(filter.flags(fine), fine);
        }
    }

    @Test
    void defaultListHits() throws Exception {
        WordFilter filter = defaults();
        for (String bad : List.of("sh1t", "SHIIIT", "f u c k".replace(" ", ""), "fucking", "b!tch".replace("!", "i"),
                "kys", "kill yourself", "go die", "dumbass", "a$$")) {
            assertTrue(filter.flags(bad), bad);
        }
    }

    private static WordFilter defaults() {
        var settings = new SettingsLoader(n -> SettingsLoader.class.getResourceAsStream("/" + n)).load(null, null, null).settings();
        return settings.filter();
    }

    @Test
    void maskHidesOnlyBlockedWordsAndKeepsTheRest() {
        assertEquals("you ****!", small.mask("you ass!"));
        assertEquals("**** it, ****", small.mask("Darned it, H3CK"));
        assertEquals("well **** **** **** then", small.mask("well go away now then"), "every word of a phrase");
        assertEquals("a first class pass", small.mask("a first class pass"));
        assertEquals("darnation stays", small.mask("darnation stays"), "the allow list");
        assertEquals("  ****  spaced ", small.mask("  @sss  spaced "), "spacing is kept");
        assertEquals("**** and ****", small.mask("hééck and heck"));
    }

    @Test
    void maskLengthGivesNothingAway() {
        assertEquals(small.mask("ass"), small.mask("asssssssss"));
    }

    @Test
    void maskWithNothingBlockedIsTheSameText() {
        String s = "nothing to see";
        assertEquals(s, small.mask(s));
        assertEquals(s, WordFilter.NONE.mask(s));
        assertEquals("", small.mask(""));
    }
}
