// What the site shows and plays.
//
// The player is one <audio>: SoundCloud's streams through hls.js (or straight, when progressive),
// Yandex's as a signed MP3 link. A radio — Yandex's Rotor or SoundCloud's related tracks — tops
// the queue up before it runs out, and Rotor hears what was finished or skipped, as in the app.
// The queue survives a reload. The design's stand-in tracks (YouTube for now) have no sound, so
// for them the player only counts time.

import Hls from 'hls.js'
import { putTrack, track as trackOf, type Service, type Track } from './catalog'
import * as live from './live'
import { directFailed, reach } from './media.svelte'
import { WAVE_MODES, WAVE_MOODS } from './mock'
import * as yandex from './services/yandex'
import { reason, toast } from './toast.svelte'

export type RouteName = 'home' | 'search' | 'friends' | 'settings' | 'set' | 'artist'
export type Route = { name: RouteName; id?: string }
export type Panel = 'friends' | 'queue' | 'lyrics'
export type ThemeMode = 'system' | 'light' | 'dark'

function parseHash(): Route {
  const [name, id] = location.hash.replace(/^#\/?/, '').split('/')
  // «Моя музыка» lives on the service's page now («Любимое»); old links land there.
  const known: RouteName[] = ['home', 'search', 'friends', 'settings', 'set', 'artist']
  return known.includes(name as RouteName) ? { name: name as RouteName, id: id ? decodeURIComponent(id) : undefined } : { name: 'home' }
}

function stored<T>(key: string, fallback: T): T {
  try {
    const raw = localStorage.getItem('yc.' + key)
    return raw == null ? fallback : (JSON.parse(raw) as T)
  } catch {
    return fallback
  }
}

export function remember(key: string, value: unknown) {
  try {
    localStorage.setItem('yc.' + key, JSON.stringify(value))
  } catch {
    // private window or blocked storage: the setting just isn't kept
  }
}

export const app = $state({
  route: parseHash(),
  service: stored<Service>('service', 'sc'),
  panel: stored<Panel>('panel', 'friends'),
  // The panel opens from the player's buttons; at first the page has the room.
  panelOpen: stored('panelOpen', false),
  nowPlaying: false,
  theme: stored<ThemeMode>('theme', 'dark'),
  coverColors: stored('coverColors', true),
  liveBackdrop: stored('liveBackdrop', true),
  query: '',
  waveMood: stored<string | null>('waveMood', null),
  waveMode: stored<string | null>('waveMode', null),
})

addEventListener('hashchange', () => {
  app.route = parseHash()
  document.querySelector('.scroller')?.scrollTo({ top: 0 })
})

export function go(path: string) {
  const [name, ...rest] = path.split('/')
  location.hash = '/' + (rest.length ? `${name}/${encodeURIComponent(rest.join('/'))}` : name)
}

export function setService(svc: Service) {
  app.service = svc
  remember('service', svc)
}

export function openPanel(panel: Panel) {
  if (app.panelOpen && app.panel === panel) {
    app.panelOpen = false
  } else {
    app.panel = panel
    app.panelOpen = true
  }
  remember('panel', app.panel)
  remember('panelOpen', app.panelOpen)
}

// ------------------------------------------------------------------------------------ player

/** A radio that keeps the queue going: Yandex's Rotor session, or the tracks related to the last
 *  (SoundCloud's related, YouTube Music's up next). */
export type Radio =
  | { kind: 'ya'; sessionId: string; batchOf: Record<string, string | undefined> }
  | { kind: 'related' }

export const player = $state({
  queue: [] as string[],
  index: 0,
  playing: false,
  loading: false,
  position: 0,
  duration: 0,
  volume: stored('volume', 0.8),
  muted: false,
  shuffle: false,
  repeat: 'off' as 'off' | 'all' | 'one',
  context: '',
  contextId: null as string | null,
  wave: false,
  radio: null as Radio | null,
  liked: {} as Record<string, boolean>,
  /** Counts jumps within a track, for whoever follows where it is (friends' "now playing"). */
  seeks: 0,
})

export const currentTrack = (): Track | undefined => (player.queue.length ? trackOf(player.queue[player.index]) : undefined)

const audio = new Audio()
audio.preload = 'auto'
let hls: Hls | null = null
let loaded: string | null = null
let silent = false
let loadSeq = 0
/** The stream playing now, as the service gave it, and how many times it has been asked again. */
let streamUrl: string | null = null
let attempt = 0
let wantPlay = false

function detach() {
  hls?.destroy()
  hls = null
  audio.pause()
  audio.removeAttribute('src')
  audio.load()
  loaded = null
}

async function loadCurrent(autoplay: boolean, startAt = 0, retry = 0) {
  const t = currentTrack()
  const seq = ++loadSeq
  attempt = retry
  wantPlay = autoplay
  streamUrl = null
  detach()
  silent = false
  player.position = startAt
  player.duration = t?.duration ?? 0
  if (!t) {
    player.playing = false
    return
  }
  mediaSession(t)
  radioHeard(t)
  player.loading = true
  try {
    const s = await live.stream(t)
    if (seq !== loadSeq) return
    if (!s) {
      // The design's stand-in tracks: no sound, only time passing.
      silent = true
      player.loading = false
      player.playing = autoplay
      return
    }
    streamUrl = s.url
    if (s.hls && !audio.canPlayType('application/vnd.apple.mpegurl') && Hls.isSupported()) {
      hls = new Hls({
        enableWorker: true,
        startPosition: startAt,
        // The playlist names its pieces by SoundCloud's own addresses: each goes the way that works.
        xhrSetup: (xhr, url) => {
          const via = reach(url)
          if (via !== url) xhr.open('GET', via, true)
        },
      })
      hls.on(Hls.Events.ERROR, (_, data) => {
        if (data.fatal && seq === loadSeq) recover(t, new Error(data.details))
      })
      hls.loadSource(reach(s.url))
      hls.attachMedia(audio)
    } else {
      audio.src = reach(s.url)
      if (startAt) audio.currentTime = startAt
    }
    loaded = t.id
    if (autoplay) await audio.play()
  } catch (e) {
    if (seq === loadSeq && !(e instanceof DOMException && e.name === 'AbortError')) failed(t, e)
  }
}

/**
 * A stream that won't play is asked for once more another way before the listener hears of it:
 * SoundCloud's through the Worker when its CDN is blocked here, YouTube's from another client
 * (a link one client hands out can be refused when fetched).
 */
function recover(t: Track, e: unknown) {
  if (attempt < 3 && (directFailed(streamUrl) || live.streamFailed(t))) {
    loadCurrent(wantPlay || !audio.paused, audio.currentTime || player.position, attempt + 1)
    return
  }
  failed(t, e)
}

function failed(t: Track, e: unknown) {
  player.loading = false
  player.playing = false
  if (e instanceof DOMException && e.name === 'NotAllowedError') toast('Браузер ждёт нажатия: нажми ▶')
  else toast(`«${t.title}» не играет: ${reason(e)}`)
}

audio.addEventListener('play', () => (player.playing = true))
audio.addEventListener('playing', () => (player.loading = false))
audio.addEventListener('waiting', () => (player.loading = true))
audio.addEventListener('pause', () => {
  if (!audio.ended) player.playing = false
})
audio.addEventListener('timeupdate', () => {
  player.position = audio.currentTime
  remember('position', Math.floor(audio.currentTime))
})
audio.addEventListener('durationchange', () => {
  if (Number.isFinite(audio.duration) && audio.duration > 0) player.duration = audio.duration
})
audio.addEventListener('ended', () => next(true))
audio.addEventListener('error', () => {
  const t = currentTrack()
  if (t && loaded === t.id && !hls) recover(t, new Error(audio.error?.code === 4 ? 'сервис не отдал звук по ссылке' : 'поток оборвался'))
})

$effect.root(() => {
  $effect(() => {
    audio.volume = player.muted ? 0 : player.volume
  })
})

/** Plays [ids] from [start]; [radio] keeps it going past its end. */
export function playQueue(ids: string[], start = 0, context = '', contextId: string | null = null, radio: Radio | null = null, wave = false) {
  if (!ids.length) return
  player.queue = ids
  player.index = Math.max(0, Math.min(start, ids.length - 1))
  player.context = context
  player.contextId = contextId
  player.radio = radio
  player.wave = wave
  player.shuffle = false
  unshuffled = null
  saveQueue()
  loadCurrent(true)
}

/** Plays [id] from [ids], or, when it already plays from there, pauses or resumes it. */
export function playTrack(id: string, ids: string[], context = '', contextId: string | null = null) {
  if (player.queue[player.index] === id && player.contextId === contextId) {
    toggle()
    return
  }
  playQueue(ids, Math.max(0, ids.indexOf(id)), context, contextId)
}

export function toggle() {
  const t = currentTrack()
  if (!t) return
  if (silent) {
    player.playing = !player.playing
    return
  }
  if (loaded !== t.id) {
    loadCurrent(true, player.position)
    return
  }
  if (audio.paused) audio.play().catch((e) => failed(t, e))
  else audio.pause()
}

export function next(auto = false) {
  const t = currentTrack()
  if (!t) return
  if (auto && player.repeat === 'one') {
    seek(0)
    if (!silent) audio.play().catch(() => {})
    return
  }
  radioTold(t, auto ? 'trackFinished' : 'skip')
  if (player.index < player.queue.length - 1) player.index++
  else if (player.repeat === 'all') player.index = 0
  else {
    player.playing = false
    return
  }
  saveQueue()
  loadCurrent(true)
  refill()
}

export function prev() {
  if (player.position > 3) {
    seek(0)
    return
  }
  if (player.index === 0) return
  player.index--
  saveQueue()
  loadCurrent(true)
}

/** Plays the queue's [index]th track. */
export function jump(index: number) {
  if (index < 0 || index >= player.queue.length) return
  player.index = index
  saveQueue()
  loadCurrent(true)
  refill()
}

export function seek(seconds: number) {
  const s = Math.max(0, Math.min(player.duration || currentTrack()?.duration || 0, seconds))
  player.position = s
  player.seeks++
  if (!silent && loaded) audio.currentTime = s
}

export function cycleRepeat() {
  player.repeat = player.repeat === 'off' ? 'all' : player.repeat === 'all' ? 'one' : 'off'
}

/** Shuffles what is still to come, keeping the track playing where it is. */
// The queue as it was before shuffling, to go back to when shuffle is turned off.
let unshuffled: string[] | null = null

/**
 * Shuffles what is still to come, keeping the track playing where it is; turned off, puts the
 * queue back in its own order around the track playing, with whatever a radio added since at
 * the end.
 */
export function toggleShuffle() {
  player.shuffle = !player.shuffle
  if (player.shuffle) {
    unshuffled = [...player.queue]
    const head = player.queue.slice(0, player.index + 1)
    const rest = player.queue.slice(player.index + 1)
    for (let i = rest.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1))
      ;[rest[i], rest[j]] = [rest[j], rest[i]]
    }
    player.queue = head.concat(rest)
  } else if (unshuffled) {
    const playing = player.queue[player.index]
    const known = new Set(unshuffled)
    const order = unshuffled.concat(player.queue.filter((id) => !known.has(id)))
    player.queue = order
    player.index = Math.max(0, order.indexOf(playing))
    unshuffled = null
  }
  saveQueue()
}

// Every frame while sound plays, the position is read off the <audio> itself — its own time
// updates come a few times a second, and the progress would move in steps. The design's
// stand-in tracks, having no sound, count time themselves. Paused, the loop stops: a page that
// asks for every frame gets every frame drawn, playing or not.
let last = 0
let ticking = false
function tick(now: number) {
  if (!player.playing) {
    ticking = false
    last = 0
    return
  }
  if (!silent && loaded && !audio.paused && !audio.seeking) {
    player.position = audio.currentTime
  } else if (silent) {
    player.position += last ? (now - last) / 1000 : 0
    if (player.position >= (currentTrack()?.duration ?? 0)) next(true)
  }
  last = now
  requestAnimationFrame(tick)
}
$effect.root(() => {
  $effect(() => {
    if (player.playing && !ticking) {
      ticking = true
      requestAnimationFrame(tick)
    }
  })
})

// ------------------------------------------------------------------------------------ likes

export async function like(id: string) {
  const t = trackOf(id)
  const want = !player.liked[id]
  player.liked[id] = want
  try {
    await live.setLiked(t, want)
  } catch (e) {
    player.liked[id] = !want
    toast(`Не вышло ${want ? 'лайкнуть' : 'убрать лайк'}: ${reason(e)}`)
  }
}

export function markLiked(ids: string[]) {
  for (const id of ids) player.liked[id] = true
}

// ------------------------------------------------------------------------------------ radio

let refilling = false

/** Tops the queue up from its radio when only a couple of tracks are left. */
async function refill() {
  const radio = player.radio
  if (!radio || refilling || player.index < player.queue.length - 3) return
  refilling = true
  try {
    const have = new Set(player.queue)
    let more: string[] = []
    if (radio.kind === 'ya') {
      const heard = player.queue.slice(-20).map((id) => yandex.yaKey(trackOf(id)))
      const batch = await yandex.radioMore(radio.sessionId, heard)
      more = batch.tracks.filter((id) => !have.has(id))
      more.forEach((id) => (radio.batchOf[id] = batch.batchId))
    } else {
      more = (await live.relatedTo(trackOf(player.queue[player.queue.length - 1]))).filter((id) => !have.has(id))
    }
    if (more.length && player.radio === radio) {
      player.queue = player.queue.concat(more)
      saveQueue()
    }
  } catch (e) {
    toast(`Радио не продолжилось: ${reason(e)}`)
  } finally {
    refilling = false
  }
}

function radioTold(t: Track, type: 'trackFinished' | 'skip') {
  const radio = player.radio
  if (radio?.kind === 'ya' && t.ya) yandex.radioFeedback(radio.sessionId, radio.batchOf[t.id], type, t, Math.round(player.position))
}

function radioHeard(t: Track) {
  const radio = player.radio
  if (radio?.kind === 'ya' && t.ya) yandex.radioFeedback(radio.sessionId, radio.batchOf[t.id], 'trackStarted', t)
}

const waveSeeds = () => [
  'user:onyourwave',
  ...(app.waveMood ? [`settingMoodEnergy:${app.waveMood}`] : []),
  ...(app.waveMode ? [`settingDiversity:${app.waveMode}`] : []),
]

/** «Моя волна»: Yandex's endless radio for this listener, tuned by the mood and mode picked. */
export async function startWave() {
  try {
    let batch: yandex.RadioBatch
    try {
      batch = await yandex.radioStart(waveSeeds())
    } catch (e) {
      // Some tunings are refused; the wave plays untuned rather than not at all.
      if (waveSeeds().length === 1) throw e
      batch = await yandex.radioStart(['user:onyourwave'])
    }
    if (!batch.tracks.length) throw new Error('волна ничего не предложила')
    const batchOf = Object.fromEntries(batch.tracks.map((id) => [id, batch.batchId]))
    yandex.radioFeedback(batch.sessionId, batch.batchId, 'radioStarted')
    playQueue(batch.tracks, 0, 'Моя волна', 'ya-wave', { kind: 'ya', sessionId: batch.sessionId, batchOf }, true)
  } catch (e) {
    toast(`Волна не запустилась: ${reason(e)}`)
  }
}

export function tuneWave(key: 'mood' | 'mode', seed: string | null) {
  if (key === 'mood') app.waveMood = seed
  else app.waveMode = seed
  remember(key === 'mood' ? 'waveMood' : 'waveMode', seed)
  // A tuned wave is a session of its own: a wave playing starts over, tuned so.
  if (player.wave) startWave()
}

export const WAVE = { moods: WAVE_MOODS, modes: WAVE_MODES }

/** Radio from [id]: Yandex's track radio, SoundCloud's related tracks after it. */
export async function startRadio(id: string) {
  const t = trackOf(id)
  try {
    if (t.ya) {
      const batch = await yandex.radioStart([`track:${t.ya.trackId}`], [yandex.yaKey(t)])
      const rest = batch.tracks.filter((x) => x !== id)
      if (!rest.length) throw new Error('радио ничего не предложило')
      const batchOf = Object.fromEntries(rest.map((x) => [x, batch.batchId]))
      yandex.radioFeedback(batch.sessionId, batch.batchId, 'radioStarted')
      playQueue([id, ...rest], 0, `Радио: ${t.title}`, null, { kind: 'ya', sessionId: batch.sessionId, batchOf })
    } else if (t.sc || t.yt) {
      const rest = (await live.relatedTo(t)).filter((x) => x !== id)
      if (!rest.length) throw new Error('похожих не нашлось')
      playQueue([id, ...rest], 0, `Радио: ${t.title}`, null, { kind: 'related' })
    } else {
      toast('У этого трека нет радио')
    }
  } catch (e) {
    toast(`Радио не запустилось: ${reason(e)}`)
  }
}

/** Opens and plays a set; its tracks are fetched first when the set came without them. */
export async function playSet(id: string, shuffled = false) {
  try {
    const s = await live.loadSet(id)
    if (!s.tracks.length) throw new Error('он пустой')
    const ids = shuffled ? [...s.tracks].sort(() => Math.random() - 0.5) : s.tracks
    playQueue(ids, 0, s.title, s.id, s.kind === 'mix' || s.kind === 'station' ? radioForSet(ids) : null)
  } catch (e) {
    toast(`Не получилось включить: ${reason(e)}`)
  }
}

// A mix or station goes on with tracks related to its last, as the app's do.
const radioForSet = (ids: string[]): Radio | null => (trackOf(ids[0])?.sc || trackOf(ids[0])?.yt ? { kind: 'related' } : null)

// ------------------------------------------------------------------------------------ restore

const QUEUE_KEY = 'yc.queue'

function saveQueue() {
  try {
    const keep = player.queue.slice(Math.max(0, player.index - 50), player.index + 150)
    const index = player.index - Math.max(0, player.index - 50)
    localStorage.setItem(
      QUEUE_KEY,
      JSON.stringify({ tracks: keep.map(trackOf), index, context: player.context, contextId: player.contextId }),
    )
  } catch {
    // the queue just won't come back after a reload
  }
}

function restoreQueue() {
  try {
    const saved = JSON.parse(localStorage.getItem(QUEUE_KEY) ?? 'null') as { tracks: Track[]; index: number; context: string; contextId: string | null } | null
    if (!saved?.tracks?.length) return
    saved.tracks.forEach((t) => putTrack(t))
    player.queue = saved.tracks.map((t) => t.id)
    player.index = Math.min(saved.index, player.queue.length - 1)
    player.context = saved.context
    player.contextId = saved.contextId
    player.position = stored('position', 0)
    player.duration = currentTrack()?.duration ?? 0
  } catch {
    // a queue from an older version: start empty
  }
}
restoreQueue()

$effect.root(() => {
  $effect(() => remember('volume', player.volume))
})

// ------------------------------------------------------------------------------------ media keys

function mediaSession(t: Track) {
  if (!('mediaSession' in navigator)) return
  navigator.mediaSession.metadata = new MediaMetadata({
    title: t.title,
    artist: t.artists.map((a) => a.name).join(', '),
    album: t.album?.title ?? '',
    artwork: t.cover ? [{ src: new URL(reach(t.cover), location.href).href, sizes: '400x400' }] : [],
  })
}

if ('mediaSession' in navigator) {
  const ms = navigator.mediaSession
  ms.setActionHandler('play', () => toggle())
  ms.setActionHandler('pause', () => toggle())
  ms.setActionHandler('previoustrack', () => prev())
  ms.setActionHandler('nexttrack', () => next())
  ms.setActionHandler('seekto', (d) => d.seekTime != null && seek(d.seekTime))
  // Where the track is and whether it plays, for the system's media controls and the browser's
  // own player (Masaki's panel): told on every change of pace, the rest they count themselves.
  const tell = () => {
    ms.playbackState = audio.paused ? 'paused' : 'playing'
    if (!Number.isFinite(audio.duration) || audio.duration <= 0) return
    try {
      ms.setPositionState({ duration: audio.duration, position: Math.min(audio.currentTime, audio.duration), playbackRate: audio.playbackRate || 1 })
    } catch {
      // a stream without a known length
    }
  }
  for (const event of ['playing', 'pause', 'seeked', 'durationchange', 'ratechange']) audio.addEventListener(event, tell)
}
