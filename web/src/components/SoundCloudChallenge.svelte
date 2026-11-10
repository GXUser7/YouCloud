<script lang="ts">
  import { onMount } from 'svelte'
  import { tr } from '../lib/i18n.svelte'
  import { go } from '../lib/state.svelte'
  import { finishSoundCloud, openSoundCloudChallenge, soundcloudChallenge as challenge } from '../lib/soundcloudChallenge.svelte'
  import Icon from './Icon.svelte'

  let dialog: HTMLDialogElement
  $effect(() => {
    if (!dialog) return
    if (challenge.open && !dialog.open) dialog.showModal()
    else if (!challenge.open && dialog.open) dialog.close()
  })
  onMount(() => () => finishSoundCloud(false))
</script>

<dialog bind:this={dialog} class="glass-overlay" oncancel={() => finishSoundCloud(false)} aria-labelledby="sc-challenge-title">
  <div class="heading">
    <span class="symbol"><Icon name="lock" size={28} /></span>
    <button class="icon-btn" onclick={() => finishSoundCloud(false)} aria-label={tr('Закрыть')}><Icon name="close" /></button>
  </div>
  <h2 id="sc-challenge-title" class="headline">{challenge.setup ? tr('Лайки в SoundCloud') : tr('Проверка SoundCloud')}</h2>
  {#if challenge.setup}
    <p class="muted">{tr('Для лайков и подписок с проверкой SoundCloud нужно расширение YouCloud 0.9 или новее в Chrome или Firefox. В Masaki пока можно выполнить действие на странице SoundCloud.')}</p>
    <div class="actions">
      <a class="btn glassy" href="https://soundcloud.com/you/likes" target="_blank" rel="noreferrer">SoundCloud<Icon name="open_in_new" size={18} /></a>
      <button class="btn filled" onclick={() => { finishSoundCloud(false); go('settings') }}>{tr('Открыть настройки')}</button>
    </div>
  {:else}
    <p class="muted">{tr('SoundCloud просит пройти капчу. Открой проверку, пройди её сам во вкладке SoundCloud и вернись сюда. Лайк или подписка отправится после нажатия «Повторить».')}</p>
    <button class="btn filled launch" disabled={challenge.busy} onclick={openSoundCloudChallenge}><Icon name="open_in_new" size={20} />{challenge.busy ? tr('Открываю…') : tr('Открыть проверку')}</button>
    {#if challenge.error}<p class="muted error" role="status">{challenge.error}</p>{/if}
    <div class="actions">
      <button class="btn glassy" onclick={() => finishSoundCloud(false)}>{tr('Отмена')}</button>
      <button class="btn tonal" disabled={challenge.busy} onclick={() => finishSoundCloud(true)}><Icon name="refresh" size={20} />{tr('Я прошёл проверку — повторить')}</button>
    </div>
  {/if}
</dialog>

<style>
  dialog { width: min(500px, calc(100vw - 32px)); max-height: calc(100dvh - 32px); overflow: auto; margin: auto; padding: 28px; border-radius: var(--r-2xl); color: var(--on-surface); }
  dialog::backdrop { background: #0007; backdrop-filter: blur(8px); }
  .heading { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
  .symbol { display: grid; place-items: center; width: 56px; height: 56px; border-radius: 20px; background: var(--secondary-container); color: var(--on-secondary-container); }
  h2 { font-size: 24px; }
  p { margin-top: 16px; line-height: 1.6; }
  .launch { margin-top: 24px; }
  .error { font-size: 13px; overflow-wrap: anywhere; }
  .actions { display: flex; justify-content: flex-end; flex-wrap: wrap; gap: 8px; margin-top: 24px; }
  .actions .btn { white-space: normal; height: auto; min-height: 48px; padding-block: 12px; text-align: center; }
</style>
