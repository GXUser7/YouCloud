// SoundCloud's covers and sound come from its own CDNs (sndcdn.com, soundcloud.cloud), which the
// site reads straight from the listener's browser — fastest, and nothing passes Cloudflare. Where
// SoundCloud is blocked (a PC without a VPN) they don't load at all, so then they go through the
// Worker like the API does. Which way works is found out once a visit: a cheap probe, and any
// cover or stream that fails directly flips the switch for the rest.

import { viaWorker } from './net'

const CDN = /(^|\.)(sndcdn\.com|soundcloud\.cloud)$/

export const media = $state({ viaWorker: false })

const isCdn = (url: string) => {
  try {
    return CDN.test(new URL(url).hostname)
  } catch {
    return false
  }
}

/** [url] as the listener's browser can reach it: through the Worker where SoundCloud is blocked. */
export function reach(url: string): string
export function reach(url: string | null | undefined): string | null
export function reach(url: string | null | undefined): string | null {
  if (!url) return null
  return media.viaWorker && isCdn(url) ? viaWorker(url) : url
}

/**
 * Whether the page may read [url]'s sound, not only play it — the full player's wave listens to
 * it. The Worker is the site's own; SoundCloud's CDNs allow it (hls.js reads their playlists and
 * pieces so anyway, and Chrome now plays their HLS itself). Other services' links are played
 * without asking: one that doesn't allow it wouldn't play at all if asked.
 */
export function readable(url: string): boolean {
  try {
    const u = new URL(url, location.href)
    return u.origin === location.origin || CDN.test(u.hostname)
  } catch {
    return false
  }
}

/** A direct cover or stream failed: when it was SoundCloud's CDN, everything goes through the Worker
 *  from now on. True when that changed anything, so the caller can try once more. */
export function directFailed(url: string | null | undefined): boolean {
  if (!url || media.viaWorker || !isCdn(url)) return false
  media.viaWorker = true
  return true
}

// The probe: any answer at all, even an error page, means the CDN is reachable. A block shows as
// a network error or as no answer.
fetch('https://i1.sndcdn.com/', { mode: 'no-cors', cache: 'no-store', signal: AbortSignal.timeout(4000) }).catch(() => {
  media.viaWorker = true
})
