package com.extantape.posse.listen;

import com.destroystokyo.paper.event.brigadier.AsyncPlayerSendSuggestionsEvent;
import com.extantape.posse.PossePlugin;
import com.extantape.posse.rules.NameSuggestions;
import com.extantape.posse.rules.Reach;
import com.extantape.posse.rules.Subject;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Tab in a command offers only the names of players the asker may reach (K832). Paper fires this event with the
 * finished list just before it goes to the player, whichever command made it: a vanilla one, a plugin's own
 * completer, or the default list of online players. So one check here covers every command and its namespaced forms.
 * With no circles set up nothing is touched. If anything goes wrong, nothing is offered.
 * <p>
 * A real game answers Tab for vanilla player arguments itself, from the players it has been told about, without
 * asking the server. {@link PresenceListener} covers that half by keeping players out of reach from the game.
 */
public final class SuggestionListener implements Listener {
    private final PossePlugin plugin;

    public SuggestionListener(PossePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSuggestions(AsyncPlayerSendSuggestionsEvent event) {
        try {
            PossePlugin.State state = plugin.state();
            Reach reach = state == null ? null : state.settings().reach();
            if (reach != null && !reach.active()) return;
            Player asker = event.getPlayer();
            List<Subject> online = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) online.add(Subject.of(p));
            Set<String> withheld = NameSuggestions.withheld(Subject.of(asker), online, reach);
            if (withheld.isEmpty()) return;
            Suggestions offered = event.getSuggestions();
            List<Suggestion> kept = new ArrayList<>(offered.getList().size());
            for (Suggestion s : offered.getList()) if (!NameSuggestions.isWithheld(s.getText(), withheld)) kept.add(s);
            if (kept.size() == offered.getList().size()) return;
            if (kept.isEmpty()) event.setCancelled(true);
            else event.setSuggestions(new Suggestions(offered.getRange(), kept));
        } catch (RuntimeException e) {
            event.setCancelled(true);
        }
    }
}
