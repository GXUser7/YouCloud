<script lang="ts">
  // SoundCloud's home: what it leads with (the listener's mixes, signed in) laid out as the
  // layout picked in Settings has it, then the listener's library and the rest of the rows.
  import * as live from '../lib/live'
  import { home, leading, libraryOf } from '../lib/home.svelte'
  import { tr } from '../lib/i18n.svelte'
  import { reason } from '../lib/toast.svelte'
  import Bento from '../components/home/Bento.svelte'
  import LibraryShelves from '../components/home/LibraryShelves.svelte'
  import Quick from '../components/home/Quick.svelte'
  import Stage from '../components/home/Stage.svelte'
  import Loading from '../components/Loading.svelte'
  import PageHeader from '../components/PageHeader.svelte'
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
  const subtitle = $derived(signedIn ? tr('Миксы для тебя, твои лайки и плейлисты') : tr('Что слушают на SoundCloud'))
</script>

{#if home.variant !== 'quick'}<PageHeader title="SoundCloud" {subtitle} />{/if}
{#await data}
  <Loading text={tr('Спрашиваю SoundCloud…')} />
{:then sections}
  {@const lead = leading(sections)}
  {#if home.variant === 'bento'}
    <Bento service="sc" lead={lead?.sets ?? []} leadTitle={lead?.title ?? ''} lib={lib.lib} {signedIn} />
  {:else if home.variant === 'stage'}
    <Stage service="sc" lead={lead?.sets ?? []} leadTitle={lead?.title ?? ''} lib={lib.lib} {signedIn} />
  {:else}
    <Quick service="sc" lead={lead?.sets ?? []} lib={lib.lib} {signedIn} subtitle="SoundCloud · {subtitle}" />
  {/if}
  <LibraryShelves lib={lib.lib} />
  {#each sections.filter((x) => x.sets.length && (x !== lead || home.variant === 'quick')) as section (section.title)}
    <Shelf title={section.title}>
      {#each section.sets as id (id)}<SetCard {id} size={180} />{/each}
    </Shelf>
  {/each}
  {#if !sections.length}
    <Problem title={tr('Пусто')} message={tr('SoundCloud ничего не предложил. Попробуй позже.')} onretry={() => (data = live.home('sc'))} />
  {/if}
{:catch e}
  <Problem message={reason(e)} onretry={() => (data = live.home('sc'))} />
{/await}
