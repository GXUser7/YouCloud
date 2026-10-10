<script lang="ts">
  // The play button: round while paused, squaring while it plays, as in the app; pressing it
  // squeezes it the other way, and the icon turns as it swaps.
  import Icon from './Icon.svelte'

  let { playing, onclick, size = 56, label = '' }: { playing: boolean; onclick: () => void; size?: number; label?: string } = $props()
</script>

<button
  class="play"
  class:playing
  style:--s="{size}px"
  {onclick}
  aria-label={label || (playing ? 'Пауза' : 'Играть')}
>
  {#key playing}
    <span class="glyph"><Icon name={playing ? 'pause' : 'play_arrow'} fill size={size * 0.46} /></span>
  {/key}
</button>

<style>
  .play {
    width: var(--s);
    height: var(--s);
    flex: none;
    display: grid;
    place-items: center;
    border-radius: 50%;
    background: var(--primary);
    color: var(--on-primary);
    position: relative;
    box-shadow: 0 10px 30px -12px color-mix(in oklab, var(--primary) 70%, transparent);
    transition:
      border-radius var(--d-spatial) var(--spring-fast),
      transform var(--d-fast) var(--spring-fast),
      background-color 400ms var(--ease-emph);
  }
  .play::after {
    content: '';
    position: absolute;
    inset: 0;
    border-radius: inherit;
    background: currentColor;
    opacity: 0;
    transition: opacity 150ms;
  }
  .play:hover::after {
    opacity: 0.08;
  }
  .play.playing {
    border-radius: 30%;
  }
  .play:active {
    border-radius: 22%;
    transform: scale(0.94);
  }
  .play.playing:active {
    border-radius: 50%;
  }
  .glyph {
    grid-area: 1 / 1;
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
