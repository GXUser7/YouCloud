// YouTube Music. Pages come from InnerTube through youtubei.js, loaded only once YouTube is opened.
// Its requests go through the extension when the listener has it — from their own IP, signed in
// as this browser is on YouTube — or through the Worker otherwise.
//
// Sound, as the app gets it: first the Android VR client, whose player hands out plain stream
// URLs to anyone YouTube trusts; where it asks to "confirm you're not a bot", the TV client
// signed in, whose URLs are unscrambled by YouTube's own player script. That script runs in a
// Worker of its own, with nothing of the page in reach.

import type { Innertube } from 'youtubei.js/web'
import { artists as artistIndex, colorFor, putArtist, putSet, putTrack, sets, type SetKind, type Track, type TrackSet } from '../catalog'
import { accounts } from '../accounts.svelte'
import { extension, extensionCall } from '../extension'
import { at, getText, postJson, request } from '../net'

const ORIGIN = 'https://music.youtube.com'
const API = `${ORIGIN}/youtubei/v1/`

// ---------------------------------------------------------------------------------- youtubei.js

/**
 * How the session goes along with YouTube's requests: the cookies Chrome attaches itself or the
 * full set put on by the extension, signed with all three SAPISID hashes or the first. Which one
 * YouTube accepts depends on the browser, so [accountName] tries them and keeps the first that
 * comes back as the listener.
 */
const SESSION_MODES = ['native3', 'dnr3', 'native1', 'dnr1'] as const
const MODE_KEY = 'yc.yt.session'
let sessionMode: string = (() => {
  try {
    return localStorage.getItem(MODE_KEY) ?? 'native3'
  } catch {
    return 'native3'
  }
})()

/** youtubei.js's requests, through the extension (signed in) or the Worker. */
async function ytFetch(input: RequestInfo | URL, init?: RequestInit): Promise<Response> {
  const isRequest = typeof Request !== 'undefined' && input instanceof Request
  const url = typeof input === 'string' ? input : input instanceof URL ? input.href : (input as Request).url
  const method = init?.method ?? (isRequest ? (input as Request).method : 'GET')
  // youtubei.js hands its headers over in a plain Headers object, User-Agent and all.
  const all = new Headers(init?.headers ?? (isRequest ? (input as Request).headers : undefined))
  const headers: Record<string, string> = {}
  let ua: string | undefined
  all.forEach((value, name) => {
    if (name === 'user-agent') ua = value
    else if (name !== 'cookie' && name !== 'origin' && name !== 'referer' && name !== 'authorization') headers[name] = value
  })
  let body = init?.body ?? undefined
  if (body && typeof body !== 'string') body = await new Response(body).text()
  // YouTube Music's own requests go to music.youtube.com, signed for that origin, as the site and
  // the app send them; youtubei.js sends them to www.youtube.com, where they come back as anyone's.
  let target = url
  if (typeof body === 'string' && body.includes('"WEB_REMIX"') && url.startsWith('https://www.youtube.com/youtubei/')) {
    target = url.replace('https://www.youtube.com/', 'https://music.youtube.com/')
  }
  // Signed in, the account's own cookies say who is visiting; a made-up visitor id would argue.
  if (accounts.yt) delete headers['x-goog-visitor-id']
  return request(target, { method, headers, body: body as string | undefined, ua, session: sessionMode })
}

/** Runs the player script's unscrambling in a Worker with nothing of the page in it. */
let evaluator: Worker | null = null
let evalSeq = 0
const evalWaiting = new Map<number, { resolve: (v: unknown) => void; reject: (e: unknown) => void }>()

function evaluate(data: { output: string }, env: Record<string, unknown>): Promise<unknown> {
  if (!evaluator) {
    const source = `onmessage = (e) => {
      try { postMessage({ id: e.data.id, result: new Function(e.data.code)() }) }
      catch (err) { postMessage({ id: e.data.id, error: String(err && err.message || err) }) }
    }`
    evaluator = new Worker(URL.createObjectURL(new Blob([source], { type: 'text/javascript' })))
    evaluator.onmessage = (e) => {
      const waiter = evalWaiting.get(e.data.id)
      evalWaiting.delete(e.data.id)
      if (e.data.error) waiter?.reject(new Error(e.data.error))
      else waiter?.resolve(e.data.result)
    }
  }
  const id = ++evalSeq
  const call = `return process(${JSON.stringify(env.n ?? '')}, ${JSON.stringify(env.sp ?? '')}, ${JSON.stringify(env.sig ?? '')})`
  return new Promise((resolve, reject) => {
    evalWaiting.set(id, { resolve, reject })
    evaluator!.postMessage({ id, code: `${data.output}\n${call}` })
  })
}

let browsing: Promise<Innertube> | null = null
let playing: Promise<Innertube> | null = null

async function create(withPlayer: boolean): Promise<Innertube> {
  const { Innertube, UniversalCache, Platform } = await import('youtubei.js/web')
  Platform.shim.eval = evaluate as typeof Platform.shim.eval
  return Innertube.create({
    fetch: ytFetch,
    cache: new UniversalCache(true),
    generate_session_locally: true,
    retrieve_player: withPlayer,
    lang: 'ru',
    location: 'RU',
  })
}

/** For pages: no player script, which is big and only needed to play. */
const yt = () => (browsing ??= create(false).catch((e) => ((browsing = null), Promise.reject(e))))
/** For sound: with the player script, to unscramble the TV client's URLs. */
const ytPlayer = () => (playing ??= create(true).catch((e) => ((playing = null), Promise.reject(e))))

// ---------------------------------------------------------------------------------- shapes

type Thumb = { url: string; width?: number; height?: number }

/** The largest picture, and Google's resizable ones asked for at a size worth showing. */
function picture(list: Thumb[] | undefined | null, size = 544): string | null {
  if (!list?.length) return null
  const best = [...list].sort((a, b) => (b.width ?? 0) - (a.width ?? 0))[0].url
  return best.replace(/=w\d+-h\d+[^&?]*/, `=w${size}-h${size}-l90-rj`).replace(/^\/\//, 'https://')
}

const thumbsOf = (item: any): Thumb[] =>
  item?.thumbnails ?? item?.thumbnail?.contents ?? (Array.isArray(item?.thumbnail) ? item.thumbnail : []) ?? []

const text = (t: any): string => (t == null ? '' : typeof t === 'string' ? t : (t.text ?? t.toString?.() ?? ''))

function putYtTrack(item: any, fallback?: { cover?: string | null; artist?: { id: string; name: string }; album?: { id: string; title: string } }): string | null {
  const videoId: string | undefined = item.video_id ?? item.id ?? item.endpoint?.payload?.videoId
  if (!videoId || videoId.length !== 11) return null
  const id = `yt:t:${videoId}`
  const people = (item.artists ?? item.authors ?? (item.author && typeof item.author === 'object' ? [item.author] : [])) as { name: string; channel_id?: string }[]
  const artists = people.length
    ? people.map((a) => ({ id: a.channel_id ? `yt:a:${a.channel_id}` : `yt:n:${a.name}`, name: a.name }))
    : fallback?.artist
      ? [fallback.artist]
      : [{ id: `yt:n:${text(item.author) || 'YouTube'}`, name: text(item.author) || 'YouTube' }]
  return putTrack({
    id,
    service: 'yt',
    title: text(item.title) || 'Без названия',
    artists,
    duration: item.duration?.seconds ?? 0,
    cover: picture(thumbsOf(item), 400) ?? fallback?.cover ?? `https://i.ytimg.com/vi/${videoId}/hqdefault.jpg`,
    color: colorFor(id),
    album: item.album?.id ? { id: `yt:al:${item.album.id}`, title: item.album.name } : fallback?.album,
    link: `https://music.youtube.com/watch?v=${videoId}`,
    yt: { videoId },
  })
}

function putYtSet(item: any, kind?: SetKind): string | null {
  const browseId: string | undefined = item.id ?? item.endpoint?.payload?.browseId
  if (!browseId) return null
  const type = kind ?? (item.item_type === 'album' ? 'album' : 'playlist')
  const id = type === 'album' || type === 'single' ? `yt:al:${browseId}` : `yt:pl:${browseId.replace(/^VL/, '')}`
  const subtitle = text(item.subtitle) || [item.author?.name, ...(item.artists ?? []).map((a: any) => a.name)].filter(Boolean).join(', ')
  const firstArtist = item.artists?.[0] ?? item.author
  const year = Number(item.year)
  return putSet({
    id,
    kind: type,
    service: 'yt',
    title: text(item.title) || 'Подборка',
    subtitle,
    cover: picture(thumbsOf(item)),
    color: colorFor(id),
    tracks: [],
    year: Number.isFinite(year) && year > 1900 ? year : undefined,
    artist: firstArtist?.channel_id ? { id: `yt:a:${firstArtist.channel_id}`, name: firstArtist.name } : undefined,
    link: type === 'album' ? `https://music.youtube.com/browse/${browseId}` : `https://music.youtube.com/playlist?list=${browseId.replace(/^VL/, '')}`,
  })
}

function putYtArtist(item: any): string | null {
  const channelId: string | undefined = item.id ?? item.endpoint?.payload?.browseId
  if (!channelId) return null
  const id = `yt:a:${channelId}`
  return putArtist({
    id,
    name: text(item.title) || item.name || 'Артист',
    service: 'yt',
    image: picture(thumbsOf(item)),
    color: colorFor(id),
    followers: item.subscribers ? `${item.subscribers}`.replace(/subscribers?/i, 'подписчиков') : undefined,
    link: `https://music.youtube.com/channel/${channelId}`,
  })
}

/** Whatever a shelf item is, put where it belongs: [tracks] for songs and videos, [sets], [artists]. */
function sortItems(items: any[], into: { tracks: string[]; sets: string[]; artists: string[] }) {
  for (const item of items ?? []) {
    const type = item.item_type
    if (type === 'song' || type === 'video' || type === 'non_music_track') {
      const id = putYtTrack(item)
      if (id) into.tracks.push(id)
    } else if (type === 'artist' || type === 'library_artist') {
      const id = putYtArtist(item)
      if (id) into.artists.push(id)
    } else if (type === 'album' || type === 'playlist') {
      const id = putYtSet(item)
      if (id) into.sets.push(id)
    }
  }
}

// ---------------------------------------------------------------------------------- pages

export interface Section {
  title: string
  sets: string[]
  tracks?: string[]
}

/**
 * The next page of a browse, asked as the site and the app ask it: the token in the address, and
 * the same visitor the first page was asked as — a token is that visitor's, for anyone else it is
 * just the first page again.
 */
async function browseContinuation(client: Innertube, token: string): Promise<any> {
  const visitor: string | undefined = (client as any).session?.context?.client?.visitorData
  const headers: Record<string, string> = { 'x-origin': ORIGIN }
  if (visitor && !accounts.yt) headers['x-goog-visitor-id'] = visitor
  return postJson(
    `${API}browse?ctoken=${encodeURIComponent(token)}&continuation=${encodeURIComponent(token)}&type=next&prettyPrint=false`,
    { context: { client: { clientName: 'WEB_REMIX', clientVersion: clientVersion(), hl: 'ru', gl: 'RU', visitorData: accounts.yt ? undefined : visitor }, user: {} } },
    { headers, session: sessionMode },
  )
}

/** How many more pages of home to fetch after the first: its rows arrive a few at a time. */
const HOME_EXTRA_PAGES = 3

/**
 * Home's rows, the personal ones included, over a few pages as the app's YouTubeMusicClient reads
 * them, with the account's liked songs leading the first row of sets.
 */
/**
 * A browse page's rows and the token for the next, in whichever shape YouTube Music answered:
 * a continuation (legacy `continuationContents`), a whole page again (tab content with its own
 * continuation), or appended items (`onResponseReceivedActions`).
 */
function pageRows(parsed: any): { shelves: any[]; token?: string } {
  const cont = parsed?.continuation_contents
  if (cont?.contents?.length) return { shelves: [...cont.contents], token: cont.continuation }
  const list = parsed?.contents?.item?.()?.tabs?.find((tab: any) => tab.selected)?.content
  if (list?.contents?.length) return { shelves: [...list.contents], token: list.continuation }
  const appended: any[] = parsed?.on_response_received_actions?.[0]?.contents ?? []
  if (appended.length) {
    const next = appended.find((item) => item.type === 'ContinuationItem')
    return { shelves: appended.filter((item) => item.type !== 'ContinuationItem'), token: next?.endpoint?.payload?.token }
  }
  return { shelves: [] }
}

export async function home(): Promise<Section[]> {
  const client = await yt()
  let feed: any = await client.music.getHomeFeed()
  const shelves: any[] = [...(feed.sections ?? [])]
  let token: string | undefined = pageRows(feed.page).token
  const { Parser } = await import('youtubei.js/web')
  for (let page = 0; page < HOME_EXTRA_PAGES && token; page++) {
    // Asked as the site and the app ask — the token in the address — and, if that comes back
    // empty, as youtubei.js asks, with the token in the body.
    let rows = await browseContinuation(client, token)
      .then((raw) => pageRows(Parser.parseResponse(raw as any)))
      .catch(() => ({ shelves: [] as any[], token: undefined as string | undefined }))
    if (!rows.shelves.length && feed?.has_continuation) {
      feed = await feed.getContinuation().catch(() => null)
      rows = feed ? { shelves: [...(feed.sections ?? [])], token: pageRows(feed.page).token } : rows
    }
    if (!rows.shelves.length) break
    shelves.push(...rows.shelves)
    token = rows.token
  }
  const sections: Section[] = []
  // A page answered again in full would repeat its rows: a row is kept once.
  const seen = new Set<string>()
  for (const shelf of shelves) {
    const into = { tracks: [] as string[], sets: [] as string[], artists: [] as string[] }
    sortItems(shelf.contents, into)
    const title = text(shelf.header?.title) || 'YouTube Music'
    const key = `${title}|${into.tracks[0] ?? into.sets[0] ?? ''}`
    if (seen.has(key)) continue
    seen.add(key)
    if (into.tracks.length && into.tracks.length >= into.sets.length) sections.push({ title, sets: [], tracks: into.tracks })
    else if (into.sets.length) sections.push({ title, sets: into.sets })
  }
  if (await signedIn()) {
    putSet({ id: 'yt:pl:LM', kind: 'liked', service: 'yt', title: 'Понравившаяся музыка', subtitle: 'Автоплейлист', color: '#e53935', tracks: [], cover: null })
    // Home puts the liked playlist in a different row each time, or leaves it out; it goes first.
    for (const section of sections) section.sets = section.sets.filter((id) => id !== 'yt:pl:LM')
    const firstSets = sections.find((section) => section.sets.length)
    if (firstSets) firstSets.sets.unshift('yt:pl:LM')
    else sections.unshift({ title: 'Твоя медиатека', sets: ['yt:pl:LM'] })
  }
  return sections.filter((section) => section.sets.length || section.tracks?.length)
}

function finish(id: string, tracks: string[], extra: Partial<TrackSet> = {}): TrackSet {
  const done = { ...sets.get(id)!, ...extra, tracks, loaded: true, total: tracks.length }
  putSet(done)
  return done
}

export async function loadSet(id: string): Promise<TrackSet> {
  const client = await yt()
  if (id.startsWith('yt:al:')) {
    const browseId = id.slice('yt:al:'.length)
    const album: any = await client.music.getAlbum(browseId)
    const header = album.header
    const cover = picture(thumbsOf(header?.thumbnail) .length ? thumbsOf(header.thumbnail) : thumbsOf(album.background))
    const artistRun = header?.strapline_text_one?.runs?.[0] ?? header?.subtitle?.runs?.find((r: any) => r.endpoint?.payload?.browseId?.startsWith('UC'))
    const artist = artistRun?.endpoint?.payload?.browseId ? { id: `yt:a:${artistRun.endpoint.payload.browseId}`, name: artistRun.text } : undefined
    if (!sets.has(id)) putSet({ id, kind: 'album', service: 'yt', title: text(header?.title), subtitle: artist?.name ?? '', color: colorFor(id), tracks: [], cover })
    const tracks = (album.contents ?? []).flatMap((item: any) => {
      const t = putYtTrack(item, { cover, artist, album: { id, title: text(header?.title) } })
      return t ? [t] : []
    })
    return finish(id, tracks, { cover: sets.get(id)?.cover ?? cover, title: text(header?.title) || sets.get(id)!.title, artist: artist ?? sets.get(id)?.artist })
  }
  const playlistId = id.slice('yt:pl:'.length)
  let playlist: any = await client.music.getPlaylist(playlistId)
  const items: any[] = [...(playlist.items ?? [])]
  while (playlist.has_continuation && items.length < 300) {
    playlist = await playlist.getContinuation()
    items.push(...(playlist.items ?? []))
  }
  const header = playlist.header
  if (!sets.has(id)) putSet({ id, kind: 'playlist', service: 'yt', title: text(header?.title), subtitle: text(header?.subtitle), color: colorFor(id), tracks: [], cover: picture(thumbsOf(header?.thumbnail)) })
  const tracks = items.flatMap((item) => {
    const t = putYtTrack(item)
    return t ? [t] : []
  })
  return finish(id, tracks)
}

export interface ArtistPage {
  artist: string
  top: string[]
  releases: string[]
  playlists: string[]
  similar: string[]
}

export async function loadArtist(id: string): Promise<ArtistPage> {
  const channelId = id.slice('yt:a:'.length)
  const page: any = await (await yt()).music.getArtist(channelId)
  const header = page.header
  putArtist({
    id,
    name: text(header?.title) || 'Артист',
    service: 'yt',
    image: picture(thumbsOf(header?.thumbnail)),
    color: colorFor(id),
    followers: header?.subscription_button?.subscriber_count?.toString?.()
      ? `${header.subscription_button.subscriber_count} подписчиков`
      : undefined,
    link: `https://music.youtube.com/channel/${channelId}`,
  })
  const top: string[] = []
  const releases: string[] = []
  const playlists: string[] = []
  const similar: string[] = []
  for (const section of (page.sections ?? []) as any[]) {
    const into = { tracks: [] as string[], sets: [] as string[], artists: [] as string[] }
    sortItems(section.contents, into)
    if (section.type === 'MusicShelf') top.push(...into.tracks)
    for (const s of into.sets) (sets.get(s)?.kind === 'album' ? releases : playlists).push(s)
    similar.push(...into.artists)
  }
  return { artist: id, top, releases, playlists, similar }
}

export interface SearchResult {
  best?: { type: 'artist' | 'track' | 'album' | 'playlist'; id: string }
  tracks: string[]
  artists: string[]
  albums: string[]
  playlists: string[]
}

/** The search page's top card, as an artist, album or song — put first in its own row too. */
function topResult(card: any, rows: { tracks: string[]; artists: string[]; albums: string[] }): SearchResult['best'] {
  if (!card) return undefined
  const tap = card.on_tap?.payload ?? card.title?.runs?.[0]?.endpoint?.payload ?? {}
  const runs: any[] = card.subtitle?.runs ?? []
  const first = <T,>(list: T[], id: T) => {
    if (!list.includes(id)) list.unshift(id)
  }
  if (typeof tap.browseId === 'string' && tap.browseId.startsWith('UC')) {
    const id = putYtArtist({ id: tap.browseId, title: card.title, thumbnail: card.thumbnail })
    if (!id) return undefined
    first(rows.artists, id)
    return { type: 'artist', id }
  }
  if (typeof tap.browseId === 'string' && tap.browseId.startsWith('MPRE')) {
    const id = putYtSet({ id: tap.browseId, title: card.title, subtitle: card.subtitle, thumbnail: card.thumbnail }, 'album')
    if (!id) return undefined
    first(rows.albums, id)
    return { type: 'album', id }
  }
  if (typeof tap.videoId === 'string') {
    const artists = runs
      .filter((r) => r.endpoint?.payload?.browseId?.startsWith('UC'))
      .map((r) => ({ name: r.text, channel_id: r.endpoint.payload.browseId }))
    const id = rows.tracks.find((t) => t === `yt:t:${tap.videoId}`) ?? putYtTrack({ video_id: tap.videoId, title: card.title, thumbnail: card.thumbnail, artists })
    if (!id) return undefined
    first(rows.tracks, id)
    return { type: 'track', id }
  }
  return undefined
}

export async function search(q: string): Promise<SearchResult> {
  const found: any = await (await yt()).music.search(q)
  // Shelves are told apart by what they hold, not by their titles, which come in the page's language.
  const into = { tracks: [] as string[], sets: [] as string[], artists: [] as string[] }
  for (const shelf of (found.contents ?? []) as any[]) sortItems(shelf.contents ?? [], into)
  const tracks = [...new Set(into.tracks)]
  const artists = [...new Set(into.artists)]
  const albums = [...new Set(into.sets.filter((id) => id.startsWith('yt:al:')))]
  const playlists = [...new Set(into.sets.filter((id) => id.startsWith('yt:pl:')))]
  // The best result is the one YouTube Music puts on its top card; failing that, an artist named
  // exactly like the query, then the first song.
  let best = topResult((found.contents ?? []).find((shelf: any) => shelf.type === 'MusicCardShelf'), { tracks, artists, albums })
  if (!best) {
    const named = artists.find((a) => artistIndex.get(a)?.name.trim().toLowerCase() === q.trim().toLowerCase())
    best = named ? { type: 'artist', id: named } : tracks[0] ? { type: 'track', id: tracks[0] } : undefined
  }
  return {
    best,
    tracks,
    artists,
    albums,
    playlists,
  }
}

/** Radio from [t], as YouTube Music plays it after a track ("Up next" with autoplay). */
export async function radio(t: Track): Promise<string[]> {
  const panel: any = await (await yt()).music.getUpNext(t.yt!.videoId, true)
  return ((panel.contents ?? []) as any[]).flatMap((item) => {
    const id = putYtTrack(item.primary ?? item)
    return id ? [id] : []
  })
}

/** YouTube Music's own lyrics for [t]: plain text, without timings; null when it has none. */
export async function lyricsText(t: Track): Promise<string | null> {
  try {
    const shelf: any = await (await yt()).music.getLyrics(t.yt!.videoId)
    const body = text(shelf?.description).trim()
    return body || null
  } catch {
    return null
  }
}

/** A track by its video id, as a shared link names it. */
export async function trackOf(videoId: string): Promise<string | null> {
  const panel: any = await (await yt()).music.getUpNext(videoId, false)
  const first = ((panel.contents ?? []) as any[]).map((item) => item.primary ?? item).find((item) => item.video_id === videoId)
  return first ? putYtTrack(first) : null
}

export interface Library {
  liked: string
  likedIds: string[]
  playlists: string[]
  artists: string[]
}

export async function library(): Promise<Library> {
  putSet({ id: 'yt:pl:LM', kind: 'liked', service: 'yt', title: 'Понравившаяся музыка', subtitle: 'YouTube Music', color: '#e53935', tracks: [], cover: null })
  const liked = await loadSet('yt:pl:LM')
  const playlists: string[] = []
  try {
    const lib: any = await (await yt()).music.getLibrary()
    const into = { tracks: [] as string[], sets: [] as string[], artists: [] as string[] }
    sortItems(lib.contents ?? [], into)
    playlists.push(...into.sets.filter((s) => s !== 'yt:pl:LM'))
  } catch {
    // the library page changes often; liked songs are what matters
  }
  return { liked: liked.id, likedIds: liked.tracks, playlists, artists: [] }
}

// ---------------------------------------------------------------------------------- account

/** Whether this browser is signed in on YouTube (its session is what the extension signs with). */
export async function signedIn(): Promise<boolean> {
  if (!(await extension())) return false
  try {
    return !!(await extensionCall<boolean>('yt-account'))
  } catch {
    return false
  }
}

async function accountNameWith(mode: string): Promise<string | null> {
  const answer = await postJson(
    `${API}account/account_menu?prettyPrint=false`,
    { context: { client: { clientName: 'WEB_REMIX', clientVersion: clientVersion(), hl: 'ru' }, user: {} } },
    { headers: { 'x-origin': ORIGIN }, session: mode },
  )
  const header = at(answer, 'actions', 0, 'openPopupAction', 'popup', 'multiPageMenuRenderer', 'header', 'activeAccountHeaderRenderer')
  const name = at(header, 'accountName', 'runs', 0, 'text')
  const handle = at(header, 'channelHandle', 'runs', 0, 'text')
  return name ? (handle ? `${name} (${handle})` : name) : null
}

/** The YouTube account this browser's session is, as YouTube itself names it; null as a guest.
 *  Finds the way of sending the session that YouTube accepts here, and keeps it. */
export async function accountName(): Promise<string | null> {
  for (const mode of [sessionMode, ...SESSION_MODES.filter((m) => m !== sessionMode)]) {
    const name = await accountNameWith(mode).catch(() => null)
    if (name) {
      if (mode !== sessionMode) {
        sessionMode = mode
        try {
          localStorage.setItem(MODE_KEY, mode)
        } catch {
          // found again next visit
        }
      }
      return name
    }
  }
  return null
}

/** What YouTube makes of this browser's session, each way of sending it — for a session it won't take. */
export async function diagnose(): Promise<string[]> {
  const lines: string[] = []
  const names = await extensionCall<string[]>('yt-cookies').catch((e) => [`ошибка: ${(e as Error).message}`])
  const key = ['SID', 'HSID', 'SSID', 'APISID', 'SAPISID', '__Secure-1PSID', '__Secure-3PSID', '__Secure-1PSIDTS', '__Secure-3PSIDTS', 'LOGIN_INFO']
  lines.push(`Cookie YouTube в браузере: ${names.length}. Ключевые: ${key.map((k) => `${k} ${names.includes(k) ? '✓' : '✗'}`).join(', ')}`)
  for (const mode of SESSION_MODES) {
    const page = await getText(ORIGIN + '/', { session: mode })
      .then((html) =>
        /"LOGGED_IN":true/.test(html)
          ? 'страница: вошёл'
          : /"LOGGED_IN":false/.test(html)
            ? 'страница: гость'
            : /consent\.(youtube|google)\.com/.test(html)
              ? 'страница: просит согласие с cookie'
              : 'страница: ?',
      )
      .catch((e) => `страница: ошибка ${(e as Error).message.slice(0, 60)}`)
    const api = await accountNameWith(mode)
      .then((n) => (n ? `API: ${n}` : 'API: гость'))
      .catch((e) => `API: ошибка ${(e as Error).message.slice(0, 80)}`)
    lines.push(`${mode}: ${page}, ${api}`)
  }
  return lines
}

export async function setLiked(t: Track, liked: boolean): Promise<void> {
  const client = await yt()
  const actions: any = (client as any).actions
  await actions.execute(liked ? '/like/like' : '/like/removelike', { target: { videoId: t.yt!.videoId }, client: 'YTMUSIC' })
}

// ---------------------------------------------------------------------------------- playback

// The Android VR client: its player answers with plain stream URLs, no script to unscramble them.
const VR_CLIENT = {
  clientName: 'ANDROID_VR',
  clientVersion: '1.62.27',
  deviceMake: 'Oculus',
  deviceModel: 'Quest 3',
  androidSdkVersion: 32,
  osName: 'Android',
  osVersion: '12L',
  hl: 'ru',
}
const VR_UA = 'com.google.android.apps.youtube.vr.oculus/1.62.27 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip'

export interface Playability {
  status: string
  reason: string
  audio: { url: string; mimeType: string; bitrate: number }[]
}

/** Whether YouTube will hand this browser [videoId]'s sound without signing in, and where it is. */
export async function playability(videoId: string): Promise<Playability> {
  const answer = await postJson(
    'https://www.youtube.com/youtubei/v1/player?prettyPrint=false',
    { videoId, context: { client: VR_CLIENT } },
    { ua: VR_UA, origin: null, referer: null },
  )
  const formats: any[] = at(answer, 'streamingData', 'adaptiveFormats') ?? []
  return {
    status: at(answer, 'playabilityStatus', 'status') ?? 'UNKNOWN',
    reason: at(answer, 'playabilityStatus', 'reason') ?? '',
    audio: formats
      .filter((f) => f.mimeType?.startsWith('audio/') && f.url)
      .map((f) => ({ url: f.url, mimeType: f.mimeType, bitrate: f.bitrate }))
      .sort((a, b) => b.bitrate - a.bitrate),
  }
}

// In development only: what home's pages hold, shelf by shelf, item types and all.
if (import.meta.env.DEV) {
  ;(globalThis as any).__ycHomeDebug = async () => {
    const client = await yt()
    const feed: any = await client.music.getHomeFeed()
    const out: string[] = []
    const tabs = feed.page?.contents?.item?.()?.tabs
    out.push(`tabs: ${tabs?.length}, selected content: ${tabs?.find((t: any) => t.selected)?.content?.type}`)
    const token = tabs?.find((t: any) => t.selected)?.content?.continuation
    out.push(`token: ${String(token).slice(0, 40)} has_continuation=${feed.has_continuation}`)
    if (!token) return out
    out.push(`visitor: ${String((client as any).session?.context?.client?.visitorData).slice(0, 20)}`)
    const raw: any = await browseContinuation(client, token)
    out.push(`raw keys: ${Object.keys(raw).join(', ')}`)
    const shape = (v: any, depth: number): string =>
      depth > 6 || v == null || typeof v !== 'object'
        ? ''
        : Array.isArray(v)
          ? `[${v.length}]${shape(v[0], depth + 1)}`
          : `{${Object.keys(v).slice(0, 6).map((k) => k + shape(v[k], depth + 1)).join(',')}}`
    out.push(`contents shape: ${shape(raw.contents, 0).slice(0, 700)}`)
    out.push(`continuationContents: ${Object.keys(raw.continuationContents ?? {}).join(', ')}`)
    const { Parser } = await import('youtubei.js/web')
    const parsed: any = Parser.parseResponse(raw)
    const next = parsed.continuation_contents
    out.push(`parsed: ${next?.type}, contents ${next?.contents?.length}, next token ${!!next?.continuation}`)
    for (const shelf of next?.contents ?? []) out.push(`  ${shelf.type} "${text(shelf.header?.title)}": ${(shelf.contents ?? []).length}`)
    return out
  }
}

// In development only: runs a made-up stream URL through the player script, to see the
// unscrambling work without a video YouTube would play here.
if (import.meta.env.DEV) {
  ;(globalThis as any).__ycDecipherCheck = async () => {
    const client = await ytPlayer()
    const before = 'https://rr1---sn-test.googlevideo.com/videoplayback?expire=1999999999&c=TVHTML5&n=AbCdEfGhIjKlMnOp'
    const after = await client.session.player!.decipher(before)
    return { player: client.session.player?.player_id, before, after }
  }
}

// In development only: what each client answers for [videoId], and the stream URL it gives.
if (import.meta.env.DEV) {
  ;(globalThis as any).__ycStreamDebug = async (videoId: string, names: string[]) => {
    const client = await ytPlayer()
    const out: any[] = []
    for (const name of names) {
      try {
        const info: any = await client.getBasicInfo(videoId, { client: name as any })
        const format = pickAudio(info.streaming_data)
        out.push({
          name,
          status: info.playability_status?.status,
          reason: info.playability_status?.reason,
          formats: info.streaming_data?.adaptive_formats?.length ?? 0,
          url: format ? await format.decipher(client.session.player) : null,
          player: client.session.player?.player_id,
        })
      } catch (e) {
        out.push({ name, error: String((e as Error).message ?? e) })
      }
    }
    return out
  }
}

const SIGNED_IN_CLIENTS = ['TV', 'WEB_EMBEDDED', 'TV_SIMPLY', 'MWEB'] as const

const reachable = (f: any) => !!(f.url || f.signature_cipher || f.cipher)

/**
 * The format to listen to: audio alone, with an address to fetch it from; the original track
 * rather than a dub, without the loudness-squashed (DRC) copy, at the best bitrate. Failing that,
 * a format with picture and sound together, which an <audio> plays just as well.
 */
function pickAudio(streaming: any): any | null {
  const audio = ((streaming?.adaptive_formats ?? []) as any[])
    .filter((f) => f.has_audio && !f.has_video && reachable(f))
    .sort(
      (a, b) =>
        Number(b.is_original ?? true) - Number(a.is_original ?? true) ||
        Number(!!a.is_drc) - Number(!!b.is_drc) ||
        (b.bitrate ?? 0) - (a.bitrate ?? 0),
    )
  if (audio[0]) return audio[0]
  return ((streaming?.formats ?? []) as any[]).filter((f) => f.has_audio && reachable(f)).sort((a, b) => (b.bitrate ?? 0) - (a.bitrate ?? 0))[0] ?? null
}

// Stream URLs are signed for a few hours; within that they're reused — unless one won't play,
// when the client that gave it is passed over for that video next time.
const streams = new Map<string, { url: string; until: number; client: string }>()
const refused = new Map<string, Set<string>>()

/** [videoId]'s stream wouldn't play: forget it, and whether another client is left to ask. */
export function streamFailed(videoId: string): boolean {
  const kept = streams.get(videoId)
  if (!kept) return false
  streams.delete(videoId)
  const skip = refused.get(videoId) ?? new Set<string>()
  skip.add(kept.client)
  refused.set(videoId, skip)
  return ['VR', ...SIGNED_IN_CLIENTS].some((c) => !skip.has(c))
}

/** The track's sound: Android VR when YouTube lets it, else the TV client signed in. */
export async function stream(t: Track): Promise<string> {
  const videoId = t.yt!.videoId
  const kept = streams.get(videoId)
  if (kept && kept.until > Date.now()) return kept.url
  let url: string | null = null
  let from = 'VR'
  let refusal = ''
  const skip = refused.get(videoId) ?? new Set<string>()
  if (!skip.has('VR')) {
    try {
      const vr = await playability(videoId)
      if (vr.status === 'OK' && vr.audio[0]) url = vr.audio[0].url
      else refusal = vr.reason || vr.status
    } catch (e) {
      refusal = (e as Error).message
    }
  }
  if (!url) {
    if (!(await extension())) throw new Error(`YouTube не отдал звук (${refusal}). Нужно расширение YouCloud`)
    const client = await ytPlayer()
    // Clients that take the listener's session, likeliest first. A client YouTube has moved to
    // its own streaming protocol (SABR) answers with formats that have no URL at all; the next
    // one is asked then.
    for (const name of SIGNED_IN_CLIENTS) {
      if (skip.has(name)) continue
      let info: any
      try {
        info = await client.getBasicInfo(videoId, { client: name })
      } catch (e) {
        refusal = (e as Error).message
        continue
      }
      const status = info.playability_status?.status
      if (status !== 'OK') {
        refusal = info.playability_status?.reason ?? status
        continue
      }
      const format = pickAudio(info.streaming_data)
      if (!format) {
        refusal = 'YouTube отдал только поток SABR, без ссылок'
        continue
      }
      url = await format.decipher(client.session.player)
      from = name
      break
    }
    if (!url) throw new Error((await signedIn()) ? `YouTube: ${refusal}` : `YouTube просит войти — войди на youtube.com в этом браузере (${refusal})`)
  }
  const expire = Number(new URL(url!).searchParams.get('expire')) * 1000
  streams.set(videoId, { url: url!, until: (expire || Date.now() + 3_600_000) - 10 * 60_000, client: from })
  return url!
}

// ---------------------------------------------------------------------------------- connection check

let visitor: string | null = null

function clientVersion(): string {
  const d = new Date()
  const ymd = `${d.getUTCFullYear()}${String(d.getUTCMonth() + 1).padStart(2, '0')}${String(d.getUTCDate()).padStart(2, '0')}`
  return `1.${ymd}.01.00`
}

async function visitorData(): Promise<string | null> {
  if (visitor) return visitor
  try {
    const page = await getText(ORIGIN + '/')
    visitor = page.match(/"VISITOR_DATA":"([^"]+)"/)?.[1] ?? null
  } catch {
    visitor = null
  }
  return visitor
}

const FILTER_SONGS = 'EgWKAQIIAWoQEAUQCRADEAQQChAREBAQFQ=='

export interface YtSong {
  videoId: string
  title: string
  artists: string
}

/** Songs for [query] straight from InnerTube: the connection check's light probe. */
export async function searchSongs(query: string): Promise<YtSong[]> {
  const id = await visitorData()
  const headers: Record<string, string> = { 'x-origin': ORIGIN }
  if (id) headers['x-goog-visitor-id'] = id
  const answer = await postJson(
    `${API}search?prettyPrint=false`,
    { query, params: FILTER_SONGS, context: { client: { clientName: 'WEB_REMIX', clientVersion: clientVersion(), hl: 'ru' }, user: {} } },
    { headers },
  )
  const tabs = at(answer, 'contents', 'tabbedSearchResultsRenderer', 'tabs', 0, 'tabRenderer', 'content', 'sectionListRenderer', 'contents') ?? []
  const songs: YtSong[] = []
  for (const section of tabs) {
    for (const item of at(section, 'musicShelfRenderer', 'contents') ?? []) {
      const row = item.musicResponsiveListItemRenderer
      const videoId = at(row, 'playlistItemData', 'videoId')
      const title = at(row, 'flexColumns', 0, 'musicResponsiveListItemFlexColumnRenderer', 'text', 'runs', 0, 'text')
      const byline = (at(row, 'flexColumns', 1, 'musicResponsiveListItemFlexColumnRenderer', 'text', 'runs') ?? [])
        .map((r: { text: string }) => r.text)
        .join('')
      if (videoId && title) songs.push({ videoId, title, artists: byline.split(' • ')[0] ?? '' })
    }
  }
  return songs
}
