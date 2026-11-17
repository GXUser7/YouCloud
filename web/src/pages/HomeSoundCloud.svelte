<script lang="ts">
  // SoundCloud's home: a hello, what it leads with (the listener's mixes, signed in) in the
  // spotlight, the listener's own a tap away, then the rest of its rows.
  import { leading, useHome } from '../lib/home.svelte'
  import { tr } from '../lib/i18n.svelte'
  import { reason } from '../lib/toast.svelte'
  import HomeHeader from '../components/home/HomeHeader.svelte'
  import LibraryShelves from '../components/home/LibraryShelves.svelte'
  import QuickRow from '../components/home/QuickRow.svelte'
  import Spotlight from '../components/home/Spotlight.svelte'
  import Loading from '../components/Loading.svelte'
  import Problem from '../components/Problem.svelte'
  import SetCard from '../components/SetCard.svelte'
  import Shelf from '../components/Shelf.svelte'

  const home = useHome('sc')
</script>

<HomeHeader service="sc" />
{#if home.status === 'loading'}
  <Loading text={tr('Спрашиваю SoundCloud…')} />
{:else if home.status === 'error'}
  <Problem message={reason(home.error)} onretry={home.retry} />
{:else if home.status === 'ready'}
  {@const sections = home.sections}
  {@const lead = leading(sections)}
  {#if lead}<Spotlight lead={lead.sets} title={lead.title} />{/if}
  <QuickRow service="sc" lib={home.lib} signedIn={home.signedIn} />
  <LibraryShelves lib={home.lib} />
  {#each sections.filter((x) => x.sets.length && x !== lead) as section (section.title)}
    <Shelf title={section.title}>
      {#each section.sets as id (id)}<SetCard {id} size={180} />{/each}
    </Shelf>
  {/each}
  {#if !sections.length}
    <Problem empty title={tr('Пусто')} message={tr('SoundCloud ничего не предложил. Попробуй позже.')} onretry={home.retry} />
  {/if}
{/if}
