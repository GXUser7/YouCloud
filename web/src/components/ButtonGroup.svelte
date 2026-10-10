<script lang="ts" generics="T extends string">
  // A Material 3 Expressive connected button group: segments a few pixels apart, the outer
  // corners round, the inner ones tight; the selected segment springs into a full pill.
  import type { Snippet } from 'svelte'

  let {
    options,
    value,
    onchange,
    size = 'm',
    item,
    stretch = false,
    allowNone = false,
  }: {
    options: { value: T; label: string }[]
    value: T | null
    onchange: (value: T | null) => void
    size?: 's' | 'm' | 'l'
    item?: Snippet<[{ value: T; label: string }, boolean]>
    stretch?: boolean
    allowNone?: boolean
  } = $props()
</script>

<div class="group {size}" class:stretch role="radiogroup">
  {#each options as option (option.value)}
    {@const selected = option.value === value}
    <button
      class="segment"
      class:selected
      role="radio"
      aria-checked={selected}
      onclick={() => onchange(selected && allowNone ? null : option.value)}
    >
      {#if item}{@render item(option, selected)}{:else}{option.label}{/if}
    </button>
  {/each}
</div>

<style>
  .group {
    display: inline-flex;
    gap: 3px;
    --h: 44px;
  }
  .group.s {
    --h: 36px;
  }
  .group.l {
    --h: 56px;
  }
  .stretch {
    display: flex;
  }
  .stretch .segment {
    flex: 1;
  }
  .segment {
    height: var(--h);
    padding: 0 18px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    font-weight: 650;
    font-size: 14px;
    white-space: nowrap;
    background: color-mix(in oklab, var(--on-surface) 9%, transparent);
    color: var(--on-surface);
    border-radius: var(--r-xs);
    position: relative;
    transition:
      border-radius var(--d-spatial) var(--spring-fast),
      background-color 250ms var(--ease-emph),
      color 250ms var(--ease-emph),
      padding var(--d-spatial) var(--spring);
  }
  .s .segment {
    padding: 0 14px;
    font-size: 13px;
  }
  .l .segment {
    padding: 0 24px;
    font-size: 15px;
  }
  .segment:first-child {
    border-top-left-radius: var(--r-full);
    border-bottom-left-radius: var(--r-full);
  }
  .segment:last-child {
    border-top-right-radius: var(--r-full);
    border-bottom-right-radius: var(--r-full);
  }
  .segment:hover {
    background: color-mix(in oklab, var(--on-surface) 14%, transparent);
  }
  .segment:active {
    border-radius: var(--r-xs);
  }
  .segment.selected {
    background: var(--primary);
    color: var(--on-primary);
    border-radius: var(--r-full);
    padding-inline: 24px;
  }
  .s .segment.selected {
    padding-inline: 18px;
  }
</style>
