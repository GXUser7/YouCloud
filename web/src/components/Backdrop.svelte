<script lang="ts">
  // The app's live background: soft Expressive shapes at different depths, in the palette's own
  // browns (or whatever the cover tints it), drifting and turning slowly. On a computer they lean
  // away from the mouse instead of the phone's tilt.
  import { onMount } from 'svelte'
  import { app } from '../lib/state.svelte'
  import { backdropMotion } from '../lib/backdrop.svelte'
  import { shapePath, type ShapeName } from '../lib/shapes'

  let { mirror = false }: { mirror?: boolean } = $props()
  const gradientPrefix = $derived(mirror ? 'bd-page-light' : 'bd-light')

  const shapes: { shape: ShapeName; x: number; y: number; size: number; depth: number; spin: number }[] = [
    { shape: 'softBurst', x: 0.92, y: 0.78, size: 0.5, depth: 0.25, spin: 90 },
    { shape: 'pentagon', x: 0.3, y: 0.06, size: 0.2, depth: 0.3, spin: 140 },
    { shape: 'cookie9', x: 0.96, y: 0.08, size: 0.52, depth: 0.5, spin: 120 },
    { shape: 'cookie12', x: 0.62, y: 0.5, size: 0.26, depth: 0.65, spin: -100 },
    { shape: 'clover4', x: 0.02, y: 0.42, size: 0.44, depth: 0.8, spin: -130 },
    { shape: 'puffy', x: 0.16, y: 0.98, size: 0.36, depth: 0.95, spin: -160 },
  ]

  // The shapes turn, and lean after the mouse, in steps set from here, twenty a second: a turn
  // takes them minutes and they are blurred, so a step moves an edge by a pixel or so of its blur
  // and the eye can't tell. Each step has the browser draw the whole window again under the glass;
  // a CSS animation or transition does that sixty times a second, steps or not — work a laptop's
  // GPU felt in everything else.
  const STEP_MS = 50
  // The share of the way to the mouse covered each step: most of it within a second, easing in as
  // the transition the shapes had did.
  const FOLLOW = 1 - Math.exp(-STEP_MS / 300)
  let towardX = 0
  let towardY = 0

  onMount(() => {
    if (mirror) return
    let last = performance.now()
    const timer = setInterval(() => {
      const now = performance.now()
      if (app.liveBackdrop && !document.hidden) {
        backdropMotion.turned += (now - last) / 1000
        if (Math.abs(towardX - backdropMotion.mx) > 0.0005 || Math.abs(towardY - backdropMotion.my) > 0.0005) {
          backdropMotion.mx += (towardX - backdropMotion.mx) * FOLLOW
          backdropMotion.my += (towardY - backdropMotion.my) * FOLLOW
        }
      }
      last = now
    }, STEP_MS)
    return () => clearInterval(timer)
  })

  function onmove(e: PointerEvent) {
    towardX = e.clientX / innerWidth - 0.5
    towardY = e.clientY / innerHeight - 0.5
  }
</script>

<svelte:window onpointermove={!mirror && app.liveBackdrop ? onmove : undefined} />

<div class="backdrop" class:mirror aria-hidden="true">
  {#each shapes as s, i}
    <div
      class="shape"
      style:left="{s.x * 100}%"
      style:top="{s.y * 100}%"
      style:width="{s.size * 100}vmax"
      style:--depth={s.depth}
      style:translate="{-backdropMotion.mx * 40 * s.depth}px {-backdropMotion.my * 40 * s.depth}px"
    >
      <!-- The turning layer holds the shape already blurred: the blur is drawn once, and the GPU only
           turns the picture. A blur over turning content would be drawn again every frame. -->
      <div class="spin" style:transform="rotate({((backdropMotion.turned / s.spin) * 360) % 360}deg)">
      <svg viewBox="0 0 100 100" style:filter="blur({18 - s.depth * 10}px)">
        <defs>
          <radialGradient id="{gradientPrefix}-{i}" cx=".3" cy=".25" r=".85">
            <stop offset="0" style="stop-color: var(--on-surface); stop-opacity: .09" />
            <stop offset="1" style="stop-color: var(--on-surface); stop-opacity: 0" />
          </radialGradient>
        </defs>
        <path d={shapePath(s.shape)} class="body" />
        <path d={shapePath(s.shape)} fill="url(#{gradientPrefix}-{i})" />
      </svg>
      </div>
    </div>
  {/each}
  <div class="veil"></div>
</div>

<style>
  .backdrop {
    position: fixed;
    inset: 0;
    overflow: hidden;
    background: var(--surface);
    z-index: 0;
    pointer-events: none;
  }
  /* Фон остаётся внутри маски, под контентом. Отдельный z-index на .page
     ограничивал область, которую Chromium даёт размывать карточкам. */
  .backdrop.mirror { z-index: -1; }
  .shape {
    position: absolute;
    aspect-ratio: 1;
    transform: translate(-50%, -50%);
    opacity: calc(0.35 + var(--depth) * 0.55);
    will-change: translate;
  }
  .spin {
    width: 100%;
    height: 100%;
    will-change: transform;
  }
  svg {
    width: 100%;
    height: 100%;
    display: block;
    overflow: visible;
  }
  .body {
    fill: color-mix(in oklab, color-mix(in oklab, var(--surface-container-highest) 85%, var(--primary)) calc(55% + var(--depth) * 45%), var(--surface));
  }
  .veil {
    position: absolute;
    inset: 0;
    background: radial-gradient(140% 110% at 50% 45%, transparent 55%, color-mix(in oklab, var(--surface) 35%, transparent) 100%);
  }
</style>
