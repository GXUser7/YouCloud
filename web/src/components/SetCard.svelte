<script lang="ts">
  // An album, playlist, mix or station as a card: the cover, and a play button that rises out of
  // its corner on hover. Opening the card opens the set; the button plays it in place. Under the
  // pointer the card lifts, the cover draws in a little behind its frame and the frame's corners
  // open up (a station's circle becomes a cookie) — morphed as polygons of the same points
  // (lib/shapes), never by animating border-radius.
  import { tr } from '../lib/i18n.svelte'
  import { set as setOf } from '../lib/catalog'
  import { go, player, playSet, toggle } from '../lib/state.svelte'
  import { CIRCLE_POLYGON, clipPolygon, roundedSquarePolygon } from '../lib/shapes'
  import Cover from './Cover.svelte'
  import Icon from './Icon.svelte'
  import LikedArt from './LikedArt.svelte'

  let { id, size = 196, subtitle }: { id: string; size?: number; subtitle?: string } = $props()

  const s = $derived(setOf(id))
  const playingThis = $derived(player.contextId === id)
  const round = $derived(s.kind === 'station')
  const rest = $derived(round ? CIRCLE_POLYGON : roundedSquarePolygon(28 / (size / 2)))
  const hover = $derived(round ? clipPolygon('cookie9', 1.03) : roundedSquarePolygon(44 / (size / 2)))

  function play(e: MouseEvent) {
    e.stopPropagation()
    if (playingThis) toggle()
    else playSet(id)
  }
</script>

<div class="card" style:--w="{size}px" style:--rest={rest} style:--hover={hover}>
  <button class="art" onclick={() => go('set/' + id)} aria-label={s.title}>
    <span class="frame">
      <span class="img">
        {#if s.kind === 'liked' && !s.cover}
          <LikedArt ids={s.tracks} seed={s.id} color={s.color} />
        {:else}
          <Cover seed={s.id} color={s.color} src={s.cover} kind={s.kind} label={s.label} title={s.kind === 'genre' || s.kind === 'personal' ? s.title : ''} />
        {/if}
      </span>
    </span>
  </button>
  <button class="play" class:active={playingThis} onclick={play} aria-label={tr('Играть %s', s.title)}>
    <Icon name={playingThis && player.playing ? 'pause' : 'play_arrow'} fill size={28} />
  </button>
  <button class="meta" onclick={() => go('set/' + id)}>
    <span class="title ellipsis">{s.title}</span>
    <span class="sub ellipsis">{subtitle ?? s.subtitle}</span>
  </button>
</div>

<style>
  .card {
    width: var(--w);
    flex: none;
    position: relative;
  }
  /* The lift and the shadow: the shadow follows the frame's shape, so it sits outside the clip. */
  .art {
    display: block;
    width: var(--w);
    height: var(--w);
    filter: drop-shadow(0 14px 20px color-mix(in oklab, #000 32%, transparent));
    transition:
      transform var(--d-spatial) var(--spring),
      filter 300ms var(--ease-emph);
  }
  .frame {
    display: block;
    width: 100%;
    height: 100%;
    clip-path: var(--rest);
    transition: clip-path var(--d-slow) var(--spring);
  }
  .img {
    display: block;
    width: 100%;
    height: 100%;
    transition: transform var(--d-slow) var(--spring);
  }
  .card:hover .art,
  .art:focus-visible {
    transform: translateY(-5px);
    filter: drop-shadow(0 22px 28px color-mix(in oklab, #000 42%, transparent));
  }
  .card:hover .frame,
  .art:focus-visible .frame {
    clip-path: var(--hover);
  }
  .card:hover .img,
  .art:focus-visible .img {
    transform: scale(1.07);
  }
  .art:active {
    transform: scale(0.97) !important;
    transition-duration: var(--d-fast);
  }
  .art:focus-visible {
    outline: none;
  }

  /* Play: rises out of the corner on a spring; a squircle that rounds under the finger. */
  .play {
    position: absolute;
    top: calc(var(--w) - 64px);
    right: 10px;
    width: 54px;
    height: 54px;
    border-radius: var(--r-l);
    background: var(--primary);
    color: var(--on-primary);
    display: grid;
    place-items: center;
    box-shadow: 0 12px 24px -10px color-mix(in oklab, #000 65%, transparent);
    opacity: 0;
    transform: translateY(14px) scale(0.7);
    transition:
      opacity 180ms var(--ease-emph),
      transform var(--d-spatial) var(--spring),
      box-shadow 300ms var(--ease-emph);
  }
  .card:hover .play,
  .play.active,
  .play:focus-visible {
    opacity: 1;
    transform: translateY(-5px);
  }
  .play:hover {
    box-shadow: 0 16px 30px -10px color-mix(in oklab, var(--primary) 70%, transparent);
  }
  .play:active {
    border-radius: 50%;
    transform: translateY(-5px) scale(0.9);
    transition-duration: var(--d-fast);
  }
  .play.active {
    border-radius: 50%;
  }
  .meta {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    text-align: left;
    width: 100%;
    margin-top: 12px;
    padding: 0 4px;
  }
  .title {
    max-width: 100%;
    font-weight: 700;
    font-size: 15.5px;
  }
  .sub {
    max-width: 100%;
    font-size: 13.5px;
    color: var(--on-surface-variant);
    margin-top: 2px;
  }
</style>
