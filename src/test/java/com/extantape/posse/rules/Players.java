package com.extantape.posse.rules;

import java.util.Set;
import java.util.UUID;

/** Made-up players for tests. */
final class Players {
    private Players() {
    }

    static Subject player(String name, String world, String... permissions) {
        Set<String> perms = Set.of(permissions);
        return Subject.of(UUID.nameUUIDFromBytes(name.getBytes()), name, world, perms::contains);
    }

    static String circle(String name) {
        return Reach.CIRCLE_PERMISSION + name;
    }

    /** A player whose delayed first-join check is still pending (D297). */
    static Subject pending(String name, String world, String... permissions) {
        Subject base = player(name, world, permissions);
        return new Subject() {
            @Override public UUID id() { return base.id(); }
            @Override public String name() { return base.name(); }
            @Override public String world() { return base.world(); }
            @Override public boolean has(String permission) { return base.has(permission); }
            @Override public boolean pending() { return true; }
        };
    }
}
