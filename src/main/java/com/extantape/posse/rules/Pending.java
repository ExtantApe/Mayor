package com.extantape.posse.rules;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Players whose delayed first-join world check hasn't run yet (D297). Until it does they reach nobody and nobody
 * reaches them: no chat, no private messages, no tab entry, no join line. Fails closed. Shared by every rule through
 * {@link Subject#pending()}, and safe to read from the async chat thread.
 */
public final class Pending {
    private static final Set<UUID> WAITING = ConcurrentHashMap.newKeySet();

    private Pending() {}

    public static void begin(UUID id) {
        WAITING.add(id);
    }

    public static void end(UUID id) {
        WAITING.remove(id);
    }

    public static boolean has(UUID id) {
        return WAITING.contains(id);
    }

    public static void clear() {
        WAITING.clear();
    }
}
