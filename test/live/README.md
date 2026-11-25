# Live tests

These run Mayor on a real Paper server and drive it with protocol bots. The unit tests in `src/test` cover the rules on their own; these cover everything that needs a server: chat, commands, permissions, reloads and load.

`run.sh` builds one throwaway server on 127.0.0.1, runs the whole suite, and deletes the server afterwards, including when a test fails. Only one server runs at a time. The worlds are small, flat and made up, and so are the players and circles.

## What's real and what isn't

- **Real:** Paper, LuckPerms, the plugin jar, Paper's chat and command handling, and private messages through vanilla `/msg`, `/tell` and `/w`.
- **A bot, not the game:** the players are [mineflayer](https://github.com/PrismarineJS/mineflayer) bots, which speak the 1.21.11 protocol and join 26.x through ViaVersion and ViaBackwards. They read chat as plain text, so colours and how the messages look aren't checked.
- **Mocked:** BlueMapMini's share. A stand-in plugin named BlueMapMini (`fakeminimap/`) fires a copy of its `CoordinateShareEvent`.
- **Not covered:** signs and anvil renames, which the bot can't do reliably. They go through the same check as books, which is covered. Paper has no `/r` of its own, so a reply that passes is seen as reaching the server, not as delivered.

## Setting up

You need Java 25, Node 22 or newer, and about 500 MB of disk while a server runs.

```bash
./gradlew build
cd test/live && npm ci && cd ../..
test/live/fetch.sh 26.2 26.3
```

`fetch.sh` downloads the latest Paper build for each version from PaperMC, ViaVersion and ViaBackwards from Hangar, and LuckPerms from Modrinth, into `test/live/work/`, which git ignores. Each file is checked against its published checksum, and a mismatch stops it.

## Running

Running a server means accepting the [Minecraft EULA](https://aka.ms/MinecraftEULA), so `run.sh` won't start until you set `MAYOR_ACCEPT_EULA=1`.

```bash
MAYOR_ACCEPT_EULA=1 test/live/run.sh 26.3
```

It takes about 4 minutes. The summary lists failures and skipped checks. The full output is in `test/live/work/server-<version>.log`, and the server console in `server-<version>-console.log`. The game port is 25619; change it with `MAYOR_PORT`.

## What the suite checks

| Part | What it checks |
| --- | --- |
| No config | A fresh install changes nothing: everyone hears everyone. |
| Reach matrix | Eight players in two circles, with no circle, and with bypass, across two world groups (one by suffix, one listed), their Nether and End, and a circle-only world. Public chat from each, and 336 private messages over six commands, match an independent model of the rule. |
| Join and leave lines | `presence-messages.js`, run with `MAYOR_SUITE=presence-messages.js test/live/run.sh <version>` (about 1 minute): six made-up players join and leave in turn, first with `presence: everyone` as a control, then `reach`. Each line is read from mineflayer's text events and from the raw chat packets, and both must match. The main suite's presence part also covers it. |
| Book authors | `book-authors.js`, run with `MAYOR_SUITE=book-authors.js MAYOR_PORT=25733 test/live/run.sh <version>`: a kid's signed book is dropped and picked up by an adult (sees Unknown), a same-circle kid, the visitor in both circles and a player in no circle; a quest-journal-style book with the name in its title; a chest opened by an adult and by a kid; the true author coming back when a circle is granted; the settings. A bot can't read a tooltip, so each check reads the server's own copy of the book, which is what the client is sent. Item frames and lecterns aren't driven. |
| Home worlds | `home-worlds.js`, run with `MAYOR_SUITE=home-worlds.js test/live/run.sh <version>` (about 2 minutes): five made-up players leave in different worlds, then rejoin, die and respawn, and are teleported around. Kids end up only in kids' worlds, the visitor and an adult are left alone, a player with no circle waits in the station, a missing fallback lands in the main world, and with the section removed nothing moves. Run it with a port in your own range, such as `MAYOR_PORT=25733`. |
| `/circles` | Lists your circles and exactly who can hear you, a count without `mayor.circles.names`, and an explanation in a circle-only world. |
| Commands | Blocked commands in plain, namespaced and mixed-case forms, unlisted ones passing, `/me` off except with bypass. |
| Replies | `/r` with nobody to reply to, and after the other player moves out of reach. |
| Word filter | Chat and private messages with a blocked word aren't delivered; "Scunthorpe has a classic assassin" is. |
| Masking for kids | With `mask-for-viewers` on, an adult's blocked word reaches a player without the bypass as `****` in chat and private messages and as written to another adult; clean text is unchanged; a kid's own blocked word is still stopped; an adult's book is saved masked (checked on the server, since the bot sees only its own copy). |
| Filter bypass | Granted with LuckPerms, a player's chat and private messages aren't filtered while another player's still are; removed, they're filtered again straight away, with no relog. |
| Books | A clean edit is kept; a filtered one, or one in a circle-only world with no circle, is refused with an explanation. A filtered word is kept for a player with `mayor.filter.bypass`. |
| Nobody heard you | A note from `quips.yml`, a different one next time, and none when someone heard. |
| Share (mocked) | Recipients filtered exactly as chat, and cancelled for a no-circle sender in a circle-only world. |
| Graves (D231) | With a LanternGraves jar (`MAYOR_LG_JAR`), real graves from planned deaths: each bot sees the owner's name or "Grave" exactly as an independent model of `mayKnow` says, with the owner in the same group, moved away, sharing a circle, offline, and in a circle-only world. Skipped without the jar. |
| LuckPerms | A circle granted or removed takes effect with no relog. |
| Config | A broken reload keeps the old rules; a broken config at startup pauses chat and private messages until it's fixed and reloaded. |
| Load | 20 more bots, 300 lines in about 15 seconds: all delivered within their circle, TPS, chat latency, no kicks. |

## The test-only plugins

`helper/` makes worlds, moves players, gives items and reports TPS, with one console command, `/mt`. It also stops test players taking damage, since a bot that spawns inside a block in a made-up Nether would otherwise die and respawn in the wrong world. `fakeminimap/` is the BlueMapMini stand-in. Both are built by `./gradlew build` and never shipped. Never install either on a real server.
