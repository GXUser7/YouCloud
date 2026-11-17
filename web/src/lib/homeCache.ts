// Two-tier home snapshot cache (L1 in-memory Map + L2 IndexedDB) with deterministic canonical
// hashing and strict service/account/language partitioning.

import { accounts } from './accounts.svelte'
import { defaultCatalog, type Artist, type CatalogContext, type Service, type Track, type TrackSet } from './catalog'
import type { Lang } from './i18n.svelte'
import type { Library, Section } from './live'

export const HOME_CACHE_SCHEMA = 1

const DB_NAME = 'youcloud-cache'
const DB_VERSION = 1
const STORE_NAME = 'home'
const IDB_TIMEOUT_MS = 1500
const YT_HINT_KEY = 'yc.yt.hint'
const LIKED_ART_TRACKS = 40

export interface HomeSnapshot {
  schema: number
  service: Service
  accountKey: string
  lang: Lang
  signedIn: boolean
  accountLabel?: string
  sections: Section[]
  lib: Library | null
  tracks: Record<string, Track>
  sets: Record<string, TrackSet>
  artists: Record<string, Artist>
  hash: string
  updatedAt: number
}

const l1 = new Map<string, HomeSnapshot>()
const serviceGeneration = new Map<Service, number>()

let ytResolved = false
let ytWaiters: (() => void)[] = []

export const homeCacheKey = (service: Service, accountKey: string, lang: Lang) =>
  `${HOME_CACHE_SCHEMA}:${service}:${accountKey}:${lang}`

export const cacheGenerationFor = (svc: Service): number => serviceGeneration.get(svc) ?? 0

function bumpGeneration(svc: Service) {
  serviceGeneration.set(svc, cacheGenerationFor(svc) + 1)
}

export function getYtAccountHint(): string | null {
  try {
    return localStorage.getItem(YT_HINT_KEY)
  } catch {
    return null
  }
}

export function setYtAccountHint(key: string | null) {
  try {
    if (key == null) localStorage.removeItem(YT_HINT_KEY)
    else localStorage.setItem(YT_HINT_KEY, key)
  } catch {
    // storage unavailable
  }
}

export function liveYtAccountKey(): string {
  if (!accounts.yt) return 'anon'
  return accounts.ytName ? `user:${accounts.ytName}` : 'user:signed'
}

export function markYtAccountResolved(accountKey = liveYtAccountKey()) {
  ytResolved = true
  setYtAccountHint(accountKey)
  ytWaiters.splice(0).forEach((fn) => fn())
}

export const isYtAccountResolved = () => ytResolved

export function waitForYtAccountResolved(timeoutMs = 2000): Promise<void> {
  if (ytResolved) return Promise.resolve()
  return new Promise((resolve) => {
    let settled = false
    const done = () => {
      if (settled) return
      settled = true
      clearTimeout(timer)
      resolve()
    }
    const timer = setTimeout(done, timeoutMs)
    ytWaiters.push(done)
  })
}

export function accountKeyFor(svc: Service): string {
  if (svc === 'sc') return accounts.sc ? `user:${accounts.sc.id}` : 'anon'
  if (svc === 'ya') return accounts.ya ? `user:${accounts.ya.uid}` : 'anon'
  if (accounts.yt) return liveYtAccountKey()
  if (!ytResolved) return getYtAccountHint() ?? 'anon'
  return 'anon'
}

// ---------------------------------------------------------------------------------- cloning & hash

function plainClone<T>(value: T): T {
  return JSON.parse(JSON.stringify(value)) as T
}

function stableStringify(value: unknown): string {
  if (value === null || typeof value !== 'object') return JSON.stringify(value)
  if (Array.isArray(value)) return `[${value.map((v) => (v === undefined ? 'null' : stableStringify(v))).join(',')}]`
  const entries = Object.entries(value as Record<string, unknown>)
    .filter(([, v]) => v !== undefined)
    .sort(([a], [b]) => (a < b ? -1 : a > b ? 1 : 0))
  return `{${entries.map(([k, v]) => `${JSON.stringify(k)}:${stableStringify(v)}`).join(',')}}`
}

/** Deterministic 128-bit synchronous digest (4 independent FNV-1a/mix lanes) of canonical JSON. */
export function hashString(input: string): string {
  let h1 = 0x811c9dc5
  let h2 = 0x9e3779b9
  let h3 = 0x85ebca6b
  let h4 = 0xc2b2ae35
  for (let i = 0; i < input.length; i++) {
    const c = input.charCodeAt(i)
    h1 = Math.imul(h1 ^ c, 0x01000193) >>> 0
    h2 = Math.imul(h2 ^ (c + i), 0x85ebca6b) >>> 0
    h3 = Math.imul(h3 ^ (c << 3), 0xc2b2ae35) >>> 0
    h4 = Math.imul(h4 ^ (c ^ (i >>> 2)), 0x27d4eb2f) >>> 0
  }
  return [h1, h2, h3, h4].map((h) => h.toString(16).padStart(8, '0')).join('')
}

function canonicalTrackForHash(t: Track): unknown {
  if (!t.sc) return t
  const { auth: _auth, ...scRest } = t.sc
  return { ...t, sc: scRest }
}

export function computeSnapshotHash(data: {
  sections: Section[]
  lib: Library | null
  tracks: Record<string, Track>
  sets: Record<string, TrackSet>
  artists: Record<string, Artist>
}): string {
  const normalizedTracks: Record<string, unknown> = {}
  for (const id of Object.keys(data.tracks).sort()) {
    normalizedTracks[id] = canonicalTrackForHash(data.tracks[id])
  }
  const canonical = {
    sections: data.sections.map((s) => ({
      title: s.title,
      subtitle: s.subtitle,
      sets: s.sets,
      tracks: s.tracks,
    })),
    lib: data.lib
      ? {
          liked: data.lib.liked,
          likedIds: data.lib.likedIds,
          playlists: data.lib.playlists,
          artists: data.lib.artists,
        }
      : null,
    tracks: normalizedTracks,
    sets: data.sets,
    artists: data.artists,
  }
  return hashString(stableStringify(canonical))
}

export function buildHomeSnapshot(params: {
  service: Service
  accountKey: string
  lang: Lang
  signedIn: boolean
  accountLabel?: string
  sections: Section[]
  lib: Library | null
  cat: CatalogContext
  updatedAt?: number
}): HomeSnapshot {
  const setIds = new Set<string>()
  const trackIds = new Set<string>()
  const artistIds = new Set<string>()

  const cleanSections: Section[] = params.sections.map((s) => {
    for (const sid of s.sets) setIds.add(sid)
    for (const tid of s.tracks ?? []) trackIds.add(tid)
    return plainClone(s)
  })

  const cleanLib: Library | null = params.lib ? plainClone(params.lib) : null
  if (cleanLib) {
    setIds.add(cleanLib.liked)
    for (const tid of cleanLib.likedIds.slice(0, LIKED_ART_TRACKS)) trackIds.add(tid)
    for (const pid of cleanLib.playlists) setIds.add(pid)
    for (const aid of cleanLib.artists) artistIds.add(aid)
  }

  const setsOut: Record<string, TrackSet> = {}
  for (const sid of setIds) {
    const s = params.cat.sets.get(sid)
    if (!s) continue
    const cloned = plainClone(s)
    setsOut[sid] = cloned
    if (cloned.artist?.id) artistIds.add(cloned.artist.id)
    if (cloned.kind === 'liked') {
      for (const tid of cloned.tracks.slice(0, LIKED_ART_TRACKS)) trackIds.add(tid)
    } else if (cloned.loaded && cloned.tracks.length) {
      for (const tid of cloned.tracks) trackIds.add(tid)
    }
  }

  const tracksOut: Record<string, Track> = {}
  for (const tid of trackIds) {
    const t = params.cat.tracks.get(tid)
    if (!t) continue
    const cloned = plainClone(t)
    tracksOut[tid] = cloned
    for (const a of cloned.artists) artistIds.add(a.id)
  }

  const artistsOut: Record<string, Artist> = {}
  for (const aid of artistIds) {
    const a = params.cat.artists.get(aid)
    if (!a) continue
    artistsOut[aid] = plainClone(a)
  }

  const hash = computeSnapshotHash({
    sections: cleanSections,
    lib: cleanLib,
    tracks: tracksOut,
    sets: setsOut,
    artists: artistsOut,
  })

  return {
    schema: HOME_CACHE_SCHEMA,
    service: params.service,
    accountKey: params.accountKey,
    lang: params.lang,
    signedIn: params.signedIn,
    ...(params.accountLabel ? { accountLabel: params.accountLabel } : {}),
    sections: cleanSections,
    lib: cleanLib,
    tracks: tracksOut,
    sets: setsOut,
    artists: artistsOut,
    hash,
    updatedAt: params.updatedAt ?? Date.now(),
  }
}

export function hydrateCatalogFromSnapshot(snapshot: HomeSnapshot, cat: CatalogContext = defaultCatalog) {
  for (const a of Object.values(snapshot.artists)) cat.putArtist(plainClone(a))
  for (const t of Object.values(snapshot.tracks)) {
    const known = cat.tracks.get(t.id)
    const cloned = plainClone(t)
    if (known) {
      if (!cloned.duration && known.duration) cloned.duration = known.duration
      if (cloned.live === undefined && known.live !== undefined) cloned.live = known.live
    }
    cat.putTrack(cloned)
  }
  for (const s of Object.values(snapshot.sets)) cat.putSet(plainClone(s))
}

// ---------------------------------------------------------------------------------- L2 IndexedDB

interface StoredRecord {
  key: string
  snapshot: HomeSnapshot
}

function isValidSnapshot(raw: unknown): raw is HomeSnapshot {
  if (!raw || typeof raw !== 'object') return false
  const s = raw as Partial<HomeSnapshot>
  return (
    s.schema === HOME_CACHE_SCHEMA &&
    (s.service === 'sc' || s.service === 'ya' || s.service === 'yt') &&
    typeof s.accountKey === 'string' &&
    s.accountKey.length > 0 &&
    (s.lang === 'ru' || s.lang === 'en') &&
    typeof s.signedIn === 'boolean' &&
    Array.isArray(s.sections) &&
    (s.lib === null || (typeof s.lib === 'object' && typeof s.lib.liked === 'string' && Array.isArray(s.lib.likedIds))) &&
    !!s.tracks && typeof s.tracks === 'object' &&
    !!s.sets && typeof s.sets === 'object' &&
    !!s.artists && typeof s.artists === 'object' &&
    typeof s.hash === 'string' &&
    s.hash.length > 0
  )
}

function withTimeout<T>(work: Promise<T>, fallback: T): Promise<T> {
  return new Promise((resolve) => {
    let settled = false
    const done = (v: T) => {
      if (settled) return
      settled = true
      clearTimeout(timer)
      resolve(v)
    }
    const timer = setTimeout(() => done(fallback), IDB_TIMEOUT_MS)
    work.then(done, () => done(fallback))
  })
}

function openCacheDb(): Promise<IDBDatabase | null> {
  if (typeof indexedDB === 'undefined') return Promise.resolve(null)
  return withTimeout(
    new Promise<IDBDatabase | null>((resolve) => {
      try {
        const req = indexedDB.open(DB_NAME, DB_VERSION)
        req.onupgradeneeded = () => {
          const db = req.result
          if (!db.objectStoreNames.contains(STORE_NAME)) {
            db.createObjectStore(STORE_NAME, { keyPath: 'key' })
          }
        }
        req.onsuccess = () => resolve(req.result)
        req.onerror = () => resolve(null)
        req.onblocked = () => resolve(null)
      } catch {
        resolve(null)
      }
    }),
    null,
  )
}

async function idbGet(key: string): Promise<HomeSnapshot | null> {
  const db = await openCacheDb()
  if (!db) return null
  try {
    return await withTimeout(
      new Promise<HomeSnapshot | null>((resolve) => {
        try {
          const tx = db.transaction(STORE_NAME, 'readonly')
          const req = tx.objectStore(STORE_NAME).get(key)
          req.onsuccess = () => {
            const rec = req.result as StoredRecord | undefined
            resolve(rec?.snapshot ?? null)
          }
          req.onerror = () => resolve(null)
        } catch {
          resolve(null)
        }
      }),
      null,
    )
  } finally {
    try {
      db.close()
    } catch {
      // ignore
    }
  }
}

async function idbPut(key: string, snapshot: HomeSnapshot): Promise<boolean> {
  const db = await openCacheDb()
  if (!db) return false
  try {
    return await withTimeout(
      new Promise<boolean>((resolve) => {
        try {
          const tx = db.transaction(STORE_NAME, 'readwrite')
          const req = tx.objectStore(STORE_NAME).put({ key, snapshot } satisfies StoredRecord)
          req.onsuccess = () => resolve(true)
          req.onerror = () => resolve(false)
        } catch {
          resolve(false)
        }
      }),
      false,
    )
  } finally {
    try {
      db.close()
    } catch {
      // ignore
    }
  }
}

async function idbDelete(key: string): Promise<void> {
  const db = await openCacheDb()
  if (!db) return
  try {
    await withTimeout(
      new Promise<void>((resolve) => {
        try {
          const tx = db.transaction(STORE_NAME, 'readwrite')
          const req = tx.objectStore(STORE_NAME).delete(key)
          req.onsuccess = () => resolve()
          req.onerror = () => resolve()
        } catch {
          resolve()
        }
      }),
      undefined,
    )
  } finally {
    try {
      db.close()
    } catch {
      // ignore
    }
  }
}

async function idbDeletePrefix(prefix: string): Promise<void> {
  const db = await openCacheDb()
  if (!db) return
  try {
    await withTimeout(
      new Promise<void>((resolve) => {
        try {
          const tx = db.transaction(STORE_NAME, 'readwrite')
          const store = tx.objectStore(STORE_NAME)
          const range = typeof IDBKeyRange !== 'undefined' ? IDBKeyRange.bound(prefix, prefix + '\uffff') : undefined
          const req = range ? store.delete(range) : store.openCursor()
          if (range) {
            req.onsuccess = () => resolve()
            req.onerror = () => resolve()
          } else {
            req.onsuccess = () => {
              const cursor = (req as IDBRequest<IDBCursorWithValue | null>).result
              if (!cursor) {
                resolve()
                return
              }
              if (String(cursor.key).startsWith(prefix)) cursor.delete()
              cursor.continue()
            }
            req.onerror = () => resolve()
          }
        } catch {
          resolve()
        }
      }),
      undefined,
    )
  } finally {
    try {
      db.close()
    } catch {
      // ignore
    }
  }
}

// ---------------------------------------------------------------------------------- public API

export function peekHomeCacheL1(svc: Service, accountKey: string, lang: Lang): HomeSnapshot | null {
  const hit = l1.get(homeCacheKey(svc, accountKey, lang))
  return hit && isValidSnapshot(hit) ? hit : null
}

export async function readHomeCache(svc: Service, accountKey: string, lang: Lang): Promise<HomeSnapshot | null> {
  const key = homeCacheKey(svc, accountKey, lang)
  const mem = l1.get(key)
  if (mem && isValidSnapshot(mem)) return mem
  const raw = await idbGet(key)
  if (!raw) return null
  if (!isValidSnapshot(raw) || raw.service !== svc || raw.accountKey !== accountKey || raw.lang !== lang) {
    l1.delete(key)
    await idbDelete(key)
    return null
  }
  l1.set(key, raw)
  return raw
}

export async function writeHomeCacheIfChanged(snapshot: HomeSnapshot): Promise<boolean> {
  if (!isValidSnapshot(snapshot)) return false
  const key = homeCacheKey(snapshot.service, snapshot.accountKey, snapshot.lang)
  let existing = l1.get(key)
  if (!existing) {
    const fromIdb = await idbGet(key)
    if (fromIdb && isValidSnapshot(fromIdb)) existing = fromIdb
  }
  if (existing && existing.hash === snapshot.hash) {
    l1.set(key, existing)
    return false
  }
  const clean = plainClone(snapshot)
  l1.set(key, clean)
  await idbPut(key, clean)
  return true
}

export async function invalidateServiceHomeCache(svc: Service): Promise<void> {
  bumpGeneration(svc)
  const prefix = `${HOME_CACHE_SCHEMA}:${svc}:`
  for (const key of [...l1.keys()]) {
    if (key.startsWith(prefix)) l1.delete(key)
  }
  if (svc === 'yt') setYtAccountHint(null)
  await idbDeletePrefix(prefix)
}

export function clearHomeCacheMemory() {
  l1.clear()
  serviceGeneration.clear()
  ytResolved = false
  ytWaiters = []
}
