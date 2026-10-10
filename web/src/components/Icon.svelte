<script lang="ts">
  // A Material Symbols Rounded glyph; [fill] switches to the filled style (a liked heart, the
  // active destination).
  let { name, size = 24, fill = false, weight = 500 }: { name: string; size?: number; fill?: boolean; weight?: number } = $props()
</script>

<span
  class="icon"
  aria-hidden="true"
  style:font-size="{size}px"
  style:font-variation-settings="'FILL' {fill ? 1 : 0}, 'wght' {weight}, 'GRAD' 0, 'opsz' {Math.min(48, Math.max(20, size))}"
>{name}</span>

<style>
  .icon {
    font-family: 'Material Symbols Rounded';
    font-weight: normal;
    font-style: normal;
    line-height: 1;
    letter-spacing: normal;
    text-transform: none;
    display: inline-block;
    white-space: nowrap;
    direction: ltr;
    flex: none;
    width: 1em;
    height: 1em;
    overflow: hidden;
    -webkit-font-smoothing: antialiased;
    font-feature-settings: 'liga';
    transition: font-variation-settings 300ms var(--ease-emph);
    user-select: none;
  }
</style>
