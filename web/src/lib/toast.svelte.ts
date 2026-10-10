// Short messages above the player — what went wrong, what just happened — as the app's toasts.

export const toasts = $state<{ id: number; text: string }[]>([])

let seq = 0

export function toast(text: string, ms = 4500) {
  const id = ++seq
  toasts.push({ id, text })
  setTimeout(() => {
    const i = toasts.findIndex((t) => t.id === id)
    if (i >= 0) toasts.splice(i, 1)
  }, ms)
}

export const reason = (e: unknown) => (e instanceof Error ? e.message : String(e))
