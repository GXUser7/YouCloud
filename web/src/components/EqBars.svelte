<script lang="ts">
  // Bars that bounce while a track plays and rest when it is paused.
  let { playing = true, size = 18 }: { playing?: boolean; size?: number } = $props()
</script>

<span class="eq" class:paused={!playing} style:--size="{size}px" aria-hidden="true">
  <i></i><i></i><i></i><i></i>
</span>

<style>
  .eq {
    display: inline-flex;
    align-items: center;
    gap: calc(var(--size) * 0.12);
    height: var(--size);
    width: var(--size);
    justify-content: center;
  }
  i {
    width: calc(var(--size) * 0.14);
    height: 100%;
    border-radius: 99px;
    background: currentColor;
    transform-origin: center;
    animation: bounce 900ms var(--ease-emph) infinite alternate;
  }
  i:nth-child(2) {
    animation-delay: -300ms;
    animation-duration: 700ms;
  }
  i:nth-child(3) {
    animation-delay: -520ms;
    animation-duration: 1100ms;
  }
  i:nth-child(4) {
    animation-delay: -150ms;
    animation-duration: 800ms;
  }
  .paused i {
    animation-play-state: paused;
  }
  @keyframes bounce {
    0% {
      transform: scaleY(0.25);
    }
    100% {
      transform: scaleY(1);
    }
  }
</style>
