// A title set as large as its place allows: the largest font size, up to [max], at which the text
// fits its element's width (no word sticking out) and [height] — one line or several, whatever
// that size makes of it. Measured again when the text changes, the window resizes or the fonts
// arrive.

import type { Action } from 'svelte/action'

export type FitOptions = {
  /** What is set: the action measures again when it changes. */
  text: string
  max: number
  min?: number
  /** The most the text may take up and down, in px; asked again on every measure. */
  height: () => number
}

export const fit: Action<HTMLElement, FitOptions> = (node, initial) => {
  let opts = initial

  const fits = (limit: number) => node.scrollHeight <= limit + 1 && node.scrollWidth <= node.clientWidth + 1

  function measure() {
    const limit = opts.height()
    let lo = opts.min ?? 14
    let hi = opts.max
    node.style.fontSize = `${hi}px`
    if (fits(limit)) return
    for (let i = 0; i < 10; i++) {
      const mid = (lo + hi) / 2
      node.style.fontSize = `${mid}px`
      if (fits(limit)) lo = mid
      else hi = mid
    }
    node.style.fontSize = `${Math.floor(lo)}px`
  }

  let frame = 0
  const later = () => {
    cancelAnimationFrame(frame)
    frame = requestAnimationFrame(measure)
  }

  let lastWidth = -1
  const observer = new ResizeObserver(([entry]) => {
    if (Math.abs(entry.contentRect.width - lastWidth) < 0.5) return
    lastWidth = entry.contentRect.width
    later()
  })
  observer.observe(node)
  measure()
  document.fonts?.ready.then(later)
  addEventListener('resize', later)

  return {
    update(next) {
      opts = next
      measure()
    },
    destroy() {
      cancelAnimationFrame(frame)
      observer.disconnect()
      removeEventListener('resize', later)
    },
  }
}
