// Rotor's exclusion queue describes what was heard, not the tracks still waiting to play.
export interface RadioHeard {
  id: string
  key: string
  at: number
}

const MONTH = 30 * 24 * 60 * 60_000
const LIMIT = 1000

/** Oldest first, with the most recently heard track last, including after a replay. */
export function radioHistory(value: unknown, now = Date.now()): RadioHeard[] {
  if (!Array.isArray(value)) return []
  const latest = new Map<string, RadioHeard>()
  for (const entry of value) {
    if (!entry || typeof entry.id !== 'string' || !/^ya:t:[^:]+$/.test(entry.id) || typeof entry.key !== 'string') continue
    if (entry.key.split(':')[0] !== entry.id.slice(5) || !Number.isFinite(entry.at) || entry.at > now || now - entry.at >= MONTH) continue
    if (!latest.has(entry.id) || latest.get(entry.id)!.at <= entry.at) latest.set(entry.id, { id: entry.id, key: entry.key, at: entry.at })
  }
  return [...latest.values()].sort((a, b) => a.at - b.at).slice(-LIMIT)
}

export function rememberRadioTrack(history: RadioHeard[], id: string, key: string, now = Date.now()): RadioHeard[] {
  return radioHistory([...history.filter((entry) => entry.id !== id), { id, key, at: now }], now)
}

export function radioQueue(history: RadioHeard[], now = Date.now()): string[] {
  return radioHistory(history, now).map((entry) => entry.key)
}

export interface TrackBatch {
  batchId?: string
  tracks: string[]
}

/** A repeat is a last resort after four batches, never a duplicate of the player's queue. */
export async function freshRadioTracks(
  first: TrackBatch | null,
  more: () => Promise<TrackBatch>,
  exclusions: () => { history: RadioHeard[]; queued: string[]; disliked: ReadonlySet<string> },
  clock: number | (() => number) = Date.now,
): Promise<{ tracks: string[]; batchOf: Record<string, string | undefined> }> {
  const repeats: { id: string; batchId?: string }[] = []
  for (let attempt = 0; attempt < 4; attempt++) {
    const batch = attempt === 0 && first ? first : await more()
    const { history, queued, disliked } = exclusions()
    const now = typeof clock === 'function' ? clock() : clock
    const heard = new Set(radioHistory(history, now).map((entry) => entry.id))
    const have = new Set(queued)
    const tracks: string[] = []
    for (const id of batch.tracks) {
      if (have.has(id) || disliked.has(id)) continue
      have.add(id)
      if (heard.has(id)) repeats.push({ id, batchId: batch.batchId })
      else tracks.push(id)
    }
    if (tracks.length) return { tracks, batchOf: Object.fromEntries(tracks.map((id) => [id, batch.batchId])) }
  }
  // Re-read the queue: playback or a dislike may have changed it while a batch was loading.
  const { queued, disliked } = exclusions()
  const have = new Set(queued)
  const batchOf: Record<string, string | undefined> = {}
  const tracks = repeats.filter(({ id, batchId }) => {
    if (have.has(id) || disliked.has(id)) return false
    have.add(id)
    batchOf[id] = batchId
    return true
  }).map(({ id }) => id)
  return { tracks, batchOf }
}

/** A skip near the end is still a skip. Only the media's ended event finishes a track. */
export const radioEndType = (ended: boolean): 'trackFinished' | 'skip' => ended ? 'trackFinished' : 'skip'
