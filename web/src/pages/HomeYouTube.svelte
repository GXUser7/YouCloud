<script lang="ts">
  import { accounts } from '../lib/accounts.svelte'
  import { onMount } from 'svelte'
  import { extension, onExtension } from '../lib/extension'
  import * as live from '../lib/live'
  import { playQueue } from '../lib/state.svelte'
  import { reason } from '../lib/toast.svelte'
  import ExtensionCard from '../components/ExtensionCard.svelte'
  import Favorites from '../components/Favorites.svelte'
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
</script>

<PageHeader title="YouTube Music" subtitle={accounts.ytName ? `Для тебя, ${accounts.ytName}` : accounts.yt ? 'Твоё любимое и подборки' : 'Что слушают на YouTube Music'} />

{#if ext === null}
  <ExtensionCard why="YouTube отдаёт звук только на твой IP, а не Cloudflare. Подборки и поиск видно и так, а играть будет через расширение." />
{/if}
{#if ext}<Favorites service="yt" />{/if}

{#await data}
  <Loading text="Спрашиваю YouTube Music…" />
{:then sections}
  {#each sections as section, i (section.title + i)}
    {#if section.tracks?.length}
      <section class="section quick rise">
        <div class="section-head">
          <h2>{section.title}</h2>
          <button class="btn text small" onclick={() => playQueue(section.tracks!, 0, section.title, 'yt-home-' + i, { kind: 'related' })}>
            <Icon name="play_arrow" fill size={20} />Слушать все
          </button>
        </div>
        <div class="grid">
          {#each section.tracks.slice(0, 12) as id (id)}
            <TrackRow {id} list={section.tracks} context={section.title} contextId={'yt-home-' + i} compact />
          {/each}
        </div>
      </section>
    {:else}
      <Shelf title={section.title}>
        {#each section.sets as id (id)}<SetCard {id} size={184} />{/each}
      </Shelf>
    {/if}
  {:else}
    <Problem title="Пусто" message="YouTube Music ничего не предложил." onretry={() => (data = live.home('yt'))} />
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
</style>
