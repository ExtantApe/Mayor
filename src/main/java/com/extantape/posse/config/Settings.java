package com.extantape.posse.config;

import com.extantape.posse.rules.CommandRules;
import com.extantape.posse.rules.Reach;
import com.extantape.posse.rules.WordFilter;

import java.util.List;
import java.util.Map;

/** Everything read from config.yml, filter.yml and quips.yml, ready to use. Replaced whole on reload. */
public record Settings(
        Reach reach,
        CommandRules commands,
        WordFilter filter,
        Filtered filtered,
        NobodyHeard nobodyHeard,
        List<String> quips,
        Map<String, String> messages,
        boolean presenceByReach,
        com.extantape.posse.rules.HomeWorlds homeWorlds,
        BookAuthors bookAuthors) {

    /** A short sentence for the log about the word filter. */
    public String filterSummary() {
        return filter.isEmpty() ? "The word filter is off." : "The word filter is on.";
    }

    /** Which kinds of text the word filter checks. */
    public record Filtered(boolean chat, boolean messages, boolean signs, boolean books, boolean itemNames, boolean maskForViewers) {
    }

    /** Whether a written book's author is hidden from players who share no circle with them (D291), and what shows instead. */
    public record BookAuthors(boolean hide, String shownAs) {
    }

    /** The note players get when nobody could hear their chat. */
    public record NobodyHeard(boolean enabled, int cooldownSeconds) {
    }
}
