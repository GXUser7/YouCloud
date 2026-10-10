<script lang="ts">
  import { artist, artists, serviceName, set } from '../lib/catalog'
  import * as live from '../lib/live'
  import { player, playQueue, startRadio, toggle } from '../lib/state.svelte'
  import { reason } from '../lib/toast.svelte'
  import ArtistCard from '../components/ArtistCard.svelte'
  import Cover from '../components/Cover.svelte'
  import Icon from '../components/Icon.svelte'
  import Loading from '../components/Loading.svelte'
  import Problem from '../components/Problem.svelte'
  import ServiceGlyph from '../components/ServiceGlyph.svelte'
  import SetCard from '../components/SetCard.svelte'
  import Shelf from '../components/Shelf.svelte'
  import TrackRow from '../components/TrackRow.svelte'

  let { id }: { id: string } = $props()

  // The page is drawn anew for each artist (App keys it), so these follow [id] only to be exact.
  let data = $derived(live.loadArtist(id))
  const known = $derived(artists.get(id))
  const ctx = $derived('artist-' + id)
</script>

{#await data}
  {#if known}{@render hero(known.id)}{/if}
  <Loading text="Загружаю артиста…" tall={!known} />
{:then page}
  {@const a = artist(page.artist)}
  {@const playingThis = player.contextId === ctx && player.playing}
  {@render hero(a.id)}

  <div class="bar">
    <button
      class="btn filled large listen"
      class:playing={playingThis}
      disabled={!page.top.length}
      onclick={() => (player.contextId === ctx ? toggle() : playQueue(page.top, 0, a.name, ctx))}
    >
      <Icon name={playingThis ? 'pause' : 'play_arrow'} fill />
      {playingThis ? 'Пауза' : 'Слушать'}
    </button>
    <button class="icon-btn tonal l" disabled={!page.top.length} onclick={() => playQueue([...page.top].sort(() => Math.random() - 0.5), 0, a.name, ctx)} aria-label="Перемешать">
      <Icon name="shuffle" />
    </button>
    {#if page.top[0]}
      <button class="icon-btn glassy l" onclick={() => startRadio(page.top[0])} aria-label="Радио" title="Радио по артисту"><Icon name="radio" /></button>
    {/if}
    <span class="spacer"></span>
    {#if a.link}
      <a class="btn glassy" href={a.link} target="_blank" rel="noreferrer"><Icon name="open_in_new" size={20} />{serviceName(a.service)}</a>
    {/if}
  </div>

  {#if page.top.length}
    <section class="section">
      <div class="section-head"><h2>Популярные треки</h2></div>
      {#each page.top.slice(0, 10) as t, i (t)}
        <TrackRow id={t} list={page.top} context={a.name} contextId={ctx} number={i + 1} />
      {/each}
    </section>
  {/if}

  {#if page.releases.length}
    <Shelf title="Альбомы и синглы">
      {#each page.releases as r (r)}
        {@const s = set(r)}
        <SetCard id={r} size={176} subtitle="{s.kind === 'single' ? 'Сингл' : 'Альбом'}{s.year ? ` · ${s.year}` : ''}" />
      {/each}
    </Shelf>
  {/if}

  {#if page.playlists.length}
    <Shelf title="Плейлисты">
      {#each page.playlists as p (p)}<SetCard id={p} size={176} />{/each}
    </Shelf>
  {/if}

  {#if page.similar.length}
    <Shelf title="Похожие артисты">
      {#each page.similar as s (s)}<ArtistCard id={s} size={150} />{/each}
    </Shelf>
  {/if}
{:catch e}
  <Problem message={reason(e)} onretry={() => (data = live.loadArtist(id))} />
{/await}

{#snippet hero(artistId: string)}
  {@const a = artist(artistId)}
  <section class="hero">
    <div class="banner"><Cover seed={'artist-' + a.id} color={a.color} src={a.image} kind="artist" grain /></div>
    <div class="shade"></div>
    <button class="back icon-btn glassy l" onclick={() => history.back()} aria-label="Назад"><Icon name="arrow_back" /></button>
    <div class="who rise">
      <span class="overline kind"><ServiceGlyph service={a.service} size={16} />Артист · {serviceName(a.service)}</span>
      <h1 class="display">{a.name}</h1>
      {#if a.followers || a.albums}
        <p class="stats">{[a.followers, a.albums ? `${a.albums} альбомов` : ''].filter(Boolean).join(' · ')}</p>
      {/if}
    </div>
  </section>
{/snippet}

<style>
  .hero {
    position: relative;
    height: 380px;
    margin: -20px -36px 0;
    border-radius: 0 0 var(--r-2xl) var(--r-2xl);
    overflow: hidden;
    display: flex;
    align-items: flex-end;
  }
  .banner {
    position: absolute;
    inset: 0;
  }
  .banner :global(.cover) {
    width: 100%;
    height: 100%;
    object-position: center 30%;
  }
  .shade {
    position: absolute;
    inset: 0;
    background: linear-gradient(transparent 25%, color-mix(in oklab, var(--surface) 92%, transparent));
  }
  .back {
    position: absolute;
    top: 20px;
    left: 36px;
  }
  .who {
    position: relative;
    padding: 0 36px 28px;
    min-width: 0;
  }
  .kind {
    display: flex;
    align-items: center;
    gap: 8px;
  }
  h1 {
    font-size: clamp(44px, 5.6vw, 88px);
    margin: 8px 0 6px;
    text-shadow: 0 10px 40px #0007;
    overflow-wrap: anywhere;
  }
  .stats {
    color: var(--on-surface-variant);
    font-size: 15px;
  }
  .bar {
    display: flex;
    align-items: center;
    gap: 12px;
    margin: 24px 0 8px;
  }
  .listen {
    --h: 60px;
    padding: 0 30px 0 24px;
    font-size: 17px;
  }
  .listen.playing {
    --r: var(--r-l);
  }
  .spacer {
    flex: 1;
  }
</style>
