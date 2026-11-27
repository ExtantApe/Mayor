package com.extantape.posse.rules;

/**
 * Who sees whom in the player list and in join and leave messages (D283, 2026-11-24). With {@code presence: reach},
 * players see each other there only if they can reach each other in chat, so a kid and an adult who share no circle
 * don't learn the other is online. Fails closed: while Posse's rules are paused, nobody but yourself is shown.
 */
public final class Presence {
    private Presence() {}

    /**
     * @param byReach whether {@code presence: reach} is set; ignored while paused
     * @param reach   the current rules, or null while paused
     */
    public static boolean maySee(Subject viewer, Subject other, boolean byReach, Reach reach) {
        if (viewer.id().equals(other.id())) return true;
        if (viewer.pending() || other.pending() || reach == null) return false;
        if (!byReach) return true;
        return reach.mayReach(viewer, other);
    }

    /**
     * Whether the viewer's game should know the other player exists at all (K832). A game that knows a player offers
     * their name when Tab is pressed in chat or after a command, listed or not, so a player the viewer may not see is
     * kept from it entirely. The one exception is a player standing in the same world: the viewer can see them walk
     * by, and hiding them would make them invisible, so they stay known and only leave the player list.
     *
     * @param maySee    the answer from {@link #maySee}
     * @param sameWorld whether both stand in the very same world right now
     */
    public static boolean mayKnowOf(boolean maySee, boolean sameWorld) {
        return maySee || sameWorld;
    }
}
