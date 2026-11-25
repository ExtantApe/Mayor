# Posse

Chat rules for family and community Paper servers. Players hear people in the same world, plus anyone who shares a "circle" with them, so groups such as families or age groups keep their own conversations across every world.

**Status:** pre-release. Version 0.11.1 (new kids are checked a second after joining, so first-spawn places them first) passed the unit tests and every bot suite on throwaway Paper 26.1.2, 26.2 and 26.3. Before that, it passed its acceptance tests on throwaway Paper 26.2 and 26.3 servers, using protocol bots with LuckPerms: chat and private messages for every pair of players, who sees whom in the player list and join messages (`presence: reach`), home worlds on rejoin, respawn and teleport, replies, blocked commands, the word filter and its bypass, masking adults' words for kids, books, `/circles`, reloads, a broken config, and 20 bots chatting at once. BlueMapMini's share was tested with a stand-in plugin here; the real BlueMapMini 0.3.0 and Posse 0.5.0 haven't been run together yet. Signs and anvil renames weren't tested. It hasn't been seen in a real game client yet, so the wording and colours haven't been checked by a person. See [the live tests](test/live/README.md) and the [test audit](docs/test-audit.md).

- Requires Paper 26.1 or newer and Java 25. Paper 1.21.11 and older aren't supported.
- With no circles set up it changes nothing about who hears whom, so it's safe to install first and configure after.

## What it does

**Circles.** A circle is a permission, `posse.circle.<name>`, and the circle names are listed in `config.yml`. A player can be in several. Two players can hear each other if:

1. they're in the same world group, unless it's a circle-only world, or
2. they share a circle.

Otherwise they can't. A world group is a world with its Nether and End (`survival`, `survival_nether`, `survival_the_end`), or any set of worlds you list under `world-groups`.

**Circle-only worlds.** In these worlds, such as a lobby where new players wait to be approved, being in the same world doesn't count. A player with no circle can't chat, message or write there, and is told why.

**What follows the rules:**

- Public chat goes only to the players who can hear you.
- Private messages (`/msg`, `/tell`, `/w` and the others in `private-message-commands`) to someone out of reach aren't sent, and you're told who you can talk to instead.
- `/r` and `/reply` are checked again when you reply, because the other player may have moved.
- Commands that would reach the whole server, `/me` and `/teammsg` by default, are off while circles are in use.
- BlueMapMini's `/hud share`, if BlueMapMini is installed, goes only to players who could hear you in chat. BlueMapMini keeps sharing off until Posse is 0.5.0 or newer.

**Who's online (optional).** With `presence: reach` in `config.yml`, the player list (Tab) and join and leave messages follow the same rule: you only see players you could chat with. A kid and an adult who share no circle don't see each other online. It's checked on join, leave and world change, and every two seconds for circle changes, so no relog is needed. If the config can't be read at startup, nobody is shown to anyone until it's fixed. The default, `presence: everyone`, leaves both as in vanilla. Death and advancement messages and a player's name above their head aren't covered.

**Home worlds (optional).** For a server where a kids' group must never end up in an adults' world. Players rejoin where they logged out, and a world plugin often checks access only when you teleport, not when you join. With a `home-worlds` section in `config.yml`, Posse steps in when someone is in a world their circle doesn't allow:

- A circle with an entry lists its worlds and a fallback. A player there who is somewhere else is moved to the fallback's spawn, on join, on respawn, and after any world change. A kid who logged out in a kids' world stays where they were.
- A player in any circle with no entry is never moved. That covers adult groups and a visitor who is in both a kids' and an adults' circle.
- A player in no circle goes to the `no-circle` fallback, such as a circle-only station, and is sent back if they leave it.
- A fallback world that doesn't exist fails closed: the player goes to the main world's spawn, the log warns, and `/posse status` names the missing world. They are never left where they were.
- Only the moved player gets a short line, such as "Welcome back! You're in the kids' world." (set per entry with `message`). Nobody else is told.

It is off unless the section is there, and it needs at least one circle. A mistake in the section is a config error, so nothing half-applies. Not covered: the first tick before the move, and a world that Posse can't see because another plugin loads it after the player joins. While chat is paused because the config couldn't be read at startup, home worlds are paused too, because Posse can't tell who is in which circle.

**Book authors.** A written book shows its author only to the author, players who share a circle with them, and players with `posse.bypass`. Everyone else sees "Unknown" (`book-authors.shown-as`), and a title that holds the author's name shows `****` for it. That includes the BetonQuest quest journal, which carries its owner's name. Same world doesn't count, only circles, so a kid and an adult standing together still don't see each other's names. It is on by default; set `book-authors.hide: false` to turn it off. With no circles set up nothing is hidden.

- The true author is kept on the book, so nothing is lost. Whoever may see it gets it back, and a circle granted or removed follows within two seconds.
- Paper can't change an item for one viewer without packet tricks, so the book itself is swapped as it moves: masked unless a player who may see the author holds it. A player's inventory is set on join, on pickup, after clicks and every two seconds. Dropped, hopper, item frame and unopened chest books are masked, and a chest or lectern shows the author only while everyone looking at it may see it.
- Fails closed: a real player Posse has never seen is hidden, and while the config can't be read, only the author sees their name. A name that matches no player, such as a generated lore book, is left alone.
- Not covered: names inside a book's pages, and books inside shulker boxes or bundles.

**Blocked commands.** Any command can be turned off, each with its own message. `sort` also blocks `anyplugin:sort`, in any letter case.

**Word filter.** `filter.yml` lists words and phrases to keep out of chat, private messages, signs, books and item names. It matches whole words, so "class" and "Scunthorpe" are fine, and it sees through `sh1t`, `$` for s and stretched letters. Spaced-out spellings get through. Blocked text is never logged or stored: the player is told kindly and can try again. Players with `posse.filter.bypass`, such as an adults group, aren't filtered anywhere.

**Masking for kids.** Where adults and kids play together, set `mask-for-viewers: true` in `filter.yml`. Adults (players with `posse.filter.bypass`) write freely, and everyone else sees each blocked word in their chat and private messages as `****`, while other adults see it as written. A player without the bypass counts as filtered, so a new account is masked until someone adds it to the adults group. Signs, books and item names are stored once for everyone, so an adult's blocked words in them are saved as `****`.

**Nobody heard you.** If you chat and nobody online can hear you, you get a light-hearted note, such as "A squid nods politely. It didn't understand a word." There are 75 in `quips.yml`, and you see them all before any comes round again. At most one every 3 minutes.

Every message players read is in `config.yml`, in [MiniMessage](https://docs.advntr.dev/minimessage/format.html). The defaults are short and kind, say what to do instead, and never rely on colour alone.

## Install

1. Put `Posse-<version>.jar` in `plugins/` and start the server.
2. List your circles in `plugins/Posse/config.yml`, and any circle-only worlds.
3. Give each player their circle permission with your permission plugin, for example with LuckPerms: `/lp group family permission set posse.circle.family`.
4. Run `/posse reload`.

Permission changes take effect straight away, with no relog.

If the config can't be read when the server starts, there are no rules to follow, so chat and private messages are paused, and admins are told when they join. Fix the file and run `/posse reload`. A reload with mistakes keeps the rules that were working, and says what's wrong.

## Commands

| Command | What it does |
| --- | --- |
| `/circles` | Shows your circles and who online can hear you right now. |
| `/posse reload` | Reads `config.yml`, `filter.yml` and `quips.yml` again. |
| `/posse status` | Shows the circles, whether the filter is on, and any config warnings. |
| `/posse check <player> <player>` | Says whether the first can reach the second, and why. |
| `/posse describe` | Lists these admin commands. |

Every `/posse` command also takes `--json <id>` at the end, for scripts and the Leaf Juice app. It then answers with one `ea-result <id> {...}` line in the shared [Signalbox](https://github.com/ExtantApe/Signalbox) format: `status` gives the circles, the filter and any problems; `reload` whether the new rules were applied; `check` both players with their UUIDs, `reaches` and a reason code such as `shared-circle`; `describe` the commands. Players are named or given by UUID; selectors like `@p` aren't accepted.

## Permissions

| Permission | Default | Allows |
| --- | --- | --- |
| `posse.circle.<name>` | nobody | Being in that circle. |
| `posse.circles` | everyone | `/circles`. |
| `posse.circles.names` | everyone | Names in `/circles`. Without it, only a count. |
| `posse.bypass` | nobody, ops included | Reaching and being reached by everyone, for moderators. It doesn't show anyone else's chat. |
| `posse.filter.bypass` | nobody, ops included | Skipping the word filter everywhere it applies, for adult groups. It doesn't change who hears whom. With `mask-for-viewers` on, it's also what lets a player read other adults' words as written. |
| `posse.admin` | ops | `/posse`, and config warnings on joining. |

## For other plugins

Posse registers a `ReachService` with Bukkit's services manager:

```java
ReachService rules = Bukkit.getServicesManager().load(ReachService.class);
if (rules == null || rules.mayReach(from, to)) { /* send it */ }
```

`mayReach(from, to)` and `maySpeak(player)` follow exactly the same rules as chat, and are safe from any thread.

`sharesCircle(viewerHas, to)` is for a viewer who isn't in game, such as someone on a web map. It's true only when they share a circle with `to`. World, world group and bypass don't count, and with no circles set up it's always false. The caller passes the viewer's permissions, read while they're offline, and anything it can't check should answer false.

`mayKnow(viewer, owner, world)` says whether a player may know who left something in a world, such as a grave's name. It's stricter than chat, so it fails closed: the owner, either side's `posse.bypass`, a shared circle, or both players standing in that world's group right now (unless it's circle-only). An offline owner counts only through a circle or bypass, from what Posse last saw of them. With no circles set up, nobody but the owner may, and it answers false for anyone Posse has never seen. Call it from the main thread. For that, Posse keeps `known-players.yml`: each player's ID with the circle names they held and whether they had `posse.bypass`, updated when they join and leave and on /posse reload. Nothing else about them is stored. If you take someone out of a circle while they're offline, Posse still uses their old circles until they next join; delete their line from the file (with the server stopped) to make it take effect at once.

## Limits

- Book pages aren't checked for names, and a book moved into a chest shows its author to anyone else looking at that chest for one tick.
- Signs are readable by anyone who walks past, and a book or named item can be carried to another world. Posse only checks them when they're written.
- Chat plugins that send messages their own way, rather than through Paper's chat event, aren't covered. Add their message commands to `private-message-commands`.
- Only the commands listed in `private-message-commands` and `reply-commands` are checked. `/r` is checked against the last conversation Posse saw.

## Building

```bash
./gradlew build
```

The jar is `build/libs/Posse-<version>.jar`. Live tests with bots are in `test/live`.

## License

MIT. See [LICENSE](LICENSE).
