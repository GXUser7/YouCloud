<script lang="ts">
  // «Любимое» of the YouCloud account: every track liked in the app — and so downloaded on the
  // phone — or here, from all three services, the latest first; the same list the app shows,
  // without the files that only the phone has.
  import { tr } from '../lib/i18n.svelte'
  import { SERVICES, track, type Service } from '../lib/catalog'
  import { favorites, retry } from '../lib/favorites.svelte'
  import { totalMinutes, tracksCount } from '../lib/format'
  import { social } from '../lib/social.svelte'
  import { app, player, playQueue, toggle } from '../lib/state.svelte'
  import AccountForm from '../components/AccountForm.svelte'
  import ButtonGroup from '../components/ButtonGroup.svelte'
  import LikedArt from '../components/LikedArt.svelte'
  import Loading from '../components/Loading.svelte'
  import PlaybackActions from '../components/PlaybackActions.svelte'
  import Problem from '../components/Problem.svelte'
  import ServiceGlyph from '../components/ServiceGlyph.svelte'
  import TrackRow from '../components/TrackRow.svelte'

  const ID = 'yc:liked'
  const COLOR = '#d81b60'

  let only = $state<Service | null>(null)

  const visibleIds = $derived(favorites.ids.filter((id) => app.showLiveInLiked || !track(id).live))
  const shown = $derived(only ? visibleIds.filter((id) => track(id).service === only) : visibleIds)
  const duration = $derived(shown.reduce((sum, id) => sum + (track(id).duration || 0), 0))
  const count = (svc: Service) => visibleIds.filter((id) => track(id).service === svc).length
  const filters = $derived([
    { value: 'all' as const, label: tr('Все') },
    ...SERVICES.filter((s) => count(s.id)).map((s) => ({ value: s.id, label: s.short })),
  ])
  // Each filter its own queue: playing the Yandex ones isn't playing them all.
  const contextId = $derived(`${ID}:${only ?? 'all'}:${app.showLiveInLiked ? 'live' : 'songs'}`)

  function shuffle() {
    const ids = [...shown]
    for (let i = ids.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1))
      ;[ids[i], ids[j]] = [ids[j], ids[i]]
    }
    playQueue(ids, 0, tr('Любимое'), contextId)
  }
</script>

<section class="head">
  <div class="art">
    <LikedArt ids={shown} seed={ID} color={COLOR} />
  </div>
  <div class="info">
    <span class="overline">{tr('YouCloud · приложение и сайт')}</span>
    <h1 class="display">{tr('Любимое')}</h1>
    <p class="muted sub">
      {#if social.me}
        {tr('Лайкнутое в приложении — и скачанное там — и здесь: со всех устройств, где вход в @%s.', social.me.nick)}
      {:else}
        {tr('То же «Любимое», что в приложении: войди в аккаунт YouCloud.')}
      {/if}
    </p>
    {#if favorites.loaded}
      <p class="muted meta">{tracksCount(shown.length)}{duration ? ` · ${totalMinutes(duration)}` : ''}</p>
    {/if}
  </div>
</section>

{#if !social.ready}
  <Loading />
{:else if !social.me}
  <section class="signin glass rise">
    <div class="pitch">
      <h2 class="headline">{tr('Одно «Любимое» везде')}</h2>
      <p class="muted">
        {tr('Войди в тот же аккаунт YouCloud, что в приложении. Всё, что лайкнуто на телефоне, появится здесь, а сердечко здесь — в приложении, и оно скачает трек.')}
      </p>
    </div>
    <AccountForm />
  </section>
{:else if favorites.error && !favorites.loaded}
  <Problem message={tr('«Любимое» не загрузилось: %s', favorites.error ?? '')} onretry={() => retry().catch(() => {})} />
{:else if !favorites.loaded}
  <Loading text={tr('Загружаю «Любимое»…')} />
{:else}
  <div class="bar">
    <PlaybackActions
      playing={player.contextId === contextId && player.playing}
      disabled={!shown.length}
      onplay={() => (player.contextId === contextId ? toggle() : playQueue(shown, 0, tr('Любимое'), contextId))}
      onshuffle={shuffle}
    />
    <div class="filters">
      <ButtonGroup variant="category" label={tr('Сервис')} options={filters} value={only ?? 'all'} allowNone onchange={(value) => (only = value === 'all' ? null : value)}>
        {#snippet item(option)}
          {#if option.value === 'all'}{option.label}{:else}
            <ServiceGlyph service={option.value} size={19} />{option.label}<span class="n">{count(option.value)}</span>
          {/if}
        {/snippet}
      </ButtonGroup>
    </div>
  </div>

  <section class="list track-list glass rise">
    {#each shown as id, i (id)}
      <TrackRow {id} list={shown} context={tr('Любимое')} {contextId} number={i + 1} />
    {:else}
      <p class="muted empty">{tr('Пока пусто. Сердечко у трека — здесь или в приложении — добавит его сюда.')}</p>
    {/each}
  </section>
{/if}

<style>
  .head {
    position: relative;
    display: flex;
    align-items: flex-end;
    gap: 32px;
    padding-top: 24px;
  }
  .art {
    width: clamp(200px, 18vw, 260px);
    aspect-ratio: 1;
    border-radius: var(--r-2xl);
    overflow: hidden;
    flex: none;
    box-shadow: 0 30px 70px -30px #000c;
    animation: rise var(--d-slow) var(--spring);
  }
  .info {
    min-width: 0;
    animation: rise var(--d-slow) var(--spring) 60ms both;
  }
  h1 {
    font-size: clamp(36px, 4.2vw, 64px);
    margin: 10px 0 14px;
  }
  .sub {
    max-width: 70ch;
  }
  .meta {
    margin-top: 12px;
  }
  .bar {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 12px;
    margin: 32px 0 20px;
  }
  .filters {
    margin-left: 8px;
    max-width: 100%;
  }
  @container page (max-width: 620px) {
    .filters {
      width: 100%;
      margin-left: 0;
    }
  }
  .n {
    font-variant-numeric: tabular-nums;
    opacity: 0.7;
  }
  .empty {
    padding: 16px 4px;
  }
  .signin {
    margin-top: 32px;
    border-radius: var(--r-2xl);
    padding: 28px;
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(320px, 420px);
    gap: 40px;
    align-items: center;
  }
  .pitch h2 {
    font-size: 30px;
    margin-bottom: 12px;
  }
  @media (max-width: 720px) {
    .head {
      flex-direction: column;
      align-items: flex-start;
      gap: 20px;
    }
    .signin {
      grid-template-columns: minmax(0, 1fr);
    }
  }
</style>
