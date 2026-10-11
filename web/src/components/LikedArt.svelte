<script lang="ts">
  // The cover of a set of likes: what is in it, not a picture made up for it — the four latest
  // liked covers in a grid, or the latest one alone while there are fewer, with a heart in an
  // Expressive shape in the corner. Under the pointer the tiles part and the heart turns.
  import { tracks } from '../lib/catalog'
  import { shapePath } from '../lib/shapes'
  import Cover from './Cover.svelte'
  import Icon from './Icon.svelte'

  let { ids = [], seed, color, badge = true }: { ids?: string[]; seed: string; color: string; badge?: boolean } = $props()

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
  const heart = shapePath('cookie9', 0.96)
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
  {#if badge && covers.length}
    <span class="badge" aria-hidden="true">
      <svg viewBox="0 0 100 100"><path d={heart} /></svg>
      <span class="badge-icon"><Icon name="favorite" fill size={18} /></span>
    </span>
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

  /* The heart in the corner, on a scalloped shape that turns a little under the pointer. */
  .badge {
    position: absolute;
    right: 6cqi;
    bottom: 6cqi;
    width: clamp(26px, 26cqi, 64px);
    aspect-ratio: 1;
    display: grid;
    place-items: center;
    filter: drop-shadow(0 4px 10px color-mix(in oklab, #000 45%, transparent));
  }
  .badge > * {
    grid-area: 1 / 1;
  }
  .badge svg {
    width: 100%;
    height: 100%;
    transition: transform var(--d-slow) var(--spring);
  }
  .badge path {
    fill: var(--primary);
  }
  .badge-icon {
    position: relative;
    display: grid;
    color: var(--on-primary);
  }
  .badge-icon :global(.icon) {
    font-size: clamp(14px, 12cqi, 30px) !important;
  }
  .liked-art:hover .badge svg {
    transform: rotate(40deg) scale(1.08);
  }
</style>
