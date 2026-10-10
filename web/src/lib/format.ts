export function time(seconds: number): string {
  const s = Math.max(0, Math.floor(seconds))
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`
}

/** "8 треков", "1 трек", "22 трека". */
export function plural(n: number, one: string, few: string, many: string): string {
  const mod10 = n % 10
  const mod100 = n % 100
  const word = mod10 === 1 && mod100 !== 11 ? one : mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14) ? few : many
  return `${n} ${word}`
}

export const tracksCount = (n: number) => plural(n, 'трек', 'трека', 'треков')

export function totalMinutes(seconds: number): string {
  const m = Math.round(seconds / 60)
  return plural(m, 'минута', 'минуты', 'минут')
}

/** A short deterministic number from a string, for covers that must look the same every time. */
export function hash(text: string): number {
  let h = 2166136261
  for (let i = 0; i < text.length; i++) {
    h ^= text.charCodeAt(i)
    h = Math.imul(h, 16777619)
  }
  return h >>> 0
}

export function rng(seed: string) {
  let a = hash(seed) || 1
  return () => {
    a ^= a << 13
    a ^= a >>> 17
    a ^= a << 5
    return ((a >>> 0) % 10000) / 10000
  }
}
