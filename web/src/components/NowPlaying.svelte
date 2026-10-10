<script lang="ts">
  // The full player, over everything: the cover large with its colour glowing behind it (the
  // app's Ambilight), the Expressive slider and big controls, and the lyrics alongside.
  import { serviceName, track } from '../lib/catalog'
  import { app, currentTrack, cycleRepeat, go, jump, like, next, player, prev, seek, startRadio, toggle, toggleShuffle } from '../lib/state.svelte'
  import { time } from '../lib/format'
  import BlurredCover from './BlurredCover.svelte'
  import Cover from './Cover.svelte'
  import Icon from './Icon.svelte'
  import Lyrics from './Lyrics.svelte'
  import PlayButton from './PlayButton.svelte'
  import ServiceGlyph from './ServiceGlyph.svelte'
  import Slider from './Slider.svelte'
  import TrackMenu from './TrackMenu.svelte'
  import TrackRow from './TrackRow.svelte'

  const t = $derived(currentTrack()!)
  const liked = $derived(!!player.liked[t.id])
  const upNext = $derived(player.queue[player.index + 1] ? track(player.queue[player.index + 1]) : null)
  const duration = $derived(player.duration || t.duration || 1)
  let scrub = $state<number | null>(null)

  // The column beside the controls: the lyrics, or what plays next. On a narrow window it takes the
  // cover's place when asked for, as the app shows lyrics in place of the cover.
  let side = $state<'lyrics' | 'queue'>('lyrics')
  let swapped = $state(false)
  const upcoming = $derived(player.queue.slice(player.index + 1, player.index + 60))

  function show(view: 'lyrics' | 'queue') {
    swapped = side === view ? !swapped : true
    side = view
  }

  function close() {
    app.nowPlaying = false
  }
</script>

<svelte:window onkeydown={(e) => e.key === 'Escape' && close()} />

<div class="np" role="dialog" aria-label="Сейчас играет">
  {#key t.id}
    <div class="glow" aria-hidden="true"><BlurredCover seed={t.id} color={t.color} src={t.cover} blur={110} saturate={1.4} /></div>
  {/key}
  <div class="veil"></div>

  <header>
    <button class="icon-btn l glassy" onclick={close} aria-label="Свернуть"><Icon name="keyboard_arrow_down" size={30} /></button>
    <div class="from">
      <span class="overline">{player.wave ? 'Моя волна' : 'Играет из'}</span>
      <span class="ctx ellipsis">{player.wave ? 'Яндекс Музыка' : player.context || serviceName(t.service)}</span>
    </div>
    <TrackMenu id={t.id} large onleave={close} />
  </header>

  <div class="stage" class:swapped>
    <div class="art-wrap">
      {#key t.id}
        <div class="art"><Cover seed={t.id} color={t.color} src={t.cover} grain /></div>
      {/key}
    </div>

    <div class="info">
      <div class="badges">
        <span class="chip service"><ServiceGlyph service={t.service} size={16} />{serviceName(t.service)}</span>
        {#if t.album}
          <button
            class="chip"
            onclick={() => {
              close()
              go('set/' + t.album!.id)
            }}><Icon name="album" size={18} /><span class="ellipsis album-name">{t.album.title || 'Альбом'}</span></button
          >
        {/if}
      </div>
      {#key t.id}
        <h1 class="display title">{t.title}</h1>
      {/key}
      <div class="artists">
        {#each t.artists as a, i (a.id + i)}
          <button
            class="chip"
            onclick={() => {
              close()
              go('artist/' + a.id)
            }}>{a.name}</button
          >
        {/each}
      </div>

      <div class="seek">
        <Slider
          value={(scrub ?? player.position) / duration}
          thickness={18}
          label="Перемотка"
          onchange={(f) => (scrub = f * duration)}
          onrelease={(f) => {
            seek(f * duration)
            scrub = null
          }}
        />
        <div class="times">
          <span>{time(scrub ?? player.position)}</span>
          <span>{time(duration)}</span>
        </div>
      </div>

      <div class="controls">
        <button class="icon-btn square heart" class:liked onclick={() => like(t.id)} aria-label="Нравится">
          <Icon name="favorite" fill={liked} size={28} />
        </button>
        <button class="icon-btn glassy big" onclick={prev} aria-label="Предыдущий"><Icon name="skip_previous" fill size={32} /></button>
        <PlayButton playing={player.playing} onclick={toggle} size={104} />
        <button class="icon-btn glassy big" onclick={() => next()} aria-label="Следующий"><Icon name="skip_next" fill size={32} /></button>
        <button class="icon-btn glassy mid" class:active={player.shuffle} onclick={toggleShuffle} aria-label="Перемешать" aria-pressed={player.shuffle}>
          <Icon name="shuffle" />
        </button>
      </div>

      <div class="next-row">
        <button class="next-card glass" class:on={side === 'queue'} onclick={() => show('queue')}>
          {#if upNext}
            <span class="next-cover"><Cover seed={upNext.id} color={upNext.color} src={upNext.cover} /></span>
            <span class="next-text">
              <span class="overline">Далее</span>
              <span class="ellipsis next-title">{upNext.title}</span>
            </span>
          {:else}
            <span class="next-text"><span class="overline">Далее</span><span class="next-title">Радио по треку</span></span>
          {/if}
          <span class="icon-btn s glassy"><Icon name="queue_music" size={20} /></span>
        </button>
        <button class="icon-btn glassy l" class:active={side === 'lyrics'} onclick={() => show('lyrics')} aria-label="Текст" title="Текст песни" aria-pressed={side === 'lyrics'}>
          <Icon name="lyrics" fill={side === 'lyrics'} />
        </button>
        <button class="icon-btn glassy l" onclick={() => startRadio(t.id)} aria-label="Радио по треку" title="Радио по треку">
          <Icon name="radio" />
        </button>
        <button class="icon-btn glassy l" class:active={player.repeat !== 'off'} onclick={cycleRepeat} aria-label="Повтор" aria-pressed={player.repeat !== 'off'}>
          <Icon name={player.repeat === 'one' ? 'repeat_one' : 'repeat'} />
        </button>
      </div>
    </div>

    <div class="words">
      {#if side === 'lyrics'}
        <Lyrics size="l" />
      {:else}
        <div class="queue">
          <p class="overline queue-head">Далее · {player.wave ? 'Моя волна' : player.context}</p>
          {#each upcoming as id, i (id + i)}
            <TrackRow {id} list={player.queue} compact context={player.context} contextId={player.contextId} onplay={() => jump(player.index + 1 + i)} />
          {:else}
            <p class="muted">{player.radio ? 'Радио подбирает следующие треки…' : 'Дальше ничего нет — включи радио по треку.'}</p>
          {/each}
        </div>
      {/if}
    </div>
  </div>
</div>

<style>
  .np {
    position: fixed;
    inset: 0;
    z-index: 20;
    background: var(--surface);
    display: flex;
    flex-direction: column;
    overflow: hidden;
    animation: up var(--d-slow) var(--spring);
  }
  @keyframes up {
    from {
      transform: translateY(40px) scale(0.97);
      opacity: 0;
      border-radius: 48px;
    }
  }
  .glow {
    position: absolute;
    left: 50%;
    top: 50%;
    width: 130vmax;
    height: 130vmax;
    translate: -50% -50%;
    opacity: 0.5;
    animation: glow-in 1.2s var(--ease-emph);
  }
  @keyframes glow-in {
    from {
      opacity: 0;
    }
  }
  .veil {
    position: absolute;
    inset: 0;
    background:
      radial-gradient(80% 60% at 30% 40%, transparent, color-mix(in oklab, var(--surface) 60%, transparent)),
      linear-gradient(color-mix(in oklab, var(--surface) 20%, transparent), color-mix(in oklab, var(--surface) 70%, transparent));
  }
  header {
    position: relative;
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 20px 28px 0;
  }
  .from {
    display: flex;
    flex-direction: column;
    align-items: center;
    min-width: 0;
  }
  .ctx {
    font-weight: 700;
    font-size: 15px;
    max-width: 40vw;
  }
  .stage {
    position: relative;
    flex: 1;
    min-height: 0;
    display: grid;
    grid-template-columns: auto minmax(360px, 520px) minmax(320px, 1fr);
    align-items: center;
    gap: clamp(28px, 4vw, 64px);
    padding: 12px clamp(28px, 4vw, 72px) 36px;
  }
  .art-wrap {
    display: grid;
    place-items: center;
  }
  .art {
    width: min(52vh, 30vw, 540px);
    aspect-ratio: 1;
    border-radius: 28px;
    overflow: hidden;
    box-shadow:
      0 40px 120px -30px color-mix(in oklab, var(--primary) 55%, transparent),
      0 30px 60px -30px #000a;
    animation: art-in var(--d-slow) var(--spring);
  }
  @keyframes art-in {
    from {
      transform: scale(0.82) rotate(-4deg);
      border-radius: 50%;
      opacity: 0.4;
    }
  }
  .info {
    min-width: 0;
  }
  .badges,
  .artists {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
  }
  .service {
    color: var(--primary);
  }
  .album-name {
    max-width: 260px;
  }
  .title {
    font-size: clamp(32px, 3.4vw, 50px);
    margin: 18px 0 16px;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
    animation: rise var(--d-slow) var(--spring);
  }
  .seek {
    margin-top: 26px;
  }
  .times {
    display: flex;
    justify-content: space-between;
    font-size: 13px;
    font-variant-numeric: tabular-nums;
    color: var(--on-surface-variant);
    margin-top: -2px;
  }
  .controls {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-top: 22px;
    gap: 10px;
  }
  .heart {
    --s: 68px;
    --r: var(--r-l);
    background: color-mix(in oklab, var(--on-surface) 10%, transparent);
    color: var(--on-surface);
  }
  .heart.liked {
    background: var(--primary);
    color: var(--on-primary);
  }
  .big {
    --s: 76px;
  }
  .mid {
    --s: 60px;
  }
  .next-row {
    display: flex;
    gap: 10px;
    margin-top: 22px;
  }
  .next-card {
    flex: 1;
    min-width: 0;
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 8px 10px 8px 8px;
    border-radius: var(--r-xl);
    text-align: left;
    transition: border-radius var(--d-fast) var(--spring-fast);
  }
  .next-card:active {
    border-radius: var(--r-m);
  }
  .next-card.on {
    background: color-mix(in oklab, var(--primary) 18%, transparent);
  }
  .queue {
    height: 100%;
    overflow-y: auto;
    padding: 8px 4px;
    mask-image: linear-gradient(#000 88%, transparent);
  }
  .queue-head {
    padding: 4px 10px 10px;
  }
  .next-cover {
    width: 44px;
    height: 44px;
    border-radius: var(--r-s);
    overflow: hidden;
    flex: none;
  }
  .next-text {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
  }
  .next-text .overline {
    font-size: 11px;
  }
  .next-title {
    font-weight: 650;
  }
  .words {
    height: 100%;
    min-height: 0;
    max-height: 640px;
    align-self: center;
  }
  @media (max-width: 1280px) {
    .stage {
      grid-template-columns: auto minmax(360px, 520px);
    }
    .words {
      display: none;
    }
    .stage.swapped .art-wrap {
      display: none;
    }
    .stage.swapped .words {
      display: block;
      grid-column: 1;
      grid-row: 1;
      width: min(52vh, 30vw, 540px);
      height: min(70vh, 640px);
    }
  }
</style>
