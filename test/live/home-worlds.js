// Home worlds (D290) on one throwaway server: a rejoin and a respawn, plus the world-change safety net and a missing
// fallback. See README.md in this folder. Usage: node home-worlds.js <dir> <jar> <port>, relative to the work folder.
// All names are made up. Run it with POSSE_SUITE=home-worlds.js test/live/run.sh <version>.
const crypto = require('crypto');
const { sleep, check, section, summary, Server, Bot } = require('./lib.js');
const [dir, jar, port] = process.argv.slice(2);

const CONFIG = `circles: [kids, adults]
home-worlds:
  kids:
    worlds: [kidland, kidland_nether]
    fallback: kidland
    message: "<gray>HOMEKID Welcome back! You're in the kids' world."
  no-circle:
    fallback: station
`;
// Where each made-up player last was, before the rules were on. K = kids' circle, A = adults', V = both, X = none.
const PEOPLE = {
  K1: { circles: ['kids'], last: 'grownups' },
  K2: { circles: ['kids'], last: 'kidland_nether' },
  A1: { circles: ['adults'], last: 'grownups' },
  V1: { circles: ['kids', 'adults'], last: 'grownups' },
  X1: { circles: [], last: 'grownups' },
};
const NAMES = Object.keys(PEOPLE);
let s; const bots = {};

async function where(n) {
  const m = await s.run(`mt where ${n}`, new RegExp(`HRT where ${n} (\\S+)`), 5000);
  return m ? m[1] : null;
}
async function waitWhere(n, want, ms = 8000) {
  const end = Date.now() + ms; let w = null;
  while (Date.now() < end) { w = await where(n); if (w === want) return w; await sleep(400); }
  return w;
}
async function join(n) { bots[n] = new Bot(n, +port); await bots[n].spawned; await sleep(1500); }
async function reload() { return s.run('posse reload', /\[Posse\] (Reloaded|The config has problems, so nothing changed|The config still has problems)[^\n]*/, 10000); }
// The protocol sends listed as 1 or 0; an entry with no listed update yet counts as listed.
const listedTo = (viewer, other) => { const p = bots[viewer].bot.players[other]; return !!p && (p.listed === undefined || Boolean(p.listed)); };
const text = (n, re, from = 0) => bots[n].heard(re, from);

(async () => {
  s = new Server(dir, jar);
  await s.start();

  section('Setup: made-up worlds, circles, and where everyone last was');
  for (const w of ['kidland', 'kidland_nether', 'grownups', 'station', 'lobby']) await s.run(`mt world ${w}`, new RegExp(`HRT world (${w}|FAILED)`), 60000);
  for (const n of NAMES) await join(n);
  for (const [n, p] of Object.entries(PEOPLE)) for (const c of p.circles) await s.run(`lp user ${n} permission set posse.circle.${c} true`, null);
  await sleep(1000);
  for (const [n, p] of Object.entries(PEOPLE)) await s.run(`mt tp ${n} ${p.last}`, new RegExp(`HRT tp ${n} ${p.last} true`), 10000);
  await sleep(1500);
  for (const n of NAMES) bots[n].quit();
  await sleep(3000);
  s.writeConfig('config.yml', CONFIG);
  check('reload applies home-worlds', /Reloaded/.test((await reload() || [''])[0]));
  const status = await s.run('posse status', /Home worlds: ([^\n]*)/, 5000);
  check('posse status reports home-worlds', status && /kids stays in 2 worlds, else kidland/.test(status[1]) && /no circle waits in station/.test(status[1]), status && status[1]);
  const m0 = s.mark();

  section('Rejoin: everyone comes back where they left, kids kept to kids\' worlds');
  for (const n of NAMES) await join(n);
  const rejoin = { K1: 'kidland', K2: 'kidland_nether', A1: 'grownups', V1: 'grownups', X1: 'station' };
  for (const [n, want] of Object.entries(rejoin)) {
    const got = await waitWhere(n, want);
    check(`${n} rejoins in ${want}`, got === want, got);
  }
  check('K1 is told, in the config\'s words', text('K1', /HOMEKID Welcome back/));
  check('K2, already in a kids\' world, is told nothing', !text('K2', /HOMEKID|Welcome back|wait here/));
  check('A1 and V1 are told nothing', !text('A1', /HOMEKID|Welcome back|wait here/) && !text('V1', /HOMEKID|Welcome back|wait here/));
  check('X1 is told to wait here', text('X1', /wait here until an admin adds you to a group/));
  check('nobody else hears those lines', ['K2', 'A1', 'V1'].every((n) => !text(n, /wait here until|HOMEKID/)));

  section('Respawn: a kid who dies and would respawn in the main world lands in kidland');
  // With no bed, vanilla respawns in the main world, which is outside the kids' list.
  const afterDeath = { K1: 'kidland', K2: 'kidland', A1: 'world', X1: 'station' };
  for (const [n, want] of Object.entries(afterDeath)) {
    const here = await where(n);
    await s.run(`mt grave ${n} 0`, new RegExp(`HRT planned death ${n}`), 8000);
    await sleep(3000);
    const got = await waitWhere(n, want);
    check(`${n} (died in ${here}) respawns in ${want}`, got === want, got);
  }
  check('K1 is told again after the respawn', text('K1', /HOMEKID Welcome back/));

  section('Safety net: a teleport that slips past the world plugin is undone');
  await s.run('mt tp K2 grownups', /HRT tp K2 grownups true/, 8000);
  check('K2 sent to the adults\' world is back in a kids\' world', (await waitWhere('K2', 'kidland')) === 'kidland', await where('K2'));
  await s.run('mt tp K2 kidland_nether', /HRT tp K2 kidland_nether true/, 8000);
  await sleep(2500);
  check('K2 moving between kids\' worlds is left alone', (await where('K2')) === 'kidland_nether', await where('K2'));
  await s.run('mt tp V1 kidland', /HRT tp V1 kidland true/, 8000);
  await sleep(2500);
  check('the visitor in both circles is left where they went', (await where('V1')) === 'kidland', await where('V1'));
  await s.run('mt tp A1 kidland', /HRT tp A1 kidland true/, 8000);
  await sleep(2500);
  check('an adult is left where they went', (await where('A1')) === 'kidland', await where('A1'));
  await s.run('mt tp X1 grownups', /HRT tp X1 grownups true/, 8000);
  check('a player with no circle is sent back to the station', (await waitWhere('X1', 'station')) === 'station', await where('X1'));

  section('First join (D297): placement after the join event is respected, a disallowed placement is still undone');
  // The lobby is a kids' world here. F1 is placed there on first join, F2 in the adults' world, F3 has no circle.
  // O1 holds only the old mayor.* nodes, which grant nothing.
  s.writeConfig('config.yml', CONFIG.replace('[kidland, kidland_nether]', '[kidland, kidland_nether, lobby]'));
  await reload();
  const uuid = (n) => { const h = crypto.createHash('md5').update('OfflinePlayer:' + n).digest(); h[6] = (h[6] & 0x0f) | 0x30; h[8] = (h[8] & 0x3f) | 0x80; const x = h.toString('hex'); return `${x.slice(0, 8)}-${x.slice(8, 12)}-${x.slice(12, 16)}-${x.slice(16, 20)}-${x.slice(20)}`; };
  const fresh = { F1: ['posse.circle.kids'], F2: ['posse.circle.kids'], F3: [], O1: ['mayor.circle.kids', 'mayor.bypass', 'mayor.filter.bypass'] };
  for (const [n, perms] of Object.entries(fresh)) for (const p of perms) await s.run(`lp user ${uuid(n)} permission set ${p} true`, null);
  for (const [n, w] of Object.entries({ F1: 'lobby', F2: 'grownups', F3: 'grownups', O1: 'grownups' })) await s.run(`mt firstspawn ${n} ${w}`, /HRT firstspawn set/, 5000);
  for (const n of Object.keys(fresh)) { bots[n] = new Bot(n, +port); await bots[n].spawned; }
  await sleep(6000);
  check('F1, a new kid placed in the allowed lobby, stays there', (await where('F1')) === 'lobby', await where('F1'));
  check('F1 is told nothing', !text('F1', /HOMEKID|wait here/));
  check('F2, a new kid placed in the adults\' world, ends in kidland', (await waitWhere('F2', 'kidland')) === 'kidland', await where('F2'));
  check('F3, a new player with no circle placed in the adults\' world, ends in the station', (await waitWhere('F3', 'station')) === 'station', await where('F3'));
  check('O1, with only old mayor.circle.kids, is in no circle, so is moved to the station', (await waitWhere('O1', 'station')) === 'station', await where('O1'));
  check('nobody was disconnected', Object.keys(fresh).every((n) => !bots[n].kicked && !bots[n].ended));
  const chk = await s.run('posse check O1 F2', /O1 can('t)? reach F2[^\n]*/, 5000);
  check('an old mayor.bypass grants no bypass: O1 cannot reach F2', chk && chk[1] === "'t" && !/bypass/.test(chk[0]), chk && chk[0]);
  const chk2 = await s.run('posse check F2 O1', /F2 can('t)? reach O1[^\n]*/, 5000);
  check('and F2 cannot reach O1 either', chk2 && chk2[1] === "'t", chk2 && chk2[0]);
  s.writeConfig('config.yml', CONFIG);
  await reload();

  section('Pending first join (D297): a new kid reaches nobody until the delayed check has run');
  // presence: reach is on. A1 (adult) stands in the main world; K1 (kid) stands in kidland. G1 is a new kid with no
  // first-spawn placement, so they are left in the main world and the check must move them to kidland. The helper
  // slows the server's next 30 ticks to a quarter second each after G1 joins, so the pending state lasts long enough to look at.
  s.writeConfig('config.yml', CONFIG.replace('[kidland, kidland_nether]', '[kidland, kidland_nether, lobby]') + 'presence: reach\n');
  await reload();
  await s.run('mt tp A1 world', /HRT tp A1 world true/, 8000);
  await sleep(1500);
  for (const n of ['G1', 'H1']) await s.run(`lp user ${uuid(n)} permission set posse.circle.kids true`, null);
  const a0 = bots.A1.mark(), k0 = bots.K1.mark(), ra0 = bots.A1.rawMark(), rk0 = bots.K1.rawMark();
  await s.run('mt stall G1 250', /HRT stall set/, 5000);
  bots.G1 = new Bot('G1', +port); await bots.G1.spawned; await sleep(500);
  const g0 = bots.G1.mark();
  bots.G1.say('pendingchat hello');
  bots.G1.say('/msg A1 pendingpm one');
  bots.G1.say('/msg K1 pendingpm two');
  bots.A1.say('/msg G1 pendingpm three');
  await sleep(2500);
  // Looked at while the server is still stalled, so G1's delayed check cannot have run.
  check('A1, in the same world, hears no chat from G1', !text('A1', /pendingchat/, a0) && !bots.A1.rawHeard(/pendingchat/, ra0));
  check('no private message from G1 reaches A1 or K1', !text('A1', /pendingpm one/, a0) && !text('K1', /pendingpm two/, k0));
  check('G1 hears no private message from A1', !text('G1', /pendingpm three/, g0));
  check('A1 sees no join line for G1', !text('A1', /G1 joined the game/, a0) && !bots.A1.rawHeard(/multiplayer\.player\.joined/, ra0));
  check('A1 has no tab entry for G1', !listedTo('A1', 'G1'));
  check('G1 sees nobody in the tab either while pending', !listedTo('G1', 'A1') && !listedTo('G1', 'K1'));
  check('K1 has no tab entry and no join line for G1 yet', !listedTo('K1', 'G1') && !text('K1', /G1 joined the game/, k0));
  check('G1 is still in the main world while pending', (await where('G1')) === 'world', await where('G1'));
  check('after the check, G1 is moved to kidland', (await waitWhere('G1', 'kidland', 10000)) === 'kidland', await where('G1'));
  await sleep(1500);
  check('K1, now in the same world as G1, gets the join line', text('K1', /G1 joined the game/, k0), bots.K1.lines(k0).slice(-4).join(' | '));
  check('K1 has a tab entry for G1', !!listedTo('K1', 'G1'));
  check('A1, who can no longer reach G1, never gets a join line', !text('A1', /G1 joined the game/, a0));
  check('A1 has no tab entry for G1 after the move', !listedTo('A1', 'G1'));
  check('G1 is told the config\'s welcome line', text('G1', /HOMEKID Welcome back/));
  const gm = bots.G1.mark();
  bots.G1.say('afterchat hello');
  await sleep(1500);
  check('K1 hears G1 once the check has run', text('K1', /afterchat/, k0));
  check('A1 still does not', !text('A1', /afterchat/, a0));

  section('Failed delayed move (D297): a move that cannot be made disconnects the new kid');
  await s.run('mt blocktp H1', /HRT blocktp set/, 5000);
  const hm = s.mark(), ah = bots.A1.mark(), ar = bots.A1.rawMark();
  bots.H1 = new Bot('H1', +port); bots.H1.spawned.catch(() => {});
  const end = Date.now() + 10000;
  while (Date.now() < end && !bots.H1.ended) await sleep(200);
  check('H1 is disconnected', !!bots.H1.ended, bots.H1.ended);
  check('H1 is told to join again', /join again/.test(bots.H1.kicked || ''), bots.H1.kicked);
  check('the log says the move did not go through', /didn't go through, so the player was disconnected/.test(s.since(hm)));
  await sleep(1000);
  check('nobody was told H1 joined or left', !text('A1', /H1 (joined|left) the game/, ah) && !bots.A1.rawHeard(/H1/, ar) && !text('K1', /H1 (joined|left) the game/, k0));
  s.writeConfig('config.yml', CONFIG);
  await reload();

  section('Fails closed: a missing fallback world');
  s.writeConfig('config.yml', CONFIG.replace('kidland_nether]\n    fallback: kidland', 'kidland_nether, ghostland]\n    fallback: ghostland'));
  await reload();
  const sm = s.mark();
  const st = await s.run('posse status', /the world "ghostland" doesn't exist[^\n]*/, 5000);
  check('posse status says which world is missing', !!st, st && st[0]);
  const json = await s.run('posse status --json hw1', /ea-result[^\n]*hw1[^\n]*/, 5000);
  check('the --json status reports home-worlds on and the problem', json && /"homeWorlds":\{/.test(json[0]) && /"enabled":true/.test(json[0]) && /"circles":\["kids"\]/.test(json[0]) && /"noCircle":true/.test(json[0]) && /ghostland/.test(json[0]), json && json[0].slice(0, 300));
  await s.run('mt tp K1 grownups', /HRT tp K1 grownups true/, 8000);
  check('a kid sent out lands in the main world, never left where they were', (await waitWhere('K1', 'world')) === 'world', await where('K1'));
  check('the log warns about the missing fallback', /home-worlds fallback "ghostland" doesn't exist/.test(s.since(sm)));

  section('Off by default: with the section removed nothing moves');
  s.writeConfig('config.yml', 'circles: [kids, adults]\n');
  await reload();
  await s.run('mt tp K1 grownups', /HRT tp K1 grownups true/, 8000);
  await sleep(3000);
  check('a kid in the adults\' world stays there', (await where('K1')) === 'grownups', await where('K1'));
  await s.run('mt tp X1 grownups', /HRT tp X1 grownups true/, 8000);
  await sleep(3000);
  check('a player with no circle stays where they are', (await where('X1')) === 'grownups', await where('X1'));
  const off = await s.run('posse status', /Home worlds: ([^\n]*)/, 5000);
  check('status says off', off && off[1] === 'off.', off && off[1]);

  const errors = s.errors(m0).filter((l) => !/ghostland|home-worlds fallback|didn't go through/.test(l));
  check('no errors or exceptions in the log', errors.length === 0, errors.slice(0, 4).join(' || '));
  Object.values(bots).forEach((b) => b.quit());
  await s.stop();
  process.exit(summary() ? 1 : 0);
})().catch(async (e) => {
  console.error('CRASHED', e);
  try { Object.values(bots).forEach((b) => b.quit()); await s.stop(); } catch { }
  summary();
  process.exit(1);
});
