<script lang="ts">
  // YouTube Music's home: its rows of sets laid out as the layout picked in Settings has it, its
  // rows of songs (quick picks, an artist's, broadcasts) as lists to play in place.
  import { accounts } from '../lib/accounts.svelte'
  import { onMount } from 'svelte'
  import { extension, onExtension } from '../lib/extension'
  import { home, leading, libraryOf, quickPicks } from '../lib/home.svelte'
  import { tr } from '../lib/i18n.svelte'
  import * as live from '../lib/live'
  import { playQueue } from '../lib/state.svelte'
  import { reason } from '../lib/toast.svelte'
  import ExtensionCard from '../components/ExtensionCard.svelte'
  import Bento from '../components/home/Bento.svelte'
  import LibraryShelves from '../components/home/LibraryShelves.svelte'
  import Quick from '../components/home/Quick.svelte'
  import Stage from '../components/home/Stage.svelte'
  import Icon from '../components/Icon.svelte'
  import Loading from '../components/Loading.svelte'
  import PageHeader from '../components/PageHeader.svelte'
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
  const subtitle = $derived(
    accounts.ytName ? tr('Для тебя, %s', accounts.ytName) : accounts.yt ? tr('Твоё любимое и подборки') : tr('Что слушают на YouTube Music'),
  )
</script>

{#snippet picks(section: import('../lib/live').Section, i: number, limit: number, compact: boolean)}
  <div class="section-head" class:tight={compact}>
    <h2>{section.title}</h2>
    <button class="btn text small" onclick={() => playQueue(section.tracks!, 0, section.title, 'yt-home-' + i, { kind: 'related' })}>
      <Icon name="play_arrow" fill size={20} />{tr('Слушать все')}
    </button>
  </div>
  <div class="grid" class:one={compact}>
    {#each section.tracks!.slice(0, limit) as id (id)}
      <TrackRow {id} list={section.tracks!} context={section.title} contextId={'yt-home-' + i} compact />
    {/each}
  </div>
{/snippet}

{#if home.variant !== 'quick'}<PageHeader title="YouTube Music" {subtitle} />{/if}
{#await data}
  <Loading text={tr('Спрашиваю YouTube Music…')} />
{:then sections}
  {@const lead = leading(sections)}
  {@const quick = quickPicks(sections)}
  {@const quickIndex = quick ? sections.indexOf(quick) : -1}
  {#snippet picksCard()}{@render picks(quick!, quickIndex, 5, true)}{/snippet}
  {#if home.variant === 'bento'}
    <Bento service="yt" lead={lead?.sets ?? []} leadTitle={lead?.title ?? ''} lib={lib.lib} signedIn={accounts.yt} />
  {:else if home.variant === 'stage'}
    <Stage service="yt" lead={lead?.sets ?? []} leadTitle={lead?.title ?? ''} lib={lib.lib} signedIn={accounts.yt} extra={quick ? picksCard : undefined} />
  {:else}
    <Quick service="yt" lead={lead?.sets ?? []} lib={lib.lib} signedIn={accounts.yt} subtitle="YouTube Music · {subtitle}" />
  {/if}
  {#if ext === null}
    <ExtensionCard why={tr('YouTube отдаёт звук только на твой IP, а не Cloudflare. Подборки и поиск видно и так, а играть будет через расширение.')} />
  {/if}
  {#if quick && home.variant !== 'stage'}
    <section class="section quick rise">{@render picks(quick, quickIndex, 12, false)}</section>
  {/if}
  <LibraryShelves lib={lib.lib} />
  {#each sections as section, i (section.title + i)}
    {#if section === quick || (section === lead && home.variant !== 'quick')}
      <!-- shown above -->
    {:else if section.tracks?.length}
      <!-- Songs, not sets ("Слушать вместе", an artist's row): played in place. -->
      <section class="section quick rise">{@render picks(section, i, 12, false)}</section>
    {:else if section.sets.length}
      <Shelf title={section.title}>
        {#each section.sets as id (id)}<SetCard {id} size={184} />{/each}
      </Shelf>
    {/if}
  {:else}
    <Problem title={tr('Пусто')} message={tr('YouTube Music ничего не предложил.')} onretry={() => (data = live.home('yt'))} />
  {/each}
{:catch e}
  <Problem message={reason(e)} onretry={() => (data = live.home('yt'))} />
{/await}

<style>
  .grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
    gap: 2px 12px;
  }
  .grid.one {
    grid-template-columns: minmax(0, 1fr);
  }
  .tight {
    margin-bottom: 8px;
  }
  .tight h2 {
    font-size: 20px;
  }
</style>
