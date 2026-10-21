<script lang="ts">
  // «Бенто»: home opens on a grid of tiles of different sizes — the leading set large, the
  // listener's likes tall beside it, two more sets small — each one its cover edge to edge.
  // Under the pointer a tile lifts, its corners open up and its play button springs out.
  import { tr } from '../../lib/i18n.svelte'
  import { serviceName, set, type Service } from '../../lib/catalog'
  import type { Library } from '../../lib/live'
  import { go, player, playQueue, playSet, toggle } from '../../lib/state.svelte'
  import { tracksCount } from '../../lib/format'
  import type { Snippet } from 'svelte'
  import Cover from '../Cover.svelte'
  import Icon from '../Icon.svelte'
  import LikedArt from '../LikedArt.svelte'
  import PlayButton from '../PlayButton.svelte'

  let {
    service,
    lead,
    leadTitle,
    lib,
    signedIn,
    main,
  }: {
    service: Service
    lead: string[]
    leadTitle: string
    lib: Library | null
    signedIn: boolean
    /** Instead of the leading set in the big tile (Yandex's wave). */
    main?: Snippet
  } = $props()

  const first = $derived(lead[0])
  const smalls = $derived(lead.slice(main ? 0 : 1, main ? 2 : 3))
  const liked = $derived(lib ? set(lib.liked) : null)

  function play(id: string) {
    if (player.contextId === id) toggle()
    else playSet(id)
  }
  function playLiked() {
    if (!liked) return
    if (player.contextId === liked.id) toggle()
    else playQueue(liked.tracks, 0, liked.title, liked.id)
  }
</script>

<section class="bento rise" class:two={!smalls.length}>
  {#if main}
    <div class="tile main custom">{@render main()}</div>
  {:else if first}
    {@const s = set(first)}
    <div class="tile main">
      <button class="bg" onclick={() => go('set/' + s.id)} aria-label={s.title}>
        <Cover seed={s.id} color={s.color} src={s.cover} kind={s.kind} label={s.label} />
      </button>
      <div class="shade"></div>
      <div class="content">
        <span class="overline">{leadTitle}</span>
        <h2 class="display">{s.title}</h2>
        {#if s.subtitle}<p class="sub ellipsis">{s.subtitle}</p>{/if}
      </div>
      <div class="actions">
        <button class="icon-btn glassy l" onclick={() => playSet(s.id, true)} aria-label={tr('Перемешать')} title={tr('Перемешать')}><Icon name="shuffle" /></button>
        <PlayButton playing={player.contextId === s.id && player.playing} onclick={() => play(s.id)} size={64} />
      </div>
    </div>
  {/if}

  <div class="tile liked">
    {#if liked}
      <button class="liked-art" onclick={() => go('set/' + liked.id)} aria-label={liked.title}>
        <LikedArt ids={liked.tracks} seed={liked.id} color={liked.color} />
      </button>
      <div class="liked-info by-play">
        <span class="overline">{tr('Любимое')}</span>
        <span class="liked-title">{liked.title}</span>
        <span class="muted count">{tracksCount(liked.tracks.length)}</span>
      </div>
      <div class="liked-play">
        <PlayButton playing={player.contextId === liked.id && player.playing} onclick={playLiked} size={52} />
      </div>
    {:else}
      <span class="liked-art blank"><LikedArt seed="liked-{service}" color="#d81b60" /></span>
      <div class="liked-info">
        <span class="overline">{tr('Любимое')}</span>
        <span class="liked-title">{signedIn ? tr('Загружаю…') : tr('Твои лайки — здесь')}</span>
        {#if !signedIn}
          <button class="btn tonal small" onclick={() => go('settings')}><Icon name="login" size={18} />{tr('Войти в %s', serviceName(service))}</button>
        {/if}
      </div>
    {/if}
  </div>

  {#each smalls as id, i (id)}
    {@const s = set(id)}
    <div class="tile small" style:grid-row={i + 1}>
      <button class="bg" onclick={() => go('set/' + s.id)} aria-label={s.title}>
        <Cover seed={s.id} color={s.color} src={s.cover} kind={s.kind} label={s.label} />
      </button>
      <div class="shade"></div>
      <div class="content">
        <span class="small-title ellipsis">{s.title}</span>
        {#if s.subtitle}<span class="small-sub ellipsis">{s.subtitle}</span>{/if}
      </div>
      <button class="pop" onclick={() => play(s.id)} aria-label={tr('Играть %s', s.title)}>
        <Icon name={player.contextId === s.id && player.playing ? 'pause' : 'play_arrow'} fill size={26} />
      </button>
    </div>
  {/each}
</section>

<style>
  .bento {
    margin-top: 28px;
    display: grid;
    grid-template-columns: 2fr 1fr 1fr;
    grid-template-rows: repeat(2, minmax(0, 1fr));
    gap: 14px;
    height: clamp(400px, 52vh, 560px);
  }
  .bento.two {
    grid-template-columns: 2fr 1fr;
  }
  .tile {
    position: relative;
    overflow: hidden;
    border-radius: var(--r-2xl);
    isolation: isolate;
    background: var(--glass);
    border: 1px solid var(--glass-line);
    transition:
      border-radius var(--d-spatial) var(--spring),
      transform var(--d-spatial) var(--spring),
      box-shadow 300ms var(--ease-emph);
  }
  .tile:hover {
    border-radius: 40px;
    transform: translateY(-3px);
    box-shadow: var(--shadow);
  }
  .main {
    grid-column: 1;
    grid-row: 1 / 3;
  }
  .liked {
    grid-column: 2;
    grid-row: 1 / 3;
    display: flex;
    flex-direction: column;
    padding: 14px;
    gap: 14px;
  }
  .small {
    grid-column: 3;
  }

  /* A cover edge to edge, darkened toward the words. */
  .bg {
    position: absolute;
    inset: 0;
    z-index: -2;
  }
  .bg :global(.cover) {
    transition: transform var(--d-slow) var(--spring);
  }
  .tile:hover .bg :global(.cover) {
    transform: scale(1.05);
  }
  .shade {
    position: absolute;
    inset: 0;
    z-index: -1;
    pointer-events: none;
    background: linear-gradient(to top, color-mix(in oklab, var(--surface) 92%, transparent) 0%, color-mix(in oklab, var(--surface) 55%, transparent) 38%, transparent 70%);
  }
  .content {
    position: absolute;
    left: 0;
    right: 0;
    bottom: 0;
    padding: 24px;
    display: flex;
    flex-direction: column;
    gap: 6px;
    pointer-events: none;
  }
  .main .content {
    padding: 28px 150px 28px 30px;
  }
  .main h2 {
    font-size: clamp(30px, 3.4vw, 52px);
    display: -webkit-box;
    -webkit-line-clamp: 2;
    line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
    color: var(--on-surface);
  }
  .sub {
    font-size: 16px;
    color: var(--on-surface-variant);
  }
  .actions {
    position: absolute;
    right: 24px;
    bottom: 24px;
    display: flex;
    align-items: center;
    gap: 10px;
  }
  .actions :global(.play) {
    transition:
      border-radius 320ms var(--ease-emph),
      transform var(--d-fast) var(--spring-fast);
  }
  .main:hover .actions :global(.play) {
    transform: scale(1.08);
  }
  .custom {
    display: grid;
    background: color-mix(in oklab, var(--primary) 14%, var(--glass));
  }

  /* Likes: the grid of what was liked last, its name, play. */
  .liked-art {
    flex: 1;
    min-height: 0;
    width: 100%;
    border-radius: var(--r-xl);
    overflow: hidden;
    display: block;
    transition: border-radius var(--d-spatial) var(--spring);
  }
  .liked:hover .liked-art {
    border-radius: 28px;
  }
  .liked-info {
    display: flex;
    flex-direction: column;
    gap: 4px;
    min-width: 0;
    padding: 0 6px 4px;
  }
  /* Kept clear of the play button in the corner: the name ran on under it. */
  .by-play {
    padding-right: 66px;
  }
  /* Two lines before it gives up: "Понравившаяся музыка" is longer than the tile is wide. */
  .liked-title {
    font-family: var(--font-display);
    font-weight: 650;
    font-size: 22px;
    letter-spacing: -0.02em;
    line-height: 1.15;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
    overflow-wrap: anywhere;
  }
  .count {
    font-size: 13px;
  }
  .liked-info .btn {
    margin-top: 8px;
    align-self: flex-start;
  }
  .liked-play {
    position: absolute;
    right: 20px;
    bottom: 20px;
  }

  /* Small tiles: the play button springs up out of the corner. */
  .small-title {
    font-weight: 750;
    font-size: 17px;
  }
  .small-sub {
    font-size: 13px;
    color: var(--on-surface-variant);
  }
  .small .content {
    padding: 18px 76px 18px 20px;
  }
  .pop {
    position: absolute;
    right: 16px;
    bottom: 16px;
    width: 52px;
    height: 52px;
    display: grid;
    place-items: center;
    border-radius: var(--r-l);
    background: var(--primary);
    color: var(--on-primary);
    box-shadow: 0 10px 24px -8px color-mix(in oklab, #000 60%, transparent);
    opacity: 0;
    transform: translateY(14px) scale(0.6) rotate(-25deg);
    transition:
      opacity 200ms var(--ease-emph),
      transform var(--d-spatial) var(--spring-fast),
      border-radius var(--d-fast) var(--spring-fast);
  }
  .small:hover .pop,
  .pop:focus-visible {
    opacity: 1;
    transform: none;
  }
  .pop:active {
    border-radius: 50%;
  }

  @container page (max-width: 980px) {
    .bento {
      grid-template-columns: 1fr 1fr;
      grid-template-rows: 340px 220px;
      height: auto;
    }
    .main {
      grid-column: 1 / 3;
      grid-row: 1;
    }
    .liked {
      grid-column: 1;
      grid-row: 2;
    }
    .small:nth-of-type(n) {
      grid-column: 2;
      grid-row: 2 !important;
    }
    .small ~ .small {
      display: none;
    }
  }
</style>
