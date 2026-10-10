<script lang="ts">
  import type { Snippet } from 'svelte'
  let { title, subtitle = '', actions }: { title: string; subtitle?: string; actions?: Snippet } = $props()
</script>

<header class="page-header rise">
  <div>
    <h1 class="display">{title}</h1>
    {#if subtitle}<p class="sub">{subtitle}</p>{/if}
  </div>
  {#if actions}<div class="actions">{@render actions()}</div>{/if}
</header>

<style>
  .page-header {
    display: flex;
    align-items: flex-end;
    justify-content: space-between;
    gap: 24px;
    flex-wrap: wrap;
    padding-top: 8px;
  }
  h1 {
    font-size: clamp(34px, 3.4vw, 50px);
  }
  .sub {
    margin-top: 10px;
    font-size: 17px;
    color: var(--on-surface-variant);
  }
  .actions {
    display: flex;
    align-items: center;
    gap: 10px;
  }
</style>
