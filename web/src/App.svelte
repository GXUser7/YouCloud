<script lang="ts">
  import { coverColor } from './lib/coverColor'
  import { onHostEvent } from './lib/extension'
  import { transparentInMasaki } from './lib/masaki.svelte'
  import { observeHostGlass } from './lib/hostGlass'
  import { app, currentTrack } from './lib/state.svelte'
  import { applyTheme, DEFAULT_SEED } from './lib/theme'
  import Snackbar from './components/Snackbar.svelte'
  import Backdrop from './components/Backdrop.svelte'
  import TopBar from './components/TopBar.svelte'
  import NowPlaying from './components/NowPlaying.svelte'
  import PlayerBar from './components/PlayerBar.svelte'
  import SidePanel from './components/SidePanel.svelte'
  import ArtistPage from './pages/ArtistPage.svelte'
  import Friends from './pages/Friends.svelte'
  import HomeSoundCloud from './pages/HomeSoundCloud.svelte'
  import HomeYandex from './pages/HomeYandex.svelte'
  import HomeYouTube from './pages/HomeYouTube.svelte'
  import Liked from './pages/Liked.svelte'
  import Search from './pages/Search.svelte'
  import SetPage from './pages/SetPage.svelte'
  import Settings from './pages/Settings.svelte'
  import SoundCloudChallenge from './components/SoundCloudChallenge.svelte'

  const media = matchMedia('(prefers-color-scheme: dark)')
  let systemDark = $state(media.matches)
  $effect(() => {
    const changed = (e: MediaQueryListEvent) => (systemDark = e.matches)
    media.addEventListener('change', changed)
    return () => media.removeEventListener('change', changed)
  })

  const transparent = $derived(transparentInMasaki())
  let browserTheme = $state<{ seed: string; dark: boolean } | null>(null)
  $effect(() =>
    onHostEvent((e) => {
      if (e.event === 'theme' && typeof e.seed === 'string') browserTheme = { seed: e.seed, dark: !!e.dark }
    }),
  )

  const dark = $derived(transparent && browserTheme ? browserTheme.dark : app.theme === 'system' ? systemDark : app.theme === 'dark')
  $effect(() => { if (transparent) return observeHostGlass() })

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
  const base = $derived(transparent ? browserTheme?.seed || DEFAULT_SEED : DEFAULT_SEED)
  const seed = $derived(app.coverColors ? (coverSeed ?? base) : base)
  $effect(() => applyTheme(seed, dark))

  // A page is drawn anew when it changes, so it rises in; home also changes with the service.
  const pageKey = $derived(`${app.route.name}/${app.route.id ?? ''}/${app.route.name === 'home' ? app.service : ''}`)
</script>

{#if !transparent}<Backdrop />{/if}

<div class="shell" class:with-panel={app.panelOpen}>
  <main class="main">
    <TopBar />
    <div class="scroller">
    {#if !transparent}<Backdrop mirror />{/if}
    {#key pageKey}
      <div class="page">
        {#if app.route.name === 'home'}
          {#if app.service === 'sc'}<HomeSoundCloud />{:else if app.service === 'ya'}<HomeYandex />{:else}<HomeYouTube />{/if}
        {:else if app.route.name === 'search'}
          <Search />
        {:else if app.route.name === 'liked'}
          <Liked />
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
<SoundCloudChallenge />

<style>
  .shell {
    position: relative;
    z-index: 1;
    height: 100vh;
    height: 100dvh;
    display: grid;
    grid-template-columns: minmax(0, 1fr);
    grid-template-rows: minmax(0, 1fr) auto;
    grid-template-areas:
      'main'
      'player';
    gap: 12px;
    padding: 12px;
  }
  .shell.with-panel {
    grid-template-columns: minmax(0, 1fr) 360px;
    grid-template-areas:
      'main side'
      'player player';
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
    --page-gutter: 36px;
    --edge-fade-height: 32px;
    flex: 1;
    min-height: 0;
    overflow-y: auto;
    overflow-x: hidden;
    padding: calc(var(--edge-fade-height) + 6px) var(--page-gutter) 56px;
    /* Фон внутри маски даёт карточкам настоящий backdrop; снаружи остаётся
       его синхронная копия, поэтому прозрачный край растворяется без шва. */
    mask-image: linear-gradient(to bottom,
      transparent, #0001 calc(var(--edge-fade-height) * .2), #0008 calc(var(--edge-fade-height) * .5),
      #000e calc(var(--edge-fade-height) * .8), #000 var(--edge-fade-height),
      #000 calc(100% - var(--edge-fade-height)), #000e calc(100% - var(--edge-fade-height) * .8),
      #0008 calc(100% - var(--edge-fade-height) * .5), #0001 calc(100% - var(--edge-fade-height) * .2), transparent);
  }
  /* Pages size themselves to their column (@container page), not to the window: the side panel
     takes a share of it. */
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
    grid-template-columns: minmax(0, 1fr);
    grid-template-areas:
      'main'
      'player';
  }
  :global(:root[data-host='masaki']) .shell.with-panel :global(.panel) {
    position: fixed;
    top: 10px;
    right: 10px;
    bottom: 84px;
    width: 340px;
    box-shadow: var(--shadow);
  }
  @media (max-width: 1200px) {
    .shell.with-panel {
      grid-template-columns: minmax(0, 1fr);
      grid-template-areas:
        'main'
        'player';
    }
    .shell.with-panel :global(.panel) {
      position: fixed;
      top: 12px;
      right: 12px;
      bottom: 88px;
      width: 340px;
      box-shadow: var(--shadow);
    }
  }
  @media (max-width: 1100px) {
    .scroller { --page-gutter: 20px; }
  }
  @media (max-width: 680px) {
    .shell { padding: 8px; gap: 8px; }
    .main { padding-top: 8px; }
    .scroller { --page-gutter: 12px; --edge-fade-height: 24px; padding-bottom: 28px; }
  }
</style>
