<script lang="ts">
  import { coverColor } from './lib/coverColor'
  import { onHostEvent } from './lib/extension'
  import { app, currentTrack } from './lib/state.svelte'
  import { applyTheme, DEFAULT_SEED } from './lib/theme'
  import Snackbar from './components/Snackbar.svelte'
  import Backdrop from './components/Backdrop.svelte'
  import Rail from './components/Rail.svelte'
  import TopBar from './components/TopBar.svelte'
  import NowPlaying from './components/NowPlaying.svelte'
  import PlayerBar from './components/PlayerBar.svelte'
  import SidePanel from './components/SidePanel.svelte'
  import ArtistPage from './pages/ArtistPage.svelte'
  import Friends from './pages/Friends.svelte'
  import HomeSoundCloud from './pages/HomeSoundCloud.svelte'
  import HomeYandex from './pages/HomeYandex.svelte'
  import HomeYouTube from './pages/HomeYouTube.svelte'
  import Search from './pages/Search.svelte'
  import SetPage from './pages/SetPage.svelte'
  import Settings from './pages/Settings.svelte'

  const media = matchMedia('(prefers-color-scheme: dark)')
  let systemDark = $state(media.matches)
  $effect(() => {
    const changed = (e: MediaQueryListEvent) => (systemDark = e.matches)
    media.addEventListener('change', changed)
    return () => media.removeEventListener('change', changed)
  })

  // In Masaki the page has no background of its own — the browser's glass shows through — so it
  // takes the browser's lightness, and its colour while no cover gives one.
  const inMasaki = document.documentElement.dataset.host === 'masaki'
  let browserTheme = $state<{ seed: string; dark: boolean } | null>(null)
  $effect(() =>
    onHostEvent((e) => {
      if (e.event === 'theme' && typeof e.seed === 'string') browserTheme = { seed: e.seed, dark: !!e.dark }
    }),
  )

  const dark = $derived(browserTheme ? browserTheme.dark : app.theme === 'system' ? systemDark : app.theme === 'dark')

  // The page takes the playing cover's colour. The last one stays until the next is read, so a
  // change of track fades once rather than twice.
  let coverSeed = $state<string | null>(null)
  $effect(() => {
    const t = currentTrack()
    if (!t) {
      coverSeed = null
      return
    }
    if (!t.cover) {
      coverSeed = t.color
      return
    }
    let alive = true
    coverColor(t.cover).then((c) => alive && (coverSeed = c ?? t.color))
    return () => (alive = false)
  })
  const base = $derived(browserTheme?.seed || DEFAULT_SEED)
  const seed = $derived(app.coverColors ? (coverSeed ?? base) : base)
  $effect(() => applyTheme(seed, dark))

  // A page is drawn anew when it changes, so it rises in; home also changes with the service.
  const pageKey = $derived(`${app.route.name}/${app.route.id ?? ''}/${app.route.name === 'home' ? app.service : ''}`)
</script>

{#if !inMasaki}<Backdrop />{/if}

<div class="shell" class:with-panel={app.panelOpen}>
  <Rail />
  <main class="main">
    <TopBar />
    <div class="scroller">
    {#key pageKey}
      <div class="page">
        {#if app.route.name === 'home'}
          {#if app.service === 'sc'}<HomeSoundCloud />{:else if app.service === 'ya'}<HomeYandex />{:else}<HomeYouTube />{/if}
        {:else if app.route.name === 'search'}
          <Search />
        {:else if app.route.name === 'friends'}
          <Friends />
        {:else if app.route.name === 'settings'}
          <Settings />
        {:else if app.route.name === 'set' && app.route.id}
          <SetPage id={app.route.id} />
        {:else if app.route.name === 'artist' && app.route.id}
          <ArtistPage id={app.route.id} />
        {/if}
      </div>
    {/key}
    </div>
  </main>
  {#if app.panelOpen}<SidePanel />{/if}
  <PlayerBar />
</div>

{#if app.nowPlaying && currentTrack()}<NowPlaying />{/if}
<Snackbar />

<style>
  .shell {
    position: relative;
    z-index: 1;
    height: 100vh;
    height: 100dvh;
    display: grid;
    --rail: 96px;
    grid-template-columns: var(--rail) minmax(0, 1fr);
    grid-template-rows: minmax(0, 1fr) auto;
    grid-template-areas:
      'rail main'
      'player player';
    gap: 12px;
    padding: 12px;
  }
  .shell.with-panel {
    grid-template-columns: var(--rail) minmax(0, 1fr) 360px;
    grid-template-areas:
      'rail main side'
      'player player player';
  }
  /* The bar stays put above the page; the page scrolls under it and fades out at its edge, so
     nothing shows through around the search field. */
  .main {
    grid-area: main;
    min-height: 0;
    display: flex;
    flex-direction: column;
    padding-top: 16px;
    position: relative;
    isolation: isolate;
  }
  .scroller {
    flex: 1;
    min-height: 0;
    overflow-y: auto;
    overflow-x: hidden;
    padding: 6px 36px 56px;
    mask-image: linear-gradient(to bottom, transparent 0, #000 18px);
  }
  /* Pages size themselves to their column (@container page), not to the window: the rail and
     the side panel take a different share of it. */
  .page {
    max-width: 1480px;
    margin: 0 auto;
    container: page / inline-size;
  }
  /* In Masaki the window already has the browser's panel on the left: the page keeps a slimmer
     frame, and friends, the queue and lyrics float over it instead of taking a third column. */
  :global(:root[data-host='masaki']) .shell {
    gap: 10px;
    padding: 10px;
  }
  :global(:root[data-host='masaki']) .shell.with-panel {
    grid-template-columns: var(--rail) minmax(0, 1fr);
    grid-template-areas:
      'rail main'
      'player player';
  }
  :global(:root[data-host='masaki']) .shell.with-panel :global(.panel) {
    position: fixed;
    top: 10px;
    right: 10px;
    bottom: 124px;
    width: 340px;
    box-shadow: var(--shadow);
  }
  @media (max-width: 1200px) {
    .shell.with-panel {
      grid-template-columns: var(--rail) minmax(0, 1fr);
      grid-template-areas:
        'rail main'
        'player player';
    }
    .shell.with-panel :global(.panel) {
      position: fixed;
      top: 12px;
      right: 12px;
      bottom: 128px;
      width: 340px;
      box-shadow: var(--shadow);
    }
  }
</style>
