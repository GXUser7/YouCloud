// The YouCloud extension, when the listener has it (web/extension): it makes requests from this
// browser, so from the listener's own IP — what Yandex Music and YouTube's player need, since
// they refuse Cloudflare's. The page talks to it by window messages through its content script.
//
// Masaki (the listener's own browser) answers the same messages itself, with nothing to install:
// its hello says host 'masaki', and the page then fits itself to the browser around it.
//
// YouCloud Helper (web/helper), a small program beside the extension, does what Masaki does beyond
// it: YouTube's sound through yt-dlp, and the status in Discord.

import { tr } from './i18n.svelte'
import { version as latest } from '../../extension/manifest.json'
import { version as latestHelper } from '../../helper/version.json'

type Answer = { status?: number; statusText?: string; headers?: [string, string][]; body?: string; base64?: boolean; value?: unknown; error?: string }

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
  /** The answer as bytes (a broadcast's pieces), which travel as base64. */
  binary?: boolean
  timeoutMs?: number
}

export function extensionFetch(url: string, init: ExtensionInit = {}): Promise<Response> {
  const id = ++seq
  const { timeoutMs = 20_000, ...request } = init
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      waiting.delete(id)
      reject(new Error(tr('расширение не ответило')))
    }, timeoutMs)
    waiting.set(id, (answer) => {
      clearTimeout(timer)
      if (answer.error) reject(new Error(explain(answer.error)))
      else {
        // A status that has no body (204, 304) can't be given one, even an empty one.
        const empty = [101, 204, 205, 304].includes(answer.status ?? 200)
        const body = empty ? null : answer.base64 ? bytesOf(answer.body ?? '') : (answer.body ?? '')
        resolve(new Response(body, { status: answer.status, statusText: answer.statusText, headers: answer.headers }))
      }
    })
    post({ kind: 'fetch', id, request: { url, ...request } })
  })
}

/** Bytes that came as base64. */
function bytesOf(text: string): Uint8Array<ArrayBuffer> {
  const from = (Uint8Array as unknown as { fromBase64?: (s: string) => Uint8Array<ArrayBuffer> }).fromBase64
  if (from) return from(text)
  const raw = atob(text)
  const bytes = new Uint8Array(raw.length)
  for (let i = 0; i < raw.length; i++) bytes[i] = raw.charCodeAt(i)
  return bytes
}

/** The oldest extension that can sign in everywhere: 0.4 signs YouTube's requests with its session. */
const SIGN_IN_VERSION = [0, 4, 0]

export const outdated = (v: string) => older(v, LATEST_EXTENSION.split('.').map(Number))

const older = (v: string, than: number[]) => {
  const parts = v.split('.').map(Number)
  for (let i = 0; i < than.length; i++) if ((parts[i] ?? 0) !== than[i]) return (parts[i] ?? 0) < than[i]
  return false
}

const RELOAD_PAGE = tr('Расширение обновилось — обнови эту страницу (F5)')

/** What a failed ask means for the listener, in words they can act on. */
function explain(error: string): string {
  if (/context invalidated|receiving end does not exist|could not establish connection/i.test(error)) return RELOAD_PAGE
  return tr('расширение: %s', error)
}

/** One of the extension's own asks: 'sc-token' (SoundCloud's session cookie), 'sc-renew' (a token
 *  other than [args].stale, soundcloud.com's session renewed), 'sc-login', 'ya-login'. */
export async function extensionCall<T = unknown>(
  name: 'sc-token' | 'sc-renew' | 'sc-login' | 'ya-login' | 'yt-account' | 'yt-login' | 'yt-cookies' | 'helper',
  timeoutMs = 10_000,
  args?: Record<string, string>,
): Promise<T> {
  const v = await extension()
  if (!v) throw new Error(tr('Нужно расширение YouCloud'))
  if (older(v, SIGN_IN_VERSION)) {
    throw new Error(tr('Расширение %s устарело: новое — в Настройках, раздел «Расширение»', v))
  }
  const id = ++seq
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      waiting.delete(id)
      reject(new Error(tr('Расширение не ответило. %s', RELOAD_PAGE)))
    }, timeoutMs)
    waiting.set(id, (answer) => {
      clearTimeout(timer)
      if (answer.error) reject(new Error(explain(answer.error)))
      else resolve(answer.value as T)
    })
    post({ kind: 'call', id, name, args })
  })
}

/** Masaki from 0.7 finds YouTube's sound itself, with yt-dlp, as the Android app does. */
const HOST_STREAM_VERSION = [0, 7, 0]

/** The oldest extension that knows the helper: 0.6 hands it YouTube's sound and what plays. */
const HELPER_EXTENSION_VERSION = [0, 6, 0]

export type HelperInfo = { version: string; os: string }

/** The helper this site hands out (web/helper/version.json). */
export const LATEST_HELPER = latestHelper as string
export const HELPER_DOWNLOADS = { windows: '/youcloud-helper.exe', mac: '/youcloud-helper-mac.zip' }
export const helperOutdated = (v: string) => older(v, LATEST_HELPER.split('.').map(Number))

/** Whether the helper can be had here: an extension that knows it, and not Masaki, which needs none. */
export const helperPossible = () => !!version && host !== 'masaki' && !older(version, HELPER_EXTENSION_VERSION)

let helperFound: HelperInfo | null = null
let helperAsk: Promise<HelperInfo | null> | null = null

/** The helper, through the extension: its version, or null without it (or in Masaki, which needs
 *  none). Found once it stays found; not found, [again] asks anew — just after installing it. */
export function helper(again = false): Promise<HelperInfo | null> {
  if (helperFound) return Promise.resolve(helperFound)
  if (again) helperAsk = null
  helperAsk ??= (async () => {
    await extension()
    if (!helperPossible()) return null
    try {
      const info = await extensionCall<HelperInfo | null>('helper')
      if (info?.version) helperFound = info
      return info?.version ? info : null
    } catch {
      return null
    }
  })()
  return helperAsk
}

/** The oldest that fetch a YouTube broadcast's playlist and pieces for the page: Masaki 0.8, the extension 0.6.1. */
const MEDIA_VERSION = { masaki: [0, 8, 0], extension: [0, 6, 1] }

/** Whether the extension (or Masaki) fetches a broadcast's pieces, as a YouTube broadcast plays only so. */
export const mediaRelay = () => !!version && !older(version, host === 'masaki' ? MEDIA_VERSION.masaki : MEDIA_VERSION.extension)

/** The oldest that let the page read YouTube's sound, not only play it (CORS on googlevideo, which
 *  YouTube doesn't give): Masaki's bridge 0.10, the extension 0.8. */
const READ_VERSION = { masaki: [0, 10, 0], extension: [0, 8, 0] }

/** Whether the full player's wave may hear YouTube: the extension (or Masaki) lets the page read its sound. */
export const youtubeReadable = () => !!version && !older(version, host === 'masaki' ? READ_VERSION.masaki : READ_VERSION.extension)

/** Whether something beside the site finds YouTube's sound with yt-dlp: Masaki itself, or the helper. */
export async function hostStreams(): Promise<boolean> {
  if (host === 'masaki') return !!version && !older(version, HOST_STREAM_VERSION)
  return !!(await helper())
}

/** yt-dlp's answer: the sound's link and when it runs out (ms); [live], a broadcast's HLS playlist.
 *  Masaki before 0.8 and the helper before 1.1 don't say live, nor know a broadcast. */
export type HostStream = { url: string; expire: number; live?: boolean }

/** [videoId]'s sound from yt-dlp (Masaki's or the helper's), from this browser's IP and YouTube
 *  sign-in; [live], the site knows it for a broadcast. The first time yt-dlp is fetched, hence the
 *  long wait allowed. */
export function hostStream(videoId: string, live = false, timeoutMs = 120_000): Promise<HostStream> {
  const id = ++seq
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      waiting.delete(id)
      reject(new Error(tr('yt-dlp не ответил')))
    }, timeoutMs)
    waiting.set(id, (answer) => {
      clearTimeout(timer)
      if (answer.error) reject(new Error(answer.error))
      else resolve(answer.value as HostStream)
    })
    post({ kind: 'yt-stream', id, videoId, live })
  })
}

/** What plays, for the status in Discord (null: nothing). The helper shows it; the extension takes
 *  it away when this page goes. */
export function tellPresence(track: PresenceTrack | null) {
  post({ kind: 'presence', track })
}

export type PresenceTrack = {
  title: string
  artist: string
  album: string
  artwork: string
  playing: boolean
  /** Seconds; null for a broadcast, which has no length. */
  duration: number | null
  /** Seconds, as of now. */
  position: number | null
}

/** Called with the token a login caught (Yandex ID's, SoundCloud's), whenever one arrives. */
export function onToken(service: 'ya' | 'sc', listener: TokenListener) {
  tokenListeners[service].push(listener)
}
