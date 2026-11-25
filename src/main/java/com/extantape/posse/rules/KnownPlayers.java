package com.extantape.posse.rules;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * What Posse last saw of each player's circles and bypass, so a rule about someone who's offline (whose grave is
 * this?) can still be answered (D231). Kept in known-players.yml: one line per player ID with the circle names they
 * held and "bypass" if they had posse.bypass. Nothing else about the player is stored. A player who isn't listed is
 * unknown, and every rule treats unknown as no.
 */
public final class KnownPlayers {
    public static final String FILE = "known-players.yml";
    private static final String BYPASS_WORD = "bypass";

    private final Path file;
    private final Logger log;
    private final Map<UUID, Set<String>> held = new ConcurrentHashMap<>();

    public KnownPlayers(Path folder, Logger log) {
        this.file = folder.resolve(FILE);
        this.log = log;
    }

    /** Reads the file. A broken file is moved aside to known-players.yml.bak and Posse starts with nobody known. */
    public void load() {
        held.clear();
        if (!Files.exists(file)) return;
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(Files.readString(file));
        } catch (IOException | InvalidConfigurationException e) {
            log.warning(FILE + " couldn't be read, so it was moved to " + FILE + ".bak and nobody offline is known yet: " + e.getMessage());
            try {
                Files.move(file, file.resolveSibling(FILE + ".bak"), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                // Left in place; the next save overwrites it, which is fine because it couldn't be read anyway.
            }
            return;
        }
        for (String key : yaml.getKeys(false)) {
            try {
                held.put(UUID.fromString(key), new HashSet<>(yaml.getStringList(key)));
            } catch (IllegalArgumentException ignored) {
                // Not a player ID: skipped.
            }
        }
    }

    /** Records what a player holds now, from the circles in use. Returns true if anything changed. */
    public boolean remember(Subject player, List<String> circles) {
        Set<String> now = new HashSet<>();
        for (String circle : circles) if (player.has(Reach.CIRCLE_PERMISSION + circle)) now.add(circle);
        if (player.has(Reach.BYPASS)) now.add(BYPASS_WORD);
        return !now.equals(held.put(player.id(), now));
    }

    /** The player as last seen, for the rules, or null if Posse has never seen them. Has no world. */
    public Subject recall(UUID id) {
        Set<String> got = held.get(id);
        if (got == null) return null;
        Set<String> copy = Set.copyOf(got);
        return Subject.of(id, "", "", permission -> {
            if (permission.equals(Reach.BYPASS)) return copy.contains(BYPASS_WORD);
            return permission.startsWith(Reach.CIRCLE_PERMISSION)
                    && copy.contains(permission.substring(Reach.CIRCLE_PERMISSION.length()));
        });
    }

    /** Writes the file through a temporary file, so a crash mid-write never leaves half of it. */
    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        held.forEach((id, set) -> yaml.set(id.toString(), set.stream().sorted().toList()));
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(FILE + ".tmp");
            Files.writeString(tmp, yaml.saveToString());
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.warning(FILE + " couldn't be saved: " + e.getMessage());
        }
    }
}
