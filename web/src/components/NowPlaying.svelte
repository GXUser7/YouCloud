<script lang="ts">
  // The full player, over everything: the cover large, the Expressive slider and big controls,
  // and the lyrics or the queue beside them — or neither, for the cover and the controls alone,
  // centred. Lyrics and «Далее» are toggles: pressed again, the column goes. Narrow, the column
  // takes the cover's place instead.
  import { flushSync } from 'svelte'
  import { fly } from 'svelte/transition'
  import { tr } from '../lib/i18n.svelte'
  import { serviceName, track } from '../lib/catalog'
  import { app, currentTrack, cycleRepeat, go, jump, player, remember, seek, startRadio } from '../lib/state.svelte'
  import { time } from '../lib/format'
  import Cover from './Cover.svelte'
  import Icon from './Icon.svelte'
  import Lyrics from './Lyrics.svelte'
  import PlayerControls from './PlayerControls.svelte'
  import ServiceGlyph from './ServiceGlyph.svelte'
  import Slider from './Slider.svelte'
  import TrackMenu from './TrackMenu.svelte'
  import TrackRow from './TrackRow.svelte'

  type Side = 'lyrics' | 'queue' | 'none'

  const t = $derived(currentTrack()!)
  const upNext = $derived(player.queue[player.index + 1] ? track(player.queue[player.index + 1]) : null)
  const duration = $derived(player.duration || t.duration || 1)
  const upcoming = $derived(player.queue.slice(player.index + 1, player.index + 60))
  let scrub = $state<number | null>(null)

  function storedSide(): Side {
    try {
      const v = JSON.parse(localStorage.getItem('yc.npSide') ?? '"lyrics"')
      return v === 'queue' || v === 'none' ? v : 'lyrics'
    } catch {
      return 'lyrics'
    }
  }

  // Wide, the column stands beside the controls (or is put away, remembered); narrow, it takes the
  // cover's place only when asked for, as the app shows lyrics in place of the cover.
  let side = $state<Side>(storedSide())
  let swapped = $state(false)
  const wideQuery = matchMedia('(min-width: 1281px)')
  let wide = $state(wideQuery.matches)
  $effect(() => {
    const changed = (e: MediaQueryListEvent) => (wide = e.matches)
    wideQuery.addEventListener('change', changed)
    return () => wideQuery.removeEventListener('change', changed)
  })
  const shown = $derived<Side>(side !== 'none' && (wide || swapped) ? side : 'none')

  let artEl = $state<HTMLElement>()
  let infoEl = $state<HTMLElement>()
  const EASE = 'cubic-bezier(0.2, 0, 0, 1)'
  const calm = matchMedia('(prefers-reduced-motion: reduce)')

  /**
   * The column turned to [view], or put away when it shows it already. The cover and the controls
   * glide to where the new layout puts them (FLIP: measured before and after, then moved back and
   * let go, by transforms alone — nothing laid out or painted again on the way).
   */
  function pick(view: 'lyrics' | 'queue') {
    const moving = calm.matches ? [] : [artEl, infoEl].filter((el): el is HTMLElement => !!el)
    const before = moving.map((el) => el.getBoundingClientRect())
    flushSync(() => {
      if (shown === view) {
        if (wide) side = 'none'
        swapped = false
      } else {
        side = view
        swapped = true
      }
      if (wide) remember('npSide', side)
    })
    moving.forEach((el, i) => {
      const a = before[i]
      const b = el.getBoundingClientRect()
      if (!b.width) return
      // The cover grows or shrinks as well; the words only move, never stretched.
      const scale = el === artEl ? a.width / b.width : 1
      const dx = a.left - b.left + (el === artEl ? 0 : (a.width - b.width) / 2)
      const dy = a.top - b.top
      if (Math.abs(dx) < 1 && Math.abs(dy) < 1 && Math.abs(scale - 1) < 0.01) return
      el.animate(
        [
          { transformOrigin: 'top left', transform: `translate(${dx}px, ${dy}px) scale(${scale})` },
          { transformOrigin: 'top left', transform: 'none' },
        ],
        { duration: 560, easing: EASE },
      )
    })
  }

  function close() {
    app.nowPlaying = false
  }
</script>

<svelte:window onkeydown={(e) => e.key === 'Escape' && close()} />

<div class="np" role="dialog" aria-label={tr('Сейчас играет')}>

  <header>
    <button class="icon-btn l glassy" onclick={close} aria-label={tr('Свернуть')}><Icon name="keyboard_arrow_down" size={30} /></button>
    <div class="from">
      <span class="overline">{player.wave ? tr('Моя волна') : tr('Играет из')}</span>
      <span class="ctx ellipsis">{player.wave ? tr('Яндекс Музыка') : player.context || serviceName(t.service)}</span>
    </div>
    <TrackMenu id={t.id} large onleave={close} />
  </header>

  <div class="stage" class:solo={shown === 'none'} class:swapped={!wide && shown !== 'none'}>
    <div class="art-wrap" bind:this={artEl}>
      {#key t.id}
        <div class="art"><Cover seed={t.id} color={t.color} src={t.cover} grain /></div>
      {/key}
    </div>

    <div class="info" bind:this={infoEl}>
      <div class="badges">
        <span class="chip service"><ServiceGlyph service={t.service} size={16} />{serviceName(t.service)}</span>
        {#if t.album}
          <button
            class="chip"
            onclick={() => {
              close()
              go('set/' + t.album!.id)
            }}><Icon name="album" size={18} /><span class="ellipsis album-name">{t.album.title || tr('Альбом')}</span></button
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

      {#if player.live}
        <!-- A broadcast's place for the slider, as tall as it: «В эфире», with a red dot, as in the app. -->
        <div class="seek live" title={tr('Прямая трансляция')}><span class="dot"></span>{tr('В эфире')}</div>
      {:else}
        <div class="seek">
          <Slider
            value={(scrub ?? player.position) / duration}
            thickness={18}
            label={tr('Перемотка')}
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
      {/if}

      <div class="main-row">
        <PlayerControls size="xl" tonal items={['like', 'prev', 'play', 'next', 'shuffle']} />
      </div>

      <div class="next-row">
        <button
          class="next-card"
          class:on={shown === 'queue'}
          onclick={() => pick('queue')}
          aria-pressed={shown === 'queue'}
          title={shown === 'queue' ? tr('Скрыть очередь') : tr('Показать очередь')}
        >
          {#if upNext}
            {#key upNext.id}<span class="next-cover"><Cover seed={upNext.id} color={upNext.color} src={upNext.cover} /></span>{/key}
            <span class="next-text">
              <span class="overline">{tr('Далее')}</span>
              <span class="ellipsis next-title">{upNext.title}</span>
            </span>
          {:else}
            <span class="next-cover empty"><Icon name="radio" size={22} /></span>
            <span class="next-text"><span class="overline">{tr('Далее')}</span><span class="next-title ellipsis">{player.radio ? tr('Радио подбирает треки…') : tr('Очередь пуста')}</span></span>
          {/if}
          {#key shown === 'queue'}<span class="next-glyph"><Icon name={shown === 'queue' ? 'close' : 'queue_music'} size={22} /></span>{/key}
        </button>
        <div class="xgroup l">
          <button
            class="xb toggle tonal"
            class:on={shown === 'lyrics'}
            onclick={() => pick('lyrics')}
            aria-pressed={shown === 'lyrics'}
            aria-label={tr('Текст')}
            title={shown === 'lyrics' ? tr('Скрыть текст') : tr('Показать текст')}
          >
            <span class="ic"><Icon name="lyrics" fill={shown === 'lyrics'} size={24} /></span>
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
        </div>
      </div>
    </div>

    {#if shown !== 'none'}
      <div class="words" in:fly={{ x: 48, duration: calm.matches ? 0 : 480, opacity: 0, easing: (t) => 1 - Math.pow(1 - t, 4) }}>
        {#if shown === 'lyrics'}
          <Lyrics size="l" />
        {:else}
          <div class="queue">
            <p class="overline queue-head">{tr('Далее · %s', player.wave ? tr('Моя волна') : player.context)}</p>
            {#each upcoming as id, i (id + i)}
              <TrackRow {id} list={player.queue} compact context={player.context} contextId={player.contextId} onplay={() => jump(player.index + 1 + i)} />
            {:else}
              <p class="muted">{player.radio ? tr('Радио подбирает следующие треки…') : tr('Дальше ничего нет — включи радио по треку.')}</p>
            {/each}
          </div>
        {/if}
      </div>
    {/if}
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
    animation: up 420ms var(--ease-in-place);
  }
  /* The player rises in and stops: no scaling, no corners rounding off on the way. */
  @keyframes up {
    from {
      transform: translateY(28px);
      opacity: 0;
    }
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

  /* The cover, the controls and the column: the layout morphs between them (a view transition
     with these names) when the column comes or goes. */
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
  .stage.solo {
    grid-template-columns: auto minmax(380px, 540px);
    justify-content: center;
    gap: clamp(40px, 6vw, 96px);
  }
  .art-wrap {
    display: grid;
    place-items: center;
  }
  .info {
    min-width: 0;
  }
  .words {
    height: 100%;
    min-height: 0;
    max-height: 640px;
    align-self: center;
  }

  .art {
    width: min(52vh, 30vw, 540px);
    aspect-ratio: 1;
    border-radius: 28px;
    overflow: hidden;
    box-shadow:
      0 40px 120px -30px color-mix(in oklab, var(--primary) 55%, transparent),
      0 30px 60px -30px #000a;
    animation: art-in 480ms var(--ease-in-place);
  }
  .solo .art {
    width: min(60vh, 38vw, 640px);
  }
  /* A new cover settles in place: a touch of scale and a fade, no turning out of a disc. */
  @keyframes art-in {
    from {
      transform: scale(0.94);
      opacity: 0;
    }
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
    animation: title-in 360ms var(--ease-in-place);
  }
  @keyframes title-in {
    from {
      opacity: 0;
      transform: translateY(8px);
    }
  }
  .seek {
    margin-top: 26px;
  }
  .seek.live {
    height: 57px;
    display: flex;
    align-items: center;
    gap: 10px;
    font-size: 15px;
    font-weight: 600;
  }
  .dot {
    width: 10px;
    height: 10px;
    border-radius: 50%;
    background: #ff3b30;
  }
  .times {
    display: flex;
    justify-content: space-between;
    font-size: 13px;
    font-variant-numeric: tabular-nums;
    color: var(--on-surface-variant);
    margin-top: -2px;
  }

  /* The big controls stretch over the column; the group spaces them, pressed or not. */
  .main-row {
    margin-top: 22px;
  }
  .main-row :global(.xgroup) {
    --w: clamp(56px, 4.6vw, 72px);
    --h: clamp(56px, 4.6vw, 72px);
    --play-w: clamp(96px, 8vw, 128px);
    --play-h: clamp(80px, 6.6vw, 104px);
    justify-content: space-between;
  }

  /* What comes next: the card is one button — it opens the queue, or puts it away. */
  .next-row {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-top: 22px;
  }
  .next-card {
    flex: 1;
    min-width: 0;
    height: 56px;
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 0 16px 0 8px;
    border-radius: 28px;
    text-align: left;
    position: relative;
    isolation: isolate;
    background: color-mix(in oklab, var(--on-surface) 10%, transparent);
    color: var(--on-surface);
    transition:
      border-radius var(--d-fast) var(--spring-fast),
      background-color 250ms var(--ease-emph),
      color 250ms var(--ease-emph),
      transform var(--d-fast) var(--spring-fast);
  }
  .next-card::before {
    content: '';
    position: absolute;
    inset: 0;
    z-index: -1;
    border-radius: inherit;
    background: currentColor;
    opacity: 0;
    transition: opacity 180ms var(--ease-emph);
  }
  .next-card:hover::before {
    opacity: 0.08;
  }
  .next-card:hover {
    border-radius: 20px;
  }
  .next-card:active {
    border-radius: 14px;
    transform: scale(0.985);
  }
  .next-card:active::before {
    opacity: 0.14;
  }
  /* The queue showing: filled as the toggles beside it are. */
  .next-card.on {
    border-radius: 18px;
    background: var(--primary);
    color: var(--on-primary);
  }
  .next-card.on .overline {
    color: color-mix(in oklab, var(--on-primary) 75%, transparent);
  }
  .next-cover {
    width: 40px;
    height: 40px;
    border-radius: 12px;
    overflow: hidden;
    flex: none;
    display: grid;
    place-items: center;
    transition: border-radius var(--d-spatial) var(--spring);
    animation: next-in 420ms var(--ease-in-place);
  }
  @keyframes next-in {
    from {
      transform: scale(0.7);
      opacity: 0;
    }
  }
  .next-card:hover .next-cover {
    border-radius: 20px;
  }
  .next-cover.empty {
    background: color-mix(in oklab, var(--on-surface) 10%, transparent);
    color: var(--on-surface-variant);
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
  .next-glyph {
    display: grid;
    color: var(--on-surface-variant);
    animation: xb-swap 260ms var(--ease-in-place);
    transition: color 200ms var(--ease-emph);
  }
  .next-card:hover .next-glyph {
    color: var(--primary);
  }
  .next-card.on .next-glyph {
    color: var(--on-primary);
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

  /* Narrow: the column takes the cover's place when asked for. */
  @media (max-width: 1280px) {
    .stage,
    .stage.solo {
      grid-template-columns: auto minmax(360px, 520px);
      justify-content: center;
    }
    .stage.swapped .art-wrap {
      display: none;
    }
    .stage.swapped .words {
      grid-column: 1;
      grid-row: 1;
      width: min(52vh, 30vw, 540px);
      height: min(70vh, 640px);
    }
  }
</style>
