<script lang="ts">
  // YouTube Music's home: a hello, what it leads with in the spotlight, the listener's own a tap
  // away, its rows of songs (quick picks, an artist's, broadcasts) as lists to play in place and
  // its rows of sets as cards.
  import { accounts } from '../lib/accounts.svelte'
  import { onMount } from 'svelte'
  import { extension, onExtension } from '../lib/extension'
  import { leading, libraryOf, quickPicks } from '../lib/home.svelte'
  import { tr } from '../lib/i18n.svelte'
  import * as live from '../lib/live'
  import { playQueue } from '../lib/state.svelte'
  import { reason } from '../lib/toast.svelte'
  import ExtensionCard from '../components/ExtensionCard.svelte'
  import HomeHeader from '../components/home/HomeHeader.svelte'
  import LibraryShelves from '../components/home/LibraryShelves.svelte'
  import QuickRow from '../components/home/QuickRow.svelte'
  import Spotlight from '../components/home/Spotlight.svelte'
  import Icon from '../components/Icon.svelte'
  import Loading from '../components/Loading.svelte'
  import Problem from '../components/Problem.svelte'
  import SetCard from '../components/SetCard.svelte'
  import Shelf from '../components/Shelf.svelte'
  import TrackRow from '../components/TrackRow.svelte'

  let data = $state(live.home('yt'))
  let ext = $state<string | null | undefined>(undefined)
  extension().then((v) => (ext = v))
  // Installed while this page is open: its requests go through it from now on.
  onMount(() =>
    onExtension((v) => {
      if (ext === null) data = live.home('yt')
      ext = v
    }),
  )
  // Signing in on YouTube makes home the listener's own.
  let wasSignedIn = accounts.yt
  $effect(() => {
    if (accounts.yt !== wasSignedIn) {
      wasSignedIn = accounts.yt
      data = live.home('yt')
    }
  })

  const lib = libraryOf('yt')
</script>

{#snippet picks(section: import('../lib/live').Section, i: number, limit: number)}
  <div class="section-head">
    <h2>{section.title}</h2>
    <button class="btn text small" onclick={() => playQueue(section.tracks!, 0, section.title, 'yt-home-' + i, { kind: 'related' })}>
      <Icon name="play_arrow" fill size={20} />{tr('Слушать все')}
    </button>
  </div>
  <div class="grid">
    {#each section.tracks!.slice(0, limit) as id (id)}
      <TrackRow {id} list={section.tracks!} context={section.title} contextId={'yt-home-' + i} compact />
    {/each}
  </div>
{/snippet}

<HomeHeader service="yt" note={accounts.ytName ? tr('Для тебя, %s', accounts.ytName) : ''} />
{#await data}
  <Loading text={tr('Спрашиваю YouTube Music…')} />
{:then sections}
  {@const lead = leading(sections)}
  {@const quick = quickPicks(sections)}
  {@const quickIndex = quick ? sections.indexOf(quick) : -1}
  {#if lead}<Spotlight lead={lead.sets} title={lead.title} />{/if}
  {#if ext === null}
    <ExtensionCard why={tr('YouTube отдаёт звук только на твой IP, а не Cloudflare. Подборки и поиск видно и так, а играть будет через расширение.')} />
  {/if}
  <QuickRow service="yt" lib={lib.lib} signedIn={accounts.yt} />
  {#if quick}
    <section class="section quick rise">{@render picks(quick, quickIndex, 12)}</section>
  {/if}
  <LibraryShelves lib={lib.lib} />
  {#each sections as section, i (section.title + i)}
    {#if section === quick || section === lead}
      <!-- shown above -->
    {:else if section.tracks?.length}
      <!-- Songs, not sets ("Слушать вместе", an artist's row): played in place. -->
      <section class="section quick rise">{@render picks(section, i, 12)}</section>
    {:else if section.sets.length}
      <Shelf title={section.title}>
        {#each section.sets as id (id)}<SetCard {id} size={184} />{/each}
      </Shelf>
    {/if}
  {:else}
    <Problem empty title={tr('Пусто')} message={tr('YouTube Music ничего не предложил.')} onretry={() => (data = live.home('yt'))} />
  {/each}
{:catch e}
  <Problem message={reason(e)} onretry={() => (data = live.home('yt'))} />
{/await}

<style>
  .grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(min(100%, 340px), 1fr));
    gap: 2px 12px;
  }
</style>
