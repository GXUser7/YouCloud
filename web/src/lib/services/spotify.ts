// Spotify via Web Player internals (open.spotify.com + Pathfinder GraphQL + spclient): no
// Developer Dashboard keys required. Metadata, personal library, liked tracks, playlists, mixes,
// radio, search and lyrics come straight from Spotify; audio is matched by title, artist and
// duration through YouTube Music first, with SoundCloud (and Yandex when signed in) as fallbacks.

import { i18n, tr } from '../i18n.svelte'
import { accounts, setSpotify, spClientToken, spToken } from '../accounts.svelte'
import {
  artists as catalogArtists,
  colorFor,
  compact,
  countWords,
  defaultCatalog,
  putArtist,
  putSet,
  putTrack,
  sets,
  tracks as catalogTracks,
  type CatalogContext,
  type SetKind,
  type Track,
  type TrackSet,
} from '../catalog'
import { extension, extensionCall, spotifySupported } from '../extension'
import type { ArtistPage, LibraryOptions, ResultPage, SearchResult, SearchSection, Section, Stream } from '../live'
import { at, getJson, getText, HttpError, postJson } from '../net'
import * as soundcloud from './soundcloud'
import * as yandex from './yandex'
import * as youtube from './youtube'

const fallbackCatalog: CatalogContext = defaultCatalog ?? {
  tracks: catalogTracks,
  sets,
  artists: catalogArtists,
  track: (id) => catalogTracks.get(id)!,
  set: (id) => sets.get(id)!,
  artist: (id) => catalogArtists.get(id)!,
  putTrack,
  putSet,
  putArtist,
}

const SITE = 'https://open.spotify.com/'
const PATHFINDER = 'https://api-partner.spotify.com/pathfinder/v2/query'
const CLIENTTOKEN_URL = 'https://clienttoken.spotify.com/v1/clienttoken'
const SPCLIENT = 'https://spclient.wg.spotify.com/'
const BUNDLE_KEY = 'yc.sp.bundle'
const DEVICE_ID_KEY = 'yc.sp.deviceId'
const BUNDLE_TTL_MS = 6 * 3600_000

const FALLBACK_HASHES: Record<string, string> = {
  home: '5fb7da7a03ee2776c0670856609cb85a189a3cecc36cc160b91cde79063b9cf1',
  getAlbum: '0b6ce3511e1b02e4a40fa95440f992acbe85080eb308af9194322d708fc1914f',
  fetchPlaylist: '21ea11a6ac75a5f3e01fd365d29c18e0a4a8b7dc09f89bbb5d73910190c6acaf',
  queryArtistOverview: 'eafb1620d783ece118e72fbfede2b0064483e029d3c1aafafa1c2de0e25a89e7',
  getTrack: 'e7b1bf4e95cb3d733d48b442f93006e9712fd504e8b88d7a82e6e3bac9306a6b',
  fetchLibraryTracks: '087278b20b743578a6262c2b0b4bcd20d879c503cc359a2285baf083ef944240',
  libraryV3: '390c78e5b951029bad359785e69b07b536a509c581cbcd0aded5e5067f187455',
  areEntitiesInLibrary: '134337999233cc6fdd6b1e6dbf94841409f04a946c5c7b744b09ba0dfe5a85ed',
  addToLibrary: '896ebcb47815681340860d121cb5d494e157e2a78d3950385cd54e0393c67148',
  removeFromLibrary: '896ebcb47815681340860d121cb5d494e157e2a78d3950385cd54e0393c67148',
  profileAttributes: '08ffb4730af3746e04a8301396f20875dbbce10c75243803091a9274eacc8ac0',
  searchDesktop: 'eef7cc54888d91bdd6802623477873caa3948ae173a0c34fd86827b267e94c03',
  searchTracks: 'b02683192a98dde7966b5e6655a79eeb62713eab703eda9902c932818dd52751',
  searchArtists: '7bf95d754fdbe32c8b161fbbe54d1ae50974900df4dce4c8f1afcbcad153224d',
  searchAlbums: '202cb3305e31e5a0767ba7925f28bd728cf8f8b0217e6da43909056071cd70e9',
  searchPlaylists: 'd520014e748f9ea44f7707d8df1819867ac1205e8b7f3e28f22fe5fc858921b1',
}

interface BundleState {
  at: number
  buildVersion: string
  totpVer: string
  totpSecretHex: string
  hashes: Record<string, string>
}

interface AuthSession {
  token: string
  clientToken: string
  expiresAt: number
}

// ---------------------------------------------------------------------------------- bundle & TOTP

let bundleCache: BundleState | null = null
let bundleLoading: Promise<BundleState> | null = null
let anonSession: AuthSession | null = null
let anonLoading: Promise<AuthSession> | null = null
let userRenewing: Promise<AuthSession | null> | null = null
let userRenewFailedAt = 0

function storedBundle(): BundleState | null {
  try {
    const raw = localStorage.getItem(BUNDLE_KEY)
    if (!raw) return null
    const b = JSON.parse(raw) as BundleState
    if (b?.at && Date.now() - b.at < BUNDLE_TTL_MS && b.hashes?.home && b.totpSecretHex) return b
  } catch {
    // ignore corrupt cache
  }
  return null
}

function deviceId(): string {
  try {
    const kept = localStorage.getItem(DEVICE_ID_KEY)
    if (kept) return kept
    const next = crypto.randomUUID()
    localStorage.setItem(DEVICE_ID_KEY, next)
    return next
  } catch {
    return crypto.randomUUID()
  }
}

function unescapeJsString(raw: string): string {
  return raw.replace(/\\(x[0-9a-fA-F]{2}|u[0-9a-fA-F]{4}|u\{[0-9a-fA-F]+\}|.)/g, (_, esc: string) => {
    if (esc === 'n') return '\n'
    if (esc === 'r') return '\r'
    if (esc === 't') return '\t'
    if (esc === 'b') return '\b'
    if (esc === 'f') return '\f'
    if (esc === 'v') return '\v'
    if (esc === '0') return '\0'
    if (esc.startsWith('x')) return String.fromCharCode(parseInt(esc.slice(1), 16))
    if (esc.startsWith('u{')) return String.fromCodePoint(parseInt(esc.slice(2, -1), 16))
    if (esc.startsWith('u')) return String.fromCharCode(parseInt(esc.slice(1), 16))
    return esc
  })
}

function decodeBase64Json<T = any>(b64: string): T {
  const bytes = Uint8Array.from(atob(b64), (c) => c.charCodeAt(0))
  return JSON.parse(new TextDecoder().decode(bytes)) as T
}

function extractOps(js: string, into: Record<string, string>) {
  for (const m of js.matchAll(/["']([a-zA-Z0-9_]+)["']\s*,\s*["'](?:query|mutation)["']\s*,\s*["']([a-f0-9]{64})["']/g)) {
    into[m[1]] = m[2]
  }
}

function extractTotp(js: string): { totpVer: string; totpSecretHex: string } {
  const idx = js.indexOf('e.charCodeAt(0)^t%33+9')
  if (idx > 0) {
    const slice = js.slice(Math.max(0, idx - 500), idx)
    const m = slice.match(/\{secret:(['"`])((?:\\.|(?!\1).)+)\1,version:(\d+)\}/)
    if (m) {
      const secret = unescapeJsString(m[2])
      const xored = [...secret].map((ch, i) => String(ch.charCodeAt(0) ^ ((i % 33) + 9))).join('')
      const bytes = new TextEncoder().encode(xored)
      const hex = [...bytes].map((b) => b.toString(16).padStart(2, '0')).join('')
      return { totpVer: m[3], totpSecretHex: hex }
    }
  }
  throw new Error(tr('Spotify не отдал параметры входа'))
}

async function scrapeBundle(withRoutes = false): Promise<BundleState> {
  const html = await getText(SITE, { timeoutMs: 25_000 })
  const cdnRaw = html.match(/<script[^>]+id="__CDN_FILE_URLS__"[^>]*>([\s\S]*?)<\/script>/)?.[1]
  const cdn = cdnRaw ? decodeBase64Json<Record<string, string>>(cdnRaw) : {}
  const mainUrl =
    cdn['build/web-player/web-player.js'] ??
    [...html.matchAll(/<script[^>]+src=["']([^"']*web-player\.[a-f0-9]+\.js)["']/g)].map((m) => m[1])[0]
  if (!mainUrl) throw new Error(tr('Spotify не отдал веб-плеер'))

  // Fetch web-player.js alone with a generous timeout first so 4 route chunks never congest it past AbortSignal.
  const mainJs = await getText(mainUrl, { timeoutMs: 45_000 }).catch(() => getText(mainUrl, { timeoutMs: 45_000 }).catch(() => ''))
  if (!mainJs) throw new Error(tr('Spotify не отдал скрипт плеера'))

  const routeUrls = withRoutes
    ? [
        cdn['build/web-player/xpui-routes-search.js'],
        cdn['build/web-player/xpui-routes-artist.js'],
        cdn['build/web-player/xpui-routes-album.js'],
        cdn['build/web-player/xpui-routes-playlist.js'],
      ].filter((u): u is string => !!u)
    : []
  const routeScripts = routeUrls.length
    ? await Promise.all(routeUrls.map((u) => getText(u, { timeoutMs: 45_000 }).catch(() => '')))
    : []

  const hashes: Record<string, string> = { ...FALLBACK_HASHES }
  for (const s of [mainJs, ...routeScripts]) if (s) extractOps(s, hashes)
  const { totpVer, totpSecretHex } = extractTotp(mainJs)
  const buildVersion = mainJs.match(/buildVersion["']?\s*[:=]\s*["']([^"']+)["']/)?.[1] || '1.2.84.0'

  const bundle: BundleState = { at: Date.now(), buildVersion, totpVer, totpSecretHex, hashes }
  try {
    localStorage.setItem(BUNDLE_KEY, JSON.stringify(bundle))
  } catch {
    // kept in memory
  }
  return bundle
}

async function getBundle(fresh = false): Promise<BundleState> {
  if (!fresh) {
    bundleCache ??= storedBundle()
    if (bundleCache) return bundleCache
  }
  bundleLoading ??= scrapeBundle(fresh)
    .then((b) => (bundleCache = b))
    .finally(() => (bundleLoading = null))
  return bundleLoading
}

async function generateTotp(secretHex: string, ms: number): Promise<string> {
  const bytes = new Uint8Array(secretHex.length / 2)
  for (let i = 0; i < bytes.length; i++) bytes[i] = parseInt(secretHex.slice(i * 2, i * 2 + 2), 16)
  const counter = Math.floor(ms / 1000 / 30)
  const msg = new Uint8Array(8)
  new DataView(msg.buffer).setBigUint64(0, BigInt(counter))
  const key = await crypto.subtle.importKey('raw', bytes, { name: 'HMAC', hash: 'SHA-1' }, false, ['sign'])
  const hmac = new Uint8Array(await crypto.subtle.sign('HMAC', key, msg))
  const offset = hmac[hmac.length - 1] & 0x0f
  const code =
    ((hmac[offset] & 0x7f) << 24) |
    ((hmac[offset + 1] & 0xff) << 16) |
    ((hmac[offset + 2] & 0xff) << 8) |
    (hmac[offset + 3] & 0xff)
  return String(code % 1_000_000).padStart(6, '0')
}

async function buildTokenParams(bundle: BundleState): Promise<URLSearchParams> {
  let serverMs = Date.now()
  try {
    const st = await getJson<{ serverTime?: number }>(`${SITE}api/server-time`)
    if (st?.serverTime) serverMs = Number(st.serverTime) * 1000
  } catch {
    // local clock fallback
  }
  const now = Date.now()
  const [totp, totpServer] = await Promise.all([
    generateTotp(bundle.totpSecretHex, now),
    generateTotp(bundle.totpSecretHex, serverMs),
  ])
  return new URLSearchParams({
    reason: 'init',
    productType: 'web-player',
    totp,
    totpServer,
    totpVer: bundle.totpVer,
  })
}

function clientTokenBody(clientVersion: string, clientId: string) {
  return {
    client_data: {
      client_version: clientVersion,
      client_id: clientId,
      js_sdk_data: {
        device_brand: 'unknown',
        device_model: 'unknown',
        os: 'windows',
        os_version: 'NT 10.0',
        device_id: deviceId(),
        device_type: 'computer',
      },
    },
  }
}

async function fetchClientToken(clientVersion: string, clientId: string): Promise<string> {
  const res = await postJson<{ granted_token?: { token?: string } }>(CLIENTTOKEN_URL, clientTokenBody(clientVersion, clientId), {
    headers: { accept: 'application/json' },
  })
  const token = res?.granted_token?.token
  if (!token) throw new Error(tr('Spotify не выдал client-token'))
  return token
}

export async function mintSessionFromCookie(spDc?: string): Promise<AuthSession & { isAnonymous: boolean }> {
  const bundle = await getBundle()
  const params = await buildTokenParams(bundle)
  const url = `${SITE}api/token?${params}`
  const tok = await getJson<{
    accessToken?: string
    clientId?: string
    accessTokenExpirationTimestampMs?: number
    isAnonymous?: boolean
  }>(url, spDc ? { cookie: `sp_dc=${spDc}` } : {})
  if (!tok?.accessToken || !tok?.clientId) throw new Error(tr('Spotify не отдал токен доступа'))
  const clientToken = await fetchClientToken(bundle.buildVersion, tok.clientId)
  return {
    token: tok.accessToken,
    clientToken,
    expiresAt: Number(tok.accessTokenExpirationTimestampMs) || Date.now() + 50 * 60_000,
    isAnonymous: !!tok.isAnonymous,
  }
}

async function getAnonymousSession(fresh = false): Promise<AuthSession> {
  if (!fresh && anonSession && anonSession.expiresAt - Date.now() > 60_000) return anonSession
  anonLoading ??= mintSessionFromCookie()
    .then((s) => (anonSession = { token: s.token, clientToken: s.clientToken, expiresAt: s.expiresAt }))
    .finally(() => (anonLoading = null))
  return anonLoading
}

async function renewUserSession(stale: string): Promise<AuthSession | null> {
  if (Date.now() - userRenewFailedAt < 60_000) return null
  userRenewing ??= (async (): Promise<AuthSession | null> => {
    const manualDc = accounts.sp?.spDc
    if (manualDc) {
      const s = await mintSessionFromCookie(manualDc)
      if (s.isAnonymous) return null
      if (accounts.sp) {
        setSpotify?.({ ...accounts.sp, token: s.token, clientToken: s.clientToken, expiresAt: s.expiresAt })
      }
      return s
    }
    if (!(await extension()) || !spotifySupported()) return null
    const bundle = await getBundle().catch(() => null)
    const apiTokenUrl = bundle ? `${SITE}api/token?${await buildTokenParams(bundle)}` : ''
    const clientDataJson = bundle ? JSON.stringify(clientTokenBody(bundle.buildVersion, '')) : ''
    const renewed = await extensionCall<{ token: string; clientToken: string; expiresAt: number } | null>(
      'sp-renew',
      35_000,
      { stale, apiTokenUrl, clientDataJson },
    ).catch(() => null)
    if (!renewed?.token || !renewed?.clientToken) return null
    if (accounts.sp) {
      setSpotify?.({ ...accounts.sp, token: renewed.token, clientToken: renewed.clientToken, expiresAt: renewed.expiresAt })
    }
    return renewed
  })()
    .catch(() => null)
    .then((s) => {
      if (!s) userRenewFailedAt = Date.now()
      return s
    })
    .finally(() => (userRenewing = null))
  return userRenewing
}

async function activeAuth(): Promise<{ session: AuthSession; signedIn: boolean }> {
  const acc = accounts.sp
  if (acc) {
    if (acc.token && acc.clientToken && acc.expiresAt - Date.now() > 60_000) {
      return { session: { token: acc.token, clientToken: acc.clientToken, expiresAt: acc.expiresAt }, signedIn: true }
    }
    const renewed = await renewUserSession(acc.token)
    if (renewed) return { session: renewed, signedIn: true }
    throw new Error(tr('Сессия Spotify истекла: войди заново в Настройках'))
  }
  return { session: await getAnonymousSession(), signedIn: false }
}

function authHeaders(session: AuthSession): Record<string, string> {
  return {
    authorization: `Bearer ${session.token}`,
    'client-token': session.clientToken,
    'app-platform': 'WebPlayer',
    accept: 'application/json',
  }
}

// ---------------------------------------------------------------------------------- transport

async function pathfinder<T = any>(operationName: string, variables: Record<string, unknown>, explicit?: AuthSession): Promise<T> {
  const run = async (bundle: BundleState, session: AuthSession) => {
    const sha256Hash = bundle.hashes[operationName]
    if (!sha256Hash) throw new Error(tr('Spotify обновил запрос %s — попробуй обновить страницу', operationName))
    const res = await postJson<{ data?: T; errors?: { message?: string }[] }>(
      PATHFINDER,
      { operationName, variables, extensions: { persistedQuery: { version: 1, sha256Hash } } },
      { headers: authHeaders(session) },
    )
    if (res?.errors?.length && !res?.data) {
      const msg = res.errors.map((e) => e.message).filter(Boolean).join('; ') || 'GraphQL error'
      throw new Error(msg)
    }
    return res.data as T
  }

  let bundle = await getBundle()
  const auth = explicit ? { session: explicit, signedIn: true } : await activeAuth()
  try {
    return await run(bundle, auth.session)
  } catch (e) {
    const msg = (e as Error)?.message ?? ''
    if (/PersistedQueryNotFound/i.test(msg)) {
      bundle = await getBundle(true)
      return run(bundle, auth.session)
    }
    if (!explicit && e instanceof HttpError && (e.status === 401 || e.status === 403)) {
      if (auth.signedIn) {
        const renewed = await renewUserSession(auth.session.token)
        if (!renewed) throw new Error(tr('Сессия Spotify истекла: войди заново в Настройках'))
        return run(bundle, renewed)
      }
      const freshAnon = await getAnonymousSession(true)
      return run(bundle, freshAnon)
    }
    throw e
  }
}

async function spclientGet<T = any>(path: string): Promise<T> {
  const { session, signedIn } = await activeAuth()
  const url = `${SPCLIENT}${path}`
  try {
    return await getJson<T>(url, { headers: authHeaders(session) })
  } catch (e) {
    if (e instanceof HttpError && (e.status === 401 || e.status === 403)) {
      if (signedIn) {
        const renewed = await renewUserSession(session.token)
        if (!renewed) throw new Error(tr('Сессия Spotify истекла: войди заново в Настройках'))
        return getJson<T>(url, { headers: authHeaders(renewed) })
      }
      const fresh = await getAnonymousSession(true)
      return getJson<T>(url, { headers: authHeaders(fresh) })
    }
    throw e
  }
}

// ---------------------------------------------------------------------------------- shapes & catalog

const idOfUri = (uri: string | undefined | null): string => uri?.split(':').pop() ?? ''

function pickImage(sources: { url?: string; width?: number | null; height?: number | null }[] | undefined | null): string | null {
  if (!sources?.length) return null
  const sorted = [...sources]
    .filter((s) => s.url)
    .sort((a, b) => (b.width ?? 0) - (a.width ?? 0))
  return sorted[0]?.url ?? sources[0]?.url ?? null
}

function coverSources(obj: any): { url?: string; width?: number | null; height?: number | null }[] | undefined {
  return (
    obj?.coverArt?.sources ??
    obj?.images?.items?.[0]?.sources ??
    obj?.image?.sources ??
    obj?.visuals?.avatarImage?.sources ??
    obj?.visualIdentity?.squareCoverImage?.image?.data?.sources
  )
}

function extractedColor(obj: any, fallbackId: string): string {
  return (
    obj?.coverArt?.extractedColors?.colorDark?.hex ??
    obj?.images?.items?.[0]?.extractedColors?.colorDark?.hex ??
    obj?.visuals?.avatarImage?.extractedColors?.colorDark?.hex ??
    colorFor(fallbackId)
  )
}

function putSpArtist(raw: any, cat: CatalogContext = fallbackCatalog): string {
  const data = raw?.data ?? raw
  const uri = data?.uri ?? raw?._uri ?? (data?.id ? `spotify:artist:${data.id}` : '')
  const nativeId = idOfUri(uri)
  const id = `sp:a:${nativeId}`
  const name = data?.profile?.name ?? data?.name ?? 'Spotify'
  const listeners = data?.stats?.monthlyListeners
  const followers = data?.stats?.followers
  return cat.putArtist({
    id,
    name,
    service: 'sp',
    image: pickImage(coverSources(data)),
    color: extractedColor(data, id),
    followers:
      listeners != null
        ? `${compact(listeners)} ${countWords(listeners, 'слушатель', 'слушателя', 'слушателей')} ${tr('в месяц')}`
        : followers != null
          ? `${compact(followers)} ${countWords(followers, 'подписчик', 'подписчика', 'подписчиков')}`
          : undefined,
    link: nativeId ? `https://open.spotify.com/artist/${nativeId}` : undefined,
  })
}

function putSpTrack(raw: any, cat: CatalogContext = fallbackCatalog, fallbackAlbum?: { id: string; title: string; cover?: string | null }): string {
  const data = raw?.data ?? raw
  const uri = data?.uri ?? raw?._uri ?? (data?.id ? `spotify:track:${data.id}` : '')
  const nativeId = idOfUri(uri)
  const id = `sp:t:${nativeId}`
  const albumRaw = data?.albumOfTrack
  const albumNative = idOfUri(albumRaw?.uri)
  const album = albumNative
    ? { id: `sp:al:${albumNative}`, title: albumRaw?.name ?? fallbackAlbum?.title ?? '' }
    : fallbackAlbum
      ? { id: fallbackAlbum.id, title: fallbackAlbum.title }
      : undefined
  const artistItems: any[] = data?.artists?.items ?? data?.firstArtist?.items ?? []
  const artists = artistItems
    .map((a) => {
      const aUri = a?.uri ?? ''
      const aId = idOfUri(aUri)
      return { id: aId ? `sp:a:${aId}` : `sp:n:${a?.profile?.name ?? ''}`, name: a?.profile?.name ?? a?.name ?? '' }
    })
    .filter((a) => a.name)
  const ms = Number(data?.duration?.totalMilliseconds ?? data?.trackDuration?.totalMilliseconds ?? 0)
  const cover = pickImage(coverSources(albumRaw)) ?? fallbackAlbum?.cover ?? null
  return cat.putTrack({
    id,
    service: 'sp',
    title: data?.name ?? tr('Без названия'),
    artists: artists.length ? artists : [{ id: 'sp:n:Spotify', name: 'Spotify' }],
    duration: Math.round(ms / 1000),
    cover,
    color: extractedColor(albumRaw, id),
    album,
    explicit: data?.contentRating?.label === 'EXPLICIT',
    link: nativeId ? `https://open.spotify.com/track/${nativeId}` : undefined,
    playable: data?.playability?.playable !== false,
    sp: { id: nativeId, uri: uri || `spotify:track:${nativeId}` },
  })
}

function albumKind(type?: string): SetKind {
  return type === 'SINGLE' ? 'single' : 'album'
}

function putSpAlbum(raw: any, cat: CatalogContext = fallbackCatalog): string {
  const data = raw?.data ?? raw
  const uri = data?.uri ?? raw?._uri ?? (data?.id ? `spotify:album:${data.id}` : '')
  const nativeId = idOfUri(uri)
  const id = `sp:al:${nativeId}`
  const artistItem = data?.artists?.items?.[0]
  const artistId = idOfUri(artistItem?.uri)
  const artistName = artistItem?.profile?.name ?? artistItem?.name ?? ''
  const year = data?.date?.year ?? (data?.date?.isoString ? Number(String(data.date.isoString).slice(0, 4)) : undefined)
  return cat.putSet({
    id,
    kind: albumKind(data?.type),
    service: 'sp',
    title: data?.name ?? tr('Без названия'),
    subtitle: artistName || 'Spotify',
    cover: pickImage(coverSources(data)),
    color: extractedColor(data, id),
    tracks: [],
    total: data?.tracksV2?.totalCount,
    year: year && !Number.isNaN(year) ? year : undefined,
    artist: artistId && artistName ? { id: `sp:a:${artistId}`, name: artistName } : undefined,
    link: nativeId ? `https://open.spotify.com/album/${nativeId}` : undefined,
  })
}

function playlistKind(data: any): SetKind {
  const attrs: { key: string; value: string }[] = data?.attributes ?? []
  const formatType = attrs.find((a) => a.key === 'format_list_type')?.value ?? data?.format ?? ''
  if (/mix|daylist|discover|release-radar|on-repeat|repeat-rewind/i.test(formatType) || /Daily Mix|Discover Weekly|Release Radar|Daylist|Микс дня/i.test(data?.name ?? '')) {
    return 'mix'
  }
  if (/radio/i.test(formatType)) return 'station'
  return 'playlist'
}

function putSpPlaylist(raw: any, cat: CatalogContext = fallbackCatalog, forceKind?: SetKind): string {
  const data = raw?.data ?? raw
  const uri = data?.uri ?? raw?._uri ?? ''
  const nativeId = idOfUri(uri)
  const id = `sp:pl:${nativeId}`
  const owner = data?.ownerV2?.data?.name ?? ''
  return cat.putSet({
    id,
    kind: forceKind ?? playlistKind(data),
    service: 'sp',
    title: data?.name ?? tr('Без названия'),
    subtitle: owner || data?.description?.replace(/<[^>]+>/g, '') || 'Spotify',
    cover: pickImage(coverSources(data)),
    color: extractedColor(data, id),
    tracks: [],
    total: data?.content?.totalCount,
    link: nativeId ? `https://open.spotify.com/playlist/${nativeId}` : undefined,
  })
}

// ---------------------------------------------------------------------------------- pages

export async function home(cat: CatalogContext = fallbackCatalog): Promise<Section[]> {
  const timeZone = Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC'
  const data = await pathfinder<{
    home?: { sectionContainer?: { sections?: { items?: any[] } } }
  }>('home', {
    timeZone,
    sp_t: '',
    facet: '',
    sectionItemsLimit: 12,
    homeEndUserIntegration: 'INTEGRATION_WEB_PLAYER',
    includeHomeChapterVideoCards: false,
    includeEpisodeContentRatingsV2: false,
  })

  const sections: Section[] = []
  for (const sec of data?.home?.sectionContainer?.sections?.items ?? []) {
    const title = sec?.data?.title?.transformedLabel || sec?.data?.title?.text || ''
    if (!title) continue
    const setsList: string[] = []
    const tracksList: string[] = []
    for (const item of sec?.sectionItems?.items ?? []) {
      const content = item?.content
      const typename = content?.__typename
      const inner = content?.data
      if (!inner || inner.__typename === 'NotFound') continue
      if (typename === 'PlaylistResponseWrapper' || inner.__typename === 'Playlist') {
        const sid = putSpPlaylist(content, cat)
        if (sid !== 'sp:pl:') setsList.push(sid)
      } else if (typename === 'AlbumResponseWrapper' || inner.__typename === 'Album') {
        const sid = putSpAlbum(content, cat)
        if (sid !== 'sp:al:') setsList.push(sid)
      } else if (typename === 'TrackResponseWrapper' || inner.__typename === 'Track') {
        const tid = putSpTrack(content, cat)
        if (tid !== 'sp:t:') tracksList.push(tid)
      }
    }
    if (setsList.length) sections.push({ title, sets: setsList })
    else if (tracksList.length) sections.push({ title, sets: [], tracks: tracksList })
  }
  return sections
}

async function fetchLikedTracksPage(offset: number, limit: number, cat: CatalogContext): Promise<{ ids: string[]; total: number }> {
  const data = await pathfinder<{
    me?: { library?: { tracks?: { items?: any[]; totalCount?: number } } }
  }>('fetchLibraryTracks', { offset, limit })
  const page = data?.me?.library?.tracks
  const ids = (page?.items ?? []).flatMap((item) => {
    const trk = item?.track
    if (!trk?.data || trk.data.__typename !== 'Track') return []
    return [putSpTrack(trk, cat)]
  })
  return { ids, total: page?.totalCount ?? ids.length }
}

export async function loadSet(id: string, cat: CatalogContext = fallbackCatalog): Promise<TrackSet> {
  if (id === 'sp:likes') {
    cat.putSet({
      id,
      kind: 'liked',
      service: 'sp',
      title: tr('Любимое'),
      subtitle: 'Spotify',
      color: '#1db954',
      tracks: [],
      cover: null,
    })
    const ids: string[] = []
    const first = await fetchLikedTracksPage(0, 100, cat)
    ids.push(...first.ids)
    const maxTotal = Math.min(first.total, 500)
    for (let offset = 100; offset < maxTotal; offset += 100) {
      const next = await fetchLikedTracksPage(offset, 100, cat).catch(() => null)
      if (!next?.ids.length) break
      ids.push(...next.ids)
    }
    return finishSet(id, ids, cat)
  }

  if (id.startsWith('sp:al:')) {
    const uri = `spotify:album:${id.slice('sp:al:'.length)}`
    const first = await pathfinder<{ albumUnion?: any }>('getAlbum', { uri, locale: '', offset: 0, limit: 100 })
    const album = first?.albumUnion
    if (!album) throw new Error(tr('Spotify не нашёл альбом'))
    putSpAlbum(album, cat)
    const albumRef = { id, title: album.name ?? '', cover: pickImage(coverSources(album)) }
    const tracks: string[] = []
    const pushItems = (items: any[] | undefined) => {
      for (const entry of items ?? []) {
        const t = entry?.track ?? entry
        if (t?.name) tracks.push(putSpTrack(t, cat, albumRef))
      }
    }
    pushItems(album.tracksV2?.items)
    const total = Math.min(album.tracksV2?.totalCount ?? tracks.length, 500)
    for (let offset = tracks.length; offset < total; offset += 100) {
      const page = await pathfinder<{ albumUnion?: any }>('getAlbum', { uri, locale: '', offset, limit: 100 }).catch(() => null)
      const items = page?.albumUnion?.tracksV2?.items
      if (!items?.length) break
      pushItems(items)
    }
    return finishSet(id, tracks, cat)
  }

  const plId = id.slice('sp:pl:'.length)
  const uri = `spotify:playlist:${plId}`
  const first = await pathfinder<{ playlistV2?: any }>('fetchPlaylist', {
    uri,
    offset: 0,
    limit: 100,
    enableWatchFeedEntrypoint: false,
  })
  const pl = first?.playlistV2
  if (!pl) throw new Error(tr('Spotify не нашёл плейлист'))
  putSpPlaylist(pl, cat)
  const tracks: string[] = []
  const pushPl = (items: any[] | undefined) => {
    for (const item of items ?? []) {
      const trk = item?.itemV2
      if (trk?.data?.__typename === 'Track') tracks.push(putSpTrack(trk, cat))
    }
  }
  pushPl(pl.content?.items)
  const total = Math.min(pl.content?.totalCount ?? tracks.length, 500)
  for (let offset = tracks.length; offset < total; offset += 100) {
    const page = await pathfinder<{ playlistV2?: any }>('fetchPlaylist', {
      uri,
      offset,
      limit: 100,
      enableWatchFeedEntrypoint: false,
    }).catch(() => null)
    const items = page?.playlistV2?.content?.items
    if (!items?.length) break
    pushPl(items)
  }
  return finishSet(id, tracks, cat)
}

function finishSet(id: string, trackIds: string[], cat: CatalogContext = fallbackCatalog): TrackSet {
  const known = cat.sets.get(id)!
  const done: TrackSet = { ...known, tracks: trackIds, loaded: true, total: trackIds.length }
  cat.putSet(done)
  return done
}

export async function loadArtist(id: string): Promise<ArtistPage> {
  const nativeId = id.slice('sp:a:'.length)
  const data = await pathfinder<{ artistUnion?: any }>('queryArtistOverview', {
    uri: `spotify:artist:${nativeId}`,
    locale: '',
    includePrerelease: false,
  })
  const artist = data?.artistUnion
  if (!artist || artist.__typename === 'NotFound') throw new Error(tr('Spotify не нашёл исполнителя'))
  putSpArtist(artist)

  const top: string[] = (artist.discography?.topTracks?.items ?? []).flatMap((item: any) =>
    item?.track ? [putSpTrack(item.track)] : [],
  )
  const releases: string[] = []
  for (const group of [
    artist.discography?.albums?.items,
    artist.discography?.singles?.items,
    artist.discography?.compilations?.items,
  ]) {
    for (const entry of group ?? []) {
      for (const rel of entry?.releases?.items ?? []) {
        if (rel?.uri) releases.push(putSpAlbum(rel))
      }
    }
  }
  const playlists: string[] = []
  for (const group of [artist.relatedContent?.featuringV2?.items, artist.relatedContent?.discoveredOnV2?.items]) {
    for (const item of group ?? []) {
      if (item?.data?.__typename === 'Playlist') playlists.push(putSpPlaylist(item))
    }
  }
  const similar: string[] = (artist.relatedContent?.relatedArtists?.items ?? []).flatMap((a: any) =>
    a?.uri ? [putSpArtist(a)] : [],
  )

  return {
    artist: id,
    top: [...new Set(top)],
    releases: [...new Set(releases)],
    playlists: [...new Set(playlists)],
    similar: [...new Set(similar)],
  }
}

// ---------------------------------------------------------------------------------- search

function searchPage(
  op: 'searchTracks' | 'searchArtists' | 'searchAlbums' | 'searchPlaylists',
  section: SearchSection,
  q: string,
  offset: number,
  limit: number,
): () => Promise<ResultPage> {
  return async () => {
    const res = await pathfinder<{ searchV2?: any }>(op, {
      searchTerm: q,
      offset,
      limit,
      numberOfTopResults: 5,
      includeAudiobooks: false,
    })
    const s2 = res?.searchV2
    const group =
      section === 'tracks'
        ? s2?.tracksV2
        : section === 'artists'
          ? s2?.artists
          : section === 'albums'
            ? s2?.albumsV2
            : s2?.playlists
    const rawItems: any[] = group?.items ?? []
    const items = mapSearchItems(section, rawItems)
    const nextOffset = group?.pagingInfo?.nextOffset
    const total = group?.totalCount ?? 0
    const nextIdx = typeof nextOffset === 'number' ? nextOffset : offset + rawItems.length
    return {
      items,
      next: rawItems.length > 0 && nextIdx < total ? searchPage(op, section, q, nextIdx, limit) : undefined,
    }
  }
}

function mapSearchItems(section: SearchSection, rawItems: any[]): string[] {
  if (section === 'tracks') {
    return rawItems.flatMap((x) => (x?.item?.data?.__typename === 'Track' ? [putSpTrack(x.item)] : []))
  }
  if (section === 'artists') {
    return rawItems.flatMap((x) => (x?.data?.__typename === 'Artist' ? [putSpArtist(x)] : []))
  }
  if (section === 'albums') {
    return rawItems.flatMap((x) => (x?.data?.__typename === 'Album' ? [putSpAlbum(x)] : []))
  }
  return rawItems.flatMap((x) => (x?.data?.__typename === 'Playlist' ? [putSpPlaylist(x)] : []))
}

export async function search(q: string): Promise<SearchResult> {
  const res = await pathfinder<{ searchV2?: any }>('searchDesktop', {
    searchTerm: q,
    offset: 0,
    limit: 15,
    numberOfTopResults: 5,
    includeAudiobooks: false,
    includeArtistHasConcertsField: false,
    includePreReleases: false,
    includeLocalConcertsField: false,
    includeAuthors: false,
  })
  const s2 = res?.searchV2 ?? {}
  const tracks = mapSearchItems('tracks', s2.tracksV2?.items ?? [])
  const artists = mapSearchItems('artists', s2.artists?.items ?? [])
  const albums = mapSearchItems('albums', s2.albumsV2?.items ?? [])
  const playlists = mapSearchItems('playlists', s2.playlists?.items ?? [])

  let best: SearchResult['best']
  const top = s2.topResultsV2?.items?.[0]?.item
  if (top?.data?.__typename === 'Artist') best = { type: 'artist', id: putSpArtist(top) }
  else if (top?.data?.__typename === 'Track') best = { type: 'track', id: putSpTrack(top) }
  else if (top?.data?.__typename === 'Album') best = { type: 'album', id: putSpAlbum(top) }
  else if (top?.data?.__typename === 'Playlist') best = { type: 'playlist', id: putSpPlaylist(top) }
  else if (artists[0]) best = { type: 'artist', id: artists[0] }
  else if (tracks[0]) best = { type: 'track', id: tracks[0] }

  const hasMore = (group: any, count: number) => count > 0 && (group?.totalCount ?? 0) > count
  return {
    best,
    tracks,
    artists,
    albums,
    playlists,
    more: {
      tracks: hasMore(s2.tracksV2, tracks.length) ? searchPage('searchTracks', 'tracks', q, 15, 20) : undefined,
      artists: hasMore(s2.artists, artists.length) ? searchPage('searchArtists', 'artists', q, 15, 20) : undefined,
      albums: hasMore(s2.albumsV2, albums.length) ? searchPage('searchAlbums', 'albums', q, 15, 20) : undefined,
      playlists: hasMore(s2.playlists, playlists.length) ? searchPage('searchPlaylists', 'playlists', q, 15, 20) : undefined,
    },
  }
}

export async function searchProbe(q: string): Promise<{ artist: string; title: string } | null> {
  const res = await search(q)
  if (!res.tracks[0]) return null
  const t = catalogTracks.get(res.tracks[0])
  return t ? { artist: t.artists.map((a) => a.name).join(', '), title: t.title } : null
}

// ---------------------------------------------------------------------------------- account & library

export interface SpotifyAccountProfile {
  id: string
  name: string
  avatar: string | null
}

export async function account(explicit?: AuthSession): Promise<SpotifyAccountProfile> {
  const data = await pathfinder<{
    me?: { profile?: { uri?: string; username?: string; name?: string; avatar?: { sources?: any[] } } }
  }>('profileAttributes', {}, explicit)
  const p = data?.me?.profile
  const id = p?.username || idOfUri(p?.uri)
  if (!id) throw new Error(tr('Spotify не узнал этот вход'))
  return { id, name: p?.name || id, avatar: pickImage(p?.avatar?.sources) }
}

export async function checkSession(): Promise<void> {
  if (spToken() && spClientToken()) await account()
}

export async function library(cat: CatalogContext = fallbackCatalog, opts: LibraryOptions = {}) {
  const likes = await loadSet('sp:likes', cat)
  let playlists: string[] = []
  let artistsList: string[] = []
  try {
    const data = await pathfinder<{
      me?: { libraryV3?: { items?: any[] } }
    }>('libraryV3', {
      filters: [],
      order: null,
      textFilter: '',
      features: ['LIKED_SONGS', 'YOUR_EPISODES_V2'],
      limit: 80,
      offset: 0,
      flatten: true,
      folderUri: null,
      includeFoldersWhenFlattening: true,
    })
    for (const entry of data?.me?.libraryV3?.items ?? []) {
      const item = entry?.item
      const typename = item?.__typename
      const inner = item?.data
      if (!inner || inner.__typename === 'NotFound') continue
      if (typename === 'PlaylistResponseWrapper' || inner.__typename === 'Playlist') {
        const pid = putSpPlaylist(item, cat)
        if (pid !== 'sp:pl:') playlists.push(pid)
      } else if (typename === 'AlbumResponseWrapper' || inner.__typename === 'Album') {
        const aid = putSpAlbum(item, cat)
        if (aid !== 'sp:al:') playlists.push(aid)
      } else if (typename === 'ArtistResponseWrapper' || inner.__typename === 'Artist') {
        const artId = putSpArtist(item, cat)
        if (artId !== 'sp:a:') artistsList.push(artId)
      }
    }
  } catch (e) {
    if (opts.strict) throw e
    opts.onPartial?.()
  }
  return {
    liked: likes.id,
    likedIds: likes.tracks,
    playlists: [...new Set(playlists)],
    artists: [...new Set(artistsList)],
  }
}

export async function setLiked(track: Track, liked: boolean): Promise<void> {
  const uri = track.sp?.uri ?? `spotify:track:${track.id.slice('sp:t:'.length)}`
  await pathfinder(liked ? 'addToLibrary' : 'removeFromLibrary', { libraryItemUris: [uri] })
}

export async function artistFollowing(id: string): Promise<boolean> {
  const uri = `spotify:artist:${id.slice('sp:a:'.length)}`
  const data = await pathfinder<{ lookup?: { data?: { saved?: boolean } }[] }>('areEntitiesInLibrary', { uris: [uri] })
  return !!data?.lookup?.[0]?.data?.saved
}

export async function setArtistFollowing(id: string, following: boolean): Promise<void> {
  const uri = `spotify:artist:${id.slice('sp:a:'.length)}`
  await pathfinder(following ? 'addToLibrary' : 'removeFromLibrary', { libraryItemUris: [uri] })
}

// ---------------------------------------------------------------------------------- radio & lyrics & links

export async function related(track: Track): Promise<string[]> {
  const uri = track.sp?.uri ?? `spotify:track:${track.id.slice('sp:t:'.length)}`
  try {
    const mix = await spclientGet<{ mediaItems?: { uri?: string }[] }>(
      `inspiredby-mix/v2/seed_to_playlist/${encodeURIComponent(uri)}?response-format=json`,
    )
    const plUri = mix?.mediaItems?.[0]?.uri
    const plId = idOfUri(plUri)
    if (plId) {
      const set = await loadSet(`sp:pl:${plId}`)
      if (set.tracks.length) return set.tracks.filter((id) => id !== track.id)
    }
  } catch {
    // Fall back to the primary artist's top tracks when inspiredby-mix rejects a track.
  }
  const primary = track.artists[0]?.id
  if (primary?.startsWith('sp:a:')) {
    const artist = await loadArtist(primary).catch(() => null)
    if (artist?.top.length) return artist.top.filter((id) => id !== track.id)
  }
  return []
}

function toTimestamp(ms: number): string {
  const totalSec = Math.max(0, ms / 1000)
  const m = Math.floor(totalSec / 60)
  const s = (totalSec % 60).toFixed(2).padStart(5, '0')
  return `[${String(m).padStart(2, '0')}:${s}]`
}

export async function lyrics(track: Track): Promise<{ text: string; synced: boolean } | null> {
  if (!accounts.sp) return null
  const spId = track.sp?.id ?? track.id.slice('sp:t:'.length)
  try {
    const res = await spclientGet<{
      lyrics?: { syncType?: string; lines?: { startTimeMs?: string; words?: string }[] }
    }>(`color-lyrics/v2/track/${spId}?format=json&vocalRemoval=false`)
    const lines = res?.lyrics?.lines ?? []
    if (!lines.length) return null
    const synced = res?.lyrics?.syncType === 'LINE_SYNCED'
    const text = lines
      .map((l) => (synced && l.startTimeMs != null ? `${toTimestamp(Number(l.startTimeMs))}${l.words ?? ''}` : l.words ?? ''))
      .join('\n')
      .trim()
    return text ? { text, synced } : null
  } catch {
    return null
  }
}

export async function resolve(link: string): Promise<{ kind: 'track' | 'set' | 'artist'; id: string } | null> {
  const url = new URL(link)
  const parts = url.pathname.replace(/^\/intl-[a-z-]+/i, '').split('/').filter(Boolean)
  const [kind, rawId] = parts
  if (!kind || !rawId) return null
  const cleanId = rawId.split('?')[0]
  if (kind === 'track') {
    const data = await pathfinder<{ trackUnion?: any }>('getTrack', { uri: `spotify:track:${cleanId}` })
    if (!data?.trackUnion || data.trackUnion.__typename === 'NotFound') return null
    return { kind: 'track', id: putSpTrack(data.trackUnion) }
  }
  if (kind === 'album') return { kind: 'set', id: `sp:al:${cleanId}` }
  if (kind === 'playlist') return { kind: 'set', id: `sp:pl:${cleanId}` }
  if (kind === 'artist') return { kind: 'artist', id: `sp:a:${cleanId}` }
  return null
}

// ---------------------------------------------------------------------------------- audio matching

type Candidate =
  | { key: string; kind: 'yt'; videoId: string; score: number }
  | { key: string; kind: 'sc'; track: soundcloud.ScTrack; score: number }
  | { key: string; kind: 'ya'; track: Track; score: number }

interface MatchState {
  candidates: Candidate[]
  index: number
  failed: Set<string>
}

const resolved = new Map<string, MatchState>()
const resolving = new Map<string, Promise<MatchState>>()

const NOISE_WORDS = /\b(?:remix|live|cover|acoustic|instrumental|karaoke|slowed|reverb|sped\s*up|nightcore|8d|mashup)\b/i
const CLEAN_TITLE = /\s*[(\[](?:feat\.?|ft\.?|featuring|with)\s+[^)\]]+[)\]]|\s+-\s+(?:remaster(?:ed)?(?:\s+\d{4})?|single version|radio edit|album version|explicit).*$/gi

function normWords(s: string): string[] {
  return s
    .toLowerCase()
    .replace(/[^\p{L}\p{N}]+/gu, ' ')
    .trim()
    .split(/\s+/)
    .filter(Boolean)
}

function jaccard(a: string[], b: string[]): number {
  if (!a.length || !b.length) return 0
  const sb = new Set(b)
  const hit = a.filter((w) => sb.has(w)).length
  return hit / new Set([...a, ...b]).size
}

function scoreCandidate(targetTitle: string, targetArtists: string[], targetDur: number, candTitle: string, candArtist: string, candDur?: number): number {
  const cleanTarget = targetTitle.replace(CLEAN_TITLE, '').trim() || targetTitle
  const nt = normWords(cleanTarget)
  const nc = normWords(candTitle.replace(CLEAN_TITLE, '').trim() || candTitle)
  let score = 0
  const jt = jaccard(nt, nc)
  if (nt.join(' ') === nc.join(' ')) score += 55
  else if (nc.join(' ').includes(nt.join(' ')) || nt.join(' ').includes(nc.join(' '))) score += 30
  score += Math.round(jt * 25)

  const targetArtNorm = targetArtists.map((a) => normWords(a).join(' ')).filter(Boolean)
  const candArtNorm = normWords(candArtist).join(' ')
  const candWhole = `${candArtNorm} ${nc.join(' ')}`
  if (targetArtNorm.some((a) => a && (candArtNorm.includes(a) || candWhole.includes(a)))) score += 30

  if (targetDur > 0 && candDur && candDur > 0) {
    const diff = Math.abs(targetDur - candDur)
    if (diff <= 3) score += 45
    else if (diff <= 8) score += 25
    else if (diff <= 15) score += 10
    else if (diff > 45) score -= 80
    else if (diff > 25) score -= 40
  }

  if (!NOISE_WORDS.test(targetTitle) && NOISE_WORDS.test(candTitle)) score -= 45
  return score
}

async function buildMatchState(track: Track): Promise<MatchState> {
  const artist = track.artists[0]?.name && track.artists[0].name !== 'Spotify' ? track.artists[0].name : ''
  const cleanTitle = track.title.replace(CLEAN_TITLE, '').trim() || track.title
  const query = artist ? `${artist} ${cleanTitle}` : cleanTitle
  const targetArtists = track.artists.map((a) => a.name)

  const candidates: Candidate[] = []
  try {
    const ytHits = await youtube.searchSongs(query)
    for (const s of ytHits.slice(0, 6)) {
      const score = scoreCandidate(track.title, targetArtists, track.duration, s.title, s.artists, s.duration)
      candidates.push({ key: `yt:${s.videoId}`, kind: 'yt', videoId: s.videoId, score })
    }
  } catch {
    // Fall through to SoundCloud/Yandex fallback
  }

  try {
    const scHits = await soundcloud.searchTracks(query, 6)
    for (const s of scHits) {
      const streams = s.media?.transcodings ?? []
      if (s.streamable === false || s.policy === 'BLOCK') continue
      if (streams.length && !streams.some((x) => !x.format.protocol.includes('encrypted'))) continue
      if (streams.length && streams.every((x) => x.snipped)) continue
      const candArtist = s.publisher_metadata?.artist?.trim() || s.user?.username || ''
      const candDur = Math.round((s.full_duration ?? s.duration ?? 0) / 1000)
      const score = scoreCandidate(track.title, targetArtists, track.duration, s.title ?? '', candArtist, candDur) - 5
      candidates.push({ key: `sc:${s.id}`, kind: 'sc', track: s, score })
    }
  } catch {
    // SoundCloud optional fallback
  }

  if (accounts.ya) {
    try {
      const yaHits = await yandex.search(query)
      for (const yid of yaHits.tracks.slice(0, 4)) {
        const yt = catalogTracks.get(yid)
        if (!yt || yt.playable === false) continue
        const candArtist = yt.artists.map((a) => a.name).join(', ')
        const score = scoreCandidate(track.title, targetArtists, track.duration, yt.title, candArtist, yt.duration) - 3
        candidates.push({ key: yid, kind: 'ya', track: yt, score })
      }
    } catch {
      // Yandex optional fallback
    }
  }

  candidates.sort((a, b) => b.score - a.score)
  if (!candidates.length) throw new Error(tr('Spotify не нашёл источник звука для «%s»', track.title))
  return { candidates, index: 0, failed: new Set() }
}

async function getMatchState(track: Track): Promise<MatchState> {
  const existing = resolved.get(track.id)
  if (existing) return existing
  let pending = resolving.get(track.id)
  if (!pending) {
    pending = buildMatchState(track)
      .then((state) => {
        resolved.set(track.id, state)
        return state
      })
      .finally(() => resolving.delete(track.id))
    resolving.set(track.id, pending)
  }
  return pending
}

function syntheticTrack(base: Track, cand: Candidate): Track {
  if (cand.kind === 'yt') return { ...base, yt: { videoId: cand.videoId } }
  if (cand.kind === 'sc') {
    return {
      ...base,
      id: `sc:t:${cand.track.id}`,
      sc: { transcodings: cand.track.media?.transcodings ?? [], auth: cand.track.track_authorization },
    }
  }
  return cand.track
}

export async function stream(track: Track): Promise<Stream> {
  const state = await getMatchState(track)
  let lastError: unknown = null
  for (let i = state.index; i < state.candidates.length; i++) {
    const cand = state.candidates[i]
    if (state.failed.has(cand.key)) continue
    state.index = i
    try {
      if (cand.kind === 'yt') return await youtube.stream(syntheticTrack(track, cand))
      if (cand.kind === 'sc') return await soundcloud.stream(syntheticTrack(track, cand))
      return { url: await yandex.stream(syntheticTrack(track, cand)), hls: false }
    } catch (e) {
      lastError = e
      state.failed.add(cand.key)
    }
  }
  throw lastError instanceof Error ? lastError : new Error(tr('Не удалось включить трек Spotify'))
}

export function streamFailed(track: Track): boolean {
  const state = resolved.get(track.id)
  if (!state) return false
  const current = state.candidates[state.index]
  if (!current) return false
  if (current.kind === 'yt' && youtube.streamFailed(current.videoId)) return true
  state.failed.add(current.key)
  return state.candidates.some((c) => !state.failed.has(c.key))
}
