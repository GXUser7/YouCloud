<script lang="ts">
  // Asks each service for something real through the Worker and shows what came back: the first
  // check anyone makes when a service stops answering.
  import type { Service } from '../lib/mock'
  import { SERVICES } from '../lib/mock'
  import * as soundcloud from '../lib/services/soundcloud'
  import * as yandex from '../lib/services/yandex'
  import * as youtube from '../lib/services/youtube'
  import { extension, onExtension } from '../lib/extension'
  import { routeOf, type Route } from '../lib/net'
  import Icon from './Icon.svelte'
  import LoadingIndicator from './LoadingIndicator.svelte'
  import ServiceGlyph from './ServiceGlyph.svelte'

  type Result = { state: 'idle' | 'busy' | 'ok' | 'warn' | 'fail'; ms?: number; detail?: string; route?: Route }

  const QUERY = 'muse'
  const probes: Record<Service, () => Promise<string>> = {
    sc: async () => {
      const [first] = await soundcloud.searchTracks(QUERY, 1)
      return first ? `${first.user?.username} — ${first.title}` : 'ответ пустой'
    },
    ya: async () => {
      const [first] = await yandex.searchTracks(QUERY)
      return first ? `${first.artists.map((a) => a.name).join(', ')} — ${first.title}` : 'ответ пустой'
    },
    yt: async () => {
      const [first] = await youtube.searchSongs(QUERY)
      if (!first) return 'ответ пустой'
      // Search answers anyone; the sound is what YouTube withholds from addresses it distrusts.
      const sound = await youtube.playability(first.videoId)
      const found = `${first.artists} — ${first.title}`
      if (sound.status !== 'OK' || !sound.audio.length) throw new SoundRefused(`${found} · аудио нет: ${sound.reason || sound.status}`)
      return `${found} · аудио есть`
    },
  }

  /** The service answered, but not with everything: shown as a warning, not a failure. */
  class SoundRefused extends Error {}

  const HOSTS: Record<Service, string> = {
    sc: 'https://api-v2.soundcloud.com/',
    ya: 'https://api.music.yandex.net/',
    yt: 'https://www.youtube.com/',
  }

  let ext = $state<string | null>(null)
  $effect(() => {
    extension().then((v) => (ext = v))
    return onExtension((v) => (ext = v))
  })

  let results = $state<Record<Service, Result>>({ sc: { state: 'idle' }, ya: { state: 'idle' }, yt: { state: 'idle' } })
  const busy = $derived(Object.values(results).some((r) => r.state === 'busy'))

  async function run() {
    for (const s of SERVICES) {
      const route = await routeOf(HOSTS[s.id])
      results[s.id] = { state: 'busy', route }
      const started = performance.now()
      const ms = () => Math.round(performance.now() - started)
      probes[s.id]()
        .then((detail) => (results[s.id] = { state: 'ok', ms: ms(), detail, route }))
        .catch((e: Error) => (results[s.id] = { state: e instanceof SoundRefused ? 'warn' : 'fail', ms: ms(), detail: e.message, route }))
    }
  }
</script>

<div class="check">
  {#each SERVICES as s (s.id)}
    {@const r = results[s.id]}
    <div class="row" data-state={r.state}>
      <span class="glyph"><ServiceGlyph service={s.id} size={22} /></span>
      <div class="text">
        <span class="name">{s.name}{#if r.route}<span class="route">{r.route === 'extension' ? 'через расширение' : 'через Worker'}</span>{/if}</span>
        <span class="detail ellipsis">
          {#if r.state === 'idle'}не проверялся{:else if r.state === 'busy'}спрашиваю «{QUERY}»…{:else}{r.detail}{/if}
        </span>
      </div>
      <span class="status">
        {#if r.state === 'busy'}
          <LoadingIndicator size={32} contained={false} />
        {:else if r.state === 'ok'}
          <span class="ms">{r.ms} мс</span><Icon name="check_circle" fill size={24} />
        {:else if r.state === 'warn'}
          <span class="ms">{r.ms} мс</span><Icon name="warning" fill size={24} />
        {:else if r.state === 'fail'}
          <Icon name="error" fill size={24} />
        {/if}
      </span>
    </div>
  {/each}
  <p class="ext muted">
    <Icon name="extension" size={18} />
    {ext ? `Расширение YouCloud ${ext} подключено: Яндекс и YouTube идут с твоего IP` : 'Расширения нет: Яндекс и аудио YouTube с Cloudflare не работают'}
  </p>
  <button class="btn tonal" disabled={busy} onclick={run}><Icon name="network_check" size={20} />Проверить связь</button>
</div>

<style>
  .check {
    display: flex;
    flex-direction: column;
    gap: 4px;
  }
  .row {
    display: flex;
    align-items: center;
    gap: 14px;
    padding: 10px 0;
    border-top: 1px solid var(--glass-line);
  }
  .glyph {
    width: 44px;
    height: 44px;
    border-radius: 34%;
    display: grid;
    place-items: center;
    flex: none;
    background: color-mix(in oklab, var(--on-surface) 8%, transparent);
    color: var(--on-surface-variant);
    transition:
      border-radius var(--d-spatial) var(--spring),
      background-color 300ms;
  }
  [data-state='ok'] .glyph {
    background: var(--primary-container);
    color: var(--on-primary-container);
    border-radius: 50%;
  }
  [data-state='fail'] .glyph {
    background: var(--error-container);
    color: var(--on-error-container);
  }
  .text {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
  }
  .name {
    font-weight: 700;
    display: flex;
    align-items: baseline;
    gap: 8px;
  }
  .route {
    font-size: 11.5px;
    font-weight: 650;
    color: var(--on-surface-variant);
  }
  .ext {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 13px;
    padding-top: 10px;
    border-top: 1px solid var(--glass-line);
  }
  [data-state='warn'] .detail,
  [data-state='warn'] .status {
    color: var(--tertiary);
  }
  .detail {
    font-size: 13px;
    color: var(--on-surface-variant);
  }
  [data-state='fail'] .detail {
    color: var(--error);
  }
  .status {
    display: flex;
    align-items: center;
    gap: 8px;
    color: var(--primary);
    flex: none;
  }
  [data-state='fail'] .status {
    color: var(--error);
  }
  .ms {
    font-size: 13px;
    font-variant-numeric: tabular-nums;
    color: var(--on-surface-variant);
  }
  .btn {
    align-self: flex-start;
    margin-top: 10px;
  }
</style>
