<script lang="ts">
  // Spotify home: Pathfinder shelves, optional spotlight lead, SoundCloud-style "Любимое" &
  // playlists quick strip (`sp:likes` + user library), and inline track rows when home returns tracks.
  import { tr } from '../lib/i18n.svelte'
  import { leading, useHome } from '../lib/home.svelte'
  import { playQueue } from '../lib/state.svelte'
  import { reason } from '../lib/toast.svelte'
  import HomeHeader from '../components/home/HomeHeader.svelte'
  import Spotlight from '../components/home/Spotlight.svelte'
  import QuickRow from '../components/home/QuickRow.svelte'
  import LibraryShelves from '../components/home/LibraryShelves.svelte'
  import Shelf from '../components/Shelf.svelte'
  import SetCard from '../components/SetCard.svelte'
  import TrackRow from '../components/TrackRow.svelte'
  import Loading from '../components/Loading.svelte'
  import Problem from '../components/Problem.svelte'

  const home = useHome('sp')
  const lead = $derived(leading(home.sections))
  const rest = $derived(home.sections.filter((s) => s !== lead))
</script>

<div class="home">
  <HomeHeader service="sp" />

  {#if (home.status === 'checking' || home.status === 'loading') && !home.sections.length}
    <Loading text={tr('Загружаем Spotify…')} />
  {:else if home.status === 'error' && !home.sections.length}
    <Problem message={reason(home.error)} onretry={home.retry} />
  {:else}
    {#if lead}
      <Spotlight lead={lead.sets} title={lead.title} />
    {/if}

    <QuickRow service="sp" lib={home.lib} signedIn={home.signedIn} />

    <LibraryShelves lib={home.lib} />

    {#each rest as sec (sec.title)}
      {#if sec.tracks?.length}
        <section class="section rise">
          <div class="section-head">
            <h2>{sec.title}</h2>
          </div>
          <div class="tracks">
            {#each sec.tracks as id, i (id)}
              <TrackRow {id} list={sec.tracks} onplay={() => sec.tracks && playQueue(sec.tracks, i, sec.title)} />
            {/each}
          </div>
        </section>
      {:else if sec.sets.length}
        <Shelf title={sec.title}>
          {#each sec.sets as id (id)}
            <SetCard {id} />
          {/each}
        </Shelf>
      {/if}
    {/each}
  {/if}
</div>

<style>
  .home {
    display: flex;
    flex-direction: column;
    gap: 36px;
  }
  .tracks {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
    gap: 6px 20px;
  }
</style>
