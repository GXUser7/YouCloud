<script lang="ts">
  // My Wave is the first thing on Yandex's home: a quiet living shape when idle, a faster pulse
  // while it plays, and the controls that tune the stream without leaving the page.
  import { onMount } from 'svelte'
  import { tr } from '../../lib/i18n.svelte'
  import { artistNames } from '../../lib/catalog'
  import { pathFromRadii, waveRadii } from '../../lib/shapes'
  import { app, currentTrack, player, startWave, toggle, tuneWave, WAVE } from '../../lib/state.svelte'
  import ButtonGroup from '../ButtonGroup.svelte'
  import Cover from '../Cover.svelte'
  import Icon from '../Icon.svelte'

  let { size = 'hero' }: { size?: 'hero' } = $props()
  let hero = $state<HTMLElement>()
  let visible = $state(false)
  let hidden = $state(false)
  let starting = $state(false)

  const mood = $derived(WAVE.moods.find((m) => m.seed === app.waveMood))
  const shape = $derived(pathFromRadii(waveRadii(mood?.lobes ?? 10, mood?.depth ?? 0.07)))
  const inner = $derived(pathFromRadii(waveRadii(mood?.lobes ?? 10, (mood?.depth ?? 0.07) * 1.4), 0.74, 180 / (mood?.lobes ?? 10)))
  const t = $derived(currentTrack())
  const on = $derived((player.wave && player.playing) || starting)
  const animate = $derived(visible && !hidden)

  onMount(() => {
    const observer = new IntersectionObserver(([entry]) => (visible = entry.isIntersecting), { threshold: 0.05 })
    if (hero) observer.observe(hero)

    const onVisibility = () => (hidden = document.hidden)
    document.addEventListener('visibilitychange', onVisibility)
    return () => {
      observer.disconnect()
      document.removeEventListener('visibilitychange', onVisibility)
    }
  })

  async function wave() {
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

<section bind:this={hero} class="hero glass rise" class:animate class:playing={on && animate}>
  <div class="hero-copy">
    <span class="overline">{tr('Яндекс Музыка')}</span>
    <h2 class="display">{tr('Моя волна')}</h2>
    <p class="subtitle">{tr('Подстраивается под «Нравится» и пропуски')}</p>

    {#if player.wave && t}
      <div class="now-playing">
        <span class="now-cover"><Cover seed={t.id} color={t.color} src={t.cover} /></span>
        <span class="now-copy">
          <span class="now-label">{tr('Сейчас играет')}</span>
          <span class="ellipsis now-title">{t.title}</span>
          <span class="ellipsis muted now-artist">{artistNames(t)}</span>
        </span>
      </div>
    {:else if starting}
      <p class="status muted">{tr('Волна подбирает треки…')}</p>
    {/if}

    <div class="tuning">
      <div class="tuning-row">
        <span class="tuning-label">{tr('Настроение')}</span>
        <div class="chips">
          <ButtonGroup options={WAVE.moods.map((m) => ({ value: m.seed, label: m.title }))} value={app.waveMood} allowNone size="s" onchange={(v) => tuneWave('mood', v)} />
        </div>
      </div>
      <div class="tuning-row">
        <span class="tuning-label">{tr('Знакомство')}</span>
        <div class="chips">
          <ButtonGroup options={WAVE.modes.map((m) => ({ value: m.seed, label: m.title }))} value={app.waveMode} allowNone size="s" onchange={(v) => tuneWave('mode', v)} />
        </div>
      </div>
    </div>
  </div>

  <div class="visual">
    <svg class="backdrop" viewBox="0 0 100 100" preserveAspectRatio="xMidYMid meet" aria-hidden="true">
      <path class="blob blob-a" d={shape} />
      <path class="blob blob-b" d={inner} />
      <path class="blob blob-c" d={shape} />
    </svg>
    <button class="form" class:on={on && animate} onclick={wave} aria-label={player.wave && player.playing ? tr('Пауза') : tr('Включить волну')}>
      <span class="spin">
        <svg viewBox="0 0 100 100"><path class="outer" d={shape} /><path class="inner" d={inner} /></svg>
      </span>
      <span class="glyph"><Icon name={player.wave && player.playing ? 'pause' : 'play_arrow'} fill size={58} /></span>
    </button>
  </div>
</section>

<style>
  .hero {
    position: relative;
    min-height: clamp(380px, 50vh, 580px);
    margin-top: 24px;
    padding: clamp(28px, 4vw, 56px);
    display: grid;
    grid-template-columns: minmax(0, 0.95fr) minmax(360px, 1.05fr);
    gap: 24px;
    overflow: hidden;
    isolation: isolate;
    border-radius: 36px;
    background: color-mix(in oklab, var(--primary) 9%, var(--glass));
    box-shadow: var(--shadow);
  }
  .hero-copy {
    position: relative;
    z-index: 2;
    min-width: 0;
    align-self: center;
    display: flex;
    flex-direction: column;
    align-items: flex-start;
  }
  .hero h2 {
    max-width: 9ch;
    margin-top: 14px;
    font-size: clamp(44px, 5.5vw, 86px);
    text-wrap: balance;
  }
  .subtitle {
    max-width: 38ch;
    margin-top: 18px;
    font-size: clamp(16px, 1.5vw, 21px);
    color: var(--on-surface-variant);
  }
  .status {
    margin-top: 24px;
    font-size: 15px;
  }
  .now-playing {
    max-width: min(100%, 430px);
    margin-top: 24px;
    padding: 8px 16px 8px 8px;
    display: flex;
    align-items: center;
    gap: 12px;
    border-radius: var(--r-xl);
    background: color-mix(in oklab, var(--surface) 22%, transparent);
    backdrop-filter: blur(18px);
    -webkit-backdrop-filter: blur(18px);
  }
  .now-cover {
    width: 52px;
    height: 52px;
    flex: none;
    overflow: hidden;
    border-radius: 16px;
  }
  .now-copy {
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 1px;
  }
  .now-label,
  .tuning-label {
    color: var(--on-surface-variant);
    font-size: 11px;
    font-weight: 750;
    letter-spacing: 0.1em;
    text-transform: uppercase;
  }
  .now-title {
    font-weight: 750;
    font-size: 15px;
  }
  .now-artist {
    font-size: 13px;
  }
  .tuning {
    width: 100%;
    max-width: 600px;
    margin-top: 30px;
    display: flex;
    flex-direction: column;
    gap: 12px;
  }
  .tuning-row {
    min-width: 0;
    display: flex;
    align-items: center;
    gap: 14px;
  }
  .tuning-label {
    width: 78px;
    flex: none;
  }
  .chips {
    min-width: 0;
    max-width: 100%;
    overflow-x: auto;
    scrollbar-width: none;
  }
  .visual {
    position: relative;
    min-height: 300px;
    display: grid;
    place-items: center;
    align-self: stretch;
  }
  .backdrop {
    position: absolute;
    inset: -26%;
    width: 152%;
    height: 152%;
    overflow: visible;
    opacity: 0;
    transform: rotate(-12deg) scale(0.88);
    transition: opacity 700ms var(--ease-emph), transform 1.2s var(--spring);
    filter: blur(8px);
  }
  .animate .backdrop {
    opacity: 0.92;
    transform: rotate(-12deg) scale(1);
  }
  .blob {
    transform-box: fill-box;
    transform-origin: center;
    fill: var(--primary);
  }
  .blob-a {
    opacity: 0.28;
    transform: translate(3%, 4%) scale(1.1);
  }
  .blob-b {
    fill: var(--secondary-container, var(--primary-container));
    opacity: 0.48;
    transform: translate(-8%, -6%) scale(0.72) rotate(36deg);
  }
  .blob-c {
    opacity: 0.18;
    transform: translate(18%, 10%) scale(0.66) rotate(-24deg);
  }
  .animate .blob-a {
    animation: drift-a 16s ease-in-out infinite alternate;
  }
  .animate .blob-b {
    animation: drift-b 19s ease-in-out infinite alternate-reverse;
  }
  .animate .blob-c {
    animation: drift-c 23s ease-in-out infinite alternate;
  }
  .playing .blob-a {
    animation-duration: 7s;
  }
  .playing .blob-b {
    animation-duration: 9s;
  }
  .playing .blob-c {
    animation-duration: 11s;
  }
  .form {
    position: relative;
    z-index: 1;
    width: clamp(170px, 19vw, 280px);
    aspect-ratio: 1;
    display: grid;
    place-items: center;
    color: var(--on-primary);
    transition: transform var(--d-fast) var(--spring-fast), filter var(--d-fast) var(--ease-emph);
  }
  .form:hover {
    transform: scale(1.04);
    filter: drop-shadow(0 22px 28px color-mix(in oklab, var(--primary) 26%, transparent));
  }
  .form:active {
    transform: scale(0.92);
  }
  .form > *,
  .spin > * {
    grid-area: 1 / 1;
  }
  .spin {
    width: 100%;
    height: 100%;
    display: grid;
    animation: turn 30s linear infinite;
    animation-play-state: paused;
    will-change: transform;
  }
  .on .spin {
    animation-play-state: running;
  }
  .form svg {
    width: 100%;
    height: 100%;
    overflow: visible;
  }
  .outer {
    fill: var(--primary);
    transition: d 900ms var(--spring);
  }
  .inner {
    fill: color-mix(in oklab, var(--on-primary) 14%, transparent);
    transition: d 900ms var(--spring);
  }
  .glyph {
    position: relative;
    display: grid;
  }
  @keyframes turn {
    to {
      transform: rotate(360deg);
    }
  }
  @keyframes drift-a {
    to { transform: translate(-5%, -4%) scale(1.2) rotate(12deg); }
  }
  @keyframes drift-b {
    to { transform: translate(8%, 8%) scale(0.82) rotate(-18deg); }
  }
  @keyframes drift-c {
    to { transform: translate(2%, -8%) scale(0.78) rotate(18deg); }
  }

  @media (prefers-reduced-motion: reduce) {
    .animate .blob,
    .on .spin {
      animation: none;
    }
  }

  @container page (max-width: 760px) {
    .hero {
      min-height: 0;
      padding: 28px 22px 24px;
      display: flex;
      flex-direction: column;
      gap: 0;
    }
    .hero h2 {
      max-width: 10ch;
      font-size: clamp(40px, 12vw, 64px);
    }
    .subtitle {
      margin-top: 14px;
    }
    .tuning {
      margin-top: 24px;
    }
    .tuning-row {
      align-items: flex-start;
      flex-direction: column;
      gap: 7px;
    }
    .visual {
      min-height: 210px;
      margin-top: 8px;
      align-items: end;
    }
    .backdrop {
      inset: -34% -22%;
      width: 144%;
      height: 144%;
    }
    .form {
      width: min(210px, 58vw);
    }
  }
</style>
