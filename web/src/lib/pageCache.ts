// Two-tier page cache (L1 RAM + L2 localStorage) for opened albums, playlists, mixes, and artist
// pages across all 4 services. Static albums stay fresh for 7 days; dynamic sets and artist pages
// render immediately from cache and revalidate silently in the background when stale.

import { defaultCatalog, type Artist, type CatalogContext, type Track, type TrackSet } from './catalog'
import type { ArtistPage } from './live'

const STORAGE_PREFIX = 'yc.page.v1:'
const INDEX_KEY = 'yc.page.v1.index'
const MAX_DISK_ENTRIES = 60

export const ALBUM_FRESH_MS = 7 * 24 * 3600_000
export const PLAYLIST_FRESH_MS = 3 * 60_000
export const ARTIST_FRESH_MS = 15 * 60_000
export const MAX_STALE_MS = 30 * 24 * 3600_000

interface CachedSetEntry {
  kind: 'set'
  id: string
  updatedAt: number
  set: TrackSet
  tracks: Track[]
  artists: Artist[]
}

interface CachedArtistEntry {
  kind: 'artist'
  id: string
  updatedAt: number
  page: {
    artist: string
    top: string[]
    releases: string[]
    playlists: string[]
    similar: string[]
  }
  artistObj?: Artist
  tracks: Track[]
  sets: TrackSet[]
  artists: Artist[]
}

type CachedEntry = CachedSetEntry | CachedArtistEntry

const l1 = new Map<string, CachedEntry>()

function clone<T>(v: T): T {
  return JSON.parse(JSON.stringify(v)) as T
}

function freshTtlForSet(set: TrackSet): number {
  if (set.kind === 'album' || set.kind === 'single') return ALBUM_FRESH_MS
  return PLAYLIST_FRESH_MS
}

function hydrateSet(entry: CachedSetEntry, cat: CatalogContext = defaultCatalog): TrackSet {
  for (const a of entry.artists) cat.putArtist(clone(a))
  for (const t of entry.tracks) cat.putTrack(clone(t))
  const s = clone(entry.set)
  cat.putSet(s)
  return s
}

function hydrateArtist(entry: CachedArtistEntry, cat: CatalogContext = defaultCatalog): ArtistPage {
  if (entry.artistObj) cat.putArtist(clone(entry.artistObj))
  for (const a of entry.artists) cat.putArtist(clone(a))
  for (const t of entry.tracks) cat.putTrack(clone(t))
  for (const s of entry.sets) cat.putSet(clone(s))
  return clone(entry.page)
}

function readDisk(key: string): CachedEntry | null {
  try {
    const raw = localStorage.getItem(STORAGE_PREFIX + key)
    if (!raw) return null
    const parsed = JSON.parse(raw) as CachedEntry
    if (!parsed || typeof parsed.updatedAt !== 'number') return null
    if (Date.now() - parsed.updatedAt > MAX_STALE_MS) {
      localStorage.removeItem(STORAGE_PREFIX + key)
      return null
    }
    return parsed
  } catch {
    return null
  }
}

function writeDisk(key: string, entry: CachedEntry) {
  try {
    localStorage.setItem(STORAGE_PREFIX + key, JSON.stringify(entry))
    const rawIdx = localStorage.getItem(INDEX_KEY)
    const idx: string[] = rawIdx ? (JSON.parse(rawIdx) as string[]).filter((k) => k !== key) : []
    idx.push(key)
    while (idx.length > MAX_DISK_ENTRIES) {
      const oldest = idx.shift()
      if (oldest) localStorage.removeItem(STORAGE_PREFIX + oldest)
    }
    localStorage.setItem(INDEX_KEY, JSON.stringify(idx))
  } catch {
    // quota exceeded or private mode: L1 RAM still works
  }
}

export function getCachedSet(
  id: string,
  cat: CatalogContext = defaultCatalog,
): { set: TrackSet; stale: boolean } | null {
  if (id.endsWith(':likes') || id === 'yt:pl:LM') return null
  const key = `set:${id}`
  let entry = l1.get(key) as CachedSetEntry | undefined
  if (!entry) {
    const disk = readDisk(key)
    if (disk?.kind === 'set') {
      entry = disk
      l1.set(key, entry)
    }
  }
  if (!entry || !entry.set?.tracks?.length) return null
  const age = Date.now() - entry.updatedAt
  if (age > MAX_STALE_MS) {
    l1.delete(key)
    return null
  }
  const set = hydrateSet(entry, cat)
  return { set, stale: age > freshTtlForSet(set) }
}

export function putCachedSet(set: TrackSet, cat: CatalogContext = defaultCatalog) {
  if (!set?.id || set.kind === 'liked' || set.id.endsWith(':likes') || set.id === 'yt:pl:LM' || !set.tracks?.length) return
  const tracks: Track[] = []
  const artistMap = new Map<string, Artist>()
  if (set.artist?.id) {
    const a = cat.artists.get(set.artist.id)
    if (a) artistMap.set(a.id, clone(a))
  }
  for (const tid of set.tracks) {
    const t = cat.tracks.get(tid)
    if (!t) continue
    tracks.push(clone(t))
    for (const a of t.artists) {
      const ao = cat.artists.get(a.id)
      if (ao && !artistMap.has(ao.id)) artistMap.set(ao.id, clone(ao))
    }
  }
  const entry: CachedSetEntry = {
    kind: 'set',
    id: set.id,
    updatedAt: Date.now(),
    set: clone(set),
    tracks,
    artists: [...artistMap.values()],
  }
  const key = `set:${set.id}`
  l1.set(key, entry)
  writeDisk(key, entry)
}

export function getCachedArtist(
  id: string,
  cat: CatalogContext = defaultCatalog,
): { page: ArtistPage; stale: boolean } | null {
  const key = `artist:${id}`
  let entry = l1.get(key) as CachedArtistEntry | undefined
  if (!entry) {
    const disk = readDisk(key)
    if (disk?.kind === 'artist') {
      entry = disk
      l1.set(key, entry)
    }
  }
  if (!entry) return null
  const age = Date.now() - entry.updatedAt
  if (age > MAX_STALE_MS) {
    l1.delete(key)
    return null
  }
  const page = hydrateArtist(entry, cat)
  return { page, stale: age > ARTIST_FRESH_MS }
}

export function putCachedArtist(page: ArtistPage, cat: CatalogContext = defaultCatalog) {
  if (!page?.artist) return
  const tracks: Track[] = []
  const sets: TrackSet[] = []
  const artistMap = new Map<string, Artist>()
  for (const tid of page.top) {
    const t = cat.tracks.get(tid)
    if (t) tracks.push(clone(t))
  }
  for (const sid of [...page.releases, ...page.playlists]) {
    const s = cat.sets.get(sid)
    if (s) sets.push(clone(s))
  }
  for (const aid of page.similar) {
    const a = cat.artists.get(aid)
    if (a) artistMap.set(a.id, clone(a))
  }
  const artistObj = cat.artists.get(page.artist)
  const entry: CachedArtistEntry = {
    kind: 'artist',
    id: page.artist,
    updatedAt: Date.now(),
    page: {
      artist: page.artist,
      top: [...page.top],
      releases: [...page.releases],
      playlists: [...page.playlists],
      similar: [...page.similar],
    },
    artistObj: artistObj ? clone(artistObj) : undefined,
    tracks,
    sets,
    artists: [...artistMap.values()],
  }
  const key = `artist:${page.artist}`
  l1.set(key, entry)
  writeDisk(key, entry)
}

export function clearPageCache() {
  l1.clear()
  try {
    const keys: string[] = []
    for (let i = 0; i < localStorage.length; i++) {
      const k = localStorage.key(i)
      if (k && (k.startsWith(STORAGE_PREFIX) || k === INDEX_KEY)) keys.push(k)
    }
    for (const k of keys) localStorage.removeItem(k)
  } catch {
    // ignore storage errors
  }
}
