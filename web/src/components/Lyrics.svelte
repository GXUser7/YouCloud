<script lang="ts">
  // Synced lyrics: the line being sung stands out and stays in the middle; a line clicked jumps
  // the track to it. Yandex's own for its tracks, LRCLIB's for the rest.
  import { lyricsFor, type Line } from '../lib/lyrics'
  import { lyrics as designLyrics } from '../lib/mock'
  import { currentTrack, player, seek } from '../lib/state.svelte'
  import LoadingIndicator from './LoadingIndicator.svelte'

  let { size = 'm', onsource }: { size?: 'm' | 'l'; onsource?: (source: string | null) => void } = $props()

  const t = $derived(currentTrack())
  let phase = $state<'loading' | 'none' | 'ready'>('loading')
  let lines = $state<Line[]>([])
  let synced = $state(true)

  $effect(() => {
    const track = t
    if (!track) {
      phase = 'none'
      return
    }
    // The design's stand-in tracks — no service behind them — keep the design's lyrics.
    if (!track.sc && !track.ya && !track.yt) {
      lines = designLyrics.map(([at, text]) => ({ at, text }))
      synced = true
      phase = 'ready'
      onsource?.('пример')
      return
    }
    phase = 'loading'
    let alive = true
    lyricsFor(track).then((found) => {
      if (!alive) return
      lines = found?.lines ?? []
      synced = found?.synced ?? true
      phase = found ? 'ready' : 'none'
      onsource?.(found?.source ?? null)
    })
    return () => (alive = false)
  })

  let box = $state<HTMLDivElement>()
  const current = $derived.by(() => {
    if (!synced) return -1
    let index = 0
    for (let i = 0; i < lines.length; i++) if (lines[i].at <= player.position) index = i
    return index
  })

  $effect(() => {
    const line = box?.children[current] as HTMLElement | undefined
    if (!line || !box) return
    box.scrollTo({ top: line.offsetTop - box.clientHeight / 2 + line.clientHeight / 2, behavior: 'smooth' })
  })
</script>

{#if phase === 'loading'}
  <div class="state"><LoadingIndicator size={48} /></div>
{:else if phase === 'none'}
  <div class="state muted">
    <p class="none">У этого трека нет текста</p>
  </div>
{:else}
  <div class="lyrics {size}" class:plain={!synced} bind:this={box}>
    {#each lines as line, i (i)}
      {#if synced}
        <button class="line" class:past={i < current} class:now={i === current} onclick={() => seek(line.at)}>{line.text || '♪'}</button>
      {:else}
        <p class="line">{line.text || ' '}</p>
      {/if}
    {/each}
  </div>
{/if}

<style>
  .state {
    height: 100%;
    display: grid;
    place-items: center;
    text-align: center;
    padding: 24px;
  }
  .none {
    font-family: var(--font-display);
    font-weight: 600;
    font-size: 17px;
    max-width: 22ch;
  }
  .lyrics {
    height: 100%;
    overflow-y: auto;
    padding: 40% 4px;
    scrollbar-width: none;
    mask-image: linear-gradient(transparent, #000 18%, #000 82%, transparent);
  }
  .lyrics::-webkit-scrollbar {
    display: none;
  }
  .line {
    display: block;
    text-align: left;
    width: 100%;
    padding: 6px 8px;
    border-radius: var(--r-s);
    font-family: var(--font-display);
    font-weight: 600;
    font-size: 19px;
    line-height: 1.3;
    letter-spacing: -0.015em;
    color: color-mix(in oklab, var(--on-surface) 46%, transparent);
    transform-origin: left center;
    transition:
      color 400ms var(--ease-emph),
      transform 500ms var(--spring),
      background-color 200ms;
  }
  .l .line {
    font-size: 26px;
    padding: 8px 10px;
  }
  button.line:hover {
    background: var(--state-hover);
  }
  .plain {
    padding-top: 12px;
  }
  .plain .line {
    margin: 0;
    color: color-mix(in oklab, var(--on-surface) 82%, transparent);
    font-size: 17px;
    min-height: 1.3em;
  }
  .plain.l .line {
    font-size: 22px;
  }
  .line.past {
    color: color-mix(in oklab, var(--on-surface) 30%, transparent);
  }
  .line.now {
    color: var(--primary);
    transform: scale(1.04);
  }
</style>
