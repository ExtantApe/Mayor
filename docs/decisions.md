# Decisions

Owner decisions for HouseRules, newest first.

| Date | Decision | Why |
| --- | --- | --- |
| 2026-11-08 | `houserules.filter.bypass` lets a group skip the word filter everywhere it applies: chat, private messages, signs, books and item names. Off for everyone by default, ops included. Granted to adult groups only; children stay filtered everywhere. | Adults never share a world with children, so filtering adults protects nobody. Off by default so nobody is unfiltered by accident. |
| 2026-11-07 | Signs, books and item names follow the chat rule: where a player can't chat, they can't write either. A configurable word filter (`filter.yml`) checks chat, private messages, signs, books and item names. Blocked text is never logged or stored. | Covers everything players write, not just chat. Not logging matches the no-spying rule for moderators. |
| 2026-11-07 | `/circles` is on for everyone by default and shows the names of people who can hear you. Both parts are permissions (`houserules.circles`, `houserules.circles.names`), so they can be changed per group. | Different groups of players, such as children and adults, never share a world, so a child only sees other children who are meant to be there. Hidden (vanished) players are never shown. |
| 2026-11-07 | When someone chats and nobody can hear them, they get a funny, never mean, note from a large pool (`quips.yml`), shuffled so repeats are rare, at most once every 3 minutes by default. | Silence is confusing. A light note explains it without nagging. |
| 2026-11-07 | `houserules.bypass` lets a moderator reach, and be reached by, everyone. It shows nobody else's chat and logs nothing. Off for everyone by default, ops included. | Moderation without chat spying. Off by default so nobody can reach children by accident. |
| 2026-11-07 | `/r` and `/reply` check reach again at the moment of the reply. | The other player may have moved to a world out of reach since the conversation started. |
| 2026-11-07 | No `/sort` block in the defaults. | Portmanteau, the planned ChestSort replacement, keeps automatic sorting on by itself, and none of its commands turn it off. |
| 2026-11-07 | `/hud share` from BlueMapHUD follows the same reach as chat, with no narrower rule for kids. | One rule for everything players send each other. |
| 2026-11-10 | A viewer who isn't in game, such as someone on the web map, sees only players who share a HouseRules circle with them (owner, D54). | Chat's world rule needs a world, and an away viewer has none. |
| 2026-11-07 | Every list is in config, including circles, circle-only worlds, world groups, the dimension suffixes, message and reply commands, blocked commands and every message. With no circles, chat is vanilla. | Generic and publishable, with no server's names in the code. |
- **BlueMinimap (2026-11-08, owner rename of BlueMapHUD):** 0.2.0 listens for `com.extantape.blueminimap.api.CoordinateShareEvent` when BlueMinimap is installed, and still for the old `com.extantape.bluemaphud.api` event when only BlueMapHUD 0.1.x is, for one release. Released together with BlueMinimap 0.2.0, which turns sharing off when HouseRules is older than 0.2, so no order of upgrades lets a share skip the circles.

## 2026-11-14

- **Map plugin's new name (owner, D194, D196):** the share hook follows BlueMapMini only. No hooks or stand-ins for its earlier names are kept.
- **Name (owner, D198):** the plugin is renamed Mayor. The owner chose "Mayor (Recommended)". Released as 0.5.0 with no old names kept, installed together with BlueMapMini 0.3.0.

## 2026-11-24

- **Who's online (owner, D283):** before kids are let into the second server, players who share no circle don't see each other in the player list or join messages there. The owner chose "Circles like [the main server] (Recommended)" after a safety review found adults could see and chat with kids at a shared station. Built as `presence: reach`, off by default so other servers keep vanilla behavior. It fails closed when the config can't be read.

## 2026-11-25

- **Home worlds (owner, D290, building on D283 and D288):** everyone rejoins wherever they logged out. On a server with kids' groups, a kid can't end up outside the kids' worlds: they rejoin where they logged out if that's a kids' world, and otherwise in their fallback. Someone in no group waits in the circle-only station. Multiverse checks world access on teleport, not on join, and Essentials' spawn-on-join is all-or-nothing, so Mayor guards it, since it already knows circles. Built as the optional `home-worlds` section, 0.9.0. A player in any circle without an entry is never moved, which exempts the visitor in both circles. A missing fallback falls back to the main world's spawn, never to staying put.
- **Book authors follow circles (owner, D291):** "How about it respects circles? So just like specific adult circles dont cross over to kids, their things like that shouldnt either"; then, to the playback: "Yes, record it (Recommended)" (Head of Games chat, 2026-11-24, on the quest journal showing its owner as author). Anything that carries a player's name follows Mayor's circles the same way chat does. A player sees the name only if they share a circle with that person; otherwise they see no name. Mayor already filters sign text and anvil renames; it gains a book-author filter, 0.10.0. Mechanism: the book item is swapped as it moves, with the true author kept in its own data, since Paper has no per-viewer item API and a packet library would be a new dependency. Choices made in the build: same world doesn't count (circles only, unlike chat); bypass on either side counts; an author Mayor has never seen is hidden; a name that matches no player is left alone; on by default, with nothing hidden when no circles are set up.

## 2026-11-24 (rename)

- **Posse (owner, D292, K736):** Mayor is renamed Posse, after a name check found Posse free on Modrinth, SpigotMC, Hangar and GitHub. Released as 0.11.0 so the version line continues. D196 applies: no old name, command, permission, folder or package is kept, and installs rename the folder and permission nodes before first start. Entries above this one keep saying Mayor, because they record what was decided then.

## 2026-11-25: Rename safety, checklist and no old keys

After the Trust and Safety review of the rename, the owner chose:
- **No folder guard in code** ("No one else uses this yet"). Instead, the live checklist checks `/posse status` and `/posse check` with players kept out.
- **No reading of old `mayor:` book keys** ("Get rid of mayor yes"). No server ran 0.10.0, so no book loses its author.
