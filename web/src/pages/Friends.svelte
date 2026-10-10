<script lang="ts">
  import type { Service } from '../lib/catalog'
  import { time } from '../lib/format'
  import { openLink } from '../lib/links'
  import {
    acceptFriend,
    ago,
    avatarUrl,
    listeningNow,
    positionOf,
    removeFriend,
    requestFriend,
    searchPeople,
    shownName,
    social,
    socialMessage,
    type Person,
  } from '../lib/social.svelte'
  import { toast } from '../lib/toast.svelte'
  import AccountForm from '../components/AccountForm.svelte'
  import Avatar from '../components/Avatar.svelte'
  import Cover from '../components/Cover.svelte'
  import Icon from '../components/Icon.svelte'
  import Loading from '../components/Loading.svelte'
  import PageHeader from '../components/PageHeader.svelte'
  import ServiceGlyph from '../components/ServiceGlyph.svelte'
  import WavyProgress from '../components/WavyProgress.svelte'

  // Friends' tracks move on in real time, as the app shows them.
  let now = $state(Date.now())
  $effect(() => {
    const timer = setInterval(() => (now = Date.now()), 1000)
    return () => clearInterval(timer)
  })

  const friends = $derived(social.friends.filter((p) => p.relation === 'friend'))
  const live = $derived(friends.filter((p) => listeningNow(p, now)))
  const recent = $derived(friends.filter((p) => !listeningNow(p, now) && p.title))
  const requests = $derived(social.friends.filter((p) => p.relation === 'incoming' || p.relation === 'outgoing'))

  let query = $state('')
  let found = $state<Person[] | null>(null)
  let searchTimer: ReturnType<typeof setTimeout>
  function typed() {
    clearTimeout(searchTimer)
    if (!query.trim()) {
      found = null
      return
    }
    searchTimer = setTimeout(async () => {
      try {
        found = await searchPeople(query)
      } catch (e) {
        toast(socialMessage(e))
      }
    }, 300)
  }

  async function act(f: () => Promise<unknown>) {
    try {
      await f()
      if (query.trim()) found = await searchPeople(query)
    } catch (e) {
      toast(socialMessage(e))
    }
  }

  const SERVICES: Record<string, Service> = { soundcloud: 'sc', yandex: 'ya', youtube: 'yt' }
  const serviceOf = (p: Person): Service | null => SERVICES[p.service ?? ''] ?? null
  const SERVICE_NAMES: Record<string, string> = { soundcloud: 'SoundCloud', yandex: 'Яндекс Музыка', youtube: 'YouTube Music', device: 'С устройства' }
</script>

<PageHeader
  title="Друзья"
  subtitle={social.me ? (live.length ? `${live.length} ${live.length === 1 ? 'слушает' : 'слушают'} прямо сейчас` : 'Сейчас никто не слушает') : 'Аккаунт YouCloud'}
>
  {#snippet actions()}
    {#if social.me}
      <label class="add glass-strong">
        <Icon name="person_search" size={22} />
        <input bind:value={query} oninput={typed} placeholder="Найти по нику или имени" spellcheck="false" />
        {#if query}<button class="icon-btn s" onclick={() => ((query = ''), (found = null))} aria-label="Очистить"><Icon name="close" size={20} /></button>{/if}
      </label>
    {/if}
  {/snippet}
</PageHeader>

{#if !social.ready}
  <Loading />
{:else if !social.me}
  <section class="signin glass rise">
    <div class="pitch">
      <h2 class="headline">Музыка вместе с друзьями</h2>
      <p class="muted">Войди в аккаунт YouCloud — тот же, что в приложении. Друзья увидят, что ты слушаешь, а ты — их, и одним нажатием включишь то же у себя.</p>
    </div>
    <AccountForm />
  </section>
{:else}
  {#if found}
    <section class="section rise">
      <div class="section-head"><h2>Поиск</h2></div>
      <div class="rows">
        {#each found as p (p.id)}
          <div class="row glass">
            <Avatar name={shownName(p)} color={p.color} src={avatarUrl(p)} size={44} />
            <div class="who">
              <span class="name">{shownName(p)}</span>
              <span class="nick">@{p.nick}</span>
            </div>
            {#if p.relation === 'friend'}
              <span class="chip">Друг</span>
            {:else if p.relation === 'outgoing'}
              <button class="btn outlined small" onclick={() => act(() => removeFriend(p.id))}>Отменить заявку</button>
            {:else if p.relation === 'incoming'}
              <button class="btn filled small" onclick={() => act(() => acceptFriend(p.id))}>Принять</button>
            {:else}
              <button class="btn filled small" onclick={() => act(() => requestFriend(p.id))}><Icon name="person_add" size={18} />Добавить</button>
            {/if}
          </div>
        {:else}
          <p class="muted">Никого с таким ником.</p>
        {/each}
      </div>
    </section>
  {/if}

  {#if requests.length}
    <section class="section">
      <div class="section-head"><h2>Заявки</h2></div>
      <div class="rows">
        {#each requests as p (p.id)}
          <div class="row glass">
            <Avatar name={shownName(p)} color={p.color} src={avatarUrl(p)} size={44} />
            <div class="who">
              <span class="name">{shownName(p)}</span>
              <span class="nick">@{p.nick} · {p.relation === 'incoming' ? 'хочет дружить' : 'ждёт ответа'}</span>
            </div>
            {#if p.relation === 'incoming'}
              <button class="btn filled small" onclick={() => act(() => acceptFriend(p.id))}>Принять</button>
              <button class="btn text small" onclick={() => act(() => removeFriend(p.id))}>Отклонить</button>
            {:else}
              <button class="btn outlined small" onclick={() => act(() => removeFriend(p.id))}>Отменить</button>
            {/if}
          </div>
        {/each}
      </div>
    </section>
  {/if}

  {#if live.length || recent.length}
    <section class="section">
      <div class="section-head"><h2>{live.length ? 'Сейчас слушают' : 'Слушали недавно'}</h2></div>
      <div class="cards">
        {#each [...live, ...recent] as p, i (p.id)}
          {@const playing = listeningNow(p, now)}
          {@const at = positionOf(p, now)}
          {@const svc = serviceOf(p)}
          <article class="card glass rise" style:animation-delay="{i * 50}ms">
            <header>
              <Avatar name={shownName(p)} color={p.color} src={avatarUrl(p)} size={52} live={playing} />
              <div class="who">
                <span class="name">{shownName(p)}</span>
                <span class="nick">@{p.nick}</span>
              </div>
              <span class="state" class:live={playing}>{playing ? 'слушает' : ago(p.updated_at, now)}</span>
            </header>
            <div class="np">
              <span class="np-cover"><Cover seed={p.id + (p.title ?? '')} color={p.color ?? '#9CC2A2'} src={p.cover_url} /></span>
              <div class="np-text">
                <span class="ellipsis np-title">{p.title}</span>
                <span class="ellipsis np-artist">{p.artist ?? ''}</span>
                <span class="np-service">
                  {#if svc}<ServiceGlyph service={svc} size={13} />{/if}{SERVICE_NAMES[p.service ?? ''] ?? ''}
                </span>
              </div>
            </div>
            {#if playing && at != null && p.duration_ms}
              <div class="progress">
                <WavyProgress value={at / (p.duration_ms / 1000)} playing stroke={3} amplitude={2} wavelength={20} />
                <span class="times">{time(at)} / {time(p.duration_ms / 1000)}</span>
              </div>
            {/if}
            <div class="actions">
              <button class="btn tonal small" disabled={!p.track_url} onclick={() => openLink(p.track_url!, `${shownName(p)} слушает`)}>
                <Icon name="play_arrow" fill size={20} />Включить у себя
              </button>
            </div>
          </article>
        {/each}
      </div>
    </section>
  {/if}

  <section class="section">
    <div class="section-head"><h2>Все друзья</h2><span class="muted">{friends.length}</span></div>
    {#if !social.friendsLoaded}
      <Loading tall={false} />
    {:else}
      <div class="rows">
        {#each friends as p (p.id)}
          <div class="row">
            <Avatar name={shownName(p)} color={p.color} src={avatarUrl(p)} size={44} live={listeningNow(p, now)} />
            <div class="who">
              <span class="name">{shownName(p)}</span>
              <span class="nick">@{p.nick}</span>
            </div>
            <span class="muted seen">{listeningNow(p, now) ? 'слушает' : p.updated_at ? ago(p.updated_at, now) : ''}</span>
            <button class="btn text small" onclick={() => act(() => removeFriend(p.id))}>Удалить</button>
          </div>
        {:else}
          <p class="muted">Друзей пока нет — найди их по нику вверху.</p>
        {/each}
      </div>
    {/if}
  </section>
{/if}

<style>
  .add {
    display: flex;
    align-items: center;
    gap: 10px;
    height: 56px;
    padding: 0 8px 0 18px;
    border-radius: var(--r-full);
    color: var(--on-surface-variant);
    transition: border-radius var(--d-spatial) var(--spring);
  }
  .add:focus-within {
    border-radius: var(--r-l);
    box-shadow: 0 0 0 2px var(--primary);
  }
  .add input {
    width: 240px;
    border: 0;
    outline: 0;
    background: none;
    font-size: 16px;
    color: var(--on-surface);
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
  .cards {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
    gap: 16px;
  }
  .card {
    border-radius: var(--r-2xl);
    padding: 18px;
    display: flex;
    flex-direction: column;
    gap: 14px;
    transition: border-radius var(--d-spatial) var(--spring);
  }
  .card:hover {
    border-radius: var(--r-xl);
  }
  header {
    display: flex;
    align-items: center;
    gap: 12px;
  }
  .who {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
  }
  .name {
    font-weight: 700;
    font-size: 16px;
  }
  .nick {
    font-size: 13px;
    color: var(--on-surface-variant);
  }
  .state {
    font-size: 12px;
    font-weight: 650;
    padding: 4px 10px;
    border-radius: var(--r-full);
    background: color-mix(in oklab, var(--on-surface) 8%, transparent);
    color: var(--on-surface-variant);
    white-space: nowrap;
  }
  .state.live {
    background: color-mix(in oklab, var(--primary) 20%, transparent);
    color: var(--primary);
  }
  .np {
    display: flex;
    gap: 14px;
    align-items: center;
  }
  .np-cover {
    width: 76px;
    height: 76px;
    border-radius: var(--r-l);
    overflow: hidden;
    flex: none;
  }
  .np-text {
    display: flex;
    flex-direction: column;
    min-width: 0;
  }
  .np-title {
    font-weight: 700;
    font-size: 17px;
  }
  .np-artist {
    color: var(--on-surface-variant);
  }
  .np-service {
    display: flex;
    align-items: center;
    gap: 6px;
    margin-top: 6px;
    font-size: 12px;
    font-weight: 650;
    color: var(--primary);
  }
  .progress {
    display: flex;
    align-items: center;
    gap: 12px;
  }
  .times {
    font-size: 12px;
    color: var(--on-surface-variant);
    font-variant-numeric: tabular-nums;
    white-space: nowrap;
  }
  .actions {
    display: flex;
    gap: 6px;
  }
  .rows {
    display: flex;
    flex-direction: column;
    gap: 6px;
    max-width: 760px;
  }
  .row {
    display: flex;
    align-items: center;
    gap: 14px;
    padding: 10px 12px;
    border-radius: var(--r-xl);
  }
  .row:not(.glass):hover {
    background: var(--state-hover);
  }
  .seen {
    font-size: 13px;
  }
  @media (max-width: 1100px) {
    .signin {
      grid-template-columns: minmax(0, 1fr);
    }
  }
</style>
