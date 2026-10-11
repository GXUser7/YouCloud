<script lang="ts">
  // Yandex's «Моя волна» in the new homes: its shape — the mood's lobes, turning while it plays —
  // and play, at three sizes: the big tile with the mood and the mode to pick ('big'), a card
  // beside the likes with the mood ('card'), a quick tile ('tile').
  import { tr } from '../../lib/i18n.svelte'
  import { artistNames } from '../../lib/catalog'
  import { pathFromRadii, waveRadii } from '../../lib/shapes'
  import { app, currentTrack, player, startWave, toggle, tuneWave, WAVE } from '../../lib/state.svelte'
  import ButtonGroup from '../ButtonGroup.svelte'
  import Icon from '../Icon.svelte'

  let { size }: { size: 'big' | 'card' | 'tile' } = $props()

  const mood = $derived(WAVE.moods.find((m) => m.seed === app.waveMood))
  const shape = $derived(pathFromRadii(waveRadii(mood?.lobes ?? 10, mood?.depth ?? 0.07)))
  const inner = $derived(pathFromRadii(waveRadii(mood?.lobes ?? 10, (mood?.depth ?? 0.07) * 1.4), 0.74, 180 / (mood?.lobes ?? 10)))
  const t = $derived(currentTrack())
  let starting = $state(false)
  const on = $derived((player.wave && player.playing) || starting)

  async function wave() {
    if (player.wave) {
      toggle()
      return
    }
    starting = true
    await startWave()
    starting = false
  }
</script>

{#snippet form(px: number)}
  <button class="form" class:on style:--px="{px}px" onclick={wave} aria-label={player.wave && player.playing ? tr('Пауза') : tr('Включить волну')}>
    <span class="spin">
      <svg viewBox="0 0 100 100" aria-hidden="true"><path class="outer" d={shape} /><path class="inner" d={inner} /></svg>
    </span>
    <span class="glyph"><Icon name={player.wave && player.playing ? 'pause' : 'play_arrow'} fill size={Math.round(px * 0.3)} /></span>
  </button>
{/snippet}

{#if size === 'big'}
  <div class="big">
    {@render form(220)}
    <div class="big-text">
      <span class="overline">{tr('Яндекс Музыка')}</span>
      <h2 class="display">{tr('Моя волна')}</h2>
      <p class="muted now">{player.wave && t ? `${t.title} — ${artistNames(t)}` : starting ? tr('Волна подбирает треки…') : tr('Подстраивается под «Нравится» и пропуски')}</p>
      <ButtonGroup options={WAVE.moods.map((m) => ({ value: m.seed, label: m.title }))} value={app.waveMood} allowNone size="s" onchange={(v) => tuneWave('mood', v)} />
      <ButtonGroup options={WAVE.modes.map((m) => ({ value: m.seed, label: m.title }))} value={app.waveMode} allowNone size="s" onchange={(v) => tuneWave('mode', v)} />
    </div>
  </div>
{:else if size === 'card'}
  <div class="card">
    {@render form(132)}
    <div class="card-text">
      <span class="overline">{tr('Моя волна')}</span>
      <h3 class="headline">{player.wave && t ? t.title : (mood?.title ?? tr('Под тебя'))}</h3>
      <ButtonGroup options={WAVE.moods.map((m) => ({ value: m.seed, label: m.title }))} value={app.waveMood} allowNone size="s" onchange={(v) => tuneWave('mood', v)} />
    </div>
  </div>
{:else}
  <button class="tile" onclick={wave}>
    <span class="tile-form" class:on>
      <span class="spin"><svg viewBox="0 0 100 100" aria-hidden="true"><path class="outer" d={shape} /></svg></span>
      <span class="glyph"><Icon name={player.wave && player.playing ? 'pause' : 'waves'} fill size={26} /></span>
    </span>
    <span class="tile-text">
      <span class="ellipsis title">{tr('Моя волна')}</span>
      <span class="ellipsis sub">{player.wave && t ? t.title : (mood?.title ?? tr('Под твой вкус'))}</span>
    </span>
  </button>
{/if}

<style>
  /* The shape: the theme's colour, turning slowly while the wave plays; the corners of the lobes
     deepen under the pointer. */
  .form {
    width: var(--px);
    height: var(--px);
    flex: none;
    display: grid;
    place-items: center;
    color: var(--on-primary);
    transition: transform var(--d-fast) var(--spring-fast);
  }
  .form > *,
  .tile-form > * {
    grid-area: 1 / 1;
  }
  .form:hover {
    transform: scale(1.04);
  }
  .form:active {
    transform: scale(0.94);
  }
  .spin {
    width: 100%;
    height: 100%;
    display: block;
    animation: turn 30s linear infinite;
    animation-play-state: paused;
    will-change: transform;
  }
  .on .spin {
    animation-play-state: running;
  }
  svg {
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

  .big {
    height: 100%;
    display: flex;
    align-items: center;
    gap: 32px;
    padding: 28px 32px;
  }
  .big-text {
    min-width: 0;
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
  }
  .big-text h2 {
    font-size: clamp(32px, 3.4vw, 52px);
  }
  .now {
    margin-bottom: 8px;
    max-width: 40ch;
  }

  .card {
    display: flex;
    align-items: center;
    gap: 20px;
    height: 100%;
  }
  .card-text {
    min-width: 0;
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }
  .card-text h3 {
    font-size: 22px;
  }

  .tile {
    width: 100%;
    height: 100%;
    display: flex;
    align-items: center;
    gap: 14px;
    padding-left: 10px;
    text-align: left;
  }
  .tile-form {
    width: 64px;
    height: 64px;
    flex: none;
    display: grid;
    place-items: center;
    color: var(--on-primary);
  }
  .tile:hover .tile-form .spin {
    animation-play-state: running;
  }
  .tile-text {
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 2px;
  }
  .title {
    font-weight: 750;
    font-size: 15.5px;
  }
  .sub {
    font-size: 13px;
    color: var(--on-surface-variant);
  }
</style>
