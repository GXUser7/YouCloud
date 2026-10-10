<script lang="ts">
  // A YouCloud profile picture: the photo when one is set, otherwise the profile's colour and the
  // first letter of its name, as the app draws one. [live] rings it while the friend is playing.
  let {
    name,
    color,
    size = 44,
    live = false,
    src = null,
  }: { name: string; color?: string | null; size?: number; live?: boolean; src?: string | null } = $props()

  import { directFailed, reach } from '../lib/media.svelte'

  let broken = $state<string | null>(null)
  const shown = $derived(reach(src))
</script>

<span class="avatar" class:live style:--size="{size}px" style:--c={color || '#9CC2A2'}>
  {#if shown && broken !== shown}
    <img class="face" src={shown} alt="" onerror={() => directFailed(shown) || (broken = shown)} />
  {:else}
    <span class="face">{name.slice(0, 1).toUpperCase()}</span>
  {/if}
</span>

<style>
  .avatar {
    position: relative;
    width: var(--size);
    height: var(--size);
    flex: none;
    display: inline-grid;
    place-items: center;
  }
  .face {
    width: 100%;
    height: 100%;
    border-radius: 50%;
    display: grid;
    place-items: center;
    background: var(--c);
    color: color-mix(in oklab, var(--c) 25%, #140d08);
    font-family: var(--font-display);
    font-weight: 750;
    font-size: calc(var(--size) * 0.42);
    object-fit: cover;
    transition: border-radius var(--d-spatial) var(--spring);
  }
  .live .face {
    border-radius: 34%;
  }
  .live::before {
    content: '';
    position: absolute;
    inset: -4px;
    border-radius: 40%;
    border: 2px solid var(--primary);
    animation: spin 8s linear infinite;
    border-style: dashed;
    opacity: 0.85;
  }
  @keyframes spin {
    to {
      transform: rotate(360deg);
    }
  }
</style>
