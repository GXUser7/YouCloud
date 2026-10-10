// Everything the pages show — tracks, sets, artists — in one shape for all three services, kept by
// id. Each service's module turns its own answers into these and puts them here; components look
// them up by id, so a track met on one page is the same object on every other.
//
// Ids say where a thing is from: `sc:t:123` is a SoundCloud track, `ya:al:42` a Yandex album.

export type Service = 'sc' | 'ya' | 'yt'

export const SERVICES: { id: Service; name: string; short: string }[] = [
  { id: 'sc', name: 'SoundCloud', short: 'SoundCloud' },
  { id: 'ya', name: 'Яндекс Музыка', short: 'Яндекс' },
  { id: 'yt', name: 'YouTube Music', short: 'YouTube' },
]

export const serviceName = (s: Service) => SERVICES.find((x) => x.id === s)!.name

export interface ArtistRef {
  id: string
  name: string
}

export interface Track {
  id: string
  service: Service
  title: string
  artists: ArtistRef[]
  /** Seconds. */
  duration: number
  cover?: string | null
  /** Draws a cover when there is none, and colours the page until the cover's own colour is known. */
  color: string
  album?: { id: string; title: string }
  explicit?: boolean
  /** The track's page on its service. */
  link?: string
  /** False for a track the service won't play (withdrawn, not licensed here). */
  playable?: boolean
  /** What SoundCloud needs to play it. */
  sc?: { transcodings: ScTranscoding[]; auth?: string }
  /** What Yandex needs to play it. */
  /** ugc: a file its owner uploaded to Yandex Music — Yandex plays it for the owner alone. */
  ya?: { trackId: string; albumId?: string; ugc?: boolean }
  /** What YouTube needs to play it. */
  yt?: { videoId: string }
}

export interface ScTranscoding {
  url: string
  preset: string
  snipped: boolean
  format: { protocol: string; mime_type: string }
}

export type SetKind = 'album' | 'single' | 'playlist' | 'mix' | 'station' | 'genre' | 'liked' | 'personal'

export interface TrackSet {
  id: string
  kind: SetKind
  service: Service
  title: string
  subtitle: string
  cover?: string | null
  color: string
  /** Track ids; empty until the set is opened, when it hasn't come with them. */
  tracks: string[]
  loaded?: boolean
  total?: number
  label?: string
  year?: number
  artist?: ArtistRef
  link?: string
}

export interface Artist {
  id: string
  name: string
  service: Service
  image?: string | null
  color: string
  followers?: string
  albums?: number
  link?: string
}

export const tracks = new Map<string, Track>()
export const sets = new Map<string, TrackSet>()
export const artists = new Map<string, Artist>()

export const track = (id: string) => tracks.get(id)!
export const set = (id: string) => sets.get(id)!
export const artist = (id: string) => artists.get(id)!

export function putTrack(t: Track): string {
  tracks.set(t.id, t)
  return t.id
}

/** Keeps the tracks a set already has when it comes again without them (as on a home page). */
export function putSet(s: TrackSet): string {
  const known = sets.get(s.id)
  sets.set(s.id, known?.loaded && !s.loaded ? { ...s, tracks: known.tracks, loaded: true } : s)
  return s.id
}

export function putArtist(a: Artist): string {
  artists.set(a.id, a)
  return a.id
}

export const artistNames = (t: Track) => t.artists.map((a) => a.name).join(', ')

export const serviceOf = (id: string): Service | null =>
  id.startsWith('sc:') ? 'sc' : id.startsWith('ya:') ? 'ya' : id.startsWith('yt:') ? 'yt' : null

// Colours for things without a cover, picked by id so each keeps its own.
const PALETTE = ['#a8673a', '#5c6bc0', '#00897b', '#d81b60', '#7e57c2', '#f9a825', '#039be5', '#6d4c41', '#43a047', '#e64a19', '#8e24aa', '#00acc1']

export function colorFor(id: string): string {
  let h = 0
  for (let i = 0; i < id.length; i++) h = (h * 31 + id.charCodeAt(i)) >>> 0
  return PALETTE[h % PALETTE.length]
}

export function countWords(n: number, one: string, few: string, many: string): string {
  const m10 = n % 10
  const m100 = n % 100
  return m10 === 1 && m100 !== 11 ? one : m10 >= 2 && m10 <= 4 && (m100 < 12 || m100 > 14) ? few : many
}

/** "5,4 млн", "48 тыс.", "812". */
export function compact(n: number): string {
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(n >= 10_000_000 ? 0 : 1).replace('.', ',').replace(',0', '')} млн`
  if (n >= 1_000) return `${(n / 1_000).toFixed(n >= 10_000 ? 0 : 1).replace('.', ',').replace(',0', '')} тыс.`
  return String(n)
}
