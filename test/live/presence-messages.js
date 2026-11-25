// Join and leave lines under presence: reach (D283), on one throwaway server. See README.md in this folder.
// Usage: node presence-messages.js <dir> <jar> <port>, relative to the work folder.
// Each check reads chat two ways: mineflayer's text events and the raw chat packets, and both must agree.
const { sleep, check, section, summary, Server, Bot } = require('./lib.js');
const [dir, jar, port] = process.argv.slice(2);

// Made-up players. The default world is circle-only, so a player with no circle can't be reached there.
const PEOPLE = { K1: ['kids'], K2: ['kids'], A1: ['adults'], A2: ['adults'], V1: ['kids', 'adults'], X1: [] };
const ORDER = ['K1', 'A1', 'K2', 'A2', 'V1', 'X1'];
const CONFIG = 'circles: [kids, adults]\ncircle-only-worlds: [world]\n';
const reaches = (a, b) => a === b || PEOPLE[a].some((c) => PEOPLE[b].includes(c));

let s; const bots = {};
// A raw packet is a line about this player if it names them and carries the vanilla join or leave key or text.
const lineRe = (name, verb) => ({ test: (m) => m.includes(name) && (m.includes(`multiplayer.player.${verb === 'joined' ? 'joined' : 'left'}`) || m.includes(`${verb} the game`)) });
async function heardBy(viewer, name, verb, mark, rawMark) {
  const text = bots[viewer].heard(`${name} ${verb} the game`, mark);
  const raw = bots[viewer].rawHeard(lineRe(name, verb), rawMark);
  return { text, raw };
}
const marks = () => Object.fromEntries(Object.entries(bots).map(([n, b]) => [n, [b.mark(), b.rawMark()]]));

async function round(label, wantFor) {
  // Players join one at a time in ORDER; whoever is online sees each join, as the rules allow.
  const bad = [], seen = {};
  const online = [];
  for (const n of ORDER) {
    const m = marks();
    bots[n] = new Bot(n, +port); await bots[n].spawned; await sleep(1500);
    for (const v of online) {
      const h = await heardBy(v, n, 'joined', ...m[v]);
      seen[`${v}<-${n}`] = h;
      const want = wantFor(v, n);
      if (h.text !== want || h.raw !== want) bad.push(`join ${n} to ${v}: want ${want} text ${h.text} raw ${h.raw}`);
    }
    online.push(n);
  }
  // Then they leave in the same order.
  for (const n of ORDER) {
    const m = marks();
    bots[n].quit(); await sleep(1500);
    online.splice(online.indexOf(n), 1);
    for (const v of online) {
      const h = await heardBy(v, n, 'left', ...m[v]);
      const want = wantFor(v, n);
      if (h.text !== want || h.raw !== want) bad.push(`leave ${n} to ${v}: want ${want} text ${h.text} raw ${h.raw}`);
    }
  }
  const total = Object.values(seen).filter((h) => h.text).length;
  return { bad, total };
}

(async () => {
  s = new Server(dir, jar);
  await s.start();
  section('Setup: circles for made-up players');
  s.writeConfig('config.yml', CONFIG);
  await s.run('mayor reload', /\[Mayor\] (Reloaded|The config)/, 10000);
  // LuckPerms only knows a player after the first join, so everyone joins once, is given circles, and leaves.
  for (const n of ORDER) { bots[n] = new Bot(n, +port); await bots[n].spawned; await sleep(300); }
  for (const [n, cs] of Object.entries(PEOPLE)) for (const c of cs) { await s.run(`lp user ${n} permission set mayor.circle.${c} true`, null); }
  await sleep(1000);
  for (const n of ORDER) bots[n].quit();
  await sleep(2500);

  section('Control: presence: everyone, every join and leave is heard');
  let r = await round('everyone', () => true);
  check('every viewer hears every join and leave (text and raw packets)', r.bad.length === 0 && r.total > 0, r.bad.slice(0, 8).join('; ') || `${r.total} joins seen`);
  check('the console logged the joins', /K1 joined the game/.test(s.log) && /V1 joined the game/.test(s.log));

  section('presence: reach');
  s.writeConfig('config.yml', CONFIG + 'presence: reach\n');
  check('reload applies presence: reach', /Reloaded/.test(((await s.run('mayor reload', /\[Mayor\] (Reloaded|The config)[^\n]*/, 10000)) || [''])[0]));
  const m0 = s.mark();
  r = await round('reach', (v, n) => reaches(v, n));
  check('same circle and the visitor hear it; other circle and no circle do not (text and raw packets)', r.bad.length === 0 && r.total > 0, r.bad.slice(0, 8).join('; ') || `${r.total} joins seen`);
  const log = s.since(m0);
  check('the console logged every join and leave', ORDER.every((n) => new RegExp(`${n} joined the game`).test(log) && new RegExp(`${n} left the game`).test(log)));
  const errors = s.errors(m0);
  check('no errors in the log', errors.length === 0, errors.slice(0, 4).join(' || '));

  await s.stop();
  process.exit(summary() ? 1 : 0);
})().catch(async (e) => {
  console.error('CRASHED', e);
  try { Object.values(bots).forEach((b) => b.quit()); await s.stop(); } catch { }
  summary();
  process.exit(1);
});
