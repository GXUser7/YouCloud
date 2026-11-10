<script lang="ts">
  import { accounts } from '../lib/accounts.svelte'
  import { serviceOf } from '../lib/catalog'
  import { tr } from '../lib/i18n.svelte'
  import * as live from '../lib/live'
  import { go } from '../lib/state.svelte'
  import { reason, toast } from '../lib/toast.svelte'
  import Icon from './Icon.svelte'
  import LoadingIndicator from './LoadingIndicator.svelte'

  let { id }: { id: string } = $props()
  const service = $derived(serviceOf(id))
  const connected = $derived(!!service && live.signedIn(service))
  const own = $derived(service === 'sc' && id === `sc:u:${accounts.sc?.id}`)
  const supported = $derived(/^(sc:u:\d+|ya:a:\d+|yt:a:UC[\w-]+)$/.test(id))
  const session = $derived(service === 'sc' ? accounts.sc?.token : service === 'ya' ? accounts.ya?.token : `${accounts.yt}:${accounts.ytName}`)
  let following = $state<boolean | null>(null)
  let loading = $state(false)
  let busy = $state(false)
  let error = $state('')
  let retry = $state(0)
  let generation = 0

  $effect(() => {
    const artistId = id
    session
    retry
    const run = ++generation
    following = null
    busy = false
    error = ''
    loading = connected && supported && !own
    if (loading) live.artistFollowing(artistId).then((value) => {
      if (run === generation) following = value
    }).catch((e) => {
      if (run === generation) error = reason(e)
    }).finally(() => {
      if (run === generation) loading = false
    })
    return () => { ++generation }
  })

  async function toggle() {
    if (!connected) { go('settings'); return }
    if (error) { retry++; return }
    if (loading || busy || following == null) return
    const run = generation
    const previous = following
    const want = !previous
    following = want
    busy = true
    try {
      await live.setArtistFollowing(id, want)
      if (run === generation) toast(want ? tr('Исполнитель добавлен в любимое') : tr('Исполнитель убран из любимого'))
    } catch (e) {
      if (run === generation) {
        following = previous
        toast(tr('Не удалось изменить подписку: %s', reason(e)))
      }
    } finally {
      if (run === generation) busy = false
    }
  }
</script>

{#if supported && !own}
  <div class="follow">
    <button
      class="btn tonal"
      class:selected={following}
      disabled={loading || busy}
      onclick={toggle}
      aria-pressed={following ?? false}
      title={!connected ? tr('Войди в сервис в Настройках') : error || (following ? tr('Убрать исполнителя из любимого') : tr('Добавить исполнителя в любимое'))}
    >
      {#if loading || busy}
        <LoadingIndicator size={22} contained={false} />
      {:else}
        <Icon name={error ? 'refresh' : 'favorite'} fill={!!following} size={22} />
      {/if}
      <span>{error ? tr('Повторить') : loading ? tr('Проверяю подписку…') : following ? (service === 'ya' ? tr('В любимом') : tr('Вы подписаны')) : service === 'ya' ? tr('Нравится') : tr('Подписаться')}</span>
    </button>
    {#if error}<span class="failure muted" role="status">{tr('Не удалось проверить подписку')}</span>{/if}
  </div>
{/if}

<style>
  .follow { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
  .selected { background: var(--primary-container); color: var(--on-primary-container); }
  .failure { font-size: 12px; max-width: 24ch; }
  .follow :global(.loader path) { fill: currentColor; }
</style>
