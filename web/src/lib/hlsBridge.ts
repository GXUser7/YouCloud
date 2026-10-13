// hls.js's way out for a YouTube broadcast: its playlist and its pieces asked through the extension
// (or Masaki), from the listener's own IP, as yt-dlp and the app fetch them. The page can't take
// them itself: googlevideo answers across sites only YouTube's own pages, and Chrome's own HLS
// takes no pieces from another host than the playlist's (rr…googlevideo.com, the playlist being on
// manifest.googlevideo.com) but by CORS.

import type { HlsConfig, Loader, LoaderCallbacks, LoaderConfiguration, LoaderContext, LoaderStats } from 'hls.js'
import { extensionFetch } from './extension'

const freshStats = (): LoaderStats => ({
  aborted: false,
  loaded: 0,
  retry: 0,
  total: 0,
  chunkCount: 0,
  bwEstimate: 0,
  loading: { start: 0, first: 0, end: 0 },
  parsing: { start: 0, end: 0 },
  buffering: { start: 0, first: 0, end: 0 },
})

export class BridgeLoader implements Loader<LoaderContext> {
  context: LoaderContext | null = null
  stats: LoaderStats = freshStats()
  private done = true
  private timer = 0

  constructor(_config: HlsConfig) {}

  load(context: LoaderContext, config: LoaderConfiguration, callbacks: LoaderCallbacks<LoaderContext>) {
    this.context = context
    this.done = false
    const stats = (this.stats = freshStats())
    stats.loading.start = performance.now()
    const binary = context.responseType === 'arraybuffer'
    const headers: Record<string, string> = { ...context.headers }
    if (context.rangeEnd) headers.range = `bytes=${context.rangeStart ?? 0}-${context.rangeEnd - 1}`
    const limit = config.loadPolicy.maxLoadTimeMs
    this.timer = window.setTimeout(() => {
      if (this.finish()) callbacks.onTimeout(stats, context, null)
    }, limit)
    extensionFetch(context.url, { headers, binary, timeoutMs: limit + 5000 })
      .then(async (res) => {
        if (this.done) return
        stats.loading.first = Math.max(performance.now(), stats.loading.start)
        const data = res.ok ? (binary ? await res.arrayBuffer() : await res.text()) : null
        if (!this.finish()) return
        if (data === null) {
          callbacks.onError({ code: res.status, text: res.statusText || `HTTP ${res.status}` }, context, null, stats)
          return
        }
        stats.loaded = stats.total = typeof data === 'string' ? data.length : data.byteLength
        stats.loading.end = Math.max(performance.now(), stats.loading.first)
        callbacks.onSuccess({ url: context.url, data, code: res.status }, stats, context, null)
      })
      .catch((e) => {
        if (this.finish()) callbacks.onError({ code: 0, text: String((e as Error)?.message ?? e) }, context, null, stats)
      })
  }

  /** Ends the load in hand: true when it hadn't ended already. */
  private finish(): boolean {
    if (this.done) return false
    this.done = true
    clearTimeout(this.timer)
    return true
  }

  abort() {
    if (this.finish()) this.stats.aborted = true
  }

  destroy() {
    this.abort()
    this.context = null
  }
}
