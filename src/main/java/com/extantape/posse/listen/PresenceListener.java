package com.extantape.posse.listen;

import com.extantape.posse.PossePlugin;
import com.extantape.posse.rules.Pending;
import com.extantape.posse.rules.Presence;
import com.extantape.posse.rules.PresenceMessages;
import com.extantape.posse.rules.Reach;
import com.extantape.posse.rules.Subject;
import net.kyori.adventure.text.Component;
import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Applies {@link Presence} to the player list and to join and leave messages (D283). Checked on join, leave and world
 * change, and every two seconds for circle changes made through the permissions plugin.
 * <p>
 * A player the viewer may not see is also kept from the viewer's game altogether while they are in different worlds
 * (K832). Taking a player off the list leaves their name in the game's memory, and the game offers every name it
 * remembers when Tab is pressed in chat or after a command, without asking the server. Hiding the player removes the
 * name. Paper's own answers to Tab, and most plugins', also skip a hidden player.
 */
public final class PresenceListener implements Listener {
    private final PossePlugin plugin;

    public PresenceListener(PossePlugin plugin) {
        this.plugin = plugin;
    }

    /** Whether presence is limited right now: presence: reach is set, or the rules are paused. */
    private boolean limited() {
        PossePlugin.State state = plugin.state();
        return state == null || state.settings().presenceByReach();
    }

    private boolean maySee(Player viewer, Player other) {
        PossePlugin.State state = plugin.state();
        Reach reach = state == null ? null : state.settings().reach();
        boolean byReach = state != null && state.settings().presenceByReach();
        return Presence.maySee(Subject.of(viewer), Subject.of(other), byReach, reach);
    }

    /** Sends the line to each allowed viewer and to the console, and nowhere else. A null message sends nothing. */
    private void deliver(Component message, Player source) {
        PossePlugin.State state = plugin.state();
        Reach reach = state == null ? null : state.settings().reach();
        boolean byReach = state != null && state.settings().presenceByReach();
        Map<Subject, Player> viewers = new IdentityHashMap<>();
        for (Player p : Bukkit.getOnlinePlayers()) viewers.put(Subject.of(p), p);
        PresenceMessages.deliver(message, Subject.of(source), viewers.keySet(), byReach, reach,
                viewer -> viewers.get(viewer).sendMessage(message), m -> Bukkit.getConsoleSender().sendMessage(m));
    }

    /** Join lines held back for players whose delayed first-join check is pending (D297). */
    private final Map<java.util.UUID, Component> held = new java.util.concurrent.ConcurrentHashMap<>();

    /** The player's delayed check is done: sends the held join line, if any, for where they are, per the presence rules. */
    public void release(Player player) {
        Component message = held.remove(player.getUniqueId());
        if (message != null) deliver(message, player);
        refresh();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        if (Pending.has(event.getPlayer().getUniqueId())) {
            // Nobody hears about them until they are in a world they may be in, whatever the presence setting.
            Component message = event.joinMessage();
            event.joinMessage(null);
            if (message != null) held.put(event.getPlayer().getUniqueId(), message);
            refresh();
            return;
        }
        if (limited()) {
            Component message = event.joinMessage();
            event.joinMessage(null);
            deliver(message, event.getPlayer());
        }
        refresh();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        if (held.remove(event.getPlayer().getUniqueId()) != null || Pending.has(event.getPlayer().getUniqueId())) {
            // Never announced, so never announced as leaving.
            event.quitMessage(null);
            return;
        }
        if (!limited()) return;
        Component message = event.quitMessage();
        event.quitMessage(null);
        deliver(message, event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorld(PlayerChangedWorldEvent event) {
        refresh();
    }

    /** The server's hiding ends with the connection, so Posse's record of it does too. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void forget(PlayerQuitEvent event) {
        UUID gone = event.getPlayer().getUniqueId();
        hidden.remove(gone);
        for (Set<UUID> from : hidden.values()) from.remove(gone);
    }

    /**
     * The server list shows a sample of who is online to anyone who looks, signed in or not. Posse can't tell who is
     * asking, so while presence is limited the sample is left empty. The player count stays.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPing(PaperServerListPingEvent event) {
        if (limited()) event.getListedPlayers().clear();
    }

    /** For each viewer, the players Posse has hidden from them. Only Posse's own hiding is ever undone. */
    private final Map<UUID, Set<UUID>> hidden = new HashMap<>();

    /**
     * Lists or unlists, and hides or shows, every pair of online players to match the rules. Cheap when nothing
     * changed. If one pair can't be worked out, that pair is hidden and the rest still run.
     */
    public void refresh() {
        var online = Bukkit.getOnlinePlayers();
        for (Player viewer : online) {
            Set<UUID> mine = hidden.computeIfAbsent(viewer.getUniqueId(), id -> new HashSet<>());
            for (Player other : online) {
                if (viewer == other) continue;
                boolean see, know;
                try {
                    see = maySee(viewer, other);
                    know = Presence.mayKnowOf(see, viewer.getWorld().equals(other.getWorld()));
                } catch (RuntimeException e) {
                    see = false;
                    know = false;
                }
                boolean hid = mine.contains(other.getUniqueId());
                if (!know && !hid) {
                    viewer.hideEntity(plugin, other);
                    mine.add(other.getUniqueId());
                } else if (know && hid) {
                    viewer.showEntity(plugin, other);
                    mine.remove(other.getUniqueId());
                }
                // Hidden from this viewer, by Posse or by another plugin such as a vanish: there is no list entry to set.
                if (!viewer.canSee(other)) continue;
                boolean listed = viewer.isListed(other);
                if (see && !listed) viewer.listPlayer(other);
                else if (!see && listed) viewer.unlistPlayer(other);
            }
        }
    }

    /** Undoes Posse's own hiding, for when the plugin is turned off. */
    public void showAll() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            for (Player other : Bukkit.getOnlinePlayers()) if (viewer != other) viewer.showEntity(plugin, other);
        }
        hidden.clear();
    }
}
