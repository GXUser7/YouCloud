<script lang="ts">
  // YouCloud's icon in the theme's colours: the tile in the primary colour, the cloud a shade
  // deeper, so it follows the cover playing like the rest of the page.
  import { ICON_CLOUD, ICON_CUTOUT, ICON_TRANSFORM, ICON_VIEWBOX } from '../lib/appIcon'

  let { size = 48 }: { size?: number } = $props()
</script>

<svg class="app-icon" width={size} height={size} viewBox={ICON_VIEWBOX} aria-hidden="true">
  <rect class="tile" x="10" y="10" width="88" height="88" rx="26" />
  <g transform={ICON_TRANSFORM}>
    <path class="cloud" d={ICON_CLOUD} />
    <path class="tile" d={ICON_CUTOUT} />
  </g>
</svg>

<style>
  .app-icon {
    display: block;
  }
  .tile {
    fill: var(--primary);
  }
  .cloud {
    fill: color-mix(in oklab, var(--primary) 58%, var(--on-primary));
  }
</style>
