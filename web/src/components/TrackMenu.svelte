<script lang="ts">
  import { tick } from 'svelte'
  import { tr } from '../lib/i18n.svelte'
  import { serviceName, track as trackOf } from '../lib/catalog'
  import { portal } from '../lib/portal'
  import { go, startRadio } from '../lib/state.svelte'
  import { toast } from '../lib/toast.svelte'
  import Icon from './Icon.svelte'

  let { id, small = true, large = false, onleave }: {
    id: string; small?: boolean; large?: boolean; onleave?: () => void
  } = $props()
  const t = $derived(trackOf(id))
  let open = $state(false)
  let button: HTMLButtonElement
  let menu: HTMLDivElement | undefined = $state()
  let at = $state({ top: 0, left: 0, ready: false })

  function close(focus = false) {
    open = false
    if (focus) button.focus()
  }

  async function toggle(e: MouseEvent) {
    e.stopPropagation()
    if (open) return close()
    window.dispatchEvent(new Event('yc-close-track-menus'))
    at = { top: 0, left: 0, ready: false }
    open = true
    await tick()
    if (!menu || !open) return
    const r = button.getBoundingClientRect()
    const m = menu.getBoundingClientRect()
    const below = r.bottom + 8
    const top = below + m.height <= innerHeight - 12 ? below : r.top - m.height - 8
    at = {
      top: Math.max(12, Math.min(top, innerHeight - m.height - 12)),
      left: Math.max(12, Math.min(r.right - m.width, innerWidth - m.width - 12)),
      ready: true,
    }
    await tick()
    menu.querySelector<HTMLElement>('[role="menuitem"]')?.focus({ preventScroll: true })
  }

  function outside(e: PointerEvent) {
    if (open && !menu?.contains(e.target as Node) && !button.contains(e.target as Node)) close()
  }
  function scrolling(e: Event) {
    // Only an ancestor's scroll moves the anchor. Lyrics scroll themselves in a sibling panel.
    const target = e.target
    if (open && (target === document || target instanceof Element && target.contains(button))) close()
  }
  $effect(() => {
    id
    close()
  })
  $effect(() => {
    const dismiss = () => close()
    window.addEventListener('yc-close-track-menus', dismiss)
    document.addEventListener('scroll', scrolling, true)
    document.addEventListener('keydown', keys, true)
    return () => {
      window.removeEventListener('yc-close-track-menus', dismiss)
      document.removeEventListener('scroll', scrolling, true)
      document.removeEventListener('keydown', keys, true)
    }
  })
  function keys(e: KeyboardEvent) {
    if (!open) return
    if (e.key === 'Escape') {
      e.preventDefault()
      e.stopPropagation()
      close(true)
    } else if (['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(e.key)) {
      e.preventDefault()
      const items = Array.from(menu?.querySelectorAll<HTMLElement>('[role="menuitem"]') ?? [])
      const i = items.indexOf(document.activeElement as HTMLElement)
      const next = e.key === 'Home' ? 0 : e.key === 'End' ? items.length - 1 : (i + (e.key === 'ArrowDown' ? 1 : -1) + items.length) % items.length
      items[next]?.focus({ preventScroll: true })
    } else if (e.key === 'Tab') close()
  }
  function act(f: () => void) { close(); f() }
  function leave(path: string) { onleave?.(); go(path) }
  async function copyLink() {
    try {
      await navigator.clipboard.writeText(t.link!)
      toast(tr('Ссылка скопирована'))
    } catch { toast(tr('Не получилось скопировать ссылку')) }
  }
</script>

<svelte:window onpointerdown={outside} onresize={() => close()} />

<button class="icon-btn more" class:s={small && !large} class:l={large} class:glassy={large} bind:this={button} onclick={toggle} aria-label={tr('Ещё')} aria-haspopup="menu" aria-expanded={open}>
  <Icon name="more_horiz" size={large ? 26 : 22} />
</button>

{#if open}
  <div use:portal bind:this={menu} class="track-menu glass-overlay glass-overlay-menu" style:top="{at.top}px" style:left="{at.left}px" style:visibility={at.ready ? 'visible' : 'hidden'} role="menu" aria-label={tr('Действия с треком')}>
    <button role="menuitem" onclick={() => act(() => startRadio(id))}><Icon name="radio" size={20} /><span>{tr('Радио по треку')}</span></button>
    {#if t.album}
      <button role="menuitem" onclick={() => act(() => leave('set/' + t.album!.id))}><Icon name="album" size={20} /><span class="ellipsis">{t.album.title || tr('Альбом')}</span></button>
    {/if}
    {#each t.artists.slice(0, 3) as a (a.id)}
      <button role="menuitem" onclick={() => act(() => leave('artist/' + a.id))}><Icon name="person" size={20} /><span class="ellipsis">{a.name}</span></button>
    {/each}
    {#if t.link}
      <button role="menuitem" onclick={() => act(copyLink)}><Icon name="link" size={20} /><span>{tr('Скопировать ссылку')}</span></button>
      <a role="menuitem" href={t.link} target="_blank" rel="noreferrer" onclick={() => close()}><Icon name="open_in_new" size={20} /><span>{tr('Открыть в %s', serviceName(t.service))}</span></a>
    {/if}
  </div>
{/if}

<style>
  .track-menu {
    position: fixed;
    z-index: 100;
    width: max-content;
    min-width: min(240px, calc(100vw - 24px));
    max-width: min(320px, calc(100vw - 24px));
    max-height: calc(100dvh - 24px);
    overflow-y: auto;
    overscroll-behavior: contain;
    padding: 8px;
    border-radius: var(--r-l);
    display: flex;
    flex-direction: column;
  }
  [role='menuitem'] {
    display: flex;
    align-items: center;
    gap: 12px;
    min-height: 48px;
    padding: 10px 12px;
    border-radius: var(--r-m);
    font-weight: 600;
    font-size: 14px;
    text-align: left;
    min-width: 0;
    transition: background-color 150ms;
  }
  [role='menuitem'] > span:last-child { min-width: 0; }
  [role='menuitem']:hover, [role='menuitem']:focus-visible { background: var(--state-hover); }
</style>
