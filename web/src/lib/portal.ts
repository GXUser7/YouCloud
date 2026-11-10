import type { Action } from 'svelte/action'

/** Menus must escape page masks, transforms and backdrop-filter roots. */
export const portal: Action<HTMLElement> = (node) => {
  document.body.append(node)
  return { destroy: () => node.remove() }
}
