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
        if (reach == null) return false;
        if (!byReach) return true;
        return reach.mayReach(viewer, other);
    }
}
