package com.extantape.posse.listen;

import com.extantape.posse.PossePlugin;
import com.extantape.posse.rules.Announcements;
import com.extantape.posse.rules.Reach;
import com.extantape.posse.rules.Subject;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.scoreboard.Team;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Death and advancement lines follow the same reach answer as chat (K832). The server would send each to everyone
 * online, so the line is taken off the event and sent by {@link Announcements} to the players who may reach the one it
 * names, and to the console. With no circles set up nothing is touched, as in vanilla.
 * <p>
 * Both run last, at MONITOR, so they act on the line as other plugins left it. A plugin that removed the line, or
 * swapped in its own, keeps that choice: Posse never sends a line the server wouldn't have sent, it only narrows who
 * gets it. Changing the event at MONITOR is unusual, but it changes only the recipients, never whether a line exists.
 */
public final class AnnouncementListener implements Listener {
    private final PossePlugin plugin;

    public AnnouncementListener(PossePlugin plugin) {
        this.plugin = plugin;
    }

    /** The current rules, or null while paused. */
    private Reach reach() {
        PossePlugin.State state = plugin.state();
        return state == null ? null : state.settings().reach();
    }

    private void deliver(Component message, Player about, Player also, Predicate<Player> allowed) {
        Map<Subject, Player> viewers = new IdentityHashMap<>();
        for (Player p : Bukkit.getOnlinePlayers()) if (allowed.test(p)) viewers.put(Subject.of(p), p);
        Announcements.deliver(message, Subject.of(about), also == null ? null : Subject.of(also), viewers.keySet(), reach(),
                viewer -> viewers.get(viewer).sendMessage(message), m -> Bukkit.getConsoleSender().sendMessage(m));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Reach reach = reach();
        if (reach != null && !reach.active()) return;
        Component message = event.deathMessage();
        if (message == null) return;
        // The server sends a death line to everyone or to nobody, so it is told nobody, and Posse sends it. The death
        // screen reads the same line, so it is kept there for the player who died, unless a plugin already set its own.
        event.deathMessage(null);
        if (!event.getShowDeathMessages()) return;
        if (event.deathScreenMessageOverride() == null) event.deathScreenMessageOverride(message);
        Player dead = event.getEntity();
        deliver(message, dead, dead.getKiller(), teamAllows(dead));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        Reach reach = reach();
        if (reach != null && !reach.active()) return;
        Component message = event.message();
        if (message == null) return;
        event.message(null);
        deliver(message, event.getPlayer(), null, viewer -> true);
    }

    /**
     * A scoreboard team can narrow who sees its members' death lines. The server applies that when it sends the line
     * itself, so it's applied here too: Posse never sends a death line to someone vanilla wouldn't have. If the team
     * can't be read, only the dead player is left.
     */
    private static Predicate<Player> teamAllows(Player dead) {
        try {
            var board = Bukkit.getScoreboardManager().getMainScoreboard();
            Team team = board.getEntityTeam(dead);
            if (team == null) return viewer -> true;
            return switch (team.getOption(Team.Option.DEATH_MESSAGE_VISIBILITY)) {
                case ALWAYS -> viewer -> true;
                case NEVER -> viewer -> false;
                case FOR_OWN_TEAM -> viewer -> team.equals(board.getEntityTeam(viewer));
                case FOR_OTHER_TEAMS -> viewer -> !team.equals(board.getEntityTeam(viewer));
            };
        } catch (RuntimeException e) {
            return viewer -> viewer.equals(dead);
        }
    }
}
