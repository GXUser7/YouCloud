<script lang="ts">
  // The bar's controls as one Expressive button group: like, shuffle, back, play, forward, repeat.
  // A pressed button widens and its neighbours make room, so the group keeps its width; a toggle
  // that is on squares off and takes the theme's tone; play is round while paused and squares
  // while it plays. Every one has its state layer on hover and tightens its corners when pressed.
  import { tr } from '../lib/i18n.svelte'
  import { currentTrack, cycleRepeat, isLiked, like, next, player, prev, toggle, toggleShuffle } from '../lib/state.svelte'
  import Icon from './Icon.svelte'
  import LoadingIndicator from './LoadingIndicator.svelte'

  const t = $derived(currentTrack())
  const liked = $derived(!!t && isLiked(t.id))
  const repeat = $derived(player.repeat)

  // The heart bursts only when the listener likes it here, not when a track that is liked comes.
  let burst = $state(0)
  function heart() {
    if (!t) return
    if (!liked) burst++
    like(t.id)
  }
</script>

<div class="controls" role="group" aria-label={tr('Управление')}>
  <button class="b toggle like" class:on={liked} disabled={!t} onclick={heart} aria-label={tr('Нравится')} aria-pressed={liked} title={liked ? tr('Убрать из любимого') : tr('Нравится')}>
    {#key burst}<span class="ic" class:pop={burst > 0}><Icon name="favorite" fill={liked} size={22} /></span>{/key}
    {#if burst > 0}{#key burst}<span class="ring" aria-hidden="true"></span>{/key}{/if}
  </button>
  <button class="b toggle" class:on={player.shuffle} onclick={toggleShuffle} aria-label={tr('Перемешать')} aria-pressed={player.shuffle} title={tr('Перемешать')}>
    <span class="ic"><Icon name="shuffle" size={22} /></span>
  </button>
  <button class="b skip back" onclick={prev} disabled={!t} aria-label={tr('Предыдущий')} title={tr('Предыдущий')}>
    <span class="ic"><Icon name="skip_previous" fill size={26} /></span>
  </button>
  <button class="b play" class:playing={player.playing} onclick={toggle} disabled={!t} aria-label={player.playing ? tr('Пауза') : tr('Играть')}>
    {#if player.loading && t}
      <span class="ic"><LoadingIndicator size={30} contained={false} /></span>
    {:else}
      {#key player.playing}<span class="ic swap"><Icon name={player.playing ? 'pause' : 'play_arrow'} fill size={28} /></span>{/key}
    {/if}
  </button>
  <button class="b skip forward" onclick={() => next()} disabled={!t} aria-label={tr('Следующий')} title={tr('Следующий')}>
    <span class="ic"><Icon name="skip_next" fill size={26} /></span>
  </button>
  <button
    class="b toggle"
    class:on={repeat !== 'off'}
    onclick={cycleRepeat}
    aria-label={tr('Повтор')}
    aria-pressed={repeat !== 'off'}
    title={repeat === 'one' ? tr('Повтор трека') : repeat === 'all' ? tr('Повтор очереди') : tr('Повтор')}
  >
    {#key repeat === 'one'}<span class="ic swap"><Icon name={repeat === 'one' ? 'repeat_one' : 'repeat'} size={22} /></span>{/key}
  </button>
</div>

<style>
  .controls {
    --w: 40px;
    flex: none;
    display: flex;
    align-items: center;
    gap: 4px;
  }

  .b {
    --base: var(--w);
    --bw: var(--base);
    --r: calc(var(--w) / 2);
    width: var(--bw);
    height: 40px;
    flex: none;
    display: grid;
    place-items: center;
    position: relative;
    isolation: isolate;
    border-radius: var(--r);
    color: var(--on-surface-variant);
    transition:
      width var(--d-fast) var(--spring-fast),
      border-radius var(--d-fast) var(--spring-fast),
      background-color 250ms var(--ease-emph),
      color 250ms var(--ease-emph);
  }
  .b > * {
    grid-area: 1 / 1;
  }
  /* The state layer: a veil of the button's own colour, over its container, under its icon. */
  .b::before {
    content: '';
    position: absolute;
    inset: 0;
    z-index: -1;
    border-radius: inherit;
    background: currentColor;
    opacity: 0;
    transition: opacity 180ms var(--ease-emph);
  }
  .b:hover::before {
    opacity: 0.08;
  }
  .b:active::before {
    opacity: 0.14;
  }
  .b:hover {
    color: var(--on-surface);
  }
  .b:disabled {
    opacity: 0.38;
    pointer-events: none;
  }

  /* Pressed: wider and squarer; the neighbours give it the room, so nothing else in the bar moves. */
  .b:active {
    --bw: calc(var(--base) + 10px);
    --r: 12px;
  }
  .b:has(+ .b:active),
  .b:active + .b {
    --bw: calc(var(--base) - 5px);
  }

  .ic {
    display: grid;
    place-items: center;
    transition: transform var(--d-fast) var(--spring-fast);
  }
  .ic :global(.icon) {
    transition: font-variation-settings 300ms var(--ease-emph);
  }
  .swap {
    animation: swap 260ms var(--ease-in-place);
  }
  @keyframes swap {
    from {
      transform: scale(0.6) rotate(-30deg);
      opacity: 0;
    }
  }

  /* ---- Toggles: like, shuffle, repeat ---- */
  .toggle.on {
    --r: 12px;
    background: color-mix(in oklab, var(--primary) 22%, transparent);
    color: var(--primary);
  }
  .toggle.on:hover {
    color: var(--primary);
  }
  .toggle.on:active {
    --r: calc(var(--w) / 2);
  }
  .like.on {
    background: var(--primary);
    color: var(--on-primary);
  }
  .like.on:hover {
    color: var(--on-primary);
  }
  .pop {
    animation: pop 520ms var(--ease-in-place);
  }
  @keyframes pop {
    0% {
      transform: scale(0.5);
    }
    45% {
      transform: scale(1.28);
    }
  }
  .ring {
    position: absolute;
    inset: 4px;
    border-radius: 50%;
    border: 2px solid var(--primary);
    pointer-events: none;
    animation: ring 600ms var(--ease-in-place) forwards;
  }
  @keyframes ring {
    from {
      transform: scale(0.4);
      opacity: 1;
    }
    to {
      transform: scale(1.7);
      opacity: 0;
      border-width: 0.5px;
    }
  }

  /* ---- Back and forward: the arrow leans the way it goes ---- */
  .skip {
    color: var(--on-surface);
  }
  .back:hover .ic {
    transform: translateX(-2px);
  }
  .forward:hover .ic {
    transform: translateX(2px);
  }
  .back:active .ic {
    transform: translateX(-5px) scale(0.92);
  }
  .forward:active .ic {
    transform: translateX(5px) scale(0.92);
  }

  /* ---- Play: round while paused, squared while it plays ---- */
  .play {
    --base: 56px;
    --r: 28px;
    height: 48px;
    margin: 0 4px;
    background: var(--primary);
    color: var(--on-primary);
    box-shadow: 0 8px 22px -10px color-mix(in oklab, var(--primary) 80%, transparent);
    transition:
      width var(--d-fast) var(--spring-fast),
      border-radius var(--d-spatial) var(--spring),
      background-color 400ms var(--ease-emph),
      box-shadow 300ms var(--ease-emph),
      transform var(--d-fast) var(--spring-fast);
  }
  .play.playing {
    --r: 16px;
  }
  .play:hover {
    color: var(--on-primary);
    box-shadow: 0 10px 28px -8px color-mix(in oklab, var(--primary) 95%, transparent);
  }
  .play:active {
    --r: 12px;
  }
  .play :global(.loader) {
    color: var(--on-primary);
  }
  .play :global(.loader path) {
    fill: var(--on-primary);
  }

  @media (max-width: 980px) {
    .toggle:not(.like) {
      display: none;
    }
  }
</style>
