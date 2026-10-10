<script lang="ts">
  import LoadingIndicator from './LoadingIndicator.svelte'
  let { text = '', tall = true }: { text?: string; tall?: boolean } = $props()
</script>

<div class="loading" class:tall>
  <LoadingIndicator size={64} />
  {#if text}<p class="muted">{text}</p>{/if}
</div>

<style>
  .loading {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 16px;
    padding: 40px 0;
  }
  .tall {
    min-height: 46vh;
  }
</style>
