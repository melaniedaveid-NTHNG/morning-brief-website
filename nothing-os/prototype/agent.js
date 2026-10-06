// Port of app/src/main/java/tech/nothing/agentos/agent/AgentModel.kt — keep the two in sync.
'use strict';

const TAU = Math.PI * 2;

// mulberry32 — the same generator as AgentModel.Mulberry32, so the prototype and the phone grow
// the identical agent from the same seed.
function rng(seed) {
  let a = seed >>> 0;
  const next = () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
  return { f: next, b: () => next() < 0.5 };
}

class AgentModel {
  constructor(seed = 7) {
    this.cells = []; this.links = []; this.bubbles = []; this.pills = [];
    this.build(rng(seed));
  }

  build(rnd) {
    const index = new Map();
    const key = (x, y) => x + ',' + y;
    const add = (gx, gy, c) => { index.set(key(gx, gy), this.cells.length); this.cells.push(c); };

    for (let gy = -5; gy <= 5; gy++) for (let gx = 0; gx <= 3; gx++) {
      const e = (gx / 2.7) ** 2 + (gy / 4.7) ** 2;
      if (e > 1) continue;
      if (rnd.f() > (e < 0.4 ? 0.9 : 0.68)) continue;
      const ring = rnd.f() < (gx === 0 ? 0.4 : 0.14);
      const r = ring ? 0.48 : 0.33 + rnd.f() * 0.12;
      const phase = rnd.f() * TAU;
      const pip = !ring && rnd.f() < 0.18;
      const kind = ring ? 'ring' : 'fill';
      add(gx, gy, { x: gx, y: gy, r, kind, phase, pip });
      if (gx > 0) add(-gx, gy, { x: -gx, y: gy, r, kind, phase: phase + 0.7, pip });
    }

    const linkMirrored = (ax, ay, bx, by, width) => {
      const a = index.get(key(ax, ay)), b = index.get(key(bx, by));
      if (a === undefined || b === undefined) return;
      this.links.push({ a, b, width });
      const ma = index.get(key(-ax, ay)), mb = index.get(key(-bx, by));
      if (ma === undefined || mb === undefined) return;
      const same = (ma === a && mb === b) || (ma === b && mb === a);
      if (!same) this.links.push({ a: ma, b: mb, width });
    };
    for (let gy = -5; gy <= 5; gy++) for (let gx = 0; gx <= 3; gx++) {
      if (!index.has(key(gx, gy))) continue;
      if (rnd.f() < 0.58) linkMirrored(gx, gy, gx + 1, gy, 0.28);
      if (rnd.f() < 0.58) linkMirrored(gx, gy, gx, gy + 1, 0.28);
    }

    const rightHalf = [...index.keys()].map(k => k.split(',').map(Number))
      .filter(([gx]) => gx >= 0).sort((p, q) => p[1] - q[1] || p[0] - q[0]);
    for (const [gx, gy] of rightHalf) {
      if (rnd.f() > 0.3) continue;
      const dx = gx === 0 ? 1 : Math.sign(gx);
      const dy = rnd.b() ? 1 : -1;
      if (index.has(key(gx + dx, gy + dy)) || index.has(key(gx + dx, gy))) continue;
      const parent = index.get(key(gx, gy));
      for (const mirror of gx === 0 ? [1] : [1, -1]) {
        const p = mirror === 1 ? parent : index.get(key(-gx, gy));
        if (p === undefined) continue;
        this.cells.push({ x: (gx + dx * 0.78) * mirror, y: gy + dy * 0.78, r: 0.22, kind: 'sat', phase: rnd.f() * TAU, pip: false });
        this.links.push({ a: p, b: this.cells.length - 1, width: 0.22 });
      }
    }

    for (let gy = -4; gy <= 4; gy++) for (let gx = -3; gx <= 3; gx++) {
      const x = gx * 1.55 + (gy % 2 !== 0 ? 0.78 : 0);
      const y = gy * 1.45;
      const outer = (x / 4.5) ** 2 + (y / 6.3) ** 2;
      const inner = (x / 2.6) ** 2 + (y / 4.5) ** 2;
      if (outer > 1 || inner < 0.7) continue;
      if (rnd.f() > 0.75) continue;
      this.bubbles.push({ x, y, r: 0.68 + rnd.f() * 0.3, grey: rnd.f() < 0.22, phase: rnd.f() * TAU });
    }

    this.pills.push({ x: -2.9, y: 2.6, length: 2.0, vertical: true, bright: true });
    this.pills.push({ x: 2.4, y: -5.5, length: 1.8, vertical: true, bright: false });
    this.pills.push({ x: 3.3, y: -3.6, length: 1.2, vertical: false, bright: false });
    this.pills.push({ x: 3.0, y: -1.4, length: 1.1, vertical: true, bright: false });
  }

  cellScale(i, t, s) {
    const c = this.cells[i];
    const breathe = 1 + 0.05 * Math.sin(t * 1.1 + c.phase);
    let react = 1;
    if (s.mode === 'listening') react = 1 + (0.12 + 0.42 * s.level) * (0.5 + 0.5 * Math.sin(t * 6 + c.y * 1.3 + c.phase * 0.3));
    else if (s.mode === 'thinking') react = 1 + 0.28 * Math.max(0, Math.cos(Math.hypot(c.x, c.y) * 1.3 - t * 5)) ** 3;
    else if (s.mode === 'speaking') react = 1 + 0.2 * (0.5 + 0.5 * Math.sin(t * 9 + c.phase)) * (0.5 + 0.5 * Math.sin(t * 2.3));
    return breathe * react;
  }
  wobble(s) { return 0.045 + (s.mode === 'listening' ? 0.06 * s.level : 0); }
  cellX(i, t, s) { const c = this.cells[i]; return c.x + this.wobble(s) * Math.sin(t * 0.7 + c.phase); }
  cellY(i, t, s) { const c = this.cells[i]; return c.y + this.wobble(s) * Math.cos(t * 0.55 + c.phase * 1.3); }
  spread(s) { return 1 + (s.mode === 'listening' ? 0.03 + 0.05 * s.level : 0); }
  bubbleX(b, t, s) { return (b.x + 0.08 * Math.sin(t * 0.35 + b.phase)) * this.spread(s); }
  bubbleY(b, t, s) { return (b.y + 0.08 * Math.cos(t * 0.3 + b.phase)) * this.spread(s); }
  bubbleR(b, t) { return b.r * (1 + 0.04 * Math.sin(t * 0.8 + b.phase)); }
  glow(s) {
    return { idle: 0.45, info: 0.3, listening: 0.65 + 0.35 * s.level, thinking: 0.7, speaking: 0.75 }[s.mode];
  }

  // Every cell's pose frozen at one instant, for sampling the shape many times per frame.
  snapshot(t, s) {
    const cells = this.cells, links = this.links;
    const x = cells.map((_, i) => this.cellX(i, t, s));
    const y = cells.map((_, i) => this.cellY(i, t, s));
    const scale = cells.map((_, i) => this.cellScale(i, t, s));
    return {
      // Signed distance to the merged core (< 0 inside). The smooth-min melts nearby shapes
      // together the same way the screen's blur + threshold does.
      distance(px, py) {
        let d = 1e9;
        for (let i = 0; i < cells.length; i++) d = smin(d, Math.hypot(px - x[i], py - y[i]) - cells[i].r * scale[i], GOO_K);
        for (const l of links) {
          d = smin(d, segDist(px, py, x[l.a], y[l.a], x[l.b], y[l.b]) - l.width * (scale[l.a] + scale[l.b]) / 4, GOO_K);
        }
        return d;
      },
      hole(px, py) {
        for (let i = 0; i < cells.length; i++) {
          if (cells[i].kind !== 'ring') continue;
          const r = cells[i].r * scale[i], d = Math.hypot(px - x[i], py - y[i]);
          if (d < r * 0.2) return 0;
          if (d < r * 0.56) return 1;
        }
        return 0;
      },
    };
  }
}

const GOO_K = 0.18;
function smin(a, b, k) { const h = Math.max(k - Math.abs(a - b), 0) / k; return Math.min(a, b) - h * h * k * 0.25; }
function segDist(px, py, ax, ay, bx, by) {
  const vx = bx - ax, vy = by - ay, len2 = vx * vx + vy * vy;
  const u = len2 === 0 ? 0 : Math.min(1, Math.max(0, ((px - ax) * vx + (py - ay) * vy) / len2));
  return Math.hypot(px - (ax + u * vx), py - (ay + u * vy));
}
function smoothstep(a, b, x) { const k = Math.min(1, Math.max(0, (x - a) / (b - a))); return k * k * (3 - 2 * k); }

// Port of glyph/MatrixRenderer.kt
const MATRIX = 25;
function renderMatrix(model, t, s, max = 255) {
  const out = new Array(MATRIX * MATRIX).fill(0);
  const step = 11 / MATRIX; // model units per LED
  const half = (MATRIX - 1) / 2;
  const pose = model.snapshot(t, s);
  for (let row = 0; row < MATRIX; row++) for (let col = 0; col < MATRIX; col++) {
    const dx = col - half, dy = row - half;
    if (dx * dx + dy * dy > (half + 0.5) ** 2) continue; // round panel
    const x = dx * step, y = dy * step;
    let b = smoothstep(step * 0.5, -step * 0.5, pose.distance(x, y)) * (1 - pose.hole(x, y));
    if (s.mode === 'listening') {
      const ring = Math.abs(Math.hypot(dx, dy) - (9 + 3 * s.level));
      b = Math.max(b, 0.35 * smoothstep(1.2, 0, ring));
    }
    out[row * MATRIX + col] = Math.round(b * max);
  }
  return out;
}

// Port of glyph/MatrixFont.kt: a proportional 5x7 font, empty side columns trimmed.
const FONT_SRC = {
  'A': '01110 10001 10001 11111 10001 10001 10001',
  'B': '11110 10001 10001 11110 10001 10001 11110',
  'C': '01110 10001 10000 10000 10000 10001 01110',
  'D': '11110 10001 10001 10001 10001 10001 11110',
  'E': '11111 10000 10000 11110 10000 10000 11111',
  'F': '11111 10000 10000 11110 10000 10000 10000',
  'G': '01110 10001 10000 10111 10001 10001 01111',
  'H': '10001 10001 10001 11111 10001 10001 10001',
  'I': '01110 00100 00100 00100 00100 00100 01110',
  'J': '00111 00010 00010 00010 00010 10010 01100',
  'K': '10001 10010 10100 11000 10100 10010 10001',
  'L': '10000 10000 10000 10000 10000 10000 11111',
  'M': '10001 11011 10101 10101 10001 10001 10001',
  'N': '10001 10001 11001 10101 10011 10001 10001',
  'O': '01110 10001 10001 10001 10001 10001 01110',
  'P': '11110 10001 10001 11110 10000 10000 10000',
  'Q': '01110 10001 10001 10001 10101 10010 01101',
  'R': '11110 10001 10001 11110 10100 10010 10001',
  'S': '01111 10000 10000 01110 00001 00001 11110',
  'T': '11111 00100 00100 00100 00100 00100 00100',
  'U': '10001 10001 10001 10001 10001 10001 01110',
  'V': '10001 10001 10001 10001 10001 01010 00100',
  'W': '10001 10001 10001 10101 10101 10101 01010',
  'X': '10001 10001 01010 00100 01010 10001 10001',
  'Y': '10001 10001 10001 01010 00100 00100 00100',
  'Z': '11111 00001 00010 00100 01000 10000 11111',
  '0': '01110 10001 10011 10101 11001 10001 01110',
  '1': '00100 01100 00100 00100 00100 00100 01110',
  '2': '01110 10001 00001 00010 00100 01000 11111',
  '3': '11111 00010 00100 00010 00001 10001 01110',
  '4': '00010 00110 01010 10010 11111 00010 00010',
  '5': '11111 10000 11110 00001 00001 10001 01110',
  '6': '00110 01000 10000 11110 10001 10001 01110',
  '7': '11111 00001 00010 00100 01000 01000 01000',
  '8': '01110 10001 10001 01110 10001 10001 01110',
  '9': '01110 10001 10001 01111 00001 00010 01100',
  '.': '00000 00000 00000 00000 00000 00100 00100',
  ',': '00000 00000 00000 00000 00100 00100 01000',
  "'": '00100 00100 01000 00000 00000 00000 00000',
  ':': '00000 00100 00100 00000 00100 00100 00000',
  '?': '01110 10001 00001 00010 00100 00000 00100',
  '!': '00100 00100 00100 00100 00100 00000 00100',
  '-': '00000 00000 00000 11111 00000 00000 00000',
  '+': '00000 00100 00100 11111 00100 00100 00000',
  '/': '00000 00001 00010 00100 01000 10000 00000',
  '%': '11000 11001 00010 00100 01000 10011 00011'
};
const FONT = Object.fromEntries(Object.entries(FONT_SRC).map(([ch, src]) => {
  const r = src.split(' ');
  const cols = [0, 1, 2, 3, 4].map(c => r.reduce((acc, row, i) => row[c] === '1' ? acc | (1 << i) : acc, 0));
  const first = cols.findIndex(v => v), last = cols.length - 1 - [...cols].reverse().findIndex(v => v);
  return [ch, cols.slice(first, last + 1)];
}));
function textColumns(text) {
  const out = [];
  for (const raw of text) {
    const ch = ({ '\u2018': "'", '\u2019': "'", '\u201c': "'", '\u201d': "'", '"': "'", '\u2013': '-', '\u2014': '-' })[raw] || raw.toUpperCase();
    const g = FONT[ch];
    if (!g) { out.push(0, 0, 0); continue; }
    if (out.length && out[out.length - 1] !== 0) out.push(0);
    out.push(...g);
  }
  while (out.length && out[out.length - 1] === 0) out.pop();
  return out;
}

// Port of glyph/MatrixFeed.kt + MatrixRenderer.renderText.
const matrixFeed = { message: null, clock: null };
const MESSAGE_HOLD_MS = 3500, SCROLL_LEAD_MS = 300, SCROLL_PX_PER_S = 14;
function matrixSay(text) { const c = textColumns(text.trim()); matrixFeed.message = c.length ? { cols: c, at: performance.now() } : null; }
function renderText(cols, x, max = 255) {
  const out = new Array(MATRIX * MATRIX).fill(0), top = (MATRIX - 7) >> 1;
  cols.forEach((bits, i) => {
    const col = x + i; if (col < 0 || col >= MATRIX) return;
    for (let row = 0; row < 7; row++) if (bits & (1 << row)) out[(top + row) * MATRIX + col] = max;
  });
  return out;
}
function renderMatrixFrame(model, t, s, now) {
  const m = matrixFeed.message;
  if (m) {
    const elapsed = now - m.at, fits = m.cols.length <= MATRIX;
    const done = fits ? elapsed > MESSAGE_HOLD_MS : elapsed > SCROLL_LEAD_MS + (m.cols.length + MATRIX) * 1000 / SCROLL_PX_PER_S;
    if (done) matrixFeed.message = null;
    else return renderText(m.cols, fits ? (MATRIX - m.cols.length) >> 1
      : MATRIX - Math.floor(Math.max(0, elapsed - SCROLL_LEAD_MS) * SCROLL_PX_PER_S / 1000));
  }
  if (matrixFeed.clock) { const c = textColumns(matrixFeed.clock); return renderText(c, (MATRIX - c.length) >> 1); }
  return renderMatrix(model, t, s);
}
