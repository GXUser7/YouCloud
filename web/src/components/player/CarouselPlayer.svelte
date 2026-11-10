<script lang="ts">
  // «Карусель»: the queue is the stage. The covers stand in a row, the one playing large in the
  // middle and its neighbours smaller and further back either side — what was, what comes. A
  // neighbour clicked plays; the row slides on springs, by transforms alone, and the page takes
  // the new cover's colours. Under it the title, the wavy progress and the controls; «Текст»
  // moves the stage left and slides the words in on glass at the right.
  import { tr } from '../../lib/i18n.svelte'
  import { serviceName, track } from '../../lib/catalog'
  import { fit } from '../../lib/fit'
  import { time } from '../../lib/format'
  import { app, currentTrack, cycleRepeat, go, jump, player, remember, seek, startRadio, stored, toggle } from '../../lib/state.svelte'
  import Cover from '../Cover.svelte'
  import Icon from '../Icon.svelte'
  import Lyrics from '../Lyrics.svelte'
  import PlayerControls from '../PlayerControls.svelte'
  import PlayerWave from '../PlayerWave.svelte'
  import TrackMenu from '../TrackMenu.svelte'
  import VolumeButton from '../VolumeButton.svelte'
  import WavyProgress from '../WavyProgress.svelte'

  let { onclose }: { onclose: () => void } = $props()

  const t = $derived(currentTrack()!)
  const duration = $derived(player.duration || t.duration || 1)

  let lyrics = $state(stored('carouselLyrics', false))
  function flipLyrics() {
    lyrics = !lyrics
    remember('carouselLyrics', lyrics)
  }

  let w = $state(0)
  let h = $state(0)
  let anchor = $state<HTMLElement>()
  const wide = $derived(w > 900)
  const aside = $derived(lyrics && wide)
  const size = $derived(Math.max(120, Math.min(h * 0.94, w * (aside ? 0.3 : 0.34), 560)))
  const cx = $derived(aside ? w * 0.3 : w / 2)
  const shift = $derived(aside ? cx - w / 2 : 0)

  // The covers either side of the one playing, three each way: the third stands invisible,
  // ready to slide in.
  type Slot = { key: string; i: number; d: number; id: string }
  const near = $derived.by(() => {
    const out: Slot[] = []
    for (let d = -3; d <= 3; d++) {
      const i = player.index + d
      if (i < 0 || i >= player.queue.length) continue
      out.push({ key: `${i}:${player.queue[i]}`, i, d, id: player.queue[i] })
    }
    return out
  })

  const SCALE = [1, 0.6, 0.4, 0.28]
  const SEEN = [1, 0.72, 0.38, 0]

  function place(d: number) {
    const a = Math.min(3, Math.abs(d))
    const gap = size * 0.09
    let off = 0
    for (let s = 1; s <= a; s++) off += (size * SCALE[s - 1]) / 2 + gap * (1 - s * 0.2) + (size * SCALE[s]) / 2
    const x = cx + Math.sign(d) * off - size / 2
    const y = (h - size) / 2
    // With the words open, what comes is behind their glass.
    const seen = aside && d > 0 ? 0 : SEEN[a]
    return { transform: `translate(${x}px, ${y}px) scale(${SCALE[a]})`, seen, radius: a === 0 ? 30 : 22 / SCALE[a], z: 10 - a }
  }

  function leave(path: string) {
    onclose()
    go(path)
  }
</script>

<div class="carousel" role="dialog" aria-label={tr('Сейчас играет')}>
  {#if app.playerWave}<PlayerWave around={anchor} />{/if}

  <div class="top">
    <span class="overline">{player.wave ? tr('Моя волна') : tr('Играет из')} · {player.index + 1} / {player.queue.length}</span>
    <span class="ctx ellipsis">{player.wave ? tr('Яндекс Музыка') : player.context || serviceName(t.service)}</span>
  </div>

  <div class="stage" bind:clientWidth={w} bind:clientHeight={h}>
    <div class="glow" style:transform="translate({cx - size}px, {h / 2 - size}px)" style:width="{size * 2}px" style:height="{size * 2}px"></div>
    <div class="anchor" bind:this={anchor} style:transform="translate({cx - size / 2}px, {(h - size) / 2}px)" style:width="{size}px" style:height="{size}px"></div>
    {#each near as s (s.key)}
      {@const p = place(s.d)}
      {@const q = track(s.id)}
      <button
        class="cv"
        class:now={s.d === 0}
        style:width="{size}px"
        style:height="{size}px"
        style:transform={p.transform}
        style:--seen={p.seen}
        style:border-radius="{p.radius}px"
        style:z-index={p.z}
        style:pointer-events={p.seen ? 'auto' : 'none'}
        tabindex={p.seen ? 0 : -1}
        onclick={() => (s.d === 0 ? toggle() : jump(s.i))}
        aria-label={s.d === 0 ? (player.playing ? tr('Пауза') : tr('Играть')) : tr('Включить: %s', q.title)}
        title={s.d === 0 ? undefined : q.title}
      >
        <Cover seed={q.id} color={q.color} src={q.cover} grain={s.d === 0} />
      </button>
    {/each}
  </div>

  <div class="lower" class:aside style:transform="translateX({shift}px)">
    {#key t.id}
      <h1 class="title display" title={t.title} use:fit={{ text: t.title, max: 68, min: 12, height: () => 116 }}>{t.title}</h1>
    {/key}
    <div class="meta">
      {#each t.artists as a, i (a.id + i)}
        <button class="chip" onclick={() => leave('artist/' + a.id)}>{a.name}</button>
      {/each}
      {#if t.album}
        <button class="chip" onclick={() => leave('set/' + t.album!.id)}><Icon name="album" size={18} /><span class="ellipsis album">{t.album.title || tr('Альбом')}</span></button>
      {/if}
    </div>
    {#if player.live}
      <div class="seek live"><span class="dot"></span>{tr('В эфире')}</div>
    {:else}
      <div class="seek">
        <span>{time(player.position)}</span>
        <div class="wavy">
          <WavyProgress value={player.position / duration} playing={player.playing} {duration} onseek={(f) => seek(f * duration)} />
        </div>
        <span>{time(duration)}</span>
      </div>
    {/if}
  </div>

  <div class="bar">
    <button class="icon-btn l glassy" onclick={onclose} aria-label={tr('Свернуть')} title={tr('Свернуть')}><Icon name="keyboard_arrow_down" size={30} /></button>
    <div class="mid" style:transform="translateX({shift}px)">
      <PlayerControls size="xl" tonal items={['like', 'prev', 'play', 'next', 'shuffle']} />
    </div>
    <div class="acts">
      <div class="xgroup l">
        <button class="xb toggle tonal" class:on={lyrics} onclick={flipLyrics} aria-pressed={lyrics} aria-label={tr('Текст')} title={lyrics ? tr('Скрыть текст') : tr('Показать текст')}>
          <span class="ic"><Icon name="lyrics" fill={lyrics} size={24} /></span>
        </button>
        <button class="xb tonal" onclick={() => startRadio(t.id)} aria-label={tr('Радио по треку')} title={tr('Радио по треку')}>
          <span class="ic"><Icon name="radio" size={24} /></span>
        </button>
        <button
          class="xb toggle tonal"
          class:on={player.repeat !== 'off'}
          onclick={cycleRepeat}
          aria-label={tr('Повтор')}
          aria-pressed={player.repeat !== 'off'}
          title={player.repeat === 'one' ? tr('Повтор трека') : player.repeat === 'all' ? tr('Повтор очереди') : tr('Повтор')}
        >
          {#key player.repeat === 'one'}<span class="ic swap"><Icon name={player.repeat === 'one' ? 'repeat_one' : 'repeat'} size={24} /></span>{/key}
        </button>
        <VolumeButton xb align="end" />
      </div>
      <TrackMenu id={t.id} large onleave={onclose} />
    </div>
  </div>

  {#if lyrics}
    <div class="panel" class:full={!wide}>
      <Lyrics size="l" />
    </div>
  {/if}
</div>

<style>
  .carousel {
    --r-m: 14px;
    --r-l: 16px;
    --r-xl: 20px;
    --r-2xl: 24px;
    position: fixed;
    inset: 0;
    z-index: 20;
    overflow: hidden;
    display: flex;
    flex-direction: column;
    background: var(--surface);
    animation: up 420ms var(--ease-in-place);
  }
  @keyframes up {
    from {
      transform: translateY(28px);
      opacity: 0;
    }
  }

  .top {
    position: relative;
    display: flex;
    flex-direction: column;
    align-items: center;
    padding-top: clamp(12px, 2.4vh, 22px);
  }
  .top .overline {
    font-size: 11px;
  }
  .ctx {
    font-weight: 700;
    font-size: 15px;
    max-width: 50vw;
  }

  .stage {
    position: relative;
    flex: 1;
    min-height: 140px;
    margin: clamp(8px, 2vh, 20px) 0 clamp(10px, 2.4vh, 24px);
  }
  .glow,
  .anchor {
    position: absolute;
    left: 0;
    top: 0;
    pointer-events: none;
    transition: transform 700ms var(--spring);
  }
  .glow {
    border-radius: 50%;
    background: radial-gradient(circle, color-mix(in oklab, var(--primary) 34%, transparent), transparent 62%);
  }
  .cv {
    position: absolute;
    left: 0;
    top: 0;
    overflow: hidden;
    transform-origin: center;
    opacity: var(--seen);
    filter: saturate(0.8) brightness(0.8);
    box-shadow: 0 30px 60px -34px #000c;
    transition:
      transform 700ms var(--spring),
      opacity 450ms var(--ease-emph),
      filter 450ms var(--ease-emph),
      border-radius 600ms var(--spring),
      box-shadow 600ms var(--ease-emph);
    animation: cv-in 480ms var(--ease-in-place);
  }
  @keyframes cv-in {
    from {
      opacity: 0;
    }
  }
  .cv:not(.now):hover {
    opacity: calc(var(--seen) + 0.25);
    filter: none;
  }
  .cv.now {
    filter: none;
    box-shadow:
      0 50px 110px -40px color-mix(in oklab, var(--primary) 70%, transparent),
      0 30px 60px -30px #000a;
  }
  .cv:focus-visible {
    outline-offset: 6px;
  }

  .lower {
    position: relative;
    display: flex;
    flex-direction: column;
    align-items: center;
    width: min(760px, 90vw);
    margin: 0 auto;
    text-align: center;
    transition:
      transform 700ms var(--spring),
      width 700ms var(--spring);
  }
  .lower.aside {
    width: min(620px, 52vw);
  }
  .title {
    width: 100%;
    margin: 0;
    font-weight: 800;
    line-height: 1.05;
    padding-block: 0.08em;
    overflow-wrap: anywhere;
    animation: title-in 380ms var(--ease-in-place);
  }
  @keyframes title-in {
    from {
      opacity: 0;
      transform: translateY(10px);
    }
  }
  .meta {
    display: flex;
    flex-wrap: wrap;
    justify-content: center;
    gap: 8px;
    margin-top: 12px;
  }
  .album {
    max-width: 240px;
  }
  .seek {
    width: min(640px, 100%);
    display: flex;
    align-items: center;
    gap: 12px;
    margin-top: clamp(10px, 2vh, 20px);
    font-size: 13px;
    font-variant-numeric: tabular-nums;
    color: var(--on-surface-variant);
  }
  .seek > span {
    width: 44px;
    flex: none;
  }
  .seek > span:first-child {
    text-align: right;
  }
  .wavy {
    flex: 1;
    min-width: 0;
  }
  .seek.live {
    justify-content: center;
    height: 36px;
    gap: 10px;
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

  .bar {
    position: relative;
    z-index: 3;
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
    align-items: center;
    gap: 16px;
    padding: clamp(10px, 2vh, 20px) clamp(16px, 3vw, 40px) clamp(14px, 3vh, 28px);
  }
  .bar > .icon-btn {
    justify-self: start;
  }
  .mid {
    transition: transform 700ms var(--spring);
  }
  .mid :global(.xgroup) {
    --w: clamp(52px, 4.4vw, 66px);
    --h: clamp(52px, 4.4vw, 66px);
    --play-w: clamp(88px, 7.4vw, 116px);
    --play-h: clamp(68px, 5.8vw, 90px);
  }
  .acts {
    justify-self: end;
    display: flex;
    align-items: center;
    gap: 8px;
  }

  /* The words, on glass at the right; narrow, over the whole stage. */
  .panel {
    position: absolute;
    z-index: 2;
    top: clamp(56px, 9vh, 80px);
    right: clamp(16px, 3vw, 40px);
    bottom: clamp(110px, 18vh, 150px);
    width: min(40vw, 560px);
    padding: 0 12px;
    border-radius: 32px;
    background: color-mix(in oklab, var(--on-surface) 7%, transparent);
    backdrop-filter: blur(30px) saturate(1.3);
    -webkit-backdrop-filter: blur(30px) saturate(1.3);
    animation: panel-in 520ms var(--ease-in-place);
  }
  .panel.full {
    left: clamp(16px, 3vw, 40px);
    width: auto;
    background: color-mix(in oklab, var(--surface) 82%, transparent);
  }
  @keyframes panel-in {
    from {
      opacity: 0;
      transform: translateX(60px);
    }
  }

  @media (max-width: 760px) {
    .acts .xgroup :global(.xb:not(.toggle)) {
      display: none;
    }
  }
</style>
