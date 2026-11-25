package com.extantape.mayor.config;

import com.extantape.mayor.rules.Reach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsLoaderTest {
    private SettingsLoader.Result load(String config, String filter, String quips) {
        return new SettingsLoader(n -> SettingsLoader.class.getResourceAsStream("/" + n)).load(config, filter, quips);
    }

    @Test
    void noConfigIsVanillaChat() {
        SettingsLoader.Result r = load(null, null, null);
        assertTrue(r.ok(), r.errors().toString());
        assertTrue(r.warnings().isEmpty(), r.warnings().toString());
        assertFalse(r.settings().reach().active());
        assertTrue(r.settings().quips().size() >= 50, "a large pool of notes");
    }

    @Test
    void circlesAndGroups() {
        SettingsLoader.Result r = load("""
                circles: [Red, blue]
                circle-only-worlds: [hall]
                world-groups:
                  home: [meadow, meadow_flat]
                blocked-commands:
                  - commands: [sort]
                    message: "<gray>No."
                """, null, null);
        assertTrue(r.ok(), r.errors().toString());
        Reach reach = r.settings().reach();
        assertEquals(java.util.List.of("red", "blue"), reach.circles());
        assertEquals("home", reach.group("meadow_flat"));
        assertTrue(reach.isCircleOnly("hall_nether"));
    }

    @Test
    void brokenYamlIsAnErrorWithNoSettings() {
        SettingsLoader.Result r = load("circles: [red\nfoo: : :", null, null);
        assertFalse(r.ok());
        assertTrue(r.errors().getFirst().startsWith("config.yml: isn't valid YAML"), r.errors().toString());
    }

    @Test
    void unclearRulesAreErrors() {
        assertFalse(load("circles: [\"has space\"]", null, null).ok());
        assertFalse(load("circles: red", null, null).ok());
        assertFalse(load("world-groups:\n  a: [x]\n  b: [x]\n", null, null).ok());
        assertFalse(load("blocked-commands:\n  - commands: [x]\n", null, null).ok());
        assertFalse(load("nobody-heard:\n  enabled: maybe\n", null, null).ok());
        assertFalse(load(null, "words: nope", null).ok());
    }

    @Test
    void smallSlipsAreWarnings() {
        SettingsLoader.Result r = load("circls: [red]\nmessages:\n  chat-pausd: hi\n", null, null);
        assertTrue(r.ok());
        assertEquals(2, r.warnings().size(), r.warnings().toString());
    }

    @Test
    void messagesOverrideDefaults() {
        SettingsLoader.Result r = load("messages:\n  chat-closed-here: \"<gray>Try talking to the guide!\"\n", null, null);
        assertEquals("<gray>Try talking to the guide!", r.settings().messages().get("chat-closed-here"));
        assertTrue(r.settings().messages().get("filtered-chat").contains("wasn't sent"));
    }

    @Test
    void filterCanBeTurnedOff() {
        assertTrue(load(null, "enabled: false", null).settings().filter().isEmpty());
    }

    @Test
    void maskForViewersIsOffByDefaultAndReadFromFilterYml() {
        assertFalse(load(null, null, null).settings().filtered().maskForViewers());
        SettingsLoader.Result on = load(null, "words: [heck]\nmask-for-viewers: true\n", null);
        assertTrue(on.ok(), on.errors().toString());
        assertTrue(on.settings().filtered().maskForViewers());
        assertTrue(on.warnings().isEmpty(), on.warnings().toString());
    }

    @Test
    void presenceDefaultsToEveryone() {
        assertFalse(load(null, null, null).settings().presenceByReach());
    }

    @Test
    void presenceReachIsRead() {
        SettingsLoader.Result r = load("circles: [kids, adults]\npresence: reach\n", null, null);
        assertTrue(r.ok(), r.errors().toString());
        assertTrue(r.settings().presenceByReach());
    }

    @Test
    void presenceWithATypoIsAnError() {
        SettingsLoader.Result r = load("circles: [kids]\npresence: rech\n", null, null);
        assertFalse(r.ok());
        assertTrue(r.errors().toString().contains("presence must be everyone or reach"), r.errors().toString());
    }

    @Test
    void presenceReachWithoutCirclesWarns() {
        SettingsLoader.Result r = load("presence: reach\n", null, null);
        assertTrue(r.ok(), r.errors().toString());
        assertTrue(r.warnings().toString().contains("presence: reach has no effect"), r.warnings().toString());
    }

    private static final String HOME = """
            circles: [kids, adults]
            home-worlds:
              kids: { worlds: [kidland, kidland_nether], fallback: kidland, message: "Hi" }
              no-circle: { fallback: station }
            """;

    @Test
    void homeWorldsAreOffByDefault() {
        SettingsLoader.Result r = load(null, null, null);
        assertFalse(r.settings().homeWorlds().enabled());
        assertTrue(r.warnings().isEmpty(), r.warnings().toString());
    }

    @Test
    void homeWorldsAreRead() {
        SettingsLoader.Result r = load(HOME, null, null);
        assertTrue(r.ok(), r.errors().toString());
        var home = r.settings().homeWorlds();
        assertTrue(home.enabled());
        assertEquals("kidland", home.byCircle().get("kids").fallback());
        assertEquals("Hi", home.byCircle().get("kids").message());
        assertEquals("station", home.noCircle().fallback());
    }

    @Test
    void homeWorldsMistakesAreErrorsSoNothingHalfAppliesToKids() {
        for (String bad : java.util.List.of(
                "circles: [kids]\nhome-worlds:\n  kids: { worlds: [a], fallback: b }\n",
                "circles: [kids]\nhome-worlds:\n  kids: { worlds: [a] }\n",
                "circles: [kids]\nhome-worlds:\n  kids: { fallback: a }\n",
                "circles: [kids]\nhome-worlds:\n  grown: { worlds: [a], fallback: a }\n",
                "circles: [kids]\nhome-worlds:\n  no-circle: {}\n",
                "circles: [kids]\nhome-worlds: [a]\n",
                "home-worlds:\n  no-circle: { fallback: station }\n")) {
            assertFalse(load(bad, null, null).ok(), bad);
        }
    }

    @Test
    void bookAuthorsAreHiddenByDefault() {
        SettingsLoader.Result r = load(null, null, null);
        assertTrue(r.settings().bookAuthors().hide());
        assertEquals("Unknown", r.settings().bookAuthors().shownAs());
        assertTrue(r.warnings().isEmpty(), r.warnings().toString());
    }

    @Test
    void bookAuthorsCanBeTurnedOffAndRenamed() {
        SettingsLoader.Result r = load("book-authors:\n  hide: false\n  shown-as: Somebody\n", null, null);
        assertTrue(r.ok(), r.errors().toString());
        assertFalse(r.settings().bookAuthors().hide());
        assertEquals("Somebody", r.settings().bookAuthors().shownAs());
    }

    @Test
    void aBlankShownAsFallsBackToUnknownAndATypoIsAnError() {
        assertEquals("Unknown", load("book-authors:\n  shown-as: \"\"\n", null, null).settings().bookAuthors().shownAs());
        SettingsLoader.Result bad = load("book-authors:\n  hide: maybe\n", null, null);
        assertFalse(bad.ok());
        assertTrue(bad.errors().toString().contains("book-authors.hide must be true or false"), bad.errors().toString());
    }
}
