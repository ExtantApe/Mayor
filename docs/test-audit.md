# Test audit

Kept by this repo's owning session (Minecraft: Plugins Lead), per `~/Code/CLAUDE.md`, "Test self-audits". Newest first.


## 2026-11-25: book authors (0.10.0, D291, after a feature)

| Promise | Covered by |
| --- | --- |
| The author and a same-circle player see the name; a player with no shared circle doesn't, even in the same world | unit `BookAuthorsTest`; bot `book-authors.js` (written, not run) |
| The visitor in both circles sees both sides | unit; bot |
| Bypass on either side counts, as in chat | unit |
| Fails closed: an unseen real player is hidden, paused means author only, nobody holding it means hidden | unit `anAuthorMayerNeverSawIsHidden...`, `whilePausedOnlyTheAuthorSeesTheName`, `nobodyHoldingItMeansHidden` |
| A name that is no player, the setting off and no circles show everything | unit |
| A title holding the name is masked, the rest untouched | unit `aTitleHoldingTheNameShowsStarsInstead` |
| A quest-journal-style book behaves the same | bot (written, not run) |
| Nothing is lost: the true author returns when a circle is granted | bot (written, not run); the swap code keeps it in the item's data |
| Chest view follows the viewer | bot (written, not run) |
| Config: default on, off, custom word, a typo is an error | unit `SettingsLoaderTest` |

Gaps: the bot test could not run because the disk was under 40 GB free, so every item-swap path is untested on a server. Not driven by bots at all: item frames, lecterns, hoppers, death drops and the one-tick view after a click. The item swap itself has no unit test, since it needs a running server. Run `POSSE_SUITE=book-authors.js POSSE_PORT=25733 test/live/run.sh <version>` on 26.1.2, 26.2 and 26.3 before release.

## 2026-11-25: home worlds (0.9.0, after a feature)

| Promise | Covered by |
| --- | --- |
| A kid in a kids' world stays; in the adults' world, the station or the main world, they're moved to the fallback | unit `HomeWorldsTest`; live, rejoin of K1 and K2 |
| The visitor in both circles and an adult are never moved | unit; live, rejoin and a teleport into the kids' world |
| A player with no circle waits in the station and is sent back | unit; live, rejoin, respawn and a teleport |
| Respawn is checked at the respawn point | unit `theRespawnPointIsCheckedNotTheCurrentWorld`; live, four players die and respawn |
| A teleport that slips past is undone after the world change | live, a kid teleported to the adults' world |
| Fails closed: a missing fallback lands the player in the main world and says so | unit `aMissingFallbackFailsClosedToTheMainWorld`; live, status text and `--json`, the log warning |
| Only the moved player is told | live, the other players' chat shows none of the lines |
| Off unless configured | unit `offMovesNobody`, `homeWorldsAreOffByDefault`; live, section removed and nobody moves |
| A bad section is an error, so nothing half-applies | unit `homeWorldsMistakesAreErrorsSoNothingHalfAppliesToKids` (seven mistakes) |

Gaps skipped: the first tick between joining and the move (a teleport inside the join event can't be watched by a bot). Not covered and said so: home worlds while chat is paused for an unreadable config; Multiverse and Essentials were not installed in the live run, so the real interplay with them is untested.

## 2026-11-24: presence (0.8.0, after a feature)

| Promise | Covered by |
| --- | --- |
| With `presence: reach`, players see each other in the player list only if they can chat | unit `PresenceTest` (circle-only station, shared circle, no circle, ordinary world, bypass); live, every pair of 8 bots against the suite's own model of reach |
| Join and leave messages go only to players who could chat with them | live, N2 leaves and rejoins and exactly the expected players see each message |
| Circle changes apply within seconds, no relog | live, a circle granted and removed with LuckPerms |
| The default changes nothing | unit `presenceEveryoneShowsEveryone`, `presenceDefaultsToEveryone`; live, switching back to `everyone` lists everyone again |
| Fails closed: paused rules show nobody | unit `pausedRulesShowNobodyButYourself`; live, nobody listed while paused at startup |
| A typo is an error; `reach` with no circles warns | unit `presenceWithATypoIsAnError`, `presenceReachWithoutCirclesWarns` |

Gaps skipped: death and advancement messages and nametags are out of scope and say so in the README. The two-second refresh timer is a scheduler call, covered by the live circle-change check.

## 2026-11-10: the --json console

What's new: every `/posse` command takes `--json <id>` (Signalbox format 1), and there's `/posse describe`. Since the first audit, only CI changes had landed, so this covers the console alone.

| Promise | Covered by |
| --- | --- |
| One answer line per request, including unknown subcommands, missing or extra arguments and bad player names | unit `ConsoleTest.everyBadRequestStillGetsExactlyOneAnswer`; live "an unknown subcommand still gets one answer" |
| `describe` lists every command, format 1 | unit `describeListsEveryCommandWithFormatOne`; live |
| `check` gives both players with UUIDs, `reaches` and a stable reason code; someone who can't chat where they are reaches nobody | unit `checkGivesBothPlayersWithUuidsAndAStableReasonCode`, `someoneWhoCantSpeakThereReachesNobodyEvenInACircle`; live |
| While chat is paused, `check` refuses rather than guess | unit `whilePausedCheckRefusesInsteadOfGuessing` |
| An offline player is said plainly | unit `anOfflinePlayerIsSaidPlainly`; live |
| `status` and `reload` report circles, filter, problems and whether a reload applied | unit; live `status` |
| A player without `posse.admin` gets nothing, `--json` or not | live "a player without posse.admin gets no answer line" |
| Without `--json`, the commands read as before | the existing live checks (`check explains a shared circle`, the reload checks), unchanged |

Live suite: 67/67 on Paper 26.3 (2 skipped, as before). The test bots' two-letter names aren't valid Minecraft names, so the `--json` checks name them by UUID, which the format allows.

**Skipped:** tab completion for the new single argument. It's convenience; Brigadier draws it.

## 2026-11-09: first full audit (at 0.2.0)

Each promise in the README, and the test that holds it to it. "Live" is `test/live/suite.js` on throwaway Paper 26.2 and 26.3 with bots and LuckPerms; "unit" is `src/test`.

| Promise | Covered by |
| --- | --- |
| No circles: nothing changes | unit `noCirclesMeansVanilla`, `noConfigIsVanillaChat`; live "No circles: vanilla chat", "starts with no config as vanilla chat" |
| Same world group reaches, unless circle-only; shared circle reaches anywhere | unit `sameWorldGroupReaches`, `sharedCircleReachesAcrossWorlds`, `circleOnlyWorld`, `worldGroups`, `reachIsSymmetric`; live reach matrix, every pair |
| No circle in a circle-only world: can't chat, message or write, and is told why | live "N2 (hall, no circle) is told…", book edits, `/circles` explanation; signs need a real client (skipped) |
| Private messages out of reach aren't sent; told who instead; selectors refused | live private-message matrix (every command), "/msg with a selector is refused" |
| `/r` re-checked on reply | unit `replies`; live "Replies" |
| `/me` and `/teammsg` off while circles are in use; bypass keeps them | unit `broadcastCommandsOffWhileCirclesAreOn`; live "Blocked and broadcast commands" |
| BlueMapMini shares follow chat | live share section, with a stand-in BlueMapMini firing the share event; BlueMapMini's own guard keeps sharing off with a Posse older than 0.5.0 |
| Blocked commands in every form, own message | unit `blockedCommandsInEveryForm`; live |
| Filter: whole words, disguises, allow list; Scunthorpe passes | unit `wholeWordsOnly`, `seesThroughDisguises`, `allowListWins`, `phrases`, `defaultList*`; live |
| **Blocked text is never logged or stored** | **new** live "filtered words never reach the server log" (chat and `/msg`) |
| Filter bypass, granted and removed without a relog; others still filtered | unit `filterBypass*`; live |
| `posse.bypass` and `.filter.bypass` off for everyone, ops included | unit `bypassesAreOffForEveryoneOpsIncluded` |
| Nobody-heard notes: all before any repeat, cooldown | unit `everyNoteBeforeAnyRepeatAndNeverTwiceInARow`, `cooldown`, `emptyIsQuiet`; live |
| Permission changes take effect without a relog | live "Circles granted and removed with LuckPerms, no relog" |
| Broken config at startup pauses chat and messages; console says why | unit `pausedBlocksMessagesButNotOtherCommands`; live |
| **Admins are told about a broken config when they join** | **new** live "an admin joining is told…", and "a player who isn't an admin isn't" |
| A broken reload keeps the working rules | unit `brokenYamlIsAnErrorWithNoSettings`; live |
| `/circles`, names only with permission | live `/circles` section |
| `/posse check` explains | live |
| 20 players chatting at once stay quick | live "Load" (TPS, 95th percentile under 500 ms) |

**Gaps added this audit:** the two in bold. Both passed on 26.2 and 26.3 (61/61, 2 real-client skips).

**Skipped, with reasons:**
- Sign text and anvil renames: bots can't place and edit signs or use an anvil. They're on the real-client list.
- `ReachService` "safe from any thread": it reads the rules from one snapshot that a reload swaps in whole (a `volatile` field with `final` contents), plus the two players' own permissions and world, the same reads async chat already makes. A dedicated concurrency test would be overkill; the live load test exercises the same path from the async chat thread with 20 bots.


## 2026-11-16: masking for kids (0.6.0, D233, D235)

- **Covered:** `WordFilter.mask` (only blocked words change, phrases masked whole, the allow list, spacing kept, the mask length gives nothing away, nothing blocked gives the same text), the setting off by default and read from `filter.yml`, private messages adult to kid masked and adult to adult as written, kids still stopped. Live on 26.3: chat masked for a player without the bypass and as written for one with it, private messages both ways, clean text unchanged, a kid's own word still stopped, an adult's book saved masked (read back from the server).
- **Skipped:** signs and anvil renames, for the same reason as before (bots can't). Item names and signs share the masking code with books. Live 26.2 wasn't rerun; the change uses no version-specific API.

## 2026-11-24: join and leave lines under `presence: reach` (K682)

A rehearsal bot run on another server saw no join or leave line at all, controls included, and asked whether Posse drops them or the bots can't see them.

- **Result:** Posse delivers them. On throwaway Paper 26.1.2, 26.2 and 26.3, with vanilla join messages on, every control (same circle, and everyone under `presence: everyone`) received the line, and the cross-circle case didn't. Each check reads mineflayer's text events and the raw chat packets, and the two agree. The console logged every line. So the missing lines in that run were the harness or that server's setup, not Posse. Nothing was changed in Posse's behavior.
- **Covered, added:** `PresenceMessagesTest` (same circle receives; other circle doesn't; the visitor in both circles is seen by everyone and sees everyone; the console always gets the line, even with nobody to send it to; the joiner isn't sent their own line; a null message, as with Essentials' custom join message set to none, sends nothing anywhere; `presence: everyone`; paused rules go to the console only). The listener now calls the same `PresenceMessages.deliver` the test runs. Live `presence-messages.js`: six made-up players (two kids, two adults, one in both circles, one with no circle) join and leave in turn in a circle-only default world, first under `presence: everyone` as a control, then `reach`.
- **Skipped:** a plugin that sets a join message after Posse at MONITOR priority, since Posse can't see it and no setting could help. Bots on the 26.x native protocol, since the suite uses 1.21.11 through ViaVersion and ViaBackwards.
- **Known limit:** the joiner's own join line isn't sent to them, which vanilla would do. It can't leak anything; noted in case a client expects it.

