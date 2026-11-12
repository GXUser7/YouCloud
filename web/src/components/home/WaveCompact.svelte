<script lang="ts">
  // «Моя волна» at the top of Yandex's home. On the left what it is and what it plays now, and
  // under that its tuning — the mood, each in its own colour, and how familiar the music is; on
  // the right the wave itself, a shape that turns, morphs to the mood and plays it. Behind it all a
  // slow glow in the mood's colour, brighter while the wave plays.
  import { tr } from '../../lib/i18n.svelte'
  import { pathFromRadii, waveRadii } from '../../lib/shapes'
  import { app, currentTrack, player, startWave, toggle, tuneWave, WAVE } from '../../lib/state.svelte'
  import EqBars from '../EqBars.svelte'
  import Icon from '../Icon.svelte'
  import LoadingIndicator from '../LoadingIndicator.svelte'
  import ServiceGlyph from '../ServiceGlyph.svelte'

  let { size = 'hero' }: { size?: 'hero' } = $props()
  let starting = $state(false)
  const mood = $derived(WAVE.moods.find((m) => m.seed === app.waveMood))
  const shape = $derived(pathFromRadii(waveRadii(mood?.lobes ?? 8, mood?.depth ?? 0.13)))
  const orbitShape = $derived(pathFromRadii(waveRadii(mood?.lobes ?? 8, Math.min((mood?.depth ?? 0.13) + 0.08, 0.26))))
  const on = $derived(player.wave && player.playing)
  const now = $derived(player.wave ? currentTrack() : undefined)
  const MODE_ICONS: Record<string, string> = { favorite: 'favorite', discover: 'explore', popular: 'trending_up' }

  async function wave() {
    if (starting) return
    if (player.wave) return toggle()
    starting = true
    try { await startWave() } finally { starting = false }
  }
  // A picked chip picked again lets the wave go back to untuned.
  const pick = (key: 'mood' | 'mode', seed: string, current: string | null) => tuneWave(key, current === seed ? null : seed)
</script>

<section class="wave glass rise {size}" class:playing={on} class:tuned={!!mood} style:--mood={mood?.color ?? null} aria-label={tr('Моя волна')}>
  <div class="aura" aria-hidden="true"><span class="glow one"></span><span class="glow two"></span><span class="glow three"></span></div>

  <header class="copy">
    <span class="kicker"><ServiceGlyph service="ya" size={14} />{tr('Яндекс Музыка')}</span>
    <h2 class="display">{tr('Моя волна')}</h2>
    {#key now?.id ?? 'idle'}
      <p class="status">
        {#if now}
          <span class="eq"><EqBars playing={player.playing} size={14} /></span>
          <span class="ellipsis"><b>{now.title}</b> · {now.artists.map((a) => a.name).join(', ')}</span>
        {:else}
          <span>{tr('Бесконечный поток музыки, который подстраивается под тебя')}</span>
        {/if}
      </p>
    {/key}
  </header>

  <div class="invitation">
    <svg class="orbit" viewBox="0 0 100 100" aria-hidden="true"><path d={orbitShape} /></svg>
    <button class="play" onclick={wave} disabled={starting} aria-busy={starting} aria-label={on ? tr('Пауза') : tr('Включить волну')}>
      <span class="play-face">
        <svg viewBox="0 0 100 100" aria-hidden="true"><path d={shape} /></svg>
        <span class="glyph">{#if starting}<LoadingIndicator size={48} contained={false} />{:else}<Icon name={on ? 'pause' : 'play_arrow'} fill size={64} />{/if}</span>
      </span>
    </button>
    <span class="sr-only" role="status">{starting ? tr('Волна подбирает треки…') : on ? tr('Сейчас играет') : ''}</span>
  </div>

  <div class="tuning">
    <div class="tuning-row">
      <span class="label">{tr('Настроение')}</span>
      <div class="chips" role="radiogroup" aria-label={tr('Настроение')}>
        {#each WAVE.moods as m (m.seed)}
          {@const picked = app.waveMood === m.seed}
          <button class="chip" class:picked role="radio" aria-checked={picked} style:--c={m.color} onclick={() => pick('mood', m.seed, app.waveMood)}>
            <span class="dot" aria-hidden="true"></span>{m.title}
          </button>
        {/each}
      </div>
    </div>
    <div class="tuning-row">
      <span class="label">{tr('Знакомство')}</span>
      <div class="chips" role="radiogroup" aria-label={tr('Знакомство')}>
        {#each WAVE.modes as m (m.seed)}
          {@const picked = app.waveMode === m.seed}
          <button class="chip" class:picked role="radio" aria-checked={picked} onclick={() => pick('mode', m.seed, app.waveMode)}>
            <span class="chip-icon" aria-hidden="true"><Icon name={MODE_ICONS[m.seed] ?? 'waves'} fill={picked} size={18} /></span>{m.title}
          </button>
        {/each}
      </div>
    </div>
  </div>
</section>

<style>
  .wave {
    --mood: var(--primary);
    --pad: clamp(24px, 3cqw, 40px);
    position: relative;
    isolation: isolate;
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto;
    grid-template-areas:
      'copy invitation'
      'tuning invitation';
    grid-template-rows: auto 1fr;
    column-gap: clamp(24px, 4cqw, 56px);
    row-gap: 28px;
    padding: var(--pad);
    margin-top: 24px;
    border-radius: var(--r-2xl);
    overflow: clip;
  }

  /* ---- The glow behind ---- */
  .aura {
    position: absolute;
    inset: 0;
    z-index: -1;
    pointer-events: none;
  }
  .glow {
    position: absolute;
    border-radius: 50%;
    background: var(--mood);
    -webkit-mask-image: radial-gradient(closest-side, #000 0%, #0008 45%, transparent 100%);
    mask-image: radial-gradient(closest-side, #000 0%, #0008 45%, transparent 100%);
    opacity: 0.22;
    transition: background-color var(--d-slow) var(--ease-emph), opacity var(--d-slow) var(--ease-emph);
    animation: drift 22s ease-in-out infinite alternate;
  }
  .glow.one {
    width: 62%;
    aspect-ratio: 1;
    right: -14%;
    top: -55%;
  }
  .glow.two {
    width: 48%;
    aspect-ratio: 1;
    right: 22%;
    bottom: -70%;
    background: var(--tertiary);
    opacity: 0.1;
    animation-duration: 28s;
    animation-delay: -9s;
  }
  .glow.three {
    width: 40%;
    aspect-ratio: 1;
    left: -14%;
    top: -60%;
    opacity: 0.08;
    animation-duration: 34s;
    animation-delay: -17s;
  }
  .tuned .glow.three {
    background: var(--mood);
  }
  .playing .glow.one {
    opacity: 0.34;
  }
  .playing .glow.two {
    opacity: 0.16;
  }
  .playing .glow {
    animation-duration: 12s;
  }
  @keyframes drift {
    from {
      transform: translate(0, 0) scale(1);
    }
    to {
      transform: translate(-8%, 10%) scale(1.15);
    }
  }

  /* ---- What it is, and what it plays ---- */
  .copy {
    grid-area: copy;
    min-width: 0;
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
  }
  .kicker {
    display: inline-flex;
    align-items: center;
    gap: 7px;
    height: 28px;
    padding: 0 12px 0 10px;
    border-radius: var(--r-full);
    background: color-mix(in oklab, var(--on-surface) 8%, transparent);
    color: var(--on-surface-variant);
    font-size: 12px;
    font-weight: 700;
    letter-spacing: 0.04em;
  }
  h2 {
    font-size: clamp(40px, 5.4cqw, 76px);
    line-height: 1;
    margin: 2px 0 0;
    overflow-wrap: anywhere;
  }
  .status {
    max-width: 100%;
    min-width: 0;
    display: flex;
    align-items: center;
    gap: 10px;
    font-size: 15.5px;
    color: var(--on-surface-variant);
    animation: status-in var(--d-spatial) var(--spring);
  }
  .status b {
    color: var(--on-surface);
    font-weight: 700;
  }
  .eq {
    display: grid;
    place-items: center;
    flex: none;
    width: 26px;
    height: 26px;
    border-radius: 50%;
    background: var(--mood);
    color: var(--on-primary);
    transition: background-color var(--d-slow) var(--ease-emph);
  }
  /* The moods' colours are all light: the bars go dark on them, in either theme. */
  .tuned .eq {
    color: color-mix(in oklab, #000 78%, var(--mood));
  }
  @keyframes status-in {
    from {
      opacity: 0;
      transform: translateY(6px);
    }
  }

  /* ---- The wave: a shape that plays ---- */
  .invitation {
    grid-area: invitation;
    position: relative;
    align-self: center;
    width: clamp(220px, 21cqw, 280px);
    aspect-ratio: 1;
    display: grid;
    place-items: center;
  }
  .orbit {
    position: absolute;
    inset: 0;
    width: 100%;
    height: 100%;
    fill: var(--mood);
    opacity: 0.12;
    animation: wave-turn 60s linear infinite reverse;
    pointer-events: none;
    transition: fill var(--d-slow) var(--ease-emph), opacity var(--d-slow) var(--ease-emph);
  }
  .playing .orbit {
    opacity: 0.22;
  }
  .orbit > path { transition: d var(--d-slow) var(--spring); }
  .play { width: 70%; aspect-ratio: 1; position: relative; display: grid; place-items: center; color: var(--on-primary); border-radius: 50%; transition: transform var(--d-spatial) var(--spring); }
  /* A soft glow of the mood under the shape: still, so the turning shape costs no filter each frame. */
  .play::before { content: ''; position: absolute; inset: 14%; z-index: -1; border-radius: 50%; background: var(--mood); filter: blur(32px); opacity: 0.35; transition: opacity var(--d-slow) var(--ease-emph), background-color var(--d-slow) var(--ease-emph); }
  .playing .play::before { opacity: 0.55; }
  .play-face { position: relative; display: grid; place-items: center; width: 100%; height: 100%; pointer-events: none; }
  .play-face > svg { position: absolute; inset: 0; width: 100%; height: 100%; fill: var(--primary); overflow: visible; animation: wave-turn 60s linear infinite; }
  .play-face > svg > path { transform-origin: 50px 50px; transition: rotate var(--d-spatial) var(--spring), d var(--d-slow) var(--spring), fill 220ms var(--ease-emph); }
  .play:enabled:hover, .play:focus-visible { transform: translateY(-3px) scale(1.07); }
  .play:enabled:hover .play-face > svg > path, .play:focus-visible .play-face > svg > path { rotate: 24deg; fill: color-mix(in oklab, var(--primary) 88%, var(--on-surface)); }
  .play:enabled:active { transform: scale(0.94); }
  .playing .play-face > svg > path { rotate: 22deg; }
  .glyph { position: relative; display: grid; place-items: center; }
  .sr-only { position: absolute; width: 1px; height: 1px; padding: 0; overflow: hidden; clip-path: inset(50%); white-space: nowrap; }
  @keyframes wave-turn {
    to { transform: rotate(1turn); }
  }

  /* ---- Tuning ---- */
  .tuning {
    grid-area: tuning;
    align-self: end;
    min-width: 0;
    display: flex;
    flex-wrap: wrap;
    gap: 20px 32px;
  }
  .tuning-row {
    display: flex;
    flex-direction: column;
    gap: 10px;
    min-width: 0;
  }
  .label {
    font-size: 11px;
    font-weight: 700;
    letter-spacing: 0.12em;
    text-transform: uppercase;
    color: var(--on-surface-variant);
    padding-left: 4px;
  }
  .chips {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
  }
  .chip {
    --c: var(--primary);
    display: inline-flex;
    align-items: center;
    gap: 8px;
    height: 38px;
    padding: 0 16px 0 13px;
    border-radius: var(--r-full);
    background: color-mix(in oklab, var(--on-surface) 7%, transparent);
    box-shadow: inset 0 0 0 1px color-mix(in oklab, var(--on-surface) 8%, transparent);
    color: var(--on-surface);
    font-size: 14px;
    font-weight: 650;
    white-space: nowrap;
    transition:
      background-color 200ms var(--ease-emph),
      box-shadow 200ms var(--ease-emph),
      color 200ms var(--ease-emph),
      transform var(--d-fast) var(--spring-fast);
  }
  .chip:hover {
    background: color-mix(in oklab, var(--on-surface) 12%, transparent);
  }
  .chip:active {
    transform: scale(0.95);
  }
  .chip.picked {
    background: color-mix(in oklab, var(--c) 22%, transparent);
    box-shadow: inset 0 0 0 1px color-mix(in oklab, var(--c) 55%, transparent);
  }
  .chip.picked:hover {
    background: color-mix(in oklab, var(--c) 30%, transparent);
  }
  .dot {
    width: 10px;
    height: 10px;
    border-radius: 50%;
    background: var(--c);
    box-shadow: 0 0 0 0 color-mix(in oklab, var(--c) 40%, transparent);
    transition: transform var(--d-fast) var(--spring-fast), box-shadow 250ms var(--ease-emph);
  }
  .chip:hover .dot {
    transform: scale(1.2);
  }
  .chip.picked .dot {
    box-shadow: 0 0 0 4px color-mix(in oklab, var(--c) 30%, transparent);
  }
  .chip-icon {
    display: grid;
    margin-left: -1px;
    color: var(--on-surface-variant);
    transition: color 200ms var(--ease-emph), transform var(--d-fast) var(--spring-fast);
  }
  .chip:hover .chip-icon {
    transform: scale(1.12) rotate(-8deg);
  }
  .chip.picked .chip-icon {
    color: var(--primary);
  }

  @container page (max-width: 760px) {
    .wave {
      --pad: 22px;
      grid-template-columns: minmax(0, 1fr);
      grid-template-areas:
        'copy'
        'invitation'
        'tuning';
      grid-template-rows: auto;
      row-gap: 20px;
    }
    .invitation {
      justify-self: center;
      width: min(240px, 72cqw);
    }
    .tuning {
      flex-direction: column;
      gap: 18px;
    }
    h2 {
      font-size: clamp(34px, 11cqw, 52px);
    }
    .status {
      font-size: 14.5px;
    }
    .chip {
      height: 36px;
      padding: 0 14px 0 11px;
    }
  }
  @media (prefers-reduced-motion: reduce) {
    .orbit, .play-face > svg, .glow { animation: none; }
    .play, .play-face > svg > path, .orbit > path { transition: none; }
  }
</style>
