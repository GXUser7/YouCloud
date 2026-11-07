// The queue as its lists show it: the whole of it, not only what comes — the tracks already
// played stand above the one playing (dimmed), so any of them can be picked again from the list,
// not only by «Предыдущий». Positions are the queue's own, so a pick jumps to exactly that one.

import { player } from './state.svelte'

/** How many played tracks a list keeps above the one playing, and how many to come under it. */
const BEHIND = 50

/** The part of the queue a list shows: [from] is the first one's position in the queue. */
export function queueWindow(ahead: number): { from: number; ids: string[] } {
  const from = Math.max(0, player.index - BEHIND)
  return { from, ids: player.queue.slice(from, player.index + 1 + ahead) }
}

/**
 * Once the list is drawn, its own scroll (the list's, never the page's) is set so the track
 * playing — the child marked data-now — stands at its top, [pad] under it; what was played is
 * above, a scroll away.
 */
export function revealNow(list: HTMLElement, pad = 8) {
  const frame = requestAnimationFrame(() => {
    const now = list.querySelector<HTMLElement>('[data-now]')
    if (!now) return
    let box: HTMLElement | null = now.parentElement
    while (box && box !== document.body && !/(auto|scroll)/.test(getComputedStyle(box).overflowY)) box = box.parentElement
    if (!box || box === document.body) return
    box.scrollTop += now.getBoundingClientRect().top - box.getBoundingClientRect().top - pad
  })
  return { destroy: () => cancelAnimationFrame(frame) }
}
