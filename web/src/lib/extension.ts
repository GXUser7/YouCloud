// The YouCloud extension, when the listener has it (web/extension): it makes requests from this
// browser, so from the listener's own IP — what Yandex Music and YouTube's player need, since
// they refuse Cloudflare's. The page talks to it by window messages through its content script.
//
// Masaki (the listener's own browser) answers the same messages itself, with nothing to install:
// its hello says host 'masaki', and the page then fits itself to the browser around it.

import { version as latest } from '../../extension/manifest.json'

type Answer = { status?: number; statusText?: string; headers?: [string, string][]; body?: string; value?: unknown; error?: string }

let version: string | null = null
/** Who answers: 'masaki' when the browser itself does, null for the extension. */
let host: string | null = null
type HostEvent = { event?: string; service?: string; seed?: string; dark?: boolean }
const eventListeners = new Set<(e: HostEvent) => void>()
let seq = 0
const waiting = new Map<number, (answer: Answer) => void>()
const helloWaiters: (() => void)[] = []
const versionListeners = new Set<(version: string) => void>()
type TokenListener = (token: string, expiresIn: number) => void
const tokenListeners: Record<'ya' | 'sc', TokenListener[]> = { ya: [], sc: [] }

addEventListener('message', (event) => {
  if (event.source !== window || event.origin !== location.origin) return
  const data = event.data
  if (!data || typeof data !== 'object' || data.yc !== 'ext') return
  if (data.kind === 'hello') {
    version = data.version
    if (typeof data.host === 'string' && data.host !== host) {
      host = data.host
      document.documentElement.dataset.host = data.host
    }
    helloWaiters.splice(0).forEach((f) => f())
    versionListeners.forEach((f) => f(data.version))
  } else if (data.kind === 'response') {
    waiting.get(data.id)?.(data)
    waiting.delete(data.id)
  } else if (data.kind === 'event') {
    eventListeners.forEach((f) => f(data))
  } else if ((data.kind === 'ya-token' || data.kind === 'sc-token') && typeof data.token === 'string') {
    tokenListeners[data.kind === 'ya-token' ? 'ya' : 'sc'].forEach((f) => f(data.token, Number(data.expiresIn) || 0))
  }
})

const post = (data: object) => window.postMessage({ yc: 'page', ...data }, location.origin)

let detection: Promise<string | null> | null = null

/** The extension's version, or null without it; asked once, answered within a moment. */
export function extension(): Promise<string | null> {
  if (version) return Promise.resolve(version)
  detection ??= new Promise((resolve) => {
    const done = () => resolve(version)
    helloWaiters.push(done)
    post({ kind: 'ping' })
    setTimeout(done, 400)
  })
  return detection
}

export const extensionVersion = () => version

/** 'masaki' when the site is open in Masaki, which is the extension itself. */
export const extensionHost = () => host

/** What the browser tells of its own accord: a sign-in on YouTube, for one. */
export function onHostEvent(f: (e: HostEvent) => void): () => void {
  eventListeners.add(f)
  return () => eventListeners.delete(f)
}

/** The extension this site hands out (/youcloud-extension.zip), built from web/extension. */
export const LATEST_EXTENSION = latest as string
export const EXTENSION_ZIP_URL = '/youcloud-extension.zip'

/** Calls back when the extension says hello: also when it is installed while the page is open. */
export function onExtension(f: (version: string) => void): () => void {
  versionListeners.add(f)
  return () => versionListeners.delete(f)
}

export type ExtensionInit = {
  method?: string
  headers?: Record<string, string>
  body?: string
  overrides?: Record<string, string | null>
  credentials?: 'omit' | 'include'
  /** YouTube, as this browser is signed in there: the extension adds the session itself, the way
   *  named ('native3', 'dnr3', 'native1', 'dnr1'; true is the extension's default). */
  session?: boolean | string
  timeoutMs?: number
}

export function extensionFetch(url: string, init: ExtensionInit = {}): Promise<Response> {
  const id = ++seq
  const { timeoutMs = 20_000, ...request } = init
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      waiting.delete(id)
      reject(new Error('расширение не ответило'))
    }, timeoutMs)
    waiting.set(id, (answer) => {
      clearTimeout(timer)
      if (answer.error) reject(new Error(explain(answer.error)))
      else {
        // A status that has no body (204, 304) can't be given one, even an empty one.
        const empty = [101, 204, 205, 304].includes(answer.status ?? 200)
        resolve(new Response(empty ? null : (answer.body ?? ''), { status: answer.status, statusText: answer.statusText, headers: answer.headers }))
      }
    })
    post({ kind: 'fetch', id, request: { url, ...request } })
  })
}

/** The oldest extension that can sign in everywhere: 0.4 signs YouTube's requests with its session. */
const SIGN_IN_VERSION = [0, 4, 0]

export const outdated = (v: string) => older(v, LATEST_EXTENSION.split('.').map(Number))

const older = (v: string, than: number[]) => {
  const parts = v.split('.').map(Number)
  for (let i = 0; i < than.length; i++) if ((parts[i] ?? 0) !== than[i]) return (parts[i] ?? 0) < than[i]
  return false
}

const RELOAD_PAGE = 'Расширение обновилось — обнови эту страницу (F5)'

/** What a failed ask means for the listener, in words they can act on. */
function explain(error: string): string {
  if (/context invalidated|receiving end does not exist|could not establish connection/i.test(error)) return RELOAD_PAGE
  return `расширение: ${error}`
}

/** One of the extension's own asks: 'sc-token' (SoundCloud's session cookie), 'sc-login', 'ya-login'. */
export async function extensionCall<T = unknown>(name: 'sc-token' | 'sc-login' | 'ya-login' | 'yt-account' | 'yt-login' | 'yt-cookies', timeoutMs = 10_000): Promise<T> {
  const v = await extension()
  if (!v) throw new Error('Нужно расширение YouCloud')
  if (older(v, SIGN_IN_VERSION)) {
    throw new Error(`Расширение ${v} устарело: новое — в Настройках, раздел «Расширение»`)
  }
  const id = ++seq
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      waiting.delete(id)
      reject(new Error(`Расширение не ответило. ${RELOAD_PAGE}`))
    }, timeoutMs)
    waiting.set(id, (answer) => {
      clearTimeout(timer)
      if (answer.error) reject(new Error(explain(answer.error)))
      else resolve(answer.value as T)
    })
    post({ kind: 'call', id, name })
  })
}

/** Called with the token a login caught (Yandex ID's, SoundCloud's), whenever one arrives. */
export function onToken(service: 'ya' | 'sc', listener: TokenListener) {
  tokenListeners[service].push(listener)
}
