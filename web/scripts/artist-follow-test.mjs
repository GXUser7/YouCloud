// Exercise the real service modules with network/InnerTube doubles. No account or network used.
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import ts from 'typescript'

const accounts = { sc: { id: 7, token: 'sc-test' }, ya: { uid: 8, token: 'ya-test' }, yt: true, ytName: 'Test account' }
const calls = []
const catalog = { artists: new Map(), tracks: new Map(), sets: new Map(), colorFor: () => '#888', compact: String, countWords: () => '', putArtist: (a) => (catalog.artists.set(a.id, a), a.id), putTrack: (t) => (catalog.tracks.set(t.id, t), t.id), putSet: (s) => (catalog.sets.set(s.id, s), s.id) }
const tr = (s, ...args) => s.replace(/%s/g, () => String(args.shift()))
class HttpError extends Error {}
const net = { HttpError }
const extension = { extension: async () => '0.9.0', soundCloudWrites: () => true, extensionCall: async (name, timeout, args) => {
  assert.equal(name, 'sc-request')
  calls.push({ url: new URL(args.url), method: args.method })
  return { status: 204, body: '' }
} }

function load(name, extra = {}) {
  const source = readFileSync(new URL(`../src/lib/services/${name}.ts`, import.meta.url), 'utf8').replaceAll('import.meta.env.DEV', 'false')
  const js = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText
  const module = { exports: {} }
  const imports = {
    '../i18n.svelte': { tr, i18n: { lang: 'ru' } },
    '../accounts.svelte': { accounts, scToken: () => accounts.sc?.token, yaToken: () => accounts.ya?.token, setSoundCloud: (a) => accounts.sc = a },
    '../catalog': catalog, '../net': net, '../extension': extension, '../md5': {},
    '../soundcloudChallenge.svelte': { challengeUrl: () => null, solveSoundCloud: async () => false, soundCloudSetup() {} }, ...extra,
  }
  vm.runInNewContext(js, { exports: module.exports, module, require: (id) => {
    assert.ok(id in imports, `unexpected dependency ${id}`)
    return imports[id]
  }, URL, URLSearchParams, Response, Headers, Request, localStorage: { getItem: () => 'test-client', setItem() {} }, setTimeout, clearTimeout, console }, { filename: name + '.ts' })
  return module.exports
}

let scPage = 0
net.request = async (url, init) => {
  calls.push({ url: new URL(url), ...init })
  if (init.method !== 'GET') return new Response(null, { status: 204 })
  scPage++
  return Response.json(scPage === 1 ? { collection: [12], next_href: 'https://api-v2.soundcloud.com/me/followings/ids?offset=1' } : { collection: [42] })
}
const sc = load('soundcloud')
assert.equal(await sc.artistFollowing('sc:u:42'), true, 'SC follows must look through paginated ids')
assert.equal(scPage, 2)
await sc.setArtistFollowing('sc:u:42', true)
assert.equal(calls.at(-1).method, 'POST')
assert.equal(calls.at(-1).url.pathname, '/me/followings/42')
await sc.setArtistFollowing('sc:u:42', false)
assert.equal(calls.at(-1).method, 'DELETE')

net.getJson = async (url, init) => {
  calls.push({ url: new URL(url), ...init })
  return { result: [{ artist: { id: '42' } }] }
}
net.postForm = async (url, body, init) => {
  calls.push({ url: new URL(url), body, ...init })
  return { result: {} }
}
const ya = load('yandex')
assert.equal(await ya.artistFollowing('ya:a:42'), true)
await ya.setArtistFollowing('ya:a:42', true)
assert.equal(calls.at(-1).url.pathname, '/users/8/likes/artists/add-multiple')
assert.equal(calls.at(-1).body['artist-ids'], '42')
accounts.ya = { uid: 99, token: 'ya-other' }
await ya.setArtistFollowing('ya:a:42', false)
assert.equal(calls.at(-1).url.pathname, '/users/99/likes/artists/remove', 'account switch must use the new uid')
assert.equal(calls.at(-1).headers.authorization, 'OAuth ya-other')

let ytLoads = 0
let failMutation = false
const client = {
  music: { getArtist: async () => {
    ytLoads++
    return { header: { title: 'Artist', subscription_button: { subscribed: false, on_subscribe_endpoints: [{ name: 'subscribeEndpoint', payload: { params: 'artist-custom%3D' } }] } }, sections: [{ contents: [{ item_type: 'song', video_id: '123456789ab', title: 'Song' }] }] }
  } },
  actions: { execute: async (path, body) => {
    calls.push({ path, body })
    if (failMutation) throw new Error('server refused')
    return {}
  } },
}
const yt = load('youtube', { 'youtubei.js/web': { Platform: { shim: {} }, UniversalCache: class {}, Innertube: { create: async () => client } } })
assert.equal(await yt.artistFollowing('yt:a:UCtest'), false)
await yt.setArtistFollowing('yt:a:UCtest', true)
assert.equal(calls.at(-1).path, '/subscription/subscribe')
assert.equal(calls.at(-1).body.params, 'artist-custom=')
assert.equal(calls.at(-1).body.channelIds[0], 'UCtest')
assert.equal(await yt.artistFollowing('yt:a:UCtest'), true)
failMutation = true
await assert.rejects(yt.setArtistFollowing('yt:a:UCtest', false), /server refused/)
assert.equal(await yt.artistFollowing('yt:a:UCtest'), true, 'a rejected mutation must preserve known subscription')
accounts.ytName = 'Other account'
assert.equal(await yt.artistFollowing('yt:a:UCtest'), false)
assert.equal(ytLoads, 2, 'account switch must reread server state')
accounts.yt = false
await assert.rejects(yt.setArtistFollowing('yt:a:UCtest', true), /Настройках/)

console.log('Artist follow: pagination, service endpoints, account switches and rejected mutations passed')
