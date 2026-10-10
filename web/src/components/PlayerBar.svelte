<script lang="ts">
  // The player along the bottom as three islands: what plays (the cover opens the full player),
  // the controls with the progress under them, and the panels and the volume.
  import { serviceName } from '../lib/catalog'
  import { app, currentTrack, cycleRepeat, go, like, next, openPanel, player, prev, seek, toggle, toggleShuffle, type Panel } from '../lib/state.svelte'
  import { time } from '../lib/format'
  import Cover from './Cover.svelte'
  import Icon from './Icon.svelte'
  import LoadingIndicator from './LoadingIndicator.svelte'
  import MorphPlay from './MorphPlay.svelte'
  import ServiceGlyph from './ServiceGlyph.svelte'
  import VolumeControl from './VolumeControl.svelte'
  import WavyProgress from './WavyProgress.svelte'

  const t = $derived(currentTrack())
  const liked = $derived(!!t && !!player.liked[t.id])
  const duration = $derived(player.duration || t?.duration || 0)

  const panels: { id: Panel; icon: string; label: string }[] = [
    { id: 'lyrics', icon: 'lyrics', label: 'Текст' },
    { id: 'queue', icon: 'queue_music', label: 'Очередь' },
    { id: 'friends', icon: 'group', label: 'Друзья' },
  ]
</script>

<footer class="bar">
  <section class="island now glass-strong">
    {#if t}
      <button class="cover" class:spinning={player.playing} onclick={() => (app.nowPlaying = true)} aria-label="Открыть плеер" title="Открыть плеер">
        {#key t.id}
          <span class="cover-art"><Cover seed={t.id} color={t.color} src={t.cover} /></span>
        {/key}
        <span class="open"><Icon name="open_in_full" size={18} /></span>
      </button>
      <div class="text">
        {#key t.id}
          <span class="title ellipsis swap">{t.title}</span>
          <span class="artists ellipsis swap">
            {#each t.artists as a, i (a.id + i)}
              {#if i > 0},&nbsp;{/if}<button class="link" onclick={() => go('artist/' + a.id)}>{a.name}</button>
            {/each}
          </span>
        {/key}
        <span class="source">
          <ServiceGlyph service={t.service} size={13} />
          <span class="ellipsis">{player.wave ? 'Моя волна' : player.context || serviceName(t.service)}</span>
          {#if player.radio && !player.wave}<Icon name="radio" size={13} />{/if}
        </span>
      </div>
      <button class="icon-btn s heart" class:active={liked} onclick={() => like(t.id)} aria-label="Нравится" aria-pressed={liked}>
        <Icon name="favorite" fill={liked} size={20} />
      </button>
    {:else}
      <span class="cover empty"><Icon name="music_note" size={26} /></span>
      <div class="text">
        <span class="title">Ничего не играет</span>
        <span class="artists">Найди трек или включи подборку</span>
      </div>
    {/if}
  </section>

  <section class="island center glass-strong">
    <div class="controls">
      <button class="icon-btn s" class:active={player.shuffle} onclick={toggleShuffle} aria-label="Перемешать" aria-pressed={player.shuffle}>
        <Icon name="shuffle" size={20} />
      </button>
      <button class="icon-btn" onclick={prev} aria-label="Предыдущий"><Icon name="skip_previous" fill size={26} /></button>
      <span class="play-wrap">
        <MorphPlay playing={player.playing} onclick={toggle} size={62} />
        {#if player.loading && t}<span class="busy"><LoadingIndicator size={62} contained={false} /></span>{/if}
      </span>
      <button class="icon-btn" onclick={() => next()} aria-label="Следующий"><Icon name="skip_next" fill size={26} /></button>
      <button class="icon-btn s" class:active={player.repeat !== 'off'} onclick={cycleRepeat} aria-label="Повтор" aria-pressed={player.repeat !== 'off'}>
        <Icon name={player.repeat === 'one' ? 'repeat_one' : 'repeat'} size={20} />
      </button>
    </div>
    <div class="progress">
      <span class="time">{time(player.position)}</span>
      <WavyProgress value={duration ? player.position / duration : 0} playing={player.playing && !player.loading} onseek={(f) => seek(f * duration)} />
      <span class="time">{time(duration)}</span>
    </div>
  </section>

  <section class="island side glass-strong">
    <div class="group" role="group" aria-label="Панель">
      {#each panels as p (p.id)}
        {@const on = app.panelOpen && app.panel === p.id}
        <button class="seg" class:on onclick={() => openPanel(p.id)} aria-label={p.label} title={p.label} aria-pressed={on}>
          <Icon name={p.icon} fill={on} size={20} />
        </button>
      {/each}
    </div>
    <div class="vol"><VolumeControl /></div>
  </section>
</footer>

<style>
  .bar {
    grid-area: player;
    display: grid;
    grid-template-columns: minmax(240px, 340px) minmax(380px, 1fr) auto;
    gap: 12px;
    z-index: 3;
    min-width: 0;
  }
  .island {
    border-radius: var(--r-2xl);
    min-width: 0;
    box-shadow: 0 -10px 40px -26px color-mix(in oklab, #000 60%, transparent);
  }

  .now {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 10px;
  }
  .cover {
    width: 60px;
    height: 60px;
    border-radius: var(--r-l);
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
  }
  .swap {
    animation: slide-in var(--d-spatial) var(--spring);
  }
  @keyframes slide-in {
    from {
      transform: translateY(8px);
      opacity: 0;
    }
  }
  .title {
    font-weight: 700;
    font-size: 15.5px;
  }
  .artists {
    font-size: 13.5px;
    color: var(--on-surface-variant);
  }
  .link:hover {
    text-decoration: underline;
    color: var(--on-surface);
  }
  .source {
    display: flex;
    align-items: center;
    gap: 5px;
    margin-top: 3px;
    font-size: 11.5px;
    font-weight: 650;
    color: var(--primary);
    min-width: 0;
  }

  .center {
    display: flex;
    flex-direction: column;
    justify-content: center;
    gap: 2px;
    padding: 8px 20px 6px;
  }
  .controls {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 14px;
  }
  .play-wrap {
    position: relative;
    display: grid;
  }
  .busy {
    position: absolute;
    inset: 0;
    pointer-events: none;
    opacity: 0.45;
    mix-blend-mode: screen;
  }
  .progress {
    display: flex;
    align-items: center;
    gap: 12px;
  }
  .time {
    font-size: 12px;
    font-variant-numeric: tabular-nums;
    color: var(--on-surface-variant);
    width: 36px;
    text-align: center;
    flex: none;
  }

  .side {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 10px 14px 10px 10px;
  }
  .group {
    display: flex;
    gap: 3px;
  }
  .seg {
    width: 44px;
    height: 44px;
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
    border-radius: 22px 6px 6px 22px;
  }
  .seg:last-child {
    border-radius: 6px 22px 22px 6px;
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
    border-radius: 22px;
    width: 52px;
  }
  .vol {
    display: flex;
  }
  @media (max-width: 1280px) {
    .bar {
      grid-template-columns: minmax(200px, 280px) minmax(340px, 1fr) auto;
    }
    .vol {
      display: none;
    }
  }
</style>
