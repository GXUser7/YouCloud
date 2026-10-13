// YouCloud's extension, the background half: makes the requests the site hands it, from this
// browser — so from the listener's own IP, which Yandex Music and YouTube serve where they turn
// Cloudflare's away. It reaches only the hosts in the manifest, and answers only YouCloud's pages
// (the content script runs nowhere else). It also signs the site in: SoundCloud's session from
// this browser — its cookie, or, signed out, the token its own requests carry once the listener
// signs in on the tab it opens — and Yandex's token from the Yandex ID login it opens. With YouCloud
// Helper installed (web/helper), it hands that program YouTube's sound to find with yt-dlp and what
// plays, for the listener's status in Discord.

const api = globalThis.browser ?? globalThis.chrome

/** How each service expects to be asked, as the Worker does it (worker/index.ts). */
const PROFILES = [
  // Yandex refuses a foreign Origin, and an extension's own would be one.
  { domains: ['yandex.net'], headers: { origin: null, referer: null, 'user-agent': 'Yandex-Music-API' } },
  { domains: ['music.youtube.com'], headers: { origin: 'https://music.youtube.com', referer: 'https://music.youtube.com/' } },
  { domains: ['www.youtube.com', 'youtubei.googleapis.com'], headers: { origin: 'https://www.youtube.com', referer: 'https://www.youtube.com/' } },
  // A YouTube broadcast's playlist and pieces, read as they are for hls.js on the page — asked as
  // yt-dlp and the app ask them, without the extension's own origin.
  { domains: ['googlevideo.com'], headers: { origin: null, referer: null } },
]

/** Where the media itself is: only read (GET), and nothing of the listener's goes along. */
const MEDIA = ['googlevideo.com']
const ALLOWED = PROFILES.flatMap((p) => p.domains)
const SITE_TABS = ['https://youcloud.inkerror67.workers.dev/*', 'http://localhost:5190/*']
const NOT_IN_A_TAB = -1
const MARK = 'ycr'

// Yandex Music's own Android client id: its login lands on music.yandex.ru with a token the
// Music API takes.
const YANDEX_LOGIN = 'https://oauth.yandex.ru/authorize?response_type=token&client_id=23cabbbdc6cd418abb4b39c32c41195d'
const SOUNDCLOUD_LOGIN = 'https://soundcloud.com/signin'
const YOUTUBE_LOGIN = 'https://accounts.google.com/ServiceLogin?service=youtube&continue=https%3A%2F%2Fmusic.youtube.com%2F'
const YOUTUBE_HOSTS = ['music.youtube.com', 'www.youtube.com', 'youtubei.googleapis.com']
const LOGIN_WINDOW_MS = 15 * 60_000

const headerOps = (headers) =>
  Object.entries(headers).map(([header, value]) =>
    value == null ? { header, operation: 'remove' } : { header, operation: 'set', value },
  )

// Header rules for the extension's own requests only (not in any tab): the pages the listener
// browses keep their headers.
const ready = (async () => {
  const existing = await api.declarativeNetRequest.getSessionRules()
  await api.declarativeNetRequest.updateSessionRules({
    removeRuleIds: existing.map((r) => r.id),
    addRules: PROFILES.map((p, i) => ({
      id: i + 1,
      priority: 1,
      action: { type: 'modifyHeaders', requestHeaders: headerOps(p.headers) },
      condition: { requestDomains: p.domains, tabIds: [NOT_IN_A_TAB] },
    })),
  })
})()

let sequence = 0

/**
 * Headers fetch can't set (User-Agent, Cookie, Origin, Referer) for one request: a rule matched
 * by a marker on that request's URL, gone as soon as the answer is in.
 */
async function withOverrides(url, overrides, run) {
  const entries = Object.entries(overrides ?? {})
  if (!entries.length) return run(url)
  const id = 1000 + (sequence++ % 50000)
  url.searchParams.set(MARK, String(id))
  await api.declarativeNetRequest.updateSessionRules({
    removeRuleIds: [id],
    addRules: [
      {
        id,
        priority: 2,
        action: { type: 'modifyHeaders', requestHeaders: headerOps(Object.fromEntries(entries)) },
        condition: { urlFilter: `${MARK}=${id}|`, tabIds: [NOT_IN_A_TAB] },
      },
    ],
  })
  try {
    return await run(url)
  } finally {
    await api.declarativeNetRequest.updateSessionRules({ removeRuleIds: [id] })
  }
}

/** The YouTube session of this browser: its SAPISID cookie, which signs requests as the site does. */
async function youtubeSapisid() {
  for (const name of ['SAPISID', '__Secure-3PAPISID']) {
    const cookie = await api.cookies.get({ url: 'https://www.youtube.com/', name })
    if (cookie?.value) return cookie.value
  }
  return null
}

/** The Cookie header this browser would send [url]: set on the request itself, so it goes along whatever
 *  the browser's SameSite rules make of a request from an extension. Chrome lists only cookies of
 *  domains the extension may reach — Google's sit on .youtube.com, hence *.youtube.com in the manifest. */
async function cookieHeader(url) {
  const cookies = await api.cookies.getAll({ url: url.origin + url.pathname })
  return cookies.map((c) => `${c.name}=${c.value}`).join('; ')
}

async function cookieValue(name) {
  return (await api.cookies.get({ url: 'https://www.youtube.com/', name }))?.value ?? null
}

/**
 * The Authorization header Google's web clients sign requests with today: for each of the SAPISID
 * cookies this browser has (first-party, 1P, 3P), SHA-1 of the time, the cookie and the origin —
 * as music.youtube.com itself and yt-dlp send it.
 */
async function sapisidAuthorization(origin, full = true) {
  const ts = Math.floor(Date.now() / 1000)
  const parts = []
  const schemes = [['SAPISIDHASH', 'SAPISID'], ['SAPISID1PHASH', '__Secure-1PAPISID'], ['SAPISID3PHASH', '__Secure-3PAPISID']]
  for (const [scheme, name] of full ? schemes : schemes.slice(0, 1)) {
    const value = await cookieValue(name)
    if (!value) continue
    const digest = await crypto.subtle.digest('SHA-1', new TextEncoder().encode(`${ts} ${value} ${origin}`))
    parts.push(`${scheme} ${ts}_${[...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, '0')).join('')}`)
  }
  return parts.join(' ')
}

async function relay(message) {
  let url
  try {
    url = new URL(message.url)
  } catch {
    return { error: 'bad url' }
  }
  if (url.protocol !== 'https:' || !ALLOWED.some((d) => url.hostname === d || url.hostname.endsWith('.' + d))) {
    return { error: `host not allowed: ${url.hostname}` }
  }
  await ready
  // A broadcast's playlist and pieces: the page can't take them itself — googlevideo answers across
  // sites only YouTube's own pages, and Chrome's own HLS takes no pieces from another host than the
  // playlist's but by CORS. The pieces come back as base64 ([binary]).
  if (MEDIA.some((d) => url.hostname.endsWith('.' + d))) {
    if ((message.method ?? 'GET') !== 'GET') return { error: 'only GET' }
    try {
      const range = message.headers?.range
      const response = await fetch(url.href, { headers: range ? { range } : {}, credentials: 'omit' })
      return {
        status: response.status,
        statusText: response.statusText,
        headers: [...response.headers],
        body: message.binary ? base64(await response.arrayBuffer()) : await response.text(),
        base64: !!message.binary,
      }
    } catch (e) {
      return { error: String(e?.message ?? e) }
    }
  }
  // Signed in as this browser is on YouTube: its cookies go along, and the request is signed with
  // SAPISIDHASH for the origin the rules give it. The cookies never leave the extension.
  const headers = { ...(message.headers ?? {}) }
  const overrides = { ...(message.overrides ?? {}) }
  // How the session goes along is the page's choice, as it finds what YouTube accepts in this
  // browser: the cookies Chrome itself attaches ('native…') or the full set put on by a rule
  // ('dnr…'); the request signed with all three SAPISID hashes ('…3') or the first ('…1').
  let credentials = 'omit'
  if (message.session && YOUTUBE_HOSTS.includes(url.hostname)) {
    const mode = typeof message.session === 'string' ? message.session : 'dnr3'
    const sapisid = await youtubeSapisid()
    if (sapisid) {
      const origin = url.hostname === 'music.youtube.com' ? 'https://music.youtube.com' : 'https://www.youtube.com'
      if (message.method && message.method !== 'GET') {
        headers['authorization'] = await sapisidAuthorization(origin, !mode.endsWith('1'))
        headers['x-goog-authuser'] = '0'
        headers['x-origin'] = origin
      }
      // An empty list would set an empty Cookie header and wipe the ones Chrome attaches itself.
      const cookie = mode.startsWith('dnr') ? await cookieHeader(url) : ''
      if (cookie) overrides.cookie = cookie
      credentials = 'include'
    }
  }
  try {
    return await withOverrides(url, overrides, async (target) => {
      const response = await fetch(target.href, {
        method: message.method ?? 'GET',
        headers,
        body: message.body ?? undefined,
        credentials,
      })
      return {
        status: response.status,
        statusText: response.statusText,
        headers: [...response.headers],
        body: await response.text(),
      }
    })
  } catch (e) {
    return { error: String(e?.message ?? e) }
  }
}

/** [buffer] as base64, for a message (which carries text). */
function base64(buffer) {
  const bytes = new Uint8Array(buffer)
  if (bytes.toBase64) return bytes.toBase64()
  let text = ''
  for (let i = 0; i < bytes.length; i += 0x8000) text += String.fromCharCode.apply(null, bytes.subarray(i, i + 0x8000))
  return btoa(text)
}

// ------------------------------------------------------------------------------------- the helper

// YouCloud Helper (web/helper): a small program the listener installs once, for what an extension
// can't reach from inside the browser — yt-dlp for YouTube's sound and the Discord app for the
// status. The browser starts it on the first ask and keeps it while the port is open; a port held
// open also keeps this worker alive. Messages carry an id to answer; one without wants no answer.
const HELPER = 'com.youcloud.helper'
const HELPER_MISSING = /not found|no such native application|not registered|access to the specified native messaging host is forbidden/i
let native = null

function helperPort() {
  if (native) return native
  const n = { port: api.runtime.connectNative(HELPER), pending: new Map(), seq: 0 }
  n.port.onMessage.addListener((message) => {
    const done = n.pending.get(message?.id)
    if (!done) return
    n.pending.delete(message.id)
    done(message)
  })
  n.port.onDisconnect.addListener((port) => {
    // Not installed, or gone: Chrome says why in lastError, Firefox on the port.
    const error = api.runtime.lastError?.message ?? port?.error?.message ?? 'helper exited'
    if (native === n) native = null
    for (const done of n.pending.values()) done({ error })
    n.pending.clear()
  })
  native = n
  return n
}

function askHelper(message, timeoutMs = 10_000) {
  return new Promise((resolve) => {
    let n
    try {
      n = helperPort()
    } catch (e) {
      resolve({ error: String(e?.message ?? e) })
      return
    }
    const id = ++n.seq
    const timer = setTimeout(() => {
      n.pending.delete(id)
      resolve({ error: 'timeout' })
    }, timeoutMs)
    n.pending.set(id, (answer) => {
      clearTimeout(timer)
      resolve(answer)
    })
    try {
      n.port.postMessage({ ...message, id })
    } catch (e) {
      n.pending.delete(id)
      clearTimeout(timer)
      resolve({ error: String(e?.message ?? e) })
    }
  })
}

/** The helper's version, or null when it isn't installed. Saying hello also gets yt-dlp ready. */
async function helperHello() {
  const answer = await askHelper({ type: 'hello' })
  if (answer.error) return HELPER_MISSING.test(answer.error) ? { value: null } : { error: answer.error }
  return { value: answer.value }
}

/** This browser's youtube.com cookies, for the helper to hand yt-dlp when YouTube wants a sign-in.
 *  None without a session; they go only to the helper, on this computer. */
async function youtubeCookies() {
  if (!(await youtubeSapisid())) return []
  const lists = await Promise.all(['https://www.youtube.com/', 'https://music.youtube.com/'].map((url) => api.cookies.getAll({ url })))
  const seen = new Set()
  return lists
    .flat()
    .filter((c) => {
      const key = `${c.domain}|${c.path}|${c.name}`
      if (seen.has(key)) return false
      seen.add(key)
      return true
    })
    .map((c) => ({
      domain: c.domain,
      hostOnly: c.hostOnly,
      path: c.path,
      secure: c.secure,
      httpOnly: c.httpOnly,
      expirationDate: c.expirationDate ?? 0,
      name: c.name,
      value: c.value,
    }))
}

/** [videoId]'s sound from the helper's yt-dlp; a broadcast's ([live]) is its HLS playlist. The first
 *  time it fetches yt-dlp, hence the long wait. */
async function ytStream(message) {
  const videoId = String(message.videoId ?? '')
  const answer = await askHelper({ type: 'yt-stream', videoId, fresh: !!message.fresh, live: !!message.live, cookies: await youtubeCookies() }, 120_000)
  return answer.error ? { error: answer.error } : { value: answer.value }
}

// The status in Discord follows the tab that told what plays last; a tab that only says it plays
// nothing doesn't take it from one that plays. The tab's line breaking (closed, reloaded, left)
// takes the status away.
let presenceFrom = null

function tellPresence(track) {
  // Nothing to show and no helper running: not worth starting it.
  if (!native && !track?.playing) return
  try {
    helperPort().port.postMessage({ type: 'presence', track })
  } catch {
    // the helper went away; the next change starts it again
  }
}

api.runtime.onConnect.addListener((port) => {
  if (port.name !== 'yc-presence' || port.sender?.id !== api.runtime.id) return
  port.onMessage.addListener((message) => {
    const track = message?.track ?? null
    if (!track?.playing && presenceFrom !== port) return
    presenceFrom = port
    tellPresence(track)
  })
  port.onDisconnect.addListener(() => {
    if (presenceFrom === port) {
      presenceFrom = null
      tellPresence(null)
    }
    letHelperGo()
  })
})

// With YouCloud closed everywhere the helper isn't needed: let it go (and with it the worker).
let idle
function letHelperGo() {
  clearTimeout(idle)
  idle = setTimeout(async () => {
    if (!native || native.pending.size) return
    const open = await api.tabs.query({ url: SITE_TABS }).catch(() => [{}])
    if (open.length || !native) return
    native.port.disconnect()
    native = null
  }, 15_000)
}

api.tabs.onRemoved.addListener(() => {
  if (native) letHelperGo()
})

/** The site's other asks: who is signed in where, and a Yandex login. */
async function call(name) {
  if (name === 'helper') return helperHello()
  if (name === 'sc-token') {
    const cookie = await api.cookies.get({ url: 'https://soundcloud.com/', name: 'oauth_token' })
    return { value: cookie?.value ? decodeURIComponent(cookie.value) : null }
  }
  if (name === 'sc-login') {
    const tab = await api.tabs.create({ url: SOUNDCLOUD_LOGIN })
    await api.storage.local.set({ scLoginAt: Date.now(), scLoginTab: tab.id })
    return { value: true }
  }
  if (name === 'yt-account') {
    return { value: !!(await youtubeSapisid()) }
  }
  if (name === 'yt-cookies') {
    // Only the names, for diagnosing a session YouTube won't take; the values stay here.
    // By address, not by domain: the extension may read only the hosts it has, not youtube.com itself.
    const lists = await Promise.all(['https://www.youtube.com/', 'https://music.youtube.com/'].map((url) => api.cookies.getAll({ url })))
    return { value: [...new Set(lists.flat().map((c) => c.name))].sort() }
  }
  if (name === 'yt-login') {
    await api.tabs.create({ url: YOUTUBE_LOGIN })
    return { value: true }
  }
  if (name === 'ya-login') {
    await api.storage.local.set({ yaLoginAt: Date.now() })
    await api.tabs.create({ url: YANDEX_LOGIN })
    return { value: true }
  }
  return { error: `unknown call: ${name}` }
}

/** A token caught on music.yandex.ru: kept only when YouCloud asked for it; the page picks it up. */
async function caughtYandexToken(message, sender) {
  const { yaLoginAt } = await api.storage.local.get('yaLoginAt')
  if (!yaLoginAt || Date.now() - yaLoginAt > LOGIN_WINDOW_MS) return
  await api.storage.local.set({ yaToken: { token: message.token, expiresIn: message.expiresIn, at: Date.now() } })
  await api.storage.local.remove('yaLoginAt')
  if (sender.tab?.id != null) api.tabs.remove(sender.tab.id).catch(() => {})
}

/**
 * SoundCloud's site sends its token with every API request (Authorization: OAuth …): once the
 * listener has signed in on the tab YouCloud opened, the first such request hands it over, as
 * the app's login WebView takes it.
 */
async function caughtSoundCloudToken(token) {
  const { scLoginAt, scLoginTab } = await api.storage.local.get(['scLoginAt', 'scLoginTab'])
  if (!scLoginAt || Date.now() - scLoginAt > LOGIN_WINDOW_MS) return
  await api.storage.local.set({ scToken: { token, at: Date.now() } })
  await api.storage.local.remove(['scLoginAt', 'scLoginTab'])
  if (scLoginTab != null) api.tabs.remove(scLoginTab).catch(() => {})
}

function watchSoundCloud(details) {
  const auth = details.requestHeaders?.find((h) => h.name.toLowerCase() === 'authorization')?.value
  if (auth?.startsWith('OAuth ')) caughtSoundCloudToken(auth.slice('OAuth '.length).trim())
}

try {
  api.webRequest.onBeforeSendHeaders.addListener(watchSoundCloud, { urls: ['https://api-v2.soundcloud.com/*'] }, ['requestHeaders', 'extraHeaders'])
} catch {
  // Firefox has no 'extraHeaders' and shows Authorization without it.
  api.webRequest.onBeforeSendHeaders.addListener(watchSoundCloud, { urls: ['https://api-v2.soundcloud.com/*'] }, ['requestHeaders'])
}

api.runtime.onMessage.addListener((message, sender, sendResponse) => {
  if (sender.id !== api.runtime.id) return
  if (message?.type === 'yc-fetch') {
    relay(message).then(sendResponse)
    return true
  }
  if (message?.type === 'yc-call') {
    call(message.name)
      .then(sendResponse)
      .catch((e) => sendResponse({ error: String(e?.message ?? e) }))
    return true
  }
  if (message?.type === 'yc-yt-stream') {
    ytStream(message)
      .then(sendResponse)
      .catch((e) => sendResponse({ error: String(e?.message ?? e) }))
    return true
  }
  if (message?.type === 'yc-ya-token') {
    caughtYandexToken(message, sender)
  }
})

// Installed or updated while YouCloud is open: Chrome gives already open pages no content script,
// so the site's tabs get their bridge here — the site sees the extension without a reload.

api.runtime.onInstalled.addListener(async () => {
  const tabs = await api.tabs.query({ url: SITE_TABS })
  for (const tab of tabs) {
    api.scripting.executeScript({ target: { tabId: tab.id }, files: ['bridge.js'] }).catch(() => {})
  }
})
