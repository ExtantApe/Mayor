// K832: every way the server would tell one player another's name follows reach. Death and advancement lines, the
// names Tab offers, the names a player's game is told about, /list, the server list sample, and a player disconnected
// while joining. See README.md in this folder. Usage: node reach-leaks.js <dir> <jar> <port>, relative to the work folder.
// Run it with POSSE_SUITE=reach-leaks.js test/live/run.sh <version>. All names are made up.
//
// Each part checks a pair that must be refused, a pair that is allowed, a player with posse.bypass, and Posse unable
// to decide (the rules paused by a config it can't read). A control with no circles shows the test can see a line
// when one is sent. On Posse 0.11.1 the refused checks fail: that is the bug this test was written for.
const mc = require('minecraft-protocol');
const { sleep, check, section, summary, Server, Bot } = require('./lib.js');
const [dir, jar, port] = process.argv.slice(2);

const CONFIG = `circles: [kids, adults]
circle-only-worlds: [station]
presence: reach
home-worlds:
  kids:
    worlds: [kidland]
    fallback: kidland
  no-circle:
    fallback: station
`;
// K = kids' circle, A = adults', V = both, M = an adult moderator with posse.bypass, X = no circle, waiting in the station.
const PEOPLE = {
  K1: { circles: ['kids'], world: 'kidland' },
  K2: { circles: ['kids'], world: 'kidland' },
  A1: { circles: ['adults'], world: 'grownups' },
  A2: { circles: ['adults'], world: 'grownups' },
  V1: { circles: ['kids', 'adults'], world: 'grownups' },
  M1: { circles: ['adults'], bypass: true, world: 'grownups' },
  X1: { circles: [], world: 'station' },
};
const NAMES = Object.keys(PEOPLE);
// Two returning kids who logged out in the adults' world before the rules were on. K3's move is blocked, K4's is not.
const LATE = ['K3', 'K4'];
let s; const bots = {};

async function join(n) { bots[n] = new Bot(n, +port); await bots[n].spawned; await sleep(1200); }
async function reload() { return s.run('posse reload', /\[Posse\] (Reloaded|The config has problems, so nothing changed|The config still has problems)[^\n]*/, 10000); }
async function where(n) { const m = await s.run(`mt where ${n}`, new RegExp(`HRT where ${n} (\\S+)`), 5000); return m ? m[1] : null; }
async function tp(n, w) { await s.run(`mt tp ${n} ${w}`, new RegExp(`HRT tp ${n} ${w} (true|false)`), 10000); await sleep(1500); }
const named = (name) => new RegExp(`\\b${name}\\b`);
const marks = () => Object.fromEntries(NAMES.filter((n) => bots[n]).map((n) => [n, [bots[n].mark(), bots[n].rawMark()]]));
/** Who was sent any chat line naming the player since the marks: by mineflayer's text and by the raw packets. */
function sentTo(name, m) {
  const got = [];
  for (const n of Object.keys(m)) if (bots[n].heard(named(name), m[n][0]) || bots[n].rawHeard(named(name), m[n][1])) got.push(n);
  return got.sort();
}
/** The first line naming the player that one of the receivers got, so a failure says which line leaked. */
function lineFor(name, m, got) {
  for (const n of got) {
    const t = bots[n].lines(m[n][0]).find((x) => named(name).test(x));
    if (t) return `${n} got: ${t.slice(0, 120)}`;
    const r = bots[n].raw.slice(m[n][1]).find((x) => named(name).test(x.m));
    if (r) return `${n} got ${r.n}: ${r.m.slice(0, 160)}`;
  }
  return '';
}
const same = (a, b) => a.length === b.length && a.every((x, i) => x === b[i]);
const without = (list, names) => names.every((n) => !list.includes(n));
const withAll = (list, names) => names.every((n) => list.includes(n));

/** The names Tab offers after the text, as the server answers. No answer at all counts as nothing offered. */
async function offered(n, text) {
  let got = null;
  try { got = await bots[n].bot.tabComplete(text, false, false, 2500); } catch { got = []; }
  return (got || []).map((x) => (typeof x === 'string' ? x : x.match || '')).filter((x) => NAMES.includes(x) || LATE.includes(x));
}
/** Every player this bot's game has been told about, listed or not. A real game offers all of these on Tab by itself. */
const knownTo = (n) => Object.keys(bots[n].bot.players).filter((x) => x !== n).sort();
const listedTo = (viewer, other) => { const p = bots[viewer].bot.players[other]; return !!p && (p.listed === undefined || Boolean(p.listed)); };
/** Sends a command and returns the lines the player got back. */
async function ask(n, command, ms = 1800) { const m = bots[n].mark(); bots[n].say(command); await sleep(ms); return bots[n].lines(m).join(' | '); }
async function sample() {
  try {
    const r = await mc.ping({ host: '127.0.0.1', port: +port, version: '1.21.11' });
    return { online: r.players && r.players.online, names: ((r.players && r.players.sample) || []).map((p) => p.name) };
  } catch (e) { return { online: null, names: ['PING FAILED ' + e.message] }; }
}
async function die(n) {
  const m = marks(), c = s.mark();
  let screen = null;
  const onScreen = (d) => { screen = JSON.stringify(d.message === undefined ? d : d.message); };
  bots[n].bot._client.on('death_combat_event', onScreen);
  await s.run(`mt grave ${n} 0`, new RegExp(`HRT planned death ${n}`), 8000);
  await sleep(3000);
  bots[n].bot._client.removeListener('death_combat_event', onScreen);
  return { got: sentTo(n, m), console: new RegExp(`\\b${n} died`).test(s.since(c)), screen };
}
async function advance(n) {
  await s.run(`advancement revoke ${n} only minecraft:story/mine_stone`, null);
  await sleep(500);
  const m = marks(), c = s.mark();
  await s.run(`advancement grant ${n} only minecraft:story/mine_stone`, null);
  await sleep(2500);
  return { got: sentTo(n, m), console: new RegExp(`\\b${n} has made the advancement`).test(s.since(c)) };
}
const TAB = ['/msg ', '/tell ', '/w ', '/minecraft:msg ', '/minecraft:tell ', '/minecraft:w ', '/mtwho '];

(async () => {
  s = new Server(dir, jar);
  await s.start();

  section('Setup: made-up worlds and players, before any circles');
  for (const w of ['kidland', 'grownups', 'station']) await s.run(`mt world ${w}`, new RegExp(`HRT world (${w}|FAILED)`), 60000);
  // Paper keeps the server's own /list for ops unless the permission is given. Give it to everyone, as a server might.
  await s.run('lp group default permission set minecraft.command.list true', null);
  for (const n of [...NAMES, ...LATE]) await join(n);
  for (const [n, p] of Object.entries(PEOPLE)) {
    for (const c of p.circles) await s.run(`lp user ${n} permission set posse.circle.${c} true`, null);
    if (p.bypass) await s.run(`lp user ${n} permission set posse.bypass true`, null);
  }
  for (const n of LATE) await s.run(`lp user ${n} permission set posse.circle.kids true`, null);
  await sleep(1000);
  for (const [n, p] of Object.entries(PEOPLE)) await tp(n, p.world);
  for (const n of LATE) { await tp(n, 'grownups'); bots[n].quit(); delete bots[n]; }
  await sleep(2500);

  section('Control: with no circles nothing is filtered, and the test sees every line');
  let d = await die('K1');
  check('control: a death line reaches everyone', same(d.got, NAMES.slice().sort()), d.got.join(' '));
  let a = await advance('K1');
  check('control: an advancement line reaches everyone', same(a.got, NAMES.slice().sort()), a.got.join(' '));
  let o = await offered('A1', '/msg ');
  check('control: Tab after /msg offers every player', withAll(o, NAMES), o.join(' '));
  o = await offered('A1', '/mtwho ');
  check('control: Tab after a plugin command offers every player', withAll(o, NAMES), o.join(' '));
  let l = await ask('K1', '/list');
  check('control: /list names everyone', NAMES.every((n) => named(n).test(l)), l.slice(0, 200));
  let p = await sample();
  check('control: the server list sample names players', p.names.some((n) => NAMES.includes(n)) && p.online === NAMES.length, `${p.online} online, ${p.names.length} in the sample`);
  await tp('K1', 'kidland');

  section('Circles on, presence: reach');
  s.writeConfig('config.yml', CONFIG);
  check('reload applies the rules', /Reloaded/.test((await reload() || [''])[0]));
  await sleep(3000);
  const m0 = s.mark();

  section('Death lines follow reach');
  d = await die('K1');
  check('refused: a kid\'s death line reaches no adult outside their circle, and nobody in no circle', without(d.got, ['A1', 'A2', 'X1']), d.got.join(' '));
  check('allowed: it reaches the kid, their circle-mate and the visitor in both circles', withAll(d.got, ['K1', 'K2', 'V1']), d.got.join(' '));
  check('bypass: it reaches a player with posse.bypass', d.got.includes('M1'), d.got.join(' '));
  check('the console and log keep the kid\'s death line', d.console);
  check('the kid\'s own death screen still says how they died', !!d.screen && /death\.|died|\bK1\b/.test(d.screen), (d.screen || 'no death screen packet').slice(0, 120));
  check('the kid respawns in the kids\' world', (await where('K1')) === 'kidland', await where('K1'));
  d = await die('A1');
  check('refused: an adult\'s death line reaches no kid, and nobody in no circle', without(d.got, ['K1', 'K2', 'X1']), d.got.join(' '));
  check('allowed: it reaches the adult, their circle-mate and the visitor', withAll(d.got, ['A1', 'A2', 'V1']), d.got.join(' '));
  check('the console and log keep the adult\'s death line', d.console);
  await tp('A1', 'grownups');

  section('Advancement lines follow reach');
  a = await advance('K2');
  check('refused: a kid\'s advancement line reaches no adult outside their circle', without(a.got, ['A1', 'A2', 'X1']), a.got.join(' '));
  check('allowed and bypass: it reaches the kid, their circle-mate, the visitor and bypass', withAll(a.got, ['K1', 'K2', 'V1', 'M1']), a.got.join(' '));
  check('the console and log keep the advancement line', a.console);
  a = await advance('A2');
  check('refused: an adult\'s advancement line reaches no kid', without(a.got, ['K1', 'K2', 'X1']), a.got.join(' '));
  check('allowed: it reaches the adult, their circle-mate and the visitor', withAll(a.got, ['A1', 'A2', 'V1']), a.got.join(' '));

  section('Tab offers only names in reach, for every command');
  const bad = [], thin = [];
  for (const t of TAB) {
    const forAdult = await offered('A1', t), forKid = await offered('K1', t), forMod = await offered('M1', t);
    if (!without(forAdult, ['K1', 'K2', 'X1'])) bad.push(`${t}to A1: ${forAdult.join(' ')}`);
    if (!without(forKid, ['A1', 'A2', 'X1'])) bad.push(`${t}to K1: ${forKid.join(' ')}`);
    if (!withAll(forAdult, ['A2', 'V1', 'M1'])) thin.push(`${t}to A1: ${forAdult.join(' ')}`);
    if (!withAll(forKid, ['K2', 'V1', 'M1'])) thin.push(`${t}to K1: ${forKid.join(' ')}`);
    if (!withAll(forMod, ['K1', 'K2', 'A1', 'A2', 'V1', 'X1'])) thin.push(`${t}to M1: ${forMod.join(' ')}`);
  }
  check(`refused: Tab offers an adult no kid, and a kid no adult (${TAB.length} commands, namespaced and a plugin's)`, bad.length === 0, bad.slice(0, 6).join('; '));
  check('allowed and bypass: the circle-mate, the visitor and bypass are still offered, and bypass is offered everyone', thin.length === 0, thin.slice(0, 6).join('; '));

  section('A player\'s game is told nothing about players out of reach');
  // A real game offers every player it has been told about when Tab is pressed, in chat or after a command, without
  // asking the server. A bot has no such menu, so this reads what the bot's game was told.
  check('refused: an adult\'s game knows no kid and nobody in no circle', without(knownTo('A1'), ['K1', 'K2', 'X1']), knownTo('A1').join(' '));
  check('refused: a kid\'s game knows no adult outside their circle', without(knownTo('K1'), ['A1', 'A2', 'X1']), knownTo('K1').join(' '));
  check('allowed: each still knows and lists their circle-mate, the visitor and bypass',
    ['A2', 'V1', 'M1'].every((n) => listedTo('A1', n)) && ['K2', 'V1', 'M1'].every((n) => listedTo('K1', n)), `A1: ${knownTo('A1').join(' ')}; K1: ${knownTo('K1').join(' ')}`);
  check('bypass: a player with posse.bypass knows everyone', withAll(knownTo('M1'), NAMES.filter((n) => n !== 'M1')), knownTo('M1').join(' '));
  await tp('A2', 'station');
  await sleep(2500);
  check('in the same circle-only world, a player with no shared circle can be seen but is not listed', knownTo('A2').includes('X1') && !listedTo('A2', 'X1'), knownTo('A2').join(' '));
  o = await offered('A2', '/msg ');
  check('and Tab still does not offer them', !o.includes('X1'), o.join(' '));
  await tp('A2', 'grownups');
  await sleep(2500);
  check('back in another world, the game is told to forget them', !knownTo('A2').includes('X1') && listedTo('A2', 'A1'), knownTo('A2').join(' '));
  await s.run('lp user K2 permission set posse.circle.adults true', null);
  await sleep(4000);
  check('a circle granted shows the player within seconds, no relog', listedTo('A1', 'K2') && listedTo('K2', 'A1'));
  await s.run('lp user K2 permission unset posse.circle.adults', null);
  await sleep(4000);
  check('and taking it away removes them again', !knownTo('A1').includes('K2') && !knownTo('K2').includes('A1'), knownTo('A1').join(' '));
  bots.A2.quit();
  await sleep(2000);
  await join('A2');
  await sleep(1500);
  check('refused: an adult who joins is told of no kid, and no kid is told of them', without(knownTo('A2'), ['K1', 'K2', 'X1']) && !knownTo('K1').includes('A2') && !knownTo('K2').includes('A2'), `A2: ${knownTo('A2').join(' ')}; K1: ${knownTo('K1').join(' ')}`);
  check('allowed: they and their circle-mate know and list each other', listedTo('A2', 'A1') && listedTo('A1', 'A2'));

  section('/list follows reach');
  for (const c of ['/list', '/minecraft:list', '/list uuids']) {
    l = await ask('K1', c);
    check(`refused: ${c} names no adult to a kid`, !/\b(A1|A2|X1)\b/.test(l), l.slice(0, 200));
    check(`allowed: ${c} tells the kid who can hear them`, /can hear you/.test(l) && /\bK2\b/.test(l), l.slice(0, 200));
  }
  l = await ask('A1', '/list');
  check('refused: /list names no kid to an adult', !/\b(K1|K2|X1)\b/.test(l) && /\bA2\b/.test(l), l.slice(0, 200));
  l = await ask('M1', '/list');
  check('bypass: /list still names everyone to a player with posse.bypass', NAMES.every((n) => named(n).test(l)), l.slice(0, 200));

  section('The server list shows a count and no names');
  p = await sample();
  check('the sample is empty and the count is kept', p.names.length === 0 && p.online === NAMES.length, `${p.online} online, sample: ${p.names.join(' ') || 'empty'}`);

  section('A returning player who must be moved is announced only once they are where they may be');
  let m = marks();
  await join('K4');
  await sleep(2500);
  check('a returning kid who logged out in the adults\' world is moved to the kids\' world', (await where('K4')) === 'kidland', await where('K4'));
  let got = sentTo('K4', m);
  check('refused: no adult outside their circle is told they joined', without(got, ['A1', 'A2', 'X1']), `${got.join(' ')} ${lineFor('K4', m, got.filter((n) => ['A1', 'A2', 'X1'].includes(n)))}`);
  check('allowed and bypass: their circle, the visitor and bypass are told, after the move', withAll(got, ['K1', 'K2', 'V1', 'M1']), got.join(' '));
  bots.K4.quit(); delete bots.K4;
  await sleep(2000);

  section('A player disconnected while joining is never announced');
  await s.run('mt blocktp K3', /HRT blocktp set/, 5000);
  m = marks();
  const km = s.mark();
  bots.K3 = new Bot('K3', +port); bots.K3.spawned.catch(() => {});
  const end = Date.now() + 10000;
  while (Date.now() < end && !bots.K3.ended) await sleep(200);
  check('a kid whose move out of the adults\' world fails is disconnected with a kind line', !!bots.K3.ended && /join again/.test(bots.K3.kicked || ''), bots.K3.kicked || bots.K3.ended);
  await sleep(1500);
  got = sentTo('K3', m);
  check('refused: nobody at all was sent a join, leave or any other line naming them', got.length === 0, `${got.join(' ')} ${lineFor('K3', m, got)}`);
  check('no game was told about them', NAMES.every((n) => !knownTo(n).includes('K3')), NAMES.filter((n) => knownTo(n).includes('K3')).join(' '));
  check('the console and log say what happened', /didn't go through, so the player was disconnected/.test(s.since(km)));
  delete bots.K3;

  let errors = s.errors(m0).filter((e) => !/home-worlds move on join didn't go through/.test(e));
  check('no errors in the log', errors.length === 0, errors.slice(0, 4).join(' || '));

  section('Posse unable to decide: a config it can\'t read at startup pauses the rules');
  NAMES.forEach((n) => bots[n].quit());
  await sleep(1500);
  await s.stop();
  s.writeConfig('config.yml', 'circles: [kids\nnope: : :');
  await s.start();
  check('the console explains the pause', /chat and private messages are paused until it's fixed/.test(s.log));
  // The made-up worlds are loaded by the helper, so load them again before anyone rejoins where they left.
  for (const w of ['kidland', 'grownups', 'station']) await s.run(`mt world ${w}`, new RegExp(`HRT world (${w}|FAILED)`), 60000);
  for (const n of NAMES) await join(n);
  check('everyone is back in their own world', (await where('K2')) === 'kidland' && (await where('A2')) === 'grownups', `${await where('K2')} ${await where('A2')}`);
  await sleep(3000);
  const forAdult = [], forMod = [];
  for (const t of TAB) { forAdult.push(...await offered('A1', t)); forMod.push(...await offered('M1', t)); }
  check('paused: Tab offers an adult nobody but themself, whatever the command', forAdult.every((n) => n === 'A1'), [...new Set(forAdult)].join(' '));
  check('paused, bypass: a player with posse.bypass is still offered everyone', withAll(forMod, NAMES), [...new Set(forMod)].join(' '));
  check('paused: an adult\'s game knows no kid, and a kid\'s no adult', without(knownTo('A2'), ['K1', 'K2', 'X1']) && without(knownTo('K2'), ['A1', 'A2', 'V1', 'M1', 'X1']), `A2: ${knownTo('A2').join(' ')}; K2: ${knownTo('K2').join(' ')}`);
  check('paused: nobody is listed to anyone else', NAMES.every((v) => NAMES.every((n) => v === n || !listedTo(v, n))));
  l = await ask('K1', '/list');
  check('paused: /list names nobody', !NAMES.some((n) => n !== 'K1' && named(n).test(l)) && /paused/.test(l), l.slice(0, 200));
  p = await sample();
  check('paused: the server list sample is empty', p.names.length === 0, p.names.join(' '));
  a = await advance('K2');
  check('paused: an advancement line reaches only the player and bypass', same(a.got, ['K2', 'M1']), a.got.join(' '));
  d = await die('K1');
  check('paused: a death line reaches only the dead player and bypass, not even a circle-mate', same(d.got, ['K1', 'M1']), d.got.join(' '));
  check('paused: the console and log still keep the death line', d.console);
  errors = s.errors(0).filter((e) => !/\[Posse\]/.test(e));
  check('no errors in the log beyond the config problem', errors.length === 0, errors.slice(0, 4).join(' || '));

  NAMES.forEach((n) => bots[n].quit());
  await s.stop();
  process.exit(summary() ? 1 : 0);
})().catch(async (e) => {
  console.error('CRASHED', e);
  try { Object.values(bots).forEach((b) => b.quit()); await s.stop(); } catch { }
  summary();
  process.exit(1);
});
