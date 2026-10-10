<script lang="ts">
  // A track's menu, as long-pressing a cover opens one in the app: radio from the track, its album,
  // its artist, its page on the service. Placed on the page itself so no scrolling list clips it.
  import { serviceName, track as trackOf } from '../lib/catalog'
  import { go, startRadio } from '../lib/state.svelte'
  import { toast } from '../lib/toast.svelte'
  import Icon from './Icon.svelte'

  let {
    id,
    small = true,
    large = false,
    onleave,
  }: {
    id: string
    small?: boolean
    /** The full player's big round button rather than a row's small one. */
    large?: boolean
    /** Called before the menu leads to another page (the full player closes itself). */
    onleave?: () => void
  } = $props()

  const t = $derived(trackOf(id))
  let open = $state(false)
  let button: HTMLButtonElement
  let at = $state({ top: 0, right: 0, up: false })

  function toggle(e: MouseEvent) {
    e.stopPropagation()
    if (open) {
      open = false
      return
    }
    const r = button.getBoundingClientRect()
    const up = r.bottom + 240 > innerHeight
    at = { top: up ? r.top - 6 : r.bottom + 6, right: innerWidth - r.right, up }
    open = true
  }

  function act(f: () => void) {
    open = false
    f()
  }

  function leave(path: string) {
    onleave?.()
    go(path)
  }

  async function copyLink() {
    const link = t.link!
    let done = false
    try {
      await navigator.clipboard.writeText(link)
      done = true
    } catch {
      // Where the Clipboard API is refused, the old way through a selected field still copies.
      const field = Object.assign(document.createElement('textarea'), { value: link, readOnly: true })
      field.style.cssText = 'position:fixed;opacity:0'
      document.body.append(field)
      field.select()
      done = document.execCommand('copy')
      field.remove()
    }
    toast(done ? 'Ссылка скопирована' : 'Не получилось скопировать ссылку')
  }
</script>

<svelte:window onclick={() => (open = false)} onkeydown={(e) => e.key === 'Escape' && (open = false)} onresize={() => (open = false)} />

<button class="icon-btn more" class:s={small && !large} class:l={large} class:glassy={large} bind:this={button} onclick={toggle} aria-label="Ещё" aria-expanded={open}>
  <Icon name="more_horiz" size={large ? 26 : 22} />
</button>

{#if open}
  <div class="menu glass-strong" class:up={at.up} style:top="{at.top}px" style:right="{at.right}px" role="menu">
    <button role="menuitem" onclick={() => act(() => startRadio(id))}><Icon name="radio" size={20} />Радио по треку</button>
    {#if t.album}
      <button role="menuitem" onclick={() => act(() => leave('set/' + t.album!.id))}><Icon name="album" size={20} /><span class="ellipsis">{t.album.title || 'Альбом'}</span></button>
    {/if}
    {#each t.artists.slice(0, 3) as a (a.id)}
      <button role="menuitem" onclick={() => act(() => leave('artist/' + a.id))}><Icon name="person" size={20} /><span class="ellipsis">{a.name}</span></button>
    {/each}
    {#if t.link}
      <button role="menuitem" onclick={() => act(copyLink)}><Icon name="link" size={20} />Скопировать ссылку</button>
      <a role="menuitem" href={t.link} target="_blank" rel="noreferrer" onclick={() => (open = false)}>
        <Icon name="open_in_new" size={20} />Открыть в {serviceName(t.service)}
      </a>
    {/if}
  </div>
{/if}

<style>
  .menu {
    position: fixed;
    z-index: 30;
    min-width: 220px;
    max-width: 300px;
    padding: 6px;
    border-radius: var(--r-l);
    box-shadow: var(--shadow);
    display: flex;
    flex-direction: column;
    animation: drop var(--d-spatial) var(--spring-fast);
    transform-origin: top right;
  }
  .menu.up {
    transform: translateY(-100%);
    transform-origin: bottom right;
    animation-name: lift;
  }
  @keyframes drop {
    from {
      opacity: 0;
      transform: scale(0.85);
    }
  }
  @keyframes lift {
    from {
      opacity: 0;
      transform: translateY(-100%) scale(0.85);
    }
  }
  button[role='menuitem'],
  a[role='menuitem'] {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 10px 12px;
    border-radius: var(--r-s);
    font-weight: 600;
    font-size: 14px;
    text-align: left;
    min-width: 0;
    transition:
      background-color 150ms,
      border-radius var(--d-fast) var(--spring-fast);
  }
  button[role='menuitem']:hover,
  a[role='menuitem']:hover {
    background: var(--state-hover);
    border-radius: var(--r-m);
  }
</style>
