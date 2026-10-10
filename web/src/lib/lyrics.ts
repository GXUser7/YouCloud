// Synced lyrics, as the app finds them: Yandex's own for its tracks, otherwise LRCLIB, an open
// library matched by artist, title and length.

import type { Track } from './catalog'
import * as yandex from './services/yandex'
import * as youtube from './services/youtube'

export type Line = { at: number; text: string }

const TIME_TAG = /\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?\]/g

export function parseLrc(lrc: string): Line[] {
  const lines: Line[] = []
  for (const raw of lrc.split(/\r?\n/)) {
    const tags = [...raw.matchAll(TIME_TAG)]
    if (!tags.length) continue
    const text = raw.slice(tags[tags.length - 1].index! + tags[tags.length - 1][0].length).trim()
    for (const [, min, sec, frac = ''] of tags) {
      const ms = frac.length === 1 ? Number(frac) * 100 : frac.length === 2 ? Number(frac) * 10 : Number(frac.slice(0, 3) || 0)
      lines.push({ at: Number(min) * 60 + Number(sec) + ms / 1000, text })
    }
  }
  return lines.sort((a, b) => a.at - b.at)
}

// What uploads add in brackets that isn't the song's name.
const NOISE =
  /\s*[(\[](?:[^)\]]*\b(?:official|video|audio|lyrics?|clip|visuali[sz]er|hd|hq|4k|mv|free\s*(?:dl|download)|prod\.?|remaster(?:ed)?|explicit|клип|премьера)\b[^)\]]*)[)\]]/gi
const FEAT = /\s*[(\[]?\s*\b(?:feat\.?|ft\.?|featuring)\s+[^)\]]*[)\]]?/gi
const VERSION = /\s+[-–—]\s+(?:\d{4}\s+)?(?:remaster(?:ed)?|live|radio edit|single version|mono|stereo).*$/i

const clean = (text: string) => text.replace(VERSION, '').replace(NOISE, '').replace(FEAT, '').replace(/\s{2,}/g, ' ').trim()

/** Who and what to ask LRCLIB for; on SoundCloud the title often names the artist ("Artist - Title"). */
function guesses(t: Track): [string, string][] {
  const title = clean(t.title)
  const credited: [string, string] | null = t.artists[0] && title ? [clean(t.artists[0].name), title] : null
  const dash = title.match(/\s+[-–—]\s+/)
  const halves: [string, string] | null =
    dash && dash.index ? [title.slice(0, dash.index).trim(), title.slice(dash.index + dash[0].length).trim()] : null
  const list = t.service === 'sc' ? [halves, credited] : [credited, halves]
  return list.filter((x): x is [string, string] => !!x && !!x[0] && !!x[1])
}

async function lrclib(t: Track): Promise<string | null> {
  for (const [artist, title] of guesses(t)) {
    const url = `https://lrclib.net/api/search?${new URLSearchParams({ track_name: title, artist_name: artist })}`
    const results: { syncedLyrics?: string | null; duration?: number }[] = await fetch(url).then((r) => (r.ok ? r.json() : []))
    const fitting = results
      .filter((r) => r.syncedLyrics)
      .map((r) => ({ lrc: r.syncedLyrics!, gap: r.duration && t.duration ? Math.abs(r.duration - t.duration) : 0 }))
      .filter((r) => r.gap <= 4) // further apart, it is another cut of the song
      .sort((a, b) => a.gap - b.gap)
    if (fitting[0]) return fitting[0].lrc
  }
  return null
}

export type Lyrics = { lines: Line[]; source: string; synced: boolean }

const cache = new Map<string, Promise<Lyrics | null>>()

export function lyricsFor(t: Track): Promise<Lyrics | null> {
  let found = cache.get(t.id)
  if (!found) {
    found = (async () => {
      const own = t.service === 'ya' ? await yandex.lyrics(t) : null
      if (own) {
        const lines = parseLrc(own)
        if (lines.length) return { lines, source: 'Яндекс Музыка', synced: true }
      }
      const open = await lrclib(t).catch(() => null)
      const lines = open ? parseLrc(open) : []
      if (lines.length) return { lines, source: 'LRCLIB', synced: true }
      // YouTube Music's own lyrics come without timings: shown whole, not followed line by line.
      const plain = t.yt ? await youtube.lyricsText(t) : null
      if (plain) {
        return {
          lines: plain.split(/\r?\n/).map((text) => ({ at: -1, text: text.trim() })),
          source: 'YouTube Music',
          synced: false,
        }
      }
      return null
    })()
    cache.set(t.id, found)
  }
  return found
}
