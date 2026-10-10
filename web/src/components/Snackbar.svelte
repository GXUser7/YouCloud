<script lang="ts">
  import { toasts } from '../lib/toast.svelte'
</script>

<div class="stack" aria-live="polite">
  {#each toasts as t (t.id)}
    <div class="snack">{t.text}</div>
  {/each}
</div>

<style>
  .stack {
    position: fixed;
    left: 50%;
    bottom: 128px;
    translate: -50% 0;
    z-index: 40;
    display: flex;
    flex-direction: column;
    gap: 8px;
    align-items: center;
    pointer-events: none;
  }
  .snack {
    max-width: min(560px, 90vw);
    padding: 14px 20px;
    border-radius: var(--r-m);
    background: var(--inverse-surface);
    color: var(--inverse-on-surface);
    font-weight: 550;
    font-size: 14px;
    box-shadow: var(--shadow);
    animation: in var(--d-spatial) var(--spring-fast);
  }
  @keyframes in {
    from {
      opacity: 0;
      transform: translateY(16px) scale(0.92);
    }
  }
</style>
