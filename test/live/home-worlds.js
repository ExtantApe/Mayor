// Home worlds (D290) on one throwaway server: a rejoin and a respawn, plus the world-change safety net and a missing
// fallback. See README.md in this folder. Usage: node home-worlds.js <dir> <jar> <port>, relative to the work folder.
// All names are made up. Run it with POSSE_SUITE=home-worlds.js test/live/run.sh <version>.
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
const text = (n, re, from = 0) => bots[n].heard(re, from);

(async () => {
  s = new Server(dir, jar);
  await s.start();

  section('Setup: made-up worlds, circles, and where everyone last was');
  for (const w of ['kidland', 'kidland_nether', 'grownups', 'station']) await s.run(`mt world ${w}`, new RegExp(`HRT world (${w}|FAILED)`), 60000);
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

  const errors = s.errors(m0).filter((l) => !/ghostland|home-worlds fallback/.test(l));
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
