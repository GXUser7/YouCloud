<script lang="ts">
  // Volume as Material 3 Expressive's inset-icon slider: one thick pill, the filled part carrying
  // the speaker icon, as Android's own volume does. Anywhere on it sets the level — a click, a
  // drag, the wheel, the arrow keys; a tap on the icon mutes and brings the sound back.
  import { player } from '../lib/state.svelte'
  import Icon from './Icon.svelte'

  let el: HTMLDivElement
  let dragging = $state(false)
  let hover = $state(false)
  let start: { x: number; onIcon: boolean; moved: boolean } | null = null

  const ICON_ZONE = 40
  const level = $derived(player.muted ? 0 : player.volume)
  const icon = $derived(level === 0 ? 'volume_off' : level < 0.4 ? 'volume_mute' : level < 0.75 ? 'volume_down' : 'volume_up')

  function at(clientX: number): number {
    const rect = el.getBoundingClientRect()
    // The fill is the icon's zone plus the level's share of the rest: its edge lands under the pointer.
    return Math.max(0, Math.min(1, (clientX - rect.left - ICON_ZONE) / (rect.width - ICON_ZONE)))
  }

  function set(v: number) {
    player.volume = Math.round(v * 100) / 100
    player.muted = v === 0
  }

  function down(e: PointerEvent) {
    el.setPointerCapture(e.pointerId)
    const rect = el.getBoundingClientRect()
    start = { x: e.clientX, onIcon: e.clientX - rect.left < ICON_ZONE, moved: false }
  }

  function move(e: PointerEvent) {
    if (!start) return
    if (!start.moved && Math.abs(e.clientX - start.x) < 3) return
    start.moved = true
    dragging = true
    set(at(e.clientX))
  }

  function up(e: PointerEvent) {
    if (!start) return
    if (!start.moved) {
      if (start.onIcon) {
        player.muted = !player.muted
        if (!player.muted && player.volume === 0) player.volume = 0.5
      } else set(at(e.clientX))
    }
    start = null
    dragging = false
  }

  function wheel(e: WheelEvent) {
    e.preventDefault()
    set(Math.max(0, Math.min(1, level + (e.deltaY < 0 ? 0.05 : -0.05))))
  }

  function key(e: KeyboardEvent) {
    const step = e.key === 'ArrowRight' || e.key === 'ArrowUp' ? 0.05 : e.key === 'ArrowLeft' || e.key === 'ArrowDown' ? -0.05 : 0
    if (step) {
      e.preventDefault()
      set(Math.max(0, Math.min(1, level + step)))
    } else if (e.key === 'm' || e.key === 'ь' || e.key === 'Enter') {
      player.muted = !player.muted
    }
  }
</script>

<div
  class="volume"
  class:dragging
  class:muted={level === 0}
  bind:this={el}
  style:--v={level}
  style:--zone="{ICON_ZONE}px"
  onpointerdown={down}
  onpointermove={move}
  onpointerup={up}
  onpointercancel={() => ((start = null), (dragging = false))}
  onpointerenter={() => (hover = true)}
  onpointerleave={() => (hover = false)}
  onwheel={wheel}
  onkeydown={key}
  role="slider"
  tabindex="0"
  aria-label="Громкость"
  aria-valuemin={0}
  aria-valuemax={100}
  aria-valuenow={Math.round(level * 100)}
  title="Громкость: колёсико или перетаскивание, клик по значку — без звука"
>
  <span class="fill">
    <span class="icon"><Icon name={icon} fill size={20} /></span>
  </span>
  <span class="value" class:shown={hover || dragging} class:over={level > 0.8}>{Math.round(level * 100)}</span>
</div>

<style>
  .volume {
    position: relative;
    width: 150px;
    height: 40px;
    border-radius: 20px;
    background: color-mix(in oklab, var(--on-surface) 10%, transparent);
    cursor: pointer;
    touch-action: none;
    flex: none;
    overflow: hidden;
    transition:
      border-radius var(--d-spatial) var(--spring-fast),
      background-color 200ms;
  }
  .volume:hover {
    background: color-mix(in oklab, var(--on-surface) 14%, transparent);
  }
  /* Held, the pill tightens its corners, as Expressive's pressed controls do. */
  .volume.dragging {
    border-radius: 12px;
  }
  .fill {
    position: absolute;
    inset: 0 auto 0 0;
    width: calc(var(--zone) + (100% - var(--zone)) * var(--v));
    background: var(--primary);
    color: var(--on-primary);
    border-radius: inherit;
    display: flex;
    align-items: center;
    transition:
      width var(--d-spatial) var(--spring),
      background-color 300ms var(--ease-emph);
  }
  .dragging .fill {
    transition: background-color 300ms var(--ease-emph);
  }
  .muted .fill {
    background: color-mix(in oklab, var(--on-surface) 22%, transparent);
    color: var(--on-surface);
  }
  .icon {
    width: var(--zone);
    display: grid;
    place-items: center;
    flex: none;
  }
  .value {
    position: absolute;
    right: 12px;
    top: 50%;
    translate: 0 -50%;
    font-size: 12px;
    font-weight: 700;
    font-variant-numeric: tabular-nums;
    color: var(--on-surface-variant);
    opacity: 0;
    transition: opacity 150ms;
    pointer-events: none;
  }
  .value.shown {
    opacity: 1;
  }
  /* Loud enough, the fill reaches the number: it takes the fill's own text colour. */
  .value.over {
    color: var(--on-primary);
  }
</style>
