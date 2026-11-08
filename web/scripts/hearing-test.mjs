const assert = (condition, message) => {
  if (!condition) throw new Error(message)
}

const clamp = (value) => Math.max(0, Math.min(1, value))
const ease = (value, target, dt, speed) => value + (target - value) * (1 - Math.exp(-dt * speed))

function createDetector() {
  return { previous: null, mean: 0, deviation: 0, refractory: 1, envelope: 0, peak: 0.01, punch: 0 }
}

// This mirrors processHearingFrame in hearing.ts without needing a TypeScript runtime in Node.
function processHearingFrame(frame, dt, state) {
  const hz = 48000 / ((frame.length - 1) * 2)
  const lowStart = Math.max(1, Math.floor(40 / hz))
  const lowEnd = Math.min(frame.length - 1, Math.ceil(130 / hz))
  const subStart = Math.max(1, Math.floor(30 / hz))
  const subEnd = Math.min(frame.length - 1, Math.ceil(160 / hz))
  let flux = 0
  let sub = 0
  for (let i = lowStart; i <= lowEnd; i++) {
    if (state.previous) flux += Math.max(0, frame[i] - state.previous[i])
  }
  for (let i = subStart; i <= subEnd; i++) sub += frame[i]
  flux /= lowEnd - lowStart + 1
  sub /= subEnd - subStart + 1
  const safeDt = Math.min(0.1, Math.max(0.001, dt))
  const threshold = state.mean + 2.2 * state.deviation + 0.0015
  const floor = Math.max(0.0003, state.envelope * 0.035)
  const kick = state.previous !== null && state.refractory >= 0.12 && flux > threshold && flux > floor
  const strength = kick ? clamp((flux - threshold) / Math.max(flux, 0.01)) : 0
  const delta = Math.abs(flux - state.mean)
  state.mean = ease(state.mean, flux, safeDt, 1 / 1.2)
  state.deviation = ease(state.deviation, delta, safeDt, 1 / 1.2)
  state.refractory = kick ? 0 : state.refractory + safeDt
  state.envelope += (sub - state.envelope) * (sub > state.envelope ? 1 - Math.exp(-safeDt * 35) : 1 - Math.exp(-safeDt * 5))
  state.peak = Math.max(sub, state.peak * Math.exp(-safeDt / 6), 0.0001)
  state.punch = kick ? Math.max(0.78, strength) : state.punch * Math.exp(-safeDt / 0.19)
  state.previous = frame.slice()
  return { kick, punch: state.punch, bass: clamp(state.envelope / state.peak) }
}

const BINS = 513
const bin = (hz) => Math.round(hz / (48000 / 1024))
const frame = (sub, kick = 0, quiet = 1) => {
  const values = new Float32Array(BINS)
  for (let i = bin(50); i <= bin(90); i++) values[i] = sub * quiet
  for (let i = bin(55); i <= bin(120); i++) values[i] += kick * quiet
  values[bin(8000)] = 0.12 * quiet
  return values
}

function run(name, makeFrame, expectedKicks, minimumBass = 0) {
  const detector = createDetector()
  const hits = []
  let maxBass = 0
  processHearingFrame(makeFrame(-1), 1 / 60, detector)
  for (let n = 0; n < 240; n++) {
    const result = processHearingFrame(makeFrame(n), 1 / 60, detector)
    if (result.kick) hits.push(n)
    maxBass = Math.max(maxBass, result.bass)
  }
  console.log(`${name}: hits=${hits.join(',')}, kicks=${hits.length}, maxBass=${maxBass.toFixed(2)}`)
  assert(hits.length === expectedKicks, `${name}: expected ${expectedKicks} kicks, got ${hits.length}`)
  assert(maxBass >= minimumBass, `${name}: bass envelope did not move`)
  return hits
}

const kickFrames = (n, quiet = 1) => {
  const beat = Math.round(60 / (120 / 60))
  const phase = n % beat
  return frame(0.18, phase === 1 ? 0.9 : phase === 2 ? 0.3 : 0, quiet)
}
const kicks = run('4/4 kick over sub-bass', (n) => kickFrames(n), 8, 0.5)
assert(kicks.every((value, index) => index === 0 || value - kicks[index - 1] >= 7), 'kicks: refractory period failed')
run('sustained 808', () => frame(0.65), 0, 0.7)
run('silence', () => frame(0), 0, 0)
run('quiet -20 dB kick', (n) => kickFrames(n, 0.1), 8, 0.2)

// — The levels: the track's own dynamics move the wave (mirror of processLevelsFrame) —

const BANDS = 32
const EDGES = Array.from({ length: BANDS + 1 }, (_, i) => 40 * Math.pow(14000 / 40, i / BANDS))
const CENTER = 0.45
const SPAN = 12
const ABS_FLOOR = -50
const ABS_SPAN = 25
const REL_WEIGHT = 0.45
const SLOW_TAU = 4
const SILENT_DB = -80
const LEVELS_FLOOR_DB = -70
const BANDS_FLOOR_DB = -78
const slowLevels = { bass: { v: NaN }, mid: { v: NaN }, high: { v: NaN } }
const slowBands = new Float32Array(BANDS)
const levelsSpectrum = new Float32Array(BANDS)

const bandPowerDb = (bins, from, to, hz) => {
  const a = Math.max(1, Math.floor(from / hz))
  const z = Math.max(a, Math.min(bins.length - 1, Math.ceil(to / hz) - 1))
  let sum = 0
  for (let i = a; i <= z; i++) sum += bins[i] * bins[i]
  return 10 * Math.log10(sum / (z - a + 1) + 1e-12)
}
const levelOf = (db, ref, dt, floorDb) => {
  ref.v = Number.isFinite(ref.v) ? ref.v + (db - ref.v) * (1 - Math.exp(-dt / SLOW_TAU)) : db
  if (db < floorDb) return 0
  const abs = clamp((db - ABS_FLOOR) / ABS_SPAN)
  const rel = clamp(CENTER + (db - ref.v) / SPAN)
  return clamp((1 - REL_WEIGHT) * abs + REL_WEIGHT * rel)
}
function processLevelsFrame(fft, hz, dt, fresh = false) {
  if (fresh) {
    slowLevels.bass.v = slowLevels.mid.v = slowLevels.high.v = NaN
    slowBands.fill(NaN)
  }
  const b = bandPowerDb(fft, 30, 160, hz)
  const m = bandPowerDb(fft, 300, 2000, hz)
  const h = bandPowerDb(fft, 4000, 12000, hz)
  for (let j = 0; j < BANDS; j++) {
    const db = bandPowerDb(fft, EDGES[j], EDGES[j + 1], hz)
    slowBands[j] = Number.isFinite(slowBands[j]) ? slowBands[j] + (db - slowBands[j]) * (1 - Math.exp(-dt / SLOW_TAU)) : db
    levelsSpectrum[j] = db < BANDS_FLOOR_DB ? 0 : clamp(CENTER + (db - slowBands[j]) / SPAN)
  }
  return {
    bass: levelOf(b, slowLevels.bass, dt, LEVELS_FLOOR_DB),
    mid: levelOf(m, slowLevels.mid, dt, LEVELS_FLOOR_DB),
    high: levelOf(h, slowLevels.high, dt, LEVELS_FLOOR_DB),
    bands: levelsSpectrum,
    heard: b > SILENT_DB || m > SILENT_DB || h > SILENT_DB,
  }
}

// A synthetic track, 20 s at 30 fps: a quiet verse, a drop with hats bursting on the eighths,
// an outro that dies away. The wave, mapped like hearing.ts does, must follow the track's own
// dynamics: the drop over the verse, the hats over the pad between them, the rest in the outro.
// The measures come once the slow averages have settled in each section.
const LF = 600
const hzL = 48000 / 2048
const fillBand = (fft, from, to, db) => {
  const amp = Math.pow(10, db / 20)
  const a = Math.max(1, Math.floor(from / hzL))
  const z = Math.min(LF_BINS - 1, Math.ceil(to / hzL) - 1)
  for (let i = a; i <= z; i++) fft[i] = Math.max(fft[i], amp)
}
const LF_BINS = 1024
const trackFrame = (n) => {
  const fft = new Float32Array(LF_BINS)
  if (n < 120) {
    fillBand(fft, 30, 160, -45)
    fillBand(fft, 300, 2000, -50)
    fillBand(fft, 4000, 12000, -58)
  } else if (n < 480) {
    fillBand(fft, 30, 160, -26)
    fillBand(fft, 300, 2000, -30)
    fillBand(fft, 4000, 12000, -40)
    if (n % 15 < 2) fillBand(fft, 8000, 14000, -24)
  } else {
    fillBand(fft, 30, 160, -60)
    fillBand(fft, 300, 2000, -62)
    fillBand(fft, 4000, 12000, -64)
  }
  return fft
}
let hatBand = 0
for (let j = 0; j < BANDS; j++) if (EDGES[j + 1] > 10500) { hatBand = j; break }

processLevelsFrame(trackFrame(-1), hzL, 1 / 30, true)
const verseBass = [], dropBass = [], hatHighs = [], padHighs = [], outro = {}
let hatBarAt, bassBarAt
for (let n = 0; n < LF; n++) {
  const L = processLevelsFrame(trackFrame(n), hzL, 1 / 30)
  if (!L.heard) throw new Error(`levels: frame ${n} not heard`)
  if (n >= 30 && n < 120) verseBass.push(L.bass)
  if (n >= 360 && n < 480) dropBass.push(L.bass)
  if (n >= 360 && n < 480) {
    if (n % 15 < 2) {
      hatHighs.push(L.high)
      if (hatBarAt === undefined) {
        hatBarAt = L.bands[hatBand]
        bassBarAt = L.bands[0]
      }
    } else padHighs.push(L.high)
  }
  if (n >= 520) {
    outro.bass = Math.max(outro.bass ?? 0, L.bass)
    outro.high = Math.max(outro.high ?? 0, L.high)
  }
}
const avg = (xs) => xs.reduce((a, v) => a + v, 0) / xs.length
const verseAvg = avg(verseBass), dropAvg = avg(dropBass), hatAvg = avg(hatHighs), padAvg = avg(padHighs)
console.log(`dynamic track: bass verse=${verseAvg.toFixed(2)} drop=${dropAvg.toFixed(2)}, high hat=${hatAvg.toFixed(2)} pad=${padAvg.toFixed(2)}, bars hat>${bassBarAt?.toFixed(2)}`)
assert(dropAvg >= verseAvg + 0.3, `levels: the drop does not lift the bass (${verseAvg.toFixed(2)} → ${dropAvg.toFixed(2)})`)
assert(hatAvg >= 0.6, `levels: hats do not lift the highs (${hatAvg.toFixed(2)})`)
assert(padAvg <= hatAvg - 0.3, `levels: the pad between the hats is as loud as them (${padAvg.toFixed(2)})`)
assert(hatBarAt > bassBarAt, `levels: the hat band does not out-show the bass band (${bassBarAt.toFixed(2)} vs ${hatBarAt.toFixed(2)})`)
assert(outro.bass < 0.25 && outro.high < 0.25, `levels: the outro does not quiet down (bass ${outro.bass.toFixed(2)}, high ${outro.high?.toFixed(2)})`)
console.log('hearing test passed')
