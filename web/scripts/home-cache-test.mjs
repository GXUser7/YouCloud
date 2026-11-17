// Contract tests for scoped CatalogContext isolation, two-tier HomeSnapshot cache (L1 + L2 IDB),
// canonical hashing, key partitioning, partial-failure protection, corruption resilience, and logout invalidation.
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import ts from 'typescript'

function transpile(relPath, extra = '') {
  const src = readFileSync(new URL(relPath, import.meta.url), 'utf8').replaceAll('import.meta.env.DEV', 'false') + '\n' + extra
  return ts.transpileModule(src, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
  }).outputText
}

function makeStorage() {
  const map = new Map()
  return {
    getItem: (k) => (map.has(k) ? map.get(k) : null),
    setItem: (k, v) => map.set(k, String(v)),
    removeItem: (k) => map.delete(k),
    clear: () => map.clear(),
    _map: map,
  }
}

function makeFakeIdb({ failOpen = false, corruptKey = null } = {}) {
  const store = new Map()
  const makeReq = (fn) => {
    const req = { result: undefined, onsuccess: null, onerror: null, onupgradeneeded: null, onblocked: null }
    queueMicrotask(() => fn(req))
    return req
  }
  const api = {
    _store: store,
    open: () => {
      if (failOpen) throw new Error('IDB blocked')
      const req = { result: null, onsuccess: null, onerror: null, onupgradeneeded: null, onblocked: null }
      queueMicrotask(() => {
        const db = {
          objectStoreNames: { contains: () => true },
          createObjectStore: () => ({}),
          close: () => {},
          transaction: () => ({
            objectStore: () => ({
              get: (key) =>
                makeReq((r) => {
                  if (corruptKey && key === corruptKey) {
                    r.result = { key, snapshot: { schema: 1, bad: true } }
                  } else {
                    r.result = store.get(key)
                  }
                  r.onsuccess?.()
                }),
              put: (rec) =>
                makeReq((r) => {
                  store.set(rec.key, structuredClone(rec))
                  r.onsuccess?.()
                }),
              delete: (arg) =>
                makeReq((r) => {
                  if (typeof arg === 'string') store.delete(arg)
                  else if (arg && typeof arg === 'object' && 'lower' in arg) {
                    for (const k of [...store.keys()]) {
                      if (k >= arg.lower && k <= arg.upper) store.delete(k)
                    }
                  }
                  r.onsuccess?.()
                }),
            }),
          }),
        }
        req.result = db
        req.onupgradeneeded?.()
        req.onsuccess?.()
      })
      return req
    },
  }
  return api
}

function loadStack({ idb, scAuth = null, yaAuth = null, lang = 'ru' } = {}) {
  const localStorage = makeStorage()
  const accounts = { sc: scAuth, ya: yaAuth, yt: false, ytName: null }
  const i18nState = { lang }
  const tr = (s, ...args) => s.replace(/%s/g, () => String(args.shift() ?? ''))
  const markedLiked = []
  let extVersion = '0.9.0'
  const netStub = {
    HttpError: class HttpError extends Error {},
    at: (v, ...p) => p.reduce((acc, k) => (acc == null ? undefined : acc[k]), v),
    request: async () => new Response('{}'),
    getJson: async () => ({ result: {} }),
    getText: async () => '',
    postJson: async () => ({}),
    postForm: async () => ({ result: {} }),
  }

  const globals = {
    URL,
    URLSearchParams,
    Response,
    Headers,
    Request,
    localStorage,
    indexedDB: idb,
    IDBKeyRange: { bound: (lower, upper) => ({ lower, upper }) },
    setTimeout,
    clearTimeout,
    console,
    $state: (v) => v,
    $derived: (v) => v,
    $effect: () => {},
  }

  const cache = new Map()
  const runModule = (relPath, overrides = {}) => {
    if (cache.has(relPath)) return cache.get(relPath)
    const exp = {}
    const mod = { exports: exp }
    const ctx = { ...globals, exports: exp, module: mod }
    ctx.require = (id) => {
      if (id in overrides) return overrides[id]
      if (id.endsWith('/i18n.svelte') || id === './i18n.svelte') return { tr, i18n: i18nState, pluralWord: () => 'треков' }
      if (id.endsWith('/accounts.svelte') || id === './accounts.svelte') {
        return { accounts, scToken: () => accounts.sc?.token, yaToken: () => accounts.ya?.token, setSoundCloud: (a) => (accounts.sc = a) }
      }
      if (id.endsWith('/catalog') || id === './catalog') return catalogMod
      if (id.endsWith('/homeCache') || id === './homeCache') return homeCacheMod
      if (id.endsWith('/extension') || id === './extension') return { extension: async () => extVersion, extensionCall: async () => false, soundCloudWrites: () => true }
      if (id.endsWith('/net') || id === './net') return netStub
      if (id.endsWith('/md5') || id === './md5') return { md5: () => '0' }
      if (id.endsWith('/soundcloudChallenge.svelte')) return { challengeUrl: () => null, solveSoundCloud: async () => false, soundCloudSetup() {} }
      if (id.endsWith('/state.svelte') || id === './state.svelte') return { markLiked: (ids) => markedLiked.push(...ids) }
      if (id.endsWith('/mock') || id === './mock') return { tracksOfArtist: () => [], releasesOf: () => [], similarTo: () => [] }
      if (id === 'svelte') return { onDestroy: () => {}, untrack: (fn) => fn() }
      throw new Error(`unexpected require(${id}) from ${relPath}`)
    }
    vm.runInNewContext(transpile(relPath), ctx, { filename: relPath })
    cache.set(relPath, mod.exports)
    return mod.exports
  }

  const catalogMod = runModule('../src/lib/catalog.ts')
  const homeCacheMod = runModule('../src/lib/homeCache.ts')
  const scMod = runModule('../src/lib/services/soundcloud.ts')
  const yaMod = runModule('../src/lib/services/yandex.ts')
  const ytMod = runModule('../src/lib/services/youtube.ts')
  const liveMod = runModule('../src/lib/live.ts', {
    './services/soundcloud': scMod,
    './services/yandex': yaMod,
    './services/youtube': ytMod,
  })
  const homeMod = runModule('../src/lib/home.svelte.ts', {
    './live': liveMod,
  })

  return {
    localStorage,
    accounts,
    i18nState,
    markedLiked,
    netStub,
    setExt: (v) => (extVersion = v),
    catalogMod,
    homeCacheMod,
    scMod,
    yaMod,
    ytMod,
    liveMod,
    homeMod,
    runModule,
  }
}

// 1. Key partitioning & yc.yt.hint cold-start resolution
{
  const idb = makeFakeIdb()
  const env = loadStack({ idb, scAuth: { id: 101, token: 'secret-oauth' }, yaAuth: { uid: 202, token: 'secret-ya' } })
  const { homeCacheMod, accounts, localStorage } = env
  assert.equal(homeCacheMod.accountKeyFor('sc'), 'user:101')
  assert.equal(homeCacheMod.accountKeyFor('ya'), 'user:202')
  assert.equal(homeCacheMod.homeCacheKey('sc', 'user:101', 'ru'), '1:sc:user:101:ru')
  assert.equal(homeCacheMod.homeCacheKey('sc', 'user:101', 'en'), '1:sc:user:101:en')

  // Cold start yt hint before refreshYouTube resolves
  localStorage.setItem('yc.yt.hint', 'user:Alice')
  assert.equal(homeCacheMod.accountKeyFor('yt'), 'user:Alice', 'cold-start yt must read yc.yt.hint synchronously')
  accounts.yt = true
  accounts.ytName = 'Alice'
  homeCacheMod.markYtAccountResolved(homeCacheMod.liveYtAccountKey())
  assert.equal(homeCacheMod.accountKeyFor('yt'), 'user:Alice')
  accounts.yt = false
  accounts.ytName = null
  homeCacheMod.markYtAccountResolved(homeCacheMod.liveYtAccountKey())
  assert.equal(homeCacheMod.accountKeyFor('yt'), 'anon', 'after resolution, signed-out yt uses anon')
}

// 2. Canonical hash stability & exclusion of volatile fields (updatedAt, sc.auth)
{
  const idb = makeFakeIdb()
  const env = loadStack({ idb })
  const { catalogMod, homeCacheMod } = env
  const c1 = catalogMod.createCatalog()
  c1.putArtist({ id: 'sc:u:1', name: 'Artist 1', service: 'sc', color: '#111' })
  c1.putTrack({ id: 'sc:t:1', service: 'sc', title: 'Track 1', artists: [{ id: 'sc:u:1', name: 'Artist 1' }], duration: 120, color: '#222', sc: { transcodings: [], auth: 'volatile-token-1' } })
  c1.putSet({ id: 'sc:pl:1', kind: 'playlist', service: 'sc', title: 'Mix A', subtitle: 'Sub', color: '#333', tracks: [] })

  const c2 = catalogMod.createCatalog()
  c2.putArtist({ color: '#111', service: 'sc', name: 'Artist 1', id: 'sc:u:1' })
  c2.putTrack({ sc: { auth: 'volatile-token-2', transcodings: [] }, color: '#222', duration: 120, artists: [{ name: 'Artist 1', id: 'sc:u:1' }], title: 'Track 1', service: 'sc', id: 'sc:t:1' })
  c2.putSet({ tracks: [], color: '#333', subtitle: 'Sub', title: 'Mix A', service: 'sc', kind: 'playlist', id: 'sc:pl:1' })

  const s1 = homeCacheMod.buildHomeSnapshot({
    service: 'sc', accountKey: 'anon', lang: 'ru', signedIn: false,
    sections: [{ title: 'Row 1', sets: ['sc:pl:1'], tracks: ['sc:t:1'] }],
    lib: null, cat: c1, updatedAt: 1000,
  })
  const s2 = homeCacheMod.buildHomeSnapshot({
    service: 'sc', accountKey: 'anon', lang: 'ru', signedIn: false,
    sections: [{ title: 'Row 1', sets: ['sc:pl:1'], tracks: ['sc:t:1'] }],
    lib: null, cat: c2, updatedAt: 999999,
  })
  assert.equal(s1.hash, s2.hash, 'hash must ignore key order, updatedAt, and sc.auth')
  assert.equal(await homeCacheMod.writeHomeCacheIfChanged(s1), true)
  assert.equal(await homeCacheMod.writeHomeCacheIfChanged(s2), false, 'unchanged hash must not rewrite')

  // Changing a title must change hash
  c2.putSet({ id: 'sc:pl:1', kind: 'playlist', service: 'sc', title: 'Mix B', subtitle: 'Sub', color: '#333', tracks: [] })
  const s3 = homeCacheMod.buildHomeSnapshot({
    service: 'sc', accountKey: 'anon', lang: 'ru', signedIn: false,
    sections: [{ title: 'Row 1', sets: ['sc:pl:1'], tracks: ['sc:t:1'] }],
    lib: null, cat: c2,
  })
  assert.notEqual(s3.hash, s1.hash)
  assert.equal(await homeCacheMod.writeHomeCacheIfChanged(s3), true)
}

// 3. Entity graph extraction (up to 40 liked tracks) + putSet loaded preservation
{
  const idb = makeFakeIdb()
  const env = loadStack({ idb })
  const { catalogMod, homeCacheMod } = env
  const cat = catalogMod.createCatalog()
  const likedIds = []
  for (let i = 0; i < 55; i++) {
    const id = `ya:t:${i}`
    likedIds.push(id)
    cat.putTrack({ id, service: 'ya', title: `Liked ${i}`, artists: [{ id: 'ya:a:1', name: 'A' }], duration: 100, color: '#444', cover: `https://avatars.yandex.net/i${i}` })
  }
  cat.putArtist({ id: 'ya:a:1', name: 'A', service: 'ya', color: '#111' })
  cat.putSet({ id: 'ya:likes', kind: 'liked', service: 'ya', title: 'Мне нравится', subtitle: 'Яндекс', color: '#f00', tracks: likedIds, loaded: true })
  cat.putSet({ id: 'ya:al:9', kind: 'album', service: 'ya', title: 'Album 9', subtitle: 'A', color: '#0f0', tracks: [] })

  const snap = homeCacheMod.buildHomeSnapshot({
    service: 'ya', accountKey: 'user:1', lang: 'ru', signedIn: true,
    sections: [{ title: 'Новые релизы', sets: ['ya:al:9'] }],
    lib: { liked: 'ya:likes', likedIds, playlists: [], artists: ['ya:a:1'] },
    cat,
  })
  assert.equal(Object.keys(snap.tracks).length, 40, 'must cap liked tracks at 40 for LikedArt')
  assert.ok(snap.sets['ya:al:9'] && snap.sets['ya:likes'] && snap.artists['ya:a:1'])

  // Pre-existing loaded set in defaultCatalog must preserve its tracks when hydrated with an unloaded snapshot set
  catalogMod.defaultCatalog.putSet({ id: 'ya:al:9', kind: 'album', service: 'ya', title: 'Album 9', subtitle: 'A', color: '#0f0', tracks: ['ya:t:0'], loaded: true })
  homeCacheMod.hydrateCatalogFromSnapshot(snap, catalogMod.defaultCatalog)
  assert.deepEqual(catalogMod.defaultCatalog.set('ya:al:9').tracks, ['ya:t:0'])
  assert.equal(catalogMod.defaultCatalog.set('ya:al:9').loaded, true)
  assert.equal(catalogMod.defaultCatalog.track('ya:t:39').title, 'Liked 39')
}

// 4. Scoped CatalogContext isolation during R2 background revalidation & partial-failure protection
{
  const idb = makeFakeIdb()
  const env = loadStack({ idb, scAuth: { id: 77, token: 'sc-tok' } })
  const { catalogMod, homeCacheMod, homeMod, netStub, markedLiked } = env

  // Seed defaultCatalog with Version A
  catalogMod.putSet({ id: 'sc:sys:v1', kind: 'mix', service: 'sc', title: 'Version A Mix', subtitle: 'SoundCloud', color: '#111', tracks: [] })
  catalogMod.putSet({ id: 'sc:likes', kind: 'liked', service: 'sc', title: 'Нравится', subtitle: 'SoundCloud', color: '#f00', tracks: ['sc:t:1'], loaded: true })
  catalogMod.putTrack({ id: 'sc:t:1', service: 'sc', title: 'Track A', artists: [{ id: 'sc:u:1', name: 'U' }], duration: 60, color: '#111' })
  const snapA = homeCacheMod.buildHomeSnapshot({
    service: 'sc', accountKey: 'user:77', lang: 'ru', signedIn: true,
    sections: [{ title: 'Твои миксы', sets: ['sc:sys:v1'] }],
    lib: { liked: 'sc:likes', likedIds: ['sc:t:1'], playlists: [], artists: [] },
    cat: catalogMod.defaultCatalog,
  })
  await homeCacheMod.writeHomeCacheIfChanged(snapA)

  let failLib = false
  let homeCalls = 0
  env.localStorage.setItem('yc.sc.clientId', 'cid')
  netStub.request = async (url) => {
    const u = new URL(url)
    if (u.pathname.includes('mixed-selections')) {
      homeCalls++
      return Response.json({
        collection: [{
          tracking_feature_name: 'your-moods',
          items: { collection: [{ kind: 'system-playlist', urn: 'v2', title: 'Version B Mix', tracks: [] }] },
        }],
      })
    }
    if (u.pathname.endsWith('/me')) return Response.json({ id: 77, username: 'Me' })
    if (u.pathname.includes('track_likes')) {
      if (failLib) throw new Error('library failed')
      return Response.json({ collection: [{ track: { id: 2, title: 'Track B', duration: 90000, user: { id: 2, username: 'U2' } } }] })
    }
    if (u.pathname.includes('me/library/all')) return Response.json({ collection: [] })
    if (u.pathname.includes('followings')) return Response.json({ collection: [] })
    return Response.json({})
  }

  // 4a. Partial-failure protection: library fails -> complete snapshot A is NOT overwritten
  failLib = true
  await homeMod.revalidateHomeInBackground('sc', 'user:77', 'ru', snapA.hash)
  const afterPartial = await homeCacheMod.readHomeCache('sc', 'user:77', 'ru')
  assert.equal(afterPartial.hash, snapA.hash, 'partial failure must not overwrite complete snapshot')
  assert.equal(catalogMod.sets.get('sc:sys:v1').title, 'Version A Mix', 'defaultCatalog untouched')
  assert.equal(catalogMod.sets.has('sc:sys:v2'), false, 'Version B must not leak into defaultCatalog')
  assert.equal(markedLiked.length, 0, 'background revalidation must not call markLiked')

  // 4b. Full background success -> Version B saved, defaultCatalog & markLiked still untouched!
  failLib = false
  // Deduplication check: launch two concurrent revalidations
  const p1 = homeMod.revalidateHomeInBackground('sc', 'user:77', 'ru', snapA.hash)
  const p2 = homeMod.revalidateHomeInBackground('sc', 'user:77', 'ru', snapA.hash)
  assert.equal(p1, p2, 'concurrent revalidations for the same key must deduplicate')
  await p1
  assert.equal(homeCalls, 2)
  assert.equal(catalogMod.sets.has('sc:sys:v2'), false, 'defaultCatalog MUST remain isolated from Version B')
  assert.equal(markedLiked.length, 0, 'markLiked MUST NOT be called during R2')

  const afterB = await homeCacheMod.readHomeCache('sc', 'user:77', 'ru')
  assert.notEqual(afterB.hash, snapA.hash)
  assert.equal(afterB.sets['sc:sys:v2'].title, 'Version B Mix')
  assert.equal(afterB.tracks['sc:t:2'].title, 'Track B')
}

// 5. Pre-commit guards (lang/account/invalidation mid-flight) & Yandex extension guard
{
  const idb = makeFakeIdb()
  const env = loadStack({ idb })
  const { homeCacheMod, homeMod, i18nState, netStub, setExt } = env
  env.localStorage.setItem('yc.sc.clientId', 'cid')

  let releaseHome
  netStub.request = async (url) => {
    if (url.includes('mixed-selections')) {
      await new Promise((r) => (releaseHome = r))
      return Response.json({ collection: [{ title: 'Row', items: { collection: [{ kind: 'playlist', id: 99, title: 'Late', user: { id: 1, username: 'u' } }] } }] })
    }
    return Response.json({})
  }
  const bg = homeMod.revalidateHomeInBackground('sc', 'anon', 'ru', 'old-hash')
  // Wait a tick for request to pause at releaseHome
  await new Promise((r) => setTimeout(r, 10))
  i18nState.lang = 'en' // user switched language mid-flight
  releaseHome()
  await bg
  assert.equal(await homeCacheMod.readHomeCache('sc', 'anon', 'ru'), null, 'mid-flight lang change must abort commit')

  // Yandex without extension must abort silently without calling net
  setExt(null)
  let yaNetCalled = false
  netStub.getJson = async () => { yaNetCalled = true; return { result: {} } }
  await homeMod.revalidateHomeInBackground('ya', 'anon', 'ru', 'old-hash')
  assert.equal(yaNetCalled, false, 'ya background refresh without extension must skip network call')
}

// 6. IDB failure & corruption resilience + logout invalidation
{
  // 6a. Corrupt IDB record
  const corruptIdb = makeFakeIdb({ corruptKey: '1:sc:anon:ru' })
  const env1 = loadStack({ idb: corruptIdb })
  assert.equal(await env1.homeCacheMod.readHomeCache('sc', 'anon', 'ru'), null, 'corrupt snapshot must return null')

  // 6b. Blocked IDB degrades cleanly to L1-only
  const blockedIdb = makeFakeIdb({ failOpen: true })
  const env2 = loadStack({ idb: blockedIdb })
  const c = env2.catalogMod.createCatalog()
  c.putSet({ id: 'sc:pl:5', kind: 'playlist', service: 'sc', title: 'L1 Only', subtitle: '', color: '#555', tracks: [] })
  const snap = env2.homeCacheMod.buildHomeSnapshot({
    service: 'sc', accountKey: 'anon', lang: 'ru', signedIn: false,
    sections: [{ title: 'S', sets: ['sc:pl:5'] }], lib: null, cat: c,
  })
  assert.equal(await env2.homeCacheMod.writeHomeCacheIfChanged(snap), true)
  assert.equal(env2.homeCacheMod.peekHomeCacheL1('sc', 'anon', 'ru')?.sets['sc:pl:5'].title, 'L1 Only')

  // 6c. Logout invalidation clears only target service
  const idb = makeFakeIdb()
  const env3 = loadStack({ idb })
  const c3 = env3.catalogMod.createCatalog()
  c3.putSet({ id: 'sc:pl:1', kind: 'playlist', service: 'sc', title: 'SC', subtitle: '', color: '#111', tracks: [] })
  c3.putSet({ id: 'ya:al:1', kind: 'album', service: 'ya', title: 'YA', subtitle: '', color: '#222', tracks: [] })
  await env3.homeCacheMod.writeHomeCacheIfChanged(env3.homeCacheMod.buildHomeSnapshot({
    service: 'sc', accountKey: 'user:1', lang: 'ru', signedIn: true, sections: [{ title: 'S', sets: ['sc:pl:1'] }], lib: null, cat: c3,
  }))
  await env3.homeCacheMod.writeHomeCacheIfChanged(env3.homeCacheMod.buildHomeSnapshot({
    service: 'ya', accountKey: 'user:2', lang: 'ru', signedIn: true, sections: [{ title: 'Y', sets: ['ya:al:1'] }], lib: null, cat: c3,
  }))
  await env3.homeCacheMod.invalidateServiceHomeCache('sc')
  assert.equal(await env3.homeCacheMod.readHomeCache('sc', 'user:1', 'ru'), null, 'sc cache invalidated')
  assert.notEqual(await env3.homeCacheMod.readHomeCache('ya', 'user:2', 'ru'), null, 'ya cache preserved')
}

console.log('Home cache contract: partitioning, catalog isolation, hashing, partial-failure, IDB resilience and invalidation passed')
