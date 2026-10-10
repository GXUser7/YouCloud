<script lang="ts">
  // Material 3 Expressive's wavy progress: the part played is a wave that keeps moving while the
  // track plays and lies flat when it is paused; the rest is a straight track ending in a dot.
  // Drag or click anywhere on it to seek.
  let {
    value,
    playing,
    onseek,
    amplitude = 3.2,
    wavelength = 36,
    stroke = 4,
  }: {
    value: number
    playing: boolean
    onseek?: (fraction: number) => void
    amplitude?: number
    wavelength?: number
    stroke?: number
  } = $props()

  let width = $state(0)
  let dragging = $state<number | null>(null)
  let el: HTMLDivElement

  const shown = $derived(Math.max(0, Math.min(1, dragging ?? value)))
  const played = $derived(width * shown)
  // Where the wave is cut: held, it stops short of the handle by the gap.
  const gap = 6
  const h = 24
  const mid = h / 2

  const cut = $derived(Math.max(0, played - (dragging !== null ? gap : 0)))

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

  function fractionAt(e: PointerEvent) {
    const rect = el.getBoundingClientRect()
    return Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width))
  }

  function down(e: PointerEvent) {
    el.setPointerCapture(e.pointerId)
    dragging = fractionAt(e)
  }
  function move(e: PointerEvent) {
    if (dragging !== null) dragging = fractionAt(e)
  }
  function up() {
    if (dragging !== null) onseek?.(dragging)
    dragging = null
  }
</script>

<div
  class="wavy"
  class:dragging={dragging !== null}
  bind:this={el}
  bind:clientWidth={width}
  onpointerdown={down}
  onpointermove={move}
  onpointerup={up}
  onpointercancel={() => (dragging = null)}
  role="slider"
  aria-label="Перемотка"
  aria-valuemin={0}
  aria-valuemax={100}
  aria-valuenow={Math.round(shown * 100)}
  tabindex="0"
>
  <!-- Drawn once and only moved, every part by a transform — what the GPU does on its own, with
       no layout or repaint: the rest of the track slides along under the progress; the wave sits
       still in a box that slides from the left as far as the part played (and is slid back inside
       it, so the box only cuts); the handle rides on top. -->
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
  <span class="thumb-at" style:transform="translateX({played}px)"><span class="thumb"></span></span>
</div>

<style>
  .wavy {
    position: relative;
    height: 24px;
    width: 100%;
    cursor: pointer;
    touch-action: none;
    contain: layout;
  }
  .rest-track {
    position: absolute;
    left: 0;
    right: 0;
    top: calc(50% - var(--stroke) / 2);
    height: var(--stroke);
    border-radius: var(--stroke);
    overflow: hidden;
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
    inset: 0;
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
  .thumb-at {
    position: absolute;
    left: 0;
    top: 0;
    will-change: transform;
    pointer-events: none;
  }
  .thumb {
    position: absolute;
    top: 0;
    width: 4px;
    height: 24px;
    margin-left: -2px;
    border-radius: 2px;
    background: var(--primary);
    opacity: 0;
    transform: scaleY(0.4);
    transition:
      opacity 150ms,
      transform 300ms var(--spring-fast);
    pointer-events: none;
  }
  .wavy:hover .thumb,
  .dragging .thumb {
    opacity: 1;
    transform: none;
  }
</style>
