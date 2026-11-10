<script lang="ts">
  import { tr } from '../lib/i18n.svelte'
  import { track as trackOf } from '../lib/catalog'
  import { go, isLiked, like, player, playTrack } from '../lib/state.svelte'
  import { time } from '../lib/format'
  import Cover from './Cover.svelte'
  import EqBars from './EqBars.svelte'
  import Icon from './Icon.svelte'
  import TrackMenu from './TrackMenu.svelte'

  let {
    id,
    list,
    context = '',
    contextId = null,
    number,
    showAlbum = false,
    compact = false,
    onplay,
  }: {
    id: string
    list: string[]
    context?: string
    contextId?: string | null
    number?: number
    showAlbum?: boolean
    compact?: boolean
    /** Instead of playing [list] from this track: the queue jumps within itself. */
    onplay?: () => void
  } = $props()

  const play = () => (onplay ? onplay() : playTrack(id, list, context, contextId))

  const t = $derived(trackOf(id))
  const current = $derived(player.queue[player.index] === id)
  const liked = $derived(isLiked(id))
  const unavailable = $derived(t.playable === false)
</script>

<div
  class="row"
  class:current
  class:compact
  class:unavailable
  role="button"
  tabindex="0"
  ondblclick={(e) => { if (!(e.target as HTMLElement).closest('button, a')) play() }}
  onkeydown={(e) => e.target === e.currentTarget && e.key === 'Enter' && play()}
>
  {#if number !== undefined}
    <span class="num">{number}</span>
  {/if}
  <button class="art" onclick={play} aria-label={tr('Играть %s', t.title)}>
    <Cover seed={t.id} color={t.color} src={t.cover} />
    <span class="over">
      {#if current && player.playing}
        <span class="eq-wrap"><EqBars size={18} /></span>
        <span class="pause"><Icon name="pause" fill size={24} /></span>
      {:else}
        <Icon name="play_arrow" fill size={26} />
      {/if}
    </span>
  </button>
  <div class="text">
    <div class="title ellipsis">
      <span class="title-text ellipsis" title={t.title}>{t.title}</span>
      {#if t.explicit}<span class="e" title={tr('Есть ненормативная лексика')}>E</span>{/if}
    </div>
    <div class="artists ellipsis">
      {#each t.artists as a, i (a.id + i)}
        {#if i > 0},&nbsp;{/if}<button class="link" onclick={() => go('artist/' + a.id)}>{a.name}</button>
      {/each}
    </div>
  </div>
  {#if showAlbum && t.album}
    <button class="album link ellipsis" onclick={() => go('set/' + t.album!.id)}>{t.album.title}</button>
  {/if}
  {#if t.live}
    <span class="dur"><span class="badge-live">{tr('Эфир')}</span></span>
  {:else}
    <!-- No length known (home's songs on YouTube, before it is asked for): nothing, not 0:00. -->
    <span class="dur">{t.duration ? time(t.duration) : ''}</span>
  {/if}
  <button class="icon-btn s heart" class:on={liked} class:keep={liked} onclick={() => like(id)} aria-label={tr('Нравится')}>
    <Icon name="favorite" fill={liked} size={22} />
  </button>
  <span class="more"><TrackMenu {id} /></span>
</div>

<style>
  .row {
    display: flex;
    align-items: center;
    gap: 12px;
    min-width: 0;
    min-height: 72px;
    padding: 8px 10px 8px 8px;
    border-radius: var(--r-l);
    transition: background-color 200ms var(--ease-emph);
    position: relative;
    user-select: none;
  }
  .row:hover,
  .row:focus-within {
    background: color-mix(in oklab, var(--on-surface) 8%, transparent);
  }
  .row:focus-visible {
    outline-offset: -3px;
  }
  .row.unavailable {
    opacity: 0.45;
  }
  .num {
    width: 26px;
    text-align: center;
    font-variant-numeric: tabular-nums;
    color: var(--on-surface-variant);
    font-weight: 600;
    display: grid;
    place-items: center;
    flex: none;
  }
  .current .num {
    color: var(--primary);
  }
  .art {
    width: 52px;
    height: 52px;
    border-radius: var(--r-m);
    overflow: hidden;
    flex: none;
    position: relative;
    transition: border-radius var(--d-spatial) var(--spring);
  }
  .compact .art {
    width: 52px;
    height: 52px;
  }
  .current .art {
    border-radius: 50%;
  }
  .over {
    position: absolute;
    inset: 0;
    display: grid;
    place-items: center;
    background: color-mix(in oklab, #000 42%, transparent);
    color: #fff;
    opacity: 0;
    transition: opacity 180ms var(--ease-emph);
  }
  .over > :global(*) {
    grid-area: 1 / 1;
  }
  .row:hover .over,
  .current .over {
    opacity: 1;
  }
  .pause {
    display: grid;
    place-items: center;
    opacity: 0;
    transition: opacity 150ms;
  }
  .row:hover .pause {
    opacity: 1;
  }
  .row:hover .eq-wrap {
    opacity: 0;
  }
  .text {
    flex: 1;
    min-width: 0;
  }
  .title {
    font-weight: 650;
    font-size: 15.5px;
    display: flex;
    align-items: center;
    gap: 6px;
  }
  .title-text { min-width: 0; }
  .eq-wrap { display: grid; place-items: center; }
  .current .title {
    color: var(--primary);
  }
  .e {
    flex: none;
    display: inline-grid;
    place-items: center;
    width: 16px;
    height: 16px;
    border-radius: 4px;
    font-size: 10px;
    font-weight: 800;
    background: color-mix(in oklab, var(--on-surface) 18%, transparent);
    color: var(--on-surface-variant);
  }
  .artists,
  .album {
    color: var(--on-surface-variant);
    font-size: 14px;
    margin-top: 2px;
  }
  .album {
    flex: 0 1 26%;
    min-width: 0;
    text-align: left;
    margin: 0;
  }
  .link:hover {
    text-decoration: underline;
    color: var(--on-surface);
  }
  .dur {
    width: 44px;
    text-align: right;
    font-variant-numeric: tabular-nums;
    color: var(--on-surface-variant);
    font-size: 14px;
    flex: none;
  }
  .more { display: grid; place-items: center; flex: none; }
  .heart { opacity: 0.65; }
  .heart.keep,
  .row:hover .heart,
  .row:hover .more,
  .row:focus-within .heart,
  .row:focus-within .more {
    opacity: 1;
  }
  @container page (max-width: 600px) {
    .row { gap: 8px; padding-inline: 8px; }
    .num { display: none; }
    .album, .dur, .heart { display: none; }
    .title { font-size: 15px; }
  }
  @media (hover: none) { .heart { opacity: 1; } }
</style>
