// What the full player's wave hears: the bass, the middle and the top of what plays, and its
// spectrum in bands, off an AnalyserNode.
//
// The playing <audio> is captured (captureStream), not routed through Web Audio: routed, a stream
// from another origin that doesn't allow it (CORS) would go silent for the listener too, while a
// capture of it is only refused. SoundCloud's and Yandex's streams are asked for in CORS and heard,
// as is whatever plays through hls.js; YouTube's are where the extension or Masaki let the page
// read them (media.svelte.ts, readable). Where nothing can be heard
// — that refusal, a browser without the capture (Firefox's own mutes the element, Safari has
// none), a stand-in track with no sound — the wave breathes on its own while the music plays.

import { player, playingSound } from './state.svelte'

export const BANDS = 32

export type Levels = {
  /** The kick jumps at once and settles in about 200 ms; [bass] holds the body underneath. */
  punch: number
  kick: boolean
  bass: number
  mid: number
  high: number
  /** The spectrum from 40 Hz to 14 kHz, low to high. */
  bands: Float32Array
  /** 1 while it plays, easing to 0 on a pause. */
  energy: number
  /** Whether these came from the sound, or the wave only breathes. */
  heard: boolean
}

export const levels: Levels = {
  punch: 0,
  kick: false,
  bass: 0,
  mid: 0,
  high: 0,
  bands: new Float32Array(BANDS),
  energy: 0,
  heard: false,
}

const EDGES = Array.from({ length: BANDS + 1 }, (_, i) => 40 * Math.pow(14000 / 40, i / BANDS))
const clamp = (v: number) => Math.max(0, Math.min(1, v))
const ease = (v: number, to: number, dt: number, up: number, down: number) => v + (to - v) * (1 - Math.exp(-dt * (to > v ? up : down)))

export type HearingFrame = {
  punch: number
  kick: boolean
  bass: number
}

export type HearingState = {
  previous: Float32Array | null
  fluxMean: number
  fluxDeviation: number
  refractory: number
  subEnvelope: number
  subPeak: number
  punch: number
  result: HearingFrame
}

export function createHearingState(): HearingState {
  return {
    previous: null,
    fluxMean: 0,
    fluxDeviation: 0,
    refractory: 1,
    subEnvelope: 0,
    subPeak: 0.01,
    punch: 0,
    result: { punch: 0, kick: false, bass: 0 },
  }
}

/** Extract the low-end onset and envelope from one linear-magnitude FFT frame. */
export function processHearingFrame(frame: Float32Array, dt: number, sampleRate = 48000, state = createHearingState()): HearingFrame {
  const fftSize = Math.max(2, (frame.length - 1) * 2)
  const hz = sampleRate / fftSize
  const lowStart = Math.max(1, Math.floor(40 / hz))
  const lowEnd = Math.min(frame.length - 1, Math.ceil(130 / hz))
  const subStart = Math.max(1, Math.floor(30 / hz))
  const subEnd = Math.min(frame.length - 1, Math.ceil(160 / hz))
  const previous = state.previous && state.previous.length === frame.length ? state.previous : null
  let flux = 0
  let sub = 0
  for (let i = lowStart; i <= lowEnd; i++) {
    const value = frame[i]
    if (previous) flux += Math.max(0, value - previous[i])
  }
  for (let i = subStart; i <= subEnd; i++) sub += frame[i]
  flux /= Math.max(1, lowEnd - lowStart + 1)
  sub /= Math.max(1, subEnd - subStart + 1)

  const safeDt = Math.min(0.1, Math.max(0.001, dt))
  const threshold = state.fluxMean + 2.2 * state.fluxDeviation + 0.0015
  // A relative floor keeps quiet passages quiet while still allowing a quiet track to wake up.
  const floor = Math.max(0.0003, state.subEnvelope * 0.035)
  const onset = previous !== null && state.refractory >= 0.12 && flux > threshold && flux > floor
  const strength = onset ? clamp((flux - threshold) / Math.max(flux, 0.01)) : 0
  const meanDelta = Math.abs(flux - state.fluxMean)
  state.fluxMean = ease(state.fluxMean, flux, safeDt, 1 / 1.2, 1 / 0.7)
  state.fluxDeviation = ease(state.fluxDeviation, meanDelta, safeDt, 1 / 1.2, 1 / 0.7)
  state.refractory = onset ? 0 : state.refractory + safeDt
  const attack = 1 - Math.exp(-safeDt * 35)
  const release = 1 - Math.exp(-safeDt * 5)
  state.subEnvelope += (sub - state.subEnvelope) * (sub > state.subEnvelope ? attack : release)
  state.subPeak = Math.max(sub, state.subPeak * Math.exp(-safeDt / 6), 0.0001)
  state.punch = onset ? Math.max(0.78, strength) : state.punch * Math.exp(-safeDt / 0.19)

  if (!state.previous || state.previous.length !== frame.length) state.previous = new Float32Array(frame.length)
  state.previous.set(frame)
  const result = state.result
  result.punch = state.punch
  result.kick = onset
  result.bass = clamp(state.subEnvelope / state.subPeak)
  return result
}

const peak = { bass: 0.015, mid: 0.02, high: 0.01, bands: 0.02 }
const spectrum = new Float32Array(BANDS)
const hearingState = createHearingState()

type Capturable = HTMLAudioElement & { captureStream?: () => MediaStream }

let ctx: AudioContext | null = null
let analyser: AnalyserNode | null = null
let onsetAnalyser: AnalyserNode | null = null
let bins: Float32Array<ArrayBuffer> | null = null
let onsetBins: Float32Array<ArrayBuffer> | null = null
let source: MediaStreamAudioSourceNode | null = null
let captured: HTMLAudioElement | null = null
let stream: MediaStream | null = null
let trackId = ''
let triedAt = -Infinity

function resetState() {
  peak.bass = 0.015
  peak.mid = 0.02
  peak.high = 0.01
  peak.bands = 0.02
  hearingState.previous = null
  hearingState.fluxMean = 0
  hearingState.fluxDeviation = 0
  hearingState.refractory = 1
  hearingState.subEnvelope = 0
  hearingState.subPeak = 0.01
  hearingState.punch = 0
  hearingState.result.punch = 0
  hearingState.result.kick = false
}

function release() {
  source?.disconnect()
  source = null
  stream?.getTracks().forEach((t) => t.stop())
  stream = null
  trackId = ''
  resetState()
}

function attach(track: MediaStreamTrack) {
  ctx ??= new AudioContext()
  if (ctx.state === 'suspended') void ctx.resume().catch(() => {})
  if (!analyser) {
    analyser = ctx.createAnalyser()
    analyser.fftSize = 2048
    analyser.smoothingTimeConstant = 0.5
    bins = new Float32Array(analyser.frequencyBinCount)
  }
  if (!onsetAnalyser) {
    onsetAnalyser = ctx.createAnalyser()
    onsetAnalyser.fftSize = 1024
    onsetAnalyser.smoothingTimeConstant = 0
    onsetBins = new Float32Array(onsetAnalyser.frequencyBinCount)
  }
  source?.disconnect()
  source = ctx.createMediaStreamSource(new MediaStream([track]))
  source.connect(analyser)
  source.connect(onsetAnalyser)
  trackId = track.id
  resetState()
}

/** The playing <audio>'s sound into the analyser. False when the browser cannot let the wave hear it. */
function connect(now: number): boolean {
  const el = playingSound() as Capturable
  if (!el.captureStream || typeof AudioContext === 'undefined') return false
  const track = stream?.getAudioTracks()[0]
  if (el === captured && track?.readyState === 'live') {
    if (track.id !== trackId) attach(track)
    return true
  }
  if (now - triedAt < 1000) return false
  triedAt = now
  release()
  captured = el
  try {
    stream = el.captureStream()
  } catch {
    return false
  }
  const first = stream.getAudioTracks()[0]
  if (first) attach(first)
  return !!first
}

function part(from: number, to: number): number {
  const hz = ctx!.sampleRate / analyser!.fftSize
  const a = Math.max(1, Math.floor(from / hz))
  const b = Math.max(a, Math.min(bins!.length - 1, Math.ceil(to / hz) - 1))
  let sum = 0
  for (let i = a; i <= b; i++) sum += bins![i]
  return sum / (b - a + 1)
}

const remembered = (key: keyof typeof peak, v: number, dt: number) => (peak[key] = Math.max(v, peak[key] * Math.exp(-dt / 6), 0.0001))

/** Where the wave stands now, [dt] seconds after it was asked last. [listen] keeps the setting «Под музыку». */
export function hear(now: number, dt: number, listen: boolean): Levels {
  const playing = player.playing && !player.loading
  let bass = 0, mid = 0, high = 0
  let punch = 0
  let kick = false
  let heard = false
  for (let j = 0; j < BANDS; j++) spectrum[j] = 0
  if (listen && playing && connect(now) && analyser && onsetAnalyser && bins && onsetBins) {
    analyser.getFloatFrequencyData(bins)
    onsetAnalyser.getFloatFrequencyData(onsetBins)
    for (let i = 0; i < bins.length; i++) bins[i] = Number.isFinite(bins[i]) ? Math.pow(10, bins[i] / 20) : 0
    for (let i = 0; i < onsetBins.length; i++) onsetBins[i] = Number.isFinite(onsetBins[i]) ? Math.pow(10, onsetBins[i] / 20) : 0
    const sampleRate = ctx!.sampleRate
    const b = part(30, 160), m = part(300, 2000), h = part(4000, 12000)
    heard = b + m + h > 0.00004
    const onset = processHearingFrame(onsetBins, dt, sampleRate, hearingState)
    if (heard) {
      bass = clamp(Math.pow(b / remembered('bass', b, dt), 0.7))
      bass = Math.max(bass, onset.bass)
      mid = clamp(m / remembered('mid', m, dt))
      high = clamp((h / remembered('high', h, dt) - 0.16) / 0.84)
      let loudest = 0
      for (let j = 0; j < BANDS; j++) {
        const a = Math.max(1, Math.floor(EDGES[j] / (sampleRate / analyser.fftSize)))
        const z = Math.max(a, Math.min(bins.length - 1, Math.ceil(EDGES[j + 1] / (sampleRate / analyser.fftSize)) - 1))
        let sum = 0
        for (let i = a; i <= z; i++) sum += bins[i]
        const value = sum / (z - a + 1)
        loudest = Math.max(loudest, spectrum[j] = value)
      }
      const top = remembered('bands', loudest, dt)
      for (let j = 0; j < BANDS; j++) spectrum[j] = clamp(Math.pow(spectrum[j] / top, 1.25))
      punch = onset.punch
      kick = onset.kick
    }
  }
  if (!heard && playing) {
    // Breathing: slow, with no beat of its own — a made-up one would argue with the music.
    const t = now / 1000
    bass = 0.3 + 0.15 * Math.sin(t * 1.1)
    mid = 0.45 + 0.15 * Math.sin(t * 0.6 + 1)
    high = 0.12 + 0.06 * Math.sin(t * 2.3)
    for (let j = 0; j < BANDS; j++) {
      const u = j / (BANDS - 1)
      spectrum[j] = (bass * Math.pow(1 - u, 2) + 0.75 * mid * Math.exp(-Math.pow((u - 0.45) / 0.22, 2)) + 0.5 * high * u) * (0.8 + 0.2 * Math.sin(t * (0.9 + (j % 5) * 0.3) + j * 1.9))
    }
  }
  levels.heard = heard
  levels.kick = kick
  levels.punch = heard ? punch : 0
  levels.bass = ease(levels.bass, bass, dt, heard ? 18 : 3, heard ? 4 : 2)
  levels.mid = ease(levels.mid, mid, dt, 6, 3)
  levels.high = ease(levels.high, high, dt, 40, 9)
  levels.energy = ease(levels.energy, playing ? 1 : 0, dt, 1.5, 0.8)
  for (let j = 0; j < BANDS; j++) levels.bands[j] = ease(levels.bands[j], spectrum[j], dt, 18, 5)
  return levels
}

/** The full player is closed: the capture is let go, and the audio context rests until it opens. */
export function stopHearing() {
  release()
  captured = null
  triedAt = -Infinity
  void ctx?.suspend().catch(() => {})
}
