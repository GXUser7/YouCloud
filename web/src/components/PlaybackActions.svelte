<script lang="ts">
  import { tr } from '../lib/i18n.svelte'
  import Icon from './Icon.svelte'
  import LoadingIndicator from './LoadingIndicator.svelte'

  let {
    playing = false,
    loading = false,
    disabled = false,
    onplay,
    onshuffle,
  }: {
    playing?: boolean
    loading?: boolean
    disabled?: boolean
    onplay: () => void
    onshuffle: () => void
  } = $props()
</script>

<div class="playback-actions" role="group" aria-label={tr('Управление')}>
  <button class="btn filled listen" class:playing disabled={disabled || loading} onclick={onplay}>
    {#if loading}
      <LoadingIndicator size={24} contained={false} />
    {:else}
      <Icon name={playing ? 'pause' : 'play_arrow'} fill />
    {/if}
    {playing ? tr('Пауза') : tr('Слушать')}
  </button>
  <button class="icon-btn tonal shuffle" disabled={disabled || loading} onclick={onshuffle} aria-label={tr('Перемешать')} title={tr('Перемешать')}>
    <Icon name="shuffle" />
  </button>
</div>

<style>
  .playback-actions {
    display: flex;
    flex: none;
    gap: 3px;
  }
  .listen {
    --h: 60px;
    --r: 30px 10px 10px 30px;
    padding: 0 28px 0 24px;
    font-size: 17px;
  }
  .listen.playing {
    --r: 20px 10px 10px 20px;
  }
  .listen :global(.loader path) {
    fill: currentColor;
  }
  .shuffle {
    --s: 60px;
    --r: 10px 30px 30px 10px;
  }
  .shuffle:disabled {
    opacity: 0.38;
    pointer-events: none;
  }
</style>
