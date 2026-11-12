<script lang="ts">
  import { tr } from '../lib/i18n.svelte'
  import { onDestroy, untrack } from 'svelte'
  import { artist, artistNames, serviceName, set, track } from '../lib/catalog'
  import * as live from '../lib/live'
  import { app, go, playSet, playTrack, remember } from '../lib/state.svelte'
  import { reason } from '../lib/toast.svelte'
  import ArtistCard from '../components/ArtistCard.svelte'
  import Cover from '../components/Cover.svelte'
  import Icon from '../components/Icon.svelte'
  import Loading from '../components/Loading.svelte'
  import LoadMoreRow from '../components/LoadMoreRow.svelte'
  import Problem from '../components/Problem.svelte'
  import SetCard from '../components/SetCard.svelte'
  import TrackRow from '../components/TrackRow.svelte'

  let results = $state<Promise<live.SearchResult> | null>(null)
  let found = $state<live.SearchResult | null>(null)
  let visible = $state<Record<live.SearchSection, number>>({ tracks: 8, artists: 10, albums: 12, playlists: 12 })
  let loadingMore = $state<Partial<Record<live.SearchSection, boolean>>>({})
  let errors = $state<Partial<Record<live.SearchSection, string>>>({})
  let generation = 0
  let searchedQuery = ''
  let searchedService = app.service
  let timer: ReturnType<typeof setTimeout>

  onDestroy(() => {
    clearTimeout(timer)
    generation++
  })

  async function more(section: live.SearchSection) {
    if (!found || loadingMore[section] || app.query.trim() !== searchedQuery || app.service !== searchedService) return
    if (visible[section] < found[section].length) {
      visible[section] = Math.min(found[section].length, visible[section] + (section === 'tracks' ? 8 : 12))
      return
    }
    const next = found.more?.[section]
    if (!next) return
    const request = generation
    const q = searchedQuery
    const svc = searchedService
    loadingMore[section] = true
    errors[section] = ''
    try {
      const page = await next()
      if (request !== generation || app.query.trim() !== q || app.service !== svc || !found) return
      found = {
        ...found,
        [section]: [...new Set([...found[section], ...page.items])],
        more: { ...found.more, [section]: page.next },
      }
      visible[section] = Math.min(found[section].length, visible[section] + (section === 'tracks' ? 8 : 12))
    } catch (e) {
      if (request === generation && app.query.trim() === q && app.service === svc) errors[section] = reason(e)
    } finally {
      if (request === generation) loadingMore[section] = false
    }
  }

  function readRecent(): string[] {
    try {
      return JSON.parse(localStorage.getItem('yc.recent') ?? '[]')
    } catch {
      return []
    }
  }
  let recent = $state<string[]>(readRecent())

  function run() {
    const q = app.query.trim()
    const svc = app.service
    const request = ++generation
    searchedQuery = q
    searchedService = svc
    found = null
    visible = { tracks: 8, artists: 10, albums: 12, playlists: 12 }
    loadingMore = {}
    errors = {}
    results = q ? live.search(svc, q).then((r) => {
      if (request === generation) found = r
      return r
    }) : null
    if (q) {
      recent = [q, ...recent.filter((x) => x.toLowerCase() !== q.toLowerCase())].slice(0, 8)
      remember('recent', recent)
    }
  }

  // Asks once typing pauses, and again whenever the service changes.
  function typed() {
    clearTimeout(timer)
    timer = setTimeout(run, app.query.trim() ? 380 : 0)
  }

  // A search asked again in the service just switched to; only the switch is followed here.
  let lastService = app.service
  $effect(() => {
    const svc = app.service
    if (svc === lastService) return
    lastService = svc
    untrack(run)
  })

  // Words in the address (#/search/<words>: Masaki's palette sends them so) go into the bar.
  $effect(() => {
    const words = app.route.name === 'search' ? app.route.id : undefined
    if (words) untrack(() => (app.query = words))
  })

  // The words come from the bar above the page: asked once typing pauses.
  $effect(() => {
    void app.query
    untrack(typed)
  })

  const kindName = { artist: tr('Артист'), track: tr('Трек'), album: tr('Альбом'), playlist: tr('Плейлист') }
</script>


{#if !results}
  {#if recent.length}
    <section class="section rise">
      <div class="section-head">
        <h2>{tr('Недавние запросы')}</h2>
        <button
          class="btn text small"
          onclick={() => {
            recent = []
            remember('recent', [])
          }}>{tr('Очистить')}</button
        >
      </div>
      <div class="chips">
        {#each recent as q (q)}
          <button
            class="chip"
            onclick={() => {
              app.query = q
              run()
            }}><Icon name="history" size={18} />{q}</button
          >
        {/each}
      </div>
    </section>
  {:else}
    <!-- Nothing asked yet: what search does, said once in the middle of the page rather than as
         a line lost at its top. -->
    <section class="hint rise">
      <span class="hint-mark" aria-hidden="true"><Icon name="search" size={34} /></span>
      <h2 class="headline">{tr('Поиск')}</h2>
      <p class="muted">{tr('Ищи в %s: трек, артиста или альбом. Сервис выбирается в строке поиска.', serviceName(app.service))}</p>
    </section>
  {/if}
{:else}
  {#await results}
    <Loading text={tr('Ищу в %s…', serviceName(app.service))} />
  {:then initial}
    {@const r = found ?? initial}
    {#if !r.tracks.length && !r.artists.length && !r.albums.length && !r.playlists.length && !Object.values(r.more ?? {}).some(Boolean)}
      <Problem empty title={tr('Ничего не нашлось')} message={tr('«%s» в %s не находится. Попробуй написать иначе или поискать в другом сервисе.', app.query, serviceName(app.service))} />
    {:else}
      <section class="results rise" class:without-best={!r.best}>
        {#if r.best}
          {@const b = r.best}
          <div class="top-result">
            <div class="section-head"><h2>{tr('Лучший результат')}</h2></div>
            {#if b.type === 'artist'}
              {@const a = artist(b.id)}
              <button class="top-card glass" onclick={() => go('artist/' + a.id)}>
                <span class="top-pic round"><Cover seed={'artist-' + a.id} color={a.color} src={a.image} kind="artist" /></span>
                <span class="top-name display">{a.name}</span>
                <span class="chip">{kindName.artist}</span>
                {#if a.followers}<span class="muted top-sub">{a.followers}</span>{/if}
              </button>
            {:else if b.type === 'track'}
              {@const t = track(b.id)}
              <button class="top-card glass" onclick={() => playTrack(t.id, r.tracks.includes(t.id) ? r.tracks : [t.id], tr('Поиск'))}>
                <span class="top-pic"><Cover seed={t.id} color={t.color} src={t.cover} /></span>
                <span class="top-name display">{t.title}</span>
                <span class="chip">{kindName.track}</span>
                <span class="muted top-sub">{artistNames(t)}</span>
                <span class="top-play"><Icon name="play_arrow" fill size={30} /></span>
              </button>
            {:else}
              {@const s = set(b.id)}
              <button class="top-card glass" onclick={() => go('set/' + s.id)}>
                <span class="top-pic"><Cover seed={s.id} color={s.color} src={s.cover} kind={s.kind} /></span>
                <span class="top-name display">{s.title}</span>
                <span class="chip">{kindName[b.type]}</span>
                <span class="muted top-sub">{s.subtitle}</span>
                <span
                  class="top-play"
                  role="button"
                  tabindex="0"
                  onclick={(e) => {
                    e.stopPropagation()
                    playSet(s.id)
                  }}
                  onkeydown={() => {}}><Icon name="play_arrow" fill size={30} /></span
                >
              </button>
            {/if}
          </div>
        {/if}
        {#if r.tracks.length || r.more?.tracks}
          <div class="top-tracks">
            <div class="section-head"><h2>{tr('Треки')}</h2></div>
            <div class="result-group glass track-list">
              {#each r.tracks.slice(0, visible.tracks) as id (id)}
                <TrackRow {id} list={r.tracks} context={tr('Поиск: %s', app.query)} contextId={'search:' + app.query} />
              {/each}
              {@render moreRow('tracks', r)}
            </div>
          </div>
        {/if}
      </section>

      {#if r.artists.length || r.more?.artists}
        <section class="section">
          <div class="section-head"><h2>{tr('Артисты')}</h2></div>
          <div class="result-group glass track-list">
            <div class="card-grid artists">
              {#each r.artists.slice(0, visible.artists) as id (id)}<ArtistCard {id} size={156} />{/each}
            </div>
            {@render moreRow('artists', r)}
          </div>
        </section>
      {/if}
      {#if r.albums.length || r.more?.albums}
        <section class="section">
          <div class="section-head"><h2>{tr('Альбомы')}</h2></div>
          <div class="result-group glass track-list">
            <div class="card-grid">
              {#each r.albums.slice(0, visible.albums) as id (id)}
                {@const s = set(id)}
                <SetCard {id} size={170} subtitle="{s.kind === 'single' ? tr('Сингл') : tr('Альбом')}{s.year ? ` · ${s.year}` : ''} · {s.subtitle}" />
              {/each}
            </div>
            {@render moreRow('albums', r)}
          </div>
        </section>
      {/if}
      {#if r.playlists.length || r.more?.playlists}
        <section class="section">
          <div class="section-head"><h2>{tr('Плейлисты')}</h2></div>
          <div class="result-group glass track-list">
            <div class="card-grid">
              {#each r.playlists.slice(0, visible.playlists) as id (id)}<SetCard {id} size={170} />{/each}
            </div>
            {@render moreRow('playlists', r)}
          </div>
        </section>
      {/if}
    {/if}
  {:catch e}
    <Problem message={reason(e)} onretry={run} />
  {/await}
{/if}

{#snippet moreRow(section: live.SearchSection, r: live.SearchResult)}
  {#if visible[section] < r[section].length || r.more?.[section]}
    <LoadMoreRow loading={loadingMore[section]} error={errors[section]} onclick={() => more(section)} />
  {/if}
{/snippet}

<style>
  .card-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(170px, 1fr));
    gap: 24px 20px;
    padding: 22px;
  }
  .card-grid.artists {
    grid-template-columns: repeat(auto-fill, minmax(156px, 1fr));
  }
  .card-grid > :global(*) {
    justify-self: center;
  }
  .chips {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
  }
  .hint {
    margin: 12vh auto 0;
    max-width: 440px;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 10px;
    text-align: center;
  }
  .hint h2 {
    font-size: 26px;
    margin-top: 8px;
  }
  .hint p {
    font-size: 15.5px;
    text-wrap: balance;
  }
  .hint-mark {
    width: 88px;
    height: 88px;
    display: grid;
    place-items: center;
    border-radius: 34%;
    background: var(--secondary-container);
    color: var(--on-secondary-container);
    animation: hint-float 6s ease-in-out infinite;
  }
  @keyframes hint-float {
    50% {
      transform: translateY(-6px) rotate(-4deg);
    }
  }
  .results {
    margin-top: 32px;
    display: grid;
    grid-template-columns: minmax(240px, 320px) minmax(0, 1fr);
    gap: 24px;
  }
  .results.without-best {
    grid-template-columns: minmax(0, 1fr);
  }
  .top-card {
    width: 100%;
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
    padding: 22px;
    border-radius: var(--r-2xl);
    text-align: left;
    position: relative;
    transition: background-color 200ms;
  }
  .top-card:hover {
    background: var(--glass-strong);
  }
  .top-pic {
    width: 120px;
    height: 120px;
    border-radius: var(--r-xl);
    overflow: hidden;
    box-shadow: var(--shadow);
    margin-bottom: 6px;
  }
  .top-pic.round {
    border-radius: 50%;
  }
  .top-name {
    font-size: 30px;
    max-width: 100%;
    overflow-wrap: anywhere;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }
  .top-sub {
    font-size: 14px;
  }
  .top-play {
    position: absolute;
    right: 20px;
    bottom: 20px;
    width: 60px;
    height: 60px;
    border-radius: 50%;
    display: grid;
    place-items: center;
    background: var(--primary);
    color: var(--on-primary);
    box-shadow: 0 10px 24px -8px #0009;
    opacity: 0;
    transform: translateY(10px) scale(0.7);
    transition: opacity 200ms, transform var(--d-spatial) var(--spring-fast);
  }
  .top-card:hover .top-play {
    opacity: 1;
    transform: none;
  }
  .top-play:active {
    border-radius: 30%;
  }
  .top-tracks {
    min-width: 0;
  }
  @container page (max-width: 760px) {
    .results {
      grid-template-columns: minmax(0, 1fr);
    }
    .card-grid {
      padding: 18px;
      gap: 20px 12px;
      grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
    }
    .card-grid :global(.card) {
      --w: 140px !important;
    }
    .card-grid.artists {
      grid-template-columns: repeat(auto-fill, minmax(132px, 1fr));
    }
    .card-grid.artists :global(.artist) {
      --w: 132px !important;
    }
  }
</style>
