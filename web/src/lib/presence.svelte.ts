// The status in Discord, "Listening to YouCloud", where the helper (web/helper) is installed: what
// plays goes to it through the extension, and it shows that in the Discord app on this computer.
// Only changes go — the track, a pause, a jump — not every tick of the position: Discord counts
// the time bar itself. Masaki reads the page's Media Session for its own status, so there the site
// says nothing.

import { currentTrack, player, remember, stored } from './state.svelte'
import { extensionHost, helper, tellPresence, type PresenceTrack } from './extension'

export const discord = $state({ on: stored('discord', false) })

export function setDiscord(on: boolean) {
  discord.on = on
  remember('discord', on)
}

let told = ''

/** Tells the helper what plays, when that changed. Also called once the helper turns up. */
export async function refreshPresence() {
  // Asking for the helper also wakes it: yt-dlp gets ready before the first YouTube track.
  if (extensionHost() === 'masaki' || !(await helper())) return
  const t = currentTrack()
  const track: PresenceTrack | null =
    discord.on && t
      ? {
          title: t.title,
          artist: t.artists.map((a) => a.name).join(', '),
          album: t.album?.title ?? '',
          // Discord fetches the cover itself: the service's own address, not the Worker's.
          artwork: t.cover && /^https:\/\//.test(t.cover) ? t.cover : '',
          playing: player.playing,
          duration: player.live ? null : player.duration || t.duration || null,
          position: player.live ? null : player.position,
        }
      : null
  const key = JSON.stringify(track && [t!.id, track.playing, player.seeks, Math.round(track.duration ?? 0)])
  if (key === told) return
  told = key
  tellPresence(track)
}

$effect.root(() => {
  $effect(() => {
    void currentTrack()?.id
    void player.playing
    void player.seeks
    void player.duration
    void player.live
    void discord.on
    void refreshPresence()
  })
})
