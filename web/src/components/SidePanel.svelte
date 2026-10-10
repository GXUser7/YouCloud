<script lang="ts">
  // The panel on the right: what friends play right now, the queue, or the lyrics — whichever the
  // player's buttons last asked for.
  import { artistNames, type Service } from '../lib/catalog'
  import { openLink } from '../lib/links'
  import { ago, avatarUrl, listeningNow, shownName, social } from '../lib/social.svelte'
  import { app, currentTrack, go, jump, openPanel, player, remember, type Panel } from '../lib/state.svelte'
  import Avatar from './Avatar.svelte'
  import ButtonGroup from './ButtonGroup.svelte'
  import Cover from './Cover.svelte'
  import EqBars from './EqBars.svelte'
  import Icon from './Icon.svelte'
  import Lyrics from './Lyrics.svelte'
  import ServiceGlyph from './ServiceGlyph.svelte'
  import TrackRow from './TrackRow.svelte'

  const tabs: { value: Panel; label: string }[] = [
    { value: 'friends', label: 'Друзья' },
    { value: 'queue', label: 'Очередь' },
    { value: 'lyrics', label: 'Текст' },
  ]

  let now = $state(Date.now())
  $effect(() => {
    const timer = setInterval(() => (now = Date.now()), 30_000)
    return () => clearInterval(timer)
  })
  // Who plays now first, then who played last, as the app's friends list goes.
  const heard = $derived(
    social.friends
      .filter((p) => p.relation === 'friend' && p.title)
      .sort((a, b) => Number(listeningNow(b, now)) - Number(listeningNow(a, now)))
      .slice(0, 12),
  )
  const SERVICES: Record<string, Service> = { soundcloud: 'sc', yandex: 'ya', youtube: 'yt' }
  const upNext = $derived(player.queue.slice(player.index + 1, player.index + 101))
  const t = $derived(currentTrack())
  let source = $state<string | null>(null)
</script>

<aside class="panel glass">
  <header>
    <ButtonGroup
      options={tabs}
      value={app.panel}
      size="s"
      stretch
      onchange={(v) => {
        if (v) {
          app.panel = v
          remember('panel', v)
        }
      }}
    />
    <button class="icon-btn s" onclick={() => openPanel(app.panel)} aria-label="Скрыть панель">
      <Icon name="close" size={20} />
    </button>
  </header>

  {#if app.panel === 'friends'}
    <div class="scroll">
      {#if !social.me}
        <div class="signed-out">
          <p class="muted">Войди в аккаунт YouCloud — здесь будет видно, что слушают друзья.</p>
          <button class="btn tonal small" onclick={() => go('friends')}><Icon name="login" size={18} />Войти</button>
        </div>
      {:else}
        <p class="overline head">{heard.some((p) => listeningNow(p, now)) ? 'Сейчас слушают' : 'Слушали недавно'}</p>
        {#each heard as f (f.id)}
          {@const playing = listeningNow(f, now)}
          {@const svc = SERVICES[f.service ?? '']}
          <div class="friend">
            <Avatar name={shownName(f)} color={f.color} src={avatarUrl(f)} size={44} live={playing} />
            <div class="who">
              <div class="name-row">
                <span class="name">{shownName(f)}</span>
                {#if playing}<span class="live"><EqBars size={12} /></span>{:else}<span class="ago">{ago(f.updated_at, now)}</span>{/if}
              </div>
              <button class="np" disabled={!f.track_url} onclick={() => openLink(f.track_url!, `${shownName(f)} слушает`)}>
                <span class="mini-cover"><Cover seed={f.id + f.title} color={f.color ?? '#9CC2A2'} src={f.cover_url} /></span>
                <span class="np-text">
                  <span class="ellipsis np-title">{f.title}</span>
                  <span class="ellipsis np-artist">{#if svc}<ServiceGlyph service={svc} size={12} />{/if} {f.artist ?? ''}</span>
                </span>
                <span class="np-play"><Icon name="play_arrow" fill size={20} /></span>
              </button>
            </div>
          </div>
        {:else}
          <p class="muted empty">{social.friendsLoaded ? 'Друзья пока ничего не слушали.' : 'Загружаю друзей…'}</p>
        {/each}
        <button class="btn text all" onclick={() => go('friends')}>
          Все друзья <Icon name="arrow_forward" size={18} />
        </button>
      {/if}
    </div>
  {:else if app.panel === 'queue'}
    <div class="scroll">
      {#if t}
        <p class="overline head">Сейчас играет</p>
        <TrackRow id={t.id} list={player.queue} compact context={player.context} contextId={player.contextId} />
        <div class="queue-head">
          <p class="overline">Далее</p>
          <span class="from ellipsis">{player.wave ? 'Моя волна' : player.context}</span>
        </div>
        {#each upNext as id, i (id + i)}
          <TrackRow {id} list={player.queue} compact context={player.context} contextId={player.contextId} onplay={() => jump(player.index + 1 + i)} />
        {:else}
          <p class="muted empty">
            {player.radio ? 'Радио подбирает следующие треки…' : 'Очередь закончилась. Включи радио по треку — и музыка не кончится.'}
          </p>
        {/each}
      {:else}
        <p class="muted empty">Очередь пуста. Включи трек или подборку.</p>
      {/if}
    </div>
  {:else}
    <div class="lyrics-wrap">
      {#if t}
        <div class="lyrics-head">
          <span class="mini-cover"><Cover seed={t.id} color={t.color} src={t.cover} /></span>
          <div class="np-text">
            <span class="ellipsis np-title">{t.title}</span>
            <span class="ellipsis np-artist">{artistNames(t)}</span>
          </div>
        </div>
      {/if}
      <Lyrics onsource={(s) => (source = s)} />
      {#if source}<p class="credit muted">Текст: {source}</p>{/if}
    </div>
  {/if}
</aside>

<style>
  .panel {
    grid-area: side;
    border-radius: var(--r-2xl);
    display: flex;
    flex-direction: column;
    min-height: 0;
    overflow: hidden;
    z-index: 2;
    animation: enter var(--d-spatial) var(--spring);
  }
  @keyframes enter {
    from {
      opacity: 0;
      transform: translateX(24px) scale(0.98);
    }
  }
  header {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 14px 12px 10px 14px;
  }
  header > :global(:first-child) {
    flex: 1;
  }
  .scroll {
    flex: 1;
    overflow-y: auto;
    padding: 4px 10px 16px;
  }
  .head {
    padding: 10px 8px 8px;
  }
  .friend {
    display: flex;
    gap: 12px;
    padding: 10px 8px;
    align-items: flex-start;
  }
  .who {
    flex: 1;
    min-width: 0;
  }
  .name-row {
    display: flex;
    align-items: center;
    gap: 8px;
    height: 22px;
  }
  .name {
    font-weight: 700;
  }
  .live {
    color: var(--primary);
    display: inline-flex;
  }
  .ago {
    font-size: 12px;
    color: var(--on-surface-variant);
  }
  .np {
    margin-top: 6px;
    width: 100%;
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 6px;
    border-radius: var(--r-m);
    background: color-mix(in oklab, var(--on-surface) 6%, transparent);
    text-align: left;
    transition:
      background-color 200ms,
      border-radius var(--d-fast) var(--spring-fast);
  }
  .np:hover {
    background: color-mix(in oklab, var(--on-surface) 11%, transparent);
  }
  .np:active {
    border-radius: var(--r-xs);
  }
  .mini-cover {
    width: 40px;
    height: 40px;
    border-radius: var(--r-s);
    overflow: hidden;
    flex: none;
  }
  .np-text {
    display: flex;
    flex-direction: column;
    min-width: 0;
    flex: 1;
  }
  .np-title {
    font-weight: 650;
    font-size: 14px;
  }
  .np-artist {
    font-size: 12.5px;
    color: var(--on-surface-variant);
    display: flex;
    align-items: center;
    gap: 4px;
  }
  .np-play {
    width: 32px;
    height: 32px;
    border-radius: 50%;
    display: grid;
    place-items: center;
    background: var(--primary);
    color: var(--on-primary);
    opacity: 0;
    transform: scale(0.6);
    transition:
      opacity 150ms,
      transform var(--d-fast) var(--spring-fast);
  }
  .np:hover .np-play {
    opacity: 1;
    transform: none;
  }
  .all {
    margin: 8px 0 0 4px;
  }
  .queue-head {
    display: flex;
    align-items: baseline;
    gap: 10px;
    padding: 18px 8px 8px;
  }
  .from {
    font-size: 13px;
    color: var(--primary);
    font-weight: 600;
  }
  .empty {
    padding: 12px 8px;
    font-size: 14px;
  }
  .signed-out {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
    padding: 16px 8px;
  }

  .lyrics-wrap {
    flex: 1;
    min-height: 0;
    display: flex;
    flex-direction: column;
    padding: 0 14px 12px;
  }
  .lyrics-head {
    display: flex;
    gap: 10px;
    align-items: center;
    padding: 6px 4px 4px;
  }
  .lyrics-wrap :global(.lyrics) {
    flex: 1;
    min-height: 0;
  }
  .credit {
    font-size: 12px;
    padding: 6px 8px 0;
  }
</style>
