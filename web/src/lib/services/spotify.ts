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
import { toast } from '../toast.svelte'
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
  const artistItems: any[] = data?.artists?.items ?? [...(data?.firstArtist?.items ?? []), ...(data?.otherArtists?.items ?? [])]
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
  | { key: string; kind: 'yt'; videoId: string; score: number; label: string }
  | { key: string; kind: 'sc'; track: soundcloud.ScTrack; score: number; label: string }
  | { key: string; kind: 'ya'; track: Track; score: number; label: string }

interface MatchState {
  candidates: Candidate[]
  index: number
  failed: Set<string>
}

const resolved = new Map<string, MatchState>()
const resolving = new Map<string, Promise<MatchState>>()

export function clearAudioCache() {
  resolved.clear()
  resolving.clear()
}

export function playingVariant(trackId: string): string | null {
  const state = resolved.get(trackId)
  if (!state) return null
  return state.candidates[state.index]?.label ?? state.candidates[0]?.label ?? null
}

export function showPlayingVariant(trackId: string) {
  const label = playingVariant(trackId) ?? tr('Вариант ещё подбирается…')
  toast(tr('Источник Spotify: %s', label))
}

function formatVariantLabel(source: string, title: string, artist: string, durSec?: number, mode?: 'strict' | 'loose' | 'popular'): string {
  const s = Math.max(0, Math.round(durSec ?? 0))
  const dur = s > 0 ? `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}` : ''
  const modeNote =
    mode === 'popular'
      ? tr('самый популярный на YouTube')
      : mode === 'loose'
        ? tr('свободная длительность')
        : ''
  const suffix = [dur, modeNote].filter(Boolean).join(' · ')
  const artPart = artist.trim() ? ` — ${artist.trim()}` : ''
  return `${source}: ${title}${artPart}${suffix ? ` (${suffix})` : ''}`
}

const BRACKET_NOISE =
  /\s*[\[(](?:feat\.?|ft\.?|featuring|with|prod\.?|official\s*(?:music\s*)?(?:video|audio|lyric\s*video|visualizer|visualiser|mv|hd|4k)|music\s*video|lyric(?:s)?(?:\s*video)?|color\s*coded(?:\s*lyrics?)?|kan\s*[/|]\s*rom\s*[/|]\s*eng|rom\s*[/|]\s*eng|eng\s*sub(?:s)?|audio|visualizer|visualiser|hd|hq|4k|explicit|clean(?:\s+edit|\s+version)?|video\s+edit|remaster(?:ed)?(?:\s+\d{4})?|single\s+version|album\s+version|radio\s+edit|original\s+mix|full(?:\s+ver(?:sion)?)?|ost|op\d*|ed\d*)[^)\]]*[)\]]/gi
const DASH_NOISE =
  /\s+[-–—]\s+(?:\d{4}\s+)?(?:remaster(?:ed)?(?:\s+\d{4})?|single\s+version|album\s+version|radio\s+edit|original\s+version|explicit|clean(?:\s+version)?|from\s+["«].*)$/gi
const TRAILING_FEAT = /\s+(?:feat\.?|ft\.?|featuring)\s+.+$/i
const TRAILING_LYRICS = /\s+lyrics?$/i
const SAFE_EDITION_NOISE = /\b(?:clean|video|radio)\s+edit\b|[\[(]\s*hd\s*[)\]]/gi

const EDITION_WORDS =
  /\b(?:live|remix|cover|karaoke|instrumental|acoustic|arrangement|soundfont|jammable|ai\s+cover|ai\s+version|version\s+by|sung\s+by|played\s+by|performed\s+by|in\s+the\s+style\s+of|sped[\s-]+up|slowed|nightcore|8d|mashup|demo|edit|reverb|bootleg|acapella|a\s+cappella|backing\s+track|minus(?:\s+one)?|off\s+vocal|no\s+vocal(?:s)?|without\s+vocal(?:s)?|vocal(?:s)?\s+only|guitar|piano|bass|drum(?:s)?|violin|cello|flute|saxophone|orchestra(?:l)?|symphonic|trio|quartet|duo|music\s+box|lullaby|city\s+pop|8[\s-]*bit|chiptune|lo[\s-]*fi|lofi|synthesia|tutorial|lesson|tabs?|chords?|midi|ringtone|pitched|pitch[\s-]+shifted|daycore|doomer|but|dance\s+practice|choreography|behind\s+the\s+scenes|making\s+of|teaser|trailer|reaction|analysis|review|breakdown|interview|commentary|snippet|preview|soviet\s+ver(?:sion)?|минус(?:овка)?|кавер|караоке|инструментал|под\s+гитару|на\s+пианино|на\s+гитаре|без\s+слов|без\s+вокала)\b|[+-]\d+\s*key\b|原曲歌手|カラオケ|弾いてみた|歌ってみた|叩いてみた|踊ってみた|吹いてみた|切り抜き|リアクション|ピアノ|ギター/gi
const SUSPICIOUS_CHANNEL_WORDS =
  /\b(?:backing\s+track(?:s)?|minus(?:\s+tracks?|\s+one)?|karaoke|instrumental(?:s)?|covers?|piano|guitar|violin|drum(?:s)?|bass|trio|quartet|orchestra|music\s+box|lullaby|acapella|a\s+cappella|jammable|soundfont|synthesia|tabs?|tutorial(?:s)?|nightcore|slowed|sped[\s-]+up|8d\s+audio|gameplay|liveplay|beatmaps?|typing|keyboard|asmr|beat\s+saber|clone\s+hero|geometry\s+dash|минус(?:овка|овки)?|караоке|кавер(?:ы)?|прохождение|геймплей)\b|(?<![\p{L}\p{N}])osu(?:![\p{L}]*)?(?![\p{L}\p{N}])|歌っちゃ王/giu
const GAMEPLAY_PATTERNS =
  /(?:^\s*\d{6}\s+|(?<![\p{L}\p{N}])osu(?:![\p{L}]*)?(?![\p{L}\p{N}])|\b(?:gameplay|playthrough|liveplay|handcam|beatmap|full\s+combo|all\s+perfect|beat\s+saber|geometry\s+dash|clone\s+hero|guitar\s+hero|rock\s+band|stepmania|etterna|quaver|muse\s+dash|project\s+sekai|proseka|arcaea|cytus|phigros|friday\s+night\s+funkin|fnf|maimai|chunithm|sound\s+voltex|sdvx|typing|keyboard|keycaps|sound\s+test|asmr|speedrun|прохождение|геймплей)\b|\b(?:hd|hr|dt|nc|ez|fl|ht){2,}\b|\+\s*(?:hd|hr|dt|nc|ez|fl|ht)+\b|\b\d{2,4}\s*pp\b|\b\d{2,3}\.\d{1,2}\s*%|[\[(]\s*(?:fc|ss|choke|nomod|nm|dt|hr)\s*[)\]])/giu

const TITLE_SPLIT = /\s*[-–—|]\s+|\s+[-–—|:]\s*|:\s+/
const BY_SPLIT = /\s+by\s+/i
const ARTIST_SEPARATORS = /\s*(?:,|&|\+|\/|\bfeat\.?\b|\bft\.?\b|\bfeaturing\b|\bwith\b|\band\b|\bx\b|\bи\b)\s*/i

function normalized(text: string): string {
  return text
    .toLowerCase()
    .normalize('NFKD')
    .replace(/\p{M}+/gu, '')
    .replace(/['’`ʼ]/g, '')
    .replace(/(?<=[\p{L}\p{N}])!(?=[\p{L}\p{N}])/gu, 'i')
    .replace(/(?<=[\p{L}\p{N}])\$(?=[\p{L}\p{N}])/gu, 's')
    .replace(/&/g, ' and ')
    .replace(/[^\p{L}\p{N}]+/gu, ' ')
    .trim()
}

function cleanAudioTitle(raw: string): string {
  let result = raw.trim()
  for (let i = 0; i < 3; i++) {
    const next = result
      .replace(BRACKET_NOISE, '')
      .replace(DASH_NOISE, '')
      .replace(TRAILING_FEAT, '')
      .replace(TRAILING_LYRICS, '')
      .trim()
    if (next === result) break
    result = next
  }
  return result || raw.trim()
}

function durationToleranceSec(targetDurSec: number): number {
  return Math.max(12, Math.min(25, Math.floor(targetDurSec / 8)))
}

function officialMvToleranceSec(targetDurSec: number): number {
  return Math.max(durationToleranceSec(targetDurSec), Math.min(60, Math.floor(targetDurSec / 3)))
}

function editionTags(title: string): Set<string> {
  const cleaned = title.replace(SAFE_EDITION_NOISE, ' ')
  const out = new Set<string>()
  for (const m of cleaned.matchAll(EDITION_WORDS)) {
    out.add(m[0].toLowerCase().replace(/[\s-]+/g, ' ').trim())
  }
  for (const m of cleaned.matchAll(GAMEPLAY_PATTERNS)) {
    out.add('game:' + m[0].toLowerCase().replace(/[\s-]+/g, ' ').trim())
  }
  return out
}

function channelVariantTags(rawArtist: string): Set<string> {
  const out = new Set<string>()
  for (const m of rawArtist.matchAll(SUSPICIOUS_CHANNEL_WORDS)) {
    out.add(m[0].toLowerCase().replace(/[\s-]+/g, ' ').trim())
  }
  return out
}

function sameSet(a: Set<string>, b: Set<string>): boolean {
  if (a.size !== b.size) return false
  for (const x of a) if (!b.has(x)) return false
  return true
}

function addArtistVariants(out: Set<string>, raw: string) {
  const cleaned = normalized(raw)
    .replace(/\s*(?:-\s*)?topic$/, '')
    .replace(/\s*vevo$/, '')
    .replace(/\s*official$/, '')
    .trim()
  if (!cleaned) return
  out.add(cleaned)
  const words = cleaned.split(' ').filter(Boolean)
  const latin = words.filter((w) => /[a-z]/.test(w) && ![...w].some((c) => c.charCodeAt(0) > 127))
  const nonLatin = words.filter((w) => [...w].some((c) => c.charCodeAt(0) > 127))
  if (latin.length && nonLatin.length) {
    const l = latin.join(' ')
    const nl = nonLatin.join(' ')
    if (l.length >= 2) out.add(l)
    if (nl.length >= 2) out.add(nl)
  }
}

function splitArtistCredits(names: string[]): string[] {
  const out = new Set<string>()
  for (const raw of names) {
    if (!raw?.trim()) continue
    addArtistVariants(out, raw)
    for (const part of raw.split(ARTIST_SEPARATORS)) {
      addArtistVariants(out, part)
    }
  }
  return [...out]
}

function artistPartVariants(part: string): string[] {
  const out = new Set<string>()
  addArtistVariants(out, part)
  for (const sub of part.split(ARTIST_SEPARATORS)) {
    addArtistVariants(out, sub)
  }
  return [...out].filter((x) => x.length >= 2)
}

function artistMatchBonus(targetArtists: string[], candArtists: string[], leadTarget: string): number | null {
  if (!targetArtists.length || !candArtists.length) return null
  if (candArtists.includes(leadTarget)) return 2_000
  if (targetArtists.some((a) => candArtists.includes(a))) return 1_400
  return null
}

function titleSimilarityScore(targetTitle: string, candidateTitle: string): number | null {
  if (!targetTitle || !candidateTitle) return null
  if (targetTitle === candidateTitle) return 10_000
  if (targetTitle.replace(/\s+/g, '') === candidateTitle.replace(/\s+/g, '')) return 9_500
  const tWords = targetTitle.split(' ').filter(Boolean)
  const cWords = candidateTitle.split(' ').filter(Boolean)
  if (!tWords.length || !cWords.length) return null
  if (tWords.length === 1) {
    return cWords.length === 1 && tWords[0] === cWords[0] ? 10_000 : null
  }
  const cSet = new Set(cWords)
  const tSet = new Set(tWords)
  let intersection = 0
  for (const w of tSet) if (cSet.has(w)) intersection++
  const union = new Set([...tSet, ...cSet]).size
  const jaccard = intersection / union
  const extraWords = Math.abs(cWords.length - tWords.length)
  const targetContained = ` ${candidateTitle} `.includes(` ${targetTitle} `) && extraWords <= 3
  const candContained = ` ${targetTitle} `.includes(` ${candidateTitle} `) && extraWords <= 2
  if (!targetContained && !candContained && jaccard < 0.72) return null
  const bonus = targetContained || candContained ? 1_000 : 0
  return 6_000 + Math.round(jaccard * 2_500) + bonus
}

function matchCandidateScore(
  targetTitle: string,
  targetArtistsRaw: string[],
  targetDurSec: number,
  candTitle: string,
  candArtistRaw: string,
  candDurSec: number | undefined,
  candViews: number | undefined,
  looseDuration: boolean,
): number | null {
  if (!candDurSec || candDurSec <= 0 || targetDurSec <= 0) return null

  const targetEditions = editionTags(targetTitle)
  if (!sameSet(targetEditions, editionTags(candTitle))) return null

  const candChannelVariants = channelVariantTags(candArtistRaw)
  if (candChannelVariants.size) {
    const allowed = new Set([...channelVariantTags(targetArtistsRaw.join(' ')), ...targetEditions])
    for (const v of candChannelVariants) if (!allowed.has(v)) return null
  }

  const targetArtists = splitArtistCredits(targetArtistsRaw)
  const leadTarget = targetArtists[0]
  if (!leadTarget) return null
  const cleanTarget = normalized(cleanAudioTitle(targetTitle))
  if (!cleanTarget) return null

  const candCredits = splitArtistCredits([candArtistRaw])
  const channelArtistBonus = artistMatchBonus(targetArtists, candCredits, leadTarget)

  const diffSec = Math.abs(targetDurSec - candDurSec)
  const strictTol = durationToleranceSec(targetDurSec)
  const isOfficialMvOverrun =
    !looseDuration &&
    channelArtistBonus !== null &&
    candDurSec > targetDurSec &&
    diffSec > strictTol &&
    diffSec <= officialMvToleranceSec(targetDurSec)

  if (looseDuration) {
    if (candDurSec < 50) return null
    const ratio = candDurSec / targetDurSec
    if (ratio < 0.55 || ratio > 1.45) return null
  } else if (!isOfficialMvOverrun && diffSec > strictTol) {
    return null
  }

  let bestTitleScore = titleSimilarityScore(cleanTarget, normalized(cleanAudioTitle(candTitle)))
  let bestArtistBonus = channelArtistBonus

  const considerSplit = (artistPart: string, titlePart: string) => {
    const partArtists = artistPartVariants(artistPart)
    const partArtistBonus = artistMatchBonus(targetArtists, partArtists, leadTarget)
    const partTitleScore = titleSimilarityScore(cleanTarget, normalized(cleanAudioTitle(titlePart)))
    if (partTitleScore !== null && (partArtistBonus !== null || bestArtistBonus !== null)) {
      const combined = Math.max(partArtistBonus ?? 0, bestArtistBonus ?? 0)
      if (bestTitleScore === null || bestArtistBonus === null || partTitleScore > bestTitleScore) {
        bestTitleScore = partTitleScore
        bestArtistBonus = combined
      }
    }
  }

  const allParts = candTitle.split(TITLE_SPLIT).map((p) => p.trim()).filter(Boolean)
  if (allParts.length >= 2) {
    considerSplit(allParts[0], allParts.slice(1).join(' - '))
    considerSplit(allParts[allParts.length - 1], allParts.slice(0, -1).join(' - '))
    for (let i = 0; i < allParts.length - 1; i++) {
      considerSplit(allParts[i], allParts[i + 1])
      considerSplit(allParts[i + 1], allParts[i])
    }
  }
  const byIdx = candTitle.search(BY_SPLIT)
  if (byIdx > 0) {
    const byParts = candTitle.split(BY_SPLIT)
    if (byParts.length >= 2) considerSplit(byParts.slice(1).join(' '), byParts[0])
  }

  if (bestTitleScore === null || bestArtistBonus === null) return null
  if ((looseDuration || isOfficialMvOverrun) && (bestTitleScore < 9_500 || bestArtistBonus < 2_000)) return null

  const officialChannelBonus = channelArtistBonus !== null ? 2_500 : 0
  let viewsAdjustment = 0
  if (candViews !== undefined) {
    if (channelArtistBonus !== null) {
      if (candViews >= 1_000) viewsAdjustment = Math.min(1_500, Math.round((Math.log10(candViews) - 2) * 250))
    } else if (candViews < 500) {
      viewsAdjustment = -3_000
    } else if (candViews < 2_000) {
      viewsAdjustment = -1_000
    } else if (candViews >= 10_000) {
      viewsAdjustment = Math.min(1_200, Math.round((Math.log10(candViews) - 3) * 300))
    }
  }
  const diffMs = diffSec * 1000
  const durationPenalty = isOfficialMvOverrun ? Math.min(2_500, Math.floor(diffMs / 10)) : Math.floor(diffMs / 10)
  return Math.max(1, bestTitleScore + bestArtistBonus + officialChannelBonus + viewsAdjustment - durationPenalty)
}

const POPULAR_BLOCKED_EDITIONS = new Set([
  'karaoke', 'backing track', 'minus', 'minus one', 'off vocal', 'no vocal', 'no vocals',
  'without vocal', 'without vocals', 'tutorial', 'lesson', 'tab', 'tabs', 'chord', 'chords',
  'synthesia', 'midi', 'ringtone', 'reaction', 'analysis', 'review', 'breakdown', 'interview',
  'commentary', 'teaser', 'trailer', 'snippet', 'preview', 'минус', 'минусовка', 'караоке',
  'под гитару', 'на пианино', 'на гитаре', 'без слов', 'без вокала', 'прохождение', 'геймплей',
  'カラオケ', '原曲歌手', 'リアクション', '切り抜き',
])

function popularYouTubeFallbackScore(
  targetTitle: string,
  targetArtistsRaw: string[],
  targetDurSec: number,
  candTitle: string,
  candArtistRaw: string,
  candDurSec: number | undefined,
  candViews: number | undefined,
): number | null {
  const minDur = targetDurSec >= 15 && targetDurSec <= 45 ? Math.max(12, Math.floor(targetDurSec / 2)) : 30
  const maxDur = targetDurSec > 450 ? targetDurSec * 2 : 900
  if (!candTitle.trim() || (candDurSec && (candDurSec < minDur || candDurSec > maxDur))) return null

  const targetEditions = editionTags(targetTitle)
  for (const tag of editionTags(candTitle)) {
    if (!targetEditions.has(tag) && (tag.startsWith('game:') || POPULAR_BLOCKED_EDITIONS.has(tag))) return null
  }
  const allowedChannel = new Set([...channelVariantTags(targetArtistsRaw.join(' ')), ...targetEditions])
  let channelPenalty = 0
  for (const v of channelVariantTags(candArtistRaw)) {
    if (!allowedChannel.has(v)) {
      channelPenalty = 4_000
      break
    }
  }

  const targetTitleWords = normalized(cleanAudioTitle(targetTitle)).split(' ').filter((w) => w.length >= 2)
  const targetArtists = splitArtistCredits(targetArtistsRaw)
  const targetArtistWords = targetArtists.flatMap((a) => a.split(' ')).filter((w) => w.length >= 2)
  const candCredits = splitArtistCredits([candArtistRaw])
  const candWords = new Set(
    [...normalized(candTitle).split(' '), ...candCredits.flatMap((a) => a.split(' '))].filter((w) => w.length >= 2),
  )

  const titleHits = targetTitleWords.filter((w) => candWords.has(w)).length
  const artistHits = targetArtistWords.filter((w) => candWords.has(w)).length
  const leadTarget = targetArtists[0]
  const officialBonus = leadTarget && artistMatchBonus(targetArtists, candCredits, leadTarget) !== null ? 5_000 : 0
  const hasOverlap = titleHits > 0 || artistHits > 0 || officialBonus > 0
  const tierBase = hasOverlap ? 15_000 : 1_000
  const views = Math.max(0, candViews ?? 0)
  const viewsScore = views > 0 ? Math.round(Math.log10(views + 1) * 2_000) : 0
  const durPenalty =
    targetDurSec > 0 && candDurSec ? Math.min(6_000, Math.floor((Math.abs(targetDurSec - candDurSec) * 1000) / 50)) : 0
  return Math.max(1, tierBase + viewsScore + officialBonus + titleHits * 800 + artistHits * 600 - durPenalty - channelPenalty)
}

const TIER_STRICT = 150_000
const TIER_LOOSE = 80_000
const BONUS_YT_SONG = 26_000
const BONUS_YT_VIDEO = 25_000
const BONUS_YT_WEB = 24_000
const BONUS_YANDEX = 10_000
const BONUS_SOUNDCLOUD = 0

async function buildMatchState(track: Track): Promise<MatchState> {
  const artist = track.artists[0]?.name && track.artists[0].name !== 'Spotify' ? track.artists[0].name : ''
  const cleanTitle = cleanAudioTitle(track.title)
  const query = artist ? `${artist} ${cleanTitle}` : cleanTitle
  const targetArtists = track.artists.map((a) => a.name).filter((n) => n && n !== 'Spotify')

  const strict: Candidate[] = []
  const loose: Candidate[] = []
  const popular: Candidate[] = []

  const [ytRes, webVideos, scRes, yaRes] = await Promise.all([
    youtube.searchAudioCandidates(query).catch(() => ({ songs: [] as youtube.YtSong[], videos: [] as youtube.YtSong[] })),
    youtube.searchWebVideos(query).catch(() => [] as youtube.YtSong[]),
    soundcloud.searchTracks(query, 15).catch(() => [] as soundcloud.ScTrack[]),
    accounts.ya ? yandex.search(query).catch(() => null) : Promise.resolve(null),
  ])

  let ytSongsMatched = 0
  for (const s of ytRes.songs) {
    const score = matchCandidateScore(track.title, targetArtists, track.duration, s.title, s.artists, s.duration, s.views, false)
    if (score !== null) {
      ytSongsMatched++
      strict.push({
        key: `yt:${s.videoId}`,
        kind: 'yt',
        videoId: s.videoId,
        score: TIER_STRICT + score + BONUS_YT_SONG,
        label: formatVariantLabel('YouTube Music', s.title, s.artists, s.duration, 'strict'),
      })
    } else {
      const l = matchCandidateScore(track.title, targetArtists, track.duration, s.title, s.artists, s.duration, s.views, true)
      if (l !== null) {
        loose.push({
          key: `yt:${s.videoId}`,
          kind: 'yt',
          videoId: s.videoId,
          score: TIER_LOOSE + l + BONUS_YT_SONG,
          label: formatVariantLabel('YouTube Music', s.title, s.artists, s.duration, 'loose'),
        })
      } else {
        const p = popularYouTubeFallbackScore(track.title, targetArtists, track.duration, s.title, s.artists, s.duration, s.views)
        if (p !== null) {
          popular.push({
            key: `yt:${s.videoId}`,
            kind: 'yt',
            videoId: s.videoId,
            score: p + 1_000,
            label: formatVariantLabel('YouTube Music', s.title, s.artists, s.duration, 'popular'),
          })
        }
      }
    }
  }
  for (const v of ytRes.videos) {
    const score = matchCandidateScore(track.title, targetArtists, track.duration, v.title, v.artists, v.duration, v.views, false)
    if (score !== null) {
      strict.push({
        key: `yt:${v.videoId}`,
        kind: 'yt',
        videoId: v.videoId,
        score: TIER_STRICT + score + BONUS_YT_VIDEO,
        label: formatVariantLabel('YouTube Music', v.title, v.artists, v.duration, 'strict'),
      })
    } else {
      const l = matchCandidateScore(track.title, targetArtists, track.duration, v.title, v.artists, v.duration, v.views, true)
      if (l !== null) {
        loose.push({
          key: `yt:${v.videoId}`,
          kind: 'yt',
          videoId: v.videoId,
          score: TIER_LOOSE + l + BONUS_YT_VIDEO,
          label: formatVariantLabel('YouTube Music', v.title, v.artists, v.duration, 'loose'),
        })
      } else {
        const p = popularYouTubeFallbackScore(track.title, targetArtists, track.duration, v.title, v.artists, v.duration, v.views)
        if (p !== null) {
          popular.push({
            key: `yt:${v.videoId}`,
            kind: 'yt',
            videoId: v.videoId,
            score: p,
            label: formatVariantLabel('YouTube', v.title, v.artists, v.duration, 'popular'),
          })
        }
      }
    }
  }

  for (const w of webVideos) {
    const score = matchCandidateScore(track.title, targetArtists, track.duration, w.title, w.artists, w.duration, w.views, false)
    if (score !== null) {
      const targetList = ytSongsMatched === 0 ? strict : loose
      const tierBase = ytSongsMatched === 0 ? TIER_STRICT : TIER_LOOSE
      targetList.push({
        key: `yt:${w.videoId}`,
        kind: 'yt',
        videoId: w.videoId,
        score: tierBase + score + BONUS_YT_WEB,
        label: formatVariantLabel('YouTube', w.title, w.artists, w.duration, 'strict'),
      })
    } else {
      const l = matchCandidateScore(track.title, targetArtists, track.duration, w.title, w.artists, w.duration, w.views, true)
      if (l !== null) {
        loose.push({
          key: `yt:${w.videoId}`,
          kind: 'yt',
          videoId: w.videoId,
          score: TIER_LOOSE + l + BONUS_YT_WEB,
          label: formatVariantLabel('YouTube', w.title, w.artists, w.duration, 'loose'),
        })
      } else {
        const p = popularYouTubeFallbackScore(track.title, targetArtists, track.duration, w.title, w.artists, w.duration, w.views)
        if (p !== null) {
          popular.push({
            key: `yt:${w.videoId}`,
            kind: 'yt',
            videoId: w.videoId,
            score: p,
            label: formatVariantLabel('YouTube', w.title, w.artists, w.duration, 'popular'),
          })
        }
      }
    }
  }

  if (yaRes) {
    for (const yid of yaRes.tracks.slice(0, 10)) {
      const yt = catalogTracks.get(yid)
      if (!yt || yt.playable === false) continue
      const candArtist = yt.artists.map((a) => a.name).join(', ')
      const score = matchCandidateScore(track.title, targetArtists, track.duration, yt.title, candArtist, yt.duration, undefined, false)
      if (score !== null) {
        strict.push({
          key: yid,
          kind: 'ya',
          track: yt,
          score: TIER_STRICT + score + BONUS_YANDEX,
          label: formatVariantLabel(tr('Яндекс Музыка'), yt.title, candArtist, yt.duration, 'strict'),
        })
      } else {
        const l = matchCandidateScore(track.title, targetArtists, track.duration, yt.title, candArtist, yt.duration, undefined, true)
        if (l !== null) {
          loose.push({
            key: yid,
            kind: 'ya',
            track: yt,
            score: TIER_LOOSE + l + BONUS_YANDEX,
            label: formatVariantLabel(tr('Яндекс Музыка'), yt.title, candArtist, yt.duration, 'loose'),
          })
        }
      }
    }
  }

  for (const s of scRes) {
    const streams = s.media?.transcodings ?? []
    if (s.streamable === false || s.policy === 'BLOCK' || s.policy === 'SNIP') continue
    if (streams.length && !streams.some((x) => !x.format.protocol.includes('encrypted') && !x.snipped)) continue
    const candArtist = s.publisher_metadata?.artist?.trim() || s.user?.username || ''
    const candDur = Math.round((s.full_duration ?? s.duration ?? 0) / 1000)
    const score = matchCandidateScore(track.title, targetArtists, track.duration, s.title ?? '', candArtist, candDur, undefined, false)
    if (score !== null) {
      strict.push({
        key: `sc:${s.id}`,
        kind: 'sc',
        track: s,
        score: TIER_STRICT + score + BONUS_SOUNDCLOUD,
        label: formatVariantLabel('SoundCloud', s.title ?? '', candArtist, candDur, 'strict'),
      })
    } else {
      const l = matchCandidateScore(track.title, targetArtists, track.duration, s.title ?? '', candArtist, candDur, undefined, true)
      if (l !== null) {
        loose.push({
          key: `sc:${s.id}`,
          kind: 'sc',
          track: s,
          score: TIER_LOOSE + l + BONUS_SOUNDCLOUD,
          label: formatVariantLabel('SoundCloud', s.title ?? '', candArtist, candDur, 'loose'),
        })
      }
    }
  }

  strict.sort((a, b) => b.score - a.score)
  loose.sort((a, b) => b.score - a.score)
  popular.sort((a, b) => b.score - a.score)
  const pool = [...strict, ...loose, ...popular]
  const seen = new Set<string>()
  const candidates = pool.filter((c) => {
    if (seen.has(c.key)) return false
    seen.add(c.key)
    return true
  })
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
