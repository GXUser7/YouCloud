<script lang="ts">
  // «Любимое» on the service's page: the liked tracks as a card to play and their first rows,
  // then the listener's playlists and artists — what «Моя музыка» held on a page of its own.
  import { serviceName, set, type Service } from '../lib/catalog'
  import { tracksCount } from '../lib/format'
  import * as live from '../lib/live'
  import { go, markLiked, player, playQueue, playSet, toggle } from '../lib/state.svelte'
  import { reason } from '../lib/toast.svelte'
  import ArtistCard from './ArtistCard.svelte'
  import Cover from './Cover.svelte'
  import Icon from './Icon.svelte'
  import PlayButton from './PlayButton.svelte'
  import Problem from './Problem.svelte'
  import SetCard from './SetCard.svelte'
  import Shelf from './Shelf.svelte'
  import SignInCard from './SignInCard.svelte'

  let { service }: { service: Service } = $props()

  const SIGN_IN: Record<Service, string> = {
    sc: 'Твои лайки, плейлисты и подписки — здесь же. Вход берёт сессию soundcloud.com из этого браузера или открывает вход.',
    ya: 'Вход через Яндекс ID: «Мне нравится», твои плейлисты и артисты будут здесь.',
    yt: 'YouTube Music берёт вход этого браузера: войди в Google — здесь появятся «Понравившаяся музыка» и плейлисты.',
  }

  const signedIn = $derived(live.signedIn(service))

  function load() {
    return live.library(service).then((lib) => {
      markLiked(lib.likedIds)
      return lib
    })
  }
  let data = $state<Promise<live.Library> | null>(null)
  $effect(() => {
    data = signedIn ? load() : null
  })
</script>

<section class="favorites section" id="favorites">
  <div class="section-head">
    <h2>Любимое</h2>
  </div>

  {#if !signedIn}
    <SignInCard {service} text={SIGN_IN[service]} />
  {:else if data}
    {#await data}
      <div class="placeholder glass" aria-busy="true"></div>
    {:then lib}
      {@const liked = set(lib.liked)}
      {@const first = liked.tracks.slice(0, 6)}
      <div class="liked glass rise">
        <button class="art" onclick={() => go('set/' + liked.id)} aria-label={liked.title}>
          <Cover seed={liked.id} color={liked.color} kind="liked" grain />
        </button>
        <div class="info">
          <span class="overline">{serviceName(service)}</span>
          <h3 class="headline">{liked.title}</h3>
          <p class="muted">{tracksCount(liked.tracks.length)}</p>
          <div class="actions">
            <PlayButton
              playing={player.contextId === liked.id && player.playing}
              onclick={() => (player.contextId === liked.id ? toggle() : playQueue(liked.tracks, 0, liked.title, liked.id))}
              size={56}
            />
            <button class="icon-btn tonal" onclick={() => playSet(liked.id, true)} aria-label="Перемешать" title="Перемешать"><Icon name="shuffle" /></button>
            <button class="btn text small" onclick={() => go('set/' + liked.id)}>Все<Icon name="arrow_forward" size={18} /></button>
          </div>
        </div>
        <div class="rows">
          {#each first as id, i (id)}
            {@const t = live.track(id)}
            <button class="row" class:now={player.queue[player.index] === id && player.contextId === liked.id} onclick={() => playQueue(liked.tracks, i, liked.title, liked.id)}>
              <span class="mini"><Cover seed={t.id} color={t.color} src={t.cover} /></span>
              <span class="row-text">
                <span class="ellipsis title">{t.title}</span>
                <span class="ellipsis muted sub">{t.artists.map((a) => a.name).join(', ')}</span>
              </span>
            </button>
          {:else}
            <p class="muted empty">Лайков пока нет — сердечко у трека добавит его сюда.</p>
          {/each}
        </div>
      </div>

      {#if lib.playlists.length}
        <Shelf title="Твои плейлисты">
          {#each lib.playlists as id (id)}<SetCard {id} size={176} />{/each}
        </Shelf>
      {/if}
      {#if lib.artists.length}
        <Shelf title="Твои артисты">
          {#each lib.artists as id (id)}<ArtistCard {id} size={150} />{/each}
        </Shelf>
      {/if}
    {:catch e}
      <Problem message={reason(e)} onretry={() => (data = load())} />
    {/await}
  {/if}
</section>

<style>
  .favorites :global(.signin) {
    margin-top: 0;
  }
  .placeholder {
    height: 248px;
    border-radius: var(--r-2xl);
    opacity: 0.6;
  }
  .liked {
    display: grid;
    grid-template-columns: auto minmax(200px, 0.8fr) minmax(0, 1.4fr);
    gap: 24px;
    align-items: center;
    padding: 20px;
    border-radius: var(--r-2xl);
  }
  .art {
    width: clamp(150px, 16cqi, 208px);
    aspect-ratio: 1;
    border-radius: var(--r-xl);
    overflow: hidden;
    box-shadow: var(--shadow);
    transition: border-radius var(--d-spatial) var(--spring);
  }
  .art:hover {
    border-radius: 50%;
  }
  .info {
    min-width: 0;
  }
  .info h3 {
    font-size: clamp(24px, 2.4cqi, 34px);
    margin: 6px 0 4px;
  }
  .actions {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-top: 16px;
  }
  .rows {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 4px 10px;
    min-width: 0;
  }
  .row {
    display: flex;
    align-items: center;
    gap: 12px;
    min-width: 0;
    padding: 6px;
    border-radius: var(--r-m);
    text-align: left;
    transition:
      background-color 150ms var(--ease-emph),
      border-radius var(--d-fast) var(--spring-fast);
  }
  .row:hover {
    background: var(--state-hover);
  }
  .row:active {
    border-radius: var(--r-s);
  }
  .row.now .title {
    color: var(--primary);
  }
  .mini {
    width: 48px;
    height: 48px;
    flex: none;
    border-radius: var(--r-s);
    overflow: hidden;
  }
  .row-text {
    display: flex;
    flex-direction: column;
    min-width: 0;
  }
  .title {
    font-weight: 650;
  }
  .sub {
    font-size: 13px;
  }
  .empty {
    grid-column: 1 / -1;
    padding: 8px;
  }
  @container page (max-width: 980px) {
    .liked {
      grid-template-columns: auto minmax(0, 1fr);
    }
    .rows {
      grid-column: 1 / -1;
    }
  }
  @container page (max-width: 640px) {
    .rows {
      grid-template-columns: minmax(0, 1fr);
    }
  }
</style>
