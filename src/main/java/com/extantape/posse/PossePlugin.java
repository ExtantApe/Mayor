package com.extantape.posse;

import com.extantape.posse.api.ReachService;
import com.extantape.posse.command.PosseCommands;
import com.extantape.posse.config.Settings;
import com.extantape.posse.config.SettingsLoader;
import com.extantape.posse.listen.ChatListener;
import com.extantape.posse.listen.CommandListener;
import com.extantape.posse.listen.PlayerListener;
import com.extantape.posse.listen.ShareHook;
import com.extantape.posse.listen.TextListener;
import com.extantape.posse.rules.Reach;
import com.extantape.posse.rules.Subject;
import com.extantape.posse.text.Messages;
import com.extantape.posse.text.Quips;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Posse: who can hear whom, which commands are off, and a word filter. See README.md.
 * <p>
 * If the config is broken at startup there are no rules to follow, so chat and private messages are paused, and
 * admins are told why, until it's fixed and reloaded. A broken reload keeps the rules that were working.
 */
public final class PossePlugin extends JavaPlugin implements ReachService {
    private com.extantape.posse.listen.PresenceListener presence;
    private com.extantape.posse.listen.BookListener books;

    public static final String ADMIN = "posse.admin";

    /** Everything that changes on reload, swapped in one go so the chat thread never sees half of it. */
    public record State(Settings settings, Messages messages, Quips quips) {
    }

    private volatile State state;
    /** The built-in defaults, used only to recognise commands and word messages while chat is paused. */
    private State fallback;
    private volatile List<String> problems = List.of();
    /** Who each player last messaged or was messaged by, for /r. */
    private final Map<UUID, UUID> partners = new ConcurrentHashMap<>();
    /** Each player's circles as last seen, for rules about someone offline (D231). */
    private com.extantape.posse.rules.KnownPlayers known;

    @Override
    public void onEnable() {
        for (String file : List.of(SettingsLoader.CONFIG, SettingsLoader.FILTER, SettingsLoader.QUIPS)) {
            if (!getDataFolder().toPath().resolve(file).toFile().exists()) saveResource(file, false);
        }
        Settings defaults = new SettingsLoader(this::getResource).load(null, null, null).settings();
        fallback = new State(defaults, new Messages(defaults.messages()), new Quips(List.of(), 0, new Random()));
        known = new com.extantape.posse.rules.KnownPlayers(getDataFolder().toPath(), getLogger());
        known.load();
        reload(Bukkit.getConsoleSender());

        var plugins = getServer().getPluginManager();
        plugins.registerEvents(new ChatListener(this), this);
        plugins.registerEvents(new CommandListener(this), this);
        plugins.registerEvents(new TextListener(this), this);
        books = new com.extantape.posse.listen.BookListener(this);
        plugins.registerEvents(books, this);
        Bukkit.getScheduler().runTaskTimer(this, books::sweep, 40L, 40L);
        plugins.registerEvents(new PlayerListener(this), this);
        plugins.registerEvents(new com.extantape.posse.listen.HomeWorldsListener(this), this);
        presence = new com.extantape.posse.listen.PresenceListener(this);
        plugins.registerEvents(presence, this);
        Bukkit.getScheduler().runTaskTimer(this, presence::refresh, 40L, 40L);
        // Only touch a share event's class when its plugin is installed. The hooks are the only classes that name them.
        if (plugins.getPlugin("BlueMapMini") != null) {
            try {
                plugins.registerEvents(new ShareHook(this), this);
                getLogger().info("BlueMapMini found: coordinate shares follow the same rules as chat.");
            } catch (LinkageError e) {
                getLogger().warning("BlueMapMini is installed but has no share event this version can use, so shares aren't filtered. Update BlueMapMini and Posse together.");
            }
        }
        getServer().getServicesManager().register(ReachService.class, this, this, ServicePriority.Normal);
        PosseCommands commands = new PosseCommands(this);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> commands.register(event.registrar()));
    }

    @Override
    public void onDisable() {
        getServer().getServicesManager().unregisterAll(this);
        partners.clear();
    }

    /** Reads the config files again. Returns true if the new rules were applied. */
    public boolean reload(CommandSender sender) {
        SettingsLoader.Result result = new SettingsLoader(this::getResource).load(getDataFolder().toPath());
        for (String warning : result.warnings()) getLogger().warning(warning);
        if (!result.ok()) {
            for (String error : result.errors()) getLogger().severe(error);
            problems = result.errors();
            if (state == null) {
                getLogger().severe("Posse couldn't read its config, so chat and private messages are paused until it's fixed. Then run /posse reload.");
            } else {
                getLogger().severe("The config has problems, so Posse kept the rules it already had.");
            }
            return false;
        }
        Settings settings = result.settings();
        state = new State(settings, new Messages(settings.messages()),
                new Quips(settings.quips(), settings.nobodyHeard().cooldownSeconds(), new Random()));
        problems = result.warnings();
        registerCirclePermissions(settings.reach());
        if (presence != null) presence.refresh();
        if (books != null) Bukkit.getScheduler().runTask(this, books::sweep);
        rememberOnline();
        Reach reach = settings.reach();
        if (settings.homeWorlds().enabled()) {
            // Other plugins may load their worlds after this one, so look again once the server is up.
            Bukkit.getScheduler().runTask(this, this::logHomeWorldProblems);
        }
        getLogger().info(reach.active()
                ? "Chat rules are on, with " + reach.circles().size() + (reach.circles().size() == 1 ? " circle. " : " circles. ") + settings.filterSummary()
                : "No circles are set up, so chat reaches everyone, as in vanilla. " + settings.filterSummary());
        return true;
    }

    /** Lists each circle's permission, so permission plugins can suggest it. Nobody has one by default. */
    private void registerCirclePermissions(Reach reach) {
        var plugins = getServer().getPluginManager();
        for (String circle : reach.circles()) {
            String node = Reach.CIRCLE_PERMISSION + circle;
            if (plugins.getPermission(node) == null) {
                plugins.addPermission(new Permission(node, "Puts a player in the " + circle + " circle.", PermissionDefault.FALSE));
            }
        }
    }

    /** Records what each online player holds now, and saves if anything changed. */
    public void rememberOnline() {
        State s = state;
        if (s == null || known == null) return;
        boolean changed = false;
        for (Player p : Bukkit.getOnlinePlayers()) changed |= known.remember(Subject.of(p), s.settings().reach().circles());
        if (changed) known.save();
    }

    /** Records one player as they are now, and saves if anything changed. */
    public void remember(Player player) {
        State s = state;
        if (s == null || known == null) return;
        if (known.remember(Subject.of(player), s.settings().reach().circles())) known.save();
    }

    /** The current rules, or null while chat is paused because the config couldn't be read. */
    public State state() {
        return state;
    }

    public boolean paused() {
        return state == null;
    }

    public Messages messages() {
        State s = state;
        return s == null ? fallback.messages() : s.messages();
    }

    /** The built-in defaults, for recognising private message commands while chat is paused. Never for reach. */
    public State fallback() {
        return fallback;
    }

    /** Config problems, plus any world named in home-worlds that isn't loaded right now. */
    public List<String> problems() {
        List<String> missing = homeWorldProblems();
        if (missing.isEmpty()) return problems;
        List<String> all = new java.util.ArrayList<>(problems);
        all.addAll(missing);
        return List.copyOf(all);
    }

    /** One line for each world named in home-worlds that doesn't exist. Empty when it's off or all are there. */
    public List<String> homeWorldProblems() {
        State s = state;
        if (s == null || !s.settings().homeWorlds().enabled()) return List.of();
        var home = s.settings().homeWorlds();
        List<String> out = new java.util.ArrayList<>();
        for (String world : home.namedWorlds()) {
            if (Bukkit.getWorld(world) != null) continue;
            boolean fallback = home.noCircle() != null && home.noCircle().fallback().equals(world)
                    || home.byCircle().values().stream().anyMatch(e -> e.fallback().equals(world));
            out.add("home-worlds: the world \"" + world + "\" doesn't exist"
                    + (fallback ? ", so players who need it land at the main world's spawn instead." : "."));
        }
        return out;
    }

    private void logHomeWorldProblems() {
        for (String p : homeWorldProblems()) getLogger().warning(p);
    }

    public Map<UUID, UUID> partners() {
        return partners;
    }

    public void tell(CommandSender to, String key, Map<String, String> vars) {
        Messages messages = messages();
        if (!messages.silenced(key)) to.sendMessage(messages.get(key, vars));
    }

    public void tell(CommandSender to, String key) {
        tell(to, key, Map.of());
    }

    public void tell(CommandSender to, Component message) {
        to.sendMessage(message);
    }

    /** Who wrote a book, as far as Posse can tell (D291). {@code player} is false when the name matches no player Posse could know. */
    public record Author(boolean player, Subject subject) {
    }

    /** Looks an author up by the player ID stored on the book, else by name: online first, then the server's name cache. */
    public Author resolveAuthor(String name, UUID tagged) {
        UUID id = tagged;
        if (id == null && name != null && !name.isBlank()) {
            Player online = Bukkit.getPlayerExact(name.trim());
            if (online != null) id = online.getUniqueId();
            else {
                var cached = Bukkit.getOfflinePlayerIfCached(name.trim());
                if (cached != null) id = cached.getUniqueId();
            }
        }
        if (id == null) return new Author(false, null);
        Player online = Bukkit.getPlayer(id);
        Subject who = online != null ? Subject.of(online) : known == null ? null : known.recall(id);
        return new Author(true, who);
    }

    @Override
    public boolean mayReach(Player from, Player to) {
        if (from.equals(to)) return true;
        State s = state;
        return s != null && s.settings().reach().mayReach(Subject.of(from), Subject.of(to));
    }

    @Override
    public boolean maySpeak(Player player) {
        State s = state;
        return s != null && s.settings().reach().maySpeak(Subject.of(player));
    }

    @Override
    public boolean sharesCircle(java.util.function.Predicate<String> viewerHas, Player to) {
        State s = state;
        if (s == null || viewerHas == null || to == null) return false;
        try {
            return s.settings().reach().reachesWhileAway(viewerHas, Subject.of(to));
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    public boolean mayKnow(Player viewer, UUID owner, String world) {
        State s = state;
        if (s == null || viewer == null || owner == null) return false;
        try {
            Player online = Bukkit.getPlayer(owner);
            Subject who = online != null ? Subject.of(online) : known == null ? null : known.recall(owner);
            return s.settings().reach().mayKnow(Subject.of(viewer), who, world, online != null);
        } catch (RuntimeException e) {
            return false;
        }
    }
}
