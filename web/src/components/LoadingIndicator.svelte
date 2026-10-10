<script lang="ts">
  // Material 3 Expressive's loading indicator: one shape turning while it morphs through the
  // others, on a round container.
  import { shapePath, type ShapeName } from '../lib/shapes'

  let { size = 48, contained = true }: { size?: number; contained?: boolean } = $props()

  const cycle: ShapeName[] = ['softBurst', 'cookie9', 'pentagon', 'puffy', 'sunny', 'cookie4', 'clover4', 'gem']
  let step = $state(0)

  $effect(() => {
    const timer = setInterval(() => (step = (step + 1) % cycle.length), 650)
    return () => clearInterval(timer)
  })
</script>

<span class="loader" class:contained style:--s="{size}px" role="progressbar" aria-label="Загрузка">
  <svg viewBox="0 0 100 100">
    <path d={shapePath(cycle[step], contained ? 0.62 : 0.9)} />
  </svg>
</span>

<style>
  .loader {
    width: var(--s);
    height: var(--s);
    display: inline-grid;
    place-items: center;
    border-radius: 50%;
  }
  .contained {
    background: var(--primary-container);
  }
  svg {
    width: 100%;
    height: 100%;
    animation: turn 2.6s linear infinite;
  }
  path {
    fill: var(--primary);
    transition: d 600ms var(--spring-fast);
  }
  .contained path {
    fill: var(--on-primary-container);
  }
  @keyframes turn {
    to {
      transform: rotate(360deg);
    }
  }
</style>
