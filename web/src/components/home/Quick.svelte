<script lang="ts">
  // «Для тебя»: home says hello and lays out what to start right now as a grid of wide tiles —
  // the likes first, then what the service leads with — each a cover and a name, quick to hit.
  // Under the pointer a tile brightens, rounds its corners out and its play button springs in.
  import { tr } from '../../lib/i18n.svelte'
  import { serviceName, set, type Service } from '../../lib/catalog'
  import type { Library } from '../../lib/live'
  import { greeting } from '../../lib/home.svelte'
  import { go, player, playQueue, playSet, toggle } from '../../lib/state.svelte'
  import { tracksCount } from '../../lib/format'
  import type { Snippet } from 'svelte'
  import Cover from '../Cover.svelte'
  import Icon from '../Icon.svelte'
  import LikedArt from '../LikedArt.svelte'

  let {
    service,
    lead,
    lib,
    signedIn,
    subtitle,
    first,
  }: {
    service: Service
    lead: string[]
    lib: Library | null
    signedIn: boolean
    subtitle: string
    /** A tile of the service's own before the sets (Yandex's wave). */
    first?: Snippet
  } = $props()

  const liked = $derived(lib ? set(lib.liked) : null)
  const tiles = $derived(lead.slice(0, first ? 6 : 7))

  function play(id: string) {
    if (player.contextId === id) toggle()
    else playSet(id)
  }
  function playLiked() {
    if (!liked) return
    if (player.contextId === liked.id) toggle()
    else playQueue(liked.tracks, 0, liked.title, liked.id)
  }
  const playingIcon = (id: string) => (player.contextId === id && player.playing ? 'pause' : 'play_arrow')
</script>

<header class="hello rise">
  <h1 class="display">{greeting()}</h1>
  <p class="muted">{subtitle}</p>
</header>

<section class="quick rise">
  {#if liked}
    <div class="tile liked-tile">
      <button class="open" onclick={() => go('set/' + liked.id)}>
        <span class="art"><LikedArt ids={liked.tracks} seed={liked.id} color={liked.color} badge={false} /></span>
        <span class="text"><span class="ellipsis title">{tr('Любимое')}</span><span class="ellipsis sub">{tracksCount(liked.tracks.length)}</span></span>
      </button>
      <button class="go" onclick={playLiked} aria-label={tr('Играть любимое')}><Icon name={playingIcon(liked.id)} fill size={24} /></button>
    </div>
  {:else}
    <div class="tile liked-tile">
      <button class="open" onclick={() => !signedIn && go('settings')}>
        <span class="art"><LikedArt seed="liked-{service}" color="#d81b60" /></span>
        <span class="text">
          <span class="ellipsis title">{tr('Любимое')}</span>
          <span class="ellipsis sub">{signedIn ? tr('Загружаю…') : tr('Войди в %s', serviceName(service))}</span>
        </span>
      </button>
    </div>
  {/if}
  {#if first}<div class="tile own">{@render first()}</div>{/if}
  {#each tiles as id (id)}
    {@const s = set(id)}
    <div class="tile">
      <button class="open" onclick={() => go('set/' + s.id)}>
        <span class="art"><Cover seed={s.id} color={s.color} src={s.cover} kind={s.kind} label={s.label} /></span>
        <span class="text"><span class="ellipsis title">{s.title}</span>{#if s.subtitle}<span class="ellipsis sub">{s.subtitle}</span>{/if}</span>
      </button>
      <button class="go" onclick={() => play(s.id)} aria-label={tr('Играть %s', s.title)}><Icon name={playingIcon(s.id)} fill size={24} /></button>
    </div>
  {/each}
</section>

<style>
  .hello {
    padding-top: 12px;
  }
  .hello h1 {
    font-size: clamp(40px, 4.4vw, 64px);
  }
  .hello p {
    margin-top: 10px;
    font-size: 17px;
  }

  .quick {
    margin-top: 28px;
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 12px;
  }
  .tile {
    position: relative;
    height: 84px;
    border-radius: var(--r-l);
    overflow: hidden;
    isolation: isolate;
    background: color-mix(in oklab, var(--on-surface) 7%, var(--glass));
    border: 1px solid var(--glass-line);
    transition:
      border-radius var(--d-spatial) var(--spring),
      background-color 250ms var(--ease-emph),
      transform var(--d-fast) var(--spring-fast),
      box-shadow 300ms var(--ease-emph);
  }
  .tile:hover {
    border-radius: 32px;
    background: color-mix(in oklab, var(--on-surface) 12%, var(--glass));
    box-shadow: var(--shadow);
  }
  .tile:active {
    border-radius: var(--r-s);
    transform: scale(0.985);
  }
  .liked-tile {
    background: color-mix(in oklab, var(--primary) 22%, var(--glass));
  }
  .liked-tile:hover {
    background: color-mix(in oklab, var(--primary) 30%, var(--glass));
  }
  .open {
    width: 100%;
    height: 100%;
    display: flex;
    align-items: center;
    gap: 14px;
    text-align: left;
    padding-right: 64px;
  }
  .art {
    width: 84px;
    height: 84px;
    flex: none;
    overflow: hidden;
    display: block;
    transition: border-radius var(--d-spatial) var(--spring);
  }
  .art :global(.cover) {
    transition: transform var(--d-slow) var(--spring);
  }
  .tile:hover .art {
    border-radius: 0 24px 24px 0;
  }
  .tile:hover .art :global(.cover) {
    transform: scale(1.08);
  }
  .text {
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 2px;
  }
  .title {
    font-weight: 750;
    font-size: 15.5px;
  }
  .sub {
    font-size: 13px;
    color: var(--on-surface-variant);
  }
  .go {
    position: absolute;
    right: 14px;
    top: calc(50% - 22px);
    width: 44px;
    height: 44px;
    display: grid;
    place-items: center;
    border-radius: 50%;
    background: var(--primary);
    color: var(--on-primary);
    box-shadow: 0 8px 20px -8px color-mix(in oklab, #000 60%, transparent);
    opacity: 0;
    transform: translateX(10px) scale(0.5) rotate(-40deg);
    transition:
      opacity 180ms var(--ease-emph),
      transform var(--d-spatial) var(--spring-fast),
      border-radius var(--d-fast) var(--spring-fast);
  }
  .tile:hover .go,
  .go:focus-visible {
    opacity: 1;
    transform: none;
  }
  .go:hover {
    border-radius: 14px;
  }
  .go:active {
    border-radius: 10px;
    transform: scale(0.9);
  }
  .own {
    background: color-mix(in oklab, var(--tertiary, var(--primary)) 18%, var(--glass));
  }

  @container page (max-width: 1100px) {
    .quick {
      grid-template-columns: repeat(2, minmax(0, 1fr));
    }
  }
</style>
