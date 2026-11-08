import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'
import vm from 'node:vm'

const source = await readFile(new URL('../src/lib/radioHistory.ts', import.meta.url), 'utf8')
const js = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 } }).outputText
const { radioHistory, rememberRadioTrack, radioQueue, freshRadioTracks, radioEndType } = await import(`data:text/javascript;base64,${Buffer.from(js).toString('base64')}`)
const now = Date.UTC(2026, 10, 8)
const day = 86_400_000
const id = (n) => `ya:t:${n}`
const entry = (n, at = now) => ({ id: id(n), key: `${n}:10`, at })

assert.deepEqual(radioHistory(null, now), [])
assert.deepEqual(radioHistory([null, {}, entry(1, now + 1), entry(2, now - 30 * day), { ...entry(3), key: '4:10' }], now), [])
let history = radioHistory([entry(1, now - day), entry(2, now - 2 * day), entry(1, now - 3 * day)], now)
assert.deepEqual(radioQueue(history, now), ['2:10', '1:10'])
history = rememberRadioTrack(history, id(2), '2:20', now)
assert.deepEqual(radioQueue(history, now), ['1:10', '2:20'])
assert.equal(radioHistory(Array.from({ length: 1005 }, (_, n) => entry(n, now - 1005 + n)), now).length, 1000)
assert.equal(radioHistory([entry(1, now - 29 * day)], now).length, 1)
assert.equal(radioHistory([entry(1, now - 31 * day)], now).length, 0)

let calls = 0
let queued = [id(3)]
const disliked = new Set([id(4)])
const exclusions = () => ({ history, queued, disliked })
const batch = (n, tracks) => ({ batchId: `b${n}`, tracks: tracks.map(id) })
let result = await freshRadioTracks(batch(1, [1, 2, 3, 4, 1]), async () => {
  calls++
  return batch(2, [2, 5, 5, 3, 4, 6])
}, exclusions, now)
assert.equal(calls, 1)
assert.deepEqual(result, { tracks: [id(5), id(6)], batchOf: { [id(5)]: 'b2', [id(6)]: 'b2' } })

calls = 0
result = await freshRadioTracks(null, async () => {
  calls++
  return batch(calls, [1, 2, 3, 4, 1])
}, exclusions, now)
assert.equal(calls, 4)
assert.deepEqual(result.tracks, [id(1), id(2)])
assert.deepEqual(result.batchOf, { [id(1)]: 'b1', [id(2)]: 'b1' })

calls = 0
result = await freshRadioTracks(batch(1, [1]), async () => {
  calls++
  queued = [id(1), id(2), id(3)]
  return batch(calls + 1, [1, 2, 3, 4])
}, exclusions, now)
assert.equal(calls, 3)
assert.deepEqual(result.tracks, [])

// A fetched/preloaded track is never remembered; only the explicit playback hook adds it.
assert(!radioQueue(history, now).includes('5:10'))
assert(radioQueue(rememberRadioTrack(history, id(5), '5:10', now), now).includes('5:10'))
assert.equal(radioEndType(true), 'trackFinished')
assert.equal(radioEndType(false), 'skip')
let liveNow = now
let liveHistory = []
result = await freshRadioTracks(null, async () => {
  liveNow++
  liveHistory = rememberRadioTrack(liveHistory, id(9), '9:90', liveNow)
  return batch(1, [9, 10])
}, () => ({ history: liveHistory, queued: [], disliked: new Set() }), () => liveNow)
assert.deepEqual(result.tracks, [id(10)])
// Exercise the actual service functions without importing accounts, storage or the bridge.
const service = await readFile(new URL('../src/lib/services/yandex.ts', import.meta.url), 'utf8')
const radio = service.slice(service.indexOf('export interface RadioBatch'), service.indexOf('// ---------------------------------------------------------------------------------- playback'))
const harness = `
const requests = [];
const tr = (text) => text;
const putYaTrack = (track) => 'ya:t:' + track.id;
const trackKey = (track) => track.ya.albumId ? track.ya.trackId + ':' + track.ya.albumId : track.ya.trackId;
const auth = () => ({ authorization: 'OAuth fake' });
const apiUrl = (path, params) => { const u = new URL(path, 'https://api.music.yandex.net/'); for (const [key, value] of Object.entries(params)) u.searchParams.set(key, value); return u.href; };
let answer = {};
async function yaPostJson(path, body) { requests.push({ path, body }); return answer; }
async function postJson(path, body, init) { requests.push({ path, body, init }); return { result: 'ok' }; }
export { requests };
export function respond(value) { answer = value; }
${radio}`
const serviceJs = ts.transpileModule(harness, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 } }).outputText
const api = await import(`data:text/javascript;base64,${Buffer.from(serviceJs).toString('base64')}`)
api.respond({ radioSessionId: 'session', batchId: 'first', sequence: [{ track: { id: 1 } }, { track: { id: 2, available: false } }] })
assert.deepEqual(await api.radioStart(['user:onyourwave'], ['8:80', '9:90']), { sessionId: 'session', batchId: 'first', tracks: [id(1)] })
assert.deepEqual(api.requests.at(-1), { path: 'rotor/session/new', body: { seeds: ['user:onyourwave'], queue: ['8:80', '9:90'], includeTracksInResponse: true } })
await api.radioMore('session', ['8:80', '9:90'])
assert.deepEqual(api.requests.at(-1), { path: 'rotor/session/session/tracks', body: { queue: ['8:80', '9:90'] } })
const timestamp = new Date(now).toISOString()
const track = { ya: { trackId: '1', albumId: '10' } }
await api.radioFeedback('session', 'first', 'skip', track, 7, timestamp)
assert.deepEqual(api.requests.at(-1), { path: 'rotor/session/session/feedback', body: { from: 'web-home-rup_main-radio-default', batchId: 'first', event: { type: 'skip', timestamp, trackId: '1:10', totalPlayedSeconds: 7 } } })
const play = { trackId: '1', albumId: '10', from: api.WAVE_FROM, playId: 'stable-play-id', timestamp, totalPlayedSeconds: 7, endPositionSeconds: 170, trackLengthSeconds: 180 }
await api.plays(play)
const request = api.requests.at(-1)
assert.equal(new URL(request.path).pathname, '/plays')
assert(Number.isFinite(Date.parse(new URL(request.path).searchParams.get('client-now'))))
assert.deepEqual(request.body, { plays: [{ ...play, fromCache: false }] })
assert.deepEqual(request.init.headers, { authorization: 'OAuth fake' })
// Run the real state hooks against a fake audio clock/transport, with no DOM or services.
const state = await readFile(new URL('../src/lib/state.svelte.ts', import.meta.url), 'utf8')
const hooks = state.slice(state.indexOf('let heard = radioHistory'), state.indexOf('function batchExclusions'))
let clock = 0
const sent = []
const storage = new Map()
const events = {}
const fakeAudio = { currentTime: 0, paused: false, ended: false, seeking: false, readyState: 4 }
const fakeTrack = { id: id(1), duration: 180, ya: { trackId: '1', albumId: '10' } }
const fakeRadio = { kind: 'ya', sessionId: 'original', uid: 1, batchOf: { [id(1)]: 'original-batch' } }
const fakeAccounts = { ya: { uid: 1 } }
const context = vm.createContext({
  radioHistory, rememberRadioTrack, stored: (key, fallback) => storage.get(key) ?? fallback,
  remember: (key, value) => storage.set(key, structuredClone(value)),
  accounts: fakeAccounts, live: { signedIn: () => !!fakeAccounts.ya },
  yandex: { WAVE_FROM: api.WAVE_FROM, yaKey: () => '1:10', plays: async (play) => sent.push({ kind: 'play', ...play }), radioFeedback: async (...args) => sent.push({ kind: 'feedback', args }) },
  crypto: { randomUUID: () => 'stable-id' }, performance: { now: () => clock }, Date,
  audio: fakeAudio, player: { radio: fakeRadio }, currentTrack: () => fakeTrack,
  addEventListener: (name, callback) => events[name] = callback,
  $effect: Object.assign(() => {}, { root: () => {} }),
  untrack: (fn) => fn(), setTimeout: () => 1, clearTimeout: () => {}, console,
  HttpError: class extends Error {},
})
const hooksJs = ts.transpileModule(hooks, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 } }).outputText
vm.runInContext(hooksJs, context)
await vm.runInContext('radioHeard(currentTrack()); flushReports()', context)
assert.equal(sent.filter((r) => r.kind === 'feedback').length, 1)
clock = 7000
fakeAudio.currentTime = 7
vm.runInContext('listenClock(); checkpointPlay(true)', context)
assert.equal(storage.get('radioActivePlay').played, 7)
// A long pause and seeking to 170s must not inflate listening time or finish the track.
fakeAudio.paused = true
vm.runInContext('listenClock()', context)
clock = 67000
fakeAudio.currentTime = 170
vm.runInContext('listenClock()', context)
context.player.radio = { ...fakeRadio, sessionId: 'replacement' }
await vm.runInContext("radioTold(currentTrack(), 'skip'); flushReports()", context)
const finalPlay = sent.filter((r) => r.kind === 'play').at(-1)
assert.equal(finalPlay.totalPlayedSeconds, 7)
assert.equal(finalPlay.endPositionSeconds, 170)
assert.equal(finalPlay.playId, 'stable-id')
assert.equal(sent.filter((r) => r.kind === 'feedback').at(-1).args[0], 'original')
assert.equal(storage.get('radioActivePlay'), null)
// Resumes/retries don't produce duplicate starts or duplicate history entries.
fakeAudio.paused = false
await vm.runInContext('radioHeard(currentTrack()); radioHeard(currentTrack()); flushReports()', context)
assert.equal(sent.filter((r) => r.kind === 'feedback' && r.args[2] === 'trackStarted').length, 2)
assert.equal(storage.get('radioHeard').length, 1)
// Another account cannot drain reports belonging to the outgoing listener.
fakeAccounts.ya = { uid: 2 }
await vm.runInContext("radioTold(currentTrack(), 'trackFinished'); flushReports()", context)
assert(storage.get('radioReports').length > 0)
fakeAccounts.ya = { uid: 1 }
await vm.runInContext('flushReports()', context)
assert.equal(storage.get('radioReports').length, 0)
const sendPlay = context.yandex.plays
context.yandex.plays = async () => { throw new Error('fake bridge offline') }
await vm.runInContext('radioHeard(currentTrack()); flushReports()', context)
assert(storage.get('radioReports').some((r) => r.kind === 'play'))
context.yandex.plays = sendPlay
await vm.runInContext('flushReports()', context)
assert.equal(storage.get('radioReports').length, 0)
clock += 2000
fakeAudio.currentTime = 172
await events.pagehide()
await vm.runInContext('flushReports()', context)
assert.equal(sent.filter((r) => r.kind === 'play').at(-1).totalPlayedSeconds, 2)
assert.equal(storage.get('radioActivePlay'), null)
console.log('Radio history, request contracts and playback hooks passed (expiry/cap, retries/fallback, deduplication, pause/seek timing, session attribution, outbox/account isolation, offline retry and pagehide).')
