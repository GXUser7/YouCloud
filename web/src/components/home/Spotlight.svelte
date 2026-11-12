<script lang="ts">
  // What a service's home leads with, one set at a time, on matte glass: the set's cover large on
  // the left, its name and what to do with it on the right, the other sets of the row as small
  // covers under them; picking one brings it into the light. The glass stays the same whatever
  // set is picked — only the cover and the words change. The picked cover takes a cookie's shape, the rest stay
  // rounded squares — morphed (lib/shapes), never by animating border-radius.
  import { tr } from '../../lib/i18n.svelte'
  import { set } from '../../lib/catalog'
  import { go, player, playSet, toggle } from '../../lib/state.svelte'
  import { roundedSquarePolygon } from '../../lib/shapes'
  import Cover from '../Cover.svelte'
  import Icon from '../Icon.svelte'

  let { lead, title }: { lead: string[]; title: string } = $props()

  const picks = $derived(lead.slice(0, 7))
  let current = $state(0)
  $effect(() => {
    if (current >= picks.length) current = 0
  })
  const s = $derived(set(picks[current] ?? picks[0]))
  const playingThis = $derived(player.contextId === s.id)
  const playing = $derived(playingThis && player.playing)

  const COVER_REST = roundedSquarePolygon(28 / 130)
  const COVER_HOVER = roundedSquarePolygon(52 / 130)

  function play() {
    if (playingThis) toggle()
    else playSet(s.id)
  }

  // The arrow keys move along the small covers, as along any row of options.
  function key(e: KeyboardEvent) {
    const step = e.key === 'ArrowRight' ? 1 : e.key === 'ArrowLeft' ? -1 : 0
    if (!step) return
    e.preventDefault()
    current = (current + step + picks.length) % picks.length
    ;(e.currentTarget as HTMLElement).querySelectorAll<HTMLElement>('.thumb')[current]?.focus()
  }
</script>

{#if picks.length}
  <section class="spotlight glass rise" style:--rest={COVER_REST} style:--hover={COVER_HOVER} aria-label={title}>
    <button class="cover" onclick={() => go('set/' + s.id)} aria-label={s.title}>
      {#key s.id}
        <span class="cover-art"><Cover seed={s.id} color={s.color} src={s.cover} kind={s.kind} label={s.label} /></span>
      {/key}
    </button>

    <div class="info">
      {#if title}<span class="kicker">{title}</span>{/if}
      {#key s.id}
        <div class="words">
          <h2 class="display">{s.title}</h2>
          {#if s.subtitle}<p class="sub">{s.subtitle}</p>{/if}
        </div>
      {/key}
      <div class="actions">
        <button class="btn filled large play-btn" class:playing onclick={play}>
          {#key playing}<span class="ic"><Icon name={playing ? 'pause' : 'play_arrow'} fill size={24} /></span>{/key}
          {playing ? tr('Пауза') : tr('Слушать')}
        </button>
        <button class="icon-btn tonal l" onclick={() => playSet(s.id, true)} aria-label={tr('Перемешать')} title={tr('Перемешать')}>
          <Icon name="shuffle" size={24} />
        </button>
        <button class="icon-btn glassy l" onclick={() => go('set/' + s.id)} aria-label={tr('Открыть')} title={tr('Открыть')}>
          <Icon name="arrow_forward" size={24} />
        </button>
      </div>
    </div>

    {#if picks.length > 1}
      <div class="thumbs" role="tablist" aria-label={title} tabindex="-1" onkeydown={key}>
        {#each picks as id, i (id)}
          {@const t = set(id)}
          <button
            class="thumb"
            class:on={i === current}
            role="tab"
            aria-selected={i === current}
            tabindex={i === current ? 0 : -1}
            onclick={() => (current = i)}
            aria-label={t.title}
            title={t.title}
          >
            <span class="thumb-art"><Cover seed={t.id} color={t.color} src={t.cover} kind={t.kind} /></span>
          </button>
        {/each}
      </div>
    {/if}
  </section>
{/if}

<style>
  .spotlight {
    position: relative;
    isolation: isolate;
    overflow: clip;
    margin-top: var(--gap);
    border-radius: var(--r-2xl);
    padding: var(--pad);
    display: grid;
    grid-template-columns: auto minmax(0, 1fr);
    grid-template-areas:
      'cover info'
      'cover thumbs';
    grid-template-rows: 1fr auto;
    column-gap: var(--gap);
    row-gap: var(--gap);
  }

  /* ---- The cover ---- */
  /* Two of the grid's cards wide, less the panel's padding: the words then start on the page's
     fifth column, the edge of the third card in the rows below. */
  .cover {
    grid-area: cover;
    width: calc(var(--card) * 2 + var(--gap) - var(--pad));
    aspect-ratio: 1;
    align-self: center;
    filter: drop-shadow(0 22px 32px color-mix(in oklab, #000 45%, transparent));
    transition: transform var(--d-spatial) var(--spring);
  }
  .cover-art {
    display: block;
    width: 100%;
    height: 100%;
    clip-path: var(--rest);
    transition: clip-path var(--d-slow) var(--spring);
    animation: cover-in var(--d-slow) var(--spring) backwards;
  }
  .cover:hover {
    transform: translateY(-4px) rotate(-1.5deg);
  }
  .cover:hover .cover-art {
    clip-path: var(--hover);
  }
  .cover:active {
    transform: scale(0.97);
    transition-duration: var(--d-fast);
  }
  @keyframes cover-in {
    from {
      opacity: 0;
      transform: scale(0.88) rotate(-4deg);
    }
  }

  /* ---- What it is, and what to do with it ---- */
  /* The words from the cover's top edge, the small covers on its bottom edge: both lines of the
     panel are the cover's. */
  .info {
    grid-area: info;
    min-width: 0;
    display: flex;
    flex-direction: column;
    justify-content: flex-start;
    padding-top: 4px;
    align-items: flex-start;
    gap: 14px;
  }
  .kicker {
    font-size: 12px;
    font-weight: 800;
    letter-spacing: 0.12em;
    text-transform: uppercase;
    color: var(--on-surface-variant);
  }
  .words {
    min-width: 0;
    max-width: 100%;
    display: flex;
    flex-direction: column;
    gap: 8px;
    animation: words-in var(--d-spatial) var(--ease-in-place) backwards;
  }
  h2 {
    font-size: clamp(26px, 2.8cqw, 40px);
    display: -webkit-box;
    -webkit-line-clamp: 2;
    line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
    overflow-wrap: anywhere;
    padding-bottom: 0.06em;
  }
  .sub {
    font-size: 16px;
    color: var(--on-surface-variant);
    display: -webkit-box;
    -webkit-line-clamp: 2;
    line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }
  @keyframes words-in {
    from {
      opacity: 0;
      transform: translateY(10px);
    }
  }
  .actions {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-top: 6px;
  }
  .play-btn .ic {
    display: grid;
    animation: xb-swap 260ms var(--ease-in-place);
  }
  /* Playing, the button squares off as a toggle that is on does. */
  .play-btn.playing {
    --r: var(--r-l);
  }

  /* ---- The other sets of the row ---- */
  .thumbs {
    grid-area: thumbs;
    align-self: end;
    display: flex;
    gap: 12px;
    min-width: 0;
    overflow-x: auto;
    padding: 6px;
    margin: -6px;
    scrollbar-width: none;
  }
  .thumbs::-webkit-scrollbar {
    display: none;
  }
  .thumb {
    flex: none;
    width: 56px;
    height: 56px;
    opacity: 0.62;
    transition: opacity 250ms var(--ease-emph), transform var(--d-fast) var(--spring-fast);
  }
  .thumb-art {
    display: block;
    width: 100%;
    height: 100%;
    clip-path: var(--shape-row);
    transition: clip-path var(--d-slow) var(--spring);
  }
  .thumb:hover {
    opacity: 1;
    transform: translateY(-2px);
  }
  .thumb:active {
    transform: scale(0.92);
  }
  .thumb.on {
    opacity: 1;
  }
  .thumb.on .thumb-art {
    clip-path: var(--shape-cookie9);
  }

  @container page (max-width: 760px) {
    .spotlight {
      grid-template-columns: minmax(0, 1fr);
      grid-template-areas:
        'cover'
        'info'
        'thumbs';
      grid-template-rows: auto;
    }
    .cover {
      width: min(240px, 70cqw);
      justify-self: center;
    }
    .thumb {
      width: 48px;
      height: 48px;
    }
  }
  @media (prefers-reduced-motion: reduce) {
    .cover-art,
    .words {
      animation: none;
    }
  }
</style>
