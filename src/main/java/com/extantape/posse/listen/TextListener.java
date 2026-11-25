package com.extantape.posse.listen;

import com.extantape.posse.PossePlugin;
import com.extantape.posse.config.Settings;
import com.extantape.posse.rules.Subject;
import com.extantape.posse.rules.WordFilter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.view.AnvilView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Signs, books and item names follow the same rule as chat: where a player can't chat, they can't write either.
 * The word filter checks them too. Blocked text is never stored or logged: the sign stays as it was, the book keeps
 * its old pages, and the anvil offers no result.
 */
public final class TextListener implements Listener {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    /** The anvil updates on every key press, so its note is shown at most this often. */
    private static final long ANVIL_NOTE_MILLIS = 5000;
    private final PossePlugin plugin;
    private final Map<UUID, Long> anvilNotes = new ConcurrentHashMap<>();

    public TextListener(PossePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSign(SignChangeEvent event) {
        List<String> lines = new ArrayList<>();
        for (Component line : event.lines()) lines.add(line == null ? "" : PLAIN.serialize(line));
        String refusal = refusal(event.getPlayer(), lines, Settings.Filtered::signs, "filtered-sign");
        if (refusal != null) {
            event.setCancelled(true);
            plugin.tell(event.getPlayer(), refusal);
            return;
        }
        WordFilter mask = masker(event.getPlayer(), Settings.Filtered::signs);
        if (mask == null) return;
        for (int i = 0; i < lines.size(); i++) {
            String masked = mask.mask(lines.get(i));
            if (!masked.equals(lines.get(i))) event.line(i, Component.text(masked));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBook(PlayerEditBookEvent event) {
        BookMeta book = event.getNewBookMeta();
        List<String> text = new ArrayList<>();
        for (Component page : book.pages()) text.add(PLAIN.serialize(page));
        if (event.isSigning() && book.hasTitle()) text.add(PLAIN.serialize(book.title()));
        String refusal = refusal(event.getPlayer(), text, Settings.Filtered::books, "filtered-book");
        if (refusal != null) {
            event.setCancelled(true);
            plugin.tell(event.getPlayer(), refusal);
            return;
        }
        WordFilter mask = masker(event.getPlayer(), Settings.Filtered::books);
        if (mask == null) return;
        boolean changed = false;
        List<Component> pages = new ArrayList<>();
        List<String> plainPages = new ArrayList<>();
        for (Component page : book.pages()) {
            String plain = PLAIN.serialize(page), masked = mask.mask(plain);
            changed |= !masked.equals(plain);
            pages.add(masked.equals(plain) ? page : Component.text(masked));
            plainPages.add(masked);
        }
        if (event.isSigning() && book.hasTitle()) {
            String plain = PLAIN.serialize(book.title()), masked = mask.mask(plain);
            if (!masked.equals(plain)) { book.title(Component.text(masked)); changed = true; }
        }
        if (!changed) return;
        // An unsigned book keeps plain-text pages; a signed one keeps formatted pages.
        if (event.isSigning()) book.pages(pages);
        else book.setPages(plainPages);
        event.setNewBookMeta(book);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onAnvil(PrepareAnvilEvent event) {
        AnvilView view = event.getView();
        String name = view.getRenameText();
        if (event.getResult() == null || name == null || name.isBlank() || !(view.getPlayer() instanceof Player player)) return;
        String refusal = refusal(player, List.of(name), Settings.Filtered::itemNames, "filtered-item-name");
        if (refusal == null) {
            WordFilter mask = masker(player, Settings.Filtered::itemNames);
            String masked = mask == null ? name : mask.mask(name);
            if (masked.equals(name)) return;
            org.bukkit.inventory.ItemStack result = event.getResult().clone();
            org.bukkit.inventory.meta.ItemMeta meta = result.getItemMeta();
            if (meta == null) return;
            meta.customName(Component.text(masked));
            result.setItemMeta(meta);
            event.setResult(result);
            return;
        }
        event.setResult(null);
        long now = System.currentTimeMillis();
        Long last = anvilNotes.get(player.getUniqueId());
        if (last == null || now - last > ANVIL_NOTE_MILLIS) {
            anvilNotes.put(player.getUniqueId(), now);
            plugin.tell(player, refusal);
        }
    }

    /**
     * With mask-for-viewers on, signs, books and item names are stored once for everyone, so an unfiltered writer's
     * blocked words are saved as **** and filtered players never see them. Returns the filter to mask with, or null
     * when nothing should change.
     */
    private WordFilter masker(Player player, java.util.function.Predicate<Settings.Filtered> check) {
        PossePlugin.State state = plugin.state();
        if (state == null) return null;
        Settings settings = state.settings();
        if (!settings.filtered().maskForViewers() || !check.test(settings.filtered()) || !player.hasPermission(WordFilter.BYPASS)) return null;
        return settings.filter();
    }

    /** The message key explaining why this text can't be written, or null if it's fine. */
    private String refusal(Player player, List<String> text, java.util.function.Predicate<Settings.Filtered> check, String filteredKey) {
        PossePlugin.State state = plugin.state();
        if (state == null) return "writing-paused";
        Settings settings = state.settings();
        if (!settings.reach().maySpeak(Subject.of(player))) return "writing-closed-here";
        if (check.test(settings.filtered()) && !player.hasPermission(WordFilter.BYPASS)) for (String part : text) if (settings.filter().flags(part)) return filteredKey;
        return null;
    }
}
