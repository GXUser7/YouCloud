<script lang="ts">
  // Yandex Music's home: a hello, My Wave leading (or, signed out, what Yandex leads with in the
  // spotlight), the listener's own a tap away, then Yandex's rows.
  import { onMount } from 'svelte'
  import { extension, onExtension } from '../lib/extension'
  import { leading, libraryOf } from '../lib/home.svelte'
  import { tr } from '../lib/i18n.svelte'
  import * as live from '../lib/live'
  import { reason } from '../lib/toast.svelte'
  import ExtensionCard from '../components/ExtensionCard.svelte'
  import HomeHeader from '../components/home/HomeHeader.svelte'
  import LibraryShelves from '../components/home/LibraryShelves.svelte'
  import QuickRow from '../components/home/QuickRow.svelte'
  import Spotlight from '../components/home/Spotlight.svelte'
  import WaveCompact from '../components/home/WaveCompact.svelte'
  import Loading from '../components/Loading.svelte'
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
</script>

<HomeHeader service="ya" />
{#if ext === null}
  <ExtensionCard why={tr('Яндекс Музыка отвечает только твоему IP, а не Cloudflare: подборки, поиск и звук идут через расширение.')} />
{:else if ext !== undefined}
  {#await data}
    <Loading text={tr('Спрашиваю Яндекс…')} />
  {:then sections}
    {@const lead = leading(sections)}
    {#if signedIn}
      <WaveCompact size="hero" />
    {:else if lead}
      <Spotlight lead={lead.sets} title={lead.title} />
    {/if}
    <QuickRow service="ya" lib={lib.lib} {signedIn} />
    <LibraryShelves lib={lib.lib} />
    {#each sections.filter((x) => x.sets.length && (signedIn || x !== lead)) as section (section.title)}
      <Shelf title={section.title}>
        {#each section.sets as id (id)}<SetCard {id} size={184} />{/each}
      </Shelf>
    {/each}
  {:catch e}
    <Problem message={reason(e)} onretry={() => (data = live.home('ya'))} />
  {/await}
{/if}
