<script lang="ts">
  import { onMount } from 'svelte'
  import { artistNames } from '../lib/catalog'
  import { extension, onExtension } from '../lib/extension'
  import * as live from '../lib/live'
  import { app, currentTrack, player, startWave, toggle, tuneWave, WAVE } from '../lib/state.svelte'
  import { reason } from '../lib/toast.svelte'
  import ButtonGroup from '../components/ButtonGroup.svelte'
  import Cover from '../components/Cover.svelte'
  import EqBars from '../components/EqBars.svelte'
  import ExtensionCard from '../components/ExtensionCard.svelte'
  import Favorites from '../components/Favorites.svelte'
  import Icon from '../components/Icon.svelte'
  import Loading from '../components/Loading.svelte'
  import PageHeader from '../components/PageHeader.svelte'
  import Problem from '../components/Problem.svelte'
  import SetCard from '../components/SetCard.svelte'
  import Shelf from '../components/Shelf.svelte'
  import WaveShape from '../components/WaveShape.svelte'

  let data = $state(live.home('ya'))
  const signedIn = $derived(live.signedIn('ya'))
  let wasSignedIn = live.signedIn('ya')
  $effect(() => {
    if (signedIn !== wasSignedIn) {
      wasSignedIn = signedIn
      data = live.home('ya')
    }
  })

  // Yandex answers only the listener's own IP: without the extension there is nothing to show.
  let ext = $state<string | null | undefined>(undefined)
  extension().then((v) => (ext = v))
  onMount(() =>
    onExtension((v) => {
      if (ext === null) data = live.home('ya')
      ext = v
    }),
  )

  const mood = $derived(WAVE.moods.find((m) => m.seed === app.waveMood))
  const t = $derived(currentTrack())
  let starting = $state(false)

  async function wave() {
    if (player.wave) {
      toggle()
      return
    }
    starting = true
    await startWave()
    starting = false
  }
</script>

<PageHeader title="Яндекс Музыка" subtitle="Моя волна, твоё любимое и подборки" />

{#if ext === null}
  <ExtensionCard why="Яндекс Музыка отвечает только твоему IP, а не Cloudflare: подборки, поиск и звук идут через расширение." />
{:else if signedIn}
  <section class="wave glass rise">
    <div class="shape-col">
      <WaveShape lobes={mood?.lobes ?? 10} depth={mood?.depth ?? 0.07} playing={(player.wave && player.playing) || starting} onclick={wave} />
    </div>
    <div class="tune">
      <div class="tune-head">
        <h2 class="headline">Настроить волну</h2>
        <button
          class="btn text small"
          disabled={!app.waveMood && !app.waveMode}
          onclick={() => {
            tuneWave('mood', null)
            tuneWave('mode', null)
          }}>Сбросить</button
        >
      </div>
      <p class="overline label">Настроение</p>
      <ButtonGroup
        options={WAVE.moods.map((m) => ({ value: m.seed, label: m.title }))}
        value={app.waveMood}
        allowNone
        size="l"
        stretch
        onchange={(v) => tuneWave('mood', v)}
      />
      <p class="overline label">Режим</p>
      <ButtonGroup
        options={WAVE.modes.map((m) => ({ value: m.seed, label: m.title }))}
        value={app.waveMode}
        allowNone
        size="l"
        stretch
        onchange={(v) => tuneWave('mode', v)}
      />
      <div class="now" class:on={player.wave && t}>
        {#if player.wave && t}
          <span class="now-cover"><Cover seed={t.id} color={t.color} src={t.cover} /></span>
          <span class="now-text">
            <span class="overline">В волне сейчас</span>
            <span class="ellipsis now-title">{t.title} — {artistNames(t)}</span>
          </span>
          <span class="eq"><EqBars playing={player.playing} size={18} /></span>
        {:else}
          <span class="hint"><Icon name="waves" size={22} />{starting ? 'Волна подбирает треки…' : 'Нажми на форму — волна подстроится под «Нравится» и пропуски'}</span>
        {/if}
      </div>
    </div>
  </section>
{/if}

{#if ext !== null}
<Favorites service="ya" />
{#await data}
  <Loading text="Спрашиваю Яндекс…" tall={false} />
{:then sections}
  {#each sections as section (section.title)}
    <Shelf title={section.title}>
      {#each section.sets as id (id)}<SetCard {id} size={184} />{/each}
    </Shelf>
  {/each}
{:catch e}
  <Problem message={reason(e)} onretry={() => (data = live.home('ya'))} />
{/await}
{/if}

<style>
  .wave {
    margin-top: 32px;
    border-radius: var(--r-2xl);
    padding: 28px 36px;
    display: grid;
    grid-template-columns: minmax(240px, 380px) minmax(0, 1fr);
    gap: 48px;
    align-items: center;
  }
  .shape-col {
    padding: 8px;
  }
  .tune-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }
  .tune-head h2 {
    font-size: 28px;
  }
  .label {
    margin: 22px 0 10px;
  }
  .now {
    margin-top: 28px;
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 10px 14px 10px 10px;
    border-radius: var(--r-xl);
    background: color-mix(in oklab, var(--on-surface) 6%, transparent);
    min-height: 68px;
    transition:
      background-color 300ms,
      border-radius var(--d-spatial) var(--spring);
  }
  .now.on {
    background: color-mix(in oklab, var(--primary) 15%, transparent);
    border-radius: var(--r-l);
  }
  .now-cover {
    width: 48px;
    height: 48px;
    border-radius: 50%;
    overflow: hidden;
    flex: none;
    animation: spin 12s linear infinite;
  }
  @keyframes spin {
    to {
      transform: rotate(360deg);
    }
  }
  .now-text {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
  }
  .now-title {
    font-weight: 650;
  }
  .eq {
    color: var(--primary);
  }
  .hint {
    display: flex;
    align-items: center;
    gap: 10px;
    color: var(--on-surface-variant);
    padding-left: 6px;
  }
</style>
