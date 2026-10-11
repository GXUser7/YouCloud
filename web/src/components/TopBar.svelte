<script lang="ts">
  // The bar over the page, everything of the site in one row: YouCloud and the way back on the
  // left; in the middle the search, with the services inside it — which one is chosen is both what
  // the page shows and where the search looks; the listener's friends and menu on the right.
  import { SERVICES, type Service } from '../lib/catalog'
  import { signedIn } from '../lib/live'
  import { avatarUrl, shownName, social } from '../lib/social.svelte'
  import { app, go, setService } from '../lib/state.svelte'
  import AppIcon from './AppIcon.svelte'
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

  function pick(svc: Service) {
    const same = app.service === svc
    setService(svc)
    // A service is a place of its own: picking one opens its page; picking it again, from any
    // page of it, comes back there too. Search keeps its words and asks the new service.
    if (app.route.name !== 'search' || same) go('home')
  }

  function typed() {
    if (app.route.name !== 'search') go('search')
  }

  // Leaving the results lets the words go, so the field is empty for the next search.
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
  <div class="start">
    <button class="brand" onclick={() => go('home')} aria-label="YouCloud — главная" title="YouCloud">
      <AppIcon size={44} />
      <span class="wordmark">YouCloud</span>
    </button>
    {#if !home}
      <button class="icon-btn glassy" onclick={back} aria-label="Назад" title="Назад">
        <Icon name="arrow_back" size={22} />
      </button>
    {/if}
  </div>

  <div class="search glass-strong" class:on={app.route.name === 'search'}>
    <div class="services" role="radiogroup" aria-label="Сервис: где искать и что показать">
      {#each SERVICES as s (s.id)}
        {@const on = app.service === s.id}
        <button class="service" class:on role="radio" aria-checked={on} aria-label={s.name} title={s.name} onclick={() => pick(s.id)}>
          <span class="glyph">
            <ServiceGlyph service={s.id} size={21} />
            {#if signedIn(s.id)}<span class="dot" aria-hidden="true"></span>{/if}
          </span>
          {#if on}<span class="label">{s.short}</span>{/if}
        </button>
      {/each}
    </div>
    <label class="field">
      <input
        bind:this={input}
        bind:value={app.query}
        oninput={typed}
        onfocus={() => app.query && typed()}
        placeholder="Найти трек, артиста или подборку"
        aria-label="Поиск"
        spellcheck="false"
        autocomplete="off"
        enterkeyhint="search"
      />
      {#if app.query}
        <button class="icon-btn s" onclick={() => ((app.query = ''), input?.focus())} aria-label="Очистить"><Icon name="close" size={20} /></button>
      {:else}
        <span class="lens"><Icon name="search" size={22} /></span>
      {/if}
    </label>
  </div>

  <div class="end">
    <button class="icon-btn glassy" class:active={app.route.name === 'liked'} onclick={() => go('liked')} aria-label="Любимое" title="Любимое">
      <Icon name="favorite" fill={app.route.name === 'liked'} size={22} />
    </button>
    <button class="icon-btn glassy" class:active={app.route.name === 'friends'} onclick={() => go('friends')} aria-label="Друзья" title="Друзья">
      <Icon name="group" size={22} />
    </button>
    <div class="me-wrap">
      <button class="me" class:open={menu} onclick={() => (menu = !menu)} aria-haspopup="menu" aria-expanded={menu} title="Профиль и настройки">
        {#if social.me}
          <Avatar name={shownName(social.me)} color={social.me.color} src={avatarUrl(social.me)} size={44} />
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
          <button class="item" role="menuitem" onclick={() => place('liked')}><Icon name="favorite" size={22} />Любимое</button>
          <button class="item" role="menuitem" onclick={() => place('friends')}><Icon name="group" size={22} />Друзья</button>
          <button class="item" role="menuitem" onclick={() => place('settings')}><Icon name="settings" size={22} />Настройки</button>
        </div>
      {/if}
    </div>
  </div>
</header>

<style>
  /* Three columns, the middle one the search: it stays in the middle whatever the sides hold. */
  .topbar {
    position: relative;
    z-index: 5;
    flex: none;
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(0, 760px) minmax(0, 1fr);
    align-items: center;
    gap: 16px;
    width: 100%;
    max-width: calc(1480px + 72px);
    margin: 0 auto;
    padding: 0 36px 10px;
  }
  .start,
  .end {
    display: flex;
    align-items: center;
    gap: 8px;
    min-width: 0;
  }
  .end {
    justify-content: flex-end;
  }

  .brand {
    display: flex;
    align-items: center;
    gap: 10px;
    min-width: 0;
    padding-right: 6px;
    border-radius: var(--r-l);
    transition: transform var(--d-fast) var(--spring-fast);
  }
  .brand:active {
    transform: scale(0.95);
  }
  .wordmark {
    font-family: var(--font-display);
    font-weight: 700;
    font-size: 19px;
    letter-spacing: -0.01em;
    color: var(--on-surface);
    white-space: nowrap;
  }

  .search {
    display: flex;
    align-items: center;
    gap: 10px;
    height: 58px;
    padding: 5px 8px 5px 5px;
    border-radius: var(--r-full);
    box-shadow: 0 10px 30px -18px color-mix(in oklab, #000 70%, transparent);
    transition:
      border-radius var(--d-fast) var(--spring-fast),
      box-shadow 200ms var(--ease-emph);
  }
  .search:focus-within,
  .search.on {
    box-shadow: 0 0 0 2px color-mix(in oklab, var(--primary) 70%, transparent);
  }

  /* The services as a well inside the field: the chosen one grows a name and a squircle. */
  .services {
    flex: none;
    display: flex;
    gap: 3px;
    padding: 2px;
    border-radius: var(--r-full);
    background: color-mix(in oklab, var(--on-surface) 7%, transparent);
  }
  .service {
    display: flex;
    align-items: center;
    gap: 8px;
    height: 44px;
    min-width: 44px;
    padding: 0 11px;
    border-radius: 22px;
    color: var(--on-surface-variant);
    font-size: 15px;
    font-weight: 700;
    white-space: nowrap;
    transition:
      border-radius var(--d-spatial) var(--spring),
      background-color 220ms var(--ease-emph),
      color 220ms var(--ease-emph),
      transform var(--d-fast) var(--spring-fast);
  }
  .service:hover:not(.on) {
    background: var(--state-hover);
    color: var(--on-surface);
  }
  .service:active {
    transform: scale(0.94);
  }
  .service.on {
    padding: 0 16px 0 12px;
    border-radius: var(--r-m);
    background: var(--primary);
    color: var(--on-primary);
  }
  .glyph {
    position: relative;
    display: grid;
    place-items: center;
  }
  .label {
    animation: grow var(--d-spatial) var(--spring);
  }
  @keyframes grow {
    from {
      opacity: 0;
      transform: translateX(-6px);
    }
  }
  /* Signed in on that service. */
  .dot {
    position: absolute;
    right: -4px;
    bottom: -3px;
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: var(--primary);
    border: 2px solid var(--surface-container);
  }
  .service.on .dot {
    background: var(--on-primary);
    border-color: var(--primary);
  }

  .field {
    flex: 1;
    min-width: 0;
    height: 100%;
    display: flex;
    align-items: center;
    gap: 6px;
    color: var(--on-surface-variant);
    cursor: text;
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
  .lens {
    display: grid;
    place-items: center;
    width: 40px;
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
    width: 44px;
    height: 44px;
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

  /* Narrower: the name goes first, then the sides close in on the search. */
  @media (max-width: 1100px) {
    .wordmark {
      display: none;
    }
    .topbar {
      grid-template-columns: auto minmax(0, 1fr) auto;
      padding: 0 20px 10px;
      gap: 10px;
    }
  }
  @media (max-width: 640px) {
    .service.on .label {
      display: none;
    }
    .service.on {
      padding: 0 11px;
    }
  }
</style>
