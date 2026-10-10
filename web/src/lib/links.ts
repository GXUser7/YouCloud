// A track's page on its service, as friends' "now playing" and profile showcases carry it, turned
// back into something to play or open — as the app's SharedLinks opens shared links.

import * as soundcloud from './services/soundcloud'
import * as yandex from './services/yandex'
import * as youtube from './services/youtube'
import { go, playQueue } from './state.svelte'
import { reason, toast } from './toast.svelte'

export function videoIdOf(url: URL): string | null {
  const host = url.hostname.replace(/^(www|m)\./, '')
  if (host === 'youtu.be') return url.pathname.slice(1, 12) || null
  if (host === 'youtube.com' || host === 'music.youtube.com') return url.searchParams.get('v')
  return null
}

/** Plays (or opens) what [link] points at; [context] names where it came from. */
export async function openLink(link: string, context = ''): Promise<void> {
  try {
    const url = new URL(link)
    const host = url.hostname.replace(/^(www|m)\./, '')
    if (host === 'soundcloud.com' || host === 'on.soundcloud.com') {
      const found = await soundcloud.resolve(link)
      if (!found) throw new Error('SoundCloud не узнал ссылку')
      if (found.kind === 'track') playQueue([found.id], 0, context, null, { kind: 'related' })
      else go(`${found.kind === 'set' ? 'set' : 'artist'}/${found.id}`)
      return
    }
    if (host.startsWith('music.yandex.')) {
      const track = url.pathname.match(/\/track\/(\d+)/)?.[1]
      const album = url.pathname.match(/\/album\/(\d+)/)?.[1]
      if (track) {
        const id = await yandex.trackByKey(album ? `${track}:${album}` : track)
        if (!id) throw new Error('Яндекс не нашёл трек')
        playQueue([id], 0, context)
      } else if (album) go(`set/ya:al:${album}`)
      else throw new Error('ссылка Яндекса без трека')
      return
    }
    const videoId = videoIdOf(url)
    if (videoId) {
      const id = await youtube.trackOf(videoId)
      if (!id) throw new Error('YouTube не отдал трек')
      playQueue([id], 0, context, null, { kind: 'related' })
      return
    }
    throw new Error('ссылка на неизвестный сервис')
  } catch (e) {
    toast(`Не получилось включить: ${reason(e)}`)
  }
}
