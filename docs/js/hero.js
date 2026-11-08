// Hero: replays a real smart cube solve on a cubing.js cube, at the pace it was turned.
import solve from './hero-solve.js';

const CUBING = '../vendor/twisty.js'; // built by tools/website (npm run build)
const TURN_MS = 110; // how long one turn takes on screen; real turns are about this fast
const HOLD_MS = 3500; // pause on the solved cube before replaying

const $ = id => document.getElementById(id);
const stage = $('stage'), cubeBox = $('cube'), clock = $('clock'), stepText = $('steptext');
const still = matchMedia('(prefers-reduced-motion: reduce)').matches;

// Split bar and labels from the solve's top-level steps.
const total = solve.time;
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
  clock.textContent = (Math.min(t, total) / 1000).toFixed(2);
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

  let i = 0, last = -1;
  function frame(now) {
    requestAnimationFrame(frame);
    if (!visible || document.hidden) return;
    if (t0 === null) { t0 = now - (last > 0 ? last : 0); }
    let t = now - t0;
    if (t > total + HOLD_MS) { t0 = now; t = 0; i = 0; }
    last = t;
    while (i < n - 1 && at[i + 1] <= t) i++;
    if (i > 0 && at[i] > t) i = 0;
    let ts;
    if (t < at[0]) ts = 0;
    else {
      const p = Math.min(1, (t - at[i]) / Math.min(TURN_MS, i < n - 1 ? at[i + 1] - at[i] : TURN_MS));
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
