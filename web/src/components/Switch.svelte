<script lang="ts">
  import Icon from './Icon.svelte'
  let { checked, onchange, label }: { checked: boolean; onchange: (v: boolean) => void; label: string } = $props()
</script>

<button class="switch" class:on={checked} role="switch" aria-checked={checked} aria-label={label} onclick={() => onchange(!checked)}>
  <span class="handle">
    {#if checked}<Icon name="check" size={16} weight={700} />{/if}
  </span>
</button>

<style>
  .switch {
    width: 52px;
    height: 32px;
    flex: none;
    border-radius: 16px;
    background: var(--surface-container-highest);
    border: 2px solid var(--outline);
    position: relative;
    transition:
      background-color 250ms var(--ease-emph),
      border-color 250ms var(--ease-emph);
  }
  .handle {
    position: absolute;
    top: 50%;
    left: 6px;
    width: 16px;
    height: 16px;
    translate: 0 -50%;
    border-radius: 50%;
    background: var(--outline);
    display: grid;
    place-items: center;
    transition:
      left var(--d-spatial) var(--spring-fast),
      width var(--d-spatial) var(--spring-fast),
      height var(--d-spatial) var(--spring-fast),
      background-color 250ms var(--ease-emph);
  }
  .switch:active .handle {
    width: 28px;
    height: 28px;
    left: 0;
  }
  .on {
    background: var(--primary);
    border-color: var(--primary);
  }
  .on .handle {
    left: 22px;
    width: 24px;
    height: 24px;
    background: var(--on-primary);
    color: var(--primary);
  }
  .on:active .handle {
    left: 20px;
    width: 28px;
    height: 28px;
  }
</style>
