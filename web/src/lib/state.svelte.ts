// What the site shows and plays.
//
// The player is an <audio>: SoundCloud's streams through hls.js (or straight, when progressive),
// Yandex's as a signed MP3 link, a YouTube broadcast as its HLS playlist, joined where it is now.
// The next tracks of the queue are made ready in <audio>s of their own while one plays, and the
// one that comes takes over from it, as the app fetches them ahead.
// A radio — Yandex's Rotor or SoundCloud's related tracks — tops the queue up before it runs out,
// and Rotor hears what was finished, skipped or disliked, as in the app. The queue survives a
// reload. The design's stand-in tracks have no sound, so for them the player only counts time.

import { tr } from './i18n.svelte'
import Hls from 'hls.js'
import { accounts } from './accounts.svelte'
import { untrack } from 'svelte'
import { putTrack, serviceName, track as trackOf, type Service, type Track } from './catalog'
import { favorites, setFavorite } from './favorites.svelte'
import { BridgeLoader } from './hlsBridge'
import * as live from './live'
import { directFailed, reach, readable, readingRefused } from './media.svelte'
import { WAVE_MODES, WAVE_MOODS } from './mock'
import { HttpError } from './net'
import * as yandex from './services/yandex'
import { freshRadioTracks, radioEndType, radioHistory, radioQueue, rememberRadioTrack } from './radioHistory'
import { reason, toast } from './toast.svelte'

export type RouteName = 'home' | 'search' | 'liked' | 'friends' | 'settings' | 'set' | 'artist'
export type Route = { name: RouteName; id?: string }
export type Panel = 'friends' | 'queue' | 'lyrics'
export type ThemeMode = 'system' | 'light' | 'dark'

function parseHash(): Route {
  const [name, id] = location.hash.replace(/^#\/?/, '').split('/')
  // «Моя музыка» lives on the service's page now («Любимое»); old links land there.
  const known: RouteName[] = ['home', 'search', 'liked', 'friends', 'settings', 'set', 'artist']
  return known.includes(name as RouteName) ? { name: name as RouteName, id: id ? decodeURIComponent(id) : undefined } : { name: 'home' }
}

export function stored<T>(key: string, fallback: T): T {
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

/** How «Волна в плеере» looks behind the full player (components/PlayerWave.svelte). */
export const WAVE_LOOKS = [
  { value: 'dunes', label: tr('Дюны') },
  { value: 'shape', label: tr('Форма') },
  { value: 'tide', label: tr('Прилив') },
  { value: 'silk', label: tr('Шёлк') },
] as const
export type WaveLook = (typeof WAVE_LOOKS)[number]['value']

/** How the full player is laid out (components/NowPlaying.svelte, the others in components/player). */
export const PLAYER_LOOKS = [
  { value: 'classic', label: tr('Классика'), hint: tr('Обложка, управление и текст или очередь рядом') },
  { value: 'poster', label: tr('Афиша'), hint: tr('Окно в цвете обложки, название на всю ширину') },
  { value: 'carousel', label: tr('Карусель'), hint: tr('Очередь лентой обложек, текущая — крупно в центре') },
  { value: 'shape', label: tr('Форма'), hint: tr('Обложка в фигуре, перемотка — кольцо вокруг неё') },
] as const
export type PlayerLook = (typeof PLAYER_LOOKS)[number]['value']

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
  // Behind the full player, a matte wave in the cover's colours; moving to the sound, or only
  // flowing, as [waveHears] says.
  playerWave: stored('playerWave', true),
  waveLook: stored<WaveLook>('waveLook', 'dunes'),
  waveHears: stored('waveHears', true),
  playerLook: stored<PlayerLook>('playerLook', 'classic'),
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
  | { kind: 'ya'; sessionId: string; batchOf: Record<string, string | undefined>; uid: number }
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
  /** What plays is a broadcast: no length, no seeking — «В эфире» where the progress is. */
  live: false,
})

export const currentTrack = (): Track | undefined => (player.queue.length ? trackOf(player.queue[player.index]) : undefined)

let audio = sound()
/** The <audio> playing now (one made ready ahead takes over when its track comes): what the full
 *  player's wave listens to. */
export const playingSound = (): HTMLAudioElement => audio
let hls: Hls | null = null
let loaded: string | null = null
let silent = false
let loadSeq = 0
/** The stream playing now, as the service gave it, and how many times it has been asked again. */
let streamUrl: string | null = null
let attempt = 0
let wantPlay = false
let transport = 0
/** When the playing <audio> was paused last: a broadcast paused for long is joined anew. */
let pausedAt = 0

/**
 * An <audio> of the player's: the one playing, or one a track to come is made ready in. What it
 * says counts only while it is the one playing.
 */
function sound(): HTMLAudioElement {
  const el = new Audio()
  el.preload = 'auto'
  const on = (type: string, heard: () => void) => el.addEventListener(type, () => el === audio && heard())
  on('play', () => (player.playing = true))
  on('playing', () => {
    player.loading = false
    passedOver = 0
    const t = currentTrack()
    if (t && loaded === t.id) radioHeard(t)
    listenClock()
    refill()
  })
  on('waiting', () => {
    listenClock(false)
    player.loading = true
  })
  on('pause', () => {
    listenClock(false)
    checkpointPlay(true)
    if (!el.ended) player.playing = false
    pausedAt = Date.now()
  })
  on('timeupdate', () => {
    listenClock()
    checkpointPlay()
    player.position = el.currentTime
    // A broadcast is joined where it is now, not where it was left.
    remember('position', player.live ? 0 : Math.floor(el.currentTime))
  })
  on('durationchange', () => {
    // A broadcast's is how much of it the browser can reach, which grows: not a length.
    if (!player.live && Number.isFinite(el.duration) && el.duration > 0) player.duration = el.duration
  })
  on('seeking', () => listenClock(false))
  on('seeked', () => listenClock())
  on('ended', () => next(true))
  on('error', () => {
    const t = currentTrack()
    if (t && loaded === t.id && !hls) recover(t, new Error(el.error?.code === 4 ? tr('сервис не отдал звук по ссылке') : tr('поток оборвался')))
  })
  for (const event of ['playing', 'pause', 'seeked', 'durationchange', 'ratechange']) on(event, tellSession)
  return el
}

function setVolume() {
  audio.volume = player.muted ? 0 : player.volume
}

/** Puts [s] into [el]: through hls.js where the browser doesn't play HLS itself, or where its pieces
 *  come through the extension ([bridge]). A broadcast starts where it is now, whatever [startAt] says. */
function attach(el: HTMLAudioElement, s: live.Stream, startAt: number, fatal: (e: Error) => void): Hls | null {
  if (s.live) startAt = 0
  if (s.hls && (s.bridge || !el.canPlayType('application/vnd.apple.mpegurl')) && Hls.isSupported()) {
    const h = new Hls({
      enableWorker: true,
      // -1, for a broadcast: at its live edge, not at the start of what it keeps.
      startPosition: s.live ? -1 : startAt,
      liveDurationInfinity: true,
      ...(s.bridge
        ? { loader: BridgeLoader }
        : {
            // The playlist names its pieces by SoundCloud's own addresses: each goes the way that works.
            xhrSetup: (xhr: XMLHttpRequest, url: string) => {
              const via = reach(url)
              if (via !== url) xhr.open('GET', via, true)
            },
          }),
    })
    h.on(Hls.Events.ERROR, (_, data) => {
      if (data.fatal) fatal(new Error(data.details))
    })
    // A broadcast's playlist with every quality (YouTube's own clients give that one): sound is all
    // that's wanted, so no more picture than 360p, which comes with the full 128 kbit/s of sound.
    if (s.live)
      h.on(Hls.Events.MANIFEST_PARSED, () => {
        const sized = h.levels.map((l, i) => [l.height, i] as const).filter(([height]) => height > 0 && height <= 360)
        if (sized.length && sized.length < h.levels.length) h.autoLevelCapping = sized.sort((a, b) => b[0] - a[0])[0][1]
      })
    h.loadSource(reach(s.url))
    h.attachMedia(el)
    return h
  }
  const url = reach(s.url)
  // Asked for in CORS where that is allowed, so the full player's wave may hear it (lib/hearing.ts).
  el.crossOrigin = readable(url) ? 'anonymous' : null
  el.src = url
  if (startAt) el.currentTime = startAt
  return null
}

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
  player.live = !!t?.live
  player.position = player.live ? 0 : startAt
  player.duration = t?.duration ?? 0
  if (!t) {
    player.playing = false
    return
  }
  mediaSession(t)
  player.loading = true
  const ready = retry ? null : take(t.id)
  if (ready && (await takeOver(ready, startAt, autoplay, seq))) return
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
    // Whether it is a broadcast is known for sure once its sound is found.
    player.live = !!s.live
    if (s.live) player.duration = player.position = 0
    streamUrl = s.url
    hls = attach(audio, s, startAt, (e) => seq === loadSeq && recover(t, e))
    loaded = t.id
    if (autoplay) await audio.play()
  } catch (e) {
    if (seq === loadSeq && !(e instanceof DOMException && e.name === 'AbortError')) failed(t, e)
  }
}

/**
 * A stream that won't play is asked for once more another way before the listener hears of it:
 * SoundCloud's through the Worker when its CDN is blocked here, without CORS when it was asked in
 * CORS (for the full player's wave), YouTube's from another client (a link one client hands out
 * can be refused when fetched).
 */
function recover(t: Track, e: unknown) {
  if (attempt < 4 && (directFailed(streamUrl) || readingRefused(streamUrl) || live.streamFailed(t))) {
    loadCurrent(wantPlay || !audio.paused, audio.currentTime || player.position, attempt + 1)
    return
  }
  failed(t, e)
}

/** YouTube tracks passed over in a row because this browser can't get their sound. */
let passedOver = 0

function failed(t: Track, e: unknown) {
  player.loading = false
  player.playing = false
  if (e instanceof DOMException && e.name === 'NotAllowedError') {
    toast(tr('Браузер ждёт нажатия: нажми ▶'))
    return
  }
  // In a plain browser YouTube keeps some tracks' sound to itself (Masaki gets it with yt-dlp): the
  // queue goes on with the ones that play instead of stopping at each one that won't — five in a
  // row at most, so a queue where nothing plays doesn't run through to its end.
  if (t.yt && wantPlay && passedOver < 5 && player.index < player.queue.length - 1) {
    passedOver++
    toast(tr('«%s» не играет в этом браузере — дальше', t.title))
    next()
    return
  }
  toast(tr('«%s» не играет: %s', t.title, reason(e)))
}

$effect.root(() => {
  $effect(setVolume)
})

// ------------------------------------------------------------------------------------ ahead

/**
 * The next [AHEAD] tracks of the queue, made ready while one plays, as the app fetches them ahead
 * into its cache: each one's stream asked for, and its sound buffering in an <audio> of its own —
 * as much as the browser keeps for one that waits, enough to start on. Skipped to or reached, the
 * track takes over from that <audio> and starts at once, instead of asking the service then (for
 * Yandex and YouTube, through the extension, that alone takes a second or two) and loading after.
 * One at a time, in the order they will come, once the track playing has started; those no longer
 * next are let go.
 */
const AHEAD = 2
// A stream link kept longer than this may have run out: the track is asked for again.
const FRESH = 20 * 60_000

type Ready = { id: string; el: HTMLAudioElement | null; hls: Hls | null; url: string; at: number; gone: boolean; done: Promise<void> }

const ahead = new Map<string, Ready>()
let aheadPlan = 0
let planned = ''

/** What comes after the track playing, as the player will come to it. */
function upcoming(): string[] {
  const ids: string[] = []
  const n = player.queue.length
  for (let step = 1; step <= Math.min(AHEAD, n - 1); step++) {
    let i = player.index + step
    if (i >= n) {
      if (player.repeat !== 'all') break
      i -= n
    }
    const id = player.queue[i]
    const t = trackOf(id)
    // The design's stand-in tracks have no sound to get ready.
    // Nor has a broadcast: held ready, it would fall behind the moment it is in.
    if (id !== player.queue[player.index] && !ids.includes(id) && (t?.sc || t?.ya || t?.yt) && !t.live) ids.push(id)
  }
  return ids
}

async function planAhead(want: string[]) {
  const plan = ++aheadPlan
  for (const r of [...ahead.values()]) if (!want.includes(r.id)) letGo(r)
  for (const id of want) {
    if (plan !== aheadPlan) return
    let r = ahead.get(id)
    if (r && Date.now() - r.at > FRESH) {
      letGo(r)
      r = undefined
    }
    await (r ?? prepare(trackOf(id))).done
  }
}

function prepare(t: Track): Ready {
  const r: Ready = { id: t.id, el: null, hls: null, url: '', at: Date.now(), gone: false, done: Promise.resolve() }
  ahead.set(t.id, r)
  r.done = (async () => {
    let s: live.Stream | null = null
    try {
      s = await live.stream(t)
    } catch {
      // Reached, it is asked for again, and the listener hears why it won't play.
    }
    if (r.gone) return
    if (!s || s.live) {
      letGo(r)
      return
    }
    const el = sound()
    r.el = el
    r.url = s.url
    // Refused before it plays, it is let go, and the way that failed isn't taken when it comes:
    // SoundCloud's through the Worker, YouTube's from another client.
    const refused = () => {
      if (!directFailed(r.url) && !readingRefused(r.url)) live.streamFailed(t)
      letGo(r)
    }
    r.hls = attach(el, s, 0, (e) => (el === audio ? loaded === t.id && recover(t, e) : refused()))
    el.addEventListener('error', () => el !== audio && refused())
    // The next one waits until this one has enough to start on, or has failed.
    await new Promise<void>((settled) => {
      const timer = setTimeout(settled, 20_000)
      for (const event of ['canplaythrough', 'error', 'emptied'])
        el.addEventListener(event, () => (clearTimeout(timer), settled()), { once: true })
    })
  })()
  return r
}

function letGo(r: Ready) {
  r.gone = true
  if (ahead.get(r.id) === r) ahead.delete(r.id)
  if (!r.el || r.el === audio) return
  r.hls?.destroy()
  r.el.pause()
  r.el.removeAttribute('src')
  r.el.load()
}

/** [id] made ready ahead, taken out of the waiting: null when it isn't, or isn't any more. */
function take(id: string): Ready | null {
  const r = ahead.get(id)
  if (!r) return null
  ahead.delete(id)
  if (!r.el || Date.now() - r.at > FRESH) {
    letGo(r)
    return null
  }
  return r
}

/**
 * [r]'s <audio> becomes the player's and plays. False when the browser won't play it — some let an
 * <audio> start by itself, without a click, only once it has played before — and the track loads
 * into the one that played until now, the usual way.
 */
async function takeOver(r: Ready, startAt: number, autoplay: boolean, seq: number): Promise<boolean> {
  const before = audio
  audio = r.el!
  hls = r.hls
  streamUrl = r.url
  loaded = r.id
  setVolume()
  if (Number.isFinite(audio.duration) && audio.duration > 0) player.duration = audio.duration
  try {
    if (startAt) audio.currentTime = startAt
    if (autoplay) await audio.play()
    else player.loading = false
    return true
  } catch (e) {
    // Paused before it started, or another track asked for: nothing to load again.
    if (seq !== loadSeq || (e instanceof DOMException && e.name === 'AbortError')) return true
    detach()
    audio = before
    setVolume()
    return false
  }
}

// Once the track playing has started — before, what comes next would take its bandwidth — and
// whenever what comes next changes. Not where the listener asks the browser to save data.
$effect.root(() => {
  $effect(() => {
    if (!player.playing || player.loading || (navigator as any).connection?.saveData) return
    const want = upcoming()
    const key = want.join('\n')
    if (key === planned) return
    planned = key
    // What the services are asked on the way is none of this effect's business.
    untrack(() => planAhead(want))
  })
})

/** Plays [ids] from [start]; [radio] keeps it going past its end. */
export function playQueue(ids: string[], start = 0, context = '', contextId: string | null = null, radio: Radio | null = null, wave = false) {
  if (!ids.length) return
  radioTold(currentTrack(), 'skip')
  radioRequest++
  transport++
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

/** Paused longer than this, a broadcast is joined where it is now when it plays again. */
const LIVE_REJOIN_MS = 20_000

export function toggle() {
  transport++
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
  if (audio.paused) {
    // A broadcast paused for a while plays on from where it is now, as a radio does.
    if (player.live && Date.now() - pausedAt > LIVE_REJOIN_MS) {
      loadCurrent(true)
      return
    }
    audio.play().catch((e) => failed(t, e))
  } else audio.pause()
}

/** The next track; [disliked], the one left was disliked — the radio heard that, not a skip. */
export function next(auto = false, disliked = false) {
  const intent = ++transport
  const t = currentTrack()
  if (!t) return
  // A broadcast that ended can't be played again: the next one comes.
  if (auto && player.repeat === 'one' && !player.live) {
    radioTold(t, 'trackFinished')
    seek(0)
    if (!silent) audio.play().catch(() => {})
    return
  }
  if (!disliked) radioTold(t, radioEndType(auto))
  if (player.index < player.queue.length - 1) player.index++
  else if (player.repeat === 'all') player.index = 0
  else {
    audio.pause()
    player.playing = false
    const radio = player.radio
    const index = player.index
    if (radio) {
      refill().then(() => {
        if (transport !== intent || player.radio !== radio || player.index !== index || player.playing || player.index >= player.queue.length - 1) return
        player.index++
        saveQueue()
        loadCurrent(true)
      })
    }
    return
  }
  saveQueue()
  loadCurrent(true)
  refill()
}

export function prev() {
  transport++
  if (player.position > 3) {
    seek(0)
    return
  }
  if (player.index === 0) return
  radioTold(currentTrack(), 'skip')
  player.index--
  saveQueue()
  loadCurrent(true)
}

/** Plays the queue's [index]th track. */
export function jump(index: number) {
  if (index < 0 || index >= player.queue.length || index === player.index) return
  transport++
  radioTold(currentTrack(), 'skip')
  player.index = index
  saveQueue()
  loadCurrent(true)
  refill()
}

export function seek(seconds: number) {
  // A broadcast has nowhere to go: it plays as it happens.
  if (player.live) return
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

/**
 * What a heart shows. Signed in to YouCloud it is the account's «Любимое», as in the app, so a
 * track has the same heart on the phone and here; signed out, the service's own like.
 */
export const isLiked = (id: string) => (favorites.on ? !!favorites.has[id] : !!player.liked[id])

export async function like(id: string) {
  const t = trackOf(id)
  const want = !isLiked(id)
  // In «Любимое» it is kept on every device (and downloaded on the phone); liked on the service
  // besides, where signed in there, as the app does.
  if (favorites.on) {
    try {
      await setFavorite(t, want)
    } catch (e) {
      toast(want ? tr('Не вышло добавить в «Любимое»: %s', reason(e)) : tr('Не вышло убрать из «Любимого»: %s', reason(e)))
      return
    }
    if (!live.signedIn(t.service)) return
    radioLiked(t, want)
    player.liked[id] = want
    live.setLiked(t, want).catch((e) => {
      toast(want ? tr('Добавлено в «Любимое», но %s лайк не принял: %s', serviceName(t.service), reason(e)) : tr('Убрано из «Любимого», но %s лайк не снял: %s', serviceName(t.service), reason(e)))
    })
    return
  }
  player.liked[id] = want
  try {
    await live.setLiked(t, want)
    radioLiked(t, want)
  } catch (e) {
    player.liked[id] = !want
    toast(want ? tr('Не вышло лайкнуть: %s', reason(e)) : tr('Не вышло убрать лайк: %s', reason(e)))
  }
}

export function markLiked(ids: string[]) {
  for (const id of ids) player.liked[id] = true
}

// Tracks disliked here: no radio brings them back — on SoundCloud, which has no dislike, that is
// all a dislike can do.
const disliked = new Set(stored<string[]>('disliked', []))
const welcome = (ids: string[]) => ids.filter((id) => !disliked.has(id))

/**
 * «Не нравится» on the track playing, while a radio plays — in place of shuffle, as in the app:
 * Rotor hears a dislike, Yandex marks it «Не рекомендовать», YouTube Music takes a thumb down, and
 * no radio here plays it again. Then the next one plays.
 */
export function dislike() {
  const t = currentTrack()
  const radio = player.radio
  if (!t || !radio) return
  disliked.add(t.id)
  remember('disliked', [...disliked].slice(-500))
  radioTold(t, 'dislike')
  const told = live.dislike(t)
  if (told) {
    told.catch((e) => toast(tr('%s не принял «Не нравится»: %s', serviceName(t.service), reason(e))))
    toast(tr('Больше не будет в рекомендациях'))
  } else {
    toast(tr('Радио больше не поставит этот трек'))
  }
  next(false, true)
}

// ------------------------------------------------------------------------------------ radio

let heard = radioHistory(stored('radioHeard', []))
let radioRequest = 0
const refilling = new WeakMap<Radio, Promise<void>>()

type YaRadio = Extract<Radio, { kind: 'ya' }>
type RadioPlay = {
  track: Track
  uid: number
  radio: YaRadio | null
  batchId?: string
  playId: string
  timestamp: string
  from: string
  played: number
  position: number
  clock: number
  running: boolean
}
let radioPlay: RadioPlay | null = null

// Feedback and play reports stay ordered, retain their original session, and survive a reload.
type NewRadioReport =
  | { kind: 'feedback'; sessionId: string; batchId?: string; type: yandex.RadioEvent; track?: Track; played?: number; timestamp: string }
  | { kind: 'play'; play: yandex.PlayReport }
type RadioReport = NewRadioReport & { uid: number }
let reports = stored<RadioReport[]>('radioReports', []).filter((item) => typeof item?.uid === 'number')
let reporting = false
let reportTimer: ReturnType<typeof setTimeout> | undefined

function report(item: NewRadioReport, uid = accounts.ya?.uid) {
  if (!uid) return
  reports.push({ ...item, uid })
  remember('radioReports', reports)
  flushReports()
}

let reportWait = Promise.resolve()
function flushReports(): Promise<void> {
  if (reporting) return reportWait
  reportWait = drainReports()
  return reportWait
}

async function drainReports() {
  if (!reports.length || !live.signedIn('ya')) return
  reporting = true
  clearTimeout(reportTimer)
  try {
    while (accounts.ya) {
      const index = reports.findIndex((item) => item.uid === accounts.ya!.uid)
      if (index < 0) break
      const item = reports[index]
      try {
        if (item.kind === 'play') await yandex.plays(item.play)
        else await yandex.radioFeedback(item.sessionId, item.batchId, item.type, item.track, item.played, item.timestamp)
      } catch (e) {
        if (!(e instanceof HttpError) || ![400, 404, 410, 422].includes(e.status)) throw e
        // An expired session or rejected payload must not block feedback for every later session.
        const rejected = stored<RadioReport[]>('radioRejectedReports', [])
        remember('radioRejectedReports', [...rejected, item].slice(-100))
        console.warn('Yandex rejected a listening report', e)
      }
      reports.splice(index, 1)
      remember('radioReports', reports)
    }
  } catch {
    // A temporary bridge or network failure must not silently erase the listener's feedback.
    reportTimer = setTimeout(flushReports, 30_000)
  } finally {
    reporting = false
  }
}
addEventListener('online', flushReports)
$effect.root(() => {
  $effect(() => {
    if (accounts.ya) untrack(flushReports)
  })
})

function feedback(radio: YaRadio, type: yandex.RadioEvent, track?: Track, played?: number, batchId = track ? radio.batchOf[track.id] : undefined) {
  report({ kind: 'feedback', sessionId: radio.sessionId, batchId, type, track, played, timestamp: new Date().toISOString() }, radio.uid)
}

/** Count audible time, not the seek position or the time spent paused/buffering. */
function listenClock(running?: boolean) {
  const p = radioPlay
  if (!p) return
  const now = performance.now()
  if (p.running) p.played += Math.max(0, now - p.clock) / 1000
  p.clock = now
  p.position = audio.currentTime
  p.running = running ?? (!audio.paused && !audio.ended && !audio.seeking && audio.readyState >= 3)
}

let checkpointAt = 0
function checkpointPlay(force = false) {
  if (!radioPlay || (!force && Date.now() - checkpointAt < 5000)) return
  checkpointAt = Date.now()
  remember('radioActivePlay', radioPlay)
}

function playReport(p: RadioPlay) {
  report({ kind: 'play', play: {
    trackId: p.track.ya!.trackId,
    albumId: p.track.ya!.albumId,
    from: p.from,
    playId: p.playId,
    timestamp: p.timestamp,
    totalPlayedSeconds: Math.round(p.played),
    endPositionSeconds: Math.round(p.position),
    trackLengthSeconds: p.track.duration,
  } }, p.uid)
}

function radioLiked(t: Track, liked: boolean) {
  const p = radioPlay
  if (p?.radio && p.track.id === t.id) feedback(p.radio, liked ? 'like' : 'unlike', t, undefined, p.batchId)
}

function radioTold(t: Track | undefined, type: 'trackFinished' | 'skip' | 'dislike') {
  const p = radioPlay
  if (!p || t?.id !== p.track.id) return
  listenClock()
  if (p.radio) feedback(p.radio, type, p.track, Math.round(p.played), p.batchId)
  playReport(p)
  radioPlay = null
  remember('radioActivePlay', null)
}

function radioHeard(t: Track) {
  if (!t.ya || radioPlay?.track.id === t.id) return
  const radio = player.radio?.kind === 'ya' ? player.radio : null
  const timestamp = new Date().toISOString()
  radioPlay = {
    track: t, uid: radio?.uid ?? accounts.ya?.uid ?? 0, radio, batchId: radio?.batchOf[t.id], playId: crypto.randomUUID(), timestamp,
    from: radio ? yandex.WAVE_FROM : 'web',
    played: 0, position: audio.currentTime, clock: performance.now(), running: true,
  }
  // Only playback counts: preload, loading errors and unplayed batch tails never enter history.
  heard = rememberRadioTrack(heard, t.id, yandex.yaKey(t))
  remember('radioHeard', heard)
  if (radio) feedback(radio, 'trackStarted', t)
  playReport(radioPlay)
  checkpointPlay(true)
}

// A closed tab reports its final counters; a crashed tab leaves a checkpoint for the next visit.
const interruptedPlay = stored<RadioPlay | null>('radioActivePlay', null)
if (interruptedPlay?.track?.ya && interruptedPlay.uid) {
  if (interruptedPlay.radio) feedback(interruptedPlay.radio, 'skip', interruptedPlay.track, Math.round(interruptedPlay.played), interruptedPlay.batchId)
  playReport(interruptedPlay)
  remember('radioActivePlay', null)
}
addEventListener('pagehide', () => radioTold(currentTrack(), 'skip'))
addEventListener('pageshow', () => {
  const t = currentTrack()
  if (t && !audio.paused && loaded === t.id) radioHeard(t)
})

// «Любимое» plays the listener's own favourites, which they hear everywhere else too: there a
// track is held back only for a few hours after it was heard, or every batch would be thrown away.
const FAVOURITES_REST = 3 * 60 * 60_000

function batchExclusions(queued: () => string[], favourites = () => player.wave && app.waveMode === 'favorite') {
  return () => {
    const since = favourites() ? Date.now() - FAVOURITES_REST : -Infinity
    return { history: heard.filter((h) => h.at >= since), queued: queued(), disliked }
  }
}

/** Tops the queue up from its radio when only a couple of tracks are left. */
function refill(): Promise<void> {
  const radio = player.radio
  if (!radio || player.index < player.queue.length - 3) return Promise.resolve()
  const pending = refilling.get(radio)
  if (pending) return pending
  const filling = (async () => {
    try {
      let more: string[]
      if (radio.kind === 'ya') {
        await flushReports()
        if (accounts.ya?.uid !== radio.uid || player.radio !== radio) return
        const batch = await freshRadioTracks(null, () => yandex.radioMore(radio.sessionId, radioQueue(heard)), batchExclusions(() => player.queue))
        more = batch.tracks
        Object.assign(radio.batchOf, batch.batchOf)
      } else {
        const have = new Set(player.queue)
        more = welcome(await live.relatedTo(trackOf(player.queue[player.queue.length - 1]))).filter((id) => !have.has(id))
      }
      if (more.length && player.radio === radio) {
        player.queue = player.queue.concat(more)
        saveQueue()
      }
    } catch (e) {
      if (player.radio === radio) toast(tr('Радио не продолжилось: %s', reason(e)))
    } finally {
      refilling.delete(radio)
    }
  })()
  refilling.set(radio, filling)
  return filling
}

const waveSeeds = () => [
  'user:onyourwave',
  ...(app.waveMood ? [`settingMoodEnergy:${app.waveMood}`] : []),
  ...(app.waveMode ? [`settingDiversity:${app.waveMode}`] : []),
]

/** «Моя волна»: Yandex's endless radio for this listener, tuned by the mood and mode picked. */
export async function startWave(retune = false) {
  if (!retune && player.wave && player.radio?.kind === 'ya') {
    if (!player.playing) toggle()
    return
  }
  const request = ++radioRequest
  const uid = accounts.ya?.uid ?? 0
  const seeds = waveSeeds()
  try {
    let batch: yandex.RadioBatch
    try {
      batch = await yandex.radioStart(seeds, radioQueue(heard))
    } catch (e) {
      // Some tunings are refused; the wave plays untuned rather than not at all.
      if (seeds.length === 1 || request !== radioRequest) throw e
      batch = await yandex.radioStart(['user:onyourwave'], radioQueue(heard))
    }
    if (request !== radioRequest || (accounts.ya?.uid ?? 0) !== uid) return
    const radio: YaRadio = { kind: 'ya', sessionId: batch.sessionId, batchOf: {}, uid }
    feedback(radio, 'radioStarted', undefined, undefined, batch.batchId)
    const fresh = await freshRadioTracks(batch, () => yandex.radioMore(radio.sessionId, radioQueue(heard)), batchExclusions(() => player.queue.slice(player.index, player.index + 1), () => app.waveMode === 'favorite'))
    if (request !== radioRequest || (accounts.ya?.uid ?? 0) !== uid) return
    if (!fresh.tracks.length) throw new Error(tr('волна ничего не предложила'))
    radio.batchOf = fresh.batchOf
    playQueue(fresh.tracks, 0, tr('Моя волна'), 'ya-wave', radio, true)
  } catch (e) {
    if (request === radioRequest) toast(tr('Волна не запустилась: %s', reason(e)))
  }
}

export function tuneWave(key: 'mood' | 'mode', seed: string | null) {
  if (key === 'mood') app.waveMood = seed
  else app.waveMode = seed
  remember(key === 'mood' ? 'waveMood' : 'waveMode', seed)
  // A tuned wave is a session of its own: a wave playing starts over, tuned so.
  if (player.wave) startWave(true)
}

export const WAVE = { moods: WAVE_MOODS, modes: WAVE_MODES }

/** Radio from [id]: Yandex's track radio, SoundCloud's related tracks after it. */
export async function startRadio(id: string) {
  const t = trackOf(id)
  const request = ++radioRequest
  const uid = accounts.ya?.uid ?? 0
  try {
    if (t.ya) {
      const batch = await yandex.radioStart([`track:${t.ya.trackId}`], radioQueue(heard))
      if (request !== radioRequest || (accounts.ya?.uid ?? 0) !== uid) return
      const radio: YaRadio = { kind: 'ya', sessionId: batch.sessionId, batchOf: batch.tracks.includes(id) ? { [id]: batch.batchId } : {}, uid }
      feedback(radio, 'radioStarted', undefined, undefined, batch.batchId)
      const fresh = await freshRadioTracks(batch, () => yandex.radioMore(radio.sessionId, radioQueue(heard)), batchExclusions(() => [id], () => false))
      if (request !== radioRequest || (accounts.ya?.uid ?? 0) !== uid) return
      if (!fresh.tracks.length) throw new Error(tr('радио ничего не предложило'))
      Object.assign(radio.batchOf, fresh.batchOf)
      playQueue([id, ...fresh.tracks], 0, tr('Радио: %s', t.title), null, radio)
    } else if (t.sc || t.yt) {
      const rest = welcome(await live.relatedTo(t)).filter((x) => x !== id)
      if (request !== radioRequest || (accounts.ya?.uid ?? 0) !== uid) return
      if (!rest.length) throw new Error(tr('похожих не нашлось'))
      playQueue([id, ...rest], 0, tr('Радио: %s', t.title), null, { kind: 'related' })
    } else {
      toast(tr('У этого трека нет радио'))
    }
  } catch (e) {
    if (request === radioRequest) toast(tr('Радио не запустилось: %s', reason(e)))
  }
}

/** Opens and plays a set; its tracks are fetched first when the set came without them. */
export async function playSet(id: string, shuffled = false) {
  try {
    const s = await live.loadSet(id)
    if (!s.tracks.length) throw new Error(tr('он пустой'))
    const ids = shuffled ? [...s.tracks].sort(() => Math.random() - 0.5) : s.tracks
    playQueue(ids, 0, s.title, s.id, s.kind === 'mix' || s.kind === 'station' ? radioForSet(ids) : null)
  } catch (e) {
    toast(tr('Не получилось включить: %s', reason(e)))
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
    player.live = !!currentTrack()?.live
    player.position = player.live ? 0 : stored('position', 0)
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
}

// Where the track is and whether it plays, for the system's media controls and the browser's own
// player (Masaki's panel): told on every change of pace, the rest they count themselves.
function tellSession() {
  if (!('mediaSession' in navigator)) return
  const ms = navigator.mediaSession
  ms.playbackState = audio.paused ? 'paused' : 'playing'
  if (!Number.isFinite(audio.duration) || audio.duration <= 0) return
  try {
    ms.setPositionState({ duration: audio.duration, position: Math.min(audio.currentTime, audio.duration), playbackRate: audio.playbackRate || 1 })
  } catch {
    // a stream without a known length
  }
}
