<script lang="ts">
  // Yandex Music's home: My Wave leads the page, then the layout picked in Settings and the library.
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

<PageHeader title={tr('Яндекс Музыка')} {subtitle} />
{#if ext === null}
  <ExtensionCard why={tr('Яндекс Музыка отвечает только твоему IP, а не Cloudflare: подборки, поиск и звук идут через расширение.')} />
{:else if ext !== undefined}
  {#await data}
    <Loading text={tr('Спрашиваю Яндекс…')} />
  {:then sections}
    {@const lead = leading(sections)}
    {#if signedIn}<WaveCompact size="hero" />{/if}
    {#if home.variant === 'bento'}
      <Bento service="ya" lead={lead?.sets ?? []} leadTitle={lead?.title ?? ''} lib={lib.lib} {signedIn} />
    {:else if home.variant === 'stage'}
      <Stage service="ya" lead={lead?.sets ?? []} leadTitle={lead?.title ?? ''} lib={lib.lib} {signedIn} />
    {:else}
      <Quick service="ya" lead={lead?.sets ?? []} lib={lib.lib} {signedIn} subtitle="{tr('Яндекс Музыка')} · {subtitle}" />
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
