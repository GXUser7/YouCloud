<script lang="ts">
  // The Material 3 Expressive slider: a thick track split by a tall narrow handle, the two halves
  // pulling away from it, and a dot where the track ends — as the app's player draws it.
  let {
    value,
    onchange,
    onrelease,
    thickness = 16,
    label = '',
  }: {
    value: number
    onchange?: (fraction: number) => void
    onrelease?: (fraction: number) => void
    thickness?: number
    label?: string
  } = $props()

  let el: HTMLDivElement
  let width = $state(0)
  let dragging = $state<number | null>(null)
  const shown = $derived(Math.max(0, Math.min(1, dragging ?? value)))

  const GAP = 6
  const HANDLE = 4
  // Where the two halves end, either side of the handle.
  const activeEnd = $derived(Math.max(0, shown * width - GAP - HANDLE / 2))
  const inactiveStart = $derived(Math.min(width, shown * width + GAP + HANDLE / 2))

  function at(e: PointerEvent) {
    const rect = el.getBoundingClientRect()
    return Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width))
  }
  function down(e: PointerEvent) {
    el.setPointerCapture(e.pointerId)
    dragging = at(e)
    onchange?.(dragging)
  }
  function move(e: PointerEvent) {
    if (dragging === null) return
    dragging = at(e)
    onchange?.(dragging)
  }
  function up() {
    if (dragging === null) return
    onrelease?.(dragging)
    dragging = null
  }
  function key(e: KeyboardEvent) {
    const step = e.key === 'ArrowRight' ? 0.05 : e.key === 'ArrowLeft' ? -0.05 : 0
    if (!step) return
    e.preventDefault()
    const v = Math.max(0, Math.min(1, value + step))
    onchange?.(v)
    onrelease?.(v)
  }
</script>

<div
  class="slider"
  class:dragging={dragging !== null}
  style:--t="{thickness}px"
  bind:this={el}
  bind:clientWidth={width}
  onpointerdown={down}
  onpointermove={move}
  onpointerup={up}
  onpointercancel={() => (dragging = null)}
  onkeydown={key}
  role="slider"
  tabindex="0"
  aria-label={label}
  aria-valuemin={0}
  aria-valuemax={100}
  aria-valuenow={Math.round(shown * 100)}
>
  <!-- Each half is a whole-width bar slid into a box with the slider's rounded end, and the handle
       rides on a transform: as the track plays nothing is laid out or painted again, the GPU only
       moves the pieces. -->
  <span class="half start"><span class="active" style:transform="translateX({activeEnd - width}px)"></span></span>
  <span class="half end"><span class="inactive" style:transform="translateX({inactiveStart}px)"></span></span>
  <span class="handle-at" style:transform="translateX({shown * width}px)"><span class="handle"></span></span>
  <span class="dot" style:opacity={1 - shown * 0.96}></span>
</div>

<style>
  .slider {
    --gap: 6px;
    --handle: 4px;
    position: relative;
    height: calc(var(--t) + 24px);
    cursor: pointer;
    touch-action: none;
  }
  .half {
    position: absolute;
    inset: 0;
    top: 50%;
    height: var(--t);
    translate: 0 -50%;
    overflow: hidden;
  }
  .half.start {
    border-radius: calc(var(--t) / 2) 0 0 calc(var(--t) / 2);
  }
  .half.end {
    border-radius: 0 calc(var(--t) / 2) calc(var(--t) / 2) 0;
  }
  .active,
  .inactive {
    position: absolute;
    inset: 0;
    will-change: transform;
  }
  .active {
    background: var(--primary);
    border-radius: 0 4px 4px 0;
  }
  .inactive {
    background: color-mix(in oklab, var(--primary) 22%, transparent);
    border-radius: 4px 0 0 4px;
  }
  .handle-at {
    position: absolute;
    left: 0;
    top: 50%;
    will-change: transform;
  }
  .handle {
    position: absolute;
    top: 0;
    left: 0;
    width: var(--handle);
    height: calc(var(--t) + 20px);
    translate: -50% -50%;
    border-radius: 2px;
    background: var(--primary);
    transition:
      width 250ms var(--spring-fast),
      height 250ms var(--spring-fast);
  }
  .slider:hover .handle {
    height: calc(var(--t) + 24px);
  }
  .dragging .handle {
    width: 2px;
  }
  .dot {
    position: absolute;
    top: 50%;
    right: calc(var(--t) / 2 - 2px);
    width: 4px;
    height: 4px;
    translate: 0 -50%;
    border-radius: 50%;
    background: var(--primary);
    will-change: opacity;
  }
</style>
