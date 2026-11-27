package com.extantape.posse.rules;

import java.util.Collection;
import java.util.function.Consumer;

/**
 * Who gets a line the server would send to everyone about a player, such as a death or advancement line (K832). It
 * follows the same answer as chat: a viewer gets the line only if they may reach the player it's about, and everyone
 * else it names, such as whoever dealt the last blow. Kept free of the server so a unit test runs the same path the
 * listener does.
 * <p>
 * Fails closed. While the rules are paused, or if the answer can't be worked out for a viewer, only the player the
 * line is about and players with posse.bypass get it.
 */
public final class Announcements {
    private Announcements() {}

    /**
     * @param reach the current rules, or null while paused
     * @param about who the line is about; they always get it
     * @param also  anyone else the line names, or null
     */
    public static boolean mayHear(Subject viewer, Reach reach, Subject about, Subject also) {
        try {
            if (viewer.id().equals(about.id())) return true;
            if (reach == null) return viewer.has(Reach.BYPASS);
            if (!reach.mayReach(viewer, about)) return false;
            return also == null || viewer.id().equals(also.id()) || reach.mayReach(viewer, also);
        } catch (RuntimeException e) {
            return bypass(viewer);
        }
    }

    /** Whether the viewer has posse.bypass, or false if even that can't be read. */
    private static boolean bypass(Subject viewer) {
        try {
            return viewer.has(Reach.BYPASS);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * Sends {@code message} once to each online player who may hear it, and always to the console, so the log keeps it.
     * A null message, which is what a plugin sets to say "no line", sends nothing at all.
     *
     * @return how many players were sent the message
     */
    public static <M> int deliver(M message, Subject about, Subject also, Collection<? extends Subject> online, Reach reach,
                                  Consumer<Subject> toViewer, Consumer<M> toConsole) {
        if (message == null) return 0;
        int sent = 0;
        for (Subject viewer : online) {
            if (!mayHear(viewer, reach, about, also)) continue;
            toViewer.accept(viewer);
            sent++;
        }
        toConsole.accept(message);
        return sent;
    }
}
