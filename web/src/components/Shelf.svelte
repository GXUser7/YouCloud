<script lang="ts">
  // A titled row of whole cards on the page grid that scrolls sideways, a page of cards at a time,
  // with arrows that appear only where there is more to see. Given [more], it asks for the next
  // cards as the row nears its end — and at once while the row isn't full yet — and shows that it
  // is loading, or what went wrong, in a card's place at the end.
  import { tr } from '../lib/i18n.svelte'
  import type { Snippet } from 'svelte'
  import Icon from './Icon.svelte'
  import LoadingIndicator from './LoadingIndicator.svelte'

  let {
    title,
    subtitle = '',
    children,
    action,
    more,
    loading = false,
    error = '',
  }: {
    title: string
    subtitle?: string
    children: Snippet
    action?: Snippet
    /** Asks for the next cards; absent when there are no more. */
    more?: () => void
    loading?: boolean
    error?: string
  } = $props()

  let track: HTMLDivElement
  let canBack = $state(false)
  let canForward = $state(true)

  function update() {
    if (!track) return
    canBack = track.scrollLeft > 4
    const left = track.scrollWidth - track.clientWidth - track.scrollLeft
    canForward = left > 4 || !!more
    // Within half a page of the end (or not even a page of cards yet): the next ones, now.
    if (more && !loading && !error && left < track.clientWidth / 2) more()
  }

  // A page of whole cards at a time: the row's width and one gap more, so the next page starts
  // on a card's edge, where the last one ended.
  function page(dir: number) {
    const gap = parseFloat(getComputedStyle(track).columnGap) || 0
    track.scrollBy({ left: dir * (track.clientWidth + gap), behavior: 'smooth' })
  }

  $effect(() => {
    update()
    const observer = new ResizeObserver(update)
    observer.observe(track)
    return () => observer.disconnect()
  })
  // New cards in, or a load over: whether there is more to see, or to ask for, again.
  $effect(() => {
    void loading
    void more
    queueMicrotask(update)
  })
</script>

<section class="section shelf rise">
  <div class="section-head">
    <div>
      <h2>{title}</h2>
      {#if subtitle}<p class="muted sub">{subtitle}</p>{/if}
    </div>
    <div class="tools">
      {@render action?.()}
      <!-- Only when there is more than fits: two dimmed arrows over a row that doesn't scroll
           pointed at nothing. -->
      {#if canBack || canForward}
        <button class="icon-btn s glassy nav" disabled={!canBack} onclick={() => page(-1)} aria-label={tr('Назад')}>
          <Icon name="chevron_left" />
        </button>
        <button class="icon-btn s glassy nav" disabled={!canForward} onclick={() => page(1)} aria-label={tr('Дальше')}>
          <Icon name="chevron_right" />
        </button>
      {/if}
    </div>
  </div>
  <div class="track" bind:this={track} onscroll={update}>
    {@render children()}
    {#if loading}
      <div class="end" aria-busy="true"><LoadingIndicator size={40} contained={false} /></div>
    {:else if error}
      <button class="end retry" onclick={() => more?.()} title={error}>
        <Icon name="refresh" size={24} />
        <span>{tr('Повторить')}</span>
      </button>
    {/if}
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
  /* In a card's place at the end of the row: loading, or a way to try again. */
  .end {
    flex: none;
    width: var(--w, 160px);
    aspect-ratio: 1;
    display: grid;
    place-items: center;
    align-content: center;
    gap: 8px;
    border-radius: var(--r-xl);
    background: color-mix(in oklab, var(--on-surface) 6%, transparent);
    color: var(--on-surface-variant);
    scroll-snap-align: start;
  }
  .retry {
    font-weight: 650;
    color: var(--primary);
    transition: background-color 200ms var(--ease-emph), transform var(--d-fast) var(--spring-fast);
  }
  .retry:hover {
    background: color-mix(in oklab, var(--primary) 10%, transparent);
  }
  .retry:active {
    transform: scale(0.97);
  }
  .nav :global(.icon) {
    transition: transform var(--d-fast) var(--spring-fast);
  }
  .nav:first-of-type:hover :global(.icon) {
    transform: translateX(-2px);
  }
  .nav:last-of-type:hover :global(.icon) {
    transform: translateX(2px);
  }
  /* Cards of the page grid's width (app.css, --card), the grid's gap between them, flush with the
     page's edges. Room above and below for a card to lift and cast its shadow; none to the sides,
     where it would put the first card off the column it belongs on. */
  .track {
    --w: var(--card);
    display: flex;
    gap: var(--gap);
    overflow-x: auto;
    scroll-snap-type: x mandatory;
    padding: 8px 0 24px;
    margin: -8px 0 -24px;
    scrollbar-width: none;
  }
  .track::-webkit-scrollbar {
    display: none;
  }
  .track > :global(*) {
    scroll-snap-align: start;
  }
</style>
