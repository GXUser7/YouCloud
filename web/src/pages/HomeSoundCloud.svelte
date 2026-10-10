<script lang="ts">
  import { set } from '../lib/catalog'
  import * as live from '../lib/live'
  import { go, player, playSet, toggle } from '../lib/state.svelte'
  import { reason } from '../lib/toast.svelte'
  import Cover from '../components/Cover.svelte'
  import Favorites from '../components/Favorites.svelte'
  import Icon from '../components/Icon.svelte'
  import Loading from '../components/Loading.svelte'
  import PageHeader from '../components/PageHeader.svelte'
  import PlayButton from '../components/PlayButton.svelte'
  import Problem from '../components/Problem.svelte'
  import SetCard from '../components/SetCard.svelte'
  import Shelf from '../components/Shelf.svelte'

  let data = $state(live.home('sc'))
  let featured = $state<string | null>(null)
  const signedIn = $derived(live.signedIn('sc'))

  // Signing in changes what home holds: the listener's own mixes appear.
  let wasSignedIn = live.signedIn('sc')
  $effect(() => {
    if (signedIn !== wasSignedIn) {
      wasSignedIn = signedIn
      data = live.home('sc')
    }
  })

  function play(id: string) {
    if (player.contextId === id) toggle()
    else playSet(id)
  }
</script>

<PageHeader title="SoundCloud" subtitle={signedIn ? 'Миксы для тебя, твои лайки и плейлисты' : 'Что слушают на SoundCloud'} />

{#await data}
  <Loading text="Спрашиваю SoundCloud…" />
{:then sections}
  {#if sections.length}
    {@const first = sections[0]}
    {@const f = set(featured && first.sets.includes(featured) ? featured : first.sets[0])}
    <section class="hero glass rise">
      {#key f.id}
        <button class="hero-art" onclick={() => go('set/' + f.id)} aria-label={f.title}>
          <Cover seed={f.id} color={f.color} src={f.cover} kind={f.kind} label={f.label} grain />
        </button>
        <div class="hero-info">
          <span class="overline">{first.title}</span>
          <h2 class="display">{f.title}</h2>
          <p class="muted artists">{f.subtitle}</p>
          <div class="hero-actions">
            <PlayButton playing={player.contextId === f.id && player.playing} onclick={() => play(f.id)} size={64} />
            <button class="icon-btn tonal l" onclick={() => playSet(f.id, true)} aria-label="Перемешать"><Icon name="shuffle" /></button>
            <button class="btn glassy" onclick={() => go('set/' + f.id)}>Открыть <Icon name="arrow_forward" size={20} /></button>
          </div>
        </div>
      {/key}
      <div class="others">
        <span class="overline">Ещё</span>
        {#each first.sets.slice(0, 6) as id (id)}
          {@const m = set(id)}
          <button class="other" class:on={id === f.id} onclick={() => (featured = id)}>
            <span class="mini"><Cover seed={m.id} color={m.color} src={m.cover} kind={m.kind} label={m.label} /></span>
            <span class="other-text">
              <span class="ellipsis other-title">{m.title}</span>
              <span class="ellipsis other-sub">{m.subtitle}</span>
            </span>
          </button>
        {/each}
      </div>
    </section>

    <Favorites service="sc" />

    {#each sections.slice(first.sets.length > 6 ? 0 : 1) as section (section.title)}
      <Shelf title={section.title}>
        {#each section.sets as id (id)}<SetCard {id} size={180} />{/each}
      </Shelf>
    {/each}
  {:else}
    <Favorites service="sc" />
    <Problem title="Пусто" message="SoundCloud ничего не предложил. Попробуй позже." onretry={() => (data = live.home('sc'))} />
  {/if}
{:catch e}
  <Problem message={reason(e)} onretry={() => (data = live.home('sc'))} />
{/await}

<style>
  .hero {
    margin-top: 32px;
    border-radius: var(--r-2xl);
    padding: 22px;
    display: grid;
    grid-template-columns: auto minmax(0, 1fr) minmax(240px, 300px);
    gap: 28px;
    align-items: center;
  }
  .hero-art {
    width: clamp(180px, 28cqi, 290px);
    aspect-ratio: 1;
    border-radius: var(--r-2xl);
    overflow: hidden;
    box-shadow: var(--shadow);
    animation: rise var(--d-slow) var(--spring);
    transition:
      transform var(--d-spatial) var(--spring),
      border-radius var(--d-spatial) var(--spring);
  }
  .hero-art:hover {
    transform: rotate(-2deg) scale(1.02);
    border-radius: 48px;
  }
  .hero-info {
    min-width: 0;
    animation: rise var(--d-slow) var(--spring) 60ms both;
  }
  .hero-info h2 {
    font-size: clamp(30px, 4.4cqi, 46px);
    margin: 10px 0 8px;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }
  .artists {
    font-size: 16px;
    max-width: 52ch;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }
  .hero-actions {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-top: 24px;
  }
  .others {
    display: flex;
    flex-direction: column;
    gap: 4px;
    align-self: stretch;
    padding-left: 22px;
    border-left: 1px solid var(--glass-line);
  }
  .others .overline {
    padding: 4px 8px 8px;
  }
  .other {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 6px;
    border-radius: var(--r-m);
    text-align: left;
    transition:
      background-color 200ms,
      border-radius var(--d-fast) var(--spring-fast);
  }
  .other:hover {
    background: var(--state-hover);
  }
  .other.on {
    background: color-mix(in oklab, var(--primary) 16%, transparent);
    border-radius: var(--r-l);
  }
  .mini {
    width: 46px;
    height: 46px;
    border-radius: var(--r-s);
    overflow: hidden;
    flex: none;
  }
  .other-text {
    display: flex;
    flex-direction: column;
    min-width: 0;
  }
  .other-title {
    font-weight: 650;
  }
  .other.on .other-title {
    color: var(--primary);
  }
  .other-sub {
    font-size: 12.5px;
    color: var(--on-surface-variant);
  }
  /* A narrow column: the other mixes go under the featured one, side by side. */
  @container page (max-width: 900px) {
    .hero {
      grid-template-columns: auto minmax(0, 1fr);
    }
    .others {
      grid-column: 1 / -1;
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(190px, 1fr));
      padding: 14px 0 0;
      border-left: none;
      border-top: 1px solid var(--glass-line);
    }
    .others .overline {
      grid-column: 1 / -1;
    }
  }
</style>
