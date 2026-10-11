<script lang="ts">
  // Material 3 Expressive's wavy progress: the part played is a wave that keeps moving while the
  // track plays and lies flat when it is paused; the rest is a straight track ending in a dot.
  // Seekable ([onseek]), it is Expressive's slider: a handle stands between the two parts with a
  // gap either side, grows under the pointer and thins while held; a bubble over the track tells
  // the time under the pointer, or the time it is dragged to. Drag, click or use the arrow keys.
  import { tr } from '../lib/i18n.svelte'
  import { time } from '../lib/format'

  let {
    value,
    playing,
    onseek,
    duration = 0,
    amplitude = 3.2,
    wavelength = 36,
    stroke = 4,
  }: {
    value: number
    playing: boolean
    onseek?: (fraction: number) => void
    /** Seconds: for the time the bubble tells. */
    duration?: number
    amplitude?: number
    wavelength?: number
    stroke?: number
  } = $props()

  let width = $state(0)
  let dragging = $state<number | null>(null)
  let hover = $state<number | null>(null)
  let el: HTMLDivElement

  const seekable = $derived(!!onseek)
  const shown = $derived(Math.max(0, Math.min(1, dragging ?? value)))
  const played = $derived(width * shown)
  // The handle stands in a gap: the wave stops short of it, the rest starts past it.
  const gap = $derived(seekable ? 6 : 0)
  const h = 24
  const mid = h / 2

  const cut = $derived(Math.max(0, played - gap))

  const STEP = 2

  /**
   * A sine wave [height] high (0 is a straight line), drawn in short steps so it bends evenly,
   * with the same commands either way so one morphs into the other. It starts a whole number of
   * wavelengths before the bar, so sliding by one wavelength loops without a seam.
   */
  function wavePath(height: number): string {
    const start = -wavelength * 2
    const end = width + wavelength * 2
    let d = `M${start} ${mid}`
    for (let x = start + STEP; x <= end; x += STEP) {
      const y = mid - Math.sin(((x - start) / wavelength) * Math.PI * 2) * height
      d += `L${x} ${y.toFixed(2)}`
    }
    return d
  }
  // Playing, a wave; paused or held, it settles into a straight line of the same weight.
  const flowing = $derived(playing && dragging === null)
  const wave = $derived(wavePath(flowing ? amplitude : 0))

  // The bubble: over the handle while it is held, else over the pointer.
  const tipAt = $derived(dragging ?? hover)
  const tipX = $derived(tipAt === null ? 0 : Math.max(18, Math.min(width - 18, tipAt * width)))

  function fractionAt(e: PointerEvent) {
    const rect = el.getBoundingClientRect()
    return Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width))
  }

  function down(e: PointerEvent) {
    if (!seekable) return
    el.setPointerCapture(e.pointerId)
    dragging = fractionAt(e)
  }
  function move(e: PointerEvent) {
    if (!seekable) return
    if (dragging !== null) dragging = fractionAt(e)
    else if (e.pointerType === 'mouse') hover = fractionAt(e)
  }
  function up() {
    if (dragging !== null) onseek?.(dragging)
    dragging = null
  }
  function key(e: KeyboardEvent) {
    if (!seekable) return
    const step = duration ? 5 / duration : 0.02
    const to = e.key === 'ArrowRight' ? value + step : e.key === 'ArrowLeft' ? value - step : e.key === 'Home' ? 0 : e.key === 'End' ? 0.999 : null
    if (to === null) return
    e.preventDefault()
    onseek?.(Math.max(0, Math.min(1, to)))
  }
</script>

<div
  class="wavy"
  class:seekable
  class:dragging={dragging !== null}
  bind:this={el}
  bind:clientWidth={width}
  onpointerdown={down}
  onpointermove={move}
  onpointerleave={() => (hover = null)}
  onpointerup={up}
  onpointercancel={() => (dragging = null)}
  onkeydown={key}
  role="slider"
  aria-label={tr('Перемотка')}
  aria-valuemin={0}
  aria-valuemax={100}
  aria-valuenow={Math.round(shown * 100)}
  aria-valuetext={duration ? tr('%s из %s', time(shown * duration), time(duration)) : undefined}
  tabindex={seekable ? 0 : -1}
>
  <!-- Drawn once and only moved, every part by a transform — what the GPU does on its own, with
       no layout or repaint: the rest of the track slides along under the progress; the wave sits
       still in a box that slides from the left as far as the part played (and is slid back inside
       it, so the box only cuts); the handle and the bubble ride on top. -->
  <span class="rest-track" class:gone={played + gap >= width - stroke} style:--stroke="{stroke}px">
    <span class="rest" style:transform="translateX({played + gap}px)"></span>
    <span class="stop"></span>
  </span>
  <span class="played-clip">
    <span class="played-box" style:transform="translateX({cut - width}px)">
      <span class="counter" style:transform="translateX({width - cut}px)">
        <svg class="wave" class:moving={flowing} width={width + wavelength * 4} height={h} viewBox="{-wavelength * 2} 0 {width + wavelength * 4} {h}" style:--wl="{wavelength}px">
          <path d={wave} class="played" stroke-width={stroke} stroke-linecap="round" stroke-linejoin="round" fill="none" />
        </svg>
      </span>
    </span>
  </span>
  {#if seekable}
    <span class="thumb-at" style:transform="translateX({played}px)"><span class="thumb"></span></span>
    {#if duration}
      <span class="tip-at" class:shown={tipAt !== null} style:transform="translateX({tipX}px)">
        <span class="tip">{time((tipAt ?? 0) * duration)}</span>
      </span>
    {/if}
  {/if}
</div>

<style>
  .wavy {
    position: relative;
    height: 24px;
    width: 100%;
    touch-action: none;
    contain: layout;
  }
  .seekable {
    height: 32px;
    cursor: pointer;
  }
  /* Taller to aim at, the track in its middle. */
  .seekable .played-clip {
    top: 4px;
  }
  .seekable:focus-visible {
    outline: none;
  }
  .rest-track {
    position: absolute;
    left: 0;
    right: 0;
    top: calc(12px - var(--stroke) / 2);
    height: var(--stroke);
    border-radius: var(--stroke);
    overflow: hidden;
  }
  .seekable .rest-track {
    top: calc(16px - var(--stroke) / 2);
  }
  .rest-track.gone {
    visibility: hidden;
  }
  .rest {
    position: absolute;
    inset: 0;
    border-radius: var(--stroke);
    background: color-mix(in oklab, var(--primary) 26%, transparent);
    will-change: transform;
    transition: background-color 200ms var(--ease-emph);
  }
  .seekable:hover .rest {
    background: color-mix(in oklab, var(--primary) 34%, transparent);
  }
  .stop {
    position: absolute;
    right: 0;
    top: 0;
    width: var(--stroke);
    height: var(--stroke);
    border-radius: 50%;
    background: var(--primary);
  }
  .played-clip {
    position: absolute;
    left: 0;
    right: 0;
    top: 0;
    height: 24px;
    overflow: hidden;
  }
  .played-box,
  .counter {
    position: absolute;
    inset: 0;
    will-change: transform;
  }
  .played-box {
    overflow: hidden;
  }
  .wave {
    display: block;
    position: absolute;
    top: 0;
    left: calc(var(--wl) * -2);
    overflow: visible;
    will-change: transform;
  }
  .played {
    stroke: var(--primary);
    transition: d 600ms var(--spring);
  }
  .moving {
    animation: slide 2.2s linear infinite;
  }
  @keyframes slide {
    to {
      transform: translateX(var(--wl));
    }
  }

  /* The handle: a bar standing in the gap, taller under the pointer, thinner while held. */
  .thumb-at {
    position: absolute;
    left: 0;
    top: 0;
    will-change: transform;
    pointer-events: none;
  }
  .thumb {
    position: absolute;
    top: 2px;
    width: 4px;
    height: 28px;
    margin-left: -2px;
    border-radius: 2px;
    background: var(--primary);
    transform: scaleY(0.64);
    transition: transform var(--d-fast) var(--spring-fast);
  }
  .seekable:hover .thumb,
  .seekable:focus-visible .thumb {
    transform: none;
  }
  .dragging .thumb {
    transform: scaleX(0.5);
  }
  .seekable:focus-visible .thumb {
    box-shadow: 0 0 0 3px color-mix(in oklab, var(--primary) 35%, transparent);
  }

  /* The bubble: the inverse of the surface, springing up out of the track. */
  .tip-at {
    position: absolute;
    left: 0;
    bottom: calc(100% + 2px);
    pointer-events: none;
    will-change: transform;
  }
  .tip {
    display: block;
    translate: -50% 0;
    padding: 4px 9px;
    border-radius: var(--r-full);
    background: var(--on-surface);
    color: var(--surface);
    font-size: 12px;
    font-weight: 700;
    font-variant-numeric: tabular-nums;
    white-space: nowrap;
    opacity: 0;
    transform: translateY(6px) scale(0.6);
    transform-origin: 50% 100%;
    transition:
      opacity 150ms var(--ease-emph),
      transform var(--d-fast) var(--spring-fast);
  }
  .tip-at.shown .tip {
    opacity: 1;
    transform: none;
  }
</style>
