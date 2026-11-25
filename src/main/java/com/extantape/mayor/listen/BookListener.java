package com.extantape.mayor.listen;

import com.extantape.mayor.MayorPlugin;
import com.extantape.mayor.rules.BookAuthors;
import com.extantape.mayor.rules.Subject;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerEditBookEvent;
import io.papermc.paper.event.player.PlayerItemFrameChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerTakeLecternBookEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Written books show their author only to players who may see it (D291): the author, anyone who shares a circle with
 * them, and anyone with mayor.bypass. Everyone else sees "Unknown" (config: book-authors.shown-as).
 * <p>
 * How: Paper can't change an item for one viewer without packet tricks, so the item itself is swapped as it moves. A
 * hidden book carries its true author (and its true title, if that held the author's name) in its own data, under
 * mayor:book-author and mayor:book-title, so nothing is lost and anyone allowed to see it gets it back. The rule is
 * masked unless held by someone who may see it:
 * <ul>
 * <li>A player's own inventory and cursor are set for that player on join, on pickup, after every click, and every
 * two seconds. The author and their circle-mates see the true author, everyone else sees "Unknown", and a circle
 * change shows up within two seconds.</li>
 * <li>Anywhere nobody holds it (dropped, in a hopper or item frame, in a chest nobody has open) the book is masked.
 * A chest or lectern someone has open shows the true author only while everyone looking may see it.</li>
 * </ul>
 * A book whose author name matches no player (a generated lore book) is left alone. A real player Mayor has never
 * seen counts as unknown and is hidden, so this fails closed.
 */
public final class BookListener implements Listener {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final List<Player> NOBODY = List.of();
    /** Inventories that belong to the player looking at them, so the book is theirs to see as themself. */
    private static final Set<InventoryType> PERSONAL = Set.of(InventoryType.PLAYER, InventoryType.CRAFTING, InventoryType.CREATIVE,
            InventoryType.WORKBENCH, InventoryType.ANVIL, InventoryType.GRINDSTONE, InventoryType.CARTOGRAPHY, InventoryType.LOOM,
            InventoryType.SMITHING, InventoryType.STONECUTTER, InventoryType.ENCHANTING, InventoryType.MERCHANT, InventoryType.ENDER_CHEST);

    private final MayorPlugin plugin;
    private final NamespacedKey trueAuthor, trueTitle, authorId;

    public BookListener(MayorPlugin plugin) {
        this.plugin = plugin;
        trueAuthor = new NamespacedKey(plugin, "book-author");
        trueTitle = new NamespacedKey(plugin, "book-title");
        authorId = new NamespacedKey(plugin, "book-author-id");
    }

    // ---- the item swap ----

    /**
     * The book as it should look to everyone in {@code viewers}, or null if it needs no change (not a written book,
     * no author, or already right). An empty list means nobody holds it. It shows the author only if every viewer may
     * see it, or if the rules are off.
     */
    ItemStack as(ItemStack item, List<Player> viewers) {
        if (item == null || item.getType() != Material.WRITTEN_BOOK || !(item.getItemMeta() instanceof BookMeta meta)) return null;
        var data = meta.getPersistentDataContainer();
        String stored = data.get(trueAuthor, PersistentDataType.STRING);
        boolean masked = stored != null;
        String author = masked ? stored : meta.getAuthor();
        if (author == null || author.isBlank()) return null;
        UUID tag = null;
        String id = data.get(authorId, PersistentDataType.STRING);
        if (id != null) {
            try {
                tag = UUID.fromString(id);
            } catch (IllegalArgumentException ignored) {
                // Not a player ID: the author is placed by name instead.
            }
        }

        MayorPlugin.State state = plugin.state();
        boolean show;
        if (state != null && (!state.settings().bookAuthors().hide() || !state.settings().reach().active())) show = true;
        else {
            show = !viewers.isEmpty();
            for (Player viewer : viewers) {
                if (show && !may(state, Subject.of(viewer), author, tag)) show = false;
            }
        }

        if (show == !masked) {
            // Already right. A book seen in its author's hands learns who that is, so the name can be placed later.
            if (!masked && tag == null && viewers.size() == 1 && viewers.get(0).getName().equalsIgnoreCase(author.trim())) {
                data.set(authorId, PersistentDataType.STRING, viewers.get(0).getUniqueId().toString());
                ItemStack tagged = item.clone();
                tagged.setItemMeta(meta);
                return tagged;
            }
            return null;
        }
        ItemStack copy = item.clone();
        if (show) {
            meta.setAuthor(stored);
            String title = data.get(trueTitle, PersistentDataType.STRING);
            if (title != null) meta.title(Component.text(title));
            data.remove(trueAuthor);
            data.remove(trueTitle);
        } else {
            data.set(trueAuthor, PersistentDataType.STRING, author);
            if (meta.hasTitle()) {
                String title = PLAIN.serialize(meta.title());
                String maskedTitle = BookAuthors.maskTitle(title, author);
                if (!maskedTitle.equals(title)) {
                    data.set(trueTitle, PersistentDataType.STRING, title);
                    meta.title(Component.text(maskedTitle));
                }
            }
            meta.setAuthor(state == null ? "Unknown" : state.settings().bookAuthors().shownAs());
        }
        copy.setItemMeta(meta);
        return copy;
    }

    private boolean may(MayorPlugin.State state, Subject viewer, String author, UUID tag) {
        if (state == null) return BookAuthors.mayShowPaused(viewer, author, tag);
        MayorPlugin.Author who = plugin.resolveAuthor(author, tag);
        return BookAuthors.mayShow(state.settings().bookAuthors().hide(), state.settings().reach(), viewer, author, tag,
                who.player(), who.subject());
    }

    private static List<Player> only(Player player) {
        return List.of(player);
    }

    /** Sets every book a player carries, and the one on their cursor, to what that player may see. */
    void set(Player player) {
        Inventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack changed = as(contents[slot], only(player));
            if (changed != null) inventory.setItem(slot, changed);
        }
        ItemStack cursor = as(player.getItemOnCursor(), only(player));
        if (cursor != null) player.setItemOnCursor(cursor);
    }

    /** Every online player, every two seconds, so a circle granted or removed is followed without a relog. */
    public void sweep() {
        for (Player player : Bukkit.getOnlinePlayers()) set(player);
    }

    private void setSoon(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) set(player);
        });
    }

    /** Sets the books in a shared inventory for whoever is looking at it, not counting someone who is leaving. */
    private void share(Inventory inventory, Player leaving) {
        List<Player> viewers = new ArrayList<>();
        boolean stranger = false;
        for (var v : inventory.getViewers()) {
            if (v.equals(leaving)) continue;
            if (v instanceof Player p) viewers.add(p);
            else stranger = true;
        }
        List<Player> who = stranger ? NOBODY : viewers;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack changed = as(inventory.getItem(slot), who);
            if (changed != null) inventory.setItem(slot, changed);
        }
    }

    private static boolean shared(Inventory inventory) {
        return inventory != null && !PERSONAL.contains(inventory.getType());
    }

    private void shareSoon(Inventory top) {
        if (shared(top)) Bukkit.getScheduler().runTask(plugin, () -> share(top, null));
    }

    // ---- events ----

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        set(event.getPlayer());
    }

    /** A signed book remembers who signed it, so its author can still be placed after the server forgets the name. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSign(PlayerEditBookEvent event) {
        if (!event.isSigning()) return;
        BookMeta meta = event.getNewBookMeta();
        meta.getPersistentDataContainer().set(authorId, PersistentDataType.STRING, event.getPlayer().getUniqueId().toString());
        event.setNewBookMeta(meta);
    }

    /** Picked up by a player: set for them before it reaches their inventory. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ItemStack changed = as(event.getItem().getItemStack(), only(player));
        if (changed != null) event.getItem().setItemStack(changed);
    }

    /** A hopper taking an item off the ground holds it for nobody. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHopperPickup(InventoryPickupItemEvent event) {
        ItemStack changed = as(event.getItem().getItemStack(), NOBODY);
        if (changed != null) event.getItem().setItemStack(changed);
    }

    /** Anything that lands on the ground, dropped or from a death or a broken item frame, is held by nobody. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpawn(ItemSpawnEvent event) {
        ItemStack changed = as(event.getEntity().getItemStack(), NOBODY);
        if (changed != null) event.getEntity().setItemStack(changed);
    }

    /** A hopper, dropper or dispenser moving a book. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(InventoryMoveItemEvent event) {
        ItemStack changed = as(event.getItem(), NOBODY);
        if (changed != null) event.setItem(changed);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFrame(PlayerItemFrameChangeEvent event) {
        ItemStack changed = as(event.getItemStack(), NOBODY);
        if (changed != null) event.setItemStack(changed);
    }

    /** A chest or lectern opens showing what everyone looking, the opener included, may see. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onOpen(InventoryOpenEvent event) {
        Inventory top = event.getInventory();
        if (!shared(top)) return;
        // The opener may not be in the viewer list yet, so count them in by hand.
        List<Player> viewers = new ArrayList<>();
        if (event.getPlayer() instanceof Player opener) viewers.add(opener);
        boolean stranger = false;
        for (var v : top.getViewers()) {
            if (v instanceof Player p) {
                if (!viewers.contains(p)) viewers.add(p);
            } else stranger = true;
        }
        List<Player> who = stranger ? NOBODY : viewers;
        for (int slot = 0; slot < top.getSize(); slot++) {
            ItemStack changed = as(top.getItem(slot), who);
            if (changed != null) top.setItem(slot, changed);
        }
    }

    /** When someone leaves, the books go back to what the people still looking may see, or masked if nobody is. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (shared(event.getInventory()) && event.getPlayer() instanceof Player leaving) share(event.getInventory(), leaving);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) setSoon(player);
        shareSoon(event.getView().getTopInventory());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) setSoon(player);
        shareSoon(event.getView().getTopInventory());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLecternTake(PlayerTakeLecternBookEvent event) {
        setSoon(event.getPlayer());
    }
}
