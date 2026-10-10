// One way for the pages to ask any service: home, search, a set, an artist, the library. The
// design's stand-in items (ids without a service prefix) still answer from lib/mock.ts.

import { accounts } from './accounts.svelte'
import { artist, serviceOf, set as setOf, track, type Service, type Track, type TrackSet } from './catalog'
import * as mock from './mock'
import * as soundcloud from './services/soundcloud'
import * as yandex from './services/yandex'
import * as youtube from './services/youtube'

export interface Section {
  title: string
  subtitle?: string
  sets: string[]
  /** Songs to play in place, as YouTube Music's "Быстрый выбор". */
  tracks?: string[]
}

export interface SearchResult {
  best?: { type: 'artist' | 'track' | 'album' | 'playlist'; id: string }
  tracks: string[]
  artists: string[]
  albums: string[]
  playlists: string[]
}

export interface ArtistPage {
  artist: string
  top: string[]
  releases: string[]
  playlists: string[]
  similar: string[]
}

export interface Library {
  liked: string
  likedIds: string[]
  playlists: string[]
  artists: string[]
}

export const signedIn = (svc: Service) => (svc === 'sc' ? !!accounts.sc : svc === 'ya' ? !!accounts.ya : accounts.yt)

export async function home(svc: Service): Promise<Section[]> {
  if (svc === 'sc') return soundcloud.home()
  if (svc === 'ya') return yandex.home()
  return youtube.home()
}

export async function loadSet(id: string): Promise<TrackSet> {
  const svc = serviceOf(id)
  if (svc === 'sc') return soundcloud.loadSet(id)
  if (svc === 'ya') return yandex.loadSet(id)
  if (svc === 'yt') return youtube.loadSet(id)
  return setOf(id)
}

export async function loadArtist(id: string): Promise<ArtistPage> {
  const svc = serviceOf(id)
  if (svc === 'sc') return soundcloud.loadArtist(id)
  if (svc === 'ya') return yandex.loadArtist(id)
  if (svc === 'yt') {
    if (!id.startsWith('yt:a:')) throw new Error('У этого исполнителя нет страницы на YouTube Music')
    return youtube.loadArtist(id)
  }
  return {
    artist: id,
    top: mock.tracksOfArtist(id).map((t) => t.id),
    releases: mock.releasesOf(id).map((s) => s.id),
    playlists: [],
    similar: mock.similarTo(id).map((a) => a.id),
  }
}

export async function search(svc: Service, q: string): Promise<SearchResult> {
  if (svc === 'sc') {
    const r = await soundcloud.search(q)
    // SoundCloud has no "best": an artist named like the query, otherwise the first track.
    const named = r.artists.find((a) => sameName(q, artist(a).name))
    return { ...r, best: named ? { type: 'artist', id: named } : r.tracks[0] ? { type: 'track', id: r.tracks[0] } : undefined }
  }
  if (svc === 'ya') return yandex.search(q)
  return youtube.search(q)
}

function sameName(q: string, name: string): boolean {
  const norm = (s: string) => s.toLowerCase().replace(/[^\p{L}\p{N}]+/gu, '')
  return norm(name) === norm(q)
}

export async function library(svc: Service): Promise<Library> {
  if (svc === 'sc') return soundcloud.library()
  if (svc === 'ya') return yandex.library()
  return youtube.library()
}

export async function setLiked(t: Track, liked: boolean): Promise<void> {
  if (t.service === 'sc' && t.sc) return soundcloud.setLiked(t, liked)
  if (t.service === 'ya' && t.ya) return yandex.setLiked(t, liked)
  if (t.yt) return youtube.setLiked(t, liked)
}

export type Stream = { url: string; hls: boolean }

/** Where the track's sound is, or null for the design's stand-in tracks, which have none. */
export async function stream(t: Track): Promise<Stream | null> {
  if (t.sc) return soundcloud.stream(t)
  if (t.ya) return { url: await yandex.stream(t), hls: false }
  if (t.yt) return { url: await youtube.stream(t), hls: false }
  return null
}

/** [t]'s stream wouldn't play: true when the service has another way to give it, tried next time. */
export function streamFailed(t: Track): boolean {
  return t.yt ? youtube.streamFailed(t.yt.videoId) : false
}

/** Tracks that go on after [t] when its queue runs out: SoundCloud's related, YouTube's up next. */
export async function relatedTo(t: Track): Promise<string[]> {
  if (t.sc) return soundcloud.related(t)
  if (t.yt) return (await youtube.radio(t)).filter((id) => id !== t.id)
  return []
}

export { track }
