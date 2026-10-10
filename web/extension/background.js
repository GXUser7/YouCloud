// YouCloud's extension, the background half: makes the requests the site hands it, from this
// browser — so from the listener's own IP, which Yandex Music and YouTube serve where they turn
// Cloudflare's away. It reaches only the hosts in the manifest, and answers only YouCloud's pages
// (the content script runs nowhere else). It also signs the site in: SoundCloud's session from
// this browser — its cookie, or, signed out, the token its own requests carry once the listener
// signs in on the tab it opens — and Yandex's token from the Yandex ID login it opens.

const api = globalThis.browser ?? globalThis.chrome

/** How each service expects to be asked, as the Worker does it (worker/index.ts). */
const PROFILES = [
  // Yandex refuses a foreign Origin, and an extension's own would be one.
  { domains: ['yandex.net'], headers: { origin: null, referer: null, 'user-agent': 'Yandex-Music-API' } },
  { domains: ['music.youtube.com'], headers: { origin: 'https://music.youtube.com', referer: 'https://music.youtube.com/' } },
  { domains: ['www.youtube.com', 'youtubei.googleapis.com'], headers: { origin: 'https://www.youtube.com', referer: 'https://www.youtube.com/' } },
]

const ALLOWED = PROFILES.flatMap((p) => p.domains)
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

/** The site's other asks: who is signed in where, and a Yandex login. */
async function call(name) {
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
  if (message?.type === 'yc-ya-token') {
    caughtYandexToken(message, sender)
  }
})

// Installed or updated while YouCloud is open: Chrome gives already open pages no content script,
// so the site's tabs get their bridge here — the site sees the extension without a reload.
const SITE_TABS = ['https://youcloud.inkerror67.workers.dev/*', 'http://localhost:5190/*']

api.runtime.onInstalled.addListener(async () => {
  const tabs = await api.tabs.query({ url: SITE_TABS })
  for (const tab of tabs) {
    api.scripting.executeScript({ target: { tabId: tab.id }, files: ['bridge.js'] }).catch(() => {})
  }
})
