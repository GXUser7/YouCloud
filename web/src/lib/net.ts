// The site's way out to the music services. Through the Worker on the site's own origin,
// `/x/<host>/<path>` (see worker/index.ts); headers a page may not set itself — Cookie,
// User-Agent, Origin, Referer — travel as x-yc-* and the Worker puts them back.
//
// Yandex Music and YouTube's player refuse Cloudflare's addresses, so when the listener has the
// YouCloud extension, their requests go through it instead, from the listener's own IP.

import { extension, extensionFetch } from './extension'

/** Hosts that answer only a listener's own IP; the extension takes them when it is there. */
const HOME_HOSTS = ['yandex.net', 'music.youtube.com', 'www.youtube.com', 'youtubei.googleapis.com']

export type Route = 'extension' | 'worker'

const isHomeHost = (host: string) => HOME_HOSTS.some((h) => host === h || host.endsWith('.' + h))

/** Which way a request to [url] goes now. */
export async function routeOf(url: string): Promise<Route> {
  return isHomeHost(new URL(url).hostname) && (await extension()) ? 'extension' : 'worker'
}

export type ProxyInit = Omit<RequestInit, 'headers'> & {
  headers?: Record<string, string>
  cookie?: string
  ua?: string
  /** null drops the header the Worker would otherwise send for this service. */
  origin?: string | null
  referer?: string | null
  /** For YouTube through the extension: signed in as this browser is (see ExtensionInit). */
  session?: boolean | string
  timeoutMs?: number
}

export class HttpError extends Error {
  constructor(
    readonly status: number,
    readonly body: string,
    readonly url: string,
  ) {
    super(`HTTP ${status} от ${new URL(url).host}${detail(body)}`)
  }
}

/** What a refusal says, short: a JSON answer's own name for it rather than the whole of it. */
function detail(body: string): string {
  if (!body) return ''
  try {
    const j = JSON.parse(body)
    const said = j?.result?.name ?? j?.error?.name ?? j?.error?.message ?? j?.error ?? j?.message ?? j?.result?.message
    if (typeof said === 'string' && said) return ` (${said.slice(0, 80)})`
  } catch {
    // Not JSON: the start of what came back.
  }
  return body.trimStart().startsWith('<') ? '' : `: ${body.slice(0, 120)}`
}

/** [url] as the Worker serves it. */
export function viaWorker(url: string): string {
  const u = new URL(url)
  return `/x/${u.host}${u.pathname}${u.search}`
}

export async function request(url: string, init: ProxyInit = {}): Promise<Response> {
  const { cookie, ua, origin, referer, session, headers = {}, timeoutMs = 20_000, ...rest } = init
  if ((await routeOf(url)) === 'extension') {
    const overrides: Record<string, string | null> = {}
    if (cookie) overrides.cookie = cookie
    if (ua) overrides['user-agent'] = ua
    if (origin !== undefined) overrides.origin = origin
    if (referer !== undefined) overrides.referer = referer
    return extensionFetch(url, {
      method: rest.method,
      headers,
      body: typeof rest.body === 'string' ? rest.body : undefined,
      overrides,
      session,
      timeoutMs,
    })
  }
  const h: Record<string, string> = { ...headers }
  if (cookie) h['x-yc-cookie'] = cookie
  if (ua) h['x-yc-ua'] = ua
  if (origin !== undefined) h['x-yc-origin'] = origin ?? '-'
  if (referer !== undefined) h['x-yc-referer'] = referer ?? '-'
  const signal = rest.signal ?? AbortSignal.timeout(timeoutMs)
  return fetch(viaWorker(url), { ...rest, headers: h, signal })
}

async function checked(url: string, init?: ProxyInit): Promise<Response> {
  const response = await request(url, init)
  if (!response.ok) throw new HttpError(response.status, await response.text().catch(() => ''), url)
  return response
}

export async function getJson<T = unknown>(url: string, init?: ProxyInit): Promise<T> {
  return (await checked(url, init)).json() as Promise<T>
}

export async function getText(url: string, init?: ProxyInit): Promise<string> {
  return (await checked(url, init)).text()
}

export async function postJson<T = unknown>(url: string, body: unknown, init: ProxyInit = {}): Promise<T> {
  const response = await checked(url, {
    ...init,
    method: 'POST',
    headers: { 'content-type': 'application/json', ...init.headers },
    body: JSON.stringify(body),
  })
  return response.json() as Promise<T>
}

export async function postForm<T = unknown>(url: string, form: Record<string, string>, init: ProxyInit = {}): Promise<T> {
  const response = await checked(url, {
    ...init,
    method: 'POST',
    headers: { 'content-type': 'application/x-www-form-urlencoded', ...init.headers },
    body: new URLSearchParams(form).toString(),
  })
  return response.json() as Promise<T>
}

/** Walks [path] — keys and indices — through parsed JSON, undefined wherever it breaks off. */
export function at(value: unknown, ...path: (string | number)[]): any {
  let current: any = value
  for (const step of path) {
    if (current == null) return undefined
    current = current[step]
  }
  return current
}
