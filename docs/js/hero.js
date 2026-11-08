// Hero: replays a smart cube solve on a cubing.js cube, each turn at its recorded time.
import solve from './hero-solve.js';

const CUBING = '../vendor/twisty.js'; // built by tools/website (npm run build)
const TURN_MS = 110; // on-screen length of a turn; a smart cube reports a turn as it completes, so it ends at its time
const LEAD_MS = 700; // the scrambled cube shown at 0.00 before the first turn
const HOLD_MS = 3500; // pause on the solved cube before replaying

const $ = id => document.getElementById(id);
const stage = $('stage'), cubeBox = $('cube'), clock = $('clock'), stepText = $('steptext');
const still = matchMedia('(prefers-reduced-motion: reduce)').matches;

// Split bar and labels from the solve's top-level steps. The clock stops on the last turn, the step total.
const total = solve.moves[solve.moves.length - 1][1];
const bar = $('bar'), labels = $('labels');
const segs = solve.steps.map(s => {
  const i = document.createElement('i');
  i.style.flex = String(s.to - s.from);
  i.style.setProperty('--c', `var(--${s.colour})`);
  bar.appendChild(i);
  const l = document.createElement('span');
  l.innerHTML = `${s.name}<b>${(Math.round((s.to - s.from) / 10) / 100).toFixed(2)}</b>`;
  labels.appendChild(l);
  return i;
});

function showReadout(t) {
  clock.textContent = (Math.max(0, Math.min(t, total)) / 1000).toFixed(2);
  let sub = null;
  solve.steps.forEach((s, k) => segs[k].classList.toggle('on', t >= s.from));
  for (const s of solve.parts) if (t >= s.from) sub = s;
  stepText.textContent = t <= 0 ? ' ' : t >= total ? 'Solved' : sub ? sub.name : ' ';
}

function finalState() { showReadout(total); }

async function start() {
  let mod;
  try { mod = await import(CUBING); } catch (e) { finalState(); return; }
  const tp = new mod.TwistyPlayer({
    puzzle: '3x3x3', background: 'none', controlPanel: 'none', hintFacelets: 'none',
    experimentalSetupAlg: solve.scramble, alg: solve.moves.map(m => m[0]).join(' '),
  });
  cubeBox.appendChild(tp);
  const model = tp.experimentalModel;
  const indexer = await model.indexer.get();
  const n = solve.moves.length;
  const at = solve.moves.map(m => m[1]);
  const startTs = i => indexer.indexToMoveStartTimestamp(i);
  const dur = i => indexer.moveDuration(i);
  const endTs = startTs(n - 1) + dur(n - 1);

  if (still) { model.timestampRequest.set(endTs); finalState(); return; }

  let t0 = null, visible = true;
  new IntersectionObserver(es => { visible = es[0].isIntersecting; }).observe(stage);
  document.addEventListener('visibilitychange', () => { if (!document.hidden) t0 = null; });

  let i = 0, last = -LEAD_MS;
  function frame(now) {
    requestAnimationFrame(frame);
    if (!visible || document.hidden) { t0 = null; return; }
    if (t0 === null) t0 = now - (last + LEAD_MS);
    let t = now - t0 - LEAD_MS;
    if (t > total + HOLD_MS) { t0 = now; t = -LEAD_MS; i = 0; }
    last = t;
    // Turn k plays over [at[k] - d, at[k]]; i is the first turn not yet finished.
    while (i < n && at[i] <= t) i++;
    let ts;
    if (i >= n) ts = endTs;
    else {
      const d = i > 0 ? Math.min(TURN_MS, at[i] - at[i - 1]) : TURN_MS;
      const p = Math.max(0, 1 - (at[i] - t) / d);
      ts = startTs(i) + p * dur(i);
    }
    model.timestampRequest.set(Math.min(ts, endTs));
    showReadout(t);
  }
  showReadout(0);
  requestAnimationFrame(frame);
}

// Load cubing.js only once the stage is on screen, so it never slows the first paint.
new IntersectionObserver((es, io) => {
  if (es[0].isIntersecting) { io.disconnect(); start(); }
}, { rootMargin: '200px' }).observe(stage);
