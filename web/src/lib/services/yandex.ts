// Yandex Music's API as the Android client calls it (the app's YandexMusicApi): OAuth token when
// signed in, no Origin. Yandex serves only listeners' own IPs, so these go through the extension
// (lib/net.ts routes them). Answers become catalog items (lib/catalog.ts).

import { yaToken } from '../accounts.svelte'
import {
  colorFor,
  compact,
  countWords,
  putArtist,
  putSet,
  putTrack,
  sets,
  type SetKind,
  type Track,
  type TrackSet,
} from '../catalog'
import { md5 } from '../md5'
import { getJson, getText, HttpError, postForm, postJson } from '../net'

const API = 'https://api.music.yandex.net/'
const SALT = 'XGRlBW9FXlekgbPrRHuSiA'
// The Android app's key for signed requests (MarshalX/yandex-music-api, utils/sign_request.py).
const SIGN_KEY = 'p93jhgh689SBReK6ghtw62'

type Params = Record<string, string | number | boolean | undefined>

function apiUrl(path: string, params: Params = {}): string {
  const url = new URL(path, API)
  for (const [k, v] of Object.entries(params)) if (v !== undefined) url.searchParams.set(k, String(v))
  return url.href
}

const auth = (token = yaToken()): Record<string, string> => (token ? { authorization: `OAuth ${token}` } : {})

async function yaGet<T = any>(path: string, params: Params = {}, headers: Record<string, string> = {}): Promise<T> {
  return (await getJson<{ result: T }>(apiUrl(path, params), { headers: { ...auth(), ...headers } })).result
}

async function yaPostForm<T = any>(path: string, form: Record<string, string>): Promise<T> {
  return (await postForm<{ result: T }>(apiUrl(path), form, { headers: auth() })).result
}

async function yaPostJson<T = any>(path: string, body: unknown): Promise<T> {
  return (await postJson<{ result: T }>(apiUrl(path), body, { headers: auth() })).result
}

// ---------------------------------------------------------------------------------- shapes

interface YaArtist {
  id: number | string
  name: string
  cover?: { uri?: string }
  ogImage?: string
  counts?: { tracks?: number; directAlbums?: number }
  likesCount?: number
}

interface YaAlbum {
  id: number
  title: string
  type?: string
  year?: number
  coverUri?: string
  artists?: YaArtist[]
  trackCount?: number
  volumes?: YaTrack[][]
}

export interface YaTrack {
  id: string | number
  realId?: string
  title: string
  version?: string
  durationMs?: number
  coverUri?: string
  artists?: YaArtist[]
  albums?: YaAlbum[]
  available?: boolean
  contentWarning?: string
  /** 'UGC' for a file a listener uploaded themselves. */
  trackSource?: string
}

interface YaPlaylist {
  uid?: number
  kind: number
  title: string
  description?: string
  trackCount?: number
  cover?: { type?: string; uri?: string; itemsUri?: string[] }
  ogImage?: string
  owner?: { uid: number; login?: string; name?: string }
  tracks?: { id: number | string; track?: YaTrack; albumId?: number }[]
  generatedPlaylistType?: string
}

export const cover = (uri: string | undefined | null, size = '400x400') => (uri ? 'https://' + uri.replace('%%', size) : null)

export function putYaTrack(t: YaTrack): string {
  const trackId = String(t.realId ?? t.id).split(':')[0]
  const album = t.albums?.[0]
  const id = `ya:t:${trackId}`
  return putTrack({
    id,
    service: 'ya',
    title: t.version ? `${t.title} (${t.version})` : t.title,
    artists: (t.artists ?? []).map((a) => ({ id: `ya:a:${a.id}`, name: a.name })),
    duration: Math.round((t.durationMs ?? 0) / 1000),
    cover: cover(t.coverUri ?? album?.coverUri),
    color: colorFor(id),
    album: album ? { id: `ya:al:${album.id}`, title: album.title } : undefined,
    explicit: t.contentWarning === 'explicit',
    link: album ? `https://music.yandex.ru/album/${album.id}/track/${trackId}` : `https://music.yandex.ru/track/${trackId}`,
    playable: t.available !== false,
    ya: { trackId, albumId: album ? String(album.id) : undefined, ugc: t.trackSource === 'UGC' || undefined },
  })
}

function putYaAlbum(a: YaAlbum): string {
  const id = `ya:al:${a.id}`
  const first = a.artists?.[0]
  return putSet({
    id,
    kind: a.type === 'single' ? 'single' : 'album',
    service: 'ya',
    title: a.title,
    subtitle: (a.artists ?? []).map((x) => x.name).join(', '),
    cover: cover(a.coverUri),
    color: colorFor(id),
    tracks: [],
    total: a.trackCount,
    year: a.year,
    artist: first ? { id: `ya:a:${first.id}`, name: first.name } : undefined,
    link: `https://music.yandex.ru/album/${a.id}`,
  })
}

function putYaPlaylist(p: YaPlaylist, kind: SetKind = 'playlist'): string {
  const uid = p.uid ?? p.owner?.uid
  const id = `ya:pl:${uid}:${p.kind}`
  const pic = p.cover?.uri ?? p.cover?.itemsUri?.[0]
  return putSet({
    id,
    kind: p.generatedPlaylistType ? 'personal' : kind,
    service: 'ya',
    title: p.title,
    subtitle: p.description?.replace(/\s+/g, ' ').slice(0, 90) || (p.trackCount != null ? `${p.trackCount} ${countWords(p.trackCount, 'трек', 'трека', 'треков')}` : p.owner?.name ?? ''),
    cover: cover(pic) ?? (p.ogImage ? cover(p.ogImage) : null),
    color: colorFor(id),
    tracks: [],
    total: p.trackCount,
    link: `https://music.yandex.ru/users/${p.owner?.login ?? uid}/playlists/${p.kind}`,
  })
}

function putYaArtist(a: YaArtist): string {
  const id = `ya:a:${a.id}`
  const likes = a.likesCount
  return putArtist({
    id,
    name: a.name,
    service: 'ya',
    image: cover(a.cover?.uri ?? a.ogImage, '400x400'),
    color: colorFor(id),
    followers: likes != null ? `${compact(likes)} ${countWords(likes, 'слушатель', 'слушателя', 'слушателей')} в избранном` : undefined,
    albums: a.counts?.directAlbums,
    link: `https://music.yandex.ru/artist/${a.id}`,
  })
}

/** Full tracks for "id:albumId" ids, a hundred at a time. */
async function tracksById(ids: string[]): Promise<string[]> {
  const out: string[] = []
  for (let i = 0; i < ids.length; i += 100) {
    const chunk = await yaPostForm<YaTrack[]>('tracks', { 'track-ids': ids.slice(i, i + 100).join(',') })
    out.push(...chunk.map(putYaTrack))
  }
  return out
}

// ---------------------------------------------------------------------------------- pages

export interface Section {
  title: string
  sets: string[]
}

/**
 * Home: the playlists made for the listener (of the day, Дежавю, Премьера, Тайник…), new
 * releases and playlists — as the app's YandexLanding reads them — and the chart. Signed out,
 * Yandex gives only the chart.
 */
export async function home(): Promise<Section[]> {
  const signed = !!yaToken()
  const [feed, releases, playlists, chart] = await Promise.allSettled([
    signed ? yaGet<{ generatedPlaylists?: { ready?: boolean; data?: YaPlaylist }[] }>('feed') : Promise.resolve(null),
    signed ? yaGet<{ newReleases: number[] }>('landing3/new-releases') : Promise.resolve(null),
    signed ? yaGet<{ newPlaylists: { uid: number; kind: number }[] }>('landing3/new-playlists') : Promise.resolve(null),
    yaGet<{ chart?: YaPlaylist }>('landing3/chart'),
  ])
  const sections: Section[] = []
  if (feed.status === 'fulfilled' && feed.value) {
    const made = (feed.value.generatedPlaylists ?? []).flatMap((g) => (g.ready !== false && g.data ? [putYaPlaylist(g.data, 'personal')] : []))
    if (made.length) sections.push({ title: 'Собрано для тебя', sets: made })
  }
  if (releases.status === 'fulfilled' && releases.value?.newReleases.length) {
    const albums = await yaPostForm<YaAlbum[]>('albums', { 'album-ids': releases.value.newReleases.slice(0, 40).join(',') }).catch(() => [])
    if (albums.length) sections.push({ title: 'Новые релизы', sets: albums.map(putYaAlbum) })
  }
  if (playlists.status === 'fulfilled' && playlists.value?.newPlaylists.length) {
    const ids = playlists.value.newPlaylists.slice(0, 40).map((p) => `${p.uid}:${p.kind}`)
    const list = await yaPostForm<YaPlaylist[]>('playlists/list', { 'playlist-ids': ids.join(',') }).catch(() => [])
    if (list.length) sections.push({ title: 'Новые плейлисты', sets: list.map((p) => putYaPlaylist(p)) })
  }
  if (chart.status === 'fulfilled' && chart.value.chart) {
    // The chart comes whole, tracks and all: the set is ready to play from home.
    const p = chart.value.chart
    const id = putYaPlaylist({ ...p, description: 'Самое популярное в Яндекс Музыке' })
    finish(id, (p.tracks ?? []).filter((x) => x.track).map((x) => putYaTrack(x.track!)))
    sections.push({ title: 'Чарт', sets: [id] })
  }
  if (!sections.length) {
    const failure = [feed, releases, playlists, chart].find((r) => r.status === 'rejected')
    if (failure?.status === 'rejected') throw failure.reason
  }
  return sections
}

export async function loadSet(id: string): Promise<TrackSet> {
  if (id.startsWith('ya:al:')) {
    const album = await yaGet<YaAlbum>(`albums/${id.slice('ya:al:'.length)}/with-tracks`)
    putYaAlbum(album)
    return finish(id, (album.volumes ?? []).flat().map(putYaTrack))
  }
  if (id === 'ya:likes') {
    const uid = await myUid()
    const answer = await yaGet<{ library: { tracks: { id: string; albumId?: string }[] } }>(`users/${uid}/likes/tracks`)
    const ids = answer.library.tracks.slice(0, 500).map((t) => (t.albumId ? `${t.id}:${t.albumId}` : t.id))
    putSet({ id, kind: 'liked', service: 'ya', title: 'Мне нравится', subtitle: 'Яндекс Музыка', color: '#ef5350', tracks: [], cover: null })
    return finish(id, await tracksById(ids))
  }
  const [, uid, kind] = id.slice('ya:'.length).split(':')
  const p = await yaGet<YaPlaylist>(`users/${uid}/playlists/${kind}`)
  putYaPlaylist(p, sets.get(id)?.kind ?? 'playlist')
  const full = (p.tracks ?? []).filter((x) => x.track).map((x) => putYaTrack(x.track!))
  const ids = full.length ? full : await tracksById((p.tracks ?? []).map((x) => (x.albumId ? `${x.id}:${x.albumId}` : String(x.id))))
  return finish(id, ids)
}

function finish(id: string, tracks: string[]): TrackSet {
  const done = { ...sets.get(id)!, tracks, loaded: true, total: tracks.length }
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
  const info = await yaGet<{ artist: YaArtist; popularTracks?: YaTrack[]; albums?: YaAlbum[]; alsoAlbums?: YaAlbum[]; similarArtists?: YaArtist[]; playlists?: YaPlaylist[] }>(
    `artists/${id.slice('ya:a:'.length)}/brief-info`,
  )
  putYaArtist(info.artist)
  return {
    artist: id,
    top: (info.popularTracks ?? []).map(putYaTrack),
    releases: [...(info.albums ?? []), ...(info.alsoAlbums ?? [])].map(putYaAlbum),
    playlists: (info.playlists ?? []).map((p) => putYaPlaylist(p)),
    similar: (info.similarArtists ?? []).map(putYaArtist),
  }
}

export interface SearchResult {
  best?: { type: 'artist' | 'track' | 'album' | 'playlist'; id: string }
  tracks: string[]
  artists: string[]
  albums: string[]
  playlists: string[]
}

export async function search(text: string): Promise<SearchResult> {
  const r = await yaGet<{
    best?: { type: string; result: any }
    tracks?: { results: YaTrack[] }
    artists?: { results: YaArtist[] }
    albums?: { results: YaAlbum[] }
    playlists?: { results: YaPlaylist[] }
  }>('search', { text, type: 'all', page: 0, nocorrect: false, 'playlist-in-best': true })
  let best: SearchResult['best']
  if (r.best?.result) {
    if (r.best.type === 'artist') best = { type: 'artist', id: putYaArtist(r.best.result) }
    else if (r.best.type === 'track') best = { type: 'track', id: putYaTrack(r.best.result) }
    else if (r.best.type === 'album') best = { type: 'album', id: putYaAlbum(r.best.result) }
    else if (r.best.type === 'playlist') best = { type: 'playlist', id: putYaPlaylist(r.best.result) }
  }
  return {
    best,
    tracks: (r.tracks?.results ?? []).map(putYaTrack),
    artists: (r.artists?.results ?? []).map(putYaArtist),
    albums: (r.albums?.results ?? []).map(putYaAlbum),
    playlists: (r.playlists?.results ?? []).map((p) => putYaPlaylist(p)),
  }
}

// ---------------------------------------------------------------------------------- account

export interface AccountStatus {
  uid: number
  name: string
  plus: boolean
}

export async function account(token = yaToken()): Promise<AccountStatus> {
  const r = await getJson<{ result: { account: { uid?: number; displayName?: string; login?: string; fullName?: string }; plus?: { hasPlus?: boolean } } }>(
    apiUrl('account/status'),
    { headers: auth(token) },
  )
  const a = r.result.account
  if (!a.uid) throw new Error('Яндекс не узнал этот токен')
  return { uid: a.uid, name: a.displayName || a.fullName || a.login || 'Яндекс', plus: !!r.result.plus?.hasPlus }
}

let uidCache: number | null = null
async function myUid(): Promise<number> {
  uidCache ??= (await account()).uid
  return uidCache
}

export interface Library {
  liked: string
  likedIds: string[]
  playlists: string[]
  artists: string[]
}

export async function library(): Promise<Library> {
  const uid = await myUid()
  const likes = await loadSet('ya:likes')
  const [playlists, artists] = await Promise.allSettled([
    yaGet<YaPlaylist[]>(`users/${uid}/playlists/list`),
    yaGet<YaArtist[]>(`users/${uid}/likes/artists`, { 'with-timestamps': false }),
  ])
  return {
    liked: likes.id,
    likedIds: likes.tracks,
    playlists: playlists.status === 'fulfilled' ? playlists.value.map((p) => putYaPlaylist(p)) : [],
    artists: artists.status === 'fulfilled' ? artists.value.map(putYaArtist) : [],
  }
}

const trackKey = (t: Track) => (t.ya?.albumId ? `${t.ya.trackId}:${t.ya.albumId}` : t.ya!.trackId)

export async function setLiked(track: Track, liked: boolean): Promise<void> {
  const uid = await myUid()
  await yaPostForm(`users/${uid}/likes/tracks/${liked ? 'add-multiple' : 'remove'}`, { 'track-ids': trackKey(track) })
}

// ---------------------------------------------------------------------------------- radio

/**
 * Rotor: Yandex Music's radio. A session starts from seeds — "user:onyourwave" for «Моя волна»,
 * tuned by a mood and a mode, or "track:123" for a track's radio — and hands out batches; what is
 * heard, skipped or finished goes back to it and steers what comes next.
 */
export interface RadioBatch {
  sessionId: string
  batchId?: string
  tracks: string[]
}

interface RotorAnswer {
  radioSessionId?: string
  batchId?: string
  sequence?: { track?: YaTrack }[]
}

const batchOf = (r: RotorAnswer): RadioBatch => ({
  sessionId: r.radioSessionId ?? '',
  batchId: r.batchId,
  tracks: (r.sequence ?? []).flatMap((x) => (x.track && x.track.available !== false ? [putYaTrack(x.track)] : [])),
})

export async function radioStart(seeds: string[], heard: string[] = []): Promise<RadioBatch> {
  const r = await yaPostJson<RotorAnswer>('rotor/session/new', { seeds, queue: heard, includeTracksInResponse: true })
  if (!r.radioSessionId) throw new Error('радио не запустилось')
  return batchOf(r)
}

export async function radioMore(sessionId: string, heard: string[]): Promise<RadioBatch> {
  return batchOf(await yaPostJson<RotorAnswer>(`rotor/session/${sessionId}/tracks`, { queue: heard }))
}

export function radioFeedback(sessionId: string, batchId: string | undefined, type: string, track?: Track, played?: number) {
  const event: Record<string, unknown> = { type, timestamp: new Date().toISOString() }
  if (track) event.trackId = trackKey(track)
  if (played != null) event.totalPlayedSeconds = played
  return yaPostJson(`rotor/session/${sessionId}/feedback`, { event, batchId }).catch(() => {})
}

export const yaKey = (track: Track) => trackKey(track)

// ---------------------------------------------------------------------------------- playback

/** A direct link to the track's MP3, signed as Yandex's own player signs it. */
export async function stream(track: Track): Promise<string> {
  let infos: { codec: string; bitrateInKbps: number; downloadInfoUrl: string; preview?: boolean }[]
  try {
    infos = await yaGet(`tracks/${track.ya!.trackId}/download-info`)
  } catch (e) {
    // A friend's own upload (or a track Yandex holds back from this account): said plainly.
    if (e instanceof HttpError && e.status === 403 && e.body.includes('no-rights')) {
      throw new Error(
        track.ya!.ugc
          ? 'это файл, который владелец сам загрузил в Яндекс Музыку, — Яндекс даёт слушать его только ему'
          : 'Яндекс не даёт слушать этот трек с твоего аккаунта — похоже, это чужая загрузка или трек закрыт',
      )
    }
    throw e
  }
  const best = infos.filter((i) => i.codec === 'mp3').sort((a, b) => b.bitrateInKbps - a.bitrateInKbps)[0] ?? infos[0]
  if (!best) throw new Error('Яндекс не даёт этот трек')
  const xml = await getText(best.downloadInfoUrl, { headers: auth() })
  const tag = (name: string) => xml.match(new RegExp(`<${name}>(.*?)</${name}>`))?.[1] ?? ''
  const [host, path, ts, s] = ['host', 'path', 'ts', 's'].map(tag)
  if (!host || !path) throw new Error('Яндекс не дал ссылку на трек')
  return `https://${host}/get-mp3/${md5(SALT + path.slice(1) + s)}/${ts}${path}`
}

// ---------------------------------------------------------------------------------- lyrics

async function lyricsSign(trackId: string, timestamp: number): Promise<string> {
  const key = await crypto.subtle.importKey('raw', new TextEncoder().encode(SIGN_KEY), { name: 'HMAC', hash: 'SHA-256' }, false, ['sign'])
  const digest = await crypto.subtle.sign('HMAC', key, new TextEncoder().encode(`${trackId}${timestamp}`))
  return btoa(String.fromCharCode(...new Uint8Array(digest)))
}

/** The track's synced lyrics as LRC, or null when Yandex has none. Needs a token. */
export async function lyrics(track: Track): Promise<string | null> {
  if (!yaToken() || !track.ya) return null
  const ts = Math.floor(Date.now() / 1000)
  try {
    const r = await yaGet<{ downloadUrl?: string }>(
      `tracks/${track.ya.trackId}/lyrics`,
      { format: 'LRC', timeStamp: ts, sign: await lyricsSign(track.ya.trackId, ts) },
      // The signature is only checked for the Android client, so the request says it is one.
      { 'x-yandex-music-client': 'YandexMusicAndroid/24023621' },
    )
    return r.downloadUrl ? await getText(r.downloadUrl) : null
  } catch {
    return null
  }
}

/** A track by its id, "id:albumId" when the album is known. */
export async function trackByKey(key: string): Promise<string | null> {
  return (await tracksById([key]))[0] ?? null
}

// Kept for the connection check in Settings.
export async function searchTracks(text: string): Promise<{ title: string; artists: { name: string }[] }[]> {
  const r = await yaGet<{ tracks?: { results: YaTrack[] } }>('search', { text, type: 'track', page: 0 })
  return (r.tracks?.results ?? []).map((t) => ({ title: t.title, artists: t.artists ?? [] }))
}
