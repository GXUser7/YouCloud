<script lang="ts">
  import { track as trackOf } from '../lib/catalog'
  import { go, like, player, playTrack } from '../lib/state.svelte'
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
  const liked = $derived(!!player.liked[id])
  const unavailable = $derived(t.playable === false)
</script>

<div
  class="row"
  class:current
  class:compact
  class:unavailable
  role="button"
  tabindex="0"
  ondblclick={play}
  onkeydown={(e) => e.key === 'Enter' && play()}
>
  {#if number !== undefined}
    <span class="num">{number}</span>
  {/if}
  <button class="art" onclick={play} aria-label="Играть {t.title}">
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
      {t.title}
      {#if t.explicit}<span class="e" title="Есть ненормативная лексика">E</span>{/if}
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
  <span class="dur">{time(t.duration)}</span>
  <button class="icon-btn s heart" class:on={liked} class:keep={liked} onclick={() => like(id)} aria-label="Нравится">
    <Icon name="favorite" fill={liked} size={22} />
  </button>
  {#if !compact}
    <span class="more"><TrackMenu {id} /></span>
  {/if}
</div>

<style>
  .row {
    display: flex;
    align-items: center;
    gap: 14px;
    padding: 8px 10px 8px 8px;
    border-radius: var(--r-l);
    transition:
      background-color 200ms var(--ease-emph),
      border-radius var(--d-spatial) var(--spring);
    position: relative;
    user-select: none;
    border: 1px solid transparent;
  }
  .row:hover {
    background: var(--state-hover);
  }
  .row.unavailable {
    opacity: 0.45;
  }
  .row.current {
    background: color-mix(in oklab, var(--primary) 16%, transparent);
    border-color: color-mix(in oklab, var(--primary) 22%, transparent);
    border-radius: var(--r-xl);
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
    width: 44px;
    height: 44px;
    border-radius: var(--r-s);
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
  .heart,
  .more {
    opacity: 0;
  }
  .heart.keep,
  .row:hover .heart,
  .row:hover .more,
  .row:focus-within .heart,
  .row:focus-within .more {
    opacity: 1;
  }
</style>
