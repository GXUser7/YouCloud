// SoundCloud's private API (api-v2), as the app's SoundCloudApi uses it: an anonymous client_id
// scraped from soundcloud.com's own scripts, and the listener's OAuth token when signed in.
// Answers become catalog items (lib/catalog.ts); pages only ever see those.

import { tr } from '../i18n.svelte'
import { accounts, scToken, setSoundCloud } from '../accounts.svelte'
import { extension, extensionCall } from '../extension'
import {
  colorFor,
  compact,
  countWords,
  putArtist,
  putSet,
  putTrack,
  sets,
  tracks as catalogTracks,
  type ScTranscoding,
  type SetKind,
  type Track,
  type TrackSet,
} from '../catalog'
import { getJson, getText, HttpError, request } from '../net'

const API = 'https://api-v2.soundcloud.com/'
const APP_VERSION = '1778677443'
const CLIENT_ID_KEY = 'yc.sc.clientId'

// ---------------------------------------------------------------------------------- client_id

let clientIdCache: string | null = null
let scraping: Promise<string> | null = null
let scrapedAt = 0

// Every request in flight fails at once when the id rotates, or when the token expires; an id
// scraped a moment ago is as fresh as another scrape would give.
const MIN_SCRAPE_INTERVAL_MS = 15_000

function storedClientId(): string | null {
  try {
    return localStorage.getItem(CLIENT_ID_KEY)
  } catch {
    return null
  }
}

/**
 * Pulls a working client_id out of soundcloud.com's scripts. The bundles carry more than one
 * 32-character literal, so each candidate is tried with a real request before it is kept.
 */
async function scrapeClientId(): Promise<string> {
  const pages = ['https://soundcloud.com/discover', 'https://soundcloud.com/']
  const tried = new Set<string>()
  for (const page of pages) {
    let html: string
    try {
      html = await getText(page)
    } catch {
      continue
    }
    const scripts = [...html.matchAll(/<script[^>]+src=["']([^"']+)["']/g)]
      .map((m) => new URL(m[1], 'https://soundcloud.com/').href)
      .reverse() // the id lives in the last bundles far more often than the first
    for (const src of scripts) {
      let js: string
      try {
        js = await getText(src)
      } catch {
        continue
      }
      for (const m of js.matchAll(/client_id\s*[:=]\s*["']?([a-zA-Z0-9]{32})/g)) {
        const candidate = m[1]
        if (tried.has(candidate)) continue
        tried.add(candidate)
        if (await isUsable(candidate)) return candidate
      }
    }
  }
  throw new Error(tr('SoundCloud не отдал client_id'))
}

async function isUsable(id: string): Promise<boolean> {
  try {
    await getJson(`${API}search/tracks?q=music&limit=1&client_id=${id}`)
    return true
  } catch {
    return false
  }
}

export async function clientId(fresh = false): Promise<string> {
  if (fresh && clientIdCache && Date.now() - scrapedAt < MIN_SCRAPE_INTERVAL_MS) fresh = false
  if (!fresh) {
    clientIdCache ??= storedClientId()
    if (clientIdCache) return clientIdCache
  }
  scraping ??= scrapeClientId()
    .then((id) => {
      clientIdCache = id
      scrapedAt = Date.now()
      try {
        localStorage.setItem(CLIENT_ID_KEY, id)
      } catch {
        // kept for this visit only
      }
      return id
    })
    .finally(() => (scraping = null))
  return scraping
}

// ---------------------------------------------------------------------------------- transport

type Params = Record<string, string | number | undefined>

function apiUrl(path: string, params: Params, id: string): string {
  const url = new URL(path, API)
  for (const [k, v] of Object.entries(params)) if (v !== undefined) url.searchParams.set(k, String(v))
  url.searchParams.set('client_id', id)
  return url.href
}

/** SoundCloud refused [e] with one of [statuses], and not as DataDome does: no new id or token lifts that. */
const refused = (e: unknown, ...statuses: number[]): e is HttpError =>
  e instanceof HttpError && statuses.includes(e.status) && !e.body.includes('captcha') && !e.body.includes('datadome')

/**
 * A call on api-v2 with the client_id put on and the listener's token when there is one, healed as
 * the app's SoundCloudApi heals it. A rotated id fails with 401/403: a fresh one is scraped and the
 * call sent once more. An expired token fails with 401: the session is renewed (renewSession) and
 * the call sent again with the new token, so whatever waited on it carries on. A [token] given
 * outright is being checked, not used, and is never renewed.
 */
async function scCall(method: string, path: string, params: Params = {}, token?: string): Promise<Response> {
  const own = token === undefined
  const send = async (id: string, auth: string | undefined) => {
    const response = await request(apiUrl(path, params, id), {
      method,
      headers: auth ? { authorization: `OAuth ${auth}` } : {},
    })
    if (!response.ok) throw new HttpError(response.status, await response.text().catch(() => ''), apiUrl(path, {}, ''))
    return response
  }
  const id = await clientId()
  const used = own ? scToken() : token
  try {
    return await send(id, used)
  } catch (e) {
    if (!refused(e, 401, 403)) throw e
    let failure: HttpError = e
    const fresh = await clientId(true).catch(() => id)
    if (fresh !== id) {
      try {
        return await send(fresh, own ? scToken() : token)
      } catch (again) {
        if (!refused(again, 401)) throw again
        failure = again
      }
    }
    if (!own || !used || failure.status !== 401) throw failure
    // Another call may have renewed it while this one was out.
    if (scToken() === used && !(await renewSession(used))) throw new Error(tr('Сессия SoundCloud истекла: войди заново в Настройках'))
    return send(await clientId(), scToken())
  }
}

async function scGet<T = any>(path: string, params: Params = {}, token?: string): Promise<T> {
  return (await scCall('GET', path, params, token)).json()
}

// ---------------------------------------------------------------------------------- session

// A dead session isn't brought back by trying again at once, and every call in flight would
// otherwise start its own renewal.
const RENEW_COOLDOWN_MS = 60_000
let renewing: Promise<boolean> | null = null
let renewFailedAt = 0

/**
 * Renews an expired session in place, as the app's SoundCloudSessionRefresher does. The token the
 * site signed in with is soundcloud.com's own, and it expires; soundcloud.com renews it silently
 * for as long as its session in this browser lives. So the extension or Masaki looks where the
 * site keeps the current one: its oauth_token cookie, then soundcloud.com loaded out of sight,
 * which renews the session and hands over the token its own requests carry. False: signed out
 * there too, and only signing in again helps. Callers share one renewal.
 */
function renewSession(stale: string): Promise<boolean> {
  if (Date.now() - renewFailedAt < RENEW_COOLDOWN_MS) return Promise.resolve(false)
  renewing ??= (async () => {
    const token = await sessionToken(stale)
    if (!token) return false
    const me = await scGet<ScUser>('me', {}, token)
    if (!accounts.sc) return false
    if (accounts.sc.token !== stale) return true
    myIdCache = me.id
    setSoundCloud({ token, id: me.id, name: me.username, avatar: art(me.avatar_url, 't200x200') })
    return true
  })()
    .catch(() => false)
    .then((ok) => {
      if (!ok) renewFailedAt = Date.now()
      return ok
    })
    .finally(() => (renewing = null))
  return renewing
}

/** This browser's soundcloud.com session as a token other than [stale], from the extension or Masaki. */
async function sessionToken(stale: string): Promise<string | null> {
  if (!(await extension())) return null
  try {
    return await extensionCall<string | null>('sc-renew', 40_000, { stale })
  } catch {
    // An extension or Masaki from before 'sc-renew' can read soundcloud.com's cookie, no more.
    const cookie = await extensionCall<string | null>('sc-token').catch(() => null)
    return cookie && cookie !== stale ? cookie : null
  }
}

/** Asks who the listener is, so a lapsed session is renewed before a tap depends on it. */
export async function checkSession(): Promise<void> {
  if (scToken()) await scGet('me')
}

// ---------------------------------------------------------------------------------- shapes

export interface ScUser {
  id: number
  username: string
  avatar_url?: string
  followers_count?: number
  track_count?: number
  permalink_url?: string
  city?: string
}

export interface ScTrack {
  id: number
  kind?: string
  title?: string
  duration?: number
  full_duration?: number
  artwork_url?: string | null
  permalink_url?: string
  user?: ScUser
  publisher_metadata?: { artist?: string; explicit?: boolean } | null
  media?: { transcodings: ScTranscoding[] }
  track_authorization?: string
  policy?: string
  streamable?: boolean
}

interface ScPlaylist {
  id: number
  kind: string
  title: string
  artwork_url?: string | null
  calculated_artwork_url?: string | null
  user?: ScUser
  track_count?: number
  tracks?: ScTrack[]
  is_album?: boolean
  set_type?: string
  release_date?: string
  published_at?: string
  permalink_url?: string
}

interface ScSystemPlaylist {
  urn: string
  kind: string
  title: string
  short_title?: string
  description?: string
  short_description?: string
  artwork_url?: string | null
  calculated_artwork_url?: string | null
  tracks?: ScTrack[]
  permalink_url?: string
}

/** Artwork in a size worth showing: SoundCloud hands out its 100-pixel "large" by default. */
export const art = (url: string | null | undefined, size = 't500x500') => (url ? url.replace(/-large\.(jpg|png)/, `-${size}.$1`) : null)

const hasDetails = (t: ScTrack) => !!t.title

export function putScTrack(t: ScTrack): string {
  const id = `sc:t:${t.id}`
  const artistName = t.publisher_metadata?.artist?.trim() || t.user?.username || 'SoundCloud'
  return putTrack({
    id,
    service: 'sc',
    title: t.title ?? tr('Без названия'),
    artists: [{ id: `sc:u:${t.user?.id}`, name: artistName }],
    duration: Math.round((t.full_duration ?? t.duration ?? 0) / 1000),
    cover: art(t.artwork_url ?? t.user?.avatar_url),
    color: colorFor(id),
    explicit: !!t.publisher_metadata?.explicit,
    link: t.permalink_url,
    playable: t.streamable !== false && t.policy !== 'BLOCK',
    sc: { transcodings: t.media?.transcodings ?? [], auth: t.track_authorization },
  })
}

export function putScUser(u: ScUser): string {
  const id = `sc:u:${u.id}`
  return putArtist({
    id,
    name: u.username,
    service: 'sc',
    image: art(u.avatar_url),
    color: colorFor(id),
    followers: u.followers_count != null ? `${compact(u.followers_count)} ${countWords(u.followers_count, 'подписчик', 'подписчика', 'подписчиков')}` : undefined,
    link: u.permalink_url,
  })
}

function putScPlaylist(p: ScPlaylist): string {
  const id = `sc:pl:${p.id}`
  const album = p.is_album || ['album', 'ep', 'single', 'compilation'].includes(p.set_type ?? '')
  const kind: SetKind = album ? (p.set_type === 'single' ? 'single' : 'album') : 'playlist'
  const full = p.tracks?.filter(hasDetails) ?? []
  const year = (p.release_date ?? p.published_at)?.slice(0, 4)
  return putSet({
    id,
    kind,
    service: 'sc',
    title: p.title,
    subtitle: p.user?.username ?? '',
    cover: art(p.artwork_url ?? p.calculated_artwork_url ?? full[0]?.artwork_url),
    color: colorFor(id),
    tracks: [],
    total: p.track_count,
    year: year ? Number(year) : undefined,
    artist: p.user ? { id: `sc:u:${p.user.id}`, name: p.user.username } : undefined,
    link: p.permalink_url,
  })
}

function systemKind(urn: string): SetKind {
  if (urn.includes('artist-stations') || urn.includes('track-stations')) return 'station'
  if (urn.includes('trending') || urn.includes('charts')) return 'genre'
  return 'mix'
}

function putScSystem(p: ScSystemPlaylist): string {
  const id = `sc:sys:${p.urn}`
  return putSet({
    id,
    kind: systemKind(p.urn),
    service: 'sc',
    title: p.short_title || p.title,
    subtitle: p.short_description || p.description || 'SoundCloud',
    cover: art(p.calculated_artwork_url ?? p.artwork_url),
    color: colorFor(id),
    tracks: [],
    total: p.tracks?.length,
    link: p.permalink_url,
  })
}

/** Full tracks for [stubs]: sets carry only ids past their first few tracks. */
async function filled(stubs: ScTrack[]): Promise<string[]> {
  const known = new Map(stubs.filter(hasDetails).map((t) => [t.id, t]))
  const missing = stubs.filter((t) => !known.has(t.id)).map((t) => t.id)
  for (let i = 0; i < missing.length; i += 50) {
    const chunk = await scGet<ScTrack[]>('tracks', { ids: missing.slice(i, i + 50).join(',') }).catch(() => [])
    chunk.forEach((t) => known.set(t.id, t))
  }
  return stubs.flatMap((s) => {
    const t = known.get(s.id)
    return t ? [putScTrack(t)] : []
  })
}

// ---------------------------------------------------------------------------------- pages

export interface Section {
  title: string
  sets: string[]
}

const SECTION_TITLES: Record<string, string> = {
  'your-moods': tr('Твои миксы'),
  'daily-drops': tr('Свежее каждый день'),
  stations: tr('Станции'),
  'discover-with-station': tr('Станции'),
  'trending-by-genre-playlists': tr('В тренде по жанрам'),
  'personalised-curated-global': tr('Подборки SoundCloud'),
  buzzing: tr('Артисты, за которыми стоит следить'),
  'charts-top': tr('Чарты'),
  'charts-trending': tr('Чарты'),
}

/** Home: the mixes made for the listener when signed in, stations, curated sets, the charts. */
export async function home(): Promise<Section[]> {
  const answer = await scGet<{ collection: { title?: string; tracking_feature_name?: string; items?: { collection: any[] } }[] }>('mixed-selections', {
    limit: 10,
    offset: 0,
    linked_partitioning: 1,
    app_version: APP_VERSION,
    app_locale: 'en',
  })
  const sections: Section[] = []
  for (const selection of answer.collection) {
    const items = selection.items?.collection ?? []
    const ids = items.flatMap((item) => {
      if (item.kind === 'system-playlist') return [putScSystem(item)]
      if (item.kind === 'playlist') return [putScPlaylist(item)]
      return []
    })
    if (!ids.length) continue
    const feature = selection.tracking_feature_name ?? ''
    sections.push({ title: SECTION_TITLES[feature] ?? selection.title ?? 'SoundCloud', sets: ids })
  }
  // The listener's own mixes first, as the app shows them.
  return sections.sort((a, b) => Number(b.title === tr('Твои миксы')) - Number(a.title === tr('Твои миксы')))
}

export async function loadSet(id: string): Promise<TrackSet> {
  if (id.startsWith('sc:sys:')) {
    const urn = id.slice('sc:sys:'.length)
    const p = await scGet<ScSystemPlaylist>(`system-playlists/${encodeURIComponent(urn)}`, { app_version: APP_VERSION, app_locale: 'en' })
    putScSystem(p)
    const tracks = await filled(p.tracks ?? [])
    return finish(id, tracks)
  }
  if (id === 'sc:likes') {
    const me = await myId()
    const page = await scGet<{ collection: { track?: ScTrack }[] }>(`users/${me}/track_likes`, { limit: 200 })
    const tracks = page.collection.flatMap((x) => (x.track ? [putScTrack(x.track)] : []))
    putSet({ id, kind: 'liked', service: 'sc', title: tr('Нравится'), subtitle: 'SoundCloud', color: '#ff7043', tracks: [], cover: null })
    return finish(id, tracks)
  }
  const plId = id.slice('sc:pl:'.length)
  const p = await scGet<ScPlaylist>(`playlists/${plId}`)
  putScPlaylist(p)
  const tracks = await filled(p.tracks ?? [])
  return finish(id, tracks)
}

function finish(id: string, tracks: string[]): TrackSet {
  const s = sets.get(id)!
  const done = { ...s, tracks, loaded: true, total: tracks.length }
  putSet(done)
  return done
}

export interface ArtistPage {
  artist: string
  top: string[]
  releases: string[]
  playlists: string[]
  similar: string[]
}

export async function loadArtist(id: string): Promise<ArtistPage> {
  const userId = id.slice('sc:u:'.length)
  const [user, top, albums, playlists, related] = await Promise.allSettled([
    scGet<ScUser>(`users/${userId}`),
    scGet<{ collection: ScTrack[] }>(`users/${userId}/toptracks`, { limit: 10 }),
    scGet<{ collection: ScPlaylist[] }>(`users/${userId}/albums`, { limit: 20 }),
    scGet<{ collection: ScPlaylist[] }>(`users/${userId}/playlists_without_albums`, { limit: 20 }),
    scGet<{ collection: ScUser[] }>(`users/${userId}/relatedartists`, { limit: 12 }),
  ])
  if (user.status === 'rejected') throw user.reason
  putScUser(user.value)
  let topIds = top.status === 'fulfilled' ? top.value.collection.map(putScTrack) : []
  if (!topIds.length) {
    const recent = await scGet<{ collection: ScTrack[] }>(`users/${userId}/tracks`, { limit: 10 }).catch(() => ({ collection: [] }))
    topIds = recent.collection.map(putScTrack)
  }
  return {
    artist: id,
    top: topIds,
    releases: albums.status === 'fulfilled' ? albums.value.collection.map(putScPlaylist) : [],
    playlists: playlists.status === 'fulfilled' ? playlists.value.collection.map(putScPlaylist) : [],
    similar: related.status === 'fulfilled' ? related.value.collection.map(putScUser) : [],
  }
}

export interface SearchResult {
  tracks: string[]
  artists: string[]
  albums: string[]
  playlists: string[]
}

export async function search(q: string): Promise<SearchResult> {
  const [tracks, users, albums, playlists] = await Promise.allSettled([
    scGet<{ collection: ScTrack[] }>('search/tracks', { q, limit: 20 }),
    scGet<{ collection: ScUser[] }>('search/users', { q, limit: 10 }),
    scGet<{ collection: ScPlaylist[] }>('search/albums', { q, limit: 12 }),
    scGet<{ collection: ScPlaylist[] }>('search/playlists_without_albums', { q, limit: 12 }),
  ])
  if (tracks.status === 'rejected' && users.status === 'rejected') throw tracks.reason
  return {
    tracks: tracks.status === 'fulfilled' ? tracks.value.collection.map(putScTrack) : [],
    artists: users.status === 'fulfilled' ? users.value.collection.map(putScUser) : [],
    albums: albums.status === 'fulfilled' ? albums.value.collection.map(putScPlaylist) : [],
    playlists: playlists.status === 'fulfilled' ? playlists.value.collection.map(putScPlaylist) : [],
  }
}

// ---------------------------------------------------------------------------------- account

export async function me(token: string): Promise<ScUser> {
  return scGet<ScUser>('me', {}, token)
}

let myIdCache: number | null = null
async function myId(): Promise<number> {
  myIdCache ??= (await scGet<ScUser>('me')).id
  return myIdCache
}

export interface Library {
  liked: string
  likedIds: string[]
  playlists: string[]
  artists: string[]
}

export async function library(): Promise<Library> {
  const likes = await loadSet('sc:likes')
  const [all, followings] = await Promise.allSettled([
    scGet<{ collection: { type: string; playlist?: ScPlaylist; system_playlist?: ScSystemPlaylist }[] }>('me/library/all', { limit: 50 }),
    scGet<{ collection: ScUser[] }>(`users/${await myId()}/followings`, { limit: 50 }),
  ])
  const playlists =
    all.status === 'fulfilled'
      ? all.value.collection.flatMap((x) => (x.playlist ? [putScPlaylist(x.playlist)] : x.system_playlist ? [putScSystem(x.system_playlist)] : []))
      : []
  return {
    liked: likes.id,
    likedIds: likes.tracks,
    playlists,
    artists: followings.status === 'fulfilled' ? followings.value.collection.map(putScUser) : [],
  }
}

export async function setLiked(track: Track, liked: boolean): Promise<void> {
  const trackId = track.id.slice('sc:t:'.length)
  await scCall(liked ? 'PUT' : 'DELETE', `users/${await myId()}/track_likes/${trackId}`)
}

/** Tracks like [track], as SoundCloud plays them after it. */
export async function related(track: Track): Promise<string[]> {
  const trackId = track.id.slice('sc:t:'.length)
  const page = await scGet<{ collection: ScTrack[] }>(`tracks/${trackId}/related`, { limit: 20 })
  return page.collection.map(putScTrack)
}

// ---------------------------------------------------------------------------------- playback

export interface Stream {
  url: string
  hls: boolean
}

/** The best stream SoundCloud lets this listener have: never the encrypted (DRM) ones. */
export async function stream(track: Track): Promise<Stream> {
  // A track known only by its id — from «Любимое», liked in the app — is asked for in full first.
  if (!track.sc?.transcodings.length) track = catalogTracks.get(putScTrack(await scGet<ScTrack>(`tracks/${track.id.slice('sc:t:'.length)}`)))!
  const usable = (track.sc?.transcodings ?? []).filter((t) => !t.format.protocol.includes('encrypted'))
  const whole = usable.filter((t) => !t.snipped)
  const choices = whole.length ? whole : usable
  const rank = (t: ScTranscoding) =>
    t.preset.startsWith('aac_160') && t.format.protocol === 'hls' ? 0 : t.format.protocol === 'progressive' ? 1 : t.format.protocol === 'hls' && !t.preset.startsWith('opus') ? 2 : 3
  const pick = [...choices].sort((a, b) => rank(a) - rank(b))[0]
  if (!pick) throw new Error(tr('SoundCloud не даёт этот трек'))
  const resolved = await scGet<{ url: string }>(pick.url.replace(API, ''), { track_authorization: track.sc?.auth })
  return { url: resolved.url, hls: pick.format.protocol === 'hls' }
}

/** Whatever a soundcloud.com page is — a track, a set, a profile — as catalog ids. */
export async function resolve(url: string): Promise<{ kind: 'track' | 'set' | 'artist'; id: string } | null> {
  const r = await scGet<any>('resolve', { url })
  if (r.kind === 'track') return { kind: 'track', id: putScTrack(r) }
  if (r.kind === 'playlist') return { kind: 'set', id: putScPlaylist(r) }
  if (r.kind === 'user') return { kind: 'artist', id: putScUser(r) }
  return null
}

// Kept for the connection check in Settings.
export async function searchTracks(q: string, limit = 20): Promise<ScTrack[]> {
  const page = await scGet<{ collection: ScTrack[] }>('search/tracks', { q, limit })
  return page.collection
}
