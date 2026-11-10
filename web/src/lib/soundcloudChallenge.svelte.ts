// A challenge is always completed by the listener in SoundCloud's browser tab. The pending
// write resumes only after an explicit retry, once; cancellation also settles every caller.
import { tr } from './i18n.svelte'
import { extensionCall } from './extension'

export const soundcloudChallenge = $state({
  open: false,
  url: '',
  setup: false,
  busy: false,
  error: '',
})

const waiting = new Set<(retry: boolean) => void>()
let expiry: ReturnType<typeof setTimeout> | undefined

export function challengeUrl(status: number, body: string): string | null {
  if (status !== 403) return null
  try {
    const url = new URL(JSON.parse(body).url)
    if (url.protocol !== 'https:' || url.username || url.password || url.port) return null
    if (url.hostname !== 'captcha-delivery.com' && !url.hostname.endsWith('.captcha-delivery.com')) return null
    return url.href
  } catch { return null }
}

export function soundCloudSetup() {
  if (waiting.size) return
  Object.assign(soundcloudChallenge, { open: true, url: '', setup: true, busy: false, error: '' })
}

export function solveSoundCloud(url: string): Promise<boolean> {
  Object.assign(soundcloudChallenge, { open: true, url, setup: false, busy: false, error: '' })
  clearTimeout(expiry)
  expiry = setTimeout(() => finishSoundCloud(false), 10 * 60_000)
  return new Promise((resolve) => waiting.add(resolve))
}

export function finishSoundCloud(retry: boolean) {
  clearTimeout(expiry)
  soundcloudChallenge.open = false
  for (const resolve of waiting) resolve(retry)
  waiting.clear()
}

export async function openSoundCloudChallenge() {
  if (soundcloudChallenge.busy || !soundcloudChallenge.url) return
  soundcloudChallenge.busy = true
  soundcloudChallenge.error = ''
  try {
    await extensionCall('sc-captcha', 40_000, { url: soundcloudChallenge.url })
  } catch (e) {
    soundcloudChallenge.error = e instanceof Error ? e.message : tr('Не удалось открыть проверку')
  } finally { soundcloudChallenge.busy = false }
}
