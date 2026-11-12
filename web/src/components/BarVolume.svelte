<script lang="ts">
  // Volume in the bar along the bottom: the speaker, which mutes and brings the sound back, and a
  // slim slider beside it. The rail thickens and the knob comes out under the pointer, and the
  // level shows above the knob while it is held. Anywhere on the slider sets the level — a click,
  // a drag, the wheel (over the speaker too), the arrow keys. The full player keeps its own
  // control (VolumeControl).
  import { tr } from '../lib/i18n.svelte'
  import { player } from '../lib/state.svelte'
  import Icon from './Icon.svelte'

  let track: HTMLSpanElement
  let dragging = $state(false)

  const level = $derived(player.muted ? 0 : player.volume)
  const icon = $derived(level === 0 ? 'volume_off' : level < 0.4 ? 'volume_mute' : level < 0.75 ? 'volume_down' : 'volume_up')

  function set(v: number) {
    const clamped = Math.max(0, Math.min(1, v))
    player.volume = Math.round(clamped * 100) / 100
    player.muted = clamped === 0
  }

  function at(clientX: number) {
    const rect = track.getBoundingClientRect()
    return (clientX - rect.left) / rect.width
  }

  function down(e: PointerEvent) {
    if (e.button !== 0) return
    ;(e.currentTarget as HTMLElement).setPointerCapture(e.pointerId)
    dragging = true
    set(at(e.clientX))
  }

  function move(e: PointerEvent) {
    if (dragging) set(at(e.clientX))
  }

  function up() {
    dragging = false
  }

  function wheel(e: WheelEvent) {
    e.preventDefault()
    set(level + (e.deltaY < 0 ? 0.05 : -0.05))
  }

  function key(e: KeyboardEvent) {
    const step = e.key === 'ArrowRight' || e.key === 'ArrowUp' ? 0.05 : e.key === 'ArrowLeft' || e.key === 'ArrowDown' ? -0.05 : 0
    if (step) {
      e.preventDefault()
      set(level + step)
    } else if (e.key === 'Home' || e.key === 'End') {
      e.preventDefault()
      set(e.key === 'Home' ? 0 : 1)
    }
  }

  function mute() {
    player.muted = !player.muted
    if (!player.muted && player.volume === 0) player.volume = 0.5
  }
</script>

<div class="bv" class:dragging class:muted={level === 0} style:--v={level} onwheel={wheel}>
  <button
    class="speaker"
    onclick={mute}
    aria-label={level === 0 ? tr('Включить звук') : tr('Без звука')}
    title={level === 0 ? tr('Включить звук') : tr('Без звука')}
  >
    {#key icon}<span class="ic"><Icon name={icon} fill size={20} /></span>{/key}
  </button>
  <div
    class="slider"
    role="slider"
    tabindex="0"
    aria-label={tr('Громкость')}
    aria-valuemin={0}
    aria-valuemax={100}
    aria-valuenow={Math.round(level * 100)}
    onpointerdown={down}
    onpointermove={move}
    onpointerup={up}
    onpointercancel={up}
    onkeydown={key}
  >
    <span class="track" bind:this={track}>
      <span class="rail"><span class="fill"></span></span>
      <span class="knob"></span>
      <span class="tip">{Math.round(level * 100)}</span>
    </span>
  </div>
</div>

<style>
  .bv {
    display: flex;
    align-items: center;
    gap: 2px;
    flex: none;
  }
  .speaker {
    width: 40px;
    height: 40px;
    display: grid;
    place-items: center;
    border-radius: 50%;
    color: var(--on-surface-variant);
    position: relative;
    isolation: isolate;
    transition: color 200ms var(--ease-emph);
  }
  .speaker::before {
    content: '';
    position: absolute;
    inset: 0;
    z-index: -1;
    border-radius: inherit;
    background: currentColor;
    opacity: 0;
    transform: scale(0.55);
    transition: opacity 180ms var(--ease-emph), transform var(--d-fast) var(--spring-fast);
  }
  .speaker:hover {
    color: var(--on-surface);
  }
  .speaker:hover::before {
    opacity: 0.1;
    transform: none;
  }
  .speaker:active::before {
    opacity: 0.16;
    transform: scale(0.9);
  }
  .speaker .ic {
    display: grid;
    animation: bv-swap 240ms var(--ease-in-place);
  }
  @keyframes bv-swap {
    from {
      transform: scale(0.7);
      opacity: 0;
    }
  }
  .muted .speaker {
    color: var(--on-surface);
  }

  /* The slider's box is taller than its rail, so it is easy to catch. */
  .slider {
    width: 96px;
    height: 32px;
    display: flex;
    align-items: center;
    padding: 0 7px;
    cursor: pointer;
    touch-action: none;
    border-radius: var(--r-full);
  }
  .slider:focus-visible {
    outline-offset: 0;
  }
  .track {
    position: relative;
    flex: 1;
    height: 14px;
  }
  .rail {
    position: absolute;
    left: 0;
    right: 0;
    top: 5px;
    height: 4px;
    border-radius: var(--r-full);
    overflow: hidden;
    background: color-mix(in oklab, var(--on-surface) 18%, transparent);
    transition: transform var(--d-fast) var(--spring-fast);
  }
  .fill {
    position: absolute;
    inset: 0 auto 0 0;
    width: calc(var(--v) * 100%);
    background: var(--on-surface-variant);
    transition: width 200ms var(--ease-emph), background-color 200ms var(--ease-emph);
  }
  .dragging .fill {
    transition: background-color 200ms var(--ease-emph);
  }
  .knob {
    position: absolute;
    top: 0;
    left: calc(var(--v) * 100%);
    width: 14px;
    height: 14px;
    margin-left: -7px;
    border-radius: 50%;
    background: var(--primary);
    box-shadow: 0 2px 8px -2px color-mix(in oklab, #000 60%, transparent);
    transform: scale(0);
    transition: transform var(--d-fast) var(--spring-fast), left 200ms var(--ease-emph);
  }
  .tip {
    position: absolute;
    bottom: calc(100% + 10px);
    left: calc(var(--v) * 100%);
    min-width: 34px;
    padding: 3px 6px;
    border-radius: var(--r-xs);
    background: var(--inverse-surface);
    color: var(--inverse-on-surface);
    font-size: 12px;
    font-weight: 700;
    line-height: 1.3;
    text-align: center;
    font-variant-numeric: tabular-nums;
    pointer-events: none;
    opacity: 0;
    translate: -50% 4px;
    scale: 0.85;
    transition: opacity 150ms var(--ease-emph), translate 200ms var(--ease-emph), scale 200ms var(--ease-emph), left 200ms var(--ease-emph);
  }
  .dragging .knob,
  .dragging .tip {
    transition-property: transform, opacity, translate, scale;
  }

  /* Under the pointer or held: the rail thickens, takes the theme's colour and shows its knob. */
  .slider:hover .rail,
  .slider:focus-visible .rail,
  .dragging .rail {
    transform: scaleY(1.5);
  }
  .slider:hover .fill,
  .slider:focus-visible .fill,
  .dragging .fill {
    background: var(--primary);
  }
  .slider:hover .knob,
  .slider:focus-visible .knob {
    transform: none;
  }
  .dragging .knob {
    transform: scale(1.2);
  }
  .dragging .tip,
  .slider:focus-visible .tip {
    opacity: 1;
    translate: -50% 0;
    scale: 1;
  }
</style>
