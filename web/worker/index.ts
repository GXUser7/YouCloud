// YouCloud's Worker: serves the site and passes the site's requests on to the music services.
//
// Browsers can't read SoundCloud's, Yandex's or YouTube's APIs from another origin (CORS), and
// can't set the headers those APIs expect (User-Agent, Cookie, Origin). So the site asks this
// Worker instead, on its own origin: `/x/<host>/<path>?<query>` goes to `https://<host>/<path>`
// with the headers each service wants. Nothing is stored here — tokens live in the browser and
// only pass through, and every decision about what to ask stays in the site's own code.
//
// Only the services' own hosts are reachable, so the Worker can't be used as an open proxy.

const PREFIX = '/x/'

const DESKTOP_UA =
  'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36'

type Profile = { ua?: string; origin?: string; referer?: string }

/** Hosts the site may reach, by suffix, and how each service expects to be asked. */
const SERVICES: { suffixes: string[]; profile: Profile }[] = [
  {
    suffixes: ['soundcloud.com', 'sndcdn.com', 'soundcloud.cloud'],
    profile: { ua: DESKTOP_UA, origin: 'https://soundcloud.com', referer: 'https://soundcloud.com/' },
  },
  // Yandex refuses requests that carry a foreign Origin; the Android client sends none.
  { suffixes: ['api.music.yandex.net', 'storage.mds.yandex.net'], profile: { ua: 'Yandex-Music-API' } },
  {
    suffixes: ['music.youtube.com', 'www.youtube.com', 'youtubei.googleapis.com'],
    profile: { ua: DESKTOP_UA, origin: 'https://music.youtube.com', referer: 'https://music.youtube.com/' },
  },
  // Stream URLs are signed for the client that asked for them; the site says which (x-yc-ua).
  { suffixes: ['googlevideo.com'], profile: { ua: DESKTOP_UA, origin: 'https://www.youtube.com', referer: 'https://www.youtube.com/' } },
  { suffixes: ['lrclib.net'], profile: {} },
]

/** Headers the site may send through as they are. */
const PASS = [
  'accept',
  'accept-language',
  'authorization',
  'content-type',
  'range',
  'if-none-match',
  'if-modified-since',
  'x-goog-authuser',
  'x-origin',
  'x-goog-visitor-id',
  'x-youtube-client-name',
  'x-youtube-client-version',
  'x-yandex-music-client',
  'x-yandex-music-without-invocation-info',
]

/** Headers a page can't set itself, sent as x-yc-*: the Worker puts them back. */
const FORBIDDEN = { 'x-yc-cookie': 'cookie', 'x-yc-ua': 'user-agent', 'x-yc-origin': 'origin', 'x-yc-referer': 'referer' }

/** Response headers that would tie the answer to the service's site rather than ours. */
const DROP = ['set-cookie', 'access-control-allow-origin', 'access-control-allow-credentials', 'content-security-policy', 'strict-transport-security', 'alt-svc', 'report-to', 'nel']

function profileOf(host: string): Profile | null {
  for (const service of SERVICES) {
    if (service.suffixes.some((s) => host === s || host.endsWith('.' + s))) return service.profile
  }
  return null
}

function fail(status: number, message: string): Response {
  return new Response(JSON.stringify({ error: message }), {
    status,
    headers: { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store' },
  })
}

async function proxy(request: Request, url: URL): Promise<Response> {
  // Only the site itself: a page elsewhere sending its visitors' browsers here is turned away.
  const origin = request.headers.get('origin')
  if (origin && origin !== url.origin) return fail(403, 'foreign origin')

  const rest = url.pathname.slice(PREFIX.length)
  const slash = rest.indexOf('/')
  const host = (slash < 0 ? rest : rest.slice(0, slash)).toLowerCase()
  const path = slash < 0 ? '/' : rest.slice(slash)
  if (!/^[a-z0-9.-]+$/.test(host)) return fail(400, 'bad host')

  const profile = profileOf(host)
  if (!profile) return fail(403, `host not allowed: ${host}`)

  const headers = new Headers()
  if (profile.ua) headers.set('user-agent', profile.ua)
  if (profile.origin) headers.set('origin', profile.origin)
  if (profile.referer) headers.set('referer', profile.referer)
  for (const name of PASS) {
    const value = request.headers.get(name)
    if (value) headers.set(name, value)
  }
  for (const [from, to] of Object.entries(FORBIDDEN)) {
    const value = request.headers.get(from)
    if (value === '-') headers.delete(to)
    else if (value) headers.set(to, value)
  }

  const target = `https://${host}${path}${url.search}`
  const hasBody = request.method !== 'GET' && request.method !== 'HEAD'
  let upstream: Response
  try {
    upstream = await fetch(target, {
      method: request.method,
      headers,
      body: hasBody ? request.body : undefined,
      redirect: 'manual',
    })
  } catch (e) {
    return fail(502, `upstream unreachable: ${(e as Error).message}`)
  }

  const out = new Headers(upstream.headers)
  for (const name of DROP) out.delete(name)
  // A redirect between the services' own hosts stays behind the Worker; anywhere else is refused.
  const location = upstream.headers.get('location')
  if (location) {
    const next = new URL(location, target)
    if (next.protocol === 'https:' && profileOf(next.hostname)) out.set('location', `${PREFIX}${next.host}${next.pathname}${next.search}`)
    else out.delete('location')
  }
  out.set('x-yc-upstream', String(upstream.status))
  // A playlist (HLS) names its pieces by the CDN's own addresses, and a browser that plays HLS
  // itself fetches them straight from there — blocked, where the site came here for that reason.
  // So they are named behind the Worker too.
  if (upstream.ok && (/mpegurl/i.test(upstream.headers.get('content-type') ?? '') || path.endsWith('.m3u8'))) {
    const playlist = (await upstream.text()).replace(/https:\/\/([a-z0-9.-]+)(\/[^\s"#]*)/gi, (whole, to: string, rest: string) =>
      profileOf(to.toLowerCase()) ? `${PREFIX}${to.toLowerCase()}${rest}` : whole,
    )
    out.delete('content-length')
    out.delete('content-encoding')
    return new Response(playlist, { status: upstream.status, statusText: upstream.statusText, headers: out })
  }
  return new Response(upstream.body, { status: upstream.status, statusText: upstream.statusText, headers: out })
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url)
    if (url.pathname.startsWith(PREFIX)) return proxy(request, url)
    if (url.pathname === '/x') return fail(400, 'no host')
    return env.ASSETS.fetch(request)
  },
} satisfies ExportedHandler<Env>

interface Env {
  ASSETS: Fetcher
}
