<script lang="ts">
  import { SERVICES, type Service } from '../lib/mock'
  import { app, setService } from '../lib/state.svelte'
  import ButtonGroup from './ButtonGroup.svelte'
  import ServiceGlyph from './ServiceGlyph.svelte'

  let { size = 'm' }: { size?: 's' | 'm' | 'l' } = $props()
  const options = SERVICES.map((s) => ({ value: s.id, label: s.short }))
</script>

<ButtonGroup {options} value={app.service} onchange={(v) => v && setService(v as Service)} {size}>
  {#snippet item(option)}
    <ServiceGlyph service={option.value as Service} size={18} />
    {option.label}
  {/snippet}
</ButtonGroup>
