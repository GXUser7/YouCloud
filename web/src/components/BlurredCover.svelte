<script lang="ts">
  // A cover as a blurred wash of its colours, filling its box and bleeding past it as a CSS blur
  // does. The blur is drawn once on a small canvas and the canvas stretched: a blur that wide
  // keeps no detail a small canvas would lose, and the GPU only scales the picture, where a CSS
  // blur of the full-size cover would be worked out again for every frame anything moves.
  import { directFailed, reach } from '../lib/media.svelte'
  import Cover from './Cover.svelte'

  let {
    seed,
    color,
    src = null,
    kind,
    label,
    blur,
    saturate = 1,
  }: { seed: string; color: string; src?: string | null; kind?: string; label?: string; blur: number; saturate?: number } = $props()

  /** Canvas pixels across the box's longer side: plenty for a blur of tens of pixels. */
  const RESOLUTION = 160

  let canvas = $state<HTMLCanvasElement>()
  let width = $state(0)
  let height = $state(0)
  let image = $state<HTMLImageElement | null>(null)
  let failed = $state(false)

  // How far past the box the blur still shows: three of its standard deviations.
  const bleed = $derived(blur * 3)

  $effect(() => {
    const url = reach(src)
    image = null
    failed = false
    if (!url) return
    const img = new Image()
    img.src = url
    // Decoded off the main thread before it is drawn.
    img.decode().then(
      () => reach(src) === url && (image = img),
      () => reach(src) === url && !directFailed(url) && (failed = true),
    )
  })

  $effect(() => {
    if (!canvas || !image || !width || !height) return
    const scale = RESOLUTION / Math.max(width, height)
    const pad = bleed * scale
    const w = Math.max(1, Math.round(width * scale + pad * 2))
    const h = Math.max(1, Math.round(height * scale + pad * 2))
    canvas.width = w
    canvas.height = h
    // A canvas this small blurs in a millisecond on the CPU; on the GPU the blur stalled the first
    // frame of the page for tens of milliseconds while its shaders were readied.
    const ctx = canvas.getContext('2d', { willReadFrequently: true })
    if (!ctx) return
    // The cover fills the box as object-fit: cover does, cut from its middle.
    const boxW = width * scale
    const boxH = height * scale
    const fit = Math.max(boxW / image.naturalWidth, boxH / image.naturalHeight)
    const sw = boxW / fit
    const sh = boxH / fit
    ctx.clearRect(0, 0, w, h)
    ctx.filter = `blur(${blur * scale}px) saturate(${saturate})`
    ctx.drawImage(image, (image.naturalWidth - sw) / 2, (image.naturalHeight - sh) / 2, sw, sh, pad, pad, boxW, boxH)
  })
</script>

<div class="wash" bind:clientWidth={width} bind:clientHeight={height}>
  {#if src && !failed}
    <canvas bind:this={canvas} style:--bleed="{bleed}px"></canvas>
  {:else}
    <!-- No artwork: the drawn cover, blurred the plain way — rare, and only then paid for. -->
    <div class="drawn" style:filter="blur({blur}px) saturate({saturate})"><Cover {seed} {color} {kind} {label} /></div>
  {/if}
</div>

<style>
  .wash {
    position: relative;
    width: 100%;
    height: 100%;
  }
  canvas {
    position: absolute;
    left: calc(var(--bleed) * -1);
    top: calc(var(--bleed) * -1);
    width: calc(100% + var(--bleed) * 2);
    height: calc(100% + var(--bleed) * 2);
  }
  .drawn {
    width: 100%;
    height: 100%;
  }
  .drawn :global(.cover) {
    width: 100%;
    height: 100%;
  }
</style>
