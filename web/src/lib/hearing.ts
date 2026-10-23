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
  /** 0…1 each, eased: the kick lifts [bass] at once and lets it down slower. */
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

export const levels: Levels = { bass: 0, mid: 0, high: 0, bands: new Float32Array(BANDS), energy: 0, heard: false }

const EDGES = Array.from({ length: BANDS + 1 }, (_, i) => 40 * Math.pow(14000 / 40, i / BANDS))

let ctx: AudioContext | null = null
let analyser: AnalyserNode | null = null
let bins: Uint8Array<ArrayBuffer> | null = null
let source: MediaStreamAudioSourceNode | null = null
let captured: HTMLAudioElement | null = null
let stream: MediaStream | null = null
let trackId = ''
let triedAt = -Infinity

// How loud each part has been lately: what is heard is measured against it, so a quiet track
// moves the wave as much as a loud one. [mean] is the bass of the last second or two, for the kick
// above it.
const peak = { bass: 0.3, mid: 0.3, high: 0.2, bands: 0.3 }
let mean = 0

type Capturable = HTMLAudioElement & { captureStream?: () => MediaStream }

function release() {
  source?.disconnect()
  source = null
  stream?.getTracks().forEach((t) => t.stop())
  stream = null
  trackId = ''
}

function attach(track: MediaStreamTrack) {
  ctx ??= new AudioContext()
  if (ctx.state === 'suspended') void ctx.resume().catch(() => {})
  if (!analyser) {
    analyser = ctx.createAnalyser()
    analyser.fftSize = 2048
    analyser.smoothingTimeConstant = 0.5
    bins = new Uint8Array(analyser.frequencyBinCount)
  }
  source?.disconnect()
  source = ctx.createMediaStreamSource(new MediaStream([track]))
  source.connect(analyser)
  trackId = track.id
}

/**
 * The playing <audio>'s sound into the analyser: captured anew when the player has moved to
 * another <audio>, or the capture has lost its track. False when it can't be heard; a refused
 * capture is tried again a second later, as the same <audio> may play another stream by then.
 */
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

/** The share of the loudest byte in [from, to) Hz. */
function part(from: number, to: number): number {
  const hz = ctx!.sampleRate / analyser!.fftSize
  const a = Math.max(1, Math.floor(from / hz))
  const b = Math.max(a, Math.min(bins!.length - 1, Math.ceil(to / hz) - 1))
  let sum = 0
  for (let i = a; i <= b; i++) sum += bins![i]
  return sum / ((b - a + 1) * 255)
}

const clamp = (v: number) => Math.max(0, Math.min(1, v))
const ease = (v: number, to: number, dt: number, up: number, down: number) => v + (to - v) * (1 - Math.exp(-dt * (to > v ? up : down)))
const remembered = (key: keyof typeof peak, v: number, dt: number) => (peak[key] = Math.max(v, peak[key] * Math.exp(-dt / 6), 0.06))

/**
 * Where the wave stands now, [dt] seconds after it was asked last. [listen]: the sound is wanted;
 * when not («Под музыку» off), the wave only breathes.
 */
export function hear(now: number, dt: number, listen: boolean): Levels {
  const playing = player.playing && !player.loading
  let bass = 0, mid = 0, high = 0
  let heard = false
  const bands = new Float32Array(BANDS)
  if (listen && playing && connect(now) && analyser && bins) {
    analyser.getByteFrequencyData(bins)
    const b = part(30, 160), m = part(300, 2000), h = part(4000, 12000)
    // Digital silence, or a capture the browser keeps empty: nothing to go by.
    heard = b + m + h > 0.004
    if (heard) {
      mean = ease(mean, b, dt, 0.8, 0.8)
      const top = remembered('bass', b, dt)
      bass = clamp(0.45 * Math.pow(b / top, 2) + 0.7 * clamp((b - mean) / Math.max(0.03, top - mean)))
      mid = clamp(m / remembered('mid', m, dt))
      high = clamp((h / remembered('high', h, dt) - 0.2) / 0.8)
      let loudest = 0
      for (let j = 0; j < BANDS; j++) loudest = Math.max(loudest, (bands[j] = part(EDGES[j], EDGES[j + 1])))
      const top2 = remembered('bands', loudest, dt)
      for (let j = 0; j < BANDS; j++) bands[j] = clamp(Math.pow(bands[j] / top2, 1.4))
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
      bands[j] = (bass * Math.pow(1 - u, 2) + 0.75 * mid * Math.exp(-Math.pow((u - 0.45) / 0.22, 2)) + 0.5 * high * u) * (0.8 + 0.2 * Math.sin(t * (0.9 + (j % 5) * 0.3) + j * 1.9))
    }
  }
  levels.heard = heard
  levels.bass = ease(levels.bass, bass, dt, heard ? 30 : 3, heard ? 7 : 2)
  levels.mid = ease(levels.mid, mid, dt, 6, 3)
  levels.high = ease(levels.high, high, dt, 40, 9)
  levels.energy = ease(levels.energy, playing ? 1 : 0, dt, 1.5, 0.8)
  for (let j = 0; j < BANDS; j++) levels.bands[j] = ease(levels.bands[j], bands[j], dt, 18, 5)
  return levels
}

/** The full player is closed: the capture is let go, and the audio context rests until it opens. */
export function stopHearing() {
  release()
  captured = null
  triedAt = -Infinity
  void ctx?.suspend().catch(() => {})
}
