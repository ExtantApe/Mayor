package com.extantape.mayor.listen;

import com.extantape.mayor.MayorPlugin;
import com.extantape.mayor.rules.HomeWorlds;
import com.extantape.mayor.rules.Subject;
import com.extantape.mayor.text.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.Map;

/**
 * Applies {@link HomeWorlds} (D290): on join, on respawn, and after any world change, as a safety net for a teleport
 * that got past the world plugin. Only the moved player is told anything.
 */
public final class HomeWorldsListener implements Listener {
    private final MayorPlugin plugin;

    public HomeWorldsListener(MayorPlugin plugin) {
        this.plugin = plugin;
    }

    /** The move this player needs from {@code atWorld}, or null. Null too while the rules are paused or the feature is off. */
    private HomeWorlds.Move decide(Player player, String atWorld) {
        MayorPlugin.State state = plugin.state();
        if (state == null) return null;
        HomeWorlds home = state.settings().homeWorlds();
        if (!home.enabled()) return null;
        Subject who = Subject.of(player.getUniqueId(), player.getName(), atWorld, player::hasPermission);
        return home.decide(state.settings().reach(), who, atWorld, w -> Bukkit.getWorld(w) != null, Bukkit.getWorlds().get(0).getName());
    }

    private Location spawnOf(HomeWorlds.Move move) {
        World world = Bukkit.getWorld(move.world());
        return world == null ? Bukkit.getWorlds().get(0).getSpawnLocation() : world.getSpawnLocation();
    }

    private void tell(Player player, HomeWorlds.Move move) {
        if (move.degraded()) {
            plugin.getLogger().warning("A player was moved to the main world's spawn because the home-worlds fallback \"" + move.wanted()
                    + "\" doesn't exist. Create that world or fix home-worlds in config.yml.");
        }
        String key = move.kind() == HomeWorlds.Kind.NO_CIRCLE ? "home-waiting" : "home-moved";
        Map<String, String> vars = Map.of("world", move.world());
        if (move.message() != null) {
            if (!move.message().isBlank()) player.sendMessage(Messages.parse(move.message(), vars));
        } else plugin.tell(player, key, vars);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        HomeWorlds.Move move = decide(player, player.getWorld().getName());
        if (move == null) return;
        player.teleportAsync(spawnOf(move)).thenAccept(ok -> {
            if (ok) tell(player, move);
            else plugin.getLogger().warning("A home-worlds move on join didn't go through. The player is still in the world they logged out in.");
        });
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        HomeWorlds.Move move = decide(player, event.getRespawnLocation().getWorld().getName());
        if (move == null) return;
        event.setRespawnLocation(spawnOf(move));
        tell(player, move);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        // Next tick, so the teleport that caused this has finished before this one starts.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            HomeWorlds.Move move = decide(player, player.getWorld().getName());
            if (move == null) return;
            player.teleportAsync(spawnOf(move)).thenAccept(ok -> {
                if (ok) tell(player, move);
                else plugin.getLogger().warning("A home-worlds move after a world change didn't go through.");
            });
        });
    }
}
