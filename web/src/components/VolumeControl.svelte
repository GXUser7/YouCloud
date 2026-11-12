<script lang="ts">
  // Volume as Material 3 Expressive's inset-icon slider: the active track in the theme's colour
  // carrying the speaker, a slim handle set off by a gap on either side, the inactive track with a
  // stop dot at its end. Anywhere on it sets the level — a click, a drag, the wheel (Shift for
  // fine steps), the arrow keys; a tap on the speaker mutes and brings the sound back. While it is
  // under the pointer or held, the level shows over the handle; the handle narrows as it is held.
  import { tr } from '../lib/i18n.svelte'
  import { player } from '../lib/state.svelte'
  import Icon from './Icon.svelte'

  let el: HTMLDivElement
  let dragging = $state(false)
  let hover = $state(false)
  let nudged = $state(false)
  let nudgeTimer: ReturnType<typeof setTimeout> | undefined
  let start: { x: number; onIcon: boolean; moved: boolean } | null = null

  // Where the handle stands at nought (past the speaker and a gap) and how far from the end at full.
  const LOW = 46
  const HIGH_INSET = 8
  // The speaker's own spot: a tap there mutes.
  const ICON_HIT = 36

  const level = $derived(player.muted ? 0 : player.volume)
  const icon = $derived(level === 0 ? 'volume_off' : level < 0.4 ? 'volume_mute' : level < 0.75 ? 'volume_down' : 'volume_up')
  const shown = $derived(hover || dragging || nudged)

  function at(clientX: number): number {
    const rect = el.getBoundingClientRect()
    return Math.max(0, Math.min(1, (clientX - rect.left - LOW) / (rect.width - LOW - HIGH_INSET)))
  }

  function set(v: number) {
    const clamped = Math.max(0, Math.min(1, v))
    player.volume = Math.round(clamped * 100) / 100
    player.muted = clamped === 0
  }

  function down(e: PointerEvent) {
    if (e.button !== 0) return
    el.setPointerCapture(e.pointerId)
    const rect = el.getBoundingClientRect()
    start = { x: e.clientX, onIcon: e.clientX - rect.left < ICON_HIT, moved: false }
    // Off the speaker, the level jumps under the pointer at once and the drag goes on from there.
    if (!start.onIcon) {
      dragging = true
      set(at(e.clientX))
    }
  }

  function move(e: PointerEvent) {
    if (!start) return
    if (!start.moved && Math.abs(e.clientX - start.x) < 3) return
    start.moved = true
    dragging = true
    set(at(e.clientX))
  }

  function up() {
    if (start?.onIcon && !start.moved) {
      player.muted = !player.muted
      if (!player.muted && player.volume === 0) player.volume = 0.5
    }
    start = null
    dragging = false
  }

  // The wheel and the keys move it without a pointer on it: the level shows a moment after.
  function nudge(by: number) {
    set(level + by)
    nudged = true
    clearTimeout(nudgeTimer)
    nudgeTimer = setTimeout(() => (nudged = false), 900)
  }

  function wheel(e: WheelEvent) {
    e.preventDefault()
    const step = e.shiftKey ? 0.01 : 0.05
    nudge(e.deltaY < 0 || e.deltaX < 0 ? step : -step)
  }

  function key(e: KeyboardEvent) {
    const step = e.shiftKey ? 0.01 : 0.05
    if (e.key === 'ArrowRight' || e.key === 'ArrowUp') {
      e.preventDefault()
      nudge(step)
    } else if (e.key === 'ArrowLeft' || e.key === 'ArrowDown') {
      e.preventDefault()
      nudge(-step)
    } else if (e.key === 'Home' || e.key === 'End') {
      e.preventDefault()
      nudge(e.key === 'Home' ? -1 : 1)
    } else if (e.key === 'm' || e.key === 'ь' || e.key === 'Enter') {
      player.muted = !player.muted
      if (!player.muted && player.volume === 0) player.volume = 0.5
    }
  }
</script>

<div
  class="volume"
  class:dragging
  class:muted={level === 0}
  class:full={level >= 0.995}
  class:shown
  bind:this={el}
  style:--v={level}
  style:--low="{LOW}px"
  style:--high="{HIGH_INSET}px"
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
  aria-label={tr('Громкость')}
  aria-valuemin={0}
  aria-valuemax={100}
  aria-valuenow={Math.round(level * 100)}
  title={tr('Громкость: колёсико или перетаскивание, клик по значку — без звука')}
>
  <span class="track active">
    {#key icon}<span class="icon"><Icon name={icon} fill size={20} /></span>{/key}
  </span>
  <span class="track inactive"><span class="stop"></span></span>
  <span class="handle"><span class="tip">{Math.round(level * 100)}</span></span>
</div>

<style>
  /* The handle's centre, in the box: past the speaker at nought, a little short of the end at
     full. Both tracks are measured from it, a gap kept on either side. */
  .volume {
    --x: calc(var(--low) + (100% - var(--low) - var(--high)) * var(--v));
    --gap: 6px;
    --handle: 4px;
    position: relative;
    width: 160px;
    height: 40px;
    cursor: pointer;
    touch-action: none;
    flex: none;
    border-radius: 20px;
  }
  .volume:focus-visible {
    outline-offset: 4px;
  }
  .track {
    position: absolute;
    top: 0;
    bottom: 0;
    display: flex;
    align-items: center;
    transition:
      width var(--d-spatial) var(--spring),
      left var(--d-spatial) var(--spring),
      background-color 300ms var(--ease-emph),
      opacity 200ms var(--ease-emph);
  }
  /* The ends that face the handle are only slightly rounded, as Expressive's tracks are. */
  .active {
    left: 0;
    width: max(0px, calc(var(--x) - var(--handle) / 2 - var(--gap)));
    border-radius: 20px 6px 6px 20px;
    background: var(--primary);
    color: var(--on-primary);
  }
  .inactive {
    left: calc(var(--x) + var(--handle) / 2 + var(--gap));
    right: 0;
    border-radius: 6px 20px 20px 6px;
    background: color-mix(in oklab, var(--on-surface) 12%, transparent);
    justify-content: flex-end;
  }
  .volume:hover .inactive {
    background: color-mix(in oklab, var(--on-surface) 16%, transparent);
  }
  /* Full: the inactive track has nothing left to show. */
  .full .inactive {
    opacity: 0;
  }
  .stop {
    width: 4px;
    height: 4px;
    margin-right: 8px;
    border-radius: 50%;
    background: var(--primary);
    flex: none;
  }
  .icon {
    width: 36px;
    display: grid;
    place-items: center;
    flex: none;
    animation: vol-icon 240ms var(--ease-in-place);
  }
  @keyframes vol-icon {
    from {
      transform: scale(0.7);
      opacity: 0;
    }
  }

  .handle {
    position: absolute;
    top: -4px;
    bottom: -4px;
    left: calc(var(--x) - var(--handle) / 2);
    width: var(--handle);
    border-radius: 2px;
    background: var(--primary);
    transition:
      left var(--d-spatial) var(--spring),
      width var(--d-fast) var(--spring-fast),
      top var(--d-fast) var(--spring-fast),
      bottom var(--d-fast) var(--spring-fast),
      background-color 300ms var(--ease-emph);
  }
  .volume:hover .handle {
    top: -6px;
    bottom: -6px;
  }
  /* Held, the handle narrows, as Expressive's does under the finger. */
  .dragging .handle {
    width: 2px;
    margin-left: 1px;
  }
  /* While held, the tracks and the handle follow the pointer exactly, not on a spring. */
  .dragging .track,
  .dragging .handle {
    transition-property: background-color, opacity, width, top, bottom;
  }
  .dragging .active {
    transition: background-color 300ms var(--ease-emph);
  }

  /* The level, over the handle. */
  .tip {
    position: absolute;
    bottom: calc(100% + 8px);
    left: 50%;
    min-width: 36px;
    padding: 4px 8px;
    border-radius: var(--r-full);
    background: var(--inverse-surface);
    color: var(--inverse-on-surface);
    font-size: 12px;
    font-weight: 700;
    line-height: 1.2;
    text-align: center;
    font-variant-numeric: tabular-nums;
    pointer-events: none;
    opacity: 0;
    translate: -50% 6px;
    scale: 0.7;
    transform-origin: 50% 100%;
    transition: opacity 150ms var(--ease-emph), translate var(--d-fast) var(--spring-fast), scale var(--d-fast) var(--spring-fast);
  }
  .shown .tip {
    opacity: 1;
    translate: -50% 0;
    scale: 1;
  }

  .muted .active {
    background: color-mix(in oklab, var(--on-surface) 22%, transparent);
    color: var(--on-surface);
  }
  .muted .handle,
  .muted .stop {
    background: var(--on-surface-variant);
  }

  @media (prefers-reduced-motion: reduce) {
    .icon {
      animation: none;
    }
  }
</style>
