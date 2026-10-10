// Material 3 Expressive shapes as SVG paths in a 100×100 box.
//
// Every shape is a radius around the centre sampled at the same angles, so any two paths have the
// same commands and CSS can morph one into the other (`d` transitions), the way the app morphs its
// MaterialShapes. The radius is 1 at the shape's outermost points.

export type ShapeName =
  | 'circle'
  | 'square'
  | 'cookie4'
  | 'cookie6'
  | 'cookie7'
  | 'cookie9'
  | 'cookie12'
  | 'sunny'
  | 'softBurst'
  | 'clover4'
  | 'clover8'
  | 'flower'
  | 'puffy'
  | 'pentagon'
  | 'triangle'
  | 'gem'

type Radius = (t: number) => number

const TAU = Math.PI * 2

/** [n] bumps; [p] above 1 makes broad lobes with narrow notches, below 1 pointed tips. */
const lobes = (n: number, depth: number, p = 1): Radius => (t) =>
  1 - depth * Math.pow((1 - Math.cos(n * t)) / 2, p)

const superellipse = (e: number): Radius => (t) =>
  Math.pow(Math.pow(Math.abs(Math.cos(t)), e) + Math.pow(Math.abs(Math.sin(t)), e), -1 / e)

/** A regular polygon with a vertex at the top; its corners are rounded by [smooth]. */
const polygon = (n: number): Radius => (t) => {
  const seg = TAU / n
  const a = (((t % seg) + seg) % seg) - seg / 2
  return Math.cos(Math.PI / n) / Math.cos(a)
}

const shapes: Record<ShapeName, { r: Radius; round?: number }> = {
  circle: { r: () => 1 },
  square: { r: superellipse(5) },
  cookie4: { r: lobes(4, 0.16, 1.4) },
  cookie6: { r: lobes(6, 0.14, 1.2) },
  cookie7: { r: lobes(7, 0.12, 1.1) },
  cookie9: { r: lobes(9, 0.1) },
  cookie12: { r: lobes(12, 0.075) },
  sunny: { r: lobes(8, 0.13, 0.75) },
  softBurst: { r: lobes(10, 0.2, 0.7) },
  clover4: { r: lobes(4, 0.42, 2.4) },
  clover8: { r: lobes(8, 0.3, 2.2) },
  flower: { r: lobes(8, 0.2, 1.6) },
  puffy: { r: lobes(6, 0.16, 2.6) },
  pentagon: { r: polygon(5), round: 9 },
  triangle: { r: polygon(3), round: 13 },
  gem: { r: polygon(6), round: 7 },
}

export const SAMPLES = 144

function circularAverage(values: number[], window: number): number[] {
  const half = Math.floor(window / 2)
  return values.map((_, i) => {
    let sum = 0
    for (let k = -half; k <= half; k++) sum += values[(i + k + values.length) % values.length]
    return sum / (half * 2 + 1)
  })
}

const radiusCache = new Map<string, number[]>()

/** The radii of [name] at [SAMPLES] angles from the top, clockwise, scaled to an outermost 1. */
export function radii(name: ShapeName): number[] {
  const cached = radiusCache.get(name)
  if (cached) return cached
  const shape = shapes[name]
  let values = Array.from({ length: SAMPLES }, (_, i) => shape.r((TAU * i) / SAMPLES))
  if (shape.round) values = circularAverage(values, shape.round)
  const max = Math.max(...values)
  values = values.map((v) => v / max)
  radiusCache.set(name, values)
  return values
}

/** Radii for a wave of [n] lobes [depth] deep — the "Моя форма" shape, tuned by mood. */
export function waveRadii(n: number, depth: number): number[] {
  return Array.from({ length: SAMPLES }, (_, i) => lobes(n, depth)((TAU * i) / SAMPLES))
}

export function pathFromRadii(values: number[], scale = 1, rotateDeg = 0): string {
  const rot = (rotateDeg * Math.PI) / 180 - Math.PI / 2
  let d = ''
  for (let i = 0; i < values.length; i++) {
    const a = (TAU * i) / values.length + rot
    const r = 50 * scale * values[i]
    const x = 50 + r * Math.cos(a)
    const y = 50 + r * Math.sin(a)
    d += `${i === 0 ? 'M' : 'L'}${x.toFixed(2)} ${y.toFixed(2)}`
  }
  return d + 'Z'
}

export function shapePath(name: ShapeName, scale = 1, rotateDeg = 0): string {
  return pathFromRadii(radii(name), scale, rotateDeg)
}

/** For `clip-path: path()` on an element [size] px across. */
export function clipPath(name: ShapeName, size: number): string {
  const values = radii(name)
  let d = ''
  for (let i = 0; i < values.length; i++) {
    const a = (TAU * i) / values.length - Math.PI / 2
    const r = (size / 2) * values[i]
    d += `${i === 0 ? 'M' : 'L'}${(size / 2 + r * Math.cos(a)).toFixed(1)} ${(size / 2 + r * Math.sin(a)).toFixed(1)}`
  }
  return `path('${d}Z')`
}

export const COVER_SHAPES: ShapeName[] = [
  'cookie9',
  'clover4',
  'softBurst',
  'flower',
  'sunny',
  'pentagon',
  'puffy',
  'cookie6',
  'clover8',
  'gem',
  'cookie4',
  'triangle',
]
