// Runs the actual extension boundary and challenge state with browser/network doubles.
// No SoundCloud account, network request or CAPTCHA solving is involved.
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import ts from 'typescript'

const pendingTimer = () => 1
const noEvent = { addListener() {}, removeListener() {} }
const tabs = []
const scripts = []
let response = { status: 204, body: '' }
const session = {}
let pageTab = null
let nextTabId = 1
let pageReads = 0
let loadingReads = 0
let onMessage
const api = {
  storage: { session: { get: async () => ({ ...session }), set: async (value) => Object.assign(session, value) } },
  runtime: { id: 'test-extension', onMessage: { addListener: (listener) => onMessage = listener }, onInstalled: noEvent, onConnect: noEvent },
  declarativeNetRequest: { getSessionRules: async () => [], updateSessionRules: async () => {} },
  webRequest: { onBeforeSendHeaders: noEvent },
  cookies: { onChanged: noEvent },
  tabs: {
    onRemoved: noEvent,
    create: async (options) => { pageTab = { id: nextTabId++, status: 'complete', ...options }; tabs.push(pageTab); return pageTab },
    get: async (id) => {
      pageReads++
      if (!pageTab || pageTab.id !== id) throw new Error('No tab')
      if (loadingReads) return { ...pageTab, status: --loadingReads ? 'loading' : 'complete' }
      return { ...pageTab }
    },
    update: async (id, options) => { tabs.push({ id, ...options }) },
  },
  scripting: { executeScript: async (script) => { scripts.push(script); return [{ result: typeof script.args[0] === 'string' ? true : response }] } },
}
const context = vm.createContext({
  chrome: api, URL, TextEncoder, crypto: globalThis.crypto,
  setTimeout: (f, ms) => { if (ms <= 1500) queueMicrotask(f); return 1 }, clearTimeout() {},
  console,
})
vm.runInContext(readFileSync(new URL('../extension/background.js', import.meta.url), 'utf8'), context)
const evaluate = (code) => vm.runInContext(code, context)

const allowed = { url: 'https://api-v2.soundcloud.com/users/12/track_likes/34?client_id=test', method: 'PUT', token: 'test-session' }
context.args = allowed
assert.equal(evaluate('validateSoundCloudWrite(args).method'), 'PUT')
for (const bad of [
  { ...allowed, url: 'https://api-v2.soundcloud.com.evil.test/users/12/track_likes/34' },
  { ...allowed, url: 'https://user:pass@api-v2.soundcloud.com/users/12/track_likes/34' },
  { ...allowed, url: 'https://api-v2.soundcloud.com/me' },
  { ...allowed, method: 'GET' },
  { ...allowed, method: 'POST' },
  { ...allowed, token: 'bad\r\nsession' },
]) {
  context.args = bad
  assert.throws(() => evaluate('validateSoundCloudWrite(args)'))
}
context.args = { ...allowed, url: 'https://api-v2.soundcloud.com/me/followings/34', method: 'POST' }
assert.equal(evaluate('validateSoundCloudWrite(args).method'), 'POST')
context.args = allowed
const written = await evaluate('soundCloudWrite(args)')
assert.equal(written.value.status, 204)
assert.equal(tabs.filter((t) => t.url).length, 1)
assert.equal(scripts[0].world, 'MAIN')
assert.equal(scripts[0].args[0].token, 'test-session')

const captcha = 'https://geo.captcha-delivery.com/captcha/?cid=test'
response = { status: 403, body: JSON.stringify({ url: captcha }) }
await evaluate('soundCloudWrite(args)')
assert.equal(tabs.filter((t) => t.url).length, 1, 'Writes must reuse the same SoundCloud tab and cookie jar')
context.challengeArgs = { url: 'https://geo.captcha-delivery.com/captcha/?cid=other' }
await assert.rejects(evaluate('showSoundCloudChallenge(challengeArgs)'))
context.challengeArgs = { url: captcha }
await evaluate('showSoundCloudChallenge(challengeArgs)')
assert.equal(tabs.at(-1).active, true, 'The user must see the actual challenge tab')
assert.equal(scripts.at(-1).world, 'MAIN')

// Only the declared top-level YouCloud pages may invoke bridge calls.
for (const sender of [
  { id: 'test-extension', url: 'https://evil.test/' },
  { id: 'test-extension', url: 'https://youcloud.inkerror67.workers.dev.evil.test/' },
  { id: 'test-extension', url: 'http://localhost:5191/' },
  { id: 'test-extension', url: 'https://youcloud.inkerror67.workers.dev/', frameId: 1 },
  { id: 'test-extension' },
]) {
  let denied
  onMessage({ type: 'yc-call', name: 'probe' }, sender, (answer) => denied = answer)
  assert.equal(denied?.error, 'YouCloud page required')
}
for (const url of ['https://youcloud.inkerror67.workers.dev/#/liked', 'http://localhost:5190/']) {
  const answer = await new Promise((resolve) => onMessage({ type: 'yc-call', name: 'probe' }, { id: 'test-extension', url, frameId: 0 }, resolve))
  assert.equal(answer.error, 'unknown call: probe', 'trusted pages reach the bridge dispatcher')
}

// Run the actual injected request function in a SoundCloud page double.
let sent
// Simulate an MV3 worker sleeping while the listener reads the verification dialog.
assert.equal(session.scWriteTab, 1)
evaluate('soundCloudWriteTab = null; lastSoundCloudChallenge = null; soundCloudChallengePage = null')
await evaluate('showSoundCloudChallenge({url: ' + JSON.stringify(captcha) + '})')
assert.equal(scripts.at(-1).target.tabId, 1, 'resumed background must open the challenge in its owned tab')
assert.equal(tabs.filter(t => t.url).length, 1, 'resuming must not create another SoundCloud tab')

// A challenge from a closed or navigated page is rejected, not replayed in a new tab.
const createdBeforeClose = tabs.filter(t => t.url).length
pageTab = null
await assert.rejects(evaluate('showSoundCloudChallenge({url: ' + JSON.stringify(captcha) + '})'), /expired/)
assert.equal(tabs.filter(t => t.url).length, createdBeforeClose)
await evaluate('soundCloudWrite(args)')
assert.equal(session.scWriteTab, 2, 'only a new write creates a replacement page')
pageTab.url = 'https://elsewhere.test/'
await assert.rejects(evaluate('showSoundCloudChallenge({url: ' + JSON.stringify(captcha) + '})'), /expired/)
assert.equal(tabs.filter(t => t.url).length, createdBeforeClose + 1)
await evaluate('soundCloudWrite(args)')
assert.equal(session.scWriteTab, 3)

// Reused pages which are still loading must reach complete before injection.
loadingReads = 2
pageTab.status = 'loading'
const readsBeforeReady = pageReads
await evaluate('soundCloudPage(false)')
assert.ok(pageReads - readsBeforeReady >= 2)
pageTab.status = 'complete'

const pageContext = vm.createContext({
  location: { origin: 'https://soundcloud.com' },
  document: { cookie: 'datadome=proof%2Bvalue', querySelectorAll: () => [] },
  fetch: async (url, init) => { sent = { url, init }; return { status: 204, text: async () => '' } },
  AbortSignal,
})
pageContext.args = scripts[0].args[0]
const pageWrite = vm.runInContext(`(${scripts[0].func.toString()})(args)`, pageContext)
assert.equal((await pageWrite).status, 204)
assert.equal(sent.init.credentials, 'include')
assert.equal(sent.init.headers['X-Datadome-ClientId'], 'proof+value')
sent = null
pageContext.args = { ...scripts[0].args[0], expiresAt: Date.now() - 1 }
await assert.rejects(vm.runInContext(`(${scripts[0].func.toString()})(args)`, pageContext), /expired/)
assert.equal(sent, null, 'expired queued writes must never reach the API')
pageContext.args = scripts[0].args[0]
pageContext.location.origin = 'https://evil.test'
await assert.rejects(vm.runInContext(`(${scripts[0].func.toString()})(args)`, pageContext))

// Exercise the injected challenge's message boundary with a synthetic page/iframe.
const events = new Map()
let challengeFrame
let storedCookie = ''
const challengePage = {
  location: { origin: 'https://soundcloud.com' }, URL,
  document: {
    querySelectorAll: () => [], getElementById: () => null,
    createElement: (tag) => {
      const node = { style: {}, append() {}, remove() {}, focus() {} }
      if (tag === 'iframe') { node.contentWindow = {}; challengeFrame = node }
      return node
    },
    documentElement: { append() {} },
    set cookie(value) { storedCookie = value },
  },
  addEventListener: (name, listener) => events.set(name, listener),
  removeEventListener: (name, listener) => { if (events.get(name) === listener) events.delete(name) },
}
const challengeScript = scripts.find(s => s.args[0] === captcha)
challengePage.url = captcha
challengePage.page = challengeScript.args[1]
challengePage.__youcloudSoundCloudWritePage = challengePage.page
vm.runInNewContext(`(${challengeScript.func.toString()})(url, page)`, challengePage)
const deliver = (cookie, overrides = {}) => events.get('message')?.({ origin: 'https://geo.captcha-delivery.com', source: challengeFrame.contentWindow, data: { cookie }, ...overrides })
deliver('datadome=proof; Domain=.soundcloud.com; Secure', { origin: 'https://evil.test' })
deliver('datadome=proof; Domain=.soundcloud.com; Secure', { source: {} })
deliver('datadome=proof; Domain=.elsewhere.test; Secure')
deliver('datadome=proof; Domain=.soundcloud.com;\r\nmalformed')
assert.equal(storedCookie, '')
deliver('datadome=proof; Domain=.soundcloud.com; Secure')
assert.equal(storedCookie, 'datadome=proof; Domain=.soundcloud.com; Secure')
assert.equal(events.has('message'), false)

// A tab id and URL survive reloading SoundCloud; the original document marker does not.
delete challengePage.__youcloudSoundCloudWritePage
assert.equal(vm.runInNewContext(`(${challengeScript.func.toString()})(url, page)`, challengePage), false)

const exports = {}
const source = ts.transpileModule(readFileSync(new URL('../src/lib/soundcloudChallenge.svelte.ts', import.meta.url), 'utf8'), {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
}).outputText
vm.runInNewContext(source, {
  exports, require: (id) => id.includes('extension') ? { extensionCall: async () => true } : { tr: (s) => s },
  URL, $state: (s) => s, setTimeout: pendingTimer, clearTimeout() {},
})
assert.equal(exports.challengeUrl(403, JSON.stringify({ url: captcha })), captcha)
for (const url of ['http://geo.captcha-delivery.com/captcha/', 'https://captcha-delivery.com.evil.test/', 'https://u:p@geo.captcha-delivery.com/', 'https://soundcloud.com/']) {
  assert.equal(exports.challengeUrl(403, JSON.stringify({ url })), null)
}
assert.equal(exports.challengeUrl(200, JSON.stringify({ url: captcha })), null)
assert.equal(exports.challengeUrl(403, '<html>denied</html>'), null)
const first = exports.solveSoundCloud(captcha)
const second = exports.solveSoundCloud(captcha)
assert.equal(exports.soundcloudChallenge.open, true)
exports.finishSoundCloud(false)
assert.equal(await first, false)
assert.equal(await second, false)
assert.equal(exports.soundcloudChallenge.open, false)
const retry = exports.solveSoundCloud(captcha)
exports.finishSoundCloud(true)
assert.equal(await retry, true)

console.log('SoundCloud challenge: trusted senders, same-page recovery, stale/loading tabs, cookie messages, cancellation and retry passed')
