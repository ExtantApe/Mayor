package com.extantape.mayor.listen;

import com.extantape.mayor.MayorPlugin;
import com.extantape.mayor.config.Settings;
import com.extantape.mayor.rules.Reach;
import com.extantape.mayor.rules.Subject;
import com.extantape.mayor.rules.WordFilter;
import com.extantape.mayor.text.Messages;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Public chat goes only to the players the speaker can reach. Runs on the async chat thread and never blocks it. */
public final class ChatListener implements Listener {
    private final MayorPlugin plugin;

    public ChatListener(MayorPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player from = event.getPlayer();
        MayorPlugin.State state = plugin.state();
        if (state == null) {
            event.setCancelled(true);
            plugin.tell(from, "chat-paused");
            return;
        }
        Settings settings = state.settings();
        Reach reach = settings.reach();
        Subject me = Subject.of(from);
        if (!reach.maySpeak(me)) {
            event.setCancelled(true);
            plugin.tell(from, "chat-closed-here");
            return;
        }
        if (settings.filtered().chat() && !me.has(WordFilter.BYPASS) && settings.filter().flags(PlainTextComponentSerializer.plainText().serialize(event.message()))) {
            event.setCancelled(true);
            plugin.tell(from, "filtered-chat");
            return;
        }
        if (!reach.active()) return;
        event.viewers().removeIf(viewer -> viewer instanceof Player to && !reach.mayReach(me, Subject.of(to)));

        if (!settings.nobodyHeard().enabled()) return;
        boolean heard = event.viewers().stream().anyMatch(v -> v instanceof Player p && !p.equals(from));
        if (heard || Bukkit.getOnlinePlayers().size() < 2) return;
        String note = state.quips().next(from.getUniqueId(), System.currentTimeMillis());
        // A tick later, so the note shows under the player's own line.
        if (note != null) Bukkit.getScheduler().runTask(plugin, () -> from.sendMessage(Messages.parse(note, Map.of())));
    }

    /**
     * With mask-for-viewers on, an unfiltered speaker's blocked words reach filtered players as ****, and everyone
     * with the bypass sees the line as written. A player without the bypass, including one whose permissions aren't
     * known, counts as filtered. The console sees it as written.
     *
     * Runs last, once nothing else will cancel the line or change who hears it. Filtered players are taken off the
     * line's viewers and sent their own copy, rendered the same way with the masked text. A signed chat message
     * carries the original words even when it's displayed differently, so it isn't enough to change the rendering.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void maskForViewers(AsyncChatEvent event) {
        MayorPlugin.State state = plugin.state();
        if (state == null) return;
        Settings settings = state.settings();
        Player from = event.getPlayer();
        if (!settings.filtered().chat() || !settings.filtered().maskForViewers() || !from.hasPermission(WordFilter.BYPASS)) return;
        String plain = PlainTextComponentSerializer.plainText().serialize(event.message());
        String masked = settings.filter().mask(plain);
        if (masked.equals(plain)) return;
        Component hidden = Component.text(masked);
        ChatRenderer renderer = event.renderer();
        List<Player> filtered = new ArrayList<>();
        for (Audience viewer : event.viewers()) if (viewer instanceof Player p && !seesAsWritten(p)) filtered.add(p);
        if (filtered.isEmpty()) return;
        event.viewers().removeAll(filtered);
        for (Player p : filtered) p.sendMessage(renderer.render(from, from.displayName(), hidden, p));
    }

    static boolean seesAsWritten(Audience viewer) {
        return !(viewer instanceof Player p) || p.hasPermission(WordFilter.BYPASS);
    }
}
