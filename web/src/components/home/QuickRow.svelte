<script lang="ts">
  // What of the listener's own is a tap away on a service's home: «Любимое» first, then their
  // playlists, as small tiles of matte glass — a cover and a name, and a play button that comes
  // up under the pointer. Not signed in, one tile says where the likes will be.
  import { tr } from '../../lib/i18n.svelte'
  import { serviceName, set, type Service } from '../../lib/catalog'
  import type { Library } from '../../lib/live'
  import { tracksCount } from '../../lib/format'
  import { QUICK_PLAYLISTS } from '../../lib/home.svelte'
  import { go, likedContext, likedTracks, player, playQueue, playSet, toggle } from '../../lib/state.svelte'
  import Cover from '../Cover.svelte'
  import Icon from '../Icon.svelte'
  import LikedArt from '../LikedArt.svelte'

  let { service, lib, signedIn }: { service: Service; lib: Library | null; signedIn: boolean } = $props()

  const liked = $derived(lib ? set(lib.liked) : null)
  const likedIds = $derived(liked ? likedTracks(liked.tracks) : [])
  const likedCtx = $derived(liked ? likedContext(liked.id) : '')
  const playlists = $derived(lib?.playlists.slice(0, QUICK_PLAYLISTS) ?? [])

  const playingIcon = (ctx: string) => (player.contextId === ctx && player.playing ? 'pause' : 'play_arrow')
  function playLiked() {
    if (!liked) return
    if (player.contextId === likedCtx) toggle()
    else playQueue(likedIds, 0, liked.title, likedCtx)
  }
  function play(id: string) {
    if (player.contextId === id) toggle()
    else playSet(id)
  }
</script>

<section class="quick rise" aria-label={tr('Любимое')}>
  {#if liked}
    <div class="tile" class:now={player.contextId === likedCtx}>
      <button class="open" onclick={() => go('set/' + liked.id)}>
        <span class="art"><LikedArt ids={likedIds} seed={liked.id} color={liked.color} /></span>
        <span class="text"><span class="title ellipsis">{tr('Любимое')}</span><span class="sub ellipsis">{tracksCount(likedIds.length)}</span></span>
      </button>
      <button class="go" onclick={playLiked} aria-label={tr('Играть любимое')}><Icon name={playingIcon(likedCtx)} fill size={22} /></button>
    </div>
    {#each playlists as id (id)}
      {@const s = set(id)}
      <div class="tile" class:now={player.contextId === id}>
        <button class="open" onclick={() => go('set/' + id)}>
          <span class="art"><Cover seed={s.id} color={s.color} src={s.cover} kind={s.kind} label={s.label} /></span>
          <span class="text"><span class="title ellipsis">{s.title}</span>{#if s.subtitle}<span class="sub ellipsis">{s.subtitle}</span>{/if}</span>
        </button>
        <button class="go" onclick={() => play(id)} aria-label={tr('Играть %s', s.title)}><Icon name={playingIcon(id)} fill size={22} /></button>
      </div>
    {/each}
  {:else}
    <div class="tile wide">
      <button class="open" onclick={() => !signedIn && go('settings')}>
        <span class="art"><LikedArt seed="liked-{service}" color="#d81b60" /></span>
        <span class="text">
          <span class="title ellipsis">{tr('Любимое')}</span>
          <span class="sub ellipsis">{signedIn ? tr('Загружаю…') : tr('Войди в %s, чтобы видеть свои лайки и плейлисты', serviceName(service))}</span>
        </span>
      </button>
      {#if !signedIn}
        <button class="btn tonal small sign" onclick={() => go('settings')}><Icon name="login" size={18} />{tr('Войти')}</button>
      {/if}
    </div>
  {/if}
</section>

<style>
  .quick {
    margin-top: 16px;
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(min(100%, 260px), 1fr));
    gap: 10px;
  }
  .tile {
    position: relative;
    height: 72px;
    display: flex;
    align-items: center;
    border-radius: var(--r-l);
    overflow: hidden;
    isolation: isolate;
    background: var(--glass);
    -webkit-backdrop-filter: var(--glass-blur);
    backdrop-filter: var(--glass-blur);
    transition: background-color 250ms var(--ease-emph), transform var(--d-fast) var(--spring-fast);
  }
  .tile:hover {
    background: color-mix(in oklab, var(--on-surface) 9%, var(--glass));
  }
  .tile:active {
    transform: scale(0.98);
  }
  .tile.wide {
    grid-column: 1 / -1;
    padding-right: 14px;
  }
  .open {
    flex: 1;
    min-width: 0;
    height: 100%;
    display: flex;
    align-items: center;
    gap: 14px;
    text-align: left;
  }
  .art {
    width: 72px;
    height: 72px;
    flex: none;
    display: block;
    overflow: hidden;
  }
  .art > :global(*) {
    transition: transform var(--d-slow) var(--spring);
  }
  .tile:hover .art > :global(*) {
    transform: scale(1.08);
  }
  .text {
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 2px;
    padding-right: 60px;
  }
  .wide .text {
    padding-right: 0;
  }
  .title {
    font-weight: 700;
    font-size: 15px;
  }
  .now .title {
    color: var(--primary);
  }
  .sub {
    font-size: 13px;
    color: var(--on-surface-variant);
  }
  /* Play: comes up from the tile's right edge under the pointer; stays while its set plays. */
  .go {
    position: absolute;
    right: 12px;
    top: 50%;
    width: 44px;
    height: 44px;
    margin-top: -22px;
    display: grid;
    place-items: center;
    border-radius: 50%;
    background: var(--primary);
    color: var(--on-primary);
    box-shadow: 0 8px 18px -8px color-mix(in oklab, #000 60%, transparent);
    opacity: 0;
    transform: translateX(10px) scale(0.7);
    transition: opacity 180ms var(--ease-emph), transform var(--d-spatial) var(--spring);
  }
  .tile:hover .go,
  .go:focus-visible,
  .now .go {
    opacity: 1;
    transform: none;
  }
  .go:active {
    border-radius: var(--r-s);
    transform: scale(0.9);
  }
  .sign {
    flex: none;
  }
  @media (hover: none) {
    .go {
      opacity: 1;
      transform: none;
    }
  }
</style>
