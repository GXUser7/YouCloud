<script lang="ts">
  import { tr } from '../../lib/i18n.svelte'
  import { pathFromRadii, shapePath, waveRadii } from '../../lib/shapes'
  import { app, player, startWave, toggle, tuneWave, WAVE } from '../../lib/state.svelte'
  import ButtonGroup from '../ButtonGroup.svelte'
  import Icon from '../Icon.svelte'
  import LoadingIndicator from '../LoadingIndicator.svelte'

  let { size = 'hero' }: { size?: 'hero' } = $props()
  let starting = $state(false)
  const mood = $derived(WAVE.moods.find((m) => m.seed === app.waveMood))
  const shape = $derived(pathFromRadii(waveRadii(mood?.lobes ?? 8, mood?.depth ?? 0.13)))
  const on = $derived(player.wave && player.playing)
  async function wave() {
    if (starting) return
    if (player.wave) return toggle()
    starting = true
    try { await startWave() } finally { starting = false }
  }
</script>

<section class="wave glass rise {size}" class:playing={on} aria-label={tr('Моя волна')}>
  <div class="copy">
    <h2 class="display">{tr('Моя волна')}</h2>
  </div>
  <div class="invitation">
    <svg class="orbit" viewBox="0 0 100 100" aria-hidden="true"><path d={shapePath('sunny')} /></svg>
    <button class="play" onclick={wave} disabled={starting} aria-busy={starting} aria-label={on ? tr('Пауза') : tr('Включить волну')}>
      <svg viewBox="0 0 100 100" aria-hidden="true"><path d={shape} /></svg>
      <span class="glyph">{#if starting}<LoadingIndicator size={40} contained={false} />{:else}<Icon name={on ? 'pause' : 'play_arrow'} fill size={48} />{/if}</span>
    </button>
    <span class="sr-only" role="status">{starting ? tr('Волна подбирает треки…') : on ? tr('Сейчас играет') : ''}</span>
  </div>
  <div class="tuning">
    <div class="tuning-row">
      <span class="overline">{tr('Настроение')}</span>
      <ButtonGroup variant="category" label={tr('Настроение')} options={WAVE.moods.map((m) => ({ value: m.seed, label: m.title }))} value={app.waveMood} allowNone onchange={(v) => tuneWave('mood', v)} />
    </div>
    <div class="tuning-row">
      <span class="overline">{tr('Знакомство')}</span>
      <ButtonGroup variant="category" label={tr('Знакомство')} options={WAVE.modes.map((m) => ({ value: m.seed, label: m.title }))} value={app.waveMode} allowNone onchange={(v) => tuneWave('mode', v)} />
    </div>
  </div>
</section>

<style>
  .wave {
    position: relative;
    display: grid;
    grid-template-columns: minmax(0, 1fr) 240px;
    gap: 32px;
    padding: clamp(24px, 3.2cqw, 48px);
    margin-top: 24px;
    border-radius: var(--r-2xl);
    overflow: clip;
  }
  .copy { min-width: 0; align-self: center; }
  h2 { font-size: clamp(36px, 5.6cqw, 76px); line-height: 1.12; margin: 0; overflow-wrap: anywhere; }
  .invitation { position: relative; grid-column: 2; grid-row: 1 / 3; display: grid; place-items: center; min-height: 240px; }
  .orbit { position: absolute; width: 240px; height: 240px; fill: var(--primary); opacity: 0.08; animation: orbit-idle 18s ease-in-out infinite; pointer-events: none; }
  .play { width: 144px; height: 144px; position: relative; display: grid; place-items: center; color: var(--on-primary); border-radius: 50%; transition: transform var(--d-spatial) var(--spring); }
  .play > svg { position: absolute; inset: 0; width: 100%; height: 100%; fill: var(--primary); overflow: visible; animation: play-idle 6s ease-in-out infinite; pointer-events: none; }
  .play > svg > path { transform-origin: 50px 50px; transition: rotate var(--d-spatial) var(--spring), d var(--d-slow) var(--spring), fill 220ms var(--ease-emph); }
  .play:enabled:hover, .play:focus-visible { transform: translateY(-3px) scale(1.09); }
  .play:enabled:hover > svg > path, .play:focus-visible > svg > path { rotate: 24deg; fill: color-mix(in oklab, var(--primary) 88%, var(--on-surface)); }
  .play:enabled:active { transform: scale(0.94); }
  .playing .play > svg > path { rotate: 22deg; }
  .glyph { position: relative; display: grid; place-items: center; transition: transform var(--d-spatial) var(--spring); }
  .play:enabled:hover .glyph { transform: scale(1.06); }
  .sr-only { position: absolute; width: 1px; height: 1px; padding: 0; overflow: hidden; clip-path: inset(50%); white-space: nowrap; }
  @keyframes play-idle {
    0%, 100% { transform: translateY(0) rotate(-6deg) scale(0.98); }
    50% { transform: translateY(-5px) rotate(8deg) scale(1.03); }
  }
  @keyframes orbit-idle {
    0%, 100% { transform: rotate(0deg) scale(0.96); }
    50% { transform: rotate(-24deg) scale(1.04); }
  }
  .tuning { display: flex; flex-wrap: wrap; gap: 24px; min-width: 0; align-self: end; }
  .tuning-row { display: flex; flex-direction: column; gap: 10px; min-width: 0; max-width: 100%; }
  .tuning-row .overline { font-size: 11px; letter-spacing: 0.1em; }
  @container page (max-width: 1000px) {
    .wave { grid-template-columns: minmax(0, 1fr) 180px; gap: 24px; }
    .orbit { width: 180px; height: 180px; }
    .play { width: 128px; height: 128px; }
  }
  @container page (max-width: 600px) {
    .wave { grid-template-columns: minmax(0, 1fr); gap: 24px; padding: 24px; }
    .invitation { grid-column: 1; grid-row: 2; min-height: 190px; }
    .tuning { flex-direction: column; gap: 20px; }
    h2 { font-size: clamp(32px, 10cqw, 52px); }
  }
  @media (prefers-reduced-motion: reduce) {
    .orbit, .play > svg { animation: none; }
    .play, .play > svg > path, .glyph { transition: none; }
  }
</style>
