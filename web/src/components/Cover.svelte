<script module lang="ts">
  let uid = 0
</script>

<script lang="ts">
  // The item's artwork, or, when it has none (or it fails to load), a cover drawn from the item's
  // colour in the same Expressive shapes the rest of the site uses.
  import { hexToHsl, hsl } from '../lib/color'
  import { rng } from '../lib/format'
  import { directFailed, reach } from '../lib/media.svelte'
  import { COVER_SHAPES, shapePath } from '../lib/shapes'

  let {
    seed,
    color,
    src = null,
    kind = 'album',
    label = '',
    title = '',
    grain = false,
  }: { seed: string; color: string; src?: string | null; kind?: string; label?: string; title?: string; grain?: boolean } = $props()

  const id = `cv${uid++}`
  let broken = $state<string | null>(null)
  // Where SoundCloud's CDN is blocked the same picture comes through the Worker.
  const shown = $derived(reach(src))
  const showImage = $derived(!!shown && broken !== shown)

  const art = $derived.by(() => {
    const r = rng(seed)
    const [h, s] = hexToHsl(color)
    const sat = Math.max(38, Math.min(78, s))
    const shape = COVER_SHAPES[Math.floor(r() * COVER_SHAPES.length)]
    const second = COVER_SHAPES[Math.floor(r() * COVER_SHAPES.length)]
    return {
      h,
      sat,
      bgA: hsl(h, sat * 0.85, 34 + r() * 10),
      bgB: hsl(h + 28 + r() * 30, sat * 0.9, 14 + r() * 8),
      ink: hsl(h - 18 + r() * 36, sat, 70 + r() * 10),
      accent: hsl(h + 150 + r() * 60, sat * 0.75, 62),
      shape: shapePath(shape, 0.62 + r() * 0.3, r() * 90),
      shape2: shapePath(second, 0.28 + r() * 0.16, r() * 90),
      x: -18 + r() * 36,
      y: -14 + r() * 40,
      x2: -30 + r() * 60,
      y2: -34 + r() * 20,
      angle: Math.floor(r() * 360),
    }
  })
</script>

{#if showImage}
  <img class="cover img" src={shown} alt="" loading="lazy" decoding="async" onerror={() => directFailed(shown) || (broken = shown)} />
{:else}
<svg class="cover" viewBox="0 0 100 100" preserveAspectRatio="xMidYMid slice" aria-hidden="true">
  <defs>
    <linearGradient id="{id}g" gradientTransform="rotate({art.angle} .5 .5)">
      <stop offset="0" stop-color={art.bgA} />
      <stop offset="1" stop-color={art.bgB} />
    </linearGradient>
    <radialGradient id="{id}l" cx=".3" cy=".2" r=".9">
      <stop offset="0" stop-color="#fff" stop-opacity=".22" />
      <stop offset=".6" stop-color="#fff" stop-opacity="0" />
    </radialGradient>
    {#if grain}
      <filter id="{id}n" x="0" y="0" width="100%" height="100%">
        <feTurbulence type="fractalNoise" baseFrequency=".9" numOctaves="2" stitchTiles="stitch" />
        <feColorMatrix values="0 0 0 0 1  0 0 0 0 1  0 0 0 0 1  0 0 0 .55 0" />
      </filter>
    {/if}
  </defs>

  {#if kind === 'mix'}
    <rect width="100" height="100" fill={hsl(art.h, art.sat, 74)} />
    <path d={art.shape} fill={hsl(art.h + 25, art.sat * 0.7, 40)} transform="translate({art.x * 0.4} {12 + art.y * 0.2})" />
    <path d={art.shape2} fill={hsl(art.h - 30, art.sat, 88)} transform="translate({art.x2} {art.y2})" />
    <rect x="7" y="74" width="86" height="19" rx="5" fill={hsl(art.h + 200, 70, 78)} />
    <text x="12" y="88.5" class="label">{label}</text>
  {:else if kind === 'artist'}
    <rect width="100" height="100" fill="url(#{id}g)" />
    <circle cx="50" cy="40" r="17" fill={art.ink} opacity=".9" />
    <path d="M18 104c2-22 15-34 32-34s30 12 32 34z" fill={art.ink} opacity=".9" />
    <path d={art.shape2} fill={art.accent} opacity=".55" transform="translate({art.x2} {art.y2})" />
  {:else if kind === 'genre' || kind === 'personal'}
    <rect width="100" height="100" fill="url(#{id}g)" />
    <path d={art.shape} fill={art.ink} opacity=".92" transform="translate({18 + art.x * 0.5} {-22 + art.y * 0.3})" />
    <path d={art.shape2} fill={art.accent} opacity=".85" transform="translate({-26 + art.x2 * 0.3} {-30})" />
    <text x="8" y="88" class="title" class:long={title.length > 9}>{title}</text>
  {:else if kind === 'liked'}
    <rect width="100" height="100" fill="url(#{id}g)" />
    <path d={shapePath('cookie9', 0.78, art.angle)} fill={art.ink} opacity=".95" />
    <path
      d="M50 66.5 47.2 64C37.5 55.2 31 49.4 31 42.2 31 36.4 35.6 31.8 41.4 31.8c3.3 0 6.4 1.5 8.6 4 2.2-2.5 5.3-4 8.6-4 5.8 0 10.4 4.6 10.4 10.4 0 7.2-6.5 13-16.2 21.8z"
      fill={hsl(art.h, art.sat, 22)}
    />
  {:else if kind === 'station'}
    <rect width="100" height="100" fill="url(#{id}g)" />
    {#each [44, 34, 24] as radius, i}
      <circle cx="50" cy="50" r={radius} fill="none" stroke={art.ink} stroke-width="1.2" opacity={0.25 + i * 0.2} />
    {/each}
    <path d={shapePath('cookie7', 0.3, art.angle)} fill={art.ink} />
  {:else}
    <rect width="100" height="100" fill="url(#{id}g)" />
    <path d={art.shape} fill={art.ink} transform="translate({art.x} {art.y})" />
    <path d={art.shape2} fill={art.accent} transform="translate({art.x2} {art.y2})" />
  {/if}

  <rect width="100" height="100" fill="url(#{id}l)" />
  {#if grain}
    <rect width="100" height="100" filter="url(#{id}n)" opacity=".1" />
  {/if}
</svg>
{/if}

<style>
  .cover {
    display: block;
    width: 100%;
    height: 100%;
  }
  .img {
    object-fit: cover;
    background: color-mix(in oklab, var(--on-surface) 8%, transparent);
  }
  .label {
    font-family: var(--font-display);
    font-weight: 800;
    font-size: 11px;
    fill: #1a1210;
    letter-spacing: -0.02em;
  }
  .title {
    font-family: var(--font-display);
    font-weight: 700;
    font-size: 12.5px;
    fill: #fff;
    letter-spacing: -0.02em;
  }
  .title.long {
    font-size: 9.5px;
  }
</style>
