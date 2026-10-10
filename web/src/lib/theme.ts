// Colours from the cover, as the app's CoverTheme makes them: a Material 3 tonal-spot scheme from
// the track's colour, set on :root as custom properties. A change of track fades the whole page
// from one palette to the next.

import { argbFromHex, hexFromArgb, Hct, SchemeTonalSpot, type DynamicScheme } from '@material/material-color-utilities'
import { iconSvg } from './appIcon'

/** The app's own warm brown, for when nothing plays or covers don't colour the page. */
export const DEFAULT_SEED = '#a8794a'

const ROLES = [
  'primary',
  'onPrimary',
  'primaryContainer',
  'onPrimaryContainer',
  'primaryFixedDim',
  'secondary',
  'onSecondary',
  'secondaryContainer',
  'onSecondaryContainer',
  'tertiary',
  'onTertiary',
  'tertiaryContainer',
  'onTertiaryContainer',
  'error',
  'onError',
  'errorContainer',
  'onErrorContainer',
  'surface',
  'surfaceDim',
  'surfaceBright',
  'surfaceContainerLowest',
  'surfaceContainerLow',
  'surfaceContainer',
  'surfaceContainerHigh',
  'surfaceContainerHighest',
  'onSurface',
  'onSurfaceVariant',
  'outline',
  'outlineVariant',
  'inverseSurface',
  'inverseOnSurface',
  'inversePrimary',
] as const

type Role = (typeof ROLES)[number]

const cssName = (role: Role) => '--' + role.replace(/[A-Z]/g, (c) => '-' + c.toLowerCase())

let registered = false

function register() {
  if (registered) return
  registered = true
  const props = ROLES.map(cssName)
  const style = document.createElement('style')
  style.textContent = props.map((p) => `@property ${p} { syntax: '<color>'; inherits: true; initial-value: #000; }`).join('\n')
  document.head.appendChild(style)
}

const schemeCache = new Map<string, Record<string, string>>()

export function schemeColors(seed: string, dark: boolean): Record<string, string> {
  const key = seed + dark
  const cached = schemeCache.get(key)
  if (cached) return cached
  const scheme: DynamicScheme = new SchemeTonalSpot(Hct.fromInt(argbFromHex(seed)), dark, 0, '2025')
  const colors: Record<string, string> = {}
  for (const role of ROLES) colors[cssName(role)] = hexFromArgb((scheme as unknown as Record<Role, number>)[role])
  schemeCache.set(key, colors)
  return colors
}

let painted = false

export function applyTheme(seed: string, dark: boolean) {
  register()
  const root = document.documentElement
  const apply = () => {
    for (const [name, value] of Object.entries(schemeColors(seed, dark))) root.style.setProperty(name, value)
    root.dataset.theme = dark ? 'dark' : 'light'
    root.style.colorScheme = dark ? 'dark' : 'light'
  }
  // The fade is a view transition: the new palette is set at once and the GPU crossfades the page
  // from its picture in the old one (app.css). Fading the properties themselves restyled and
  // repainted every element for every frame of it — the stutter on each change of track.
  const fade = painted && !document.hidden && 'startViewTransition' in document && !matchMedia('(prefers-reduced-motion: reduce)').matches
  if (fade) document.startViewTransition(apply)
  else apply()
  painted = true
  paintFavicon(seed, dark)
}

/** Mixes two #rrggbb colours, [t] of the way from [a] to [b]. */
function mix(a: string, b: string, t: number): string {
  const ca = parseInt(a.slice(1), 16)
  const cb = parseInt(b.slice(1), 16)
  const ch = (shift: number) => Math.round(((ca >> shift) & 255) * (1 - t) + ((cb >> shift) & 255) * t)
  return '#' + [16, 8, 0].map((s) => ch(s).toString(16).padStart(2, '0')).join('')
}

let favicon: HTMLLinkElement | null = null

/** The browser tab's icon in the same colours as the page's: the dark theme's, which read on any tab bar. */
function paintFavicon(seed: string, dark: boolean) {
  const c = schemeColors(seed, true)
  const tile = c['--primary']
  const cloud = mix(tile, c['--on-primary'], 0.42)
  favicon ??= document.querySelector<HTMLLinkElement>('link[rel="icon"]')
  if (!favicon) return
  favicon.href = 'data:image/svg+xml,' + encodeURIComponent(iconSvg(tile, cloud))
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', schemeColors(seed, dark)['--surface'])
}
