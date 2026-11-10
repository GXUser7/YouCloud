<script lang="ts">
  import { onDestroy } from 'svelte'
  import { tr } from '../lib/i18n.svelte'
  import { artist, artists, serviceName, set } from '../lib/catalog'
  import { plural, tracksCount } from '../lib/format'
  import * as live from '../lib/live'
  import { player, playQueue, startRadio, toggle } from '../lib/state.svelte'
  import { reason } from '../lib/toast.svelte'
  import ArtistCard from '../components/ArtistCard.svelte'
  import Cover from '../components/Cover.svelte'
  import FollowArtist from '../components/FollowArtist.svelte'
  import Icon from '../components/Icon.svelte'
  import Loading from '../components/Loading.svelte'
  import LoadingIndicator from '../components/LoadingIndicator.svelte'
  import LoadMoreRow from '../components/LoadMoreRow.svelte'
  import Problem from '../components/Problem.svelte'
  import ServiceGlyph from '../components/ServiceGlyph.svelte'
  import SetCard from '../components/SetCard.svelte'
  import Shelf from '../components/Shelf.svelte'
  import TrackRow from '../components/TrackRow.svelte'

  let { id }: { id: string } = $props()

  const known = $derived(artists.get(id))
  const ctx = $derived('artist-' + id)
  let page = $state<live.ArtistPage | null>(null)
  let loading = $state(true)
  let error = $state('')
  let attempt = $state(0)
  let top = $state<string[]>([])
  let visible = $state(10)
  let next = $state<(() => Promise<live.ResultPage>) | undefined>()
  let paging = $state(false)
  let pageError = $state('')
  let preparing = $state(false)
  let pendingAction: 'listen' | 'shuffle' | null = null
  let pending: Promise<boolean> | null = null
  let generation = 0
  let action = 0

  $effect(() => {
    const artistId = id
    attempt
    const current = ++generation
    ++action
    page = null
    loading = true
    error = ''
    top = []
    visible = 10
    next = undefined
    paging = false
    pageError = ''
    preparing = false
    pendingAction = null
    pending = null

    live.loadArtist(artistId).then((result) => {
      if (current !== generation) return
      page = result
      top = [...new Set(result.top)]
      next = result.moreTop
      loading = false
    }).catch((e) => {
      if (current !== generation) return
      error = reason(e)
      loading = false
    })

    return () => { ++generation; ++action }
  })

  onDestroy(() => { ++generation; ++action })

  // Revealing rows and preparing a complete queue share the same continuation request.
  function requestMore(): Promise<boolean> {
    if (pending) return pending
    const fetch = next
    if (!fetch) return Promise.resolve(true)
    const current = generation
    paging = true
    pageError = ''
    const task = Promise.resolve().then(fetch).then((result) => {
      if (current !== generation) return false
      top = [...new Set([...top, ...result.items])]
      next = result.next
      return true
    }).catch((e) => {
      if (current === generation) pageError = reason(e)
      return false
    }).finally(() => {
      if (current === generation && pending === task) {
        paging = false
        pending = null
      }
    })
    pending = task
    return task
  }

  async function reveal() {
    if (paging || preparing) return
    if (pendingAction) {
      await playPopular(pendingAction)
      return
    }
    if (visible < top.length) {
      visible = Math.min(top.length, visible + 10)
      return
    }
    const current = generation
    const revealTo = top.length + 10
    if (await requestMore() && current === generation) visible = Math.min(top.length, revealTo)
  }

  async function playPopular(mode: 'listen' | 'shuffle') {
    if (!page || preparing) return
    pendingAction = null
    const queued = new Set(player.queue)
    const completeQueue = !next && player.queue.length === top.length && top.every((t) => queued.has(t))
    if (mode === 'listen' && player.contextId === ctx && (player.playing || completeQueue)) {
      toggle()
      return
    }

    const current = generation
    const run = ++action
    const contextId = ctx
    const name = artist(page.artist).name
    const previousQueue = player.queue
    const previousIndex = player.index
    const previousPlaying = player.playing
    pendingAction = mode
    preparing = true
    try {
      while (next) {
        if (!await requestMore() || current !== generation || run !== action) return
      }
      // Do not replace music the listener chose while the remaining pages were loading.
      if (current !== generation || run !== action || player.queue !== previousQueue || player.index !== previousIndex || player.playing !== previousPlaying) return
      const ids = [...top]
      if (mode === 'shuffle') {
        for (let i = ids.length - 1; i > 0; i--) {
          const j = Math.floor(Math.random() * (i + 1))
          ;[ids[i], ids[j]] = [ids[j], ids[i]]
        }
      }
      pendingAction = null
      playQueue(ids, 0, name, contextId)
    } finally {
      if (current === generation && run === action) {
        preparing = false
        if (!pageError) pendingAction = null
      }
    }
  }
</script>

{#if loading}
  {#if known}{@render hero(known.id)}{/if}
  <Loading text={tr('Загружаю артиста…')} tall={!known} />
{:else if error}
  {#if known}{@render hero(known.id)}{/if}
  <Problem message={error} onretry={() => attempt++} />
{:else if page}
  {@const a = artist(page.artist)}
  {@const playingThis = player.contextId === ctx && player.playing}
  {@render hero(a.id)}

  <div class="bar">
    <div class="actions">
      <button
        class="btn filled large listen"
        class:playing={playingThis}
        disabled={preparing || (!top.length && !next)}
        onclick={() => playPopular('listen')}
      >
        {#if preparing}
          <LoadingIndicator size={24} contained={false} />
        {:else}
          <Icon name={playingThis ? 'pause' : 'play_arrow'} fill />
        {/if}
        {playingThis ? tr('Пауза') : tr('Слушать')}
      </button>
      <button class="icon-btn tonal l shuffle" disabled={preparing || (!top.length && !next)} onclick={() => playPopular('shuffle')} aria-label={tr('Перемешать')} title={tr('Перемешать')}>
        <Icon name="shuffle" />
      </button>
    </div>
    <FollowArtist id={a.id} />
    {#if top[0]}
      <button class="icon-btn glassy l" disabled={preparing} onclick={() => startRadio(top[0])} aria-label={tr('Радио по артисту')} title={tr('Радио по артисту')}><Icon name="radio" /></button>
    {/if}
    <span class="spacer"></span>
    {#if a.link}
      <a class="btn glassy service-link" href={a.link} target="_blank" rel="noreferrer" aria-label={tr('Открыть в %s', serviceName(a.service))}>
        <Icon name="open_in_new" size={20} />{serviceName(a.service)}
      </a>
    {/if}
  </div>
  <div class="action-status muted" role="status" aria-live="polite">
    {#if preparing}{tr('Готовлю все популярные треки…')}{/if}
  </div>

  <section class="section popular">
    <div class="section-head">
      <h2>{tr('Популярные треки')}</h2>
      {#if top.length && !next}<span class="muted count">{tracksCount(top.length)}</span>{/if}
    </div>
    <div class="track-list glass" aria-busy={paging || preparing}>
      {#each top.slice(0, visible) as t, i (t)}
        <TrackRow id={t} list={top} context={a.name} contextId={ctx} number={i + 1} />
      {:else}
        {#if !next && !paging}<p class="muted empty">{tr('Здесь пока пусто.')}</p>{/if}
      {/each}
      {#if visible < top.length || next || paging || pageError || preparing}
        <LoadMoreRow loading={paging || preparing} error={pageError} onclick={reveal} />
      {/if}
    </div>
  </section>

  {#if page.releases.length}
    <Shelf title={tr('Альбомы и синглы')}>
      {#each page.releases as r (r)}
        {@const s = set(r)}
        <SetCard id={r} size={176} subtitle="{s.kind === 'single' ? tr('Сингл') : tr('Альбом')}{s.year ? ` · ${s.year}` : ''}" />
      {/each}
    </Shelf>
  {/if}

  {#if page.playlists.length}
    <Shelf title={tr('Плейлисты')}>
      {#each page.playlists as p (p)}<SetCard id={p} size={176} />{/each}
    </Shelf>
  {/if}

  {#if page.similar.length}
    <Shelf title={tr('Похожие артисты')}>
      {#each page.similar as s (s)}<ArtistCard id={s} size={150} />{/each}
    </Shelf>
  {/if}
{/if}

{#snippet hero(artistId: string)}
  {@const a = artist(artistId)}
  <section class="hero">
    <div class="ambient" aria-hidden="true"><Cover seed={'artist-' + a.id} color={a.color} src={a.image} kind="artist" /></div>
    <div class="photo" aria-hidden="true"><Cover seed={'artist-' + a.id} color={a.color} src={a.image} kind="artist" /></div>
    <div class="shade"></div>
    <div class="who">
      <span class="overline kind"><ServiceGlyph service={a.service} size={16} />{tr('Артист · %s', serviceName(a.service))}</span>
      <h1 class="display">{a.name}</h1>
      {#if a.followers || a.albums}
        <p class="stats">{[a.followers, a.albums ? plural(a.albums, 'альбом', 'альбома', 'альбомов') : ''].filter(Boolean).join(' · ')}</p>
      {/if}
    </div>
  </section>
{/snippet}

<style>
  /* The photo is the hero itself: on the right at the hero's full height, no frame, dissolving
     into the page on its left and lower edges, the name running over that edge. It is drawn no
     larger than the hero, so a 1000 px picture stays sharp instead of stretched across the page. */
  .hero {
    position: relative;
    isolation: isolate;
    display: flex;
    align-items: flex-end;
    min-height: clamp(340px, 50vh, 560px);
    padding: clamp(28px, 4cqw, 56px);
    margin-top: 8px;
    border-radius: var(--r-2xl);
    overflow: hidden;
    background: var(--glass);
  }
  .ambient {
    position: absolute;
    z-index: -3;
    inset: -80px;
    opacity: 0.45;
    filter: blur(70px) saturate(1.1);
    pointer-events: none;
  }
  .photo {
    position: absolute;
    z-index: -2;
    top: 0;
    right: 0;
    bottom: 0;
    width: min(66%, 760px);
    -webkit-mask-image: linear-gradient(90deg, transparent, #000 42%), linear-gradient(0deg, transparent, #000 34%);
    -webkit-mask-composite: source-in;
    mask-image: linear-gradient(90deg, transparent, #000 42%), linear-gradient(0deg, transparent, #000 34%);
    mask-composite: intersect;
    animation: settle var(--d-slow) var(--spring) both;
    pointer-events: none;
  }
  .photo :global(.cover) {
    object-position: center 22%;
  }
  .shade {
    position: absolute;
    z-index: -1;
    inset: 0;
    background: linear-gradient(0deg, color-mix(in oklab, var(--surface) 55%, transparent), transparent 55%);
    pointer-events: none;
  }
  @keyframes settle {
    from {
      opacity: 0;
      transform: scale(1.06);
    }
  }
  .who {
    min-width: 0;
    max-width: min(72%, 1000px);
    text-shadow: 0 2px 30px color-mix(in oklab, var(--surface) 60%, transparent);
    animation: rise var(--d-slow) var(--spring) 60ms both;
  }
  .kind {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 8px;
    line-height: 1.6;
  }
  h1 {
    font-size: clamp(36px, 6.4cqw, 104px);
    line-height: 1.05;
    margin: 16px 0;
    overflow-wrap: anywhere;
  }
  .stats {
    color: var(--on-surface-variant);
    font-size: 15px;
    line-height: 1.6;
  }
  .bar {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 12px;
    margin: 28px 0 0;
  }
  .actions {
    display: flex;
    gap: 3px;
  }
  .listen {
    --h: 60px;
    --r: 30px 10px 10px 30px;
    padding: 0 28px 0 24px;
    font-size: 17px;
  }
  .listen.playing {
    --r: 20px 10px 10px 20px;
  }
  .listen :global(.loader path) {
    fill: currentColor;
  }
  .shuffle {
    --s: 60px;
    --r: 10px 30px 30px 10px;
  }
  .bar button:disabled {
    opacity: 0.38;
    pointer-events: none;
  }
  .spacer {
    flex: 1;
  }
  .action-status {
    min-height: 24px;
    padding-top: 8px;
    font-size: 13px;
  }
  .popular {
    margin-top: 20px;
  }
  .count {
    font-size: 13px;
    white-space: nowrap;
  }
  .empty {
    padding: 28px 24px;
    font-size: 14px;
  }
  @container page (max-width: 760px) {
    .hero {
      padding: 24px;
    }
    .photo {
      width: 82%;
    }
    .who {
      max-width: 100%;
    }
  }
  @container page (max-width: 520px) {
    .hero {
      min-height: 0;
      padding-top: min(78cqw, 380px);
    }
    .photo {
      left: 0;
      width: auto;
      bottom: auto;
      height: min(100cqw, 460px);
      -webkit-mask-image: linear-gradient(0deg, transparent, #000 45%);
      mask-image: linear-gradient(0deg, transparent, #000 45%);
    }
    h1 {
      font-size: clamp(30px, 8cqw, 42px);
      margin: 12px 0;
    }
    .bar {
      gap: 10px;
      margin-top: 20px;
    }
    .service-link {
      --h: 40px;
      padding: 0 16px;
      font-size: 13px;
    }
    .spacer {
      display: none;
    }
    .section-head {
      align-items: flex-start;
      flex-wrap: wrap;
      gap: 8px;
    }
    .section-head h2 {
      font-size: 19px;
    }
  }
</style>
