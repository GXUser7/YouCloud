<script lang="ts">
  import { untrack } from 'svelte'
  import { artist, artistNames, serviceName, set, track } from '../lib/catalog'
  import * as live from '../lib/live'
  import { app, go, playSet, playTrack, remember } from '../lib/state.svelte'
  import { reason } from '../lib/toast.svelte'
  import ArtistCard from '../components/ArtistCard.svelte'
  import Cover from '../components/Cover.svelte'
  import Icon from '../components/Icon.svelte'
  import Loading from '../components/Loading.svelte'
  import Problem from '../components/Problem.svelte'
  import SetCard from '../components/SetCard.svelte'
  import Shelf from '../components/Shelf.svelte'
  import TrackRow from '../components/TrackRow.svelte'

  let results = $state<Promise<live.SearchResult> | null>(null)
  let timer: ReturnType<typeof setTimeout>

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
    results = q ? live.search(app.service, q) : null
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

  const kindName = { artist: 'Артист', track: 'Трек', album: 'Альбом', playlist: 'Плейлист' }
</script>


{#if !results}
  {#if recent.length}
    <section class="section rise">
      <div class="section-head">
        <h2>Недавние запросы</h2>
        <button
          class="btn text small"
          onclick={() => {
            recent = []
            remember('recent', [])
          }}>Очистить</button
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
    <p class="hint muted rise">Ищи в {serviceName(app.service)}: трек, артиста или альбом. Сервис — на панели слева.</p>
  {/if}
{:else}
  {#await results}
    <Loading text="Ищу в {serviceName(app.service)}…" />
  {:then r}
    {#if !r.tracks.length && !r.artists.length && !r.albums.length && !r.playlists.length}
      <Problem title="Ничего не нашлось" message="«{app.query}» в {serviceName(app.service)} не находится. Попробуй написать иначе или поискать в другом сервисе." />
    {:else}
      <section class="results rise">
        {#if r.best}
          {@const b = r.best}
          <div class="top-result">
            <div class="section-head"><h2>Лучший результат</h2></div>
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
              <button class="top-card glass" onclick={() => playTrack(t.id, r.tracks.includes(t.id) ? r.tracks : [t.id], 'Поиск')}>
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
        <div class="top-tracks">
          <div class="section-head"><h2>Треки</h2></div>
          {#each r.tracks.slice(0, 8) as id (id)}
            <TrackRow {id} list={r.tracks} context="Поиск: {app.query}" contextId={'search:' + app.query} compact />
          {/each}
        </div>
      </section>

      {#if r.artists.length}
        <Shelf title="Артисты">
          {#each r.artists as id (id)}<ArtistCard {id} size={156} />{/each}
        </Shelf>
      {/if}
      {#if r.albums.length}
        <Shelf title="Альбомы">
          {#each r.albums as id (id)}
            {@const s = set(id)}
            <SetCard {id} size={170} subtitle="{s.kind === 'single' ? 'Сингл' : 'Альбом'}{s.year ? ` · ${s.year}` : ''} · {s.subtitle}" />
          {/each}
        </Shelf>
      {/if}
      {#if r.playlists.length}
        <Shelf title="Плейлисты">
          {#each r.playlists as id (id)}<SetCard {id} size={170} />{/each}
        </Shelf>
      {/if}
    {/if}
  {:catch e}
    <Problem message={reason(e)} onretry={run} />
  {/await}
{/if}

<style>
  .chips {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
  }
  .hint {
    margin-top: 32px;
    font-size: 16px;
  }
  .results {
    margin-top: 32px;
    display: grid;
    grid-template-columns: minmax(280px, 380px) minmax(0, 1fr);
    gap: 28px;
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
    transition:
      border-radius var(--d-spatial) var(--spring),
      background-color 200ms;
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
    transition:
      opacity 200ms,
      transform var(--d-spatial) var(--spring-fast),
      border-radius var(--d-fast) var(--spring-fast);
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
</style>
