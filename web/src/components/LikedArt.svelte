<script lang="ts">
  // The cover of a set of likes: what is in it, not a picture made up for it — the four latest
  // liked covers in a grid, or the latest one alone while there are fewer. Under the pointer the
  // tiles part. No heart on it: the word "Любимое" by it says as much.
  import { tracks } from '../lib/catalog'
  import Cover from './Cover.svelte'
  import Icon from './Icon.svelte'

  let { ids = [], seed, color }: { ids?: string[]; seed: string; color: string } = $props()

  // Distinct pictures from the latest tracks: an album liked whole would fill the grid with one.
  const covers = $derived.by(() => {
    const seen = new Set<string>()
    const out: { id: string; cover: string; color: string }[] = []
    for (const id of ids.slice(0, 40)) {
      const t = tracks.get(id)
      if (!t?.cover || seen.has(t.cover)) continue
      seen.add(t.cover)
      out.push({ id: t.id, cover: t.cover, color: t.color })
      if (out.length === 4) break
    }
    return out
  })
</script>

<span class="liked-art" class:grid={covers.length === 4} class:empty={!covers.length} style:--c={color}>
  {#if covers.length === 4}
    {#each covers as c (c.id)}
      <span class="tile"><Cover seed={c.id} color={c.color} src={c.cover} /></span>
    {/each}
  {:else if covers.length}
    <span class="tile one"><Cover seed={covers[0].id} color={covers[0].color} src={covers[0].cover} /></span>
  {:else}
    <span class="blank"><Icon name="favorite" fill size={56} /></span>
  {/if}
</span>

<style>
  .liked-art {
    position: relative;
    display: grid;
    width: 100%;
    height: 100%;
    overflow: hidden;
    background: var(--surface-container-high);
    container-type: inline-size;
  }
  .grid {
    grid-template: 1fr 1fr / 1fr 1fr;
    gap: 2px;
    transition: gap var(--d-fast) var(--spring-fast);
  }
  .tile {
    display: block;
    min-width: 0;
    min-height: 0;
    overflow: hidden;
    transition:
      border-radius var(--d-fast) var(--spring-fast),
      transform var(--d-spatial) var(--spring);
  }
  .tile :global(.cover) {
    transition: transform var(--d-slow) var(--spring);
  }
  .one {
    grid-area: 1 / 1;
  }
  /* A gap in percent: the art's own width (a container unit here would be the page's). */
  .grid:hover {
    gap: 3%;
  }
  .grid:hover .tile {
    border-radius: 18%;
  }
  .liked-art:hover .tile :global(.cover) {
    transform: scale(1.06);
  }
  .blank {
    display: grid;
    place-items: center;
    color: color-mix(in oklab, var(--primary) 85%, var(--on-surface));
    background:
      radial-gradient(circle at 30% 25%, color-mix(in oklab, var(--primary) 30%, transparent), transparent 60%),
      color-mix(in oklab, var(--primary) 12%, var(--surface-container-high));
  }
  .blank :global(.icon) {
    font-size: 34cqi !important;
  }
</style>
