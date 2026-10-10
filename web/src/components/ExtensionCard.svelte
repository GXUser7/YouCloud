<script lang="ts">
  // The extension, handed out by the site itself: a zip to download and drop on the browser's
  // extensions page, which installs it as it is — nothing to unpack. The page waits for the
  // extension's hello and says when it is in; Chrome hands it to this tab without a reload.
  import { onMount } from 'svelte'
  import { EXTENSION_ZIP_URL, extension, extensionHost, LATEST_EXTENSION, onExtension, outdated } from '../lib/extension'
  import { shapePath } from '../lib/shapes'
  import { toast } from '../lib/toast.svelte'
  import Icon from './Icon.svelte'
  import LoadingIndicator from './LoadingIndicator.svelte'

  let {
    why = 'Яндекс Музыка и звук YouTube работают только с твоего IP — расширение ходит к ним из этого браузера.',
    always = false,
  }: {
    /** What the extension gives on the page that asks for it. */
    why?: string
    /** Settings: shown also when the extension is in and current, as its status. */
    always?: boolean
  } = $props()

  let version = $state<string | null | undefined>(undefined)
  let downloaded = $state(false)

  onMount(() => {
    extension().then((v) => (version ??= v))
    return onExtension((v) => {
      const arrived = version !== undefined && version !== v
      version = v
      if (arrived) toast(`Расширение YouCloud ${v} подключено`)
    })
  })

  const ua = navigator.userAgent
  const firefox = /Firefox\//.test(ua)
  const page = /Edg\//.test(ua) ? 'edge://extensions' : /OPR\//.test(ua) ? 'opera://extensions' : /YaBrowser\//.test(ua) ? 'browser://extensions' : 'chrome://extensions'

  // In Masaki the browser is the extension: nothing to download or update.
  const builtIn = $derived(!!version && extensionHost() === 'masaki')
  const update = $derived(!!version && !builtIn && outdated(version))
  // Before 0.5 the extension had no key of its own: the new one comes in beside it, not over it.
  const keyless = $derived(!!version && outdated(version) && version.startsWith('0.4'))
  const shown = $derived(version !== undefined && (always || !version || update))

  async function copyPage() {
    try {
      await navigator.clipboard.writeText(page)
      toast(`Адрес скопирован — вставь его в новую вкладку`)
    } catch {
      toast(`Открой ${page} в новой вкладке`)
    }
  }
</script>

{#if shown}
  <section class="ext glass rise" class:done={version && !update}>
    <div class="head">
      <svg class="mark" viewBox="0 0 100 100" aria-hidden="true"><path d={shapePath('cookie9')} /></svg>
      <span class="mark-icon"><Icon name="extension" fill size={34} /></span>
      <div class="text">
        {#if builtIn}
          <h2 class="headline">Встроено в Masaki</h2>
          <p class="muted">Masaki сам ходит в Яндекс и YouTube с твоего IP и входит в сервисы своими вкладками — расширение не нужно.</p>
        {:else if version && !update}
          <h2 class="headline">Расширение {version} подключено</h2>
          <p class="muted">Яндекс и YouTube идут с твоего IP. Последняя версия — {LATEST_EXTENSION}.</p>
        {:else if update}
          <h2 class="headline">Обнови расширение</h2>
          <p class="muted">Стоит {version}, на сайте — {LATEST_EXTENSION}. Скачай и перетащи так же, как в первый раз.</p>
        {:else}
          <h2 class="headline">Поставь расширение YouCloud</h2>
          <p class="muted">{why}</p>
        {/if}
      </div>
      {#if !builtIn}<a class="btn {version && !update ? 'tonal' : 'filled large'}" href={EXTENSION_ZIP_URL} download="youcloud-extension.zip" onclick={() => (downloaded = true)}>
        <Icon name="download" size={22} />{version && !update ? 'Скачать снова' : 'Скачать'}
      </a>{/if}
    </div>

    {#if !version || update}
      {#if firefox}
        <ol class="steps">
          <li class:on={downloaded}><span class="n">1</span><span>Скачай архив — кнопка справа.</span></li>
          <li><span class="n">2</span><span>Открой <b>about:debugging#/runtime/this-firefox</b> и нажми «Загрузить временное дополнение…» — выбери скачанный zip.</span></li>
          <li><span class="n">3</span><span>Firefox держит такое дополнение до перезапуска: потом — снова шаг 2.</span></li>
        </ol>
      {:else}
        <ol class="steps">
          <li class:on={downloaded}>
            <span class="n">{#if downloaded}<Icon name="check" size={18} weight={700} />{:else}1{/if}</span>
            <span>Скачай <b>youcloud-extension.zip</b> — кнопка справа.</span>
          </li>
          <li>
            <span class="n">2</span>
            <span>Открой <b>{page}</b> в новой вкладке и включи <b>«Режим разработчика»</b> справа вверху.</span>
            <button class="btn text small" onclick={copyPage}><Icon name="content_copy" size={18} />Адрес</button>
          </li>
          <li>
            <span class="n">3</span>
            <span>Перетащи скачанный zip на ту страницу — прямо из значка загрузок <Icon name="download" size={16} /> в углу браузера. Распаковывать не нужно.</span>
          </li>
        </ol>
        {#if keyless}
          <p class="note"><Icon name="warning" size={18} />Старое «YouCloud — домашний IP» там же удали: новое встанет рядом, а не поверх.</p>
        {/if}
        <p class="wait">
          {#if downloaded}<LoadingIndicator size={28} contained={false} />Жду расширение — вернись сюда, оно подключится само{:else}Не перетаскивается? Распакуй архив и нажми «Загрузить распакованное расширение» — выбери папку.{/if}
        </p>
      {/if}
    {/if}
  </section>
{/if}

<style>
  .ext {
    margin-top: var(--ext-gap, 28px);
    border-radius: var(--r-2xl);
    padding: 24px 28px;
    display: flex;
    flex-direction: column;
    gap: 18px;
  }
  .head {
    display: flex;
    align-items: center;
    gap: 22px;
    position: relative;
  }
  .mark {
    width: 84px;
    height: 84px;
    flex: none;
    animation: turn 24s linear infinite;
  }
  .mark path {
    fill: var(--primary-container);
    transition: fill 400ms var(--ease-emph);
  }
  .done .mark path {
    fill: var(--primary);
  }
  .mark-icon {
    position: absolute;
    left: 0;
    width: 84px;
    display: grid;
    place-items: center;
    color: var(--on-primary-container);
  }
  .done .mark-icon {
    color: var(--on-primary);
  }
  @keyframes turn {
    to {
      transform: rotate(360deg);
    }
  }
  .text {
    flex: 1;
    min-width: 0;
  }
  h2 {
    font-size: 24px;
    margin-bottom: 4px;
  }
  .steps {
    list-style: none;
    margin: 0;
    padding: 0;
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 10px;
  }
  .steps li {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
    padding: 16px;
    border-radius: var(--r-xl);
    background: color-mix(in oklab, var(--on-surface) 6%, transparent);
    font-size: 14px;
    line-height: 1.45;
    transition:
      background-color 300ms var(--ease-emph),
      border-radius var(--d-spatial) var(--spring);
  }
  .steps li.on {
    background: color-mix(in oklab, var(--primary) 16%, transparent);
    border-radius: var(--r-l);
  }
  .steps .n {
    width: 32px;
    height: 32px;
    border-radius: 50%;
    display: grid;
    place-items: center;
    font-weight: 800;
    background: var(--secondary-container);
    color: var(--on-secondary-container);
  }
  .steps li.on .n {
    background: var(--primary);
    color: var(--on-primary);
  }
  .steps b {
    font-weight: 700;
    color: var(--on-surface);
  }
  .steps :global(.icon) {
    vertical-align: -3px;
  }
  .steps .btn {
    margin: -4px 0 -6px -10px;
  }
  .note,
  .wait {
    display: flex;
    align-items: center;
    gap: 10px;
    font-size: 13.5px;
    color: var(--on-surface-variant);
    margin: 0;
  }
  .note {
    color: var(--on-surface);
  }
  @media (max-width: 900px) {
    .steps {
      grid-template-columns: minmax(0, 1fr);
    }
  }
</style>
