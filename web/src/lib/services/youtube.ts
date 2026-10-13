// YouTube Music. Pages come from InnerTube through youtubei.js, loaded only once YouTube is opened.
// Its requests go through the extension when the listener has it — from their own IP, signed in
// as this browser is on YouTube — or through the Worker otherwise.
//
// Sound, as the app gets it: first the Android VR client, whose player hands out plain stream
// URLs to anyone YouTube trusts; where it asks to "confirm you're not a bot", the TV client
// signed in, whose URLs are unscrambled by YouTube's own player script. That script runs in a
// Worker of its own, with nothing of the page in reach.

import { i18n, tr } from '../i18n.svelte'
import type { Innertube } from 'youtubei.js/web'
import { artists as artistIndex, colorFor, putArtist, putSet, putTrack, sets, tracks as catalogTracks, type SetKind, type Track, type TrackSet } from '../catalog'
import { accounts } from '../accounts.svelte'
import { extension, extensionCall, extensionHost, hostStream, hostStreams, mediaRelay } from '../extension'
import { at, getText, HttpError, postJson, request } from '../net'

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
  const response = await request(target, { method, headers, body: body as string | undefined, ua, session: sessionMode })
  // youtubei.js would report a refusal as "Request to  failed with status code 400": a Response
  // made from the extension's answer has no address, and the body saying why is dropped.
  if (!response.ok) throw new HttpError(response.status, await response.text().catch(() => ''), target)
  return response
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
    lang: i18n.lang,
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
    title: text(item.title) || tr('Без названия'),
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
    title: text(item.title) || tr('Подборка'),
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
    name: text(item.title) || item.name || tr('Артист'),
    service: 'yt',
    image: picture(thumbsOf(item)),
    color: colorFor(id),
    followers: item.subscribers ? `${item.subscribers}`.replace(/subscribers?/i, tr('подписчиков')) : undefined,
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
    { context: { client: { clientName: 'WEB_REMIX', clientVersion: clientVersion(), hl: i18n.lang, gl: 'RU', visitorData: accounts.yt ? undefined : visitor }, user: {} } },
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
    putSet({ id: 'yt:pl:LM', kind: 'liked', service: 'yt', title: tr('Понравившаяся музыка'), subtitle: tr('Автоплейлист'), color: '#e53935', tracks: [], cover: null })
    // Home puts the liked playlist in a different row each time, or leaves it out; it goes first.
    for (const section of sections) section.sets = section.sets.filter((id) => id !== 'yt:pl:LM')
    const firstSets = sections.find((section) => section.sets.length)
    if (firstSets) firstSets.sets.unshift('yt:pl:LM')
    else sections.unshift({ title: tr('Твоя медиатека'), sets: ['yt:pl:LM'] })
  }
  const kept = sections.filter((section) => section.sets.length || section.tracks?.length)
  // Home's songs come without their length; asked for, but home doesn't wait long for them.
  const songs = kept.flatMap((section) => section.tracks ?? [])
  await Promise.race([fillLengths(songs).catch(() => {}), new Promise((done) => setTimeout(done, 3000))])
  return kept
}

/** "3:36", "1:02:03", "33 778:11:52" in seconds; null for what isn't a length. */
function lengthOf(text: string | undefined): number | null {
  if (!text) return null
  const parts = text.replace(/\s/g, '').split(':').map(Number)
  if (parts.length < 2 || parts.some((n) => !Number.isFinite(n))) return null
  return parts.reduce((sum, n) => sum * 60 + n, 0)
}

/** Longer than any song: a broadcast's running time (one 24/7 stream's ran past 33 000 hours). */
const LIVE_AFTER_S = 24 * 3600

/**
 * The lengths of [ids] that have none — home's songs, which YouTube Music shows without one — as
 * its player asks for a queue, fifty at a time. A broadcast is marked live instead: the queue
 * gives it a «Прямая трансляция» badge and no length, or (a 24/7 one, before) its running time.
 */
async function fillLengths(ids: string[]): Promise<void> {
  const missing = [...new Set(ids)].filter((id) => {
    const t = catalogTracks.get(id)
    return t?.yt && !t.duration && !t.live
  })
  if (!missing.length) return
  const actions: any = ((await yt()) as any).actions
  const chunks: string[][] = []
  for (let i = 0; i < missing.length; i += 50) chunks.push(missing.slice(i, i + 50))
  await Promise.all(
    chunks.map(async (chunk) => {
      const answer = await actions.execute('/music/get_queue', { client: 'YTMUSIC', videoIds: chunk.map((id) => catalogTracks.get(id)!.yt!.videoId), parse: false })
      for (const q of answer?.data?.queueDatas ?? []) {
        const v = q.content?.playlistPanelVideoRenderer ?? q.content?.playlistPanelVideoWrapperRenderer?.primaryRenderer?.playlistPanelVideoRenderer
        const t = v?.videoId && catalogTracks.get(`yt:t:${v.videoId}`)
        if (!t) continue
        if ((v.badges ?? []).some((b: any) => b.liveBadge || b.liveBadgeRenderer)) {
          t.live = true
          continue
        }
        const seconds = lengthOf(v.lengthText?.runs?.[0]?.text ?? v.lengthText?.simpleText)
        if (seconds == null) continue
        if (seconds > LIVE_AFTER_S) t.live = true
        else t.duration = seconds
      }
    }),
  )
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
    name: text(header?.title) || tr('Артист'),
    service: 'yt',
    image: picture(thumbsOf(header?.thumbnail)),
    color: colorFor(id),
    followers: header?.subscription_button?.subscriber_count?.toString?.()
      ? tr('%s подписчиков', header.subscription_button.subscriber_count)
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
  // A video found without a length is mostly a broadcast: the queue says, briefly waited for.
  await Promise.race([fillLengths(tracks).catch(() => {}), new Promise((done) => setTimeout(done, 2000))])
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
  putSet({ id: 'yt:pl:LM', kind: 'liked', service: 'yt', title: tr('Понравившаяся музыка'), subtitle: 'YouTube Music', color: '#e53935', tracks: [], cover: null })
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
    { context: { client: { clientName: 'WEB_REMIX', clientVersion: clientVersion(), hl: i18n.lang }, user: {} } },
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
  const names = await extensionCall<string[]>('yt-cookies').catch((e) => [tr('ошибка: %s', (e as Error).message)])
  const key = ['SID', 'HSID', 'SSID', 'APISID', 'SAPISID', '__Secure-1PSID', '__Secure-3PSID', '__Secure-1PSIDTS', '__Secure-3PSIDTS', 'LOGIN_INFO']
  lines.push(tr('Cookie YouTube в браузере: %s. Ключевые: %s', names.length, key.map((k) => `${k} ${names.includes(k) ? '✓' : '✗'}`).join(', ')))
  for (const mode of SESSION_MODES) {
    const page = await getText(ORIGIN + '/', { session: mode })
      .then((html) =>
        /"LOGGED_IN":true/.test(html)
          ? tr('страница: вошёл')
          : /"LOGGED_IN":false/.test(html)
            ? tr('страница: гость')
            : /consent\.(youtube|google)\.com/.test(html)
              ? tr('страница: просит согласие с cookie')
              : tr('страница: ?'),
      )
      .catch((e) => tr('страница: ошибка %s', (e as Error).message.slice(0, 60)))
    const api = await accountNameWith(mode)
      .then((n) => (n ? `API: ${n}` : tr('API: гость')))
      .catch((e) => tr('API: ошибка %s', (e as Error).message.slice(0, 80)))
    lines.push(`${mode}: ${page}, ${api}`)
  }
  return lines
}

export async function setLiked(t: Track, liked: boolean): Promise<void> {
  const client = await yt()
  const actions: any = (client as any).actions
  await actions.execute(liked ? '/like/like' : '/like/removelike', { target: { videoId: t.yt!.videoId }, client: 'YTMUSIC' })
}

/** A thumb down: YouTube Music offers less like it. */
export async function dislike(t: Track): Promise<void> {
  const client = await yt()
  const actions: any = (client as any).actions
  await actions.execute('/like/dislike', { target: { videoId: t.yt!.videoId }, client: 'YTMUSIC' })
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
  hl: i18n.lang,
}
const VR_UA = 'com.google.android.apps.youtube.vr.oculus/1.62.27 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip'

export interface Playability {
  status: string
  reason: string
  audio: { url: string; mimeType: string; bitrate: number }[]
  /** A broadcast going on now: its sound is [hls], the HLS playlist. */
  live: boolean
  hls: string | null
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
      // A broadcast's adaptive formats are its pieces of a few seconds (targetDurationSec), not files.
      .filter((f) => f.mimeType?.startsWith('audio/') && f.url && !f.targetDurationSec)
      .map((f) => ({ url: f.url, mimeType: f.mimeType, bitrate: f.bitrate }))
      .sort((a, b) => b.bitrate - a.bitrate),
    live: !!at(answer, 'videoDetails', 'isLive'),
    hls: at(answer, 'streamingData', 'hlsManifestUrl') ?? null,
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

/**
 * Clients that take the listener's session. Measured 2026-10-10 from a home IP YouTube asks to
 * "confirm you're not a bot": TV_SIMPLY refuses a session outright (400 INVALID_ARGUMENT), and
 * MWEB's links play the first megabyte and then answer 403 — the fetch wants a PO token — so
 * both are left out. TV answers "the page needs to be reloaded" to some accounts (YouTube's test
 * since September 2026, yt-dlp issue 17389) and WEB_EMBEDDED only plays where embedding is
 * allowed; they stay, for the accounts and videos they still serve.
 */
const SIGNED_IN_CLIENTS = ['TV', 'WEB_EMBEDDED'] as const

// A file to fetch: not a broadcast's piece of a few seconds (target_duration_sec), which a
// broadcast's adaptive formats are.
const reachable = (f: any) => !!(f.url || f.signature_cipher || f.cipher) && !f.target_duration_sec

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
const streams = new Map<string, { url: string; until: number; client: string; live: boolean }>()
const refused = new Map<string, Set<string>>()

/** An HLS playlist rather than a file: how YouTube and yt-dlp give a broadcast's sound. */
const isHls = (url: string) => /\/api\/manifest\/hls_|\.m3u8(\?|$)/.test(url)

/** When [url] runs out, ms: its `expire`, which a broadcast's playlist carries in its path. */
function expiryOf(url: string): number {
  const u = new URL(url)
  const seconds = Number(u.searchParams.get('expire') ?? u.pathname.match(/\/expire\/(\d+)/)?.[1])
  return seconds > 0 ? seconds * 1000 : Date.now() + 3_600_000
}

/**
 * A broadcast's HLS playlist as YouTube's player would fetch it: an `n` in its path (/n/…/) is
 * unscrambled by the player script as a stream URL's is in its query — so asked on a made-up one.
 */
async function unscrambled(manifest: string): Promise<string> {
  const n = new URL(manifest).pathname.match(/\/n\/([^/]+)\//)?.[1]
  if (!n) return manifest
  const client = await ytPlayer()
  const probe = await client.session.player!.decipher(`https://rr1---sn-yc.googlevideo.com/videoplayback?n=${n}`)
  const solved = new URL(probe).searchParams.get('n')
  return solved ? manifest.replace(`/n/${n}/`, `/n/${solved}/`) : manifest
}

/** [videoId]'s stream wouldn't play: forget it, and whether another client is left to ask. */
export function streamFailed(videoId: string): boolean {
  const kept = streams.get(videoId)
  if (!kept) return false
  streams.delete(videoId)
  const skip = refused.get(videoId) ?? new Set<string>()
  skip.add(kept.client)
  refused.set(videoId, skip)
  return ['YTDLP', 'VR', ...SIGNED_IN_CLIENTS].some((c) => !skip.has(c))
}

export type Stream = { url: string; hls: boolean; live: boolean; bridge: boolean }

/** A broadcast plays only with its pieces fetched by the extension or Masaki: one too old for that is named. */
function liveRelayed() {
  if (mediaRelay()) return
  throw new Error(
    extensionHost() === 'masaki'
      ? tr('Эфиры YouTube играют в Masaki 0.8 и новее — обнови Masaki')
      : tr('Эфиры YouTube играют с расширением 0.6.1 и новее — обнови его в Настройках'),
  )
}

/**
 * The track's sound: in Masaki or with the helper, yt-dlp's; elsewhere Android VR when YouTube
 * lets it, else the clients signed in — which, in a plain browser, YouTube refuses for some
 * tracks altogether. A broadcast's sound is its HLS playlist, and the track is marked live
 * whichever way that came out.
 */
export async function stream(t: Track): Promise<Stream> {
  const videoId = t.yt!.videoId
  const kept = streams.get(videoId)
  if (kept && kept.until > Date.now()) {
    if (kept.live) liveRelayed()
    return { url: kept.url, hls: kept.live, live: kept.live, bridge: kept.live }
  }
  let url: string | null = null
  let live = !!t.live
  let from = 'VR'
  let refusal = ''
  // What the first client said is what the listener can act on ("confirm you're not a bot");
  // the signed-in ones' refusals after it only add to it.
  let first = ''
  const skip = refused.get(videoId) ?? new Set<string>()
  // Masaki or the helper ask yt-dlp, which gets past what the clients below run into; they stay for
  // when it can't.
  if (!skip.has('YTDLP') && (await extension()) && (await hostStreams())) {
    try {
      const found = await hostStream(videoId, live)
      url = found.url
      live = !!found.live || isHls(found.url)
      from = 'YTDLP'
    } catch (e) {
      refusal = first = `yt-dlp: ${(e as Error).message}`
    }
  }
  if (!url && !skip.has('VR')) {
    try {
      const vr = await playability(videoId)
      if (vr.status === 'OK' && vr.live && vr.hls) {
        url = await unscrambled(vr.hls)
        live = true
      } else if (vr.status === 'OK' && vr.audio[0]) url = vr.audio[0].url
      else refusal = first = vr.reason || vr.status
    } catch (e) {
      refusal = first = (e as Error).message
    }
  }
  if (!url) {
    if (!(await extension())) throw new Error(tr('YouTube не отдал звук (%s). Нужно расширение YouCloud', refusal))
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
      // A broadcast: the HLS playlist with all its qualities, which the browser picks from.
      if (info.basic_info?.is_live && info.streaming_data?.hls_manifest_url) {
        url = await unscrambled(info.streaming_data.hls_manifest_url)
        live = true
        from = name
        break
      }
      const format = pickAudio(info.streaming_data)
      if (!format) {
        refusal = tr('YouTube отдал только поток SABR, без ссылок')
        continue
      }
      url = await format.decipher(client.session.player)
      from = name
      break
    }
    if (!url) {
      if (!(await signedIn())) throw new Error(tr('YouTube просит войти — войди на youtube.com в этом браузере (%s)', first || refusal))
      throw new Error(first && first !== refusal ? tr('YouTube не отдал звук: %s; с входом — %s', first, refusal) : `YouTube: ${refusal}`)
    }
  }
  streams.set(videoId, { url: url!, until: expiryOf(url!) - 10 * 60_000, client: from, live })
  // Told by its sound: a broadcast found as one, or a recording of one that has ended.
  t.live = live
  if (live) liveRelayed()
  return { url: url!, hls: live, live, bridge: live }
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
    { query, params: FILTER_SONGS, context: { client: { clientName: 'WEB_REMIX', clientVersion: clientVersion(), hl: i18n.lang }, user: {} } },
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
