<script lang="ts">
  // A titled row that scrolls sideways, with arrows that appear only where there is more to see.
  import type { Snippet } from 'svelte'
  import Icon from './Icon.svelte'

  let { title, subtitle = '', children, action }: { title: string; subtitle?: string; children: Snippet; action?: Snippet } = $props()

  let track: HTMLDivElement
  let canBack = $state(false)
  let canForward = $state(true)

  function update() {
    if (!track) return
    canBack = track.scrollLeft > 4
    canForward = track.scrollLeft + track.clientWidth < track.scrollWidth - 4
  }

  function page(dir: number) {
    track.scrollBy({ left: dir * track.clientWidth * 0.8, behavior: 'smooth' })
  }

  $effect(() => {
    update()
    const observer = new ResizeObserver(update)
    observer.observe(track)
    return () => observer.disconnect()
  })
</script>

<section class="section shelf">
  <div class="section-head">
    <div>
      <h2>{title}</h2>
      {#if subtitle}<p class="muted sub">{subtitle}</p>{/if}
    </div>
    <div class="tools">
      {@render action?.()}
      <button class="icon-btn s glassy" disabled={!canBack} onclick={() => page(-1)} aria-label="Назад">
        <Icon name="chevron_left" />
      </button>
      <button class="icon-btn s glassy" disabled={!canForward} onclick={() => page(1)} aria-label="Дальше">
        <Icon name="chevron_right" />
      </button>
    </div>
  </div>
  <div class="track" bind:this={track} onscroll={update}>
    {@render children()}
  </div>
</section>

<style>
  .sub {
    margin-top: 4px;
    font-size: 14px;
  }
  .tools {
    display: flex;
    gap: 6px;
    align-items: center;
  }
  .tools button:disabled {
    opacity: 0.3;
    pointer-events: none;
  }
  .track {
    display: flex;
    gap: 20px;
    overflow-x: auto;
    scroll-snap-type: x proximity;
    padding: 6px 4px 18px;
    margin: -6px -4px -18px;
    scrollbar-width: none;
  }
  .track::-webkit-scrollbar {
    display: none;
  }
  .track > :global(*) {
    scroll-snap-align: start;
  }
</style>
