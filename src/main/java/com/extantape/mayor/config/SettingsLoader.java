package com.extantape.mayor.config;

import com.extantape.mayor.rules.CommandRules;
import com.extantape.mayor.rules.Reach;
import com.extantape.mayor.rules.WordFilter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Reads the three config files. Mistakes that make the rules unclear, such as a circle name that can't be a
 * permission, are errors, and nothing is applied. Smaller slips, such as an unknown key, are warnings.
 */
public final class SettingsLoader {
    public static final String CONFIG = "config.yml", FILTER = "filter.yml", QUIPS = "quips.yml";
    private static final Pattern NAME = Pattern.compile("[a-z0-9_-]+");
    private static final Set<String> CONFIG_KEYS = Set.of("config-version", "circles", "circle-only-worlds",
            "world-groups", "dimension-suffixes", "private-message-commands", "reply-commands", "broadcast-commands",
            "blocked-commands", "nobody-heard", "messages", "presence", "home-worlds", "book-authors");
    private static final Set<String> FILTER_KEYS = Set.of("enabled", "check", "words", "allow", "mask-for-viewers");

    /** The result: settings to apply, or errors explaining why there are none. Warnings either way. */
    public record Result(Settings settings, List<String> errors, List<String> warnings) {
        public boolean ok() {
            return settings != null;
        }
    }

    private final Function<String, InputStream> defaults;
    private final List<String> errors = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    /** @param defaults opens a default file from the jar by name */
    public SettingsLoader(Function<String, InputStream> defaults) {
        this.defaults = defaults;
    }

    /** Reads the files in a folder. A missing file uses the defaults in the jar. */
    public Result load(Path folder) {
        return load(read(folder, CONFIG), read(folder, FILTER), read(folder, QUIPS));
    }

    /** Reads from text, for tests. Null means the file is missing. */
    public Result load(String config, String filterText, String quips) {
        errors.clear();
        warnings.clear();
        YamlConfiguration base = parse(CONFIG, config);
        YamlConfiguration words = parse(FILTER, filterText);
        YamlConfiguration notes = parse(QUIPS, quips);
        YamlConfiguration baseDefaults = parseDefault(CONFIG);
        if (!errors.isEmpty()) return new Result(null, List.copyOf(errors), List.copyOf(warnings));

        unknownKeys(CONFIG, base, CONFIG_KEYS);
        unknownKeys(FILTER, words, FILTER_KEYS);

        List<String> circles = new ArrayList<>();
        for (String circle : strings(CONFIG, base, "circles")) {
            String lower = circle.trim().toLowerCase(Locale.ROOT);
            if (!NAME.matcher(lower).matches()) {
                errors.add(CONFIG + ": circle \"" + circle + "\" can only use letters, numbers, - and _, because it becomes the permission mayor.circle.<name>.");
            } else if (circles.contains(lower)) {
                warnings.add(CONFIG + ": circle \"" + lower + "\" is listed twice.");
            } else circles.add(lower);
        }
        Set<String> circleOnly = new LinkedHashSet<>(strings(CONFIG, base, "circle-only-worlds"));
        if (circles.isEmpty() && !circleOnly.isEmpty()) {
            warnings.add(CONFIG + ": circle-only-worlds has no effect until at least one circle is listed.");
        }
        Map<String, String> groupOf = new HashMap<>();
        ConfigurationSection groups = base.getConfigurationSection("world-groups");
        if (groups != null) {
            for (String group : groups.getKeys(false)) {
                if (!groups.isList(group)) {
                    errors.add(CONFIG + ": world-groups." + group + " must be a list of world names.");
                    continue;
                }
                for (String world : groups.getStringList(group)) {
                    String before = groupOf.put(world, group);
                    if (before != null && !before.equals(group)) {
                        errors.add(CONFIG + ": world \"" + world + "\" is in two world groups, " + before + " and " + group + ".");
                    }
                }
            }
        } else if (base.contains("world-groups") && !(base.get("world-groups") instanceof Map<?, ?>)) {
            errors.add(CONFIG + ": world-groups must be a section, with a list of worlds under each group name.");
        }
        List<String> suffixes = base.contains("dimension-suffixes") ? strings(CONFIG, base, "dimension-suffixes") : List.of("_nether", "_the_end");

        List<CommandRules.Blocked> blocked = new ArrayList<>();
        List<?> entries = Objects.requireNonNullElse(base.getList("blocked-commands"), List.of());
        for (int i = 0; i < entries.size(); i++) {
            if (!(entries.get(i) instanceof Map<?, ?> entry) || !(entry.get("commands") instanceof List<?> commands)
                    || !(entry.get("message") instanceof String message)) {
                errors.add(CONFIG + ": blocked-commands entry " + (i + 1) + " needs a commands list and a message.");
                continue;
            }
            Set<String> labels = new LinkedHashSet<>();
            for (Object c : commands) labels.add(String.valueOf(c));
            blocked.add(new CommandRules.Blocked(labels, message));
        }
        if (base.contains("blocked-commands") && !base.isList("blocked-commands")) {
            errors.add(CONFIG + ": blocked-commands must be a list.");
        }

        Settings.NobodyHeard nobodyHeard = new Settings.NobodyHeard(
                bool(CONFIG, base, "nobody-heard.enabled", true),
                Math.max(0, number(CONFIG, base, "nobody-heard.cooldown-seconds", 180)));

        Map<String, String> messages = new LinkedHashMap<>();
        ConfigurationSection defaultMessages = baseDefaults.getConfigurationSection("messages");
        if (defaultMessages != null) for (String key : defaultMessages.getKeys(false)) messages.put(key, defaultMessages.getString(key));
        ConfigurationSection own = base.getConfigurationSection("messages");
        if (own != null) {
            for (String key : own.getKeys(false)) {
                if (!messages.containsKey(key)) warnings.add(CONFIG + ": messages." + key + " isn't a message Mayor uses.");
                else if (!own.isString(key)) errors.add(CONFIG + ": messages." + key + " must be text.");
                else messages.put(key, own.getString(key));
            }
        }

        boolean filterOn = bool(FILTER, words, "enabled", true);
        WordFilter filter = filterOn ? WordFilter.of(strings(FILTER, words, "words"), strings(FILTER, words, "allow")) : WordFilter.NONE;
        Settings.Filtered filtered = new Settings.Filtered(
                bool(FILTER, words, "check.chat", true), bool(FILTER, words, "check.private-messages", true),
                bool(FILTER, words, "check.signs", true), bool(FILTER, words, "check.books", true), bool(FILTER, words, "check.item-names", true),
                bool(FILTER, words, "mask-for-viewers", false));

        List<String> quipList = new ArrayList<>();
        for (String note : strings(QUIPS, notes, "notes")) if (!note.isBlank()) quipList.add(note);
        if (nobodyHeard.enabled() && quipList.isEmpty()) warnings.add(QUIPS + ": there are no notes, so nobody-heard stays quiet.");

        String presence = String.valueOf(base.get("presence", "everyone")).trim().toLowerCase(java.util.Locale.ROOT);
        if (!presence.equals("everyone") && !presence.equals("reach")) errors.add(CONFIG + ": presence must be everyone or reach.");
        if (presence.equals("reach") && circles.isEmpty()) warnings.add(CONFIG + ": presence: reach has no effect until at least one circle is listed.");

        com.extantape.mayor.rules.HomeWorlds homeWorlds = homeWorlds(base, circles);

        String shownAs = base.contains("book-authors.shown-as") ? String.valueOf(base.get("book-authors.shown-as")).trim() : "Unknown";
        if (base.contains("book-authors.shown-as") && !base.isString("book-authors.shown-as")) errors.add(CONFIG + ": book-authors.shown-as must be text.");
        if (shownAs.isEmpty()) shownAs = "Unknown";
        Settings.BookAuthors bookAuthors = new Settings.BookAuthors(bool(CONFIG, base, "book-authors.hide", true), shownAs);

        Reach reach = new Reach(circles, circleOnly, groupOf, suffixes);
        CommandRules commands = new CommandRules(reach, filter, filtered.messages(), filtered.maskForViewers(),
                Set.copyOf(strings(CONFIG, base, "private-message-commands")), Set.copyOf(strings(CONFIG, base, "reply-commands")),
                Set.copyOf(strings(CONFIG, base, "broadcast-commands")), blocked);
        if (!errors.isEmpty()) return new Result(null, List.copyOf(errors), List.copyOf(warnings));
        return new Result(new Settings(reach, commands, filter, filtered, nobodyHeard, List.copyOf(quipList), Map.copyOf(messages), presence.equals("reach"), homeWorlds, bookAuthors),
                List.of(), List.copyOf(warnings));
    }

    /** The optional home-worlds section (D290). Every slip is an error: a half-read rule could leave a kid in the wrong world. */
    private com.extantape.mayor.rules.HomeWorlds homeWorlds(YamlConfiguration base, List<String> circles) {
        if (!base.contains("home-worlds") || base.get("home-worlds") == null) return com.extantape.mayor.rules.HomeWorlds.OFF;
        ConfigurationSection section = base.getConfigurationSection("home-worlds");
        if (section == null) {
            errors.add(CONFIG + ": home-worlds must be a section, with one entry per circle and an optional no-circle entry.");
            return com.extantape.mayor.rules.HomeWorlds.OFF;
        }
        Map<String, com.extantape.mayor.rules.HomeWorlds.Entry> byCircle = new LinkedHashMap<>();
        com.extantape.mayor.rules.HomeWorlds.Entry noCircle = null;
        for (String key : section.getKeys(false)) {
            String where = "home-worlds." + key;
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (entry == null) {
                errors.add(CONFIG + ": " + where + " must be a section" + (key.equals("no-circle") ? " with a fallback." : " with worlds and a fallback."));
                continue;
            }
            boolean none = key.equals("no-circle");
            for (String sub : entry.getKeys(false)) {
                if (!(sub.equals("fallback") || sub.equals("message") || (!none && sub.equals("worlds")))) {
                    warnings.add(CONFIG + ": " + where + "." + sub + " isn't a setting Mayor uses, so it's ignored.");
                }
            }
            String fallback = entry.isString("fallback") ? entry.getString("fallback").trim() : "";
            if (fallback.isEmpty()) errors.add(CONFIG + ": " + where + ".fallback must be a world name.");
            String message = null;
            if (entry.contains("message")) {
                if (entry.isString("message")) message = entry.getString("message");
                else errors.add(CONFIG + ": " + where + ".message must be text.");
            }
            if (none) {
                if (!fallback.isEmpty()) noCircle = new com.extantape.mayor.rules.HomeWorlds.Entry(Set.of(), fallback, message);
                continue;
            }
            String circle = key.toLowerCase(Locale.ROOT);
            if (!circles.contains(circle)) {
                errors.add(CONFIG + ": " + where + " names a circle that isn't in circles.");
                continue;
            }
            List<String> worlds = strings(CONFIG, entry, "worlds");
            if (worlds.isEmpty()) errors.add(CONFIG + ": " + where + ".worlds must list at least one world.");
            if (!fallback.isEmpty() && !worlds.isEmpty() && !worlds.contains(fallback)) {
                errors.add(CONFIG + ": " + where + ".fallback must be one of its worlds.");
            }
            if (!fallback.isEmpty() && !worlds.isEmpty()) {
                byCircle.put(circle, new com.extantape.mayor.rules.HomeWorlds.Entry(new LinkedHashSet<>(worlds), fallback, message));
            }
        }
        if (circles.isEmpty() && !section.getKeys(false).isEmpty()) {
            errors.add(CONFIG + ": home-worlds needs at least one circle in circles. With none, nobody is in a circle and everyone would be sent to the no-circle world.");
        }
        return new com.extantape.mayor.rules.HomeWorlds(byCircle, noCircle);
    }

    private String read(Path folder, String name) {
        Path file = folder.resolve(name);
        if (!Files.exists(file)) return null;
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            errors.add(name + ": couldn't be read (" + e.getMessage() + ").");
            return "";
        }
    }

    /** Parses a file, falling back to the jar's copy for keys it leaves out. */
    private YamlConfiguration parse(String name, String text) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.setDefaults(parseDefault(name));
        if (text == null) return yaml;
        try {
            yaml.loadFromString(text);
        } catch (InvalidConfigurationException e) {
            String reason = e.getMessage() == null ? "" : e.getMessage().lines().filter(l -> l.contains("line")).findFirst().orElse("").trim();
            errors.add(name + ": isn't valid YAML" + (reason.isEmpty() ? "." : " (" + reason + ")."));
        }
        return yaml;
    }

    private YamlConfiguration parseDefault(String name) {
        YamlConfiguration yaml = new YamlConfiguration();
        try (InputStream in = defaults.apply(name)) {
            if (in != null) yaml.loadFromString(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException | InvalidConfigurationException e) {
            throw new IllegalStateException("The built-in " + name + " is broken", e);
        }
        return yaml;
    }

    private void unknownKeys(String file, YamlConfiguration yaml, Set<String> known) {
        for (String key : yaml.getKeys(false)) {
            if (!known.contains(key)) warnings.add(file + ": \"" + key + "\" isn't a setting Mayor uses, so it's ignored.");
        }
    }

    private List<String> strings(String file, ConfigurationSection yaml, String key) {
        if (!yaml.contains(key) || yaml.get(key) == null) return List.of();
        if (!yaml.isList(key)) {
            errors.add(file + ": " + key + " must be a list.");
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object o : yaml.getList(key)) {
            if (o instanceof String || o instanceof Number || o instanceof Boolean) out.add(String.valueOf(o));
            else errors.add(file + ": " + key + " must be a list of plain words.");
        }
        return out;
    }

    private boolean bool(String file, ConfigurationSection yaml, String key, boolean fallback) {
        if (!yaml.contains(key)) return fallback;
        if (!yaml.isBoolean(key)) {
            errors.add(file + ": " + key + " must be true or false.");
            return fallback;
        }
        return yaml.getBoolean(key);
    }

    private int number(String file, ConfigurationSection yaml, String key, int fallback) {
        if (!yaml.contains(key)) return fallback;
        if (!yaml.isInt(key)) {
            errors.add(file + ": " + key + " must be a whole number.");
            return fallback;
        }
        return yaml.getInt(key);
    }

}
