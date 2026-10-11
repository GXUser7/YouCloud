<script lang="ts">
  // «Любимое» of the YouCloud account: every track liked in the app — and so downloaded on the
  // phone — or here, from all three services, the latest first; the same list the app shows,
  // without the files that only the phone has.
  import { tr } from '../lib/i18n.svelte'
  import { SERVICES, track, type Service } from '../lib/catalog'
  import { favorites, retry } from '../lib/favorites.svelte'
  import { totalMinutes, tracksCount } from '../lib/format'
  import { social } from '../lib/social.svelte'
  import { player, playQueue, toggle } from '../lib/state.svelte'
  import AccountForm from '../components/AccountForm.svelte'
  import LikedArt from '../components/LikedArt.svelte'
  import Icon from '../components/Icon.svelte'
  import Loading from '../components/Loading.svelte'
  import PlayButton from '../components/PlayButton.svelte'
  import Problem from '../components/Problem.svelte'
  import ServiceGlyph from '../components/ServiceGlyph.svelte'
  import TrackRow from '../components/TrackRow.svelte'

  const ID = 'yc:liked'
  const COLOR = '#d81b60'

  let only = $state<Service | null>(null)

  const shown = $derived(only ? favorites.ids.filter((id) => track(id).service === only) : favorites.ids)
  const duration = $derived(shown.reduce((sum, id) => sum + (track(id).duration || 0), 0))
  const count = (svc: Service) => favorites.ids.filter((id) => track(id).service === svc).length
  // Each filter its own queue: playing the Yandex ones isn't playing them all.
  const contextId = $derived(only ? `${ID}:${only}` : ID)

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
    <LikedArt ids={favorites.ids} seed={ID} color={COLOR} />
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
    <PlayButton
      playing={player.contextId === contextId && player.playing}
      onclick={() => (player.contextId === contextId ? toggle() : playQueue(shown, 0, tr('Любимое'), contextId))}
      size={72}
    />
    <button class="icon-btn tonal l" onclick={shuffle} disabled={!shown.length} aria-label={tr('Перемешать')} title={tr('Перемешать')}><Icon name="shuffle" /></button>
    <div class="filters" role="radiogroup" aria-label={tr('Сервис')}>
      <button class="chip" class:on={!only} role="radio" aria-checked={!only} onclick={() => (only = null)}>{tr('Все')}</button>
      {#each SERVICES as s (s.id)}
        {@const n = count(s.id)}
        {#if n}
          <button class="chip" class:on={only === s.id} role="radio" aria-checked={only === s.id} onclick={() => (only = only === s.id ? null : s.id)}>
            <ServiceGlyph service={s.id} size={16} />{s.short}<span class="n">{n}</span>
          </button>
        {/if}
      {/each}
    </div>
  </div>

  <section class="list rise">
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
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    margin-left: 8px;
  }
  .chip.on {
    background: var(--secondary-container);
    color: var(--on-secondary-container);
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
