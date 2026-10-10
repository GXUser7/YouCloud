<script lang="ts">
  import { serviceName, sets, track } from '../lib/catalog'
  import { totalMinutes, tracksCount } from '../lib/format'
  import * as live from '../lib/live'
  import { go, player, playQueue, playSet, toggle } from '../lib/state.svelte'
  import { reason } from '../lib/toast.svelte'
  import BlurredCover from '../components/BlurredCover.svelte'
  import Cover from '../components/Cover.svelte'
  import Icon from '../components/Icon.svelte'
  import Loading from '../components/Loading.svelte'
  import PlayButton from '../components/PlayButton.svelte'
  import Problem from '../components/Problem.svelte'
  import ServiceGlyph from '../components/ServiceGlyph.svelte'
  import TrackRow from '../components/TrackRow.svelte'

  let { id }: { id: string } = $props()

  const kindName: Record<string, string> = {
    album: 'Альбом',
    single: 'Сингл',
    playlist: 'Плейлист',
    mix: 'Микс',
    station: 'Станция',
    genre: 'Чарт',
    liked: 'Плейлист',
    personal: 'Собрано для тебя',
  }

  let data = $derived(live.loadSet(id))
  // What the card that led here already knew, shown while the tracks load.
  const known = $derived(sets.get(id))
</script>

{#await data}
  {#if known}
    {@render head(known, null)}
  {/if}
  <Loading text="Загружаю треки…" tall={!known} />
{:then s}
  {@render head(s, s.tracks)}
  <section class="list rise">
    <div class="list-head">
      <span>#</span>
      <span class="grow">Название</span>
      {#if s.kind !== 'album' && s.kind !== 'single'}<span class="album-col">Альбом</span>{/if}
      <Icon name="schedule" size={18} />
    </div>
    {#each s.tracks as t, i (t + i)}
      <TrackRow id={t} list={s.tracks} context={s.title} contextId={s.id} number={i + 1} showAlbum={s.kind !== 'album' && s.kind !== 'single'} />
    {:else}
      <p class="muted empty">Здесь пока пусто.</p>
    {/each}
  </section>
{:catch e}
  <Problem message={reason(e)} onretry={() => (data = live.loadSet(id))} />
{/await}

{#snippet head(s: import('../lib/catalog').TrackSet, ids: string[] | null)}
  {@const duration = ids ? ids.reduce((sum, t) => sum + track(t).duration, 0) : 0}
  <div class="bleed" aria-hidden="true"><BlurredCover seed={s.id} color={s.color} src={s.cover} kind={s.kind} label={s.label} blur={70} saturate={1.3} /></div>

  <section class="head">
    <button class="back icon-btn glassy l" onclick={() => history.back()} aria-label="Назад"><Icon name="arrow_back" /></button>
    <div class="art" class:round={s.kind === 'station'}>
      <Cover seed={s.id} color={s.color} src={s.cover} kind={s.kind} label={s.label} title={s.kind === 'genre' || s.kind === 'personal' ? s.title : ''} grain />
    </div>
    <div class="info">
      <span class="overline kind"><ServiceGlyph service={s.service} size={16} />{kindName[s.kind]}{s.year ? ` · ${s.year}` : ''} · {serviceName(s.service)}</span>
      <h1 class="display">{s.title}</h1>
      <div class="by">
        {#if s.artist}
          <button class="chip" onclick={() => go('artist/' + s.artist!.id)}>{s.artist.name}</button>
        {:else if s.subtitle}
          <span class="muted sub">{s.subtitle}</span>
        {/if}
      </div>
      {#if ids}
        <p class="muted meta">{tracksCount(ids.length)}{duration ? ` · ${totalMinutes(duration)}` : ''}</p>
      {/if}
    </div>
  </section>

  <div class="bar">
    <PlayButton
      playing={player.contextId === s.id && player.playing}
      onclick={() => (player.contextId === s.id ? toggle() : ids ? playQueue(ids, 0, s.title, s.id) : playSet(s.id))}
      size={72}
    />
    <button class="icon-btn tonal l" onclick={() => playSet(s.id, true)} aria-label="Перемешать"><Icon name="shuffle" /></button>
    {#if s.link}
      <a class="icon-btn glassy l" href={s.link} target="_blank" rel="noreferrer" aria-label="Открыть в {serviceName(s.service)}">
        <Icon name="open_in_new" />
      </a>
    {/if}
  </div>
{/snippet}

<style>
  .bleed {
    position: absolute;
    inset: 0 0 auto 0;
    height: 520px;
    overflow: hidden;
    opacity: 0.55;
    mask-image: linear-gradient(#000 20%, transparent);
    pointer-events: none;
    z-index: -1;
  }
  .bleed :global(.cover) {
    width: 100%;
    height: 100%;
  }
  .head {
    position: relative;
    display: flex;
    align-items: flex-end;
    gap: 32px;
    padding-top: 64px;
  }
  .back {
    position: absolute;
    top: 0;
    left: 0;
  }
  .art {
    width: clamp(200px, 18vw, 260px);
    aspect-ratio: 1;
    border-radius: var(--r-2xl);
    overflow: hidden;
    flex: none;
    box-shadow: 0 30px 70px -30px #000c;
    animation: rise var(--d-slow) var(--spring);
  }
  .art.round {
    border-radius: 50%;
  }
  .info {
    min-width: 0;
    animation: rise var(--d-slow) var(--spring) 60ms both;
  }
  .kind {
    display: flex;
    align-items: center;
    gap: 8px;
  }
  h1 {
    font-size: clamp(36px, 4.2vw, 64px);
    margin: 10px 0 14px;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
    overflow-wrap: anywhere;
  }
  .sub {
    display: -webkit-box;
    -webkit-line-clamp: 2;
    line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
    max-width: 70ch;
  }
  .meta {
    margin-top: 12px;
  }
  .bar {
    display: flex;
    align-items: center;
    gap: 12px;
    margin: 32px 0 20px;
  }
  .list-head {
    display: flex;
    align-items: center;
    gap: 14px;
    padding: 0 22px 10px 18px;
    margin-bottom: 6px;
    border-bottom: 1px solid var(--glass-line);
    font-size: 13px;
    font-weight: 650;
    color: var(--on-surface-variant);
  }
  .list-head > span:first-child {
    width: 26px;
    text-align: center;
  }
  .grow {
    flex: 1;
    padding-left: 66px;
  }
  .album-col {
    flex: 0 1 26%;
  }
  .list-head :global(.icon) {
    margin-right: 96px;
  }
  .empty {
    padding: 16px 4px;
  }
</style>
