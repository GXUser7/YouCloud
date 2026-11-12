<script lang="ts">
  // «Моя волна» at the top of Yandex's home: its name and what it is on the left, its tuning under
  // them — the mood, and how familiar the music is; on the right the wave itself, a shape that
  // turns, morphs to the mood and plays it. What it plays now is the bar's to say, not repeated
  // here. All in the scheme's colours (docs/DESIGN.md): the accent marks the shape and what is
  // picked, and nothing has a rim.
  import { tr } from '../../lib/i18n.svelte'
  import { pathFromRadii, waveRadii } from '../../lib/shapes'
  import { app, player, startWave, toggle, tuneWave, WAVE } from '../../lib/state.svelte'
  import Icon from '../Icon.svelte'
  import LoadingIndicator from '../LoadingIndicator.svelte'

  let { size = 'hero' }: { size?: 'hero' } = $props()
  let starting = $state(false)
  const mood = $derived(WAVE.moods.find((m) => m.seed === app.waveMood))
  const shape = $derived(pathFromRadii(waveRadii(mood?.lobes ?? 8, mood?.depth ?? 0.13)))
  const orbitShape = $derived(pathFromRadii(waveRadii(mood?.lobes ?? 8, Math.min((mood?.depth ?? 0.13) + 0.08, 0.26))))
  const on = $derived(player.wave && player.playing)

  async function wave() {
    if (starting) return
    if (player.wave) return toggle()
    starting = true
    try { await startWave() } finally { starting = false }
  }
  // A picked chip picked again lets the wave go back to untuned.
  const pick = (key: 'mood' | 'mode', seed: string, current: string | null) => tuneWave(key, current === seed ? null : seed)
</script>

<section class="wave glass rise {size}" class:playing={on} aria-label={tr('Моя волна')}>
  <span class="glow" aria-hidden="true"></span>

  <header class="copy">
    <h2 class="display">{tr('Моя волна')}</h2>
    <p class="about">{tr('Бесконечный поток музыки, который подстраивается под тебя')}</p>
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
    {#each [{ key: 'mood' as const, label: tr('Настроение'), options: WAVE.moods, value: app.waveMood }, { key: 'mode' as const, label: tr('Знакомство'), options: WAVE.modes, value: app.waveMode }] as group (group.key)}
      <div class="tuning-row">
        <span class="label">{group.label}</span>
        <div class="chips" role="radiogroup" aria-label={group.label}>
          {#each group.options as m (m.seed)}
            {@const picked = group.value === m.seed}
            <button class="chip" class:picked role="radio" aria-checked={picked} onclick={() => pick(group.key, m.seed, group.value)}>{m.title}</button>
          {/each}
        </div>
      </div>
    {/each}
  </div>
</section>

<style>
  .wave {
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

  /* One soft light of the accent behind the shape, a little brighter while the wave plays. */
  .glow {
    position: absolute;
    z-index: -1;
    width: 60%;
    aspect-ratio: 1;
    right: -16%;
    top: 50%;
    translate: 0 -50%;
    border-radius: 50%;
    background: var(--primary);
    -webkit-mask-image: radial-gradient(closest-side, #000 0%, #0007 45%, transparent 100%);
    mask-image: radial-gradient(closest-side, #000 0%, #0007 45%, transparent 100%);
    opacity: 0.12;
    pointer-events: none;
    transition: opacity var(--d-slow) var(--ease-emph);
    animation: breathe 9s ease-in-out infinite alternate;
  }
  .playing .glow {
    opacity: 0.2;
  }
  @keyframes breathe {
    to {
      transform: scale(1.12);
    }
  }

  /* ---- What it is ---- */
  .copy {
    grid-area: copy;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 12px;
  }
  h2 {
    font-size: clamp(40px, 5.4cqw, 76px);
    line-height: 1;
    margin: 0;
    overflow-wrap: anywhere;
  }
  .about {
    max-width: 36ch;
    font-size: 15.5px;
    color: var(--on-surface-variant);
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
  .orbit { position: absolute; inset: 0; width: 100%; height: 100%; fill: var(--primary); opacity: 0.08; animation: wave-turn 60s linear infinite reverse; pointer-events: none; transition: opacity var(--d-slow) var(--ease-emph); }
  .playing .orbit { opacity: 0.14; }
  .orbit > path { transition: d var(--d-slow) var(--spring); }
  .play { width: 70%; aspect-ratio: 1; position: relative; display: grid; place-items: center; color: var(--on-primary); border-radius: 50%; transition: transform var(--d-spatial) var(--spring); }
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

  /* ---- Tuning: tonal chips; the picked one in the accent, squarer, as a toggle that is on ---- */
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
    font-size: 12px;
    font-weight: 800;
    letter-spacing: 0.1em;
    text-transform: uppercase;
    color: var(--on-surface-variant);
  }
  .chips {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
  }
  .chip {
    height: 40px;
    padding: 0 18px;
    border-radius: var(--r-full);
    background: color-mix(in oklab, var(--on-surface) 9%, transparent);
    color: var(--on-surface);
    font-size: 14px;
    font-weight: 650;
    white-space: nowrap;
    transition:
      background-color 300ms var(--ease-emph),
      color 300ms var(--ease-emph),
      transform var(--d-fast) var(--spring-fast);
  }
  .chip:hover {
    background: color-mix(in oklab, var(--on-surface) 14%, transparent);
  }
  .chip:active {
    border-radius: var(--r-s);
    transform: scale(0.95);
  }
  .chip.picked {
    border-radius: var(--r-s);
    background: var(--primary);
    color: var(--on-primary);
  }
  .chip.picked:hover {
    background: color-mix(in oklab, var(--primary) 90%, var(--on-primary));
  }
  .chip.picked:active {
    border-radius: var(--r-full);
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
    .glow {
      width: 110%;
      right: -5%;
      top: 45%;
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
    .about {
      font-size: 14.5px;
    }
    .chip {
      height: 38px;
      padding: 0 16px;
    }
  }
  @media (prefers-reduced-motion: reduce) {
    .orbit, .play-face > svg, .glow { animation: none; }
    .play, .play-face > svg > path, .orbit > path { transition: none; }
  }
</style>
