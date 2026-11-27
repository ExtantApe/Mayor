package com.extantape.posse.rules;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Which online players' names a player may be offered when they press Tab in a command (K832). It follows the same
 * answer as chat: a name is offered only if the player asking may reach its owner. It covers every command, since it
 * works on the names and not on the command.
 * <p>
 * Fails closed. While the rules are paused, or if the answer can't be worked out, only the asker's own name is
 * offered, unless they have posse.bypass.
 */
public final class NameSuggestions {
    private NameSuggestions() {}

    /** @param reach the current rules, or null while paused */
    public static boolean mayOffer(Subject asker, Subject other, Reach reach) {
        try {
            if (asker.id().equals(other.id())) return true;
            if (reach == null) return asker.has(Reach.BYPASS);
            return reach.mayReach(asker, other);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** The names, in lower case, of the online players {@code asker} may not be offered. */
    public static Set<String> withheld(Subject asker, Collection<? extends Subject> online, Reach reach) {
        Set<String> names = new HashSet<>();
        for (Subject other : online) {
            String name;
            try {
                name = other.name();
            } catch (RuntimeException e) {
                continue;
            }
            if (name != null && !mayOffer(asker, other, reach)) names.add(name.toLowerCase(Locale.ROOT));
        }
        return names;
    }

    /** The suggestions left once every one that is a withheld name, quoted or not, in any letter case, is taken out. */
    public static List<String> keep(List<String> suggestions, Set<String> withheld) {
        List<String> kept = new ArrayList<>(suggestions.size());
        for (String text : suggestions) if (!isWithheld(text, withheld)) kept.add(text);
        return kept;
    }

    public static boolean isWithheld(String suggestion, Set<String> withheld) {
        if (suggestion == null) return false;
        String text = suggestion.trim();
        if (text.length() >= 2 && (text.startsWith("\"") && text.endsWith("\"") || text.startsWith("'") && text.endsWith("'"))) {
            text = text.substring(1, text.length() - 1);
        }
        return withheld.contains(text.toLowerCase(Locale.ROOT));
    }
}
