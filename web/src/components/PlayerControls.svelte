<script lang="ts">
  // The player's controls as one Expressive button group (app.css, .xgroup): the bar's, with like,
  // shuffle, back, play, forward and repeat; the full player's, large and toned on the cover's
  // colours, with the like, back, play, forward and shuffle. While «Моя волна» or a radio plays,
  // shuffle gives way to «Не нравится», as in the app: there is no order there to shuffle.
  import { tr } from '../lib/i18n.svelte'
  import { currentTrack, cycleRepeat, dislike, isLiked, like, next, player, prev, toggle, toggleShuffle } from '../lib/state.svelte'
  import Icon from './Icon.svelte'
  import LoadingIndicator from './LoadingIndicator.svelte'

  type Item = 'like' | 'shuffle' | 'prev' | 'play' | 'next' | 'repeat'

  let {
    size = 'm',
    items = ['like', 'shuffle', 'prev', 'play', 'next', 'repeat'],
    tonal = false,
  }: { size?: 'm' | 'xl'; items?: Item[]; tonal?: boolean } = $props()

  const t = $derived(currentTrack())
  const liked = $derived(!!t && isLiked(t.id))
  const repeat = $derived(player.repeat)
  const px = $derived(size === 'xl' ? { icon: 30, skip: 38, play: 48, loader: 56 } : { icon: 22, skip: 26, play: 28, loader: 30 })

  // The heart bursts only when the listener likes it here, not when a track that is liked comes.
  let burst = $state(0)
  function heart() {
    if (!t) return
    if (!liked) burst++
    like(t.id)
  }
</script>

<div class="xgroup controls" class:xl={size === 'xl'} role="group" aria-label={tr('Управление')}>
  {#each items as item (item)}
    {#if item === 'like'}
      <button class="xb toggle like" class:tonal class:on={liked} disabled={!t} onclick={heart} aria-label={tr('Нравится')} aria-pressed={liked} title={liked ? tr('Убрать из любимого') : tr('Нравится')}>
        {#key burst}<span class="ic" class:pop={burst > 0}><Icon name="favorite" fill={liked} size={px.icon} /></span>{/key}
        {#if burst > 0}{#key burst}<span class="ring" aria-hidden="true"></span>{/key}{/if}
      </button>
    {:else if item === 'shuffle' && player.radio}
      <button class="xb opt" class:tonal onclick={dislike} disabled={!t} aria-label={tr('Не нравится')} title={tr('Не нравится')}>
        <span class="ic swap"><Icon name="heart_broken" size={px.icon} /></span>
      </button>
    {:else if item === 'shuffle'}
      <button class="xb toggle opt" class:tonal class:on={player.shuffle} onclick={toggleShuffle} aria-label={tr('Перемешать')} aria-pressed={player.shuffle} title={tr('Перемешать')}>
        <span class="ic"><Icon name="shuffle" size={px.icon} /></span>
      </button>
    {:else if item === 'prev'}
      <button class="xb skip back" class:tonal onclick={prev} disabled={!t} aria-label={tr('Предыдущий')} title={tr('Предыдущий')}>
        <span class="ic"><Icon name="skip_previous" fill size={px.skip} /></span>
      </button>
    {:else if item === 'play'}
      <button class="xb play" class:playing={player.playing} onclick={toggle} disabled={!t} aria-label={player.playing ? tr('Пауза') : tr('Играть')}>
        {#if player.loading && t}
          <span class="ic"><LoadingIndicator size={px.loader} contained={false} /></span>
        {:else}
          {#key player.playing}<span class="ic swap"><Icon name={player.playing ? 'pause' : 'play_arrow'} fill size={px.play} /></span>{/key}
        {/if}
      </button>
    {:else if item === 'next'}
      <button class="xb skip forward" class:tonal onclick={() => next()} disabled={!t} aria-label={tr('Следующий')} title={tr('Следующий')}>
        <span class="ic"><Icon name="skip_next" fill size={px.skip} /></span>
      </button>
    {:else}
      <button
        class="xb toggle opt"
        class:tonal
        class:on={repeat !== 'off'}
        onclick={cycleRepeat}
        aria-label={tr('Повтор')}
        aria-pressed={repeat !== 'off'}
        title={repeat === 'one' ? tr('Повтор трека') : repeat === 'all' ? tr('Повтор очереди') : tr('Повтор')}
      >
        {#key repeat === 'one'}<span class="ic swap"><Icon name={repeat === 'one' ? 'repeat_one' : 'repeat'} size={px.icon} /></span>{/key}
      </button>
    {/if}
  {/each}
</div>

<style>
  /* In the bar (PlayerBar's container), narrow: shuffle and repeat step aside for the progress;
     narrowest, the like too, for back, play and forward. */
  @container bar (max-width: 960px) {
    .controls:not(.xl) .opt {
      display: none;
    }
  }
  @container bar (max-width: 420px) {
    .controls:not(.xl) .like {
      display: none;
    }
  }
</style>
