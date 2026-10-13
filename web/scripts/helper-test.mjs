// YouCloud Helper end to end, in a real Chromium with the extension: the browser starts the helper,
// the site's messages reach it, yt-dlp's answers come back, and the status goes to Discord.
// Nothing real is touched: the helper installs under a test name into a temporary folder,
// yt-dlp and Discord are stand-ins (YouTube is never asked), and the browser's YouTube "sign-in"
// is a made-up cookie. At the end the test uninstalls the helper and checks nothing is left.
//
// npm run test:helper — needs Go and Playwright's Chromium (npx playwright-core install chromium),
// and port 5190 free: the test starts the site's dev server there. Screenshots of the helper's card
// in Settings, before and after the install, go to test-output/.

import { execFileSync, spawn } from 'node:child_process'
import fs from 'node:fs'
import net from 'node:net'
import os from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright-core'

const WEB = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const NAME = 'com.youcloud.helper_test'
const win = process.platform === 'win32'
const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'yc-helper-test-'))
const home = path.join(tmp, 'home')
const out = path.join(WEB, 'test-output')
fs.mkdirSync(out, { recursive: true })

const failures = []
const check = (ok, what) => {
  console.log(`  ${ok ? 'ok' : 'FAIL'} — ${what}`)
  if (!ok) failures.push(what)
}
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
async function waitFor(get, what, ms = 15000) {
  const until = Date.now() + ms
  while (Date.now() < until) {
    const v = await get()
    if (v) return v
    await sleep(100)
  }
  throw new Error('не дождались: ' + what)
}

// ---------- The helper, built for this machine ----------

function goCommand() {
  for (const c of [process.env.GO, 'go', 'C:\\Program Files\\Go\\bin\\go.exe', '/usr/local/go/bin/go'].filter(Boolean)) {
    try {
      execFileSync(c, ['version'], { stdio: 'ignore' })
      return c
    } catch {}
  }
  throw new Error('нужен Go')
}
const exe = path.join(tmp, win ? 'youcloud-helper.exe' : 'youcloud-helper')
execFileSync(goCommand(), ['build', '-buildvcs=false', '-o', exe, '.'], { cwd: path.join(WEB, 'helper'), stdio: 'inherit' })

// ---------- Stand-ins: yt-dlp and Discord ----------

const calls = path.join(tmp, 'ytdlp-calls.txt')
const fakeJs = path.join(tmp, 'fake-ytdlp.mjs')
fs.writeFileSync(
  fakeJs,
  `import fs from 'node:fs'
const args = process.argv.slice(2)
const id = new URL(args.at(-1)).searchParams.get('v')
const cookies = args.includes('--cookies') ? fs.readFileSync(args[args.indexOf('--cookies') + 1], 'utf8') : ''
const live = args[args.indexOf('-f') + 1].includes('m3u8')
fs.appendFileSync(${JSON.stringify(calls)}, JSON.stringify({ id, cookies: !!cookies, live, js: args[args.indexOf('--js-runtimes') + 1] }) + '\\n')
const ok = () => console.log(JSON.stringify({ url: 'https://rr1---sn-test.googlevideo.com/videoplayback?expire=' + (Math.floor(Date.now() / 1000) + 21600) + '&id=' + id }))
// A broadcast: asked for as a file it has none (yt-dlp passes over its live formats); asked for as
// one, its HLS playlist, with expire in the path.
const broadcast = () => {
  if (!live) {
    console.error('ERROR: [youtube] ' + id + ': No video formats found!; please report this issue on  https://github.com/yt-dlp/yt-dlp/issues?q= , filling out the appropriate issue template')
    process.exit(1)
  }
  console.log(JSON.stringify({ url: 'https://manifest.googlevideo.com/api/manifest/hls_playlist/expire/' + (Math.floor(Date.now() / 1000) + 21600) + '/id/' + id + '.1/itag/234/source/yt_live_broadcast/playlist/index.m3u8', is_live: true, protocol: 'm3u8_native' }))
}
if (id === 'livestream1') broadcast()
else if (id === 'livesignin1') {
  if (cookies) broadcast()
  else { console.error("ERROR: [youtube] livesignin1: Sign in to confirm you're not a bot"); process.exit(1) }
} else if (id === 'needsignin0') {
  if (cookies.startsWith('# Netscape HTTP Cookie File') && /\\tSAPISID\\tmade-up/.test(cookies)) ok()
  else { console.error("ERROR: [youtube] needsignin0: Sign in to confirm you're not a bot"); process.exit(1) }
} else if (id === 'refusedvid1') {
  console.error('WARNING: something'); console.error('ERROR: [youtube] refusedvid1: Video unavailable'); process.exit(1)
} else ok()
`,
)
let fakeYtdlp
if (win) {
  fakeYtdlp = path.join(tmp, 'fake-yt-dlp.cmd')
  fs.writeFileSync(fakeYtdlp, `@"${process.execPath}" "${fakeJs}" %*\r\n`)
} else {
  fakeYtdlp = path.join(tmp, 'fake-yt-dlp')
  fs.writeFileSync(fakeYtdlp, `#!/bin/sh\nexec "${process.execPath}" "${fakeJs}" "$@"\n`, { mode: 0o755 })
}

const PIPE = win ? `\\\\?\\pipe\\youcloud-test-discord-${process.pid}` : path.join(tmp, 'discord-ipc-test')
let handshake = null
const activities = []
let discordClosed = false
const frame = (op, data) => {
  const json = Buffer.from(JSON.stringify(data))
  const head = Buffer.alloc(8)
  head.writeUInt32LE(op, 0)
  head.writeUInt32LE(json.length, 4)
  return Buffer.concat([head, json])
}
const discord = net.createServer((s) => {
  discordClosed = false
  let buf = Buffer.alloc(0)
  s.on('data', (d) => {
    buf = Buffer.concat([buf, d])
    while (buf.length >= 8) {
      const op = buf.readUInt32LE(0)
      const len = buf.readUInt32LE(4)
      if (buf.length < 8 + len) break
      const msg = JSON.parse(buf.subarray(8, 8 + len).toString('utf8'))
      buf = buf.subarray(8 + len)
      if (op === 0) {
        handshake = msg
        s.write(frame(1, { cmd: 'DISPATCH', evt: 'READY', data: { v: 1 } }))
      } else if (msg.cmd === 'SET_ACTIVITY') {
        activities.push(msg.args.activity ?? null)
        s.write(frame(1, { cmd: 'SET_ACTIVITY', data: msg.args.activity ?? null, nonce: msg.nonce }))
      }
    }
  })
  s.on('close', () => (discordClosed = true))
  s.on('error', () => {})
})
await new Promise((r) => discord.listen(PIPE, r))

const env = {
  ...process.env,
  YOUCLOUD_HELPER_HOME: home,
  YOUCLOUD_HELPER_NAME: NAME,
  YOUCLOUD_YTDLP: fakeYtdlp,
  YOUCLOUD_JS_RUNTIME: 'node:' + process.execPath,
  YOUCLOUD_DISCORD_PIPE: PIPE,
  YOUCLOUD_DISCORD_APP_ID: '1234567890',
}

// The extension, asking for the test's helper instead of the real one (same key, same id).
const ext = path.join(tmp, 'extension')
fs.cpSync(path.join(WEB, 'extension'), ext, { recursive: true })
const bg = path.join(ext, 'background.js')
fs.writeFileSync(bg, fs.readFileSync(bg, 'utf8').replace("const HELPER = 'com.youcloud.helper'", `const HELPER = '${NAME}'`))

// ---------- The site: its dev server, and a blank page of its origin for the protocol ----------

if (await fetch('http://localhost:5190/').then(() => true, () => false)) throw new Error('порт 5190 занят — останови дев-сервер YouCloud')
const dev = spawn(process.execPath, [path.join(WEB, 'node_modules', 'vite', 'bin', 'vite.js')], { cwd: WEB, stdio: 'ignore' })
const stopDev = () => {
  // Vite with the Worker beside it (workerd): on Windows only a kill of the whole tree stops both.
  if (win) {
    try {
      execFileSync('taskkill', ['/pid', String(dev.pid), '/t', '/f'], { stdio: 'ignore' })
    } catch {}
  } else dev.kill()
}
// The protocol is checked on a page of the site's origin with nothing of the site on it: the real
// pages talk to the extension themselves and would answer in between.
const BLANK = 'http://localhost:5190/__helper-test'

const profile = path.join(tmp, 'profile')
const launch = async () => {
  const ctx = await chromium.launchPersistentContext(profile, {
    channel: 'chromium',
    headless: true,
    env,
    viewport: { width: 1400, height: 1000 },
    args: [`--disable-extensions-except=${ext}`, `--load-extension=${ext}`],
  })
  await ctx.route(BLANK, (route) => route.fulfill({ contentType: 'text/html; charset=utf-8', body: '<!doctype html><title>YouCloud (test)</title><body>test</body>' }))
  return ctx
}

/** Settings, scrolled to the helper's card: a screenshot of it, and the card itself. */
async function settingsCard(ctx, shot, ready) {
  const tab = await ctx.newPage()
  await tab.goto('http://localhost:5190/#/settings')
  const card = tab.locator('.hc')
  await card.waitFor({ timeout: 30000 })
  await waitFor(async () => ready(await card.innerText()), 'карточка помощника: ' + shot, 20000)
  await card.scrollIntoViewIfNeeded()
  await sleep(600)
  return { tab, card, shoot: () => tab.screenshot({ path: path.join(out, shot) }) }
}

/** The site's side of the protocol, run in the page: one message, its answer. */
const ask = (page, data, kind = 'response') =>
  page.evaluate(
    ({ data, kind }) =>
      new Promise((resolve) => {
        const on = (e) => {
          const d = e.data
          if (!d || d.yc !== 'ext' || d.kind !== kind || (kind === 'response' && d.id !== data.id)) return
          removeEventListener('message', on)
          resolve(d)
        }
        addEventListener('message', on)
        postMessage({ yc: 'page', ...data }, location.origin)
        setTimeout(() => resolve({ error: 'no answer' }), 20000)
      }),
    { data, kind },
  )

/** A message the site sends without waiting for an answer. */
const post = (page, data) => page.evaluate((data) => postMessage({ yc: 'page', ...data }, location.origin), data)

let ctx
try {
  await waitFor(() => fetch('http://localhost:5190/').then((r) => r.ok).catch(() => false), 'дев-сервер YouCloud', 60000)
  ctx = await launch()

  console.log('Карточка до установки')
  {
    const { tab, card, shoot } = await settingsCard(ctx, 'helper-missing.png', (t) => /Поставь помощник|Install YouCloud Helper/.test(t))
    const link = card.locator('a[download]')
    check((await link.getAttribute('href'))?.startsWith('/youcloud-helper'), `кнопка «Скачать» — ${await link.getAttribute('href')}`)
    await shoot()
    console.log('  снимок test-output/helper-missing.png')
    await tab.close()
  }

  const page = ctx.pages()[0] ?? (await ctx.newPage())
  await page.goto(BLANK)

  console.log('Расширение')
  const hello = await ask(page, { kind: 'ping' }, 'hello')
  check(hello.version === JSON.parse(fs.readFileSync(path.join(WEB, 'extension', 'manifest.json'), 'utf8')).version, `расширение ${hello.version} на странице`)

  console.log('Без помощника')
  const none = await ask(page, { kind: 'call', id: 1, name: 'helper' })
  check(!none.error && none.value === null, 'помощника нет — ответ null, а не ошибка')

  console.log('Установка')
  execFileSync(exe, ['--install'], { env })
  const installed = path.join(home, path.basename(exe))
  check(fs.existsSync(installed), 'помощник скопировал себя в свою папку')
  const manifest = JSON.parse(fs.readFileSync(path.join(home, NAME + '.json'), 'utf8'))
  check(manifest.path === installed && manifest.allowed_origins?.[0] === 'chrome-extension://jddbcipfppgdolkopjmaidoejnjoanch/', 'манифест: путь и только расширение YouCloud')
  if (win) {
    const reg = execFileSync('reg', ['query', `HKCU\\Software\\Chromium\\NativeMessagingHosts\\${NAME}`, '/ve'], { encoding: 'utf8' })
    check(reg.includes(path.join(home, NAME + '.json')), 'Chromium знает о помощнике (реестр)')
  }

  console.log('Помощник')
  const found = await ask(page, { kind: 'call', id: 2, name: 'helper' })
  check(found.value?.version === JSON.parse(fs.readFileSync(path.join(WEB, 'helper', 'version.json'), 'utf8')).version, `помощник ${found.value?.version} отвечает (${found.value?.os})`)

  console.log('Карточка после установки')
  {
    const { tab, card, shoot } = await settingsCard(ctx, 'helper-connected.png', (t) => /подключён|connected/.test(t))
    await card.getByRole('switch').click()
    await sleep(400)
    check((await card.getByRole('switch').getAttribute('aria-checked')) === 'true', 'переключатель статуса в Discord')
    await shoot()
    console.log('  снимок test-output/helper-connected.png')
    await tab.close()
  }

  console.log('Звук YouTube')
  const first = await ask(page, { kind: 'yt-stream', id: 3, videoId: 'dQw4w9WgXcQ' })
  check(/^https:\/\/rr1---sn-test\.googlevideo\.com\/videoplayback\?expire=/.test(first.value?.url ?? ''), 'ссылка от yt-dlp')
  check(first.value?.expire > Date.now() + 5 * 3600_000, 'срок ссылки — из её expire')
  const again = await ask(page, { kind: 'yt-stream', id: 4, videoId: 'dQw4w9WgXcQ' })
  const log = () => fs.readFileSync(calls, 'utf8').trim().split('\n').map((l) => JSON.parse(l))
  check(again.value?.url === first.value?.url && log().filter((c) => c.id === 'dQw4w9WgXcQ').length === 1, 'вторая просьба — из запаса, yt-dlp не запускался')
  check(log()[0].js === 'node:' + process.execPath && !log()[0].cookies, 'yt-dlp с движком JavaScript и без входа')

  const refused = await ask(page, { kind: 'yt-stream', id: 5, videoId: 'refusedvid1' })
  check(refused.error === 'Video unavailable', `отказ YouTube дошёл до сайта: «${refused.error}»`)
  console.log('Трансляции')
  const live = await ask(page, { kind: 'yt-stream', id: 60, videoId: 'livestream1', live: true })
  check(/^https:\/\/manifest\.googlevideo\.com\/api\/manifest\/hls_playlist\//.test(live.value?.url ?? '') && live.value?.live === true, 'трансляция: плейлист HLS и live')
  check(live.value?.expire > Date.now() + 5 * 3600_000, 'срок плейлиста — из expire в пути')
  const runs = (id) => log().filter((c) => c.id === id).map((c) => `${c.cookies ? 'вход' : 'гость'}:${c.live ? 'эфир' : 'файл'}`).join(' ')
  check(runs('livestream1') === 'гость:эфир', 'сайт сказал «трансляция» — yt-dlp спрошен один раз, сразу как трансляция')
  const guessed = await ask(page, { kind: 'yt-stream', id: 61, videoId: 'livestream1', fresh: true })
  check(guessed.value?.live === true, 'сайт не знал — помощник сам понял, что это трансляция')
  check(runs('livestream1') === 'гость:эфир гость:файл гость:эфир', `как файл — форматов нет, тогда как трансляция (${runs('livestream1')})`)
  const notLive = await ask(page, { kind: 'yt-stream', id: 62, videoId: 'refusedvid1', fresh: true })
  check(notLive.error === 'Video unavailable' && !runs('refusedvid1').includes('эфир'), `отказ — не трансляция, спрошен только как файл: «${notLive.error}»`)

  const bad = await ask(page, { kind: 'yt-stream', id: 6, videoId: 'nope' })
  check(!!bad.error && !log().some((c) => c.id === 'nope'), 'неверный id до yt-dlp не доходит')

  const noSignIn = await ask(page, { kind: 'yt-stream', id: 7, videoId: 'needsignin0' })
  check(/Sign in to confirm/.test(noSignIn.error ?? ''), 'без входа в YouTube — отказ «подтвердите, что вы не бот»')
  await ctx.addCookies([
    { name: 'SAPISID', value: 'made-up', domain: '.youtube.com', path: '/', secure: true, expires: Math.floor(Date.now() / 1000) + 3600 },
    { name: 'SID', value: 'made-up-too', domain: '.youtube.com', path: '/', secure: true, httpOnly: true, expires: Math.floor(Date.now() / 1000) + 3600 },
  ])
  const signedIn = await ask(page, { kind: 'yt-stream', id: 8, videoId: 'needsignin0', fresh: true })
  check(!!signedIn.value?.url, 'со входом браузера — ссылка')
  const tries = log().filter((c) => c.id === 'needsignin0')
  check(tries.at(-2)?.cookies === false && tries.at(-1)?.cookies === true, 'сначала без входа, потом с куками')
  check(!fs.readdirSync(path.join(home, 'yt-dlp', 'tmp')).length, 'файл с куками удалён после запуска')
  const liveSignIn = await ask(page, { kind: 'yt-stream', id: 9, videoId: 'livesignin1' })
  check(liveSignIn.value?.live === true, 'трансляция, о которой сайт не знал, — со входом браузера')
  check(runs('livesignin1') === 'гость:файл вход:файл вход:эфир', `гостем отказ, со входом как файл — нет форматов, тогда как трансляция (${runs('livesignin1')})`)

  console.log('Статус в Discord')
  const track = { title: 'Тестовый трек', artist: 'Masaki Band', album: 'Альбом для Discord', artwork: 'https://example.com/cover.jpg', playing: true, duration: 200, position: 10 }
  await post(page, { kind: 'presence', track })
  const act = await waitFor(() => activities.findLast((a) => a?.details === 'Тестовый трек'), 'статус в Discord', 20000)
  check(handshake?.client_id === '1234567890', 'помощник представился приложением Discord')
  check(act.type === 2 && act.state === 'Masaki Band', '«Слушает»: трек и исполнитель')
  check(act.assets?.large_image === 'https://example.com/cover.jpg' && act.assets?.large_text === 'Альбом для Discord', 'обложка и альбом')
  check(act.timestamps?.end - act.timestamps?.start === 200_000, 'полоска времени трека')
  check(act.buttons?.[0]?.url?.startsWith('https://youcloud.'), 'кнопка «Открыть YouCloud»')

  let before = activities.length
  await post(page, { kind: 'presence', track: { ...track, playing: false } })
  await waitFor(() => activities.length > before && activities.at(-1) === null, 'снятие статуса на паузе')
  check(true, 'на паузе статус снят')
  await post(page, { kind: 'presence', track: { ...track, position: 50 } })
  await waitFor(() => activities.at(-1)?.details === 'Тестовый трек', 'статус после паузы')
  check(true, 'продолжили — статус вернулся')

  before = activities.length
  await page.goto('about:blank')
  await waitFor(() => activities.length > before && activities.at(-1) === null, 'снятие статуса, когда страница ушла')
  check(true, 'страница ушла — статус снят')
  await waitFor(() => discordClosed, 'помощник отпущен, когда YouCloud закрыт', 30000)
  check(true, 'YouCloud закрыт — расширение отпустило помощника')
} catch (e) {
  failures.push(e.message)
  console.error('ОШИБКА:', e.message)
} finally {
  await ctx?.close().catch(() => {})
  stopDev()
  discord.close()
  console.log('Удаление')
  try {
    execFileSync(exe, ['--uninstall'], { env })
    await sleep(500)
    check(!fs.existsSync(home), 'папка помощника удалена')
    if (win) {
      let gone = false
      try {
        execFileSync('reg', ['query', `HKCU\\Software\\Chromium\\NativeMessagingHosts\\${NAME}`], { stdio: 'ignore' })
      } catch {
        gone = true
      }
      check(gone, 'браузеры забыли о помощнике (реестр)')
    }
  } catch (e) {
    failures.push('удаление: ' + e.message)
  }
  fs.rmSync(tmp, { recursive: true, force: true, maxRetries: 5 })
}

console.log(failures.length ? `\nПровалов: ${failures.length}` : '\nВсё прошло')
process.exit(failures.length ? 1 : 0)
