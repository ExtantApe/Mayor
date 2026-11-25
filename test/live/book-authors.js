// Book authors follow circles (D291) on one throwaway server. See README.md in this folder.
// Usage: node book-authors.js <dir> <jar> <port>, relative to the work folder.
// Run it with MAYOR_SUITE=book-authors.js MAYOR_PORT=<port> test/live/run.sh <version>.
// All names are made up. The bots only stand in for players: what each one "sees" is read from the server's own copy
// of the book, which is exactly what the client is sent. Item frames and lecterns aren't driven here (see the README).
const { sleep, check, section, summary, Server, Bot } = require('./lib.js');
const [dir, jar, port] = process.argv.slice(2);

const CONFIG = `circles: [kids, adults]\n`;
// K = kids' circle, A = adults', V = both, X = neither.
const PEOPLE = { K1: ['kids'], K2: ['kids'], A1: ['adults'], V1: ['kids', 'adults'], X1: [] };
let s; const bots = {};

async function books(n) {
  const m = await s.run(`mt books ${n}`, new RegExp(`HRT books ${n} ([^\\n]*)`), 5000);
  return m ? m[1].trim() : null;
}
async function give(n, author, title) { await s.run(`mt book ${n} ${author} ${title}`, new RegExp(`HRT book ${n}`), 5000); }
async function reload() { return s.run('mayor reload', /\[Mayor\] (Reloaded|The config has problems, so nothing changed|The config still has problems)[^\n]*/, 10000); }
/** Waits for the sweep (every 2 seconds), then reads what the player is carrying. */
async function carried(n, want, ms = 6000) {
  const end = Date.now() + ms; let got = null;
  while (Date.now() < end) { got = await books(n); if (got === want) return got; await sleep(500); }
  return got;
}

(async () => {
  s = new Server(dir, jar);
  await s.start();
  const m0 = s.mark();

  section('Setup: five made-up players and two circles');
  for (const n of Object.keys(PEOPLE)) { bots[n] = new Bot(n, +port); await bots[n].spawned; await sleep(1200); }
  for (const [n, circles] of Object.entries(PEOPLE)) for (const c of circles) await s.run(`lp user ${n} permission set mayor.circle.${c} true`, null);
  s.writeConfig('config.yml', CONFIG);
  await reload();
  await sleep(1500);

  section('A kid writes a book and drops it: an adult who picks it up sees no author, a kid friend sees it');
  await give('K1', 'K1', 'Tale');
  check('the author keeps seeing their own name', (await carried('K1', 'K1/Tale')) === 'K1/Tale', await books('K1'));
  await s.run('mt drop K1', /HRT drop K1/, 5000);
  await s.run('mt near A1 drop', /HRT near A1 true/, 8000);
  check('an adult who picks it up sees Unknown', (await carried('A1', 'Unknown/Tale')) === 'Unknown/Tale', await books('A1'));
  await give('K1', 'K1', 'Tale2');
  await sleep(2500);
  await s.run('mt drop K1', /HRT drop K1/, 5000);
  await s.run('mt near K2 drop', /HRT near K2 true/, 8000);
  check('a kid in the same circle who picks it up sees the author', (await carried('K2', 'K1/Tale2')) === 'K1/Tale2', await books('K2'));
  await give('K1', 'K1', 'Tale3');
  await sleep(2500);
  await s.run('mt drop K1', /HRT drop K1/, 5000);
  await s.run('mt near V1 drop', /HRT near V1 true/, 8000);
  check('the visitor in both circles sees the author', (await carried('V1', 'K1/Tale3')) === 'K1/Tale3', await books('V1'));
  await give('K1', 'K1', 'Tale4');
  await sleep(2500);
  await s.run('mt drop K1', /HRT drop K1/, 5000);
  await s.run('mt near X1 drop', /HRT near X1 true/, 8000);
  check('a player in no circle sees Unknown', (await carried('X1', 'Unknown/Tale4')) === 'Unknown/Tale4', await books('X1'));

  section('A quest journal made by a plugin, with the player\'s name as author and in the title');
  await give('K2', 'K1', 'K1_Journal');
  check('a same-circle kid holding it sees the author and the title', (await carried('K2', 'K1/K1_Journal')) === 'K1/K1_Journal', await books('K2'));
  await give('A1', 'K1', 'K1_Journal');
  check('an adult holding it sees Unknown and no name in the title', (await carried('A1', 'Unknown/****_Journal')) === 'Unknown/****_Journal', await books('A1'));
  await give('V1', 'K1', 'K1_Journal');
  check('the visitor in both circles sees all of it', (await carried('V1', 'K1/K1_Journal')) === 'K1/K1_Journal', await books('V1'));
  await give('K1', 'K1', 'K1_Journal');
  check('its own player sees all of it', (await carried('K1', 'K1/K1_Journal')) === 'K1/K1_Journal', await books('K1'));

  section('A chest: the same book shows what the person looking may see');
  await give('A1', 'K1', 'Chesty');
  await sleep(2500);
  const adultChest = await s.run('mt chest A1', /HRT chest A1 ([^\n]*)/, 6000);
  check('an adult opening a chest sees Unknown', adultChest && adultChest[1].trim() === 'Unknown/Chesty', adultChest && adultChest[1]);
  await give('K2', 'K1', 'Chesty2');
  await sleep(2500);
  const kidChest = await s.run('mt chest K2', /HRT chest K2 ([^\n]*)/, 6000);
  check('a kid in the same circle opening a chest sees the author', kidChest && kidChest[1].trim() === 'K1/Chesty2', kidChest && kidChest[1]);

  section('Nothing is lost: the true author comes back when a circle is granted, and goes again when it is taken away');
  await give('A1', 'K1', 'Lasting');
  check('before: an adult sees Unknown', (await carried('A1', 'Unknown/Lasting')) === 'Unknown/Lasting', await books('A1'));
  await s.run('lp user A1 permission set mayor.circle.kids true', null);
  check('granted the kids circle, the same book shows its true author', (await carried('A1', 'K1/Lasting')) === 'K1/Lasting', await books('A1'));
  await s.run('lp user A1 permission unset mayor.circle.kids', null);
  check('with the circle taken away it is hidden again', (await carried('A1', 'Unknown/Lasting')) === 'Unknown/Lasting', await books('A1'));

  section('Settings: hide off shows everything, and a custom word is used');
  s.writeConfig('config.yml', CONFIG + 'book-authors:\n  shown-as: Somebody\n');
  await reload();
  await give('A1', 'K1', 'Word');
  check('shown-as is used', (await carried('A1', 'Somebody/Word')) === 'Somebody/Word', await books('A1'));
  s.writeConfig('config.yml', CONFIG + 'book-authors:\n  hide: false\n');
  await reload();
  check('with hide: false the adult sees the author again', (await carried('A1', 'K1/Word')) === 'K1/Word', await books('A1'));
  s.writeConfig('config.yml', 'circles: []\n');
  await reload();
  await give('A1', 'K1', 'NoCircles');
  check('with no circles nothing is hidden', (await carried('A1', 'K1/NoCircles')) === 'K1/NoCircles', await books('A1'));

  const errors = s.errors(m0);
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
