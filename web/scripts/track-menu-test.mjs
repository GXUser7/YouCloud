// Exercise the real player layouts and lyric auto-scroll in a clean browser, without service accounts.
import assert from 'node:assert/strict'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright-core'
import { svelte } from '@sveltejs/vite-plugin-svelte'
import { createServer } from 'vite'

const server = await createServer({
  configFile: false,
  root: fileURLToPath(new URL('..', import.meta.url)),
  plugins: [svelte()],
  logLevel: 'error',
  server: { host: '127.0.0.1', port: 0 },
})
let browser
try {
  await server.listen()
  const address = server.httpServer.address()
  assert.ok(address && typeof address === 'object')
  browser = await chromium.launch({ headless: true })
  const page = await browser.newPage({ viewport: { width: 1600, height: 1000 }, reducedMotion: 'reduce' })
  await page.route('**/x/**', (route) => route.fulfill({ status: 503, body: 'No live service calls in this test' }))
  await page.addInitScript(() => {
    for (const key of ['liveBackdrop', 'playerWave']) localStorage.setItem('yc.' + key, 'false')
    localStorage.setItem('yc.carouselLyrics', 'true')
  })
  await page.goto(`http://127.0.0.1:${address.port}/#/set/yt-liked`)
  await page.locator('.playback-actions').waitFor()

  for (const look of ['classic', 'poster', 'carousel', 'shape']) {
    await page.evaluate(async () => {
      const { app } = await import('/src/lib/state.svelte.ts')
      app.nowPlaying = false
    })
    await page.evaluate(async (look) => {
      const { app, player } = await import('/src/lib/state.svelte.ts')
      player.queue = ['sc1', 'sc2']
      player.index = 0
      player.duration = 154
      player.position = 9
      app.playerLook = look
      app.nowPlaying = true
    }, look)
    const dialog = page.getByRole('dialog', { name: 'Сейчас играет', exact: true })
    const lyrics = dialog.locator('.lyrics')
    await lyrics.waitFor()
    await page.waitForFunction(() => document.querySelector('[role="dialog"] .lyrics .now')?.textContent?.includes('Город'))
    const before = await lyrics.evaluate((el) => el.scrollTop)
    const trigger = dialog.getByRole('button', { name: 'Ещё', exact: true })
    await trigger.click()
    const menu = page.getByRole('menu', { name: 'Действия с треком' })
    await menu.waitFor()
    await page.evaluate(async () => {
      const { player } = await import('/src/lib/state.svelte.ts')
      player.position = 78
    })
    await page.waitForFunction((before) => document.querySelector('[role="dialog"] .lyrics').scrollTop > before + 40, before)
    assert.equal(await menu.count(), 1, `${look}: changing the lyric line must keep the menu open`)
    assert.equal(await trigger.getAttribute('aria-expanded'), 'true')
    await menu.dispatchEvent('scroll')
    assert.equal(await menu.count(), 1, `${look}: scrolling the menu must keep it open`)
    await page.keyboard.press('Escape')
    await menu.waitFor({ state: 'detached' })
    assert.ok(await trigger.evaluate((el) => el === document.activeElement), `${look}: Escape restores focus`)
    await trigger.click()
    await menu.waitFor()
    await dialog.getByRole('heading').first().click()
    await menu.waitFor({ state: 'detached' })
    console.log(`${look}: lyric auto-scroll, menu scroll, Escape and outside click passed`)
  }

  await page.evaluate(async () => {
    const { app } = await import('/src/lib/state.svelte.ts')
    app.nowPlaying = false
  })
  const rowTrigger = page.locator('.track-list').getByRole('button', { name: 'Ещё', exact: true }).first()
  await rowTrigger.click()
  const menu = page.getByRole('menu', { name: 'Действия с треком' })
  await menu.waitFor()
  await page.locator('.scroller').evaluate((el) => el.scrollBy(0, 60))
  await menu.waitFor({ state: 'detached' })
  console.log('Track row: scrolling the anchor container dismisses the menu')
} finally {
  await browser?.close()
  await server.close()
}
