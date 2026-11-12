<script lang="ts">
  // The top of a service's home: which service it is, small, and a hello for the hour, large.
  // Nothing else up here — what to play starts right under it.
  import type { Service } from '../../lib/catalog'
  import { serviceName } from '../../lib/catalog'
  import { greeting } from '../../lib/home.svelte'
  import ServiceGlyph from '../ServiceGlyph.svelte'

  let { service, note = '' }: { service: Service; note?: string } = $props()
</script>

<header class="hello rise">
  <span class="kicker"><ServiceGlyph {service} size={14} />{serviceName(service)}</span>
  <h1 class="display">{greeting()}</h1>
  {#if note}<p class="note">{note}</p>{/if}
</header>

<style>
  .hello {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
    padding-top: 6px;
  }
  .kicker {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    font-size: 12px;
    font-weight: 800;
    letter-spacing: 0.12em;
    text-transform: uppercase;
    color: var(--on-surface-variant);
  }
  h1 {
    font-size: clamp(30px, 3.4cqw, 46px);
  }
  .note {
    font-size: 16px;
    color: var(--on-surface-variant);
  }
</style>
