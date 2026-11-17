// Headless Chromium Playwright E2E tests for Home page caching (R1) and silent background revalidation (R2)
// across all 3 web main pages (SoundCloud, YouTube Music, Yandex Music) plus a production bundle smoke check.
import assert from 'node:assert/strict'
import { createServer as createHttpServer } from 'node:http'
import { existsSync, readFileSync } from 'node:fs'
import { extname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright-core'
import { svelte } from '@sveltejs/vite-plugin-svelte'
import { createServer } from 'vite'

const rootDir = fileURLToPath(new URL('..', import.meta.url))
const distClientDir = resolve(rootDir, 'dist/client')

function deferred() {
  let resolveFn
  let rejectFn
  const promise = new Promise((res, rej) => {
    resolveFn = res
    rejectFn = rej
  })
  return { promise, resolve: resolveFn, reject: rejectFn }
}

function scPayload(ver) {
  return {
    collection: [
      {
        tracking_feature_name: 'your-moods',
        items: {
          collection: [
            { kind: 'system-playlist', urn: `sc-mix-${ver}-1`, title: `SC Mix ${ver}1`, short_description: `Mix ${ver}`, tracks: [] },
            { kind: 'system-playlist', urn: `sc-mix-${ver}-2`, title: `SC Mix ${ver}2`, short_description: `Mix ${ver}`, tracks: [] },
          ],
        },
      },
      {
        tracking_feature_name: 'stations',
        items: {
          collection: [
            { kind: 'system-playlist', urn: `sc-st-${ver}-1`, title: `SC Station ${ver}1`, short_description: `Station ${ver}`, tracks: [] },
          ],
        },
      },
    ],
  }
}

function ytBrowsePayload(ver) {
  const videoId = ver === 'A' ? 'ytSongA1234' : 'ytSongB5678'
  const albumId = ver === 'A' ? 'MPREytAlbumA' : 'MPREytAlbumB'
  const album2Id = ver === 'A' ? 'MPREytMoreA' : 'MPREytMoreB'
  return {
    contents: {
      singleColumnBrowseResultsRenderer: {
        tabs: [
          {
            tabRenderer: {
              selected: true,
              content: {
                sectionListRenderer: {
                  contents: [
                    {
                      musicCarouselShelfRenderer: {
                        header: { musicCarouselShelfBasicHeaderRenderer: { title: { runs: [{ text: `YT Shelf ${ver}` }] } } },
                        contents: [
                          {
                            musicTwoRowItemRenderer: {
                              title: { runs: [{ text: `YT Album ${ver}` }], accessibility: { accessibilityData: { label: `YT Album ${ver}` } } },
                              subtitle: { runs: [{ text: `YT Artist ${ver}` }] },
                              navigationEndpoint: {
                                browseEndpoint: {
                                  browseId: albumId,
                                  browseEndpointContextSupportedConfigs: { browseEndpointContextMusicConfig: { pageType: 'MUSIC_PAGE_TYPE_ALBUM' } },
                                },
                              },
                              thumbnailRenderer: {
                                musicThumbnailRenderer: { thumbnail: { thumbnails: [{ url: `https://lh3.googleusercontent.com/yt${ver}=w544-h544`, width: 544, height: 544 }] } },
                              },
                            },
                          },
                        ],
                      },
                    },
                    {
                      musicCarouselShelfRenderer: {
                        header: { musicCarouselShelfBasicHeaderRenderer: { title: { runs: [{ text: 'Quick picks' }] } } },
                        contents: [
                          {
                            musicResponsiveListItemRenderer: {
                              flexColumns: [
                                {
                                  musicResponsiveListItemFlexColumnRenderer: {
                                    text: {
                                      runs: [
                                        {
                                          text: `YT Song ${ver}`,
                                          navigationEndpoint: {
                                            watchEndpoint: {
                                              videoId,
                                              watchEndpointMusicSupportedConfigs: { watchEndpointMusicConfig: { musicVideoType: 'MUSIC_VIDEO_TYPE_ATV' } },
                                            },
                                          },
                                        },
                                      ],
                                    },
                                  },
                                },
                                { musicResponsiveListItemFlexColumnRenderer: { text: { runs: [{ text: `YT Singer ${ver}` }] } } },
                              ],
                              playlistItemData: { videoId },
                              thumbnail: {
                                musicThumbnailRenderer: { thumbnail: { thumbnails: [{ url: `https://i.ytimg.com/vi/${videoId}/hqdefault.jpg`, width: 480, height: 360 }] } },
                              },
                            },
                          },
                        ],
                      },
                    },
                    {
                      musicCarouselShelfRenderer: {
                        header: { musicCarouselShelfBasicHeaderRenderer: { title: { runs: [{ text: `YT Extra ${ver}` }] } } },
                        contents: [
                          {
                            musicTwoRowItemRenderer: {
                              title: { runs: [{ text: `YT Card ${ver}` }], accessibility: { accessibilityData: { label: `YT Card ${ver}` } } },
                              subtitle: { runs: [{ text: `YT Artist 2 ${ver}` }] },
                              navigationEndpoint: {
                                browseEndpoint: {
                                  browseId: album2Id,
                                  browseEndpointContextSupportedConfigs: { browseEndpointContextMusicConfig: { pageType: 'MUSIC_PAGE_TYPE_ALBUM' } },
                                },
                              },
                              thumbnailRenderer: {
                                musicThumbnailRenderer: { thumbnail: { thumbnails: [{ url: `https://lh3.googleusercontent.com/yt2${ver}=w544-h544`, width: 544, height: 544 }] } },
                              },
                            },
                          },
                        ],
                      },
                    },
                  ],
                },
              },
            },
          },
        ],
      },
    },
  }
}

function yaResponseFor(url, ver) {
  const u = new URL(url)
  const p = u.pathname
  const num = ver === 'A' ? 100 : 200
  if (p === '/feed') return { generatedPlaylists: [] }
  if (p === '/landing3/new-releases') return { newReleases: [num + 1] }
  if (p === '/albums') return [{ id: num + 1, title: `YA Album ${ver}`, artists: [{ id: 1, name: `YA Artist ${ver}` }], trackCount: 5 }]
  if (p === '/landing3/new-playlists') return { newPlaylists: [] }
  if (p === '/landing3/chart') return {}
  if (p.includes('/likes/tracks')) return { library: { tracks: [{ id: String(num + 10) }] } }
  if (p === '/tracks') return [{ id: num + 10, title: `YA Liked ${ver}`, durationMs: 120000, artists: [{ id: 1, name: `YA Artist ${ver}` }] }]
  if (p.includes('/playlists/list')) return [{ uid: 42, kind: num + 20, title: `YA Playlist ${ver}`, trackCount: 3 }]
  if (p.includes('/likes/artists')) return []
  return {}
}

async function waitForIdbHashChange(page, key, prevHash, timeoutMs = 10000) {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    const hash = await readIdbHash(page, key)
    if (hash && hash !== prevHash) return hash
    await new Promise((r) => setTimeout(r, 25))
  }
  throw new Error(`Timed out waiting for IDB hash change on ${key} (previous: ${prevHash})`)
}

async function readIdbHash(page, key) {
  return page.evaluate(async (key) => {
    const db = await new Promise((res) => {
      const req = indexedDB.open('youcloud-cache', 1)
      req.onupgradeneeded = () => {
        if (!req.result.objectStoreNames.contains('home')) req.result.createObjectStore('home', { keyPath: 'key' })
      }
      req.onsuccess = () => res(req.result)
      req.onerror = () => res(null)
    })
    if (!db) return null
    try {
      const rec = await new Promise((res) => {
        const tx = db.transaction('home', 'readonly')
        const req = tx.objectStore('home').get(key)
        req.onsuccess = () => res(req.result)
        req.onerror = () => res(null)
      })
      return rec?.snapshot?.hash ?? null
    } finally {
      db.close()
    }
  }, key)
}

async function createIsolatedPage(browser, { service, extraLocalStorage = {}, onScRequest, onYtBrowse, onYaFetch }) {
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 }, reducedMotion: 'reduce' })
  const page = await context.newPage()
  const errors = []
  let navigations = 0
  page.on('pageerror', (e) => errors.push(e.message))
  page.on('framenavigated', (frame) => {
    if (frame === page.mainFrame()) navigations++
  })

  await page.route('**/*.supabase.co/**', (r) => r.abort())
  await page.route('https://fonts.googleapis.com/**', (r) => r.fulfill({ status: 200, contentType: 'text/css', body: '' }))
  await page.route('https://i1.sndcdn.com/**', (r) => r.abort())
  await page.route('https://i.ytimg.com/**', (r) => r.abort())
  await page.route('https://lh3.googleusercontent.com/**', (r) => r.abort())
  await page.route('https://avatars.yandex.net/**', (r) => r.abort())

  await page.route('**/x/**', async (route) => {
    const url = route.request().url()
    if (url.includes('api-v2.soundcloud.com/mixed-selections')) {
      const res = await onScRequest(url)
      await route.fulfill({ status: res.status ?? 200, contentType: 'application/json', body: JSON.stringify(res.body ?? {}) })
      return
    }
    if (url.includes('music.youtube.com/youtubei/v1/browse')) {
      const res = await onYtBrowse(url)
      await route.fulfill({ status: res.status ?? 200, contentType: 'application/json', body: JSON.stringify(res.body ?? {}) })
      return
    }
    if (url.includes('music.youtube.com/youtubei/v1/music/get_queue')) {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ queueDatas: [] }) })
      return
    }
    await route.fulfill({ status: 503, body: 'Blocked unexpected /x/ request in test' })
  })

  if (onYaFetch) {
    await page.exposeFunction('__ycBridgeFetch', async (url, method, body) => onYaFetch(url, method, body))
  }

  await page.addInitScript(
    ({ service, extraLocalStorage, hasYaBridge }) => {
      localStorage.setItem('yc.service', JSON.stringify(service))
      localStorage.setItem('yc.lang', JSON.stringify('ru'))
      localStorage.setItem('yc.liveBackdrop', 'false')
      localStorage.setItem('yc.playerWave', 'false')
      for (const [k, v] of Object.entries(extraLocalStorage)) {
        localStorage.setItem(k, typeof v === 'string' ? v : JSON.stringify(v))
      }
      if (hasYaBridge) {
        addEventListener('message', async (e) => {
          if (e.data?.yc !== 'page') return
          const data = e.data
          if (data.kind === 'ping') {
            window.postMessage({ yc: 'ext', kind: 'hello', version: '0.9.0' }, location.origin)
          } else if (data.kind === 'fetch') {
            try {
              const res = await window.__ycBridgeFetch(data.request.url, data.request.method || 'GET', data.request.body || '')
              window.postMessage(
                { yc: 'ext', kind: 'response', id: data.id, status: res.status ?? 200, body: JSON.stringify(res.body ?? {}) },
                location.origin,
              )
            } catch (err) {
              window.postMessage({ yc: 'ext', kind: 'response', id: data.id, error: String(err?.message ?? err) }, location.origin)
            }
          } else if (data.kind === 'call' && data.name === 'yt-account') {
            window.postMessage({ yc: 'ext', kind: 'response', id: data.id, value: false }, location.origin)
          }
        })
      }
    },
    { service, extraLocalStorage, hasYaBridge: !!onYaFetch },
  )

  return {
    context,
    page,
    errors,
    getNavigations: () => navigations,
    resetNavigations: () => { navigations = 0 },
  }
}

async function testSoundCloud(browser, origin, label = 'dev') {
  let mode = 'A'
  let gate = null
  let reqCount = 0
  const { context, page, errors, getNavigations, resetNavigations } = await createIsolatedPage(browser, {
    service: 'sc',
    extraLocalStorage: { 'yc.sc.clientId': 'test-sc-client' },
    onScRequest: async () => {
      reqCount++
      if (gate) await gate.promise
      if (mode === 'ERR') return { status: 500, body: { error: 'boom' } }
      return { status: 200, body: scPayload(mode) }
    },
  })

  try {
    // 1. Cold open -> caches Version A
    await page.goto(origin)
    await page.getByText('SC Station A1').waitFor()
    const hashA = await readIdbHash(page, '1:sc:anon:ru')
    assert.ok(hashA, `SC (${label}): hashA saved in IDB`)

    // 2. Full reload with held deferred server -> R1 instant Version A + R2a request + R2b frozen DOM on B
    mode = 'B'
    gate = deferred()
    const reqsBefore = reqCount
    await page.reload()
    await page.getByText('SC Station A1').waitFor()
    assert.equal(await page.locator('.loading').count(), 0, `SC (${label}): R1 must not show Loading spinner on reload`)
    await page.waitForFunction(() => !!document.querySelector('.shelf .card'))
    // Wait until background request is actually emitted while deferred gate is STILL pending
    const start = Date.now()
    while (reqCount === reqsBefore && Date.now() - start < 5000) await new Promise((r) => setTimeout(r, 20))
    assert.ok(reqCount > reqsBefore, `SC (${label}): R2a background request must be emitted`)
    assert.equal(await page.getByText('SC Station B1').count(), 0, `SC (${label}): R1 must render Version A while server is held`)

    // Attach DOM identity marker to the active Version A card before resolving Version B
    await page.evaluate(() => {
      const card = document.querySelector('.shelf .card')
      card.__ycMarker = 'vA-node'
    })
    resetNavigations()
    gate.resolve()
    await waitForIdbHashChange(page, '1:sc:anon:ru', hashA)
    await page.waitForTimeout(100)

    const domState = await page.evaluate(() => {
      const card = document.querySelector('.shelf .card')
      return { marker: card?.__ycMarker, connected: card?.isConnected, text: document.body.innerText }
    })
    assert.equal(domState.marker, 'vA-node', `SC (${label}): R2b must preserve exact DOM node identity`)
    assert.equal(domState.connected, true)
    assert.ok(domState.text.includes('SC Station A1') && !domState.text.includes('SC Station B1'), `SC (${label}): R2b active screen must remain on Version A`)
    assert.equal(getNavigations(), 0, `SC (${label}): R2b must not trigger reload`)

    // 3. Reload again with server held -> Version B renders immediately from updated cache
    const reqsBefore3 = reqCount
    gate = deferred()
    await page.reload()
    await page.getByText('SC Station B1').waitFor()
    assert.equal(await page.locator('.loading').count(), 0)
    assert.equal(await page.getByText('SC Station A1').count(), 0)
    while (reqCount === reqsBefore3) await new Promise((r) => setTimeout(r, 20))
    gate.resolve()
    await page.waitForTimeout(50)

    // 4. SPA route switch (#/settings -> #/home) with server switched back to A and held
    mode = 'A'
    const hashB = await readIdbHash(page, '1:sc:anon:ru')
    const reqsBefore4 = reqCount
    gate = deferred()
    await page.evaluate(() => (location.hash = '#/settings'))
    await page.getByRole('heading', { name: 'Настройки' }).waitFor()
    await page.evaluate(() => (location.hash = '#/'))
    await page.getByText('SC Station B1').waitFor()
    assert.equal(await page.locator('.loading').count(), 0, `SC (${label}): SPA revisit must render Version B with 0ms L1 hit`)
    while (reqCount === reqsBefore4) await new Promise((r) => setTimeout(r, 20))
    gate.resolve()
    await waitForIdbHashChange(page, '1:sc:anon:ru', hashB)
    // Open DOM stays on Version B after background update to A
    assert.ok((await page.locator('body').innerText()).includes('SC Station B1'))
    // Next SPA revisit shows A
    await page.evaluate(() => (location.hash = '#/settings'))
    await page.getByRole('heading', { name: 'Настройки' }).waitFor()
    await page.evaluate(() => (location.hash = '#/'))
    await page.getByText('SC Station A1').waitFor()

    // 5. Background 500 error resilience
    mode = 'ERR'
    const hashBeforeErr = await readIdbHash(page, '1:sc:anon:ru')
    await page.reload()
    await page.getByText('SC Station A1').waitFor()
    await page.waitForTimeout(150)
    assert.equal(await readIdbHash(page, '1:sc:anon:ru'), hashBeforeErr, `SC (${label}): background 500 must preserve valid cache`)
    assert.equal(await page.locator('.problem').count(), 0, `SC (${label}): background 500 must not show Problem UI`)

    assert.deepEqual(errors, [], `SC (${label}): unexpected page errors`)
    console.log(`E2E SoundCloud (${label}): R1, R2a, R2b DOM identity, SPA revisit, and 500 resilience passed`)
  } finally {
    await context.close()
  }
}

async function testYouTube(browser, origin) {
  let mode = 'A'
  let gate = null
  let reqCount = 0
  const { context, page, errors } = await createIsolatedPage(browser, {
    service: 'yt',
    onYtBrowse: async () => {
      reqCount++
      if (gate) await gate.promise
      if (mode === 'ERR') return { status: 500, body: {} }
      return { status: 200, body: ytBrowsePayload(mode) }
    },
  })

  try {
    // 1. Cold open -> caches Version A (Quick picks track + album shelf)
    await page.goto(origin)
    await page.getByText('YT Song A').waitFor()
    await page.getByText('YT Album A').waitFor()
    const hashA = await readIdbHash(page, '1:yt:anon:ru')
    assert.ok(hashA, 'YT: hashA saved in IDB')

    // 2. Reload with held deferred server -> R1 instant render of A + R2a request + R2b frozen DOM on B
    mode = 'B'
    gate = deferred()
    const reqsBefore = reqCount
    await page.reload()
    await page.getByText('YT Song A').waitFor()
    await page.getByText('YT Album A').waitFor()
    assert.equal(await page.locator('.loading').count(), 0, 'YT: R1 must not show Loading spinner on reload')

    const start = Date.now()
    while (reqCount === reqsBefore && Date.now() - start < 5000) await new Promise((r) => setTimeout(r, 20))
    assert.ok(reqCount > reqsBefore, 'YT: R2a background browse request must be emitted')

    await page.evaluate(() => {
      document.querySelector('.shelf .card').__ycMarker = 'yt-vA'
    })
    gate.resolve()
    await waitForIdbHashChange(page, '1:yt:anon:ru', hashA)
    await page.waitForTimeout(100)

    const domState = await page.evaluate(() => ({
      marker: document.querySelector('.shelf .card')?.__ycMarker,
      connected: document.querySelector('.shelf .card')?.isConnected,
      text: document.body.innerText,
    }))
    assert.equal(domState.marker, 'yt-vA', 'YT: R2b must preserve DOM node identity')
    assert.equal(domState.connected, true)
    assert.ok(domState.text.includes('YT Album A') && domState.text.includes('YT Song A'))
    assert.ok(!domState.text.includes('YT Album B') && !domState.text.includes('YT Song B'))

    // 3. Reload with held server -> Version B renders immediately from cache
    const reqsBefore3 = reqCount
    gate = deferred()
    await page.reload()
    await page.getByText('YT Album B').waitFor()
    await page.getByText('YT Song B').waitFor()
    assert.equal(await page.locator('.loading').count(), 0)
    while (reqCount === reqsBefore3) await new Promise((r) => setTimeout(r, 20))
    gate.resolve()
    await page.waitForTimeout(50)

    // 4. Background 500 error leaves cached screen & cache intact
    mode = 'ERR'
    const hashBeforeErr = await readIdbHash(page, '1:yt:anon:ru')
    await page.reload()
    await page.getByText('YT Album B').waitFor()
    await page.waitForTimeout(500)
    assert.equal(await readIdbHash(page, '1:yt:anon:ru'), hashBeforeErr)

    assert.deepEqual(errors, [], 'YT: unexpected page errors')
    console.log('E2E YouTube: R1, R2a, R2b DOM identity, and 500 resilience passed')
  } finally {
    await context.close()
  }
}

async function testYandex(browser, origin) {
  let mode = 'A'
  let gate = null
  let reqCount = 0
  const { context, page, errors } = await createIsolatedPage(browser, {
    service: 'ya',
    extraLocalStorage: { 'yc.acc.ya': { token: 'ya-e2e', uid: 42, name: 'Tester', plus: true } },
    onYaFetch: async (url) => {
      reqCount++
      if (gate) await gate.promise
      if (mode === 'ERR') return { status: 500, body: { error: 'boom' } }
      return { status: 200, body: { result: yaResponseFor(url, mode) } }
    },
  })

  try {
    // 1. Cold open signed in -> renders WaveCompact + QuickRow playlist + Album shelf Version A
    await page.goto(origin)
    await page.getByText('YA Album A').waitFor()
    await page.getByText('YA Playlist A').waitFor()
    const hashA = await readIdbHash(page, '1:ya:user:42:ru')
    assert.ok(hashA, 'YA: hashA saved in IDB')

    // 2. Reload with held bridge -> R1 instant render (not waiting for bridge or 400ms ext timeout)
    mode = 'B'
    gate = deferred()
    const reqsBefore = reqCount
    await page.reload()
    await page.getByText('YA Album A').waitFor()
    await page.getByText('YA Playlist A').waitFor()
    assert.equal(await page.locator('.loading').count(), 0, 'YA: R1 must not show Loading spinner')

    const start = Date.now()
    while (reqCount === reqsBefore && Date.now() - start < 5000) await new Promise((r) => setTimeout(r, 20))
    assert.ok(reqCount > reqsBefore, 'YA: R2a background bridge request must be emitted')

    await page.evaluate(() => {
      document.querySelector('.shelf .card').__ycMarker = 'ya-vA'
    })
    gate.resolve()
    await waitForIdbHashChange(page, '1:ya:user:42:ru', hashA)
    await page.waitForTimeout(100)

    const domState = await page.evaluate(() => ({
      marker: document.querySelector('.shelf .card')?.__ycMarker,
      connected: document.querySelector('.shelf .card')?.isConnected,
      text: document.body.innerText,
    }))
    assert.equal(domState.marker, 'ya-vA', 'YA: R2b must preserve DOM node identity')
    assert.equal(domState.connected, true)
    assert.ok(domState.text.includes('YA Album A') && domState.text.includes('YA Playlist A'))
    assert.ok(!domState.text.includes('YA Album B') && !domState.text.includes('YA Playlist B'))

    // 3. Reload with held bridge -> Version B renders immediately from cache
    const reqsBefore3 = reqCount
    gate = deferred()
    await page.reload()
    await page.getByText('YA Album B').waitFor()
    await page.getByText('YA Playlist B').waitFor()
    assert.equal(await page.locator('.loading').count(), 0)
    while (reqCount === reqsBefore3) await new Promise((r) => setTimeout(r, 20))
    gate.resolve()
    await page.waitForTimeout(100)

    // 4. Background 500 error leaves cached screen & cache intact
    mode = 'ERR'
    const hashBeforeErr = await readIdbHash(page, '1:ya:user:42:ru')
    await page.reload()
    await page.getByText('YA Album B').waitFor()
    await page.waitForTimeout(150)
    assert.equal(await readIdbHash(page, '1:ya:user:42:ru'), hashBeforeErr)

    assert.deepEqual(errors, [], 'YA: unexpected page errors')
    console.log('E2E Yandex: R1, R2a, R2b DOM identity, signed-in library, and 500 resilience passed')
  } finally {
    await context.close()
  }
}

async function testCorruptIdbAndWaiter(browser, origin) {
  // 1. Verify waitForIdbHashChange genuinely polls and throws on unchanged hash
  {
    const { context, page } = await createIsolatedPage(browser, {
      service: 'sc',
      extraLocalStorage: { 'yc.sc.clientId': 'test-sc-client' },
      onScRequest: async () => ({ status: 200, body: scPayload('A') }),
    })
    try {
      await page.goto(origin)
      await page.getByText('SC Station A1').waitFor()
      const h = await readIdbHash(page, '1:sc:anon:ru')
      assert.ok(h)
      await assert.rejects(waitForIdbHashChange(page, '1:sc:anon:ru', h, 150), /Timed out waiting for IDB hash change/)
    } finally {
      await context.close()
    }
  }

  // 2. Seed subtly corrupt IDB payloads and verify clean fallback to network with 0 pageerrors
  const corruptPayloads = [
    ['null track entry', { schema: 1, service: 'sc', accountKey: 'anon', lang: 'ru', signedIn: false, sections: [{ title: 'Row', sets: [] }], lib: null, tracks: { 'sc:t:1': null }, sets: {}, artists: {}, hash: 'corrupt-1', updatedAt: 1 }],
    ['array tracks', { schema: 1, service: 'sc', accountKey: 'anon', lang: 'ru', signedIn: false, sections: [{ title: 'Row', sets: [] }], lib: null, tracks: [], sets: {}, artists: {}, hash: 'corrupt-2', updatedAt: 1 }],
    ['null section', { schema: 1, service: 'sc', accountKey: 'anon', lang: 'ru', signedIn: false, sections: [null], lib: null, tracks: {}, sets: {}, artists: {}, hash: 'corrupt-3', updatedAt: 1 }],
    ['lib missing arrays', { schema: 1, service: 'sc', accountKey: 'anon', lang: 'ru', signedIn: false, sections: [{ title: 'Row', sets: [] }], lib: { liked: 'sc:likes', likedIds: [] }, tracks: {}, sets: {}, artists: {}, hash: 'corrupt-4', updatedAt: 1 }],
    ['null section set ref', { schema: 1, service: 'sc', accountKey: 'anon', lang: 'ru', signedIn: false, sections: [{ title: 'Row', sets: [null] }], lib: null, tracks: {}, sets: {}, artists: {}, hash: 'corrupt-5', updatedAt: 1 }],
    ['dangling section set ref', { schema: 1, service: 'sc', accountKey: 'anon', lang: 'ru', signedIn: false, sections: [{ title: 'Row', sets: ['sc:pl:missing'] }], lib: null, tracks: {}, sets: {}, artists: {}, hash: 'corrupt-6', updatedAt: 1 }],
  ]

  for (const [label, payload] of corruptPayloads) {
    const { context, page, errors } = await createIsolatedPage(browser, {
      service: 'sc',
      extraLocalStorage: { 'yc.sc.clientId': 'test-sc-client' },
      onScRequest: async () => ({ status: 200, body: scPayload('A') }),
    })
    try {
      await page.goto(origin)
      await page.getByText('SC Station A1').waitFor()
      await page.evaluate(async (snap) => {
        const db = await new Promise((res, rej) => {
          const req = indexedDB.open('youcloud-cache', 1)
          req.onupgradeneeded = () => {
            if (!req.result.objectStoreNames.contains('home')) req.result.createObjectStore('home', { keyPath: 'key' })
          }
          req.onsuccess = () => res(req.result)
          req.onerror = () => rej(req.error)
        })
        await new Promise((res, rej) => {
          const tx = db.transaction('home', 'readwrite')
          const req = tx.objectStore('home').put({ key: '1:sc:anon:ru', snapshot: snap })
          req.onsuccess = () => res()
          req.onerror = () => rej(req.error)
        })
        db.close()
      }, payload)
      await page.reload()
      await page.getByText('SC Station A1').waitFor()
      const recoveredHash = await waitForIdbHashChange(page, '1:sc:anon:ru', payload.hash, 5000)
      assert.ok(recoveredHash && recoveredHash !== payload.hash, `corrupt IDB (${label}) must fall back to network and rewrite valid hash`)
      assert.deepEqual(errors, [], `corrupt IDB (${label}) must produce 0 pageerrors`)
    } finally {
      await context.close()
    }
  }
  console.log('E2E Corrupt IDB fallback & waitForIdbHashChange polling passed')
}

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.ttf': 'font/ttf',
  '.json': 'application/json',
}

function startStaticDistServer() {
  return new Promise((resolvePromise) => {
    const server = createHttpServer((req, res) => {
      const pathOnly = (req.url || '/').split('?')[0]
      let filePath = resolve(distClientDir, '.' + (pathOnly === '/' ? '/index.html' : pathOnly))
      if (!filePath.startsWith(distClientDir) || !existsSync(filePath)) {
        filePath = join(distClientDir, 'index.html')
      }
      const ext = extname(filePath)
      res.writeHead(200, { 'content-type': MIME[ext] || 'application/octet-stream' })
      res.end(readFileSync(filePath))
    })
    server.listen(0, '127.0.0.1', () => {
      const addr = server.address()
      resolvePromise({ server, origin: `http://127.0.0.1:${addr.port}` })
    })
  })
}

const server = await createServer({
  configFile: false,
  root: rootDir,
  plugins: [svelte()],
  logLevel: 'error',
  server: { host: '127.0.0.1', port: 0 },
})
let browser
let distServer
try {
  await server.listen()
  const addr = server.httpServer.address()
  assert.ok(addr && typeof addr === 'object')
  const origin = `http://127.0.0.1:${addr.port}`
  browser = await chromium.launch({ headless: true })

  await testSoundCloud(browser, origin, 'dev')
  await testYouTube(browser, origin)
  await testYandex(browser, origin)
  await testCorruptIdbAndWaiter(browser, origin)

  if (existsSync(join(distClientDir, 'index.html'))) {
    const started = await startStaticDistServer()
    distServer = started.server
    await testSoundCloud(browser, started.origin, 'dist-prod')
  }
} finally {
  await browser?.close()
  await server.close()
  distServer?.close()
}
