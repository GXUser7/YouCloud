// Signing in, without copying tokens. SoundCloud: the extension reads the session of soundcloud.com
// in this browser (its oauth_token cookie); signed out there, it opens SoundCloud's sign-in and
// takes the token the site's own requests carry once the listener is in — as the app's login
// WebView does. Yandex: the extension opens Yandex ID and catches the token it hands back.

import { accounts, setSoundCloud, setYandex } from './accounts.svelte'
import type { Service } from './catalog'
import { extension, extensionCall, onHostEvent, onToken } from './extension'
import * as soundcloud from './services/soundcloud'
import * as yandex from './services/yandex'
import * as youtube from './services/youtube'
import { reason, toast } from './toast.svelte'

type Waiter = { resolve: () => void; reject: (e: unknown) => void }
const waiting: Record<'sc' | 'ya', Waiter | null> = { sc: null, ya: null }

async function useSoundCloud(token: string) {
  const me = await soundcloud.me(token)
  setSoundCloud({ token, id: me.id, name: me.username, avatar: soundcloud.art(me.avatar_url, 't200x200') })
  return me.username
}

async function useYandex(token: string) {
  const a = await yandex.account(token)
  setYandex({ token, uid: a.uid, name: a.name, plus: a.plus })
  return a.name
}

// A token can arrive after a reload too: the extension holds it until a YouCloud page listens.
function caught(service: 'sc' | 'ya', use: (token: string) => Promise<string>, known: () => string | undefined) {
  onToken(service, async (token) => {
    if (known() === token) return
    const waiter = waiting[service]
    waiting[service] = null
    try {
      const name = await use(token)
      if (waiter) waiter.resolve()
      else toast(`${service === 'sc' ? 'SoundCloud' : 'Яндекс'} подключён: ${name}`)
    } catch (e) {
      if (waiter) waiter.reject(e)
      else toast(`Вход не удался: ${reason(e)}`)
    }
  })
}
caught('sc', useSoundCloud, () => accounts.sc?.token)
caught('ya', useYandex, () => accounts.ya?.token)

/** Opens the service's sign-in in a new tab; done when the extension has caught the token. */
async function loginInTab(service: 'sc' | 'ya'): Promise<void> {
  const done = new Promise<void>((resolve, reject) => {
    waiting[service] = { resolve, reject }
    setTimeout(() => {
      if (waiting[service]?.resolve === resolve) {
        waiting[service] = null
        reject(new Error('вход не завершился за 10 минут'))
      }
    }, 10 * 60_000)
  })
  await extensionCall(service === 'sc' ? 'sc-login' : 'ya-login')
  return done
}

export async function connectSoundCloud(pasted?: string): Promise<void> {
  if (pasted?.trim()) {
    await useSoundCloud(pasted.trim())
    return
  }
  if (!(await extension())) throw new Error('Нужно расширение YouCloud — или вставь токен вручную')
  const session = await extensionCall<string | null>('sc-token')
  if (session) {
    try {
      await useSoundCloud(session)
      return
    } catch {
      // A stale cookie: signing in again on soundcloud.com gives a fresh token.
    }
  }
  return loginInTab('sc')
}

export async function connectYandex(): Promise<void> {
  if (!(await extension())) throw new Error('Для Яндекса нужно расширение YouCloud')
  return loginInTab('ya')
}

/** YouTube is signed in as this browser is: whether it is, checked now and whenever the tab comes back. */
export async function refreshYouTube() {
  accounts.yt = await youtube.signedIn()
  accounts.ytName = accounts.yt ? await youtube.accountName().catch(() => null) : null
}
refreshYouTube()
addEventListener('focus', () => refreshYouTube())
// Masaki says when Google's sign-in lands, without waiting for the tab to be focused again.
onHostEvent((e) => e.event === 'session' && e.service === 'yt' && refreshYouTube())

/** Opens Google's sign-in for YouTube; the session counts as soon as the listener is back here. */
export async function connectYouTube(): Promise<void> {
  if (!(await extension())) throw new Error('Для YouTube нужно расширение YouCloud')
  await extensionCall('yt-login')
}

export async function connect(svc: Service): Promise<void> {
  if (svc === 'sc') return connectSoundCloud()
  if (svc === 'ya') return connectYandex()
  return connectYouTube()
}

export function disconnect(svc: Service) {
  if (svc === 'sc') setSoundCloud(null)
  if (svc === 'ya') setYandex(null)
}
