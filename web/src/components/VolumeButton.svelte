<script lang="ts">
  // Volume in the full player: a button whose speaker shows the level; pressed, the bar's volume
  // pill (VolumeControl) stands over it on glass. The wheel over the button sets the level at
  // once. In a button group ([xb]) it is one of the group's buttons; on its own it is a round
  // tonal one, 48 across — its colours the page's (--on-surface, --primary), so a look that
  // inks its buttons differently sets those around it.
  import { tr } from '../lib/i18n.svelte'
  import { player } from '../lib/state.svelte'
  import Icon from './Icon.svelte'
  import VolumeControl from './VolumeControl.svelte'

  let { xb = false, align = 'center', size = 22 }: { xb?: boolean; align?: 'center' | 'end'; size?: number } = $props()

  let open = $state(false)
  let host: HTMLSpanElement

  const level = $derived(player.muted ? 0 : player.volume)
  const icon = $derived(level === 0 ? 'volume_off' : level < 0.4 ? 'volume_mute' : level < 0.75 ? 'volume_down' : 'volume_up')

  function wheel(e: WheelEvent) {
    e.preventDefault()
    const v = Math.max(0, Math.min(1, level + (e.deltaY < 0 ? 0.05 : -0.05)))
    player.volume = Math.round(v * 100) / 100
    player.muted = v === 0
  }

  function outside(e: PointerEvent) {
    if (!host.contains(e.target as Node)) open = false
  }

  // Escape closes the pill, not the player under it.
  function key(e: KeyboardEvent) {
    if (open && e.key === 'Escape') {
      e.stopPropagation()
      open = false
    }
  }
</script>

<svelte:window onpointerdown={open ? outside : undefined} />

<!-- svelte-ignore a11y_no_static_element_interactions -->
<span class="vb" bind:this={host} onkeydown={key}>
  <button
    class={xb ? 'xb toggle tonal' : 'round'}
    class:on={open}
    onclick={() => (open = !open)}
    onwheel={wheel}
    aria-label={tr('Громкость')}
    aria-expanded={open}
    title="{tr('Громкость')} · {Math.round(level * 100)}"
  >
    <span class="ic"><Icon name={icon} fill size={xb ? 24 : size} /></span>
  </button>
  {#if open}
    <div class="pop" data-host-glass class:end={align === 'end'}><VolumeControl /></div>
  {/if}
</span>

<style>
  .vb {
    position: relative;
    display: inline-grid;
    flex: none;
  }
  .round {
    width: 48px;
    height: 48px;
    display: grid;
    place-items: center;
    border-radius: 24px;
    background: color-mix(in oklab, var(--on-surface) 10%, transparent);
    color: var(--on-surface);
    transition: background-color 200ms var(--ease-emph), color 200ms var(--ease-emph), transform var(--d-fast) var(--spring-fast);
  }
  /* As an inline span the wrapper grows a line box (the inherited font's line-height) and the
     glyph, seated on its baseline, drifts off the circle; a grid keeps the box glyph-sized. */
  .round .ic {
    display: grid;
    place-items: center;
  }
  .round:hover {
    background: color-mix(in oklab, var(--on-surface) 15%, transparent);
  }
  .round:active {
    border-radius: 14px;
    transform: scale(0.94);
  }
  .round.on {
    border-radius: 16px;
    background: var(--primary);
    color: var(--on-primary);
  }

  /* The pill over the button, on the page's glass: no rim, a soft shadow under it. */
  .pop {
    position: absolute;
    bottom: calc(100% + 10px);
    left: 50%;
    z-index: 30;
    padding: 6px;
    border-radius: 26px;
    background: var(--vb-pop, color-mix(in oklab, var(--surface-container-high) 86%, transparent));
    -webkit-backdrop-filter: var(--glass-blur);
    backdrop-filter: var(--glass-blur);
    box-shadow: 0 18px 40px -16px #000a;
    translate: -50% 0;
    transform-origin: 50% 100%;
    animation: vb-in 260ms var(--ease-in-place);
  }
  .pop.end {
    left: auto;
    right: 0;
    translate: none;
    transform-origin: 85% 100%;
  }
  @keyframes vb-in {
    from {
      opacity: 0;
      transform: translateY(8px) scale(0.92);
    }
  }
  @media (prefers-reduced-motion: reduce) {
    .pop {
      animation: none;
    }
  }
</style>
