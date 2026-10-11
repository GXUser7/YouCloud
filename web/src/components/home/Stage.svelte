<script lang="ts">
  // «Сцена»: home opens on Material 3 Expressive's carousel — one set large with its name and
  // play, the next one half that, the rest narrow strips. Moving on, the large one shrinks away
  // as the next one grows into its place.
  // Under it, the listener's likes in a row, and what the service has besides (the wave, songs).
  import { tr } from '../../lib/i18n.svelte'
  import { set, track as trackOf, type Service, serviceName } from '../../lib/catalog'
  import type { Library } from '../../lib/live'
  import { go, player, playQueue, playSet, playTrack, toggle } from '../../lib/state.svelte'
  import { tracksCount } from '../../lib/format'
  import type { Snippet } from 'svelte'
  import Cover from '../Cover.svelte'
  import Icon from '../Icon.svelte'
  import LikedArt from '../LikedArt.svelte'
  import PlayButton from '../PlayButton.svelte'

  let {
    service,
    lead,
    leadTitle,
    lib,
    signedIn,
    extra,
  }: {
    service: Service
    lead: string[]
    leadTitle: string
    lib: Library | null
    signedIn: boolean
    /** Beside the likes: Yandex's wave, YouTube's songs. */
    extra?: Snippet
  } = $props()

  const items = $derived(lead.slice(0, 12))
  let focus = $state(0)
  const current = $derived(items[focus] ? set(items[focus]) : null)
  const liked = $derived(lib ? set(lib.liked) : null)

  /** How much of the row each one takes: the large, the half, two strips; one strip of the last. */
  function weight(i: number): number {
    const d = i - focus
    if (d === 0) return 6
    if (d === 1) return 3
    if (d === 2 || d === -1) return 1.1
    if (d === 3) return 0.8
    return 0
  }

  function to(i: number) {
    focus = Math.max(0, Math.min(items.length - 1, i))
  }

  // A sideways scroll of the wheel or the touchpad moves on one at a time.
  let wheelAt = 0
  function wheel(e: WheelEvent) {
    if (Math.abs(e.deltaX) < Math.abs(e.deltaY) || Math.abs(e.deltaX) < 12) return
    e.preventDefault()
    const now = performance.now()
    if (now - wheelAt < 380) return
    wheelAt = now
    to(focus + Math.sign(e.deltaX))
  }

  function play(id: string) {
    if (player.contextId === id) toggle()
    else playSet(id)
  }
  function playLiked() {
    if (!liked) return
    if (player.contextId === liked.id) toggle()
    else playQueue(liked.tracks, 0, liked.title, liked.id)
  }
</script>

<section class="stage rise">
  <div class="head">
    <span class="overline">{leadTitle}</span>
    <div class="nav">
      <span class="count">{focus + 1} / {items.length}</span>
      <button class="icon-btn s glassy" disabled={focus === 0} onclick={() => to(focus - 1)} aria-label={tr('Назад')}><Icon name="chevron_left" /></button>
      <button class="icon-btn s glassy" disabled={focus >= items.length - 1} onclick={() => to(focus + 1)} aria-label={tr('Дальше')}><Icon name="chevron_right" /></button>
    </div>
  </div>

  <div class="carousel" onwheel={wheel} role="list">
    {#each items as id, i (id)}
      {@const s = set(id)}
      {@const w = weight(i)}
      {@const role = i === focus ? 'large' : i === focus + 1 ? 'medium' : 'strip'}
      <div class="item {role}" class:gone={w === 0} style:flex-grow={w} role="listitem">
        <button class="bg" onclick={() => (i === focus ? go('set/' + s.id) : to(i))} aria-label={s.title} tabindex={w === 0 ? -1 : 0}>
          <Cover seed={s.id} color={s.color} src={s.cover} kind={s.kind} label={s.label} />
        </button>
        <div class="shade"></div>
        <div class="words">
          <h2 class="display">{s.title}</h2>
          {#if s.subtitle}<p class="sub ellipsis">{s.subtitle}</p>{/if}
        </div>
        <div class="medium-words"><span class="ellipsis">{s.title}</span></div>
        <div class="actions">
          <PlayButton playing={player.contextId === s.id && player.playing} onclick={() => play(s.id)} size={64} />
          <button class="icon-btn glassy l" onclick={() => playSet(s.id, true)} aria-label={tr('Перемешать')} title={tr('Перемешать')}><Icon name="shuffle" /></button>
        </div>
      </div>
    {/each}
  </div>
</section>

<section class="below rise">
  <div class="liked glass">
    {#if liked}
      <button class="liked-art" onclick={() => go('set/' + liked.id)} aria-label={liked.title}>
        <LikedArt ids={liked.tracks} seed={liked.id} color={liked.color} />
      </button>
      <div class="liked-main">
        <div class="liked-head">
          <div>
            <span class="overline">{tr('Любимое')}</span>
            <h3 class="headline">{liked.title}</h3>
            <span class="muted count-s">{tracksCount(liked.tracks.length)}</span>
          </div>
          <PlayButton playing={player.contextId === liked.id && player.playing} onclick={playLiked} size={52} />
        </div>
        <div class="rows">
          {#each liked.tracks.slice(0, 3) as id, i (id)}
            {@const t = trackOf(id)}
            <button class="row" onclick={() => playTrack(id, liked.tracks, liked.title, liked.id)}>
              <span class="mini"><Cover seed={t.id} color={t.color} src={t.cover} /></span>
              <span class="row-text"><span class="ellipsis t">{t.title}</span><span class="ellipsis a">{t.artists.map((a) => a.name).join(', ')}</span></span>
              <Icon name="play_arrow" fill size={20} />
            </button>
          {/each}
        </div>
      </div>
    {:else}
      <span class="liked-art"><LikedArt seed="liked-{service}" color="#d81b60" /></span>
      <div class="liked-main">
        <span class="overline">{tr('Любимое')}</span>
        <h3 class="headline">{signedIn ? tr('Загружаю…') : tr('Здесь будут твои лайки')}</h3>
        {#if !signedIn}
          <button class="btn tonal small" onclick={() => go('settings')}><Icon name="login" size={18} />{tr('Войти в %s', serviceName(service))}</button>
        {/if}
      </div>
    {/if}
  </div>
  {#if extra}<div class="extra glass">{@render extra()}</div>{/if}
</section>

<style>
  .stage {
    margin-top: 24px;
  }
  .head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 12px;
  }
  .nav {
    display: flex;
    align-items: center;
    gap: 6px;
  }
  .nav button:disabled {
    opacity: 0.3;
    pointer-events: none;
  }
  .count {
    font-size: 13px;
    font-variant-numeric: tabular-nums;
    color: var(--on-surface-variant);
    margin-right: 6px;
  }

  /* The row: each item's share of it springs, so the large one hands its place to the next. */
  /* Each item keeps 12 px to its right; the row reaches 12 px past the edge to take the last. */
  .carousel {
    display: flex;
    height: clamp(320px, 44vh, 460px);
    margin-right: -12px;
  }
  .item {
    position: relative;
    flex: 1 1 0;
    min-width: 0;
    margin-right: 12px;
    overflow: hidden;
    border-radius: 28px;
    isolation: isolate;
    transition:
      flex-grow var(--d-slow) var(--spring),
      margin var(--d-slow) var(--spring),
      border-radius var(--d-spatial) var(--spring),
      opacity 300ms var(--ease-emph);
  }
  .item.gone {
    margin-right: 0;
    opacity: 0;
  }
  .item.strip {
    border-radius: 22px;
  }
  .item.large:hover {
    border-radius: 44px;
  }
  .item.strip:hover,
  .item.medium:hover {
    border-radius: 36px;
  }
  .bg {
    position: absolute;
    inset: 0;
    z-index: -2;
  }
  .bg :global(.cover) {
    object-fit: cover;
    transition: transform var(--d-slow) var(--spring);
  }
  .item:hover .bg :global(.cover) {
    transform: scale(1.04);
  }
  .shade {
    position: absolute;
    inset: 0;
    z-index: -1;
    pointer-events: none;
    background: linear-gradient(to top, color-mix(in oklab, var(--surface) 90%, transparent), transparent 62%);
    opacity: 0;
    transition: opacity 400ms var(--ease-emph);
  }
  .large .shade,
  .medium .shade {
    opacity: 1;
  }
  .words {
    position: absolute;
    left: 30px;
    right: 200px;
    bottom: 28px;
    pointer-events: none;
    opacity: 0;
    transform: translateY(12px);
    transition:
      opacity 300ms var(--ease-emph),
      transform var(--d-spatial) var(--spring);
  }
  .large .words {
    opacity: 1;
    transform: none;
    transition-delay: 150ms;
  }
  .words h2 {
    font-size: clamp(30px, 3.6vw, 56px);
    display: -webkit-box;
    -webkit-line-clamp: 2;
    line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }
  .sub {
    margin-top: 8px;
    font-size: 16px;
    color: var(--on-surface-variant);
  }
  .medium-words {
    position: absolute;
    left: 18px;
    right: 18px;
    bottom: 16px;
    font-weight: 750;
    font-size: 16px;
    display: flex;
    pointer-events: none;
    opacity: 0;
    transition: opacity 250ms var(--ease-emph);
  }
  .medium .medium-words {
    opacity: 1;
    transition-delay: 150ms;
  }
  .actions {
    position: absolute;
    right: 26px;
    bottom: 26px;
    display: flex;
    flex-direction: row-reverse;
    align-items: center;
    gap: 10px;
    opacity: 0;
    transform: scale(0.6);
    pointer-events: none;
    transition:
      opacity 250ms var(--ease-emph),
      transform var(--d-spatial) var(--spring-fast);
  }
  .large .actions {
    opacity: 1;
    transform: none;
    pointer-events: auto;
  }

  /* Under the stage: likes, and what the service adds. */
  .below {
    margin-top: 16px;
    display: grid;
    grid-template-columns: minmax(0, 1.25fr) minmax(0, 1fr);
    gap: 16px;
  }
  .below:not(:has(.extra)) {
    grid-template-columns: minmax(0, 1fr);
  }
  .liked,
  .extra {
    border-radius: var(--r-2xl);
    padding: 16px;
    min-width: 0;
  }
  .liked {
    display: flex;
    gap: 18px;
    align-items: stretch;
  }
  .liked-art {
    width: 168px;
    height: 168px;
    flex: none;
    border-radius: var(--r-xl);
    overflow: hidden;
    display: block;
    transition: border-radius var(--d-spatial) var(--spring);
  }
  .liked-art:hover {
    border-radius: 32px;
  }
  .liked-main {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 6px;
    justify-content: center;
  }
  .liked-main .btn {
    align-self: flex-start;
    margin-top: 8px;
  }
  .liked-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
  }
  .liked-head h3,
  .liked-main h3 {
    font-size: 22px;
  }
  .count-s {
    font-size: 13px;
  }
  .rows {
    margin-top: 6px;
    display: flex;
    flex-direction: column;
  }
  .row {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 5px 10px 5px 5px;
    border-radius: var(--r-s);
    text-align: left;
    color: var(--on-surface-variant);
    transition:
      background-color 200ms var(--ease-emph),
      border-radius var(--d-fast) var(--spring-fast);
  }
  .row:hover {
    background: var(--state-hover);
    border-radius: var(--r-l);
    color: var(--on-surface);
  }
  .row :global(.icon) {
    opacity: 0;
    transition: opacity 150ms;
  }
  .row:hover :global(.icon) {
    opacity: 1;
  }
  .mini {
    width: 36px;
    height: 36px;
    border-radius: 10px;
    overflow: hidden;
    flex: none;
  }
  .row-text {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
  }
  .t {
    font-weight: 650;
    font-size: 14px;
    color: var(--on-surface);
  }
  .a {
    font-size: 12.5px;
  }

  @container page (max-width: 900px) {
    .below {
      grid-template-columns: minmax(0, 1fr);
    }
    .words {
      right: 30px;
      bottom: 110px;
    }
  }
</style>
