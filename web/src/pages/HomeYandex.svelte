<script lang="ts">
  // Yandex Music's home: «Моя волна» and what Yandex made for the listener (only the chart when
  // signed out), laid out as the layout picked in Settings has it, then the library and the rows.
  import { onMount } from 'svelte'
  import { extension, onExtension } from '../lib/extension'
  import { home, leading, libraryOf } from '../lib/home.svelte'
  import { tr } from '../lib/i18n.svelte'
  import * as live from '../lib/live'
  import { reason } from '../lib/toast.svelte'
  import ExtensionCard from '../components/ExtensionCard.svelte'
  import Bento from '../components/home/Bento.svelte'
  import LibraryShelves from '../components/home/LibraryShelves.svelte'
  import Quick from '../components/home/Quick.svelte'
  import Stage from '../components/home/Stage.svelte'
  import WaveCompact from '../components/home/WaveCompact.svelte'
  import Loading from '../components/Loading.svelte'
  import PageHeader from '../components/PageHeader.svelte'
  import Problem from '../components/Problem.svelte'
  import SetCard from '../components/SetCard.svelte'
  import Shelf from '../components/Shelf.svelte'

  let data = $state(live.home('ya'))
  const signedIn = $derived(live.signedIn('ya'))
  let wasSignedIn = live.signedIn('ya')
  $effect(() => {
    if (signedIn !== wasSignedIn) {
      wasSignedIn = signedIn
      data = live.home('ya')
    }
  })

  // Yandex answers only the listener's own IP: without the extension there is nothing to show.
  let ext = $state<string | null | undefined>(undefined)
  extension().then((v) => (ext = v))
  onMount(() =>
    onExtension((v) => {
      if (ext === null) data = live.home('ya')
      ext = v
    }),
  )

  const lib = libraryOf('ya')
  const subtitle = $derived(tr('Моя волна, твоё любимое и подборки'))
</script>

{#snippet waveBig()}<WaveCompact size="big" />{/snippet}
{#snippet waveCard()}<WaveCompact size="card" />{/snippet}
{#snippet waveTile()}<WaveCompact size="tile" />{/snippet}

{#if home.variant !== 'quick' || ext === null}<PageHeader title={tr('Яндекс Музыка')} {subtitle} />{/if}
{#if ext === null}
  <ExtensionCard why={tr('Яндекс Музыка отвечает только твоему IP, а не Cloudflare: подборки, поиск и звук идут через расширение.')} />
{:else if ext !== undefined}
  {#await data}
    <Loading text={tr('Спрашиваю Яндекс…')} />
  {:then sections}
    {@const lead = leading(sections)}
    {#if home.variant === 'bento'}
      <Bento service="ya" lead={lead?.sets ?? []} leadTitle={lead?.title ?? ''} lib={lib.lib} {signedIn} main={signedIn ? waveBig : undefined} />
    {:else if home.variant === 'stage'}
      <Stage service="ya" lead={lead?.sets ?? []} leadTitle={lead?.title ?? ''} lib={lib.lib} {signedIn} extra={signedIn ? waveCard : undefined} />
    {:else}
      <Quick service="ya" lead={lead?.sets ?? []} lib={lib.lib} {signedIn} subtitle="{tr('Яндекс Музыка')} · {subtitle}" first={signedIn ? waveTile : undefined} />
    {/if}
    <LibraryShelves lib={lib.lib} />
    {#each sections.filter((x) => x.sets.length && (x !== lead || home.variant === 'quick')) as section (section.title)}
      <Shelf title={section.title}>
        {#each section.sets as id (id)}<SetCard {id} size={184} />{/each}
      </Shelf>
    {/each}
  {:catch e}
    <Problem message={reason(e)} onretry={() => (data = live.home('ya'))} />
  {/await}
{/if}
