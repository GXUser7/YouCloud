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
console.log('hearing test passed')
