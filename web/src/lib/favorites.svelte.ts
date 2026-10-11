// «Любимое» of the YouCloud account, the same here as in the app on every phone signed in to it
// (supabase/migrations/20261011120000_favorites.sql): what was liked there — and so downloaded
// there — and what was liked here. Files from a phone aren't in it. Signed out, a heart is the
// service's own like, as before.
//
// The site is always online, so it keeps no list of its own: it reads the account's when signed
// in, writes each change at once, and hears of the others over Realtime as they are made — and,
// coming back to the tab or after the connection drops, asks what changed since it last looked.

import { tr } from './i18n.svelte'
import type { RealtimeChannel } from '@supabase/supabase-js'
import { colorFor, putTrack, tracks, type Service, type Track } from './catalog'
import { supabase } from './supabase'

type RowService = 'soundcloud' | 'yandex' | 'youtube'

/** A track of «Любимое» as the database keeps it. */
interface Row {
  service: RowService
  track_id: string
  liked: boolean
  changed_at: string
  title?: string
  artists?: { id?: string | null; name: string; link?: string | null }[]
  album_id?: string | null
  cover?: string | null
  duration_ms?: number | null
  link?: string | null
  live?: boolean
}

const ROW_SERVICE: Record<Service, RowService> = { sc: 'soundcloud', ya: 'yandex', yt: 'youtube' }
const SERVICE: Record<RowService, Service> = { soundcloud: 'sc', yandex: 'ya', youtube: 'yt' }
// What the database takes for a track's id.
const TRACK_ID = /^[A-Za-z0-9_-]{1,64}$/
// Asked again from a little before the last look: a change written as that look was made may
// carry an earlier time than the newest one it saw.
const OVERLAP_MS = 60_000

export const favorites = $state({
  /** Signed in to YouCloud: a heart is the account's «Любимое». */
  on: false,
  loaded: false,
  /** Track ids, the latest liked first. */
  ids: [] as string[],
  has: {} as Record<string, boolean>,
  /** Why the list didn't come, the last time it was asked for. */
  error: null as string | null,
})

/** Asks for the list again, after it didn't come. */
export const retry = () => refresh()

/** When each track's latest change was made, liked or taken out: an older one never undoes it. */
const changed = new Map<string, number>()
/** When each track in «Любимое» was liked: the order. */
const likedAt = new Map<string, number>()

const idOf = (r: Pick<Row, 'service' | 'track_id'>) => `${SERVICE[r.service]}:t:${r.track_id}`

// ------------------------------------------------------------------------------- rows and tracks

function artistId(svc: Service, a: { id?: string | null; name: string }): string {
  if (svc === 'sc') return a.id ? `sc:u:${a.id}` : `sc:n:${a.name}`
  if (svc === 'ya') return a.id ? `ya:a:${a.id}` : `ya:n:${a.name}`
  return a.id?.startsWith('UC') ? `yt:a:${a.id}` : `yt:n:${a.name}`
}

/** The artist's own id on the service, from the catalog's ("sc:u:123" → "123"). */
const nativeArtist = (id: string) => /^(?:sc:u|ya:a|yt:a):(.+)$/.exec(id)?.[1]?.replace(/^undefined$/, '') || null

/** A cover in the size the site shows: the app keeps SoundCloud's 100 px and Yandex's 200 px ones. */
function coverOf(svc: Service, url: string | null | undefined): string | null {
  if (!url) return null
  if (svc === 'sc') return url.replace(/-large\.(jpg|png)/, '-t500x500.$1')
  if (svc === 'ya') return url.replace(/\/\d+x\d+$/, '/400x400')
  return url
}

/** The track a row names: the one the site has met already, or one made of the row. */
function put(r: Row): string {
  const id = idOf(r)
  if (tracks.has(id)) return id
  const svc = SERVICE[r.service]
  const artists = (r.artists ?? []).filter((a) => a?.name).map((a) => ({ id: artistId(svc, a), name: a.name }))
  return putTrack({
    id,
    service: svc,
    title: r.title || tr('Без названия'),
    artists,
    duration: Math.round((r.duration_ms ?? 0) / 1000),
    cover: coverOf(svc, r.cover),
    color: colorFor(id),
    link: r.link ?? undefined,
    // SoundCloud's ways to play it are asked for when it plays (services/soundcloud.ts stream).
    ...(svc === 'sc'
      ? { sc: { transcodings: [] } }
      : svc === 'ya'
        ? { ya: { trackId: r.track_id, albumId: r.album_id ?? undefined } }
        : { yt: { videoId: r.track_id } }),
  })
}

function rowOf(t: Track, liked: boolean): Row | null {
  const trackId = t.service === 'sc' ? t.id.slice('sc:t:'.length) : t.service === 'ya' ? t.ya?.trackId : t.yt?.videoId
  if (!trackId || !TRACK_ID.test(trackId)) return null
  return {
    service: ROW_SERVICE[t.service],
    track_id: trackId,
    liked,
    changed_at: new Date().toISOString(),
    title: t.title,
    artists: t.artists.map((a) => ({ id: nativeArtist(a.id), name: a.name })),
    album_id: t.ya?.albumId ?? null,
    cover: t.cover ?? null,
    duration_ms: t.duration ? t.duration * 1000 : null,
    link: t.link ?? null,
  }
}

/** Takes in what a row says, unless a later change to its track is known already. */
function apply(r: Row) {
  if (!r?.service || !SERVICE[r.service] || !r.track_id) return
  const id = idOf(r)
  const at = Date.parse(r.changed_at) || 0
  if ((changed.get(id) ?? -1) > at) return
  changed.set(id, at)
  if (r.liked) {
    put(r)
    likedAt.set(id, at)
    favorites.has[id] = true
  } else {
    likedAt.delete(id)
    delete favorites.has[id]
  }
}

function reorder() {
  favorites.ids = [...likedAt.keys()].sort((a, b) => likedAt.get(b)! - likedAt.get(a)!)
}

// ------------------------------------------------------------------------------- the account's list

let userId: string | null = null
let until: string | null = null
let channel: RealtimeChannel | null = null
let asking: Promise<void> | null = null

/** What changed since the last look; everything, the first time. */
function refresh(): Promise<void> {
  if (!userId) return Promise.resolve()
  if (asking) return asking
  const who = userId
  const p = (async () => {
    const since = until ? new Date(Date.parse(until) - OVERLAP_MS).toISOString() : null
    const { data, error } = await supabase.rpc('favorites_since', since ? { since } : {})
    if (who !== userId) return
    if (error) {
      // A database without «Любимое» yet (its migration not applied): the hearts stay the services'.
      if (error.code === 'PGRST202') favorites.on = false
      favorites.error = error.message
      throw error
    }
    favorites.error = null
    if (!data) return
    for (const r of (data.items ?? []) as Row[]) apply(r)
    until = data.until ?? until
    reorder()
    favorites.loaded = true
  })().finally(() => {
    if (asking === p) asking = null
  })
  asking = p
  return p
}

function start(id: string) {
  if (userId === id) return
  stop()
  userId = id
  favorites.on = true
  channel = supabase
    .channel('yc-favorites')
    .on('postgres_changes', { event: '*', schema: 'public', table: 'favorites', filter: `user_id=eq.${id}` }, (change) => {
      apply(change.new as Row)
      reorder()
    })
    // Joined, and joined again after a dropped connection: what changed meanwhile.
    .subscribe((status) => {
      if (status === 'SUBSCRIBED') refresh().catch(() => {})
    })
  refresh().catch(() => {})
}

function stop() {
  if (channel) supabase.removeChannel(channel)
  channel = null
  userId = null
  until = null
  asking = null
  changed.clear()
  likedAt.clear()
  favorites.on = false
  favorites.loaded = false
  favorites.ids = []
  favorites.has = {}
  favorites.error = null
}

supabase.auth.onAuthStateChange((_event, session) => {
  // Supabase asks not to await its own calls inside this callback.
  setTimeout(() => (session ? start(session.user.id) : stop()))
})

addEventListener('visibilitychange', () => {
  if (document.visibilityState === 'visible') refresh().catch(() => {})
})

/** Puts [t] in «Любимое» or takes it out, here at once and then for every device. */
export async function setFavorite(t: Track, liked: boolean): Promise<void> {
  const row = rowOf(t, liked)
  if (!row) throw new Error(tr('этот трек не сохранить в «Любимое»'))
  const before = { changed: changed.get(t.id), likedAt: likedAt.get(t.id) }
  apply(row)
  reorder()
  const { error } = await supabase.rpc('save_favorites', { items: [row] })
  if (!error) return
  // Not saved: back as it was, unless something newer came meanwhile.
  if (changed.get(t.id) === Date.parse(row.changed_at)) {
    before.changed == null ? changed.delete(t.id) : changed.set(t.id, before.changed)
    if (before.likedAt == null) {
      likedAt.delete(t.id)
      delete favorites.has[t.id]
    } else {
      likedAt.set(t.id, before.likedAt)
      favorites.has[t.id] = true
    }
    reorder()
  }
  throw error
}
