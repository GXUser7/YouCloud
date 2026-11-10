<script lang="ts">
  // «Форма»: Material 3 Expressive at full stretch. The cover is cut in a nine-scalloped cookie
  // whose scallops creep round while the track plays — the picture itself stays still. Around it
  // the progress is a ring, wavy while it plays, dragged round to seek. «Играть» is a big sun that
  // turns while it plays and rounds off on pause; like, back and forward are shapes of the same
  // family. The line being sung stands under the title; «Далее» swaps it for the tracks to come,
  // each in a shape of its own, the last few played before them. Behind, a few shapes drift in
  // the cover's colours — or, with «Волна в плеере» on, the wave moves there instead.
  import { tr } from '../../lib/i18n.svelte'
  import { serviceName, track } from '../../lib/catalog'
  import { fit } from '../../lib/fit'
  import { time } from '../../lib/format'
  import { clipPolygon, shapePath, type ShapeName } from '../../lib/shapes'
  import {
    app,
    currentTrack,
    cycleRepeat,
    dislike,
    go,
    isLiked,
    jump,
    like,
    next,
    player,
    prev,
    remember,
    seek,
    startRadio,
    stored,
    toggle,
    toggleShuffle,
  } from '../../lib/state.svelte'
  import Cover from '../Cover.svelte'
  import Icon from '../Icon.svelte'
  import LoadingIndicator from '../LoadingIndicator.svelte'
  import Lyrics from '../Lyrics.svelte'
  import PlayerWave from '../PlayerWave.svelte'
  import ServiceGlyph from '../ServiceGlyph.svelte'
  import TrackMenu from '../TrackMenu.svelte'
  import VolumeButton from '../VolumeButton.svelte'

  let { onclose }: { onclose: () => void } = $props()

  const t = $derived(currentTrack()!)
  const duration = $derived(player.duration || t.duration || 1)
  const upNext = $derived(player.queue[player.index + 1] ? track(player.queue[player.index + 1]) : null)
  const upcoming = $derived(player.queue.slice(player.index + 1, player.index + 7))
  // The last three played, before what comes: picked again from here, not only by «Предыдущий».
  const played = $derived.by(() => {
    const from = Math.max(0, player.index - 3)
    return player.queue.slice(from, player.index).map((id, i) => ({ id, at: from + i }))
  })
  const liked = $derived(isLiked(t.id))

  let side = $state<'lyrics' | 'queue'>(stored<string>('shapeSide', 'lyrics') === 'queue' ? 'queue' : 'lyrics')
  function show(view: 'lyrics' | 'queue') {
    side = view
    remember('shapeSide', view)
  }

  const COOKIE = clipPolygon('cookie9')
  const SUN = shapePath('sunny')
  const ROUND = shapePath('circle', 0.9)
  const QUEUE_SHAPES: ShapeName[] = ['cookie9', 'sunny', 'flower', 'clover4', 'pentagon', 'cookie12']
  const QUEUE_CLIPS = QUEUE_SHAPES.map(clipPolygon)
  const NEXT_CLIP = clipPolygon('softBurst')
  const DECOR = (['sunny', 'softBurst', 'clover4', 'cookie6'] as ShapeName[]).map(clipPolygon)

  // The ring, in a box 1000 across: the part played a wave going round, the rest a thin track.
  const C = 500
  const R = 411
  const LENGTH = 2 * Math.PI * R
  function ring(amplitude: number): string {
    let d = ''
    for (let i = 0; i <= 360; i++) {
      const a = (i / 360) * Math.PI * 2
      const r = R + amplitude * Math.sin(a * 44)
      d += `${i ? 'L' : 'M'}${(C + r * Math.cos(a)).toFixed(1)} ${(C + r * Math.sin(a)).toFixed(1)}`
    }
    return d
  }
  const WAVY = ring(8)
  const FLAT = ring(0)

  let scrub = $state<number | null>(null)
  const fraction = $derived(Math.max(0, Math.min(1, (scrub ?? player.position) / duration)))
  const handle = $derived.by(() => {
    const a = fraction * Math.PI * 2
    return { x: C + R * Math.sin(a), y: C - R * Math.cos(a), deg: fraction * 360 }
  })

  let dial = $state<HTMLDivElement>()
  function at(e: PointerEvent) {
    const r = dial!.getBoundingClientRect()
    let a = Math.atan2(e.clientX - (r.left + r.width / 2), -(e.clientY - (r.top + r.height / 2)))
    if (a < 0) a += Math.PI * 2
    return a / (Math.PI * 2)
  }
  function down(e: PointerEvent) {
    if (player.live) return
    ;(e.currentTarget as Element).setPointerCapture(e.pointerId)
    scrub = at(e) * duration
  }
  function move(e: PointerEvent) {
    if (scrub !== null) scrub = at(e) * duration
  }
  function up() {
    if (scrub === null) return
    seek(scrub)
    scrub = null
  }
  function keys(e: KeyboardEvent) {
    if (player.live) return
    const step = { ArrowRight: 5, ArrowUp: 5, ArrowLeft: -5, ArrowDown: -5 }[e.key]
    if (step) seek(Math.max(0, Math.min(duration, player.position + step)))
    else if (e.key === 'Home') seek(0)
    else return
    e.preventDefault()
  }

  let burst = $state(0)
  function heart() {
    if (!liked) burst++
    like(t.id)
  }

  function leave(path: string) {
    onclose()
    go(path)
  }
</script>

<div class="shape" class:playing={player.playing} role="dialog" aria-label={tr('Сейчас играет')}>
  {#if app.playerWave}
    <div class="bg"><PlayerWave around={dial} /></div>
  {:else}
    <div class="decor" aria-hidden="true">
      {#each DECOR as clip, i (i)}<span class="f f{i}" style:clip-path={clip}></span>{/each}
    </div>
  {/if}

  <div class="top">
    <span class="overline">{player.wave ? tr('Моя волна') : tr('Играет из')}</span>
    <span class="ctx ellipsis">{player.wave ? tr('Яндекс Музыка') : player.context || serviceName(t.service)}</span>
  </div>

  <div class="main">
    <div class="dial-col">
      <div
        class="dial"
        bind:this={dial}
        role="slider"
        tabindex="0"
        aria-label={tr('Перемотка')}
        aria-valuemin={0}
        aria-valuemax={Math.round(duration)}
        aria-valuenow={Math.round(scrub ?? player.position)}
        aria-valuetext={time(scrub ?? player.position)}
        onkeydown={keys}
      >
        <div class="glow"></div>
        {#key t.id}
          <div class="cookie" style:clip-path={COOKIE}>
            <div class="still"><Cover seed={t.id} color={t.color} src={t.cover} grain /></div>
          </div>
        {/key}
        <svg class="ring" viewBox="0 0 1000 1000" aria-hidden="true">
          <defs>
            <mask id="shape-played">
              <circle cx={C} cy={C} r={R} fill="none" stroke="#fff" stroke-width="46" stroke-dasharray="{LENGTH * fraction} {LENGTH}" transform="rotate(-90 {C} {C})" />
            </mask>
          </defs>
          <circle class="track" cx={C} cy={C} r={R} />
          {#if !player.live}
            <g mask="url(#shape-played)"><path class="wave" style:d="path('{player.playing && scrub === null ? WAVY : FLAT}')" /></g>
            <rect class="knob" class:held={scrub !== null} x={handle.x - 5} y={handle.y - 22} width="10" height="44" rx="5" transform="rotate({handle.deg} {handle.x} {handle.y})" />
            <!-- The ring is dragged with a pointer; the keys work on the dial, which is the slider. -->
            <!-- svelte-ignore a11y_no_static_element_interactions -->
            <circle class="hit" cx={C} cy={C} r={R} onpointerdown={down} onpointermove={move} onpointerup={up} onpointercancel={up} />
          {/if}
        </svg>
      </div>
      {#if player.live}
        <div class="times live"><span class="dot"></span>{tr('В эфире')}</div>
      {:else}
        <div class="times"><b>{time(scrub ?? player.position)}</b> · {time(duration)}</div>
      {/if}
    </div>

    <div class="info">
      <div class="chips">
        <span class="chip service"><ServiceGlyph service={t.service} size={16} />{serviceName(t.service)}</span>
        {#if t.album}
          <button class="chip" onclick={() => leave('set/' + t.album!.id)}><Icon name="album" size={18} /><span class="ellipsis album">{t.album.title || tr('Альбом')}</span></button>
        {/if}
      </div>
      {#key t.id}
        <h1 class="title" title={t.title} use:fit={{ text: t.title, max: 100, min: 14, height: () => Math.max(48, Math.min(innerWidth / 1280, innerHeight / 720) * 114) }}>{t.title}</h1>
      {/key}
      <div class="artists">
        {#each t.artists as a, i (a.id + i)}
          <button onclick={() => leave('artist/' + a.id)}>{a.name}{i < t.artists.length - 1 ? ',' : ''}</button>
        {/each}
      </div>

      <div class="slot">
        {#if side === 'lyrics'}
          <span class="kicker singing">♪ {tr('Сейчас играет')}</span>
          <div class="words"><Lyrics size="l" /></div>
        {:else}
          <div class="shapes">
            {#each played as p (p.at + ':' + p.id)}
              {@const q = track(p.id)}
              <button class="shp past" onclick={() => jump(p.at)} title="{q.title} — {q.artists.map((a) => a.name).join(', ')}" aria-label={tr('Включить: %s', q.title)}>
                <span class="shp-art" style:clip-path={QUEUE_CLIPS[(p.at + 5) % QUEUE_CLIPS.length]}><Cover seed={q.id} color={q.color} src={q.cover} /></span>
              </button>
            {/each}
            {#if played.length && upcoming.length}<span class="here" aria-hidden="true"></span>{/if}
            {#each upcoming as id, i (player.index + 1 + i + ':' + id)}
              {@const q = track(id)}
              <button class="shp" onclick={() => jump(player.index + 1 + i)} title="{q.title} — {q.artists.map((a) => a.name).join(', ')}" aria-label={tr('Включить: %s', q.title)}>
                <span class="shp-art" style:clip-path={QUEUE_CLIPS[i % QUEUE_CLIPS.length]}><Cover seed={q.id} color={q.color} src={q.cover} /></span>
              </button>
            {:else}
              <p class="muted">{player.radio ? tr('Радио подбирает следующие треки…') : tr('Дальше ничего нет — включи радио по треку.')}</p>
            {/each}
          </div>
        {/if}
      </div>

      <div class="ctrls" role="group" aria-label={tr('Управление')}>
        <button class="sb like" class:on={liked} onclick={heart} aria-pressed={liked} aria-label={tr('Нравится')} title={liked ? tr('Убрать из любимого') : tr('Нравится')}>
          {#key burst}<span class="ic" class:pop={burst > 0}><Icon name="favorite" fill={liked} size={28} /></span>{/key}
        </button>
        <button class="sb pill back" onclick={prev} aria-label={tr('Предыдущий')} title={tr('Предыдущий')}><span class="ic"><Icon name="skip_previous" fill size={32} /></span></button>
        <button class="sun" onclick={toggle} aria-label={player.playing ? tr('Пауза') : tr('Играть')}>
          <svg viewBox="0 0 100 100" aria-hidden="true"><path style:d="path('{player.playing ? SUN : ROUND}')" /></svg>
          {#if player.loading}
            <span class="ic"><LoadingIndicator size={54} contained={false} /></span>
          {:else}
            {#key player.playing}<span class="ic swap"><Icon name={player.playing ? 'pause' : 'play_arrow'} fill size={50} /></span>{/key}
          {/if}
        </button>
        <button class="sb pill fwd" onclick={() => next()} aria-label={tr('Следующий')} title={tr('Следующий')}><span class="ic"><Icon name="skip_next" fill size={32} /></span></button>
        {#if player.radio}
          <button class="sb round" onclick={dislike} aria-label={tr('Не нравится')} title={tr('Не нравится')}><Icon name="heart_broken" size={26} /></button>
        {:else}
          <button class="sb round" class:on={player.shuffle} onclick={toggleShuffle} aria-pressed={player.shuffle} aria-label={tr('Перемешать')} title={tr('Перемешать')}><Icon name="shuffle" size={26} /></button>
        {/if}
      </div>
    </div>
  </div>

  <div class="bar">
    <button class="icon-btn l glassy" onclick={onclose} aria-label={tr('Свернуть')} title={tr('Свернуть')}><Icon name="keyboard_arrow_down" size={30} /></button>
    <div class="footer-right">
      <button class="next-card" class:on={side === 'queue'} onclick={() => show(side === 'queue' ? 'lyrics' : 'queue')} aria-pressed={side === 'queue'} title={side === 'queue' ? tr('Скрыть очередь') : tr('Показать очередь')}>
        {#if upNext}
          {#key upNext.id}<span class="next-art" style:clip-path={NEXT_CLIP}><Cover seed={upNext.id} color={upNext.color} src={upNext.cover} /></span>{/key}
          <span class="next-text"><span class="kicker">{tr('Далее')}</span><span class="ellipsis next-title">{upNext.title}</span></span>
        {:else}
          <span class="next-art empty" style:clip-path={NEXT_CLIP}><Icon name="radio" size={22} /></span>
          <span class="next-text"><span class="kicker">{tr('Далее')}</span><span class="ellipsis next-title">{player.radio ? tr('Радио подбирает треки…') : tr('Очередь пуста')}</span></span>
        {/if}
      </button>
      <div class="pills">
        <button class="pill-btn" class:on={side === 'lyrics'} onclick={() => show('lyrics')} aria-pressed={side === 'lyrics'} aria-label={tr('Текст')} title={tr('Текст')}><Icon name="lyrics" fill={side === 'lyrics'} size={20} /><span class="hide-s">{tr('Текст')}</span></button>
        <button class="pill-btn round" class:on={side === 'queue'} onclick={() => show('queue')} aria-pressed={side === 'queue'} aria-label={tr('Очередь')} title={tr('Очередь')}><Icon name="queue_music" size={20} /></button>
        <button class="pill-btn round" onclick={() => startRadio(t.id)} aria-label={tr('Радио по треку')} title={tr('Радио по треку')}><Icon name="radio" size={20} /></button>
        <button
          class="pill-btn round"
          class:on={player.repeat !== 'off'}
          onclick={cycleRepeat}
          aria-pressed={player.repeat !== 'off'}
          aria-label={tr('Повтор')}
          title={player.repeat === 'one' ? tr('Повтор трека') : player.repeat === 'all' ? tr('Повтор очереди') : tr('Повтор')}
        >
          <Icon name={player.repeat === 'one' ? 'repeat_one' : 'repeat'} size={20} />
        </button>
        <VolumeButton align="end" size={20} />
        <TrackMenu id={t.id} large onleave={onclose} />
      </div>
    </div>
  </div>
</div>

<style>
  .shape {
    position: fixed;
    inset: 0;
    z-index: 20;
    overflow: hidden;
    /* One artboard pixel, scaled by whichever side of the window has less room. */
    --unit: min(0.078125vw, 0.138889dvh);
    display: grid;
    grid-template-rows: calc(64 * var(--unit)) minmax(0, 1fr) calc(100 * var(--unit));
    background:
      radial-gradient(circle at 26% 52%, color-mix(in oklab, var(--primary-container) 85%, transparent), transparent 55%),
      radial-gradient(circle at 90% 6%, color-mix(in oklab, var(--primary-container) 55%, transparent), transparent 40%),
      var(--surface);
    animation: up 420ms var(--ease-in-place);
  }
  @keyframes up {
    from {
      transform: translateY(28px);
      opacity: 0;
    }
  }
  .shape > :not(.decor, .bg) {
    position: relative;
  }

  /* Shapes drifting behind, lit from one side, the far ones softer. */
  .decor {
    position: absolute;
    inset: 0;
    pointer-events: none;
  }
  .f {
    position: absolute;
    background: radial-gradient(
      circle at 34% 28%,
      color-mix(in oklab, var(--primary), #fff 8%),
      var(--primary-container) 58%,
      color-mix(in oklab, var(--primary-container), #000 35%)
    );
  }
  .f0 {
    width: 190px;
    height: 190px;
    right: -40px;
    top: -50px;
    opacity: 0.45;
    filter: blur(6px);
    animation: drift-a 13s ease-in-out infinite;
  }
  .f1 {
    width: 240px;
    height: 240px;
    left: -80px;
    bottom: -90px;
    opacity: 0.6;
    filter: blur(2px);
    animation: drift-b 11s ease-in-out infinite;
  }
  .f2 {
    width: 110px;
    height: 110px;
    right: -36px;
    top: 58%;
    opacity: 0.45;
    filter: blur(3px);
    animation: drift-a 15s ease-in-out infinite reverse;
  }
  .f3 {
    width: 80px;
    height: 80px;
    left: 38%;
    bottom: -26px;
    opacity: 0.35;
    filter: blur(5px);
    animation: drift-b 17s ease-in-out infinite;
  }
  @keyframes drift-a {
    50% {
      transform: translate(8px, -18px) rotate(10deg);
    }
  }
  @keyframes drift-b {
    50% {
      transform: translate(-12px, 14px) rotate(-12deg);
    }
  }

  .top {
    display: flex;
    flex-direction: column;
    align-items: center;
    padding-top: calc(16 * var(--unit));
  }
  .top .overline {
    font-size: 11px;
  }
  .ctx {
    font-weight: 700;
    font-size: 15px;
    max-width: 50vw;
  }

  .main {
    min-height: 0;
    display: grid;
    grid-template-columns: minmax(0, 540fr) minmax(0, 596fr);
    align-items: start;
    gap: calc(40 * var(--unit));
    padding: calc(16 * var(--unit)) calc(44 * var(--unit)) 0 calc(60 * var(--unit));
  }

  /* The dial: the cookie, its glow, the ring. */
  .dial-col {
    display: flex;
    flex-direction: column;
    align-items: center;
    min-width: 0;
  }
  .dial {
    position: relative;
    width: min(100%, calc(540 * var(--unit)));
    aspect-ratio: 1;
    border-radius: 50%;
  }
  .glow {
    position: absolute;
    inset: 6%;
    border-radius: 50%;
    background: radial-gradient(circle, color-mix(in oklab, var(--primary) 70%, transparent) 40%, transparent 72%);
    filter: blur(28px);
  }
  .cookie,
  .still {
    position: absolute;
    animation: turn 64s linear infinite;
    animation-play-state: paused;
  }
  .cookie {
    inset: 16%;
    animation-name: art-in, turn;
    animation-duration: 520ms, 64s;
    animation-timing-function: var(--ease-in-place), linear;
    animation-iteration-count: 1, infinite;
    animation-play-state: running, paused;
  }
  .still {
    inset: 0;
    animation-direction: reverse;
  }
  .playing .cookie {
    animation-play-state: running, running;
  }
  .playing .still {
    animation-play-state: running;
  }
  @keyframes turn {
    to {
      rotate: 360deg;
    }
  }
  @keyframes art-in {
    from {
      scale: 0.9;
      opacity: 0;
    }
  }
  .ring {
    position: absolute;
    inset: 0;
    width: 100%;
    height: 100%;
    overflow: visible;
  }
  .track {
    fill: none;
    stroke: color-mix(in oklab, var(--on-surface) 16%, transparent);
    stroke-width: 8;
  }
  .wave {
    fill: none;
    stroke: var(--primary);
    stroke-width: 11;
    stroke-linecap: round;
    transform-box: view-box;
    transform-origin: 500px 500px;
    transition: d 600ms var(--spring);
    animation: turn 30s linear infinite;
    animation-play-state: paused;
  }
  .playing .wave {
    animation-play-state: running;
  }
  .knob {
    fill: var(--primary);
    transition: height 200ms var(--spring-fast);
  }
  .hit {
    fill: none;
    stroke: transparent;
    stroke-width: 90;
    pointer-events: stroke;
    cursor: pointer;
    touch-action: none;
  }
  .dial:focus-visible {
    outline-offset: 4px;
  }
  .times {
    margin-top: calc(-28 * var(--unit));
    font-size: clamp(12px, calc(13 * var(--unit)), 18px);
    font-variant-numeric: tabular-nums;
    color: var(--on-surface-variant);
  }
  .times b {
    color: var(--on-surface);
    font-weight: 700;
  }
  .times.live {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 15px;
    font-weight: 600;
    color: var(--on-surface);
  }
  .dot {
    width: 10px;
    height: 10px;
    border-radius: 50%;
    background: #ff3b30;
  }

  /* The words side. */
  .info {
    min-width: 0;
    min-height: 0;
    height: calc(498 * var(--unit));
    margin-top: calc(12 * var(--unit));
    display: grid;
    grid-template-rows: auto auto auto minmax(0, 1fr) auto;
  }
  .chips {
    display: flex;
    min-width: 0;
    gap: 8px;
  }
  .chips .chip {
    min-width: 0;
    height: clamp(28px, calc(30 * var(--unit)), 40px);
    font-size: clamp(12px, calc(13 * var(--unit)), 18px);
  }
  .service {
    color: var(--primary);
  }
  .album {
    max-width: 240px;
  }
  .title {
    width: 100%;
    min-height: 0;
    margin: calc(18 * var(--unit)) 0 0;
    font-family: var(--font-display);
    font-weight: 900;
    line-height: 1.12;
    letter-spacing: -0.04em;
    text-wrap: balance;
    overflow-wrap: anywhere;
    animation: title-in 380ms var(--ease-in-place);
  }
  @keyframes title-in {
    from {
      opacity: 0;
      transform: translateY(10px);
    }
  }
  .artists {
    display: flex;
    gap: 0 0.3em;
    min-width: 0;
    max-height: 1.4em;
    overflow: hidden;
    margin-top: calc(10 * var(--unit));
    font-size: clamp(18px, calc(28 * var(--unit)), 40px);
    font-weight: 800;
    color: var(--on-surface-variant);
  }
  .artists button {
    border-radius: 8px;
    transition: color 150ms;
  }
  .artists button:hover {
    color: var(--on-surface);
  }
  .slot {
    min-width: 0;
    min-height: 0;
    overflow: hidden;
    display: flex;
    flex-direction: column;
    margin: calc(30 * var(--unit)) 0 calc(24 * var(--unit));
  }
  .singing {
    flex: none;
    color: var(--primary);
    margin-bottom: 4px;
  }
  .words {
    flex: 1;
    min-height: 0;
    overflow: hidden;
  }
  .slot :global(.state) {
    place-items: center start;
    text-align: left;
    padding: 0 8px;
  }
  .slot :global(.l .line) {
    font-family: var(--font-body);
    font-weight: 800;
    font-size: clamp(16px, calc(20 * var(--unit)), 28px);
    padding: 3px 8px;
  }
  .slot :global(.l .line.now) {
    font-size: clamp(18px, calc(26 * var(--unit)), 36px);
    color: var(--on-surface);
    transform: none;
  }
  .shapes {
    min-height: 0;
    height: 100%;
    overflow-y: auto;
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 14px;
    padding: 6px;
  }
  .shp {
    width: clamp(56px, 9vh, 84px);
    aspect-ratio: 1;
    border-radius: 50%;
    transition: transform var(--d-fast) var(--spring-fast);
  }
  .shp:hover {
    transform: scale(1.08) rotate(-6deg);
  }
  .shp:active {
    transform: scale(0.94);
  }
  /* Played already: quieter, and set apart from what comes by a dot where the track playing is. */
  .shp.past {
    opacity: 0.5;
  }
  .shp.past:hover,
  .shp.past:focus-visible {
    opacity: 1;
  }
  .here {
    width: 8px;
    height: 8px;
    flex: none;
    border-radius: 50%;
    background: var(--primary);
  }
  .bg {
    position: absolute;
    inset: 0;
  }
  .shp-art {
    display: block;
    width: 100%;
    height: 100%;
  }

  /* The controls: a family of shapes. */
  .ctrls {
    display: flex;
    align-items: center;
    gap: calc(14 * var(--unit));
    height: calc(120 * var(--unit));
  }
  .sb {
    display: grid;
    place-items: center;
    flex: none;
    background: color-mix(in oklab, var(--on-surface) 10%, transparent);
    color: var(--on-surface);
    transition:
      border-radius var(--d-fast) var(--spring-fast),
      background-color 250ms var(--ease-emph),
      color 250ms var(--ease-emph),
      transform var(--d-fast) var(--spring-fast);
  }
  .sb:hover {
    background: color-mix(in oklab, var(--on-surface) 15%, transparent);
  }
  .sb:active {
    transform: scale(0.93);
  }
  .sb .ic {
    display: grid;
    transition: transform var(--d-fast) var(--spring-fast);
  }
  .like {
    width: calc(64 * var(--unit));
    height: calc(64 * var(--unit));
    border-radius: calc(24 * var(--unit));
  }
  .like.on {
    background: color-mix(in oklab, var(--primary) 26%, transparent);
    color: var(--primary);
  }
  .like:active {
    border-radius: 32px;
  }
  .pill {
    width: calc(80 * var(--unit));
    height: calc(58 * var(--unit));
    border-radius: calc(29 * var(--unit));
  }
  .pill:active {
    border-radius: 16px;
  }
  .back:hover .ic {
    transform: translateX(-3px);
  }
  .fwd:hover .ic {
    transform: translateX(3px);
  }
  .round {
    width: calc(58 * var(--unit));
    height: calc(58 * var(--unit));
    border-radius: 50%;
  }
  .round.on {
    border-radius: 20px;
    background: var(--primary);
    color: var(--on-primary);
  }
  .sun {
    position: relative;
    width: calc(120 * var(--unit));
    height: calc(120 * var(--unit));
    flex: none;
    display: grid;
    place-items: center;
    color: var(--on-surface);
    transition:
      transform var(--d-fast) var(--spring-fast),
      color 250ms var(--ease-emph);
  }
  /* Paused, the sun is a quiet round in the other buttons' tone; playing, it turns, lit. */
  .playing .sun {
    color: var(--on-primary);
  }
  .sun:active {
    transform: scale(0.92);
  }
  .sun > * {
    grid-area: 1 / 1;
  }
  .sun svg {
    width: 100%;
    height: 100%;
    overflow: visible;
    animation: turn 14s linear infinite;
    animation-play-state: paused;
  }
  .playing .sun svg {
    filter: drop-shadow(0 14px 22px color-mix(in oklab, var(--primary) 50%, transparent));
    animation-play-state: running;
  }
  .sun path {
    fill: color-mix(in oklab, var(--on-surface) 12%, transparent);
    transition:
      d 600ms var(--spring),
      fill 250ms var(--ease-emph);
  }
  .playing .sun path {
    fill: var(--primary);
  }
  .sun :global(.loader path) {
    fill: currentColor;
  }
  .sun .ic {
    position: relative;
    display: grid;
  }

  .bar {
    display: grid;
    grid-template-columns: minmax(0, 540fr) minmax(0, 596fr);
    align-items: center;
    gap: calc(40 * var(--unit));
    padding: 0 calc(44 * var(--unit)) calc(14 * var(--unit)) calc(60 * var(--unit));
  }
  .bar > .icon-btn {
    margin-left: calc(-36 * var(--unit));
  }
  .footer-right {
    display: flex;
    align-items: center;
    gap: 12px;
    min-width: 0;
  }
  .next-card {
    flex: 1;
    max-width: 360px;
    min-width: 0;
    height: 60px;
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 0 18px 0 4px;
    border-radius: 30px;
    text-align: left;
    background: color-mix(in oklab, var(--on-surface) 8%, transparent);
    transition:
      background-color 250ms var(--ease-emph),
      color 250ms var(--ease-emph),
      border-radius var(--d-fast) var(--spring-fast);
  }
  .next-card:hover {
    background: color-mix(in oklab, var(--on-surface) 13%, transparent);
  }
  .next-card:active {
    border-radius: 16px;
  }
  .next-card.on {
    background: var(--primary);
    color: var(--on-primary);
  }
  .next-art {
    width: 52px;
    height: 52px;
    flex: none;
    display: grid;
    place-items: center;
    animation: art-in 420ms var(--ease-in-place);
  }
  .next-art.empty {
    background: color-mix(in oklab, var(--on-surface) 12%, transparent);
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
    opacity: 0.8;
  }
  .next-title {
    font-size: 15px;
    font-weight: 800;
  }
  .pills {
    display: flex;
    flex: none;
    align-items: center;
    gap: 8px;
    margin-left: auto;
  }
  .pill-btn {
    height: 44px;
    min-width: 44px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    padding: 0 18px 0 14px;
    border-radius: 24px;
    font-size: 14px;
    font-weight: 800;
    background: color-mix(in oklab, var(--on-surface) 10%, transparent);
    color: var(--on-surface);
    transition:
      border-radius var(--d-fast) var(--spring-fast),
      background-color 250ms var(--ease-emph),
      color 250ms var(--ease-emph);
  }
  .pill-btn.round {
    width: 44px;
    padding: 0;
  }
  .pill-btn:hover {
    background: color-mix(in oklab, var(--on-surface) 15%, transparent);
  }
  .pill-btn:active {
    border-radius: 14px;
  }
  .pill-btn.on {
    border-radius: 16px;
    background: var(--primary);
    color: var(--on-primary);
  }

  @media (max-width: 1100px) {
    .hide-s {
      display: none;
    }
    .pill-btn {
      padding: 0 12px;
    }
  }
  @media (max-width: 900px) {
    .shape {
      --unit: min(1px, 0.15dvh, calc((100vw - 40px) / 458));
      grid-template-rows: 56px minmax(0, 1fr) auto;
    }
    .main {
      grid-template-columns: minmax(0, 1fr);
      overflow-y: auto;
      gap: 24px;
      padding: 12px 20px 24px;
    }
    .dial {
      width: min(46dvh, 76vw);
    }
    .info {
      height: auto;
      min-height: calc(430 * var(--unit));
      margin: 0;
      grid-template-rows: auto auto auto minmax(100px, 1fr) auto;
    }
    .ctrls {
      justify-content: center;
    }
    .bar {
      grid-template-columns: auto minmax(0, 1fr);
      gap: 12px;
      padding: 10px 16px;
    }
    .bar > .icon-btn {
      margin: 0;
    }
    .footer-right {
      justify-content: flex-end;
    }
    .next-card {
      height: 48px;
    }
    .next-art {
      width: 40px;
      height: 40px;
    }
  }
  @media (max-width: 600px) {
    .footer-right {
      display: contents;
    }
    .next-card {
      max-width: none;
    }
    .pills {
      grid-column: 1 / -1;
      flex-wrap: wrap;
      justify-content: flex-end;
      gap: 4px;
    }
  }
  @media (max-height: 650px) and (min-width: 901px) {
    .shape { overflow-y: auto; grid-template-rows: 56px minmax(400px, 1fr) auto; --unit: 0.85px; }
    .main { padding-bottom: 24px; }
  }
</style>
