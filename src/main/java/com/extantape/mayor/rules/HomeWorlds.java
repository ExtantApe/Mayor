package com.extantape.mayor.rules;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Keeps players in the worlds their circles belong in (D290, 2026-11-25). Players rejoin where they logged out, so
 * this only steps in when that world isn't one their circle allows. Nothing moves unless {@code home-worlds} is set.
 * <p>
 * The rules, in order:
 * <ol>
 *   <li>A player in no circle goes to the {@code no-circle} fallback, unless they're already there.</li>
 *   <li>A player in any circle that has no entry is never moved. That covers the visitor in both a kids' and an adults'
 *       circle, and every adult group.</li>
 *   <li>Otherwise every circle they hold has an entry, and they may be in any of those worlds. Anywhere else, they go
 *       to the fallback of the first of their circles that has one.</li>
 *   <li>Fails closed: if that fallback world doesn't exist, they go to the main world's spawn instead, and the caller
 *       logs a warning. They are never left where they are.</li>
 * </ol>
 */
public final class HomeWorlds {
    /** Where a circle's players may be, where they go otherwise, and an optional line to tell them. */
    public record Entry(Set<String> worlds, String fallback, String message) {
        public Entry {
            worlds = Set.copyOf(worlds);
        }
    }

    /** Why a player is moved, so the right message can be used. */
    public enum Kind { CIRCLE, NO_CIRCLE }

    /**
     * A move to make. {@code world} is the world whose spawn to use; {@code degraded} means the wanted fallback
     * doesn't exist and the main world stands in; {@code message} is the entry's own line, or null for the default.
     */
    public record Move(String world, boolean degraded, String wanted, String message, Kind kind) {}

    public static final HomeWorlds OFF = new HomeWorlds(Map.of(), null);

    private final Map<String, Entry> byCircle;
    private final List<String> orderedKeys;
    private final Entry noCircle;

    /** @param byCircle circle name to its entry, in config order; @param noCircle the entry for players with no circle, or null */
    public HomeWorlds(Map<String, Entry> inOrder, Entry noCircle) {
        this.byCircle = Map.copyOf(inOrder);
        this.orderedKeys = List.copyOf(inOrder.keySet());
        this.noCircle = noCircle;
    }

    public boolean enabled() {
        return !byCircle.isEmpty() || noCircle != null;
    }

    public Map<String, Entry> byCircle() {
        return byCircle;
    }

    public Entry noCircle() {
        return noCircle;
    }

    /** Every world named anywhere in the section, for checking they exist. */
    public Set<String> namedWorlds() {
        Set<String> all = new java.util.TreeSet<>();
        for (Entry e : byCircle.values()) {
            all.addAll(e.worlds());
            all.add(e.fallback());
        }
        if (noCircle != null) all.add(noCircle.fallback());
        return all;
    }

    /**
     * Whether and where to move a player.
     *
     * @param who       the player, for their circles
     * @param atWorld   the world they're in or about to be in (a respawn point, say)
     * @param exists    whether a world is loaded
     * @param mainWorld the main world's name, the last resort
     * @return the move, or null to leave them
     */
    public Move decide(Reach reach, Subject who, String atWorld, Predicate<String> exists, String mainWorld) {
        if (!enabled() || reach == null || !reach.active()) return null;
        List<String> circles = reach.circlesOf(who);
        if (circles.isEmpty()) {
            if (noCircle == null || atWorld.equals(noCircle.fallback())) return null;
            return move(noCircle, atWorld, exists, mainWorld, Kind.NO_CIRCLE);
        }
        Entry first = null;
        Set<String> allowed = new java.util.HashSet<>();
        for (String circle : orderedKeys) {
            if (circles.contains(circle)) {
                Entry e = byCircle.get(circle);
                if (first == null) first = e;
                allowed.addAll(e.worlds());
            }
        }
        for (String circle : circles) if (!byCircle.containsKey(circle)) return null;
        if (first == null || allowed.contains(atWorld)) return null;
        return move(first, atWorld, exists, mainWorld, Kind.CIRCLE);
    }

    private static Move move(Entry entry, String atWorld, Predicate<String> exists, String mainWorld, Kind kind) {
        if (exists.test(entry.fallback())) return new Move(entry.fallback(), false, entry.fallback(), entry.message(), kind);
        // The fallback is missing. The main world is the last resort; standing in it already is as far as this can go.
        if (atWorld.equals(mainWorld)) return null;
        return new Move(mainWorld, true, entry.fallback(), entry.message(), kind);
    }
}
