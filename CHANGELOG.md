# Changelog

## 0.11.1

- **A new kid lands where first-spawn puts them** (D297). With `home-worlds` on, a player's first-ever join is now checked 20 ticks (one second) after they join, not at the join, so WelcomeWagon or Multiverse's first-spawn places them first. A new kid placed in an allowed world, such as the lobby, stays there and is told nothing. One placed anywhere else is moved to their fallback as before. A returning player is still checked at the join itself, so nobody who has played before waits a second in a wrong world. If the player quits before the check, nothing happens. If the move doesn't go through, they are disconnected, as before. The respawn and world-change checks are unchanged.
- **A new kid reaches nobody until that check has run.** Between the join and the delayed check, a player home worlds can move (a kid with an entry, or a player in no circle when there is a `no-circle` entry) is pending: their chat isn't delivered, private messages aren't sent either way, nobody's tab lists them, and no join line goes out for them, whatever `presence` says. A player with no home-worlds entry at all, such as an adult, has no pending state and behaves as before. When the check passes, the held join line is sent for where they are, under the usual presence rules. If they quit while pending, no join or leave line is ever sent. `/posse check` reports a pending player as "still being placed". The delayed task keeps the player's ID and looks the player up again when it runs, so a player who quit and came back is never mistaken for the old one. The kick after a failed move now runs on the main thread.
- Old `mayor.*` permission nodes are tested to grant nothing: a player with only `mayor.circle.kids` is in no circle, and one with `mayor.bypass` or `mayor.filter.bypass` has no bypass.
- No Mayor or HouseRules wording remains in anything Posse ships (a unit test now checks `config.yml`, `filter.yml`, `quips.yml` and `paper-plugin.yml`). A `[HouseRules]` prefix on `/posse status` comes from a `config.yml` carried over from an old install, because every message line lives in that file. Change those lines to `[Posse]` when you move the folder.
- Tested: `./gradlew build` (unit tests, including `OldNodesTest`, `ShippedTextTest`, `PendingTest` and the new `HomeWorldsTest` cases), and the home-worlds bot suite on 26.1.2, 26.2 and 26.3 with new bots placed by a stand-in first-spawn (an allowed lobby, an adults' world, and no circle), a new kid with no placement who ends in the fallback, a pending new kid watched by an adult in the same world and a kid in the fallback (no chat, private message, tab entry or join line; the join line and tab entry arrive after the check), a failed delayed move that disconnects (the helper cancels the teleport), and a player holding only old `mayor.*` nodes. The pending window is stretched by a test helper that slows the server's ticks. Not tested: a real WelcomeWagon or Multiverse first-spawn, and a real game client.

## 0.11.0

- **Mayor is now Posse** (D292). Nothing about how it behaves has changed: every circle filter (chat, private messages, who's online, home worlds, book authors, signs, anvil renames, map shares) works as in 0.10.0 and fails closed the same way. No old name, command, permission or folder is kept, so a server owner must rename these before the first start:
  - The folder `plugins/Mayor` becomes `plugins/Posse`, and the jar `Mayor-*.jar` is replaced by `Posse-0.11.0.jar`. Move the old folder; don't copy it.
  - Every permission node `mayor.*` becomes `posse.*` for each group and user: `mayor.admin`, `mayor.bypass`, `mayor.filter.bypass`, `mayor.circle.<name>`, `mayor.circles` and `mayor.circles.names`. A node left under the old name grants nothing. A kid whose circle isn't renamed is in no circle, so home worlds stop holding them and they can share a world, chat and the player list with adults. A `mayor.circles: false` left behind gives `/circles` back.
  - The command `/mayor` is now `/posse`. `/circles` is unchanged.
  - Any config of another plugin that names it: BlueMapMini's `share.audience` (`mayor` and `mayor-world-group` become `posse` and `posse-world-group`), the Ten Winds condition in MobSquad (`mayor.circle.adults` becomes `posse.circle.adults`), and any `mayor.circle` in other plugins' permission settings. Plugins that depend on it (BlueMapAuth, BlueMapMini, LanternGraves) need their Posse releases.
  - The Java package is `com.extantape.posse` (the `api` package with `ReachService` included), for any plugin that looks the service up.
- **Safe order for a server with kids.** Rename the permissions before Posse ever runs:
  1. While Mayor is still running, add every `posse.*` node next to its `mayor.*` node. Mayor ignores them.
  2. Stop the server. Move `plugins/Mayor` to `plugins/Posse`, swap the jar, and change MobSquad's condition and BlueMapMini's `share.audience` in the same stop.
  3. Start with the whitelist on and players kept out. Check that `/posse status` lists the circles and home worlds you expect, and that `/posse check <kid> <adult>` answers NOT_SHARED.
  4. Open the server, then remove the old `mayor.*` nodes.

  A forgotten folder move leaves Posse on its default settings, with no circles, so step 3 matters.
- Book data moves from `mayor:` to `posse:` keys, with no reading of the old ones (owner, 2026-11-25: "Get rid of mayor yes"). Books masked by 0.10.0 would keep "Unknown" for good. No server ran 0.10.0, so no books are affected here.
- Tested: `./gradlew build` (unit tests) and the bot suites on throwaway Paper 26.1.2, 26.2 and 26.3: the unit tests passed, and suite.js passed 84/84 (3 skipped, the LanternGraves checks), book-authors.js 18/18, home-worlds.js 30/30 and presence-messages.js 6/6 on all three. Not run against the renamed BlueMapAuth, BlueMapMini or LanternGraves (their own releases cover that). Not yet seen in a real game client.

## 0.10.0

- **Book authors follow circles** (D291). A written book shows its author only to the author, players who share a circle with them, and players with `mayor.bypass`, the same way chat does. Everyone else sees "Unknown" as the author, and a title that holds the author's name shows `****` for it. This covers signed books and the BetonQuest quest journal, which carries its owner's name. The true author stays on the book (in its own data), so nothing is lost: anyone who may see it gets it back, and a circle granted or removed shows up within two seconds. On by default; `book-authors.hide: false` turns it off, and `book-authors.shown-as` changes the word. With no circles set up nothing is hidden.
- How it works: Paper can't change an item for one viewer without packet tricks, so the item is swapped as it moves. It is masked unless a player who may see the author is holding it. A player's inventory and cursor are set on join, on pickup, after each click and every two seconds. Dropped items, hoppers, item frames and unopened chests hold it masked. A chest or lectern someone has open shows the author only while everyone looking may see it. A signed book also remembers who signed it, so its author can be placed after the server forgets the name.
- Fails closed. An author name that matches a real player Mayor has never seen is hidden. While the config can't be read at startup, only the author sees their name. A name that matches no player at all, such as a generated lore book, is left alone.
- Not covered: names inside a book's pages, books inside shulker boxes or bundles, and the same chest being watched by a same-circle player and a stranger at once (it is masked for both). A click that puts a book in a chest shows its author for one tick to anyone else looking at that chest. An existing item frame or lectern that already holds a book is masked once someone opens it or it is broken.
- Tested: unit tests for the decision (author, same circle, no shared circle even in the same world, the visitor in both circles, bypass, an unknown author, a name that is no player, the tagged ID, paused, the settings off or no circles) and the title masking, and config loading. A bot test (`book-authors.js`) checks a dropped book picked up by an adult, a same-circle kid, the visitor and a player in no circle, a BetonQuest-style journal, a chest, a circle granted and removed, and the settings. Bot test `book-authors.js` passed 18/18 on throwaway Paper 26.1.2, 26.2 and 26.3. Item frames and lecterns are not driven by bots. Not yet seen in a real game client.

## 0.9.0

- **Home worlds, optionally** (D290). A `home-worlds` section in `config.yml` keeps players in the worlds their circles belong in. Players rejoin where they logged out; if that world isn't allowed for their circle, they're moved to the circle's fallback world. Checked on join, on respawn, and after any world change, in case a teleport slips past the world plugin. A player in any circle without an entry (a visitor in both kids' and adults' circles, an adult) is never moved. A player in no circle goes to the `no-circle` fallback, such as a circle-only station. Only the moved player gets a short, friendly line. Off unless the section is there.
- Fails closed. If a fallback world doesn't exist, the player goes to the main world's spawn and the log warns. `/mayor status` (and its `--json` form, now with a `homeWorlds` part) names any missing world. A mistake in the section, such as a fallback that isn't one of the circle's worlds or a circle that isn't listed, is a config error.
- Not covered: the moment between joining and the move, and home worlds while chat is paused for an unreadable config.
- Tested: unit tests for a kid in a kids' world, in the adults' world, the station and the main world, the visitor, an adult, no circle, a missing fallback, and the feature off; config loading for the default, a good section and seven kinds of mistake. Bots on throwaway Paper 26.1.2, 26.2 and 26.3 (30/30 each) checked a rejoin and a respawn for five made-up players, the safety net after a teleport, a missing fallback, and the feature switched off. The existing suite still passes on 26.3 (84/84, 3 skipped). Not yet seen in a real game client, and not run with Multiverse or Essentials.

## 0.8.0

- **Who's online follows the rules, optionally** (D283). With `presence: reach` in `config.yml`, the player list (Tab) and join and leave messages show a player only to people they could chat with. A kid and an adult who share no circle don't see each other online, including in a circle-only lobby. Checked on join, leave and world change, and every two seconds for circle changes, so no relog is needed. If the config can't be read at startup, nobody is shown to anyone until it's fixed. The default, `presence: everyone`, changes nothing. A typo in `presence` is a config error.
- Not covered: death and advancement messages, and the name above a player's head.
- Tested: unit tests for a circle-only station, a shared circle, no circle, bypass, the default and paused rules; config loading for the default, `reach`, a typo and `reach` with no circles. Bots on throwaway Paper 26.3 (84/84) checked the player list for every pair of 8 players, who saw a player leave and rejoin, a circle granted and removed with LuckPerms, switching back to `everyone`, and nobody listed while paused. Not yet seen in a real game client.

## 0.7.0

- **Who may know whose it is** (D231). `ReachService.mayKnow(viewer, owner, world)` tells another plugin, such as LanternGraves, whether a player may see the owner's name on something they left behind. Stricter than chat, so it fails closed: the owner, either side's bypass, a shared circle, or both players in that world's group right now. Offline owners count only through a circle or bypass; with no circles set up, nobody but the owner.
- Mayor remembers each player's circles and bypass in `known-players.yml` when they join and leave, so the answer works while the owner is offline. A broken file is moved to `known-players.yml.bak` and nobody offline counts as known until they're seen again.
- Tested: unit tests for kids and adults in separate worlds, an adult who left the kids' world or went offline, a shared family circle, a circle-only world, bypass, no circles, an unknown owner, and the file surviving a restart or breaking. Bots on a throwaway Paper 26.3 server with LanternGraves 0.2.0 saw names or "Grave" exactly as the rule says (94/94). Not yet seen in a real game client.

## 0.6.1

- `mask-for-viewers` no longer gets a warning at start-up saying it isn't a setting. 0.6.0 read and used it all along; only the list of known settings missed it.
- Tested: unit tests, including a check that the setting raises no warning. Not run on a live server.

## 0.6.0

- **Adults' words masked for kids** (D233, D235). A new `mask-for-viewers` setting in `filter.yml`, off by default. With it on, a player with `mayor.filter.bypass` writes freely, and anyone without the bypass sees each blocked word as `****` in that player's chat and private messages, while players with the bypass see them as written. A player without the bypass counts as filtered, so one whose permissions aren't known yet is filtered too.
- Filtered players get their own copy of the chat line, rendered the same way. A signed chat message carries its original words even when it's displayed differently, so changing only how it looks wasn't enough.
- Signs, books and item names are stored once for everyone, so with the setting on an adult's blocked words in them are saved as `****`. Showing them per viewer is planned.
- Players without the bypass are still stopped and told kindly when they use a blocked word, as before.
- Tested: 48 unit tests, 6 of them new, and on throwaway Paper 26.3 with bots 75/75 live checks, 8 of them new. Signs and anvil renames weren't tested, and it hasn't been seen in a real game client.

## 0.5.0

- **A new name: Mayor** (D198). The mayor sets how folks in town talk to each other. How it works is unchanged. As the owner asked (D196), no old names are kept: the package, plugin, jar, commands, permissions and data folder all use Mayor.
- `/hud share` filtering follows BlueMapMini, the map plugin's new name (D194). Mayor hooks only BlueMapMini's share event; the hooks for its earlier names are gone.
- Install it together with BlueMapMini 0.3.0, BlueMapAuth 0.2.0 and OneVoid, which find it by its new name. BlueMapMini keeps sharing off until Mayor 0.5.0 is installed, so shares can never skip chat circles.
- To install over the previous version, with the server stopped: rename its plugin folder to `plugins/Mayor`, move its LuckPerms permissions to the matching `mayor.*` nodes (circles, bypass and filter bypass), then swap the jar.
- Tested: unit tests pass, and the live share check uses a stand-in named BlueMapMini. The real BlueMapMini 0.3.0 and Mayor 0.5.0 haven't been run together yet.

## 0.4.0

- `ReachService.sharesCircle(viewerHas, to)`: for a viewer who isn't in game, such as someone on Mapkeeper's web map, only a shared circle counts (owner decision D54, 2026-11-10). World, world group and bypass don't, and with no circles set up nobody is reached.
- Tested: 42 unit tests, 3 of them new, plus Mapkeeper's bot test with this jar on throwaway Paper 26.1.2 (25/25): an away kid sees only their circle, and in game chat's reach applies. Chat itself is unchanged and wasn't re-run live. Not seen in a real game client.

## 0.3.0

- Every `/houserules` command takes `--json <id>` and answers with one line in the shared Signalbox format, for scripts and the Leaf Juice app. `/houserules describe` lists the commands. The command is still hidden from players without `houserules.admin`.
- `/houserules check` takes player names or UUIDs. Selectors such as `@p` aren't accepted any more.
- Tested on throwaway Paper 26.3 with bots: 67/67 live checks, 7 of them new for `--json`, and 8 new unit tests. Not seen in a real game client.

## 0.2.0

- Follows BlueMapHUD's rename to BlueMinimap (2026-11-08): coordinate shares from BlueMinimap 0.2 are filtered like chat. Shares from BlueMapHUD 0.1.x are still filtered for this one release, so the two can be updated in either order. Update both together: BlueMinimap 0.2 turns `/hud share` off when HouseRules is older than 0.2.
- No change to chat, private messages, the word filter or circles.

## 0.1.1

- `houserules.filter.bypass` lets a group skip the word filter in chat, private messages, signs, books and item names. It's off for everyone by default, ops included, and changes take effect without a relog.
- `/houserules check` says when a player skips the word filter.

## 0.1.0

First release.

- Chat circles: chat and private messages reach players in the same world group, or anyone sharing a circle. Circle-only worlds, such as a lobby, count circles only.
- `/msg`, `/tell`, `/w` and replies are checked against the same rules, and replies are checked again when sent.
- Commands that would broadcast past the circles (`/me`, `/teammsg`) are off while circles are in use.
- Blocked commands, each with its own message, covering namespaced and mixed-case forms.
- A word filter for chat, private messages, signs, books and item names, with a starting list in `filter.yml`.
- A light-hearted note when nobody could hear you, from a pool of 75 in `quips.yml`.
- `/circles`, and `/houserules reload`, `status` and `check`.
- `houserules.bypass` for moderators.
- An optional hook for BlueMapHUD's `/hud share`, and a `ReachService` for other plugins.
