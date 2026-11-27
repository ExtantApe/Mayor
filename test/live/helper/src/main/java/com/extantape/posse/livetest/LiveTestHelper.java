package com.extantape.posse.livetest;

import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

/**
 * Test only. One console command, /mt:
 *   mt world <name>          makes a small flat world (a name ending in _nether or _the_end gets that environment)
 *   mt tp <player> <world>   moves a player to a world's spawn
 *   mt firstspawn <player> <world>  when that player next joins for the first time, moves them to the world 1 tick
 *                            after the join event, as WelcomeWagon or Multiverse's first-spawn would (D297)
 *   mt stall <player> <ms>   after that player next joins, makes each of the next 30 ticks take <ms> longer, so a
 *                            20-tick delay lasts about 20 x <ms> in real time and a test can look inside it (D297)
 *   mt blocktp <player>      cancels every later teleport of that player, so a move Posse tries fails (D297)
 *   mt where <player>        prints "HRT where <player> <world>"
 *   mt tps                   prints "HRT tps <1m> mspt <avg>"
 *   mt worlds                prints "HRT worlds <name,name,...>"
 *   mt give <player> <item>  puts one item in the player's main hand
 *   mt book <player> <author> <title>  puts a signed book in the player's first slot (D291)
 *   mt drop <player>         drops the player's first-slot item 20 blocks east, where nobody stands, and remembers the spot
 *   mt near <player> <other> moves a player onto another player's spot, or onto the last dropped item if other is "drop"
 *   mt clear <player>        empties the player's inventory
 *   mt books <player>        prints "HRT books <player> <author>/<title>;..." for each written book carried
 *   mt chest <player>        puts the first-slot item in a chest at the player's spot, opens it for them and prints "HRT chest <player> ..."
 * And one command any player may use, mtwho <name>, which does nothing but offer every online player's name on Tab,
 * hidden or not: a stand-in for a plugin whose completer knows nothing of Posse (K832).
 */
public final class LiveTestHelper extends JavaPlugin implements Listener {
    /** Bots stand still in made-up worlds, so a death only means a bad spawn point. Keep them alive, and say if one dies anyway. */
    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) event.setCancelled(true);
    }

    private org.bukkit.Location lastDrop;

    /** Players a test killed on purpose (mt grave), so their death isn't reported as a bad spawn. */
    private final java.util.Set<String> planned = new java.util.HashSet<>();

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        String n = event.getPlayer().getName();
        getLogger().info((planned.remove(n) ? "HRT planned death " : "HRT death ") + n);
    }

    /** Made-up first-spawn placements: player name to world name (mt firstspawn). */
    private final java.util.Map<String, String> firstSpawn = new java.util.concurrent.ConcurrentHashMap<>();

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
    public void onFirstJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        Player p = event.getPlayer();
        String world = firstSpawn.remove(p.getName());
        if (world == null || p.hasPlayedBefore()) return;
        Bukkit.getScheduler().runTaskLater(this, () -> {
            World w = Bukkit.getWorld(world);
            if (w != null && p.isOnline()) p.teleportAsync(w.getSpawnLocation()).thenAccept(ok -> getLogger().info("HRT firstspawn " + p.getName() + " " + world + " " + ok));
        }, 1L);
    }

    /** Players whose teleports are cancelled (mt blocktp). */
    private final java.util.Set<String> blocked = java.util.concurrent.ConcurrentHashMap.newKeySet();

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
    public void onTeleport(org.bukkit.event.player.PlayerTeleportEvent event) {
        if (blocked.contains(event.getPlayer().getName())) event.setCancelled(true);
    }

    /** Players whose next join is followed by a main-thread stall: name to milliseconds (mt stall). */
    private final java.util.Map<String, Long> stalls = new java.util.concurrent.ConcurrentHashMap<>();

    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR)
    public void onStallJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        Long ms = stalls.remove(event.getPlayer().getName());
        if (ms == null) return;
        // A single long sleep doesn't work: the server runs the missed ticks back to back afterwards.
        int[] left = {30};
        Bukkit.getScheduler().runTaskTimer(this, task -> {
            if (left[0]-- <= 0) task.cancel();
            else try { Thread.sleep(ms); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        }, 1L, 1L);
    }

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> event.registrar().register(Commands.literal("mtwho")
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            for (Player p : Bukkit.getOnlinePlayers()) builder.suggest(p.getName());
                            return builder.buildFuture();
                        })
                        .executes(ctx -> 1))
                .build()));
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> event.registrar().register(Commands.literal("mt")
                .requires(src -> src.getSender().isOp() || !(src.getSender() instanceof Player))
                .then(Commands.literal("world").then(Commands.argument("name", StringArgumentType.word()).executes(ctx -> {
                    String name = StringArgumentType.getString(ctx, "name");
                    World.Environment env = name.endsWith("_nether") ? World.Environment.NETHER
                            : name.endsWith("_the_end") ? World.Environment.THE_END : World.Environment.NORMAL;
                    World world = new WorldCreator(name).environment(env).type(WorldType.FLAT).generateStructures(false).createWorld();
                    getLogger().info("HRT world " + (world == null ? "FAILED " + name : world.getName()));
                    return 1;
                })))
                .then(Commands.literal("tp").then(Commands.argument("player", StringArgumentType.word())
                        .then(Commands.argument("world", StringArgumentType.word()).executes(ctx -> {
                            Player p = Bukkit.getPlayerExact(StringArgumentType.getString(ctx, "player"));
                            World w = Bukkit.getWorld(StringArgumentType.getString(ctx, "world"));
                            if (p == null || w == null) {
                                getLogger().info("HRT tp FAILED");
                                return 0;
                            }
                            p.teleportAsync(w.getSpawnLocation()).thenAccept(ok -> getLogger().info("HRT tp " + p.getName() + " " + w.getName() + " " + ok));
                            return 1;
                        }))))
                // mt grave <player> <offset>: moves the player offset blocks east of their world's spawn, gives them a
                // stick so there's something to leave, and kills them there, for LanternGraves' grave tests (D231).
                .then(Commands.literal("grave").then(Commands.argument("player", StringArgumentType.word())
                        .then(Commands.argument("offset", com.mojang.brigadier.arguments.IntegerArgumentType.integer(-30, 30)).executes(ctx -> {
                            Player p = Bukkit.getPlayerExact(StringArgumentType.getString(ctx, "player"));
                            if (p == null) {
                                getLogger().info("HRT grave FAILED");
                                return 0;
                            }
                            int off = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "offset");
                            org.bukkit.Location at = p.getWorld().getSpawnLocation().clone().add(off, 0, 0);
                            p.teleport(at);
                            p.getInventory().addItem(new org.bukkit.inventory.ItemStack(org.bukkit.Material.STICK));
                            planned.add(p.getName());
                            Bukkit.getScheduler().runTaskLater(this, () -> {
                                p.setHealth(0);
                                getLogger().info("HRT grave " + p.getName() + " " + p.getWorld().getName() + " " + at.getBlockX() + " " + at.getBlockZ());
                            }, 5L);
                            return 1;
                        }))))
                .then(Commands.literal("firstspawn").then(Commands.argument("player", StringArgumentType.word())
                        .then(Commands.argument("world", StringArgumentType.word()).executes(ctx -> {
                            firstSpawn.put(StringArgumentType.getString(ctx, "player"), StringArgumentType.getString(ctx, "world"));
                            getLogger().info("HRT firstspawn set");
                            return 1;
                        }))))
                .then(Commands.literal("stall").then(Commands.argument("player", StringArgumentType.word())
                        .then(Commands.argument("ms", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 20000)).executes(ctx -> {
                            stalls.put(StringArgumentType.getString(ctx, "player"), (long) com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "ms"));
                            getLogger().info("HRT stall set");
                            return 1;
                        }))))
                .then(Commands.literal("blocktp").then(Commands.argument("player", StringArgumentType.word()).executes(ctx -> {
                    blocked.add(StringArgumentType.getString(ctx, "player"));
                    getLogger().info("HRT blocktp set");
                    return 1;
                })))
                .then(Commands.literal("where").then(Commands.argument("player", StringArgumentType.word()).executes(ctx -> {
                    String name = StringArgumentType.getString(ctx, "player");
                    Player p = Bukkit.getPlayerExact(name);
                    getLogger().info("HRT where " + name + " " + (p == null ? "OFFLINE" : p.getWorld().getName()));
                    return 1;
                })))
                .then(Commands.literal("worlds").executes(ctx -> {
                    getLogger().info("HRT worlds " + String.join(",", Bukkit.getWorlds().stream().map(World::getName).toList()));
                    return 1;
                }))
                .then(Commands.literal("give").then(Commands.argument("player", StringArgumentType.word())
                        .then(Commands.argument("item", StringArgumentType.word()).executes(ctx -> {
                            Player p = Bukkit.getPlayerExact(StringArgumentType.getString(ctx, "player"));
                            org.bukkit.Material m = org.bukkit.Material.matchMaterial(StringArgumentType.getString(ctx, "item"));
                            if (p == null || m == null) {
                                getLogger().info("HRT give FAILED");
                                return 0;
                            }
                            p.getInventory().setHeldItemSlot(0);
                            p.getInventory().setItem(0, new org.bukkit.inventory.ItemStack(m));
                            getLogger().info("HRT give " + p.getName() + " " + m.getKey().getKey());
                            return 1;
                        }))))
                .then(Commands.literal("book").then(Commands.argument("player", StringArgumentType.word())
                        .then(Commands.argument("author", StringArgumentType.word())
                                .then(Commands.argument("title", StringArgumentType.word()).executes(ctx -> {
                                    Player p = Bukkit.getPlayerExact(StringArgumentType.getString(ctx, "player"));
                                    if (p == null) {
                                        getLogger().info("HRT book FAILED");
                                        return 0;
                                    }
                                    org.bukkit.inventory.ItemStack book = new org.bukkit.inventory.ItemStack(org.bukkit.Material.WRITTEN_BOOK);
                                    org.bukkit.inventory.meta.BookMeta meta = (org.bukkit.inventory.meta.BookMeta) book.getItemMeta();
                                    meta.setAuthor(StringArgumentType.getString(ctx, "author"));
                                    meta.setTitle(StringArgumentType.getString(ctx, "title"));
                                    meta.addPages(net.kyori.adventure.text.Component.text("A made-up page."));
                                    book.setItemMeta(meta);
                                    p.getInventory().setItem(0, book);
                                    getLogger().info("HRT book " + p.getName());
                                    return 1;
                                })))))
                .then(Commands.literal("drop").then(Commands.argument("player", StringArgumentType.word()).executes(ctx -> {
                    Player p = Bukkit.getPlayerExact(StringArgumentType.getString(ctx, "player"));
                    org.bukkit.inventory.ItemStack held = p == null ? null : p.getInventory().getItem(0);
                    if (held == null) {
                        getLogger().info("HRT drop FAILED");
                        return 0;
                    }
                    p.getInventory().setItem(0, null);
                    lastDrop = p.getLocation().add(20, 0, 0);
                    p.getWorld().dropItem(lastDrop, held);
                    getLogger().info("HRT drop " + p.getName());
                    return 1;
                })))
                .then(Commands.literal("clear").then(Commands.argument("player", StringArgumentType.word()).executes(ctx -> {
                    Player p = Bukkit.getPlayerExact(StringArgumentType.getString(ctx, "player"));
                    if (p == null) {
                        getLogger().info("HRT clear FAILED");
                        return 0;
                    }
                    p.getInventory().clear();
                    getLogger().info("HRT clear " + p.getName());
                    return 1;
                })))
                .then(Commands.literal("near").then(Commands.argument("player", StringArgumentType.word())
                        .then(Commands.argument("other", StringArgumentType.word()).executes(ctx -> {
                            Player p = Bukkit.getPlayerExact(StringArgumentType.getString(ctx, "player"));
                            Player o = Bukkit.getPlayerExact(StringArgumentType.getString(ctx, "other"));
                            if (p == null || (o == null && lastDrop == null)) {
                                getLogger().info("HRT near FAILED");
                                return 0;
                            }
                            p.teleportAsync(o == null ? lastDrop : o.getLocation()).thenAccept(ok -> getLogger().info("HRT near " + p.getName() + " " + ok));
                            return 1;
                        }))))
                .then(Commands.literal("books").then(Commands.argument("player", StringArgumentType.word()).executes(ctx -> {
                    Player p = Bukkit.getPlayerExact(StringArgumentType.getString(ctx, "player"));
                    if (p == null) {
                        getLogger().info("HRT books FAILED");
                        return 0;
                    }
                    getLogger().info("HRT books " + p.getName() + " " + describe(p.getInventory().getContents()));
                    return 1;
                })))
                .then(Commands.literal("chest").then(Commands.argument("player", StringArgumentType.word()).executes(ctx -> {
                    Player p = Bukkit.getPlayerExact(StringArgumentType.getString(ctx, "player"));
                    org.bukkit.inventory.ItemStack held = p == null ? null : p.getInventory().getItem(0);
                    if (held == null) {
                        getLogger().info("HRT chest FAILED");
                        return 0;
                    }
                    org.bukkit.block.Block block = p.getLocation().add(2, 0, 0).getBlock();
                    block.setType(org.bukkit.Material.CHEST);
                    org.bukkit.block.Chest chest = (org.bukkit.block.Chest) block.getState();
                    chest.getBlockInventory().setItem(0, held);
                    p.getInventory().setItem(0, null);
                    p.openInventory(chest.getBlockInventory());
                    // The open event has run by now; read what the player is looking at.
                    getLogger().info("HRT chest " + p.getName() + " " + describe(p.getOpenInventory().getTopInventory().getContents()));
                    Bukkit.getScheduler().runTaskLater(this, () -> p.closeInventory(), 20L);
                    return 1;
                })))
                .then(Commands.literal("tps").executes(ctx -> {
                    double[] tps = Bukkit.getTPS();
                    getLogger().info(String.format(Locale.ROOT, "HRT tps %.2f mspt %.2f", tps[0], Bukkit.getAverageTickTime()));
                    return 1;
                }))
                .build()));
    }

    /** "author/title;author/title" for each written book, so a test sees what the server holds (what the client is sent). */
    private static String describe(org.bukkit.inventory.ItemStack[] items) {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (org.bukkit.inventory.ItemStack item : items) {
            if (item == null || item.getType() != org.bukkit.Material.WRITTEN_BOOK || !(item.getItemMeta() instanceof org.bukkit.inventory.meta.BookMeta meta)) continue;
            out.add(meta.getAuthor() + "/" + net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(meta.title()));
        }
        return out.isEmpty() ? "NONE" : String.join(";", out);
    }
}
