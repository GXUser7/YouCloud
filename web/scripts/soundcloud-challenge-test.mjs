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
const api = {
  runtime: { id: 'test-extension', onMessage: noEvent, onInstalled: noEvent, onConnect: noEvent },
  declarativeNetRequest: { getSessionRules: async () => [], updateSessionRules: async () => {} },
  webRequest: { onBeforeSendHeaders: noEvent },
  cookies: { onChanged: noEvent },
  tabs: {
    onRemoved: noEvent,
    create: async (options) => { const tab = { id: 1, status: 'complete', ...options }; tabs.push(tab); return tab },
    get: async () => ({ id: 1, url: 'https://soundcloud.com/', status: 'complete' }),
    update: async (id, options) => { tabs.push({ id, ...options }) },
  },
  scripting: { executeScript: async (script) => { scripts.push(script); return [{ result: response }] } },
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

// Run the actual injected request function in a SoundCloud page double.
let sent
const pageContext = vm.createContext({
  location: { origin: 'https://soundcloud.com' },
  document: { cookie: 'datadome=proof%2Bvalue', querySelectorAll: () => [] },
  fetch: async (url, init) => { sent = { url, init }; return { status: 204, text: async () => '' } },
  AbortSignal,
})
pageContext.args = allowed
const pageWrite = vm.runInContext(`(${scripts[0].func.toString()})(args)`, pageContext)
assert.equal((await pageWrite).status, 204)
assert.equal(sent.init.credentials, 'include')
assert.equal(sent.init.headers['X-Datadome-ClientId'], 'proof+value')
pageContext.location.origin = 'https://evil.test'
await assert.rejects(vm.runInContext(`(${scripts[0].func.toString()})(args)`, pageContext))

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

console.log('SoundCloud challenge: allowlists, same-tab writes, cookie use, cancellation and explicit retry passed')
