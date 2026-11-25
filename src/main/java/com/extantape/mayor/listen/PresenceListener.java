package com.extantape.mayor.listen;

import com.extantape.mayor.MayorPlugin;
import com.extantape.mayor.rules.Presence;
import com.extantape.mayor.rules.PresenceMessages;
import com.extantape.mayor.rules.Reach;
import com.extantape.mayor.rules.Subject;
import net.kyori.adventure.text.Component;
import java.util.IdentityHashMap;
import java.util.Map;

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
 */
public final class PresenceListener implements Listener {
    private final MayorPlugin plugin;

    public PresenceListener(MayorPlugin plugin) {
        this.plugin = plugin;
    }

    /** Whether presence is limited right now: presence: reach is set, or the rules are paused. */
    private boolean limited() {
        MayorPlugin.State state = plugin.state();
        return state == null || state.settings().presenceByReach();
    }

    private boolean maySee(Player viewer, Player other) {
        MayorPlugin.State state = plugin.state();
        Reach reach = state == null ? null : state.settings().reach();
        boolean byReach = state != null && state.settings().presenceByReach();
        return Presence.maySee(Subject.of(viewer), Subject.of(other), byReach, reach);
    }

    /** Sends the line to each allowed viewer and to the console, and nowhere else. A null message sends nothing. */
    private void deliver(Component message, Player source) {
        MayorPlugin.State state = plugin.state();
        Reach reach = state == null ? null : state.settings().reach();
        boolean byReach = state != null && state.settings().presenceByReach();
        Map<Subject, Player> viewers = new IdentityHashMap<>();
        for (Player p : Bukkit.getOnlinePlayers()) viewers.put(Subject.of(p), p);
        PresenceMessages.deliver(message, Subject.of(source), viewers.keySet(), byReach, reach,
                viewer -> viewers.get(viewer).sendMessage(message), m -> Bukkit.getConsoleSender().sendMessage(m));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        if (limited()) {
            Component message = event.joinMessage();
            event.joinMessage(null);
            deliver(message, event.getPlayer());
        }
        refresh();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        if (!limited()) return;
        Component message = event.quitMessage();
        event.quitMessage(null);
        deliver(message, event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorld(PlayerChangedWorldEvent event) {
        refresh();
    }

    /** Lists or unlists every pair of online players to match the rules. Cheap when nothing changed. */
    public void refresh() {
        var online = Bukkit.getOnlinePlayers();
        for (Player viewer : online) {
            for (Player other : online) {
                if (viewer == other) continue;
                boolean see = maySee(viewer, other);
                boolean listed = viewer.isListed(other);
                if (see && !listed) viewer.listPlayer(other);
                else if (!see && listed) viewer.unlistPlayer(other);
            }
        }
    }
}
