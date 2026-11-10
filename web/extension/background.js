// YouCloud's extension, the background half: makes the requests the site hands it, from this
// browser — so from the listener's own IP, which Yandex Music and YouTube serve where they turn
// Cloudflare's away. It reaches only the hosts in the manifest, and answers only YouCloud's pages
// (the content script runs nowhere else). It also signs the site in: SoundCloud's session from
// this browser — its cookie, or, signed out, the token its own requests carry once the listener
// signs in on the tab it opens — and renews it when it expires; and Yandex's token from the Yandex
// ID login it opens. With YouCloud Helper installed (web/helper), it hands that program YouTube's
// sound to find with yt-dlp and what plays, for the listener's status in Discord. And it lets the
// site read YouTube's sound as it plays, for the full player's wave (READ_MEDIA).

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
/** The same pages, as a rule names where a request comes from. */
const SITE_DOMAINS = ['youcloud.inkerror67.workers.dev', 'localhost']

/**
 * YouTube's sound in the site's own <audio>, asked in CORS so the full player's wave can hear it:
 * googlevideo answers that only to YouTube, so the answer is let read by the site. Nothing else
 * changes — the request, and what the site's page may reach.
 */
const READ_MEDIA = {
  id: 100,
  priority: 1,
  action: { type: 'modifyHeaders', responseHeaders: [{ header: 'access-control-allow-origin', operation: 'set', value: '*' }] },
  condition: { requestDomains: MEDIA, initiatorDomains: SITE_DOMAINS, resourceTypes: ['media'] },
}
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
// browses keep their headers. And the one for the site's <audio> playing YouTube (READ_MEDIA).
const ready = (async () => {
  const existing = await api.declarativeNetRequest.getSessionRules()
  await api.declarativeNetRequest.updateSessionRules({
    removeRuleIds: existing.map((r) => r.id),
    addRules: [
      ...PROFILES.map((p, i) => ({
        id: i + 1,
        priority: 1,
        action: { type: 'modifyHeaders', requestHeaders: headerOps(p.headers) },
        condition: { requestDomains: p.domains, tabIds: [NOT_IN_A_TAB] },
      })),
      READ_MEDIA,
    ],
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

// SoundCloud mutations run in its own page, not the service worker. A DataDome cookie is bound
// to the browser that earned it; the listener solves the challenge in this very same tab.
let soundCloudWriteTab = null
let lastSoundCloudChallenge = null
let soundCloudWrites = Promise.resolve()

function safeSoundCloudChallenge(value) {
  try {
    const url = new URL(value)
    return url.protocol === 'https:' && !url.username && !url.password && !url.port &&
      (url.hostname === 'captcha-delivery.com' || url.hostname.endsWith('.captcha-delivery.com')) ? url.href : null
  } catch { return null }
}

function validateSoundCloudWrite(args) {
  const url = new URL(String(args?.url ?? ''))
  const method = String(args?.method ?? '')
  const like = /^\/users\/\d+\/track_likes\/\d+$/.test(url.pathname) && ['PUT', 'DELETE'].includes(method)
  const follow = /^\/me\/followings\/\d+$/.test(url.pathname) && ['POST', 'DELETE'].includes(method)
  if (url.origin !== 'https://api-v2.soundcloud.com' || url.username || url.password || url.hash || !(like || follow)) throw new Error('SoundCloud write not allowed')
  const token = String(args?.token ?? '')
  if (!token || token.length > 4096 || /[\r\n]/.test(token)) throw new Error('SoundCloud session missing')
  return { url: url.href, method, token }
}

async function soundCloudPage() {
  if (soundCloudWriteTab != null) {
    const existing = await api.tabs.get(soundCloudWriteTab).catch(() => null)
    if (existing?.url && new URL(existing.url).origin === 'https://soundcloud.com') return existing.id
  }
  const tab = await api.tabs.create({ url: 'https://soundcloud.com/', active: false })
  soundCloudWriteTab = tab.id
  const deadline = Date.now() + 25_000
  while (Date.now() < deadline) {
    const current = await api.tabs.get(tab.id)
    if (current.status === 'complete') {
      if (new URL(current.url).origin !== 'https://soundcloud.com') throw new Error('SoundCloud page changed')
      // Give SoundCloud's own scripts, including its DataDome tag, time to start.
      await new Promise((resolve) => setTimeout(resolve, 1500))
      return tab.id
    }
    await new Promise((resolve) => setTimeout(resolve, 200))
  }
  throw new Error('SoundCloud page did not load')
}

async function soundCloudWrite(args) {
  const request = validateSoundCloudWrite(args)
  const task = soundCloudWrites.catch(() => {}).then(async () => {
    const tabId = await soundCloudPage()
    const result = await api.scripting.executeScript({
      target: { tabId }, world: 'MAIN', args: [request],
      func: async ({ url, method, token }) => {
        if (location.origin !== 'https://soundcloud.com') throw new Error('SoundCloud page changed')
        const headers = { Authorization: `OAuth ${token}`, Accept: 'application/json, text/javascript, */*; q=0.1' }
        const cookie = document.cookie.match(/(?:^|;\s*)datadome=([^;]+)/)
        if (cookie) headers['X-Datadome-ClientId'] = decodeURIComponent(cookie[1])
        // A tag may abort the fetch while showing its challenge. Return that challenge rather
        // than losing it behind a generic network error. No challenge is ever solved by code.
        const displayedChallenge = () => {
          for (const frame of document.querySelectorAll('iframe[src]')) {
            try {
              const u = new URL(frame.src)
              if (u.protocol === 'https:' && (u.hostname === 'captcha-delivery.com' || u.hostname.endsWith('.captcha-delivery.com'))) return u.href
            } catch { /* Not a challenge frame. */ }
          }
          return null
        }
        try {
          const response = await fetch(url, { method, headers, credentials: 'include', signal: AbortSignal.timeout(20_000) })
          return { status: response.status, body: await response.text() }
        } catch (e) {
          const challenge = displayedChallenge()
          if (challenge) return { status: 403, body: JSON.stringify({ url: challenge }) }
          throw new Error(String(e?.message ?? e))
        }
      },
    })
    const response = result[0]?.result
    if (!response || !Number.isInteger(response.status) || typeof response.body !== 'string') throw new Error('SoundCloud did not answer')
    if (response.status === 403) {
      try { lastSoundCloudChallenge = safeSoundCloudChallenge(JSON.parse(response.body).url) } catch { lastSoundCloudChallenge = null }
    }
    return { value: response }
  })
  soundCloudWrites = task
  return task
}

async function showSoundCloudChallenge(args) {
  const url = safeSoundCloudChallenge(args?.url)
  if (!url || url !== lastSoundCloudChallenge || soundCloudWriteTab == null) throw new Error('SoundCloud challenge expired: retry the action')
  const tabId = await soundCloudPage()
  await api.scripting.executeScript({
    target: { tabId }, world: 'MAIN', args: [url],
    func: (url) => {
      if (location.origin !== 'https://soundcloud.com') throw new Error('SoundCloud page changed')
      // Its own tag may already have opened the matching frame.
      if ([...document.querySelectorAll('iframe[src]')].some((f) => f.src === url)) return
      document.getElementById('youcloud-soundcloud-challenge')?.remove()
      const host = document.createElement('div')
      host.id = 'youcloud-soundcloud-challenge'
      host.style.cssText = 'position:fixed;inset:0;z-index:2147483647;background:#fff;display:flex;flex-direction:column'
      const close = document.createElement('button')
      close.textContent = 'Закрыть проверку SoundCloud'
      close.style.cssText = 'height:52px;padding:12px 24px;border:0;background:#f2f2f2;color:#222;font:600 16px system-ui;cursor:pointer'
      const frame = document.createElement('iframe')
      frame.title = 'Проверка SoundCloud'
      frame.src = url
      frame.style.cssText = 'width:100%;flex:1;border:0'
      const origin = new URL(url).origin
      const cleanup = () => { host.remove(); removeEventListener('message', solved) }
      // The challenge hands its cookie to its SoundCloud parent, as DataDome's web tag does.
      // Keep all original attributes; never expose the cookie to the YouCloud page.
      const solved = (event) => {
        if (event.origin !== origin || event.source !== frame.contentWindow) return
        let data = event.data
        try { if (typeof data === 'string') data = JSON.parse(data) } catch { return }
        const cookie = data?.cookie
        if (typeof cookie !== 'string' || !/^datadome=[^;\r\n]+(?:;|$)/.test(cookie) || cookie.length > 4096) return
        const domain = /;\s*domain=([^;]+)/i.exec(cookie)?.[1]?.toLowerCase()
        if (domain && domain !== 'soundcloud.com' && domain !== '.soundcloud.com') return
        document.cookie = cookie
        cleanup()
      }
      addEventListener('message', solved)
      close.onclick = cleanup
      host.append(close, frame)
      document.documentElement.append(host)
      close.focus()
    },
  })
  await api.tabs.update(tabId, { active: true })
  return { value: true }
}

/** The site's other asks: who is signed in where, and a Yandex login. */
async function call(name, args) {
  if (name === 'sc-request') return soundCloudWrite(args)
  if (name === 'sc-captcha') return showSoundCloudChallenge(args)
  if (name === 'helper') return helperHello()
  if (name === 'sc-token') {
    return { value: await soundCloudCookie() }
  }
  if (name === 'sc-renew') {
    return { value: await renewSoundCloud(String(args?.stale ?? '')) }
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
  if (!auth?.startsWith('OAuth ')) return
  const token = auth.slice('OAuth '.length).trim()
  caughtSoundCloudToken(token)
  renewedSoundCloud(token)
}

async function soundCloudCookie() {
  const cookie = await api.cookies.get({ url: 'https://soundcloud.com/', name: 'oauth_token' })
  return cookie?.value ? decodeURIComponent(cookie.value) : null
}

const SOUNDCLOUD_SITE = 'https://soundcloud.com/discover'
const RENEW_TIMEOUT_MS = 25_000
let renewal = null
let renewWaiter = null

/**
 * SoundCloud's session renewed, as the app renews it (SoundCloudSessionRefresher). The token the
 * site signed in with is soundcloud.com's own and expires, and soundcloud.com renews it itself for
 * as long as its session in this browser lives. So a token other than the [stale] one is looked
 * for where soundcloud.com keeps it: its cookie, and, when that is the stale one too, soundcloud.com
 * opened in a background tab, muted — it renews the session, the token its own requests carry (or
 * the cookie it sets) is taken, and the tab is closed. Null: signed out there too. The site
 * checks the token before it keeps it.
 */
function renewSoundCloud(stale) {
  renewal ??= (async () => {
    const cookie = await soundCloudCookie()
    if (cookie && cookie !== stale) return cookie
    return new Promise((resolve) => {
      let tabId = null
      const waiter = {
        stale,
        done: (token) => {
          if (renewWaiter !== waiter) return
          renewWaiter = null
          clearTimeout(timer)
          if (tabId != null) api.tabs.remove(tabId).catch(() => {})
          resolve(token)
        },
      }
      renewWaiter = waiter
      const timer = setTimeout(() => waiter.done(null), RENEW_TIMEOUT_MS)
      api.tabs.create({ url: SOUNDCLOUD_SITE, active: false }).then(
        (tab) => {
          tabId = tab.id
          if (renewWaiter !== waiter) api.tabs.remove(tab.id).catch(() => {})
          else api.tabs.update(tab.id, { muted: true }).catch(() => {})
        },
        () => waiter.done(null),
      )
    })
  })().finally(() => (renewal = null))
  return renewal
}

function renewedSoundCloud(token) {
  if (renewWaiter && token && token !== renewWaiter.stale) renewWaiter.done(token)
}

api.cookies.onChanged.addListener(({ cookie, removed }) => {
  if (removed || !renewWaiter || cookie.name !== 'oauth_token') return
  if (!/(^|\.)soundcloud\.com$/.test(cookie.domain.replace(/^\./, ''))) return
  renewedSoundCloud(decodeURIComponent(cookie.value))
})

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
    call(message.name, message.args)
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
