<script lang="ts">
  // The play button as an Expressive shape: a scalloped cookie while paused that flows into a
  // soft square while it plays, the icon turning as it swaps. The two outlines are sampled at the
  // same angles (lib/shapes.ts), so the browser morphs one into the other.
  import { shapePath } from '../lib/shapes'
  import Icon from './Icon.svelte'

  let { playing, onclick, size = 56 }: { playing: boolean; onclick: () => void; size?: number } = $props()

  const paused = shapePath('cookie9')
  const going = shapePath('square', 0.94)
</script>

<button class="morph" class:playing style:--s="{size}px" {onclick} aria-label={playing ? 'Пауза' : 'Играть'}>
  <svg viewBox="0 0 100 100" aria-hidden="true"><path d={playing ? going : paused} /></svg>
  {#key playing}
    <span class="glyph"><Icon name={playing ? 'pause' : 'play_arrow'} fill size={size * 0.44} /></span>
  {/key}
</button>

<style>
  .morph {
    width: var(--s);
    height: var(--s);
    flex: none;
    display: grid;
    place-items: center;
    position: relative;
    color: var(--on-primary);
    transition: transform var(--d-fast) var(--spring-fast);
  }
  .morph > * {
    grid-area: 1 / 1;
  }
  svg {
    width: 100%;
    height: 100%;
    overflow: visible;
    transition: transform var(--d-slow) var(--spring);
  }
  .morph:not(.playing) svg {
    transform: rotate(-20deg);
  }
  path {
    fill: var(--primary);
    transition:
      d var(--d-spatial) var(--spring),
      fill 400ms var(--ease-emph);
  }
  .morph:hover path {
    fill: color-mix(in oklab, var(--primary) 88%, var(--on-primary));
  }
  .morph:active {
    transform: scale(0.92);
  }
  /* Above the shape: a rotated shape paints over its unrotated siblings. */
  .glyph {
    position: relative;
    z-index: 1;
    display: grid;
    animation: swap var(--d-spatial) var(--spring-fast);
  }
  @keyframes swap {
    from {
      transform: rotate(-90deg) scale(0.4);
      opacity: 0;
    }
  }
</style>
