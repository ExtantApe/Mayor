package com.extantape.posse.rules;

import java.util.Collection;
import java.util.function.Consumer;

/**
 * Who gets a join or leave line under {@code presence: reach} (D283). Kept free of the server so a unit test can run the
 * same path the listener does: the message is taken from the event, then sent here once to each allowed viewer and once
 * to the console.
 */
public final class PresenceMessages {
    private PresenceMessages() {}

    /**
     * Sends {@code message} to every online player who may see {@code source}, never to {@code source} itself, and always
     * to the console. A null message, which is what a plugin sets to say "no join message", sends nothing at all.
     *
     * @param byReach whether {@code presence: reach} is set; ignored while paused
     * @param reach   the current rules, or null while paused (then only the console gets it)
     * @return how many players were sent the message
     */
    public static <M> int deliver(M message, Subject source, Collection<? extends Subject> online, boolean byReach, Reach reach,
                                  Consumer<Subject> toViewer, Consumer<M> toConsole) {
        if (message == null) return 0;
        int sent = 0;
        for (Subject viewer : online) {
            if (viewer.id().equals(source.id()) || !Presence.maySee(viewer, source, byReach, reach)) continue;
            toViewer.accept(viewer);
            sent++;
        }
        toConsole.accept(message);
        return sent;
    }
}
