<script lang="ts">
  import { tr } from '../lib/i18n.svelte'
  import Icon from './Icon.svelte'
  import LoadingIndicator from './LoadingIndicator.svelte'

  let { loading = false, error = '', onclick }: { loading?: boolean; error?: string; onclick: () => void } = $props()
</script>

<div class="more" aria-busy={loading}>
  {#if error}<p class="muted" role="status">{tr('Не загрузилось')} · {error}</p>{/if}
  <button disabled={loading} {onclick}>
    {#if loading}
      <LoadingIndicator size={28} contained={false} />
      <span>{tr('Загрузка')}</span>
    {:else}
      <Icon name={error ? 'refresh' : 'expand_more'} size={22} />
      <span>{error ? tr('Повторить') : tr('Показать ещё')}</span>
    {/if}
  </button>
</div>

<style>
  .more {
    padding: 4px;
  }
  p {
    padding: 12px 18px 4px;
    text-align: center;
    font-size: 13px;
    overflow-wrap: anywhere;
  }
  button {
    width: 100%;
    min-height: 56px;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 10px;
    padding: 12px 18px;
    border-radius: var(--r-l);
    color: var(--primary);
    font-weight: 650;
    transition:
      background-color var(--d-fast),
      border-radius var(--d-spatial) var(--spring-fast),
      transform var(--d-spatial) var(--spring-fast);
  }
  button:hover:not(:disabled) {
    background: color-mix(in oklab, var(--primary) 8%, transparent);
  }
  button:active:not(:disabled) {
    transform: scale(0.98);
    border-radius: var(--r-xs);
  }
  button:disabled {
    cursor: wait;
  }
</style>
