<script lang="ts">
  // SoundCloud's home: a hello, what it leads with (the listener's mixes, signed in) in the
  // spotlight, the listener's own a tap away, then the rest of its rows.
  import * as live from '../lib/live'
  import { leading, libraryOf } from '../lib/home.svelte'
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

  let data = $state(live.home('sc'))
  const signedIn = $derived(live.signedIn('sc'))

  // Signing in changes what home holds: the listener's own mixes appear.
  let wasSignedIn = live.signedIn('sc')
  $effect(() => {
    if (signedIn !== wasSignedIn) {
      wasSignedIn = signedIn
      data = live.home('sc')
    }
  })

  const lib = libraryOf('sc')
</script>

<HomeHeader service="sc" />
{#await data}
  <Loading text={tr('Спрашиваю SoundCloud…')} />
{:then sections}
  {@const lead = leading(sections)}
  {#if lead}<Spotlight lead={lead.sets} title={lead.title} />{/if}
  <QuickRow service="sc" lib={lib.lib} {signedIn} />
  <LibraryShelves lib={lib.lib} />
  {#each sections.filter((x) => x.sets.length && x !== lead) as section (section.title)}
    <Shelf title={section.title}>
      {#each section.sets as id (id)}<SetCard {id} size={180} />{/each}
    </Shelf>
  {/each}
  {#if !sections.length}
    <Problem empty title={tr('Пусто')} message={tr('SoundCloud ничего не предложил. Попробуй позже.')} onretry={() => (data = live.home('sc'))} />
  {/if}
{:catch e}
  <Problem message={reason(e)} onretry={() => (data = live.home('sc'))} />
{/await}
