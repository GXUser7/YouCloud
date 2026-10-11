<script lang="ts">
  // The player along the bottom: one bar, everything on a single line — what plays (the cover
  // opens the full player), the controls, the progress, the panels and the volume. One row keeps
  // it as low as a cover, and the page gets the height back.
  import { serviceName } from '../lib/catalog'
  import { app, currentTrack, cycleRepeat, go, isLiked, like, next, openPanel, player, prev, seek, toggle, toggleShuffle, type Panel } from '../lib/state.svelte'
  import { time } from '../lib/format'
  import Cover from './Cover.svelte'
  import Icon from './Icon.svelte'
  import LoadingIndicator from './LoadingIndicator.svelte'
  import MorphPlay from './MorphPlay.svelte'
  import ServiceGlyph from './ServiceGlyph.svelte'
  import VolumeControl from './VolumeControl.svelte'
  import WavyProgress from './WavyProgress.svelte'

  const t = $derived(currentTrack())
  const liked = $derived(!!t && isLiked(t.id))
  const duration = $derived(player.duration || t?.duration || 0)
  const source = $derived(t ? (player.wave ? 'Моя волна' : player.context || serviceName(t.service)) : '')

  const panels: { id: Panel; icon: string; label: string }[] = [
    { id: 'lyrics', icon: 'lyrics', label: 'Текст' },
    { id: 'queue', icon: 'queue_music', label: 'Очередь' },
    { id: 'friends', icon: 'group', label: 'Друзья' },
  ]
</script>

<footer class="bar glass-strong">
  <section class="now">
    {#if t}
      <button class="cover" class:spinning={player.playing} onclick={() => (app.nowPlaying = true)} aria-label="Открыть плеер" title="Открыть плеер">
        {#key t.id}
          <span class="cover-art"><Cover seed={t.id} color={t.color} src={t.cover} /></span>
        {/key}
        <span class="open"><Icon name="open_in_full" size={16} /></span>
      </button>
      <div class="text">
        {#key t.id}
          <span class="title ellipsis swap">{t.title}</span>
          <span class="sub ellipsis swap">
            {#each t.artists as a, i (a.id + i)}
              {#if i > 0},&nbsp;{/if}<button class="link" onclick={() => go('artist/' + a.id)}>{a.name}</button>
            {/each}
            <span class="source" title={source}>
              <ServiceGlyph service={t.service} size={12} />{source}{#if player.radio && !player.wave}<Icon name="radio" size={12} />{/if}
            </span>
          </span>
        {/key}
      </div>
      <button class="icon-btn s heart" class:active={liked} onclick={() => like(t.id)} aria-label="Нравится" aria-pressed={liked}>
        <Icon name="favorite" fill={liked} size={18} />
      </button>
    {:else}
      <span class="cover empty"><Icon name="music_note" size={22} /></span>
      <div class="text">
        <span class="title">Ничего не играет</span>
        <span class="sub">Найди трек или включи подборку</span>
      </div>
    {/if}
  </section>

  <div class="transport">
    <button class="icon-btn s opt" class:active={player.shuffle} onclick={toggleShuffle} aria-label="Перемешать" aria-pressed={player.shuffle}>
      <Icon name="shuffle" size={18} />
    </button>
    <button class="icon-btn" onclick={prev} aria-label="Предыдущий"><Icon name="skip_previous" fill size={24} /></button>
    <span class="play-wrap">
      <MorphPlay playing={player.playing} onclick={toggle} size={48} />
      {#if player.loading && t}<span class="busy"><LoadingIndicator size={48} contained={false} /></span>{/if}
    </span>
    <button class="icon-btn" onclick={() => next()} aria-label="Следующий"><Icon name="skip_next" fill size={24} /></button>
    <button class="icon-btn s opt" class:active={player.repeat !== 'off'} onclick={cycleRepeat} aria-label="Повтор" aria-pressed={player.repeat !== 'off'}>
      <Icon name={player.repeat === 'one' ? 'repeat_one' : 'repeat'} size={18} />
    </button>
  </div>

  <div class="progress">
    <span class="time">{time(player.position)}</span>
    <WavyProgress value={duration ? player.position / duration : 0} playing={player.playing && !player.loading} onseek={(f) => seek(f * duration)} />
    <span class="time">{time(duration)}</span>
  </div>

  <div class="side">
    <div class="group" role="group" aria-label="Панель">
      {#each panels as p (p.id)}
        {@const on = app.panelOpen && app.panel === p.id}
        <button class="seg" class:on onclick={() => openPanel(p.id)} aria-label={p.label} title={p.label} aria-pressed={on}>
          <Icon name={p.icon} fill={on} size={20} />
        </button>
      {/each}
    </div>
    <div class="vol"><VolumeControl /></div>
  </div>
</footer>

<style>
  /* As wide as the page's column and on the same edges as the bar on top — not the window's
     width, which on a wide screen stretched the progress across the whole monitor. */
  .bar {
    grid-area: player;
    justify-self: center;
    width: min(1480px, 100% - 72px);
    z-index: 3;
    min-width: 0;
    height: 64px;
    box-sizing: border-box;
    display: flex;
    align-items: center;
    gap: 18px;
    padding: 8px 12px 8px 8px;
    border-radius: 32px;
    box-shadow: 0 -10px 40px -26px color-mix(in oklab, #000 60%, transparent);
  }

  /* ---- What plays ---- */
  .now {
    flex: 0 1 320px;
    min-width: 180px;
    display: flex;
    align-items: center;
    gap: 12px;
  }
  .cover {
    width: 48px;
    height: 48px;
    border-radius: var(--r-m);
    overflow: hidden;
    position: relative;
    flex: none;
    display: grid;
    transition:
      border-radius var(--d-slow) var(--spring),
      transform var(--d-fast) var(--spring-fast);
  }
  .cover > * {
    grid-area: 1 / 1;
  }
  /* Playing, the cover rounds into a disc: the shape says it plays before the button does. */
  .cover.spinning {
    border-radius: 50%;
  }
  .cover:active {
    transform: scale(0.94);
  }
  .cover.empty {
    place-items: center;
    background: color-mix(in oklab, var(--on-surface) 8%, transparent);
    color: var(--on-surface-variant);
  }
  .cover-art {
    display: block;
    width: 100%;
    height: 100%;
    animation: pop var(--d-spatial) var(--spring);
  }
  @keyframes pop {
    from {
      transform: scale(0.6);
      opacity: 0;
    }
  }
  .open {
    display: grid;
    place-items: center;
    background: color-mix(in oklab, #000 40%, transparent);
    color: #fff;
    opacity: 0;
    transition: opacity 150ms;
  }
  .cover:hover .open {
    opacity: 1;
  }
  .text {
    min-width: 0;
    flex: 1;
    display: flex;
    flex-direction: column;
    gap: 1px;
  }
  .swap {
    animation: slide-in var(--d-spatial) var(--spring);
  }
  @keyframes slide-in {
    from {
      transform: translateY(6px);
      opacity: 0;
    }
  }
  .title {
    font-weight: 700;
    font-size: 15px;
    line-height: 1.25;
  }
  .sub {
    font-size: 13px;
    line-height: 1.3;
    color: var(--on-surface-variant);
  }
  .link:hover {
    text-decoration: underline;
    color: var(--on-surface);
  }
  /* Where it plays from, after the artists: the playlist, the wave, the service. */
  .source {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    margin-left: 8px;
    font-size: 12px;
    font-weight: 650;
    color: var(--primary);
    vertical-align: -1px;
  }

  /* ---- Controls and progress ---- */
  .transport {
    flex: none;
    display: flex;
    align-items: center;
    gap: 2px;
  }
  .transport .opt {
    margin: 0 4px;
  }
  .play-wrap {
    position: relative;
    display: grid;
    margin: 0 4px;
  }
  .busy {
    position: absolute;
    inset: 0;
    pointer-events: none;
    opacity: 0.45;
    mix-blend-mode: screen;
  }
  .progress {
    flex: 1 1 auto;
    min-width: 140px;
    display: flex;
    align-items: center;
    gap: 10px;
  }
  .time {
    flex: none;
    width: 38px;
    font-size: 12px;
    font-variant-numeric: tabular-nums;
    text-align: center;
    color: var(--on-surface-variant);
  }

  /* ---- Panels and volume ---- */
  .side {
    flex: none;
    display: flex;
    align-items: center;
    gap: 12px;
  }
  .group {
    display: flex;
    gap: 3px;
  }
  .seg {
    width: 40px;
    height: 40px;
    display: grid;
    place-items: center;
    color: var(--on-surface);
    background: color-mix(in oklab, var(--on-surface) 8%, transparent);
    border-radius: 6px;
    transition:
      border-radius var(--d-spatial) var(--spring-fast),
      background-color 250ms var(--ease-emph),
      color 250ms var(--ease-emph),
      width var(--d-spatial) var(--spring-fast);
  }
  .seg:first-child {
    border-radius: 20px 6px 6px 20px;
  }
  .seg:last-child {
    border-radius: 6px 20px 20px 6px;
  }
  .seg:hover {
    background: color-mix(in oklab, var(--on-surface) 14%, transparent);
  }
  .seg:active {
    border-radius: 6px;
  }
  .seg.on {
    background: var(--primary);
    color: var(--on-primary);
    border-radius: 20px;
    width: 48px;
  }
  .vol {
    display: flex;
  }

  /* Narrower, things step aside for the progress in this order: the times, shuffle and repeat,
     and only then the volume, which the listener keeps at hand. */
  @media (max-width: 1240px) {
    .time {
      display: none;
    }
  }
  /* The bar on top keeps a slimmer margin here too. */
  @media (max-width: 1100px) {
    .bar {
      width: calc(100% - 40px);
    }
  }
  @media (max-width: 980px) {
    .transport .opt {
      display: none;
    }
    .now {
      flex-basis: 240px;
    }
  }
  @media (max-width: 820px) {
    .vol {
      display: none;
    }
  }
</style>
