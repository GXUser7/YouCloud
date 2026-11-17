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

function makeFakeIdb({ failOpen = false, corruptKey = null, corruptRecords = null, onGet = null, onOpen = null, onPut = null, failPutTx = false } = {}) {
  const store = new Map()
  const makeReq = (fn) => {
    const req = { result: undefined, onsuccess: null, onerror: null, onupgradeneeded: null, onblocked: null }
    queueMicrotask(() => fn(req))
    return req
  }
  let openCount = 0
  const api = {
    _store: store,
    open: () => {
      if (failOpen) throw new Error('IDB blocked')
      const callIndex = ++openCount
      const req = { result: null, onsuccess: null, onerror: null, onupgradeneeded: null, onblocked: null }
      const finishOpen = () => {
        const db = {
          objectStoreNames: { contains: () => true },
          createObjectStore: () => ({}),
          close: () => {},
          transaction: () => {
            const tx = {
              onabort: null,
              onerror: null,
              objectStore: () => ({
                get: (key) => {
                  const r = { result: undefined, onsuccess: null, onerror: null }
                  const finish = () => {
                    if (corruptRecords && corruptRecords.has(key)) {
                      r.result = { key, snapshot: corruptRecords.get(key) }
                    } else if (corruptKey && key === corruptKey) {
                      r.result = { key, snapshot: { schema: 1, bad: true } }
                    } else {
                      r.result = store.get(key)
                    }
                    r.onsuccess?.()
                  }
                  if (onGet) onGet(key, finish)
                  else queueMicrotask(finish)
                  return r
                },
                put: (rec) => {
                  const r = { result: undefined, onsuccess: null, onerror: null }
                  const finish = () => {
                    if (failPutTx) {
                      tx.onabort?.()
                      return
                    }
                    store.set(rec.key, structuredClone(rec))
                    r.onsuccess?.()
                  }
                  if (onPut) onPut(rec, finish)
                  else queueMicrotask(finish)
                  return r
                },
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
            }
            return tx
          },
        }
        req.result = db
        req.onupgradeneeded?.()
        req.onsuccess?.()
      }
      if (onOpen) onOpen(callIndex, finishOpen)
      else queueMicrotask(finishOpen)
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
    addEventListener: () => {},
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
        return {
          accounts,
          scToken: () => accounts.sc?.token,
          yaToken: () => accounts.ya?.token,
          setSoundCloud: (a) => (accounts.sc = a),
          setYandex: (a) => (accounts.ya = a),
        }
      }
      if (id.endsWith('/catalog') || id === './catalog') return catalogMod
      if (id.endsWith('/homeCache') || id === './homeCache') return homeCacheMod
      if (id.endsWith('/extension') || id === './extension') {
        return {
          extension: async () => extVersion,
          extensionCall: async () => false,
          soundCloudWrites: () => true,
          onHostEvent: () => () => {},
          onToken: () => () => {},
        }
      }
      if (id.endsWith('/net') || id === './net') return netStub
      if (id.endsWith('/md5') || id === './md5') return { md5: () => '0' }
      if (id.endsWith('/soundcloudChallenge.svelte')) return { challengeUrl: () => null, solveSoundCloud: async () => false, soundCloudSetup() {} }
      if (id.endsWith('/state.svelte') || id === './state.svelte') return { markLiked: (ids) => markedLiked.push(...ids) }
      if (id.endsWith('/toast.svelte') || id === './toast.svelte') return { reason: String, toast: () => {} }
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

// 3. Entity graph extraction (all >40 liked tracks) + putSet loaded preservation
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
  assert.equal(Object.keys(snap.tracks).length, 55, 'must serialize all liked tracks without 40-track truncation')
  assert.ok(snap.sets['ya:al:9'] && snap.sets['ya:likes'] && snap.artists['ya:a:1'])

  // Pre-existing loaded set in defaultCatalog must preserve its tracks when hydrated with an unloaded snapshot set
  catalogMod.defaultCatalog.putSet({ id: 'ya:al:9', kind: 'album', service: 'ya', title: 'Album 9', subtitle: 'A', color: '#0f0', tracks: ['ya:t:0'], loaded: true })
  homeCacheMod.hydrateCatalogFromSnapshot(snap, catalogMod.defaultCatalog)
  assert.deepEqual(catalogMod.defaultCatalog.set('ya:al:9').tracks, ['ya:t:0'])
  assert.equal(catalogMod.defaultCatalog.set('ya:al:9').loaded, true)
  for (let i = 0; i < 55; i++) {
    const t = catalogMod.defaultCatalog.track(`ya:t:${i}`)
    assert.ok(t, `liked track ${i} must hydrate cleanly without undefined entries`)
    assert.equal(t.title, `Liked ${i}`)
  }
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

// 7. Subtly corrupt IDB validation, idbPut tx abort speed, and boot fallback
{
  const validCat = loadStack({ idb: makeFakeIdb() }).catalogMod.createCatalog()
  validCat.putArtist({ id: 'sc:u:1', name: 'A', service: 'sc', color: '#111' })
  validCat.putTrack({ id: 'sc:t:1', service: 'sc', title: 'T', artists: [{ id: 'sc:u:1', name: 'A' }], duration: 60, color: '#111' })
  validCat.putSet({ id: 'sc:pl:1', kind: 'playlist', service: 'sc', title: 'P', subtitle: '', color: '#111', tracks: [] })
  validCat.putSet({ id: 'sc:likes', kind: 'liked', service: 'sc', title: 'Likes', subtitle: '', color: '#f00', tracks: ['sc:t:1'], loaded: true })
  const baseSnap = loadStack({ idb: makeFakeIdb() }).homeCacheMod.buildHomeSnapshot({
    service: 'sc', accountKey: 'anon', lang: 'ru', signedIn: false,
    sections: [{ title: 'Row', sets: ['sc:pl:1'] }], lib: null, cat: validCat,
  })
  const signedSnap = loadStack({ idb: makeFakeIdb() }).homeCacheMod.buildHomeSnapshot({
    service: 'sc', accountKey: 'user:1', lang: 'ru', signedIn: true,
    sections: [{ title: 'Row', sets: ['sc:pl:1'], tracks: ['sc:t:1'] }],
    lib: { liked: 'sc:likes', likedIds: ['sc:t:1'], playlists: ['sc:pl:1'], artists: ['sc:u:1'] },
    cat: validCat,
  })
  {
    const idbOk = makeFakeIdb()
    const envOk = loadStack({ idb: idbOk })
    assert.equal(await envOk.homeCacheMod.writeHomeCacheIfChanged(signedSnap), true)
    envOk.homeCacheMod.clearHomeCacheMemory()
    assert.equal((await envOk.homeCacheMod.readHomeCache('sc', 'user:1', 'ru'))?.hash, signedSnap.hash)
  }

  const corruptVariants = [
    ['null track entry', { ...baseSnap, tracks: { 'sc:t:1': null } }],
    ['array tracks', { ...baseSnap, tracks: [] }],
    ['array sets', { ...baseSnap, sets: [] }],
    ['null section item', { ...baseSnap, sections: [null] }],
    ['bad section sets', { ...baseSnap, sections: [{ title: 'Row', sets: 'bad' }] }],
    ['null section set ref', { ...baseSnap, sections: [{ title: 'Row', sets: [null] }] }],
    ['missing section set ref', { ...baseSnap, sections: [{ title: 'Row', sets: ['sc:pl:missing'] }] }],
    ['null section track ref', { ...baseSnap, sections: [{ title: 'Row', sets: [], tracks: [null] }] }],
    ['missing section track ref', { ...baseSnap, sections: [{ title: 'Row', sets: [], tracks: ['sc:t:missing'] }] }],
    ['track missing artists array', { ...baseSnap, tracks: { 'sc:t:1': { id: 'sc:t:1', service: 'sc', title: 'X' } } }],
    ['lib missing playlists/artists', { ...baseSnap, lib: { liked: 'sc:likes', likedIds: [] } }],
    ['lib missing liked set', { ...signedSnap, accountKey: 'anon', signedIn: false, lib: { liked: 'sc:likes:missing', likedIds: [], playlists: [], artists: [] } }],
    ['lib missing liked track', { ...signedSnap, accountKey: 'anon', signedIn: false, lib: { liked: 'sc:likes', likedIds: ['sc:t:missing'], playlists: [], artists: [] } }],
    ['lib missing playlist set', { ...signedSnap, accountKey: 'anon', signedIn: false, lib: { liked: 'sc:likes', likedIds: [], playlists: ['sc:pl:missing'], artists: [] } }],
    ['lib missing artist', { ...signedSnap, accountKey: 'anon', signedIn: false, lib: { liked: 'sc:likes', likedIds: [], playlists: [], artists: ['sc:u:missing'] } }],
  ]
  for (const [label, payload] of corruptVariants) {
    const key = '1:sc:anon:ru'
    const idb = makeFakeIdb({ corruptRecords: new Map([[key, payload]]) })
    const env = loadStack({ idb })
    assert.equal(await env.homeCacheMod.readHomeCache('sc', 'anon', 'ru'), null, `corrupt variant (${label}) must be rejected`)
  }

  // idbPut tx.onabort resolves immediately instead of waiting 1500ms timeout
  const abortIdb = makeFakeIdb({ failPutTx: true })
  const abortEnv = loadStack({ idb: abortIdb })
  const t0 = Date.now()
  await abortEnv.homeCacheMod.writeHomeCacheIfChanged(baseSnap)
  assert.ok(Date.now() - t0 < 500, 'idbPut tx abort must resolve immediately without 1500ms timeout')
}

// 8. Generation races: writeHomeCacheIfChanged mid-idbGet & inFlightBg across generation bumps
{
  let releaseGet
  const idb = makeFakeIdb({
    onGet: (key, finish) => {
      if (key === '1:sc:user:1:ru') releaseGet = finish
      else queueMicrotask(finish)
    },
  })
  const env = loadStack({ idb, scAuth: { id: 1, token: 'tok-1' } })
  const cat = env.catalogMod.createCatalog()
  cat.putSet({ id: 'sc:pl:1', kind: 'playlist', service: 'sc', title: 'Race', subtitle: '', color: '#111', tracks: [] })
  const snap = env.homeCacheMod.buildHomeSnapshot({
    service: 'sc', accountKey: 'user:1', lang: 'ru', signedIn: true,
    sections: [{ title: 'S', sets: ['sc:pl:1'] }], lib: null, cat,
  })

  const writePromise = env.homeCacheMod.writeHomeCacheIfChanged(snap)
  await new Promise((r) => setTimeout(r, 10))
  assert.ok(releaseGet, 'writeHomeCacheIfChanged must be awaiting idbGet')
  await env.homeCacheMod.invalidateServiceHomeCache('sc')
  releaseGet()
  assert.equal(await writePromise, false, 'writeHomeCacheIfChanged must abort when generation bumps mid-idbGet')
  assert.equal(env.homeCacheMod.peekHomeCacheL1('sc', 'user:1', 'ru'), null, 'L1 must not resurrect invalidated snapshot')
  assert.equal(idb._store.has('1:sc:user:1:ru'), false, 'IDB must not resurrect invalidated snapshot')

  // idbPut-paused (indexedDB.open) race vs invalidateServiceHomeCache
  {
    let releasePutOpen
    const idbPutOpen = makeFakeIdb({
      onOpen: (callIndex, finish) => {
        // Call 1 = idbGet inside writeHomeCacheIfChanged, Call 2 = idbPut's openCacheDb()
        if (callIndex === 2) releasePutOpen = finish
        else queueMicrotask(finish)
      },
    })
    const envOpen = loadStack({ idb: idbPutOpen, scAuth: { id: 101, token: 'tok-101' } })
    const catOpen = envOpen.catalogMod.createCatalog()
    catOpen.putSet({ id: 'sc:pl:101', kind: 'playlist', service: 'sc', title: 'Secret 101', subtitle: '', color: '#111', tracks: [] })
    const snapOpen = envOpen.homeCacheMod.buildHomeSnapshot({
      service: 'sc', accountKey: 'user:101', lang: 'ru', signedIn: true,
      sections: [{ title: 'S', sets: ['sc:pl:101'] }], lib: null, cat: catOpen,
    })

    const writeP = envOpen.homeCacheMod.writeHomeCacheIfChanged(snapOpen)
    await new Promise((r) => setTimeout(r, 10))
    assert.ok(releasePutOpen, 'writeHomeCacheIfChanged must be awaiting openCacheDb inside idbPut')
    await envOpen.homeCacheMod.invalidateServiceHomeCache('sc')
    releasePutOpen()
    const wrote = await writeP
    assert.equal(wrote, false, 'idbPut(open)-paused write must return false after invalidation')
    assert.equal(idbPutOpen._store.has('1:sc:user:101:ru'), false, 'idbPut(open)-paused write must not resurrect in IDB')
    envOpen.homeCacheMod.clearHomeCacheMemory()
    assert.equal(await envOpen.homeCacheMod.readHomeCache('sc', 'user:101', 'ru'), null, 'readHomeCache must return null after idbPut(open) race')
  }

  // idbPut-paused (objectStore.put) race vs invalidateServiceHomeCache
  {
    let releasePut
    const idbPutStore = makeFakeIdb({
      onPut: (rec, finish) => {
        if (rec.key === '1:sc:user:101:ru') releasePut = finish
        else queueMicrotask(finish)
      },
    })
    const envPut = loadStack({ idb: idbPutStore, scAuth: { id: 101, token: 'tok-101' } })
    const catPut = envPut.catalogMod.createCatalog()
    catPut.putSet({ id: 'sc:pl:101', kind: 'playlist', service: 'sc', title: 'Secret 101', subtitle: '', color: '#111', tracks: [] })
    const snapPut = envPut.homeCacheMod.buildHomeSnapshot({
      service: 'sc', accountKey: 'user:101', lang: 'ru', signedIn: true,
      sections: [{ title: 'S', sets: ['sc:pl:101'] }], lib: null, cat: catPut,
    })

    const writeP = envPut.homeCacheMod.writeHomeCacheIfChanged(snapPut)
    await new Promise((r) => setTimeout(r, 10))
    assert.ok(releasePut, 'writeHomeCacheIfChanged must be awaiting objectStore.put inside idbPut')
    await envPut.homeCacheMod.invalidateServiceHomeCache('sc')
    releasePut()
    const wrote = await writeP
    assert.equal(wrote, false, 'idbPut(store.put)-paused write must return false after invalidation')
    assert.equal(envPut.homeCacheMod.peekHomeCacheL1('sc', 'user:101', 'ru'), null, 'L1 must remain empty after idbPut(store.put) race')
    assert.equal(idbPutStore._store.has('1:sc:user:101:ru'), false, 'IDB must not resurrect after idbPut(store.put) race')
    envPut.homeCacheMod.clearHomeCacheMemory()
    assert.equal(await envPut.homeCacheMod.readHomeCache('sc', 'user:101', 'ru'), null, 'readHomeCache must return null after idbPut(store.put) race')
  }

  // inFlightBg generation-keyed deduplication
  const idb2 = makeFakeIdb()
  const env2 = loadStack({ idb: idb2 })
  env2.localStorage.setItem('yc.sc.clientId', 'cid')
  let releaseBg1
  let bgCalls = 0
  env2.netStub.request = async (url) => {
    if (url.includes('mixed-selections')) {
      bgCalls++
      if (bgCalls === 1) {
        await new Promise((r) => (releaseBg1 = r))
        return Response.json({ collection: [{ title: 'Old', items: { collection: [{ kind: 'system-playlist', urn: 'old', title: 'Old', tracks: [] }] } }] })
      }
      return Response.json({ collection: [{ title: 'New', items: { collection: [{ kind: 'system-playlist', urn: 'new', title: 'New', tracks: [] }] } }] })
    }
    return Response.json({})
  }
  const pOld = env2.homeMod.revalidateHomeInBackground('sc', 'anon', 'ru', 'h0')
  await new Promise((r) => setTimeout(r, 10))
  await env2.homeCacheMod.invalidateServiceHomeCache('sc')
  const pNew = env2.homeMod.revalidateHomeInBackground('sc', 'anon', 'ru', 'h0')
  assert.notEqual(pOld, pNew, 'post-invalidation revalidation must not join dying pre-invalidation inFlightBg promise')
  await pNew
  releaseBg1()
  await pOld
  assert.equal(bgCalls, 2)
  const fresh = await env2.homeCacheMod.readHomeCache('sc', 'anon', 'ru')
  assert.equal(fresh?.sets['sc:sys:new']?.title, 'New', 'post-invalidation revalidation must persist fresh snapshot')
}

// 9. SoundCloud account switch (User 101 -> disconnect('sc') -> User 202) & myIdCache isolation
{
  const idb = makeFakeIdb()
  const env = loadStack({ idb })
  env.localStorage.setItem('yc.sc.clientId', 'cid')
  const loginMod = env.runModule('../src/lib/login.ts', {
    './services/soundcloud': env.scMod,
    './services/yandex': env.yaMod,
    './services/youtube': env.ytMod,
  })
  const requested = []
  env.netStub.request = async (url, init = {}) => {
    const u = new URL(url)
    const auth = init.headers?.authorization ?? ''
    requested.push({ path: u.pathname, auth })
    if (u.pathname.endsWith('/me')) {
      if (auth === 'OAuth tok-101') return Response.json({ id: 101, username: 'User101', avatar_url: null })
      if (auth === 'OAuth tok-202') return Response.json({ id: 202, username: 'User202', avatar_url: null })
    }
    if (u.pathname === '/users/101/track_likes') {
      return Response.json({ collection: [{ track: { id: 1010, title: 'T101', duration: 60000, user: { id: 101, username: 'U101' } } }] })
    }
    if (u.pathname === '/users/101/followings') {
      return Response.json({ collection: [{ id: 1011, username: 'A101' }] })
    }
    if (u.pathname === '/users/202/track_likes') {
      return Response.json({ collection: [{ track: { id: 2020, title: 'T202', duration: 60000, user: { id: 202, username: 'U202' } } }] })
    }
    if (u.pathname === '/users/202/followings') {
      return Response.json({ collection: [{ id: 2021, username: 'A202' }] })
    }
    if (u.pathname.includes('me/library/all')) return Response.json({ collection: [] })
    return Response.json({})
  }

  await loginMod.connectSoundCloud('tok-101')
  const lib101 = await env.liveMod.library('sc', env.catalogMod.createCatalog(), { strict: true })
  assert.deepEqual(Array.from(lib101.likedIds), ['sc:t:1010'])

  loginMod.disconnect('sc')
  await loginMod.connectSoundCloud('tok-202')
  const cat202 = env.catalogMod.createCatalog()
  const lib202 = await env.liveMod.library('sc', cat202, { strict: true })
  assert.deepEqual(Array.from(lib202.likedIds), ['sc:t:2020'], 'User 202 library must contain only User 202 likes')
  assert.deepEqual(Array.from(lib202.artists), ['sc:u:2021'], 'User 202 library must contain only User 202 followings')
  assert.ok(requested.some((r) => r.path === '/users/202/track_likes' && r.auth === 'OAuth tok-202'))
  assert.ok(requested.some((r) => r.path === '/users/202/followings' && r.auth === 'OAuth tok-202'))
  assert.equal(requested.some((r) => r.path.includes('/users/101/') && r.auth === 'OAuth tok-202'), false, 'must never fetch User 101 endpoints with User 202 token')

  const snap202 = env.homeCacheMod.buildHomeSnapshot({
    service: 'sc', accountKey: env.homeCacheMod.accountKeyFor('sc'), lang: 'ru', signedIn: true,
    sections: [], lib: lib202, cat: cat202,
  })
  await env.homeCacheMod.writeHomeCacheIfChanged(snap202)
  const cached202 = await env.homeCacheMod.readHomeCache('sc', 'user:202', 'ru')
  assert.deepEqual(Array.from(cached202?.lib?.likedIds ?? []), ['sc:t:2020'])
}

// 10. Partial library sub-request 500 failure must preserve complete cached playlists & artists
{
  const idb = makeFakeIdb()
  const env = loadStack({ idb, scAuth: { id: 77, token: 'sc-tok' }, yaAuth: { uid: 88, token: 'ya-tok' } })
  env.localStorage.setItem('yc.sc.clientId', 'cid')
  const cat = env.catalogMod.createCatalog()
  cat.putSet({ id: 'sc:sys:m1', kind: 'mix', service: 'sc', title: 'Mix 1', subtitle: '', color: '#111', tracks: [] })
  cat.putSet({ id: 'sc:pl:55', kind: 'playlist', service: 'sc', title: 'Saved PL', subtitle: '', color: '#222', tracks: [] })
  cat.putArtist({ id: 'sc:u:66', name: 'Saved Artist', service: 'sc', color: '#333' })
  cat.putSet({ id: 'sc:likes', kind: 'liked', service: 'sc', title: 'Likes', subtitle: '', color: '#f00', tracks: ['sc:t:1'], loaded: true })
  cat.putTrack({ id: 'sc:t:1', service: 'sc', title: 'T1', artists: [{ id: 'sc:u:66', name: 'Saved Artist' }], duration: 60, color: '#111' })
  const completeSc = env.homeCacheMod.buildHomeSnapshot({
    service: 'sc', accountKey: 'user:77', lang: 'ru', signedIn: true,
    sections: [{ title: 'Mix', sets: ['sc:sys:m1'] }],
    lib: { liked: 'sc:likes', likedIds: ['sc:t:1'], playlists: ['sc:pl:55'], artists: ['sc:u:66'] },
    cat,
  })
  await env.homeCacheMod.writeHomeCacheIfChanged(completeSc)

  env.netStub.request = async (url) => {
    const u = new URL(url)
    if (u.pathname.includes('mixed-selections')) {
      return Response.json({ collection: [{ title: 'Mix', items: { collection: [{ kind: 'system-playlist', urn: 'm2', title: 'Mix 2', tracks: [] }] } }] })
    }
    if (u.pathname.includes('track_likes')) {
      return Response.json({ collection: [{ track: { id: 1, title: 'T1', duration: 60000, user: { id: 66, username: 'Saved Artist' } } }] })
    }
    if (u.pathname.includes('me/library/all') || u.pathname.includes('followings')) {
      return new Response('server error', { status: 500 })
    }
    return Response.json({})
  }

  await env.homeMod.revalidateHomeInBackground('sc', 'user:77', 'ru', completeSc.hash)
  const afterSc = await env.homeCacheMod.readHomeCache('sc', 'user:77', 'ru')
  assert.deepEqual(Array.from(afterSc?.lib?.playlists ?? []), ['sc:pl:55'], 'partial 500 on me/library/all must preserve cached playlists')
  assert.deepEqual(Array.from(afterSc?.lib?.artists ?? []), ['sc:u:66'], 'partial 500 on followings must preserve cached artists')

  // Foreground non-strict library still returns partial likes while flagging onPartial
  let sawPartial = false
  const partialFg = await env.liveMod.library('sc', env.catalogMod.createCatalog(), { onPartial: () => (sawPartial = true) })
  assert.equal(sawPartial, true)
  assert.deepEqual(Array.from(partialFg.likedIds), ['sc:t:1'])

  // Yandex partial 500 protection in strict mode
  env.netStub.getJson = async (url) => {
    if (url.includes('/likes/tracks')) return { result: { library: { tracks: [{ id: '1' }] } } }
    if (url.includes('/playlists/list')) throw new Error('500 playlists')
    return { result: [] }
  }
  env.netStub.postForm = async (url) => {
    if (url.includes('/tracks')) return { result: [{ id: 1, title: 'Y1', durationMs: 60000, artists: [{ id: 2, name: 'YA' }] }] }
    return { result: {} }
  }
  await assert.rejects(env.liveMod.library('ya', env.catalogMod.createCatalog(), { strict: true }), /500 playlists/)
}

console.log('Home cache contract: partitioning, catalog isolation, hashing, partial-failure, IDB resilience and invalidation passed')
