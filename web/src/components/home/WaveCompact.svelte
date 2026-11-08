<script lang="ts">
  // The stream flows behind the controls; the cover's palette already follows the player on :root.
  import { onMount } from 'svelte'
  import { tr } from '../../lib/i18n.svelte'
  import { artistNames } from '../../lib/catalog'
  import { clipPolygon, pathFromRadii, shapePath, waveRadii } from '../../lib/shapes'
  import { app, currentTrack, player, startWave, toggle, tuneWave, WAVE } from '../../lib/state.svelte'
  import ButtonGroup from '../ButtonGroup.svelte'
  import Cover from '../Cover.svelte'

  let { size = 'hero' }: { size?: 'hero' } = $props()
  let hero: HTMLElement
  let visible = $state(false)
  let hidden = $state(false)
  let starting = $state(false)

  const mood = $derived(WAVE.moods.find((m) => m.seed === app.waveMood))
  const shape = $derived(pathFromRadii(waveRadii(mood?.lobes ?? 8, mood?.depth ?? 0.13)))
  const t = $derived(currentTrack())
  const on = $derived(player.wave && player.playing)
  const animate = $derived(visible && !hidden)
  const clouds = ['cookie12', 'sunny', 'flower', 'softBurst'] as const
  const currents = [
    'M-300 340 C-40 70 180 80 440 310 S830 540 1080 275 S1500 80 1800 310',
    'M-300 380 C-20 150 200 120 460 350 S840 470 1100 240 S1510 170 1800 360',
    'M-300 280 C-40 40 200 240 440 360 S840 370 1080 230 S1490 40 1800 280',
    'M-300 430 C-50 220 190 170 450 390 S850 430 1100 300 S1510 120 1800 390',
  ]

  onMount(() => {
    hidden = document.hidden
    const observer = new IntersectionObserver(([entry]) => (visible = entry.isIntersecting), { threshold: 0.05 })
    observer.observe(hero)
    const onVisibility = () => (hidden = document.hidden)
    document.addEventListener('visibilitychange', onVisibility)

    // Only pointer events schedule work: touch, reduced motion and an unseen hero stay still.
    const fine = matchMedia('(hover: hover) and (pointer: fine)')
    const reduced = matchMedia('(prefers-reduced-motion: reduce)')
    let frame = 0
    let x = 0
    let y = 0
    const paint = () => {
      frame = 0
      hero.style.setProperty('--px', `${x}px`)
      hero.style.setProperty('--py', `${y}px`)
    }
    const move = (event: PointerEvent) => {
      if (!fine.matches || reduced.matches || event.pointerType !== 'mouse' || !visible || hidden) return
      const rect = hero.getBoundingClientRect()
      x = ((event.clientX - rect.left) / rect.width - 0.5) * 16
      y = ((event.clientY - rect.top) / rect.height - 0.5) * 12
      if (!frame) frame = requestAnimationFrame(paint)
    }
    const reset = () => {
      x = y = 0
      if (!frame) frame = requestAnimationFrame(paint)
    }
    hero.addEventListener('pointermove', move)
    hero.addEventListener('pointerleave', reset)
    reduced.addEventListener('change', reset)
    return () => {
      observer.disconnect()
      cancelAnimationFrame(frame)
      document.removeEventListener('visibilitychange', onVisibility)
      hero.removeEventListener('pointermove', move)
      hero.removeEventListener('pointerleave', reset)
      reduced.removeEventListener('change', reset)
    }
  })

  async function wave() {
    if (starting) return
    if (player.wave) {
      toggle()
      return
    }
    starting = true
    try {
      await startWave()
    } finally {
      starting = false
    }
  }
</script>

<section bind:this={hero} class="hero glass rise {size}" class:animate class:playing={on} aria-label={tr('Моя волна')}>
  <div class="atmosphere" aria-hidden="true">
    {#each clouds as cloud, i}
      <div class="cloud-depth cloud-{i}">
        <svg class="cloud" viewBox="0 0 100 100"><path d={shapePath(cloud)} /></svg>
      </div>
    {/each}
  </div>
  <div class="veil" aria-hidden="true"></div>
  <div class="flow-depth" aria-hidden="true">
    <svg class="currents" viewBox="0 0 1480 600" preserveAspectRatio="none">
      {#each currents as d, i}<path {d} class="current current-{i}" />{/each}
    </svg>
  </div>

  <div class="hero-copy">
    <span class="overline">{tr('Яндекс Музыка')}</span>
    <h2 class="display">{tr('Моя волна')}</h2>
    <p class="subtitle">{tr('Подстраивается под «Нравится» и пропуски')}</p>

    <div class="tuning">
      <div class="tuning-row">
        <span class="tuning-label">{tr('Настроение')}</span>
        <ButtonGroup expressive label={tr('Настроение')} options={WAVE.moods.map((m) => ({ value: m.seed, label: m.title }))} value={app.waveMood} allowNone onchange={(v) => tuneWave('mood', v)} />
      </div>
      <div class="tuning-row">
        <span class="tuning-label">{tr('Знакомство')}</span>
        <ButtonGroup expressive label={tr('Знакомство')} options={WAVE.modes.map((m) => ({ value: m.seed, label: m.title }))} value={app.waveMode} allowNone onchange={(v) => tuneWave('mode', v)} />
      </div>
    </div>

    {#if player.wave && t}
      <div class="now-playing">
        <span class="now-cover" style:clip-path={clipPolygon('cookie12')}><Cover seed={t.id} color={t.color} src={t.cover} /></span>
        <span class="now-copy">
          <span class="now-label">{player.playing ? tr('Сейчас играет') : tr('Пауза')}</span>
          <span class="ellipsis now-title">{t.title}</span>
          <span class="ellipsis muted now-artist">{artistNames(t)}</span>
        </span>
        <span class="equalizer" aria-hidden="true">{#each [0, 1, 2, 3] as i}<i style:--i={i}></i>{/each}</span>
      </div>
    {:else if starting}
      <p class="status muted" role="status">{tr('Волна подбирает треки…')}</p>
    {/if}
  </div>

  <div class="visual">
    <div class="invitation">
      <div class="beacon">
        <svg class="ripples" viewBox="0 0 220 220" aria-hidden="true">
          {#each [0, 1, 2] as i}<circle cx="110" cy="110" r="92" style:--i={i} />{/each}
        </svg>
        <button class="form" style:--round="path('{shapePath('circle')}')" onclick={wave} aria-busy={starting} aria-label={on ? tr('Пауза') : tr('Включить волну')}>
          <span class="spin">
            <svg viewBox="0 0 100 100" aria-hidden="true"><path class="outer" d={shape} /></svg>
          </span>
          <svg class="glyph" viewBox="0 0 64 64" aria-hidden="true">
            {#if on}<path d="M21 16h6a2 2 0 0 1 2 2v28a2 2 0 0 1-2 2h-6a2 2 0 0 1-2-2V18a2 2 0 0 1 2-2Zm16 0h6a2 2 0 0 1 2 2v28a2 2 0 0 1-2 2h-6a2 2 0 0 1-2-2V18a2 2 0 0 1 2-2Z" />
            {:else}<path d="M24 15.5c0-2 2.2-3.2 3.9-2.1l24 16.5a2.5 2.5 0 0 1 0 4.2l-24 16.5c-1.7 1.1-3.9-.1-3.9-2.1Z" />{/if}
          </svg>
        </button>
      </div>
      <p class="stream-caption">{tr('Бесконечный поток. Только твой.')}</p>
    </div>
  </div>
</section>

<style>
  .hero {
    --motion: paused;
    --px: 0px;
    --py: 0px;
    position: relative;
    min-height: clamp(380px, 55vh, 640px);
    margin-top: 24px;
    padding: clamp(28px, 3.4vw, 56px);
    display: grid;
    grid-template-columns: minmax(0, 1.65fr) minmax(260px, 1fr);
    gap: 24px;
    /* Clip the drifting layers without letting focus scroll the hero's contents. */
    overflow: clip;
    isolation: isolate;
    border-radius: 36px;
    background: var(--glass);
    box-shadow: var(--shadow);
  }
  .animate { --motion: running; }
  .atmosphere, .veil, .flow-depth {
    position: absolute;
    inset: 0;
    pointer-events: none;
  }
  .atmosphere { z-index: -3; }
  .cloud-depth {
    position: absolute;
    width: 52%;
    aspect-ratio: 1;
    transform: translate3d(calc(var(--px) * var(--depth)), calc(var(--py) * var(--depth)), 0);
    transition: transform 700ms var(--spring);
  }
  .cloud {
    width: 100%;
    height: 100%;
    filter: blur(80px);
    animation: drift 24s ease-in-out infinite alternate;
    animation-play-state: var(--motion);
    will-change: transform;
  }
  .cloud-0 { --depth: 0.6; left: -18%; top: -65%; fill: var(--secondary-container); opacity: 0.85; }
  .cloud-1 { --depth: 1.4; left: 34%; top: -42%; fill: var(--primary); opacity: 0.48; }
  .cloud-2 { --depth: -0.8; right: -20%; bottom: -80%; fill: var(--tertiary); opacity: 0.65; }
  .cloud-3 { --depth: 1; left: 12%; bottom: -95%; fill: var(--tertiary-container); opacity: 0.9; }
  .cloud-1 .cloud { animation-duration: 29s; animation-delay: -12s; }
  .cloud-2 .cloud { animation-duration: 33s; animation-delay: -19s; }
  .cloud-3 .cloud { animation-duration: 27s; animation-delay: -7s; }
  .veil {
    z-index: -2;
    background: linear-gradient(105deg, color-mix(in oklab, var(--surface) 62%, transparent), color-mix(in oklab, var(--surface-container) 20%, transparent) 65%, color-mix(in oklab, var(--surface) 30%, transparent));
  }
  .flow-depth {
    z-index: -1;
    transform: translate3d(calc(var(--px) * -0.5), calc(var(--py) * -0.5), 0);
    transition: transform 700ms var(--spring);
    mask-image: linear-gradient(90deg, transparent, #000 35%, #000);
  }
  .currents { width: 100%; height: 100%; overflow: visible; }
  .current {
    fill: none;
    stroke: var(--primary);
    stroke-width: 1.5;
    opacity: 0.26;
    transform-origin: 70% 50%;
    animation: flow 14s ease-in-out infinite alternate;
    animation-play-state: var(--motion);
    will-change: transform;
  }
  .current-1 { stroke: var(--tertiary); stroke-width: 3; opacity: 0.2; animation-duration: 19s; animation-delay: -8s; }
  .current-2 { stroke: var(--on-secondary-container); stroke-width: 1; opacity: 0.18; animation-duration: 23s; animation-delay: -14s; }
  .current-3 { stroke-width: 6; opacity: 0.07; animation-duration: 17s; animation-delay: -4s; }
  .hero-copy {
    min-width: 0;
    align-self: center;
    display: flex;
    flex-direction: column;
    align-items: flex-start;
  }
  .hero h2 {
    margin-top: 18px;
    font-size: clamp(54px, 8.1cqw, 120px);
    white-space: nowrap;
  }
  .subtitle {
    max-width: 36ch;
    margin-top: 20px;
    font-size: clamp(16px, 1.3vw, 21px);
    color: var(--on-surface-variant);
    text-wrap: pretty;
  }
  .tuning {
    margin-top: 32px;
    max-width: 100%;
    display: flex;
    flex-direction: column;
    gap: 18px;
  }
  .tuning-row { min-width: 0; display: flex; flex-direction: column; align-items: flex-start; gap: 8px; }
  .tuning-label, .now-label {
    color: var(--on-surface-variant);
    font-size: 10px;
    font-weight: 750;
    letter-spacing: 0.12em;
    text-transform: uppercase;
  }
  .visual { position: relative; display: grid; place-items: center; }
  .invitation {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 36px;
    transform: translate3d(calc(var(--px) * 0.4), calc(var(--py) * 0.4), 0);
    transition: transform 700ms var(--spring);
  }
  .beacon { position: relative; width: clamp(180px, 11.5vw, 220px); aspect-ratio: 1; }
  .beacon::before {
    content: '';
    position: absolute;
    inset: -18%;
    border-radius: 50%;
    background: var(--primary);
    opacity: 0.2;
    filter: blur(42px);
  }
  .ripples { position: absolute; inset: 0; width: 100%; height: 100%; overflow: visible; }
  .ripples circle {
    fill: none;
    stroke: var(--primary);
    stroke-width: 1;
    transform-origin: center;
    animation: ripple 9s linear infinite;
    animation-delay: calc(var(--i) * -3s);
    animation-play-state: var(--motion);
  }
  .playing .ripples circle { animation-duration: 4.8s; animation-delay: calc(var(--i) * -1.6s); stroke-width: 1.5; }
  .form {
    position: relative;
    width: 100%;
    height: 100%;
    display: grid;
    place-items: center;
    color: var(--on-primary);
    border-radius: 50%;
    transition: transform var(--d-fast) var(--spring-fast);
  }
  .form:hover { transform: scale(1.04); }
  .form:active { transform: scale(0.95); }
  .form > * { grid-area: 1 / 1; }
  .spin {
    width: 100%;
    height: 100%;
    display: grid;
    animation: turn 36s linear infinite;
    animation-play-state: paused;
  }
  .playing .spin { animation-play-state: var(--motion); }
  .form svg { width: 100%; height: 100%; overflow: visible; }
  .outer { fill: var(--primary); transition: d var(--d-spatial) var(--spring); }
  .form:active .outer { d: var(--round); }
  .form .glyph { position: relative; width: 64px; height: 64px; fill: currentColor; }
  .stream-caption {
    max-width: 22ch;
    text-align: center;
    text-wrap: balance;
    font-size: 14px;
    font-weight: 500;
    letter-spacing: 0.015em;
    color: var(--on-surface-variant);
  }
  .now-playing {
    width: min(100%, 410px);
    margin-top: 24px;
    padding: 8px 16px 8px 8px;
    display: flex;
    align-items: center;
    gap: 12px;
    border-radius: 24px;
    background: color-mix(in oklab, var(--surface) 36%, transparent);
  }
  .now-cover { width: 52px; height: 52px; flex: none; overflow: hidden; }
  .now-copy { min-width: 0; flex: 1; display: flex; flex-direction: column; gap: 2px; }
  .now-title { font-weight: 750; font-size: 15px; }
  .now-artist { font-size: 13px; }
  .equalizer { display: flex; align-items: center; gap: 3px; height: 24px; color: var(--primary); }
  .equalizer i {
    width: 3px;
    height: 20px;
    border-radius: var(--r-full);
    background: currentColor;
    transform: scaleY(0.3);
    animation: equalize 950ms ease-in-out infinite alternate;
    animation-delay: calc(var(--i) * -230ms);
    animation-play-state: paused;
  }
  .playing .equalizer i { animation-play-state: var(--motion); }
  .status { margin-top: 24px; font-size: 14px; }
  @keyframes turn { to { transform: rotate(360deg); } }
  @keyframes drift { to { transform: translate3d(9%, 12%, 0) rotate(48deg) scale(1.12, 0.94); } }
  @keyframes flow {
    from { transform: translate3d(-40px, -15px, 0) scaleY(0.85) rotate(-3deg); }
    to { transform: translate3d(40px, 20px, 0) scaleY(1.12) rotate(3deg); }
  }
  @keyframes ripple {
    from { transform: scale(0.85); opacity: 0.32; }
    to { transform: scale(2.15); opacity: 0; }
  }
  @keyframes equalize { to { transform: scaleY(1); } }

  @container page (max-width: 1100px) {
    .hero h2 { font-size: clamp(54px, 7.4cqw, 90px); }
    .hero { grid-template-columns: minmax(0, 1.8fr) minmax(220px, 1fr); }
    .tuning { margin-top: 24px; gap: 14px; }
  }
  @media (max-height: 800px) and (min-width: 900px) {
    .hero { padding-block: 28px; }
    .tuning { margin-top: 24px; gap: 12px; }
    .subtitle { margin-top: 16px; }
  }
  @container page (max-width: 760px) {
    .hero {
      min-height: 0;
      padding: 28px 22px 30px;
      display: flex;
      flex-direction: column;
      gap: 0;
      border-radius: 28px;
    }
    .hero-copy { width: 100%; }
    .hero h2 { margin-top: 14px; font-size: clamp(36px, 12cqw, 72px); }
    .subtitle { margin-top: 16px; font-size: 16px; }
    .tuning { width: 100%; margin-top: 26px; gap: 16px; }
    .tuning-row { align-items: stretch; }
    .tuning :global(.expressive) { display: flex; }
    .tuning :global(.expressive .segment) {
      flex: 1;
      padding-inline: 6px;
      font-size: clamp(12px, 3.35cqw, 15px);
    }
    .visual { min-height: 270px; margin-top: 22px; }
    .beacon { width: 180px; }
    .invitation { gap: 26px; }
    .stream-caption { font-size: 13px; max-width: none; }
    .flow-depth { inset: 20% -100% -10%; mask-image: linear-gradient(180deg, transparent, #000 35%); }
    .cloud-depth { width: 125%; }
    .cloud-0 { top: -10%; left: -75%; }
    .cloud-1 { top: 35%; left: 25%; }
    .cloud-2 { right: -50%; bottom: -25%; }
    .cloud-3 { left: -70%; bottom: -20%; }
    .cloud { filter: blur(60px); }
  }
  @media (max-width: 600px) {
    .hero { margin-inline: -24px; padding-inline: 20px; }
  }
  @media (prefers-reduced-motion: reduce) {
    .hero { --motion: paused; }
    .hero :is(.cloud, .current, .ripples circle, .spin, .equalizer i) { animation: none; }
    .ripples circle { opacity: 0; }
    .ripples circle:first-child { opacity: 0.18; transform: scale(1.35); }
    .hero :is(.cloud-depth, .flow-depth, .invitation, .form, .outer) { transition: none; }
  }
</style>
