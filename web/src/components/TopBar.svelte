<script lang="ts">
  // The bar over the page: the way back from a set, an artist or a setting; search in the service
  // the rail has chosen (typing opens the results); and the listener — friends and settings live
  // behind their picture, out of the way of the music.
  import { serviceName } from '../lib/catalog'
  import { avatarUrl, shownName, social } from '../lib/social.svelte'
  import { app, go } from '../lib/state.svelte'
  import Avatar from './Avatar.svelte'
  import Icon from './Icon.svelte'
  import ServiceGlyph from './ServiceGlyph.svelte'

  let input = $state<HTMLInputElement>()
  let menu = $state(false)

  const home = $derived(app.route.name === 'home')

  function back() {
    if (history.length > 1) history.back()
    else go('home')
  }

  function typed() {
    if (app.route.name !== 'search') go('search')
  }

  // Leaving the results lets the words go, so the field says where it will search next.
  $effect(() => {
    if (app.route.name !== 'search' && document.activeElement !== input) app.query = ''
  })

  // Ctrl+K or / from anywhere: to the search field, as in the browser it sits in.
  function keys(e: KeyboardEvent) {
    const typing = e.target instanceof HTMLInputElement || e.target instanceof HTMLTextAreaElement
    if ((e.key === 'k' && (e.ctrlKey || e.metaKey)) || (e.key === '/' && !typing)) {
      e.preventDefault()
      input?.focus()
      input?.select()
    }
    if (e.key === 'Escape') menu = false
  }

  function place(route: string) {
    menu = false
    go(route)
  }
</script>

<svelte:window onkeydown={keys} />

<header class="topbar">
  {#if !home}
    <button class="icon-btn glassy" onclick={back} aria-label="Назад" title="Назад">
      <Icon name="arrow_back" size={22} />
    </button>
  {/if}

  <label class="search glass-strong" class:on={app.route.name === 'search'}>
    <span class="glyph"><ServiceGlyph service={app.service} size={20} /></span>
    <input
      bind:this={input}
      bind:value={app.query}
      oninput={typed}
      onfocus={() => app.query && typed()}
      placeholder="Поиск в {serviceName(app.service)}"
      spellcheck="false"
      autocomplete="off"
      enterkeyhint="search"
    />
    {#if app.query}
      <button class="icon-btn s" onclick={() => ((app.query = ''), input?.focus())} aria-label="Очистить"><Icon name="close" size={20} /></button>
    {:else}
      <Icon name="search" size={22} />
    {/if}
  </label>

  <span class="grow"></span>

  <div class="me-wrap">
    <button class="me" class:open={menu} onclick={() => (menu = !menu)} aria-haspopup="menu" aria-expanded={menu} title="Профиль, друзья, настройки">
      {#if social.me}
        <Avatar name={shownName(social.me)} color={social.me.color} src={avatarUrl(social.me)} size={40} />
      {:else}
        <span class="no-me"><Icon name="person" size={22} /></span>
      {/if}
    </button>
    {#if menu}
      <button class="scrim" aria-label="Закрыть меню" onclick={() => (menu = false)}></button>
      <div class="menu glass-strong" role="menu">
        <div class="who">
          {#if social.me}
            <Avatar name={shownName(social.me)} color={social.me.color} src={avatarUrl(social.me)} size={44} />
            <span class="who-text"><b class="ellipsis">{shownName(social.me)}</b><small class="muted">Аккаунт YouCloud</small></span>
          {:else}
            <span class="no-me big"><Icon name="person" size={24} /></span>
            <span class="who-text"><b>Гость</b><small class="muted">Войди, чтобы видеть друзей</small></span>
          {/if}
        </div>
        <button class="item" role="menuitem" onclick={() => place('friends')}><Icon name="group" size={22} />Друзья</button>
        <button class="item" role="menuitem" onclick={() => place('settings')}><Icon name="settings" size={22} />Настройки</button>
      </div>
    {/if}
  </div>
</header>

<style>
  .topbar {
    position: relative;
    z-index: 5;
    flex: none;
    display: flex;
    align-items: center;
    gap: 10px;
    width: 100%;
    max-width: calc(1480px + 72px);
    margin: 0 auto;
    padding: 0 36px 8px;
  }
  .search {
    display: flex;
    align-items: center;
    gap: 10px;
    height: 52px;
    width: min(560px, 100%);
    padding: 0 8px 0 16px;
    border-radius: var(--r-full);
    color: var(--on-surface-variant);
    cursor: text;
    transition:
      border-radius var(--d-fast) var(--spring-fast),
      box-shadow 200ms var(--ease-emph);
  }
  .search:focus-within,
  .search.on {
    border-radius: var(--r-l);
    box-shadow: 0 0 0 2px color-mix(in oklab, var(--primary) 70%, transparent);
  }
  .search :global(.icon) {
    margin-right: 8px;
  }
  .glyph {
    display: grid;
    place-items: center;
    color: var(--primary);
  }
  input {
    flex: 1;
    min-width: 0;
    height: 100%;
    border: 0;
    outline: 0;
    background: none;
    font-size: 16px;
    color: var(--on-surface);
  }
  input::placeholder {
    color: var(--on-surface-variant);
  }
  .grow {
    flex: 1;
  }

  .me-wrap {
    position: relative;
  }
  .me {
    display: grid;
    place-items: center;
    width: 48px;
    height: 48px;
    border-radius: 50%;
    transition:
      transform var(--d-fast) var(--spring-fast),
      box-shadow 200ms var(--ease-emph);
  }
  .me:hover,
  .me.open {
    box-shadow: 0 0 0 3px color-mix(in oklab, var(--primary) 55%, transparent);
  }
  .me:active {
    transform: scale(0.92);
  }
  .no-me {
    display: grid;
    place-items: center;
    width: 40px;
    height: 40px;
    border-radius: 50%;
    background: var(--secondary-container);
    color: var(--on-secondary-container);
  }
  .no-me.big {
    width: 44px;
    height: 44px;
  }
  .scrim {
    position: fixed;
    inset: 0;
    z-index: 6;
    cursor: default;
  }
  .menu {
    position: absolute;
    right: 0;
    top: calc(100% + 8px);
    z-index: 7;
    width: 260px;
    padding: 8px;
    border-radius: var(--r-xl);
    box-shadow: var(--shadow);
    transform-origin: top right;
    animation: pop var(--d-fast) var(--spring-fast);
  }
  @keyframes pop {
    from {
      opacity: 0;
      transform: scale(0.9);
    }
  }
  .who {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 10px 10px 14px;
  }
  .who-text {
    display: flex;
    flex-direction: column;
    min-width: 0;
  }
  .who-text small {
    font-size: 13px;
  }
  .item {
    display: flex;
    align-items: center;
    gap: 14px;
    width: 100%;
    height: 48px;
    padding: 0 14px;
    border-radius: var(--r-l);
    font-weight: 600;
    transition:
      background-color 150ms var(--ease-emph),
      border-radius var(--d-fast) var(--spring-fast);
  }
  .item:hover {
    background: var(--state-hover);
  }
  .item:active {
    border-radius: var(--r-s);
    background: var(--state-press);
  }
</style>
