<script lang="ts">
  // "Моя форма": Yandex's wave as one large shape that turns slowly while the wave plays and takes
  // the form of the mood picked — more lobes for "Бодрое", a soft few for "Грустное".
  import { pathFromRadii, waveRadii } from '../lib/shapes'
  import PlayButton from './PlayButton.svelte'

  let {
    lobes = 10,
    depth = 0.07,
    playing,
    onclick,
  }: { lobes?: number; depth?: number; playing: boolean; onclick: () => void } = $props()

  // Lobes are drawn at the same angles whatever their number, so the path morphs between moods.
  const outer = $derived(pathFromRadii(waveRadii(lobes, depth)))
  const inner = $derived(pathFromRadii(waveRadii(lobes, depth * 1.4), 0.78, 180 / lobes))
</script>

<div class="wave" class:playing>
  <!-- It turns as a whole picture, on the GPU: turning a group inside the SVG drew the shape and
       its blurred shadow again for every frame. -->
  <div class="spin">
  <svg viewBox="0 0 100 100" aria-hidden="true">
    <defs>
      <linearGradient id="wave-fill" x1="0" y1="0" x2="1" y2="1">
        <stop offset="0" style="stop-color: var(--primary)" />
        <stop offset="1" style="stop-color: color-mix(in oklab, var(--primary) 55%, var(--tertiary))" />
      </linearGradient>
    </defs>
    <path class="shadow" d={outer} />
    <path class="outer" d={outer} fill="url(#wave-fill)" />
    <path class="inner" d={inner} />
  </svg>
  </div>
  <div class="center">
    <PlayButton {playing} {onclick} size={104} label={playing ? 'Пауза' : 'Включить волну'} />
  </div>
</div>

<style>
  .wave {
    position: relative;
    width: 100%;
    aspect-ratio: 1;
    display: grid;
    place-items: center;
  }
  .spin {
    position: absolute;
    inset: 0;
    animation: turn 40s linear infinite;
    animation-play-state: paused;
    will-change: transform;
  }
  svg {
    width: 100%;
    height: 100%;
    overflow: visible;
  }
  .playing .spin {
    animation-play-state: running;
  }
  path {
    transition: d 900ms var(--spring);
  }
  .shadow {
    fill: #000;
    opacity: 0.25;
    filter: blur(6px);
    transform: translate(0, 3px);
  }
  .inner {
    fill: color-mix(in oklab, var(--on-primary) 14%, transparent);
  }
  .center {
    position: relative;
    transition: transform var(--d-slow) var(--spring);
  }
  .playing .center {
    animation: breathe 3.2s ease-in-out infinite;
  }
  .center :global(.play) {
    background: var(--on-primary);
    color: var(--primary);
  }
  @keyframes turn {
    to {
      transform: rotate(360deg);
    }
  }
  @keyframes breathe {
    50% {
      transform: scale(1.06);
    }
  }
</style>
