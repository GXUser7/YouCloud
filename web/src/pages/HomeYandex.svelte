<script lang="ts">
  // Yandex Music's home: a hello, My Wave leading (or, signed out, what Yandex leads with in the
  // spotlight), the listener's own a tap away, then Yandex's rows.
  import { onMount } from 'svelte'
  import { extension, onExtension } from '../lib/extension'
  import { leading, useHome } from '../lib/home.svelte'
  import { tr } from '../lib/i18n.svelte'
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

  const home = useHome('ya', { waitForExtension: true })

  // Yandex answers only the listener's own IP: without the extension there is nothing to show on a miss.
  let ext = $state<string | null | undefined>(undefined)
  extension().then((v) => (ext = v))
  onMount(() =>
    onExtension((v) => {
      const wasMissing = ext === null
      ext = v
      if (wasMissing && !home.servedFromCache) home.retry()
    }),
  )
</script>

{#snippet content(sections: import('../lib/live').Section[])}
  {@const lead = leading(sections)}
  {#if home.signedIn}
    <WaveCompact size="hero" />
  {:else if lead}
    <Spotlight lead={lead.sets} title={lead.title} />
  {/if}
  <QuickRow service="ya" lib={home.lib} signedIn={home.signedIn} />
  <LibraryShelves lib={home.lib} />
  {#each sections.filter((x) => x.sets.length && (home.signedIn || x !== lead)) as section (section.title)}
    <Shelf title={section.title}>
      {#each section.sets as id (id)}<SetCard {id} size={184} />{/each}
    </Shelf>
  {/each}
{/snippet}

<HomeHeader service="ya" />
{#if home.servedFromCache && home.status === 'ready'}
  {@render content(home.sections)}
{:else if ext === null}
  <ExtensionCard why={tr('Яндекс Музыка отвечает только твоему IP, а не Cloudflare: подборки, поиск и звук идут через расширение.')} />
{:else if ext !== undefined}
  {#if home.status === 'loading'}
    <Loading text={tr('Спрашиваю Яндекс…')} />
  {:else if home.status === 'error'}
    <Problem message={reason(home.error)} onretry={home.retry} />
  {:else if home.status === 'ready'}
    {@render content(home.sections)}
  {/if}
{/if}
