<script lang="ts">
  // «Афиша»: the full player as the track's poster. The window takes the cover's brightest tone —
  // the scheme's primary, light in the dark theme and deep in the light one — and its ink; the
  // title is set across the whole width, as large as the window lets it, and the cover lies over
  // its top when it is short. The song's words, or what comes next, are the poster's text, and
  // everything pressed is low: what is next, the controls in an ink capsule, the rest beside.
  // With «Волна в плеере» on, the poster is printed on the wave instead: the page's dark (or light)
  // surface moving to the music, the ink its text colour — the bright paper only without it.
  import { tr } from '../../lib/i18n.svelte'
  import { serviceName, track } from '../../lib/catalog'
  import { fit } from '../../lib/fit'
  import { time } from '../../lib/format'
  import { queueWindow, revealNow } from '../../lib/queueView'
  import { app, currentTrack, cycleRepeat, go, jump, player, remember, seek, startRadio, stored } from '../../lib/state.svelte'
  import Cover from '../Cover.svelte'
  import Icon from '../Icon.svelte'
  import Lyrics from '../Lyrics.svelte'
  import PlayerControls from '../PlayerControls.svelte'
  import PlayerWave from '../PlayerWave.svelte'
  import Slider from '../Slider.svelte'
  import TrackMenu from '../TrackMenu.svelte'
  import VolumeButton from '../VolumeButton.svelte'

  let { onclose }: { onclose: () => void } = $props()

  const t = $derived(currentTrack()!)
  const duration = $derived(player.duration || t.duration || 1)
  const upNext = $derived(player.queue[player.index + 1] ? track(player.queue[player.index + 1]) : null)
  const list = $derived(queueWindow(40))
  let artEl = $state<HTMLElement>()
  let scrub = $state<number | null>(null)

  let side = $state<'lyrics' | 'queue'>(stored<string>('posterSide', 'lyrics') === 'queue' ? 'queue' : 'lyrics')
  function show(view: 'lyrics' | 'queue') {
    side = view
    remember('posterSide', view)
  }

  // A short title stays on one line under the cover's edge; a long one gets the whole width.
  const tuck = $derived(t.title.length <= 12)
  let mainH = $state(0)
  let width = $state(innerWidth)
  const TUCK = 0.07
  const art = $derived(Math.max(120, Math.min(mainH + (tuck ? innerHeight * TUCK : 0), width * 0.4)))

  function leave(path: string) {
    onclose()
    go(path)
  }
</script>

<svelte:window bind:innerWidth={width} />

<div class="poster" class:waved={app.playerWave} role="dialog" aria-label={tr('Сейчас играет')}>
  {#if app.playerWave}<div class="bg"><PlayerWave around={artEl} /></div>{/if}
  <svg class="grain" aria-hidden="true">
    <filter id="poster-grain" x="0" y="0" width="100%" height="100%">
      <feTurbulence type="fractalNoise" baseFrequency=".85" numOctaves="2" stitchTiles="stitch" />
      <feColorMatrix values="0 0 0 0 0  0 0 0 0 0  0 0 0 0 0  0 0 0 .9 0" />
    </filter>
    <rect width="100%" height="100%" filter="url(#poster-grain)" />
  </svg>

  <div class="top">
    <span class="ellipsis">{player.wave ? tr('Моя волна') : tr('Играет из')} — {player.wave ? tr('Яндекс Музыка') : player.context || serviceName(t.service)}</span>
    <span class="ellipsis mid">{serviceName(t.service)}{#if t.album} · {t.album.title || tr('Альбом')}{/if}</span>
    <span class="count">{player.index + 1} / {player.queue.length}</span>
  </div>
  <div class="rule"></div>

  <div class="main" bind:clientHeight={mainH}>
    <div class="text">
      <div class="artists">
        {#each t.artists as a, i (a.id + i)}
          <button class="artist" onclick={() => leave('artist/' + a.id)}>{a.name}{i < t.artists.length - 1 ? ',' : ''}</button>
        {/each}
      </div>
      <div class="tags">
        {#if t.album}
          <button class="tag" onclick={() => leave('set/' + t.album!.id)}><Icon name="album" size={18} /><span class="ellipsis album">{t.album.title || tr('Альбом')}</span></button>
        {/if}
        {#if !player.live && duration > 1}<span class="tag">{time(duration)}</span>{/if}
      </div>
      <div class="words">
        {#if side === 'lyrics'}
          <Lyrics size="l" />
        {:else}
          <ol class="list" use:revealNow={0}>
            {#each list.ids as id, i (list.from + i + ':' + id)}
              {@const q = track(id)}
              {@const at = list.from + i}
              <li class:past={at < player.index} class:now={at === player.index} data-now={at === player.index ? '' : undefined}>
                <button onclick={() => jump(at)} aria-current={at === player.index ? 'true' : undefined}>
                  <span class="n">{String(at + 1).padStart(2, '0')}</span>
                  <span class="qt ellipsis">{q.title}</span>
                  <span class="qd">{q.duration ? time(q.duration) : ''}</span>
                  <span class="qa ellipsis">{q.artists.map((a) => a.name).join(', ')}</span>
                </button>
              </li>
            {/each}
            {#if player.index + 1 >= player.queue.length}
              <li class="empty">{player.radio ? tr('Радио подбирает следующие треки…') : tr('Дальше ничего нет — включи радио по треку.')}</li>
            {/if}
          </ol>
        {/if}
      </div>
    </div>
    {#key t.id}
      <div class="art" class:tuck bind:this={artEl} style:width="{art}px" style:height="{art}px"><Cover seed={t.id} color={t.color} src={t.cover} grain /></div>
    {/key}
  </div>

  {#key t.id}
    <h1 class="title" class:tuck title={t.title} use:fit={{ text: t.title, max: 420, min: 28, height: () => Math.max(72, innerHeight * 0.3) }}>{t.title}</h1>
  {/key}

  {#if player.live}
    <div class="seek live"><span class="dot"></span>{tr('В эфире')}</div>
  {:else}
    <div class="seek">
      <span>{time(scrub ?? player.position)}</span>
      <div class="slider">
        <Slider
          value={(scrub ?? player.position) / duration}
          thickness={6}
          label={tr('Перемотка')}
          onchange={(f) => (scrub = f * duration)}
          onrelease={(f) => {
            seek(f * duration)
            scrub = null
          }}
        />
      </div>
      <span>{time(duration)}</span>
    </div>
  {/if}

  <div class="bar">
    <button class="next" class:on={side === 'queue'} onclick={() => show('queue')} aria-label={tr('Показать очередь')}>
      {#if upNext}
        {#key upNext.id}<span class="next-cover"><Cover seed={upNext.id} color={upNext.color} src={upNext.cover} /></span>{/key}
        <span class="next-text">
          <span class="kicker">{tr('Далее')}</span>
          <span class="ellipsis next-title">{upNext.title}</span>
          <span class="ellipsis next-artist">{upNext.artists.map((a) => a.name).join(', ')}</span>
        </span>
      {:else}
        <span class="next-cover empty"><Icon name="radio" size={22} /></span>
        <span class="next-text"><span class="kicker">{tr('Далее')}</span><span class="ellipsis next-title">{player.radio ? tr('Радио подбирает треки…') : tr('Очередь пуста')}</span></span>
      {/if}
    </button>

    <div class="capsule">
      <PlayerControls size="m" items={['like', 'prev', 'play', 'next', 'shuffle']} />
    </div>

    <div class="acts">
      <button class="pb" class:on={side === 'lyrics'} onclick={() => show('lyrics')} aria-pressed={side === 'lyrics'} aria-label={tr('Текст')} title={tr('Показать текст')}>
        <Icon name="lyrics" fill={side === 'lyrics'} size={22} />
      </button>
      <button class="pb" class:on={side === 'queue'} onclick={() => show('queue')} aria-pressed={side === 'queue'} aria-label={tr('Очередь')} title={tr('Показать очередь')}>
        <Icon name="queue_music" size={22} />
      </button>
      <button class="pb hide-s" onclick={() => startRadio(t.id)} aria-label={tr('Радио по треку')} title={tr('Радио по треку')}>
        <Icon name="radio" size={22} />
      </button>
      <button
        class="pb hide-s"
        class:on={player.repeat !== 'off'}
        onclick={cycleRepeat}
        aria-pressed={player.repeat !== 'off'}
        aria-label={tr('Повтор')}
        title={player.repeat === 'one' ? tr('Повтор трека') : player.repeat === 'all' ? tr('Повтор очереди') : tr('Повтор')}
      >
        <Icon name={player.repeat === 'one' ? 'repeat_one' : 'repeat'} size={22} />
      </button>
      <span class="vol-host"><VolumeButton align="end" /></span>
      <span class="menu-host"><TrackMenu id={t.id} large onleave={onclose} /></span>
      <button class="pb" onclick={onclose} aria-label={tr('Свернуть')} title={tr('Свернуть')}><Icon name="keyboard_arrow_down" size={26} /></button>
    </div>
  </div>
</div>

<style>
  .poster {
    --r-m: 14px;
    --r-l: 16px;
    --r-xl: 20px;
    --r-2xl: 24px;
    --paper: var(--primary);
    --ink: var(--on-primary);
    /* What the shadows are cast in: the ink on paper; plain black on the wave, whose ink is light. */
    --shade: var(--ink);
    position: fixed;
    inset: 0;
    z-index: 20;
    overflow: hidden;
    display: flex;
    flex-direction: column;
    padding: clamp(16px, 3vh, 30px) clamp(18px, 3.2vw, 44px) clamp(14px, 2.6vh, 26px);
    background:
      radial-gradient(ellipse 60% 70% at 16% 14%, color-mix(in oklab, var(--paper), #fff 16%), transparent 70%),
      var(--paper);
    color: var(--ink);
    animation: up 420ms var(--ease-in-place);
  }
  @keyframes up {
    from {
      transform: translateY(28px);
      opacity: 0;
    }
  }
  .grain {
    position: absolute;
    inset: 0;
    width: 100%;
    height: 100%;
    opacity: 0.12;
    mix-blend-mode: multiply;
    pointer-events: none;
  }
  .poster > :not(.grain, .bg) {
    position: relative;
  }
  /* On the wave: the page's surface for paper, its text colour for ink; the wave has grain of its own. */
  .poster.waved {
    --paper: var(--surface);
    --ink: var(--on-surface);
    --shade: #000;
    background: var(--surface);
  }
  .waved .grain {
    display: none;
  }
  .bg {
    position: absolute;
    inset: 0;
  }

  .top {
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
    gap: 16px;
    font-size: 12px;
    font-weight: 800;
    letter-spacing: 0.16em;
    text-transform: uppercase;
  }
  .mid {
    text-align: center;
    max-width: 40vw;
  }
  .count {
    text-align: right;
    font-variant-numeric: tabular-nums;
  }
  .rule {
    height: 1.5px;
    margin-top: 10px;
    background: color-mix(in oklab, var(--ink) 35%, transparent);
  }

  .main {
    flex: 1;
    min-height: 0;
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto;
    align-items: start;
    gap: clamp(20px, 4vw, 64px);
    margin-top: clamp(14px, 3vh, 32px);
  }
  .text {
    height: 100%;
    min-height: 0;
    display: flex;
    flex-direction: column;
  }
  .artists {
    display: flex;
    flex-wrap: wrap;
    gap: 0 0.3em;
    font-size: clamp(24px, 5vh, 46px);
    font-weight: 800;
    letter-spacing: -0.02em;
    line-height: 1.05;
  }
  .artist {
    border-radius: 10px;
    transition: opacity 150ms;
  }
  .artist:hover {
    opacity: 0.7;
  }
  .tags {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    margin-top: clamp(8px, 1.6vh, 16px);
  }
  .tag {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    height: 30px;
    padding: 0 13px;
    border-radius: 15px;
    background: color-mix(in oklab, var(--ink) 12%, transparent);
    font-size: 13px;
    font-weight: 700;
    transition:
      background-color 200ms var(--ease-emph),
      border-radius var(--d-fast) var(--spring-fast);
  }
  button.tag:hover {
    background: color-mix(in oklab, var(--ink) 18%, transparent);
  }
  button.tag:active {
    border-radius: 8px;
  }
  .album {
    max-width: 260px;
  }

  /* The poster's text: the song's words in ink, the line sung full, the rest faded. */
  .words {
    --on-surface: var(--ink);
    --primary: var(--ink);
    flex: 1;
    min-height: 0;
    margin-top: clamp(8px, 2vh, 24px);
    max-width: 680px;
  }
  .words :global(.state) {
    place-items: center start;
    text-align: left;
    padding: 0 8px;
  }
  .words :global(.line) {
    font-family: var(--font-body);
    font-weight: 800;
    letter-spacing: -0.01em;
  }
  .words :global(.l .line) {
    font-size: clamp(18px, 3vh, 26px);
    padding: 4px 8px;
  }
  .words :global(button.line:hover) {
    background: color-mix(in oklab, var(--ink) 8%, transparent);
  }
  .list {
    list-style: none;
    margin: 0;
    padding: 0;
    height: 100%;
    overflow-y: auto;
    scrollbar-width: none;
    mask-image: linear-gradient(#000 82%, transparent);
  }
  .list button {
    width: 100%;
    display: grid;
    grid-template-columns: 34px minmax(0, 1fr) auto;
    align-items: baseline;
    column-gap: 12px;
    padding: 7px 10px;
    border-radius: 14px;
    text-align: left;
    transition: background-color 150ms;
  }
  .list button:hover {
    background: color-mix(in oklab, var(--ink) 8%, transparent);
  }
  /* The one playing stands out; what was played is quieter, still there to pick. */
  .list .now button {
    background: color-mix(in oklab, var(--ink) 14%, transparent);
  }
  .list .past {
    opacity: 0.55;
    transition: opacity 150ms;
  }
  .list .past:hover,
  .list .past:focus-within {
    opacity: 1;
  }
  .n {
    font-weight: 800;
    font-size: 13px;
    font-variant-numeric: tabular-nums;
    color: color-mix(in oklab, var(--ink) 70%, var(--paper));
  }
  .qt {
    font-size: clamp(16px, 2.6vh, 22px);
    font-weight: 800;
    letter-spacing: -0.01em;
  }
  .qd {
    font-size: 13px;
    font-weight: 700;
    font-variant-numeric: tabular-nums;
  }
  .qa {
    grid-column: 2 / 4;
    font-size: 14px;
    font-weight: 600;
    color: color-mix(in oklab, var(--ink) 78%, var(--paper));
  }
  .empty {
    font-weight: 700;
    padding: 8px 10px;
  }

  .art {
    z-index: 2;
    border-radius: 22px;
    overflow: hidden;
    box-shadow:
      0 46px 80px -34px color-mix(in oklab, var(--shade) 75%, transparent),
      0 10px 26px -10px color-mix(in oklab, var(--shade) 45%, transparent);
    animation: art-in 480ms var(--ease-in-place);
  }
  @keyframes art-in {
    from {
      transform: scale(0.94);
      opacity: 0;
    }
  }

  /* The title: as large as the width and a third of the window let it, its top under the cover. */
  .title {
    z-index: 1;
    width: 100%;
    margin: 0.06em 0 0;
    flex-shrink: 0;
    padding-block: 0.15em;
    font-family: var(--font-display);
    font-weight: 900;
    line-height: 0.88;
    letter-spacing: -0.045em;
    text-wrap: balance;
    overflow-wrap: anywhere;
    animation: title-in 420ms var(--ease-in-place);
  }
  @keyframes title-in {
    from {
      opacity: 0;
      transform: translateY(14px);
    }
  }

  .seek {
    --primary: var(--ink);
    display: flex;
    align-items: center;
    gap: 14px;
    margin-top: clamp(8px, 1.6vh, 16px);
    font-size: 13px;
    font-weight: 800;
    font-variant-numeric: tabular-nums;
  }
  .slider {
    flex: 1;
    min-width: 0;
  }
  .seek.live {
    height: 32px;
    gap: 10px;
    font-size: 15px;
  }
  .dot {
    width: 10px;
    height: 10px;
    border-radius: 50%;
    background: #ff3b30;
  }

  .bar {
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
    align-items: center;
    gap: 16px;
    margin-top: clamp(8px, 1.4vh, 14px);
  }
  .next {
    justify-self: start;
    max-width: 100%;
    min-width: 0;
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 4px 16px 4px 4px;
    border-radius: 18px;
    text-align: left;
    transition:
      background-color 200ms var(--ease-emph),
      border-radius var(--d-fast) var(--spring-fast);
  }
  .next:hover,
  .next.on {
    background: color-mix(in oklab, var(--ink) 10%, transparent);
  }
  .next:active {
    border-radius: 10px;
  }
  .next-cover {
    width: 52px;
    height: 52px;
    flex: none;
    border-radius: 14px;
    overflow: hidden;
    display: grid;
    place-items: center;
    box-shadow: 0 10px 20px -10px color-mix(in oklab, var(--shade) 60%, transparent);
  }
  .next-cover.empty {
    background: color-mix(in oklab, var(--ink) 12%, transparent);
    box-shadow: none;
  }
  .next-text {
    min-width: 0;
    display: flex;
    flex-direction: column;
    line-height: 1.2;
  }
  .kicker {
    font-size: 10px;
    font-weight: 800;
    letter-spacing: 0.16em;
    text-transform: uppercase;
  }
  .next-title {
    font-size: 16px;
    font-weight: 800;
  }
  .next-artist {
    font-size: 13px;
    font-weight: 600;
    color: color-mix(in oklab, var(--ink) 78%, var(--paper));
  }

  /* The controls: an ink capsule, the paper's colour on it. */
  .capsule {
    --on-surface: var(--paper);
    --on-surface-variant: var(--paper);
    --primary: var(--paper);
    --on-primary: var(--ink);
    padding: 6px;
    border-radius: 999px;
    background: var(--ink);
    color: var(--paper);
    box-shadow: 0 24px 40px -18px color-mix(in oklab, var(--shade) 75%, transparent);
  }
  /* The ends follow the capsule's round: a like or shuffle that is on squares only its inner
     corners, so the outer ones stay concentric with the capsule's. */
  .capsule :global(.xb:first-child) {
    border-radius: calc(var(--h) / 2) var(--r) var(--r) calc(var(--h) / 2);
  }
  .capsule :global(.xb:last-child) {
    border-radius: var(--r) calc(var(--h) / 2) calc(var(--h) / 2) var(--r);
  }
  .capsule :global(.xgroup) {
    --w: 52px;
    --h: 52px;
    --play-w: 76px;
    --play-h: 52px;
    gap: 4px;
  }

  .acts {
    justify-self: end;
    display: flex;
    gap: 8px;
  }
  .pb {
    width: 48px;
    height: 48px;
    flex: none;
    display: grid;
    place-items: center;
    border-radius: 24px;
    background: color-mix(in oklab, var(--ink) 12%, transparent);
    color: var(--ink);
    transition:
      border-radius var(--d-fast) var(--spring-fast),
      background-color 200ms var(--ease-emph),
      color 200ms var(--ease-emph),
      transform var(--d-fast) var(--spring-fast);
  }
  .pb:hover {
    background: color-mix(in oklab, var(--ink) 18%, transparent);
  }
  .pb:active {
    border-radius: 14px;
    transform: scale(0.94);
  }
  .pb.on {
    border-radius: 15px;
    background: var(--ink);
    color: var(--paper);
  }
  /* Volume in ink like the rest; its pill stands on a sheet of the paper. */
  .vol-host {
    display: contents;
    --on-surface: var(--ink);
    --on-surface-variant: var(--ink);
    --primary: var(--ink);
    --on-primary: var(--paper);
    --vb-pop: color-mix(in oklab, var(--paper) 90%, var(--ink));
  }
  .vol-host :global(.round) {
    background: color-mix(in oklab, var(--ink) 12%, transparent);
  }
  .vol-host :global(.round:hover) {
    background: color-mix(in oklab, var(--ink) 18%, transparent);
  }
  .vol-host :global(.round.on) {
    border-radius: 15px;
    background: var(--ink);
  }
  /* The track's menu: its button in ink like the rest; the menu itself keeps the page's colours. */
  .menu-host {
    display: contents;
  }
  .menu-host :global(.more) {
    --s: 48px;
    background: color-mix(in oklab, var(--ink) 12%, transparent);
    color: var(--ink);
  }
  .menu-host :global(.menu) {
    color: var(--on-surface);
  }

  @media (max-width: 1100px) {
    .mid {
      display: none;
    }
    .hide-s {
      display: none;
    }
  }
  @media (max-width: 820px) {
    .next-text {
      display: none;
    }
    .main {
      grid-template-columns: minmax(0, 1fr);
    }
    .art {
      display: none;
    }
  }
</style>
