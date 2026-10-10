<script lang="ts">
  import { SERVICES, type Service } from '../lib/catalog'
  import { openLink } from '../lib/links'
  import { avatarUrl, setShareListening, shownName, signOut, social, socialMessage, type ShowcaseItem } from '../lib/social.svelte'
  import AccountForm from '../components/AccountForm.svelte'
  import { accounts } from '../lib/accounts.svelte'
  import { connect, connectSoundCloud, disconnect, refreshYouTube } from '../lib/login'
  import { diagnose } from '../lib/services/youtube'
  import { app, remember, type ThemeMode } from '../lib/state.svelte'
  import { reason, toast } from '../lib/toast.svelte'
  import Avatar from '../components/Avatar.svelte'
  import ButtonGroup from '../components/ButtonGroup.svelte'
  import ConnectionCheck from '../components/ConnectionCheck.svelte'
  import ExtensionCard from '../components/ExtensionCard.svelte'
  import Cover from '../components/Cover.svelte'
  import Icon from '../components/Icon.svelte'
  import PageHeader from '../components/PageHeader.svelte'
  import ServiceGlyph from '../components/ServiceGlyph.svelte'
  import Switch from '../components/Switch.svelte'

  const loginHint: Record<Service, string> = {
    sc: 'Возьму сессию soundcloud.com из этого браузера или открою вход',
    ya: 'Откроется вход Яндекс ID, вкладка закроется сама',
    yt: 'Вход этого браузера на youtube.com — откроется вход Google',
  }

  const accountName = (svc: Service) =>
    svc === 'sc'
      ? accounts.sc?.name
      : svc === 'ya'
        ? accounts.ya
          ? `${accounts.ya.name}${accounts.ya.plus ? ' · Плюс' : ' · без Плюса: 30 секунд'}`
          : null
        : accounts.yt
          ? accounts.ytName ?? 'Вход этого браузера есть, но YouTube отвечает как гостю'
          : null

  let busy = $state<Service | null>(null)
  let ytReport = $state<string[] | null>(null)
  let ytChecking = $state(false)

  async function checkYouTube() {
    ytChecking = true
    try {
      ytReport = await diagnose()
      await refreshYouTube()
    } finally {
      ytChecking = false
    }
  }
  let errors = $state<Partial<Record<Service, string>>>({})
  let pasting = $state(false)
  let pasted = $state('')

  async function signIn(svc: Service) {
    busy = svc
    errors[svc] = ''
    try {
      await connect(svc)
    } catch (e) {
      errors[svc] = reason(e)
      toast(errors[svc]!)
    } finally {
      busy = null
    }
  }

  async function usePasted() {
    busy = 'sc'
    errors.sc = ''
    try {
      await connectSoundCloud(pasted)
      pasting = false
      pasted = ''
    } catch (e) {
      errors.sc = reason(e)
    } finally {
      busy = null
    }
  }

  const showcase = $derived(
    social.me
      ? ([
          ['Исполнитель', social.me.fav_artist, true],
          ['Трек', social.me.fav_track, false],
          ['Альбом', social.me.fav_album, false],
        ] as [string, ShowcaseItem | null | undefined, boolean][])
      : [],
  )
  const friendCount = $derived(social.friends.filter((p) => p.relation === 'friend').length)

  let language = $state<'ru' | 'en'>('ru')

  const inMasaki = document.documentElement.dataset.host === 'masaki'
</script>

<PageHeader title="Настройки" subtitle="Аккаунты, оформление и сайт" />

<div class="grid">
  <div class="wide"><ExtensionCard always /></div>
  <section class="card glass profile rise">
    {#if social.me}
      {@const me = social.me}
      <div class="me">
        <Avatar name={shownName(me)} color={me.color} src={avatarUrl(me)} size={84} />
        <div>
          <span class="overline">Аккаунт YouCloud</span>
          <h2 class="headline">{shownName(me)}</h2>
          <p class="muted">@{me.nick} · {friendCount} {friendCount % 10 === 1 && friendCount % 100 !== 11 ? 'друг' : friendCount % 10 >= 2 && friendCount % 10 <= 4 && (friendCount % 100 < 12 || friendCount % 100 > 14) ? 'друга' : 'друзей'}</p>
        </div>
      </div>
      <p class="overline label">Витрина</p>
      <div class="showcase">
        {#each showcase as [label, item, round]}
          <button class="tile" disabled={!item?.link} onclick={() => item?.link && openLink(item.link, `Витрина: ${label}`)}>
            <span class="tile-art" class:round>
              <Cover seed={label + (item?.title ?? '')} color={me.color ?? '#9CC2A2'} src={item?.cover} kind={round ? 'artist' : 'album'} />
            </span>
            <span class="overline tiny">{label}</span>
            <span class="ellipsis tile-title">{item?.title ?? 'Не выбран'}</span>
          </button>
        {/each}
      </div>
      <p class="muted small-note">Имя, аватар и витрина меняются в приложении.</p>
      <div class="toggle">
        <div>
          <span class="t-name">Показывать, что я слушаю</span>
          <span class="muted t-sub">Друзья видят твой трек в реальном времени</span>
        </div>
        <Switch
          checked={me.share_listening}
          label="Показывать, что я слушаю"
          onchange={(v) => setShareListening(v).catch((e) => toast(socialMessage(e)))}
        />
      </div>
      <div class="row-actions">
        <button class="btn text" onclick={() => signOut()}>Выйти из YouCloud</button>
      </div>
    {:else}
      <span class="overline">Аккаунт YouCloud</span>
      <h2 class="headline title acc-title">Друзья и витрина</h2>
      <AccountForm />
    {/if}
  </section>

  <section class="card glass rise">
    <h2 class="headline title">Сервисы</h2>
    <p class="muted lead">Токены хранятся только в этом браузере и уходят только в свой сервис — через Worker или расширение.</p>
    {#each SERVICES as s (s.id)}
      {@const name = accountName(s.id)}
      <div class="account">
        <span class="glyph" class:on={!!name}><ServiceGlyph service={s.id} size={26} /></span>
        <div class="acc-text">
          <span class="acc-name">{s.name}</span>
          <span class="muted acc-sub" class:err={!!errors[s.id]}>{errors[s.id] || name || loginHint[s.id]}</span>
        </div>
        {#if s.id === 'yt' && name}
          <a class="btn outlined small" href="https://music.youtube.com/" target="_blank" rel="noreferrer">youtube.com</a>
        {:else if name}
          <button class="btn outlined small" onclick={() => disconnect(s.id)}>Выйти</button>
        {:else}
          <button class="btn filled small" disabled={busy === s.id} onclick={() => signIn(s.id)}>
            <Icon name="login" size={18} />{busy === s.id ? 'Жду вход…' : 'Войти'}
          </button>
        {/if}
      </div>
      {#if s.id === 'yt' && accounts.yt && !accounts.ytName}
        <div class="diag">
          <button class="btn tonal small" disabled={ytChecking} onclick={checkYouTube}>
            <Icon name="network_check" size={18} />{ytChecking ? 'Проверяю…' : 'Проверить вход YouTube'}
          </button>
          {#if ytReport}
            <pre class="report">{ytReport.join('\n')}</pre>
          {/if}
        </div>
      {/if}
      {#if s.id === 'sc' && !name}
        {#if pasting}
          <div class="paste">
            <input bind:value={pasted} placeholder="oauth_token с soundcloud.com" spellcheck="false" autocomplete="off" />
            <button class="btn tonal small" disabled={!pasted.trim() || busy === 'sc'} onclick={usePasted}>Подключить</button>
          </div>
        {:else}
          <button class="btn text small paste-link" onclick={() => (pasting = true)}>Вставить токен вручную</button>
        {/if}
      {/if}
    {/each}
  </section>

  <section class="card glass rise">
    <h2 class="headline title">Оформление</h2>
    {#if inMasaki}
      <!-- In Masaki the page lies on the browser's glass: its lightness and colours are Masaki's. -->
      <p class="muted lead">Тема, палитра и фон — как в Masaki: сайт лежит на его стекле. Поменять их можно в настройках Masaki.</p>
    {:else}
    <p class="overline label">Тема</p>
    <ButtonGroup
      options={[
        { value: 'system', label: 'Системная' },
        { value: 'light', label: 'Светлая' },
        { value: 'dark', label: 'Тёмная' },
      ]}
      value={app.theme}
      stretch
      onchange={(v) => {
        if (!v) return
        app.theme = v as ThemeMode
        remember('theme', v)
      }}
    />
    {/if}
    <div class="toggle">
      <div>
        <span class="t-name">Цвета из обложки</span>
        <span class="muted t-sub">Сайт перекрашивается под трек, который играет</span>
      </div>
      <Switch
        checked={app.coverColors}
        label="Цвета из обложки"
        onchange={(v) => {
          app.coverColors = v
          remember('coverColors', v)
        }}
      />
    </div>
    {#if !inMasaki}
    <div class="toggle">
      <div>
        <span class="t-name">Живой фон</span>
        <span class="muted t-sub">Фигуры позади медленно плывут и тянутся за мышью</span>
      </div>
      <Switch
        checked={app.liveBackdrop}
        label="Живой фон"
        onchange={(v) => {
          app.liveBackdrop = v
          remember('liveBackdrop', v)
        }}
      />
    </div>
    {/if}
  </section>

  <section class="card glass rise">
    <h2 class="headline title">Связь с сервисами</h2>
    <p class="muted lead">Сайт ходит в сервисы через Worker на Cloudflare. Проверка ищет «muse» в каждом и показывает первый трек.</p>
    <ConnectionCheck />
  </section>

  <section class="card glass rise">
    <h2 class="headline title">Язык</h2>
    <ButtonGroup
      options={[
        { value: 'ru', label: 'Русский' },
        { value: 'en', label: 'English' },
      ]}
      value={language}
      stretch
      onchange={(v) => v && (language = v as 'ru' | 'en')}
    />
    <h2 class="headline title about">О сайте</h2>
    <p class="muted">YouCloud Web 0.1. Связь с сервисами уже настоящая, а страницы пока показывают тестовые данные — их заменят живые следующим шагом.</p>
    <a class="btn glassy small gh" href="https://github.com/GXUser7/YouCloud" target="_blank" rel="noreferrer">
      <Icon name="open_in_new" size={18} />YouCloud на GitHub
    </a>
  </section>
</div>

<style>
  .grid {
    margin-top: 32px;
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(420px, 1fr));
    gap: 20px;
    align-items: start;
  }
  .wide {
    grid-column: 1 / -1;
    --ext-gap: 0;
  }
  .card {
    border-radius: var(--r-2xl);
    padding: 24px;
  }
  .title {
    font-size: 22px;
  }
  .lead {
    margin: 6px 0 12px;
    font-size: 14px;
  }
  .label {
    margin: 22px 0 12px;
  }
  .me {
    display: flex;
    align-items: center;
    gap: 18px;
  }
  .me h2 {
    font-size: 28px;
    margin: 2px 0;
  }
  .showcase {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 12px;
  }
  .tile {
    display: flex;
    flex-direction: column;
    gap: 4px;
    min-width: 0;
    padding: 10px;
    border-radius: var(--r-xl);
    background: color-mix(in oklab, var(--on-surface) 5%, transparent);
  }
  .tile-art {
    width: 100%;
    aspect-ratio: 1;
    border-radius: var(--r-l);
    overflow: hidden;
    margin-bottom: 6px;
  }
  .tile-art.round {
    border-radius: 50%;
  }
  .tiny {
    font-size: 10.5px;
  }
  .tile-title {
    font-weight: 650;
    font-size: 14px;
  }
  button.tile {
    text-align: left;
    transition:
      background-color 200ms,
      border-radius var(--d-fast) var(--spring-fast);
  }
  button.tile:not(:disabled):hover {
    background: color-mix(in oklab, var(--on-surface) 10%, transparent);
  }
  .small-note {
    font-size: 12.5px;
    margin-top: 10px;
  }
  .acc-title {
    margin: 4px 0 16px;
  }
  .row-actions {
    display: flex;
    gap: 8px;
    margin-top: 20px;
  }
  .account {
    display: flex;
    align-items: center;
    gap: 14px;
    padding: 12px 0;
    border-top: 1px solid var(--glass-line);
  }
  .glyph {
    width: 52px;
    height: 52px;
    border-radius: 34%;
    display: grid;
    place-items: center;
    background: color-mix(in oklab, var(--on-surface) 8%, transparent);
    color: var(--on-surface-variant);
    flex: none;
    transition:
      border-radius var(--d-spatial) var(--spring),
      background-color 300ms;
  }
  .glyph.on {
    background: var(--primary-container);
    color: var(--on-primary-container);
    border-radius: 50%;
  }
  .acc-text {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
  }
  .acc-name {
    font-weight: 700;
  }
  .acc-sub {
    font-size: 13px;
  }
  .acc-sub.err {
    color: var(--error);
  }
  .diag {
    margin: -4px 0 10px 66px;
  }
  .report {
    margin: 10px 0 0;
    padding: 12px;
    border-radius: var(--r-m);
    background: color-mix(in oklab, var(--on-surface) 7%, transparent);
    font-size: 12px;
    white-space: pre-wrap;
    user-select: all;
  }
  .paste-link {
    margin: -6px 0 6px 52px;
  }
  .paste {
    display: flex;
    gap: 8px;
    margin: -4px 0 10px 66px;
  }
  .paste input {
    flex: 1;
    min-width: 0;
    height: 36px;
    padding: 0 14px;
    border-radius: var(--r-full);
    border: 1px solid var(--outline-variant);
    background: transparent;
    outline: none;
    font-size: 13px;
  }
  .paste input:focus {
    border-color: var(--primary);
  }
  .toggle {
    display: flex;
    align-items: center;
    gap: 16px;
    justify-content: space-between;
    padding: 16px 0 4px;
  }
  .toggle > div {
    display: flex;
    flex-direction: column;
  }
  .t-name {
    font-weight: 650;
  }
  .t-sub {
    font-size: 13px;
  }
  .about {
    margin: 28px 0 8px;
  }
  .gh {
    margin-top: 16px;
  }
</style>
