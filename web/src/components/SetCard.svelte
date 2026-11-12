<script lang="ts">
  // An album, playlist, mix or station as a card: the cover, and a play button that springs out of
  // its corner on hover. Opening the card opens the set; the button plays it in place.
  import { tr } from '../lib/i18n.svelte'
  import { set as setOf } from '../lib/catalog'
  import { go, player, playSet, toggle } from '../lib/state.svelte'
  import Cover from './Cover.svelte'
  import Icon from './Icon.svelte'
  import LikedArt from './LikedArt.svelte'

  let { id, size = 196, subtitle }: { id: string; size?: number; subtitle?: string } = $props()

  const s = $derived(setOf(id))
  const playingThis = $derived(player.contextId === id)

  function play(e: MouseEvent) {
    e.stopPropagation()
    if (playingThis) toggle()
    else playSet(id)
  }
</script>

<div class="card" style:--w="{size}px">
  <button class="art" class:round={s.kind === 'station'} onclick={() => go('set/' + id)} aria-label={s.title}>
    {#if s.kind === 'liked' && !s.cover}
      <LikedArt ids={s.tracks} seed={s.id} color={s.color} />
    {:else}
      <Cover seed={s.id} color={s.color} src={s.cover} kind={s.kind} label={s.label} title={s.kind === 'genre' || s.kind === 'personal' ? s.title : ''} />
    {/if}
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
  .art {
    display: block;
    width: var(--w);
    height: var(--w);
    border-radius: var(--r-xl);
    overflow: hidden;
    box-shadow: var(--shadow);
    transition: transform var(--d-spatial) var(--spring), box-shadow 300ms var(--ease-emph);
  }
  .art.round {
    border-radius: 50%;
  }
  .card:hover .art {
    border-radius: var(--r-2xl);
    transform: translateY(-4px);
    box-shadow: 0 22px 44px -22px color-mix(in oklab, #000 80%, transparent);
  }
  /* Under the pointer the cover drifts in a little behind its frame, as the bento's tiles do. */
  .art > :global(*) {
    transition: transform var(--d-slow) var(--spring);
  }
  .card:hover .art > :global(*) {
    transform: scale(1.06);
  }
  .card:hover .art.round {
    border-radius: 38%;
  }
  .art:active {
    transform: scale(0.97) !important;
  }
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
    box-shadow: 0 10px 24px -8px color-mix(in oklab, #000 60%, transparent);
    opacity: 0;
    transform: translateY(10px) scale(0.6) rotate(-20deg);
    transition: opacity 200ms var(--ease-emph), transform var(--d-spatial) var(--spring-fast);
  }
  .card:hover .play,
  .play.active,
  .play:focus-visible {
    opacity: 1;
    transform: none;
  }
  .play:hover {
    box-shadow: 0 14px 28px -10px color-mix(in oklab, var(--primary) 70%, transparent);
  }
  .play:active {
    border-radius: 50%;
    transform: scale(0.92);
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
    transition: color 200ms var(--ease-emph);
  }
  .card:hover .title {
    color: var(--primary);
  }
  .sub {
    max-width: 100%;
    font-size: 13.5px;
    color: var(--on-surface-variant);
    margin-top: 2px;
  }
</style>
