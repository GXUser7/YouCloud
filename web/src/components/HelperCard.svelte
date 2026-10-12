<script lang="ts">
  // YouCloud Helper, handed out by the site like the extension: a program to download and open
  // once. It installs itself, the extension finds it, and the card says so — then holds the switch
  // for the status in Discord. Shown only where the helper can be had: beside an extension that
  // knows it (0.6 and newer), never in Masaki, which does all of it itself.
  import { tr } from '../lib/i18n.svelte'
  import { onMount } from 'svelte'
  import { extension, helper, helperOutdated, helperPossible, HELPER_DOWNLOADS, LATEST_HELPER, onExtension, type HelperInfo } from '../lib/extension'
  import { discord, refreshPresence, setDiscord } from '../lib/presence.svelte'
  import { shapePath } from '../lib/shapes'
  import { toast } from '../lib/toast.svelte'
  import Icon from './Icon.svelte'
  import LoadingIndicator from './LoadingIndicator.svelte'
  import Switch from './Switch.svelte'

  let possible = $state(false)
  let info = $state<HelperInfo | null | undefined>(undefined)
  let downloaded = $state(false)

  // The helper is built for Windows and macOS; elsewhere the card shows only a helper already there.
  const mac = /Macintosh|Mac OS X/.test(navigator.userAgent)
  const offered = mac || /Windows/.test(navigator.userAgent)
  const file = mac ? 'youcloud-helper-mac.zip' : 'youcloud-helper.exe'
  const href = mac ? HELPER_DOWNLOADS.mac : HELPER_DOWNLOADS.windows

  const outdated = $derived(!!info && helperOutdated(info.version))
  const shown = $derived(possible && info !== undefined && (!!info || offered))

  async function check(again = false) {
    await extension()
    possible = helperPossible()
    if (!possible) return
    const found = await helper(again)
    const arrived = info === null && !!found
    info = found
    if (arrived && found) {
      toast(tr('Помощник YouCloud %s подключён', found.version))
      void refreshPresence()
    }
  }

  onMount(() => {
    check()
    const off = onExtension(() => check(true))
    // Waiting for the install: asked again every few seconds while the page is in sight.
    const timer = setInterval(() => {
      if (possible && info === null && document.visibilityState === 'visible') check(true)
    }, 4000)
    return () => {
      off()
      clearInterval(timer)
    }
  })
</script>

{#if shown}
  <section class="hc glass rise" class:done={info && !outdated}>
    <div class="head">
      <svg class="mark" viewBox="0 0 100 100" aria-hidden="true"><path d={shapePath('flower')} /></svg>
      <span class="mark-icon"><Icon name="link" size={34} weight={600} /></span>
      <div class="text">
        {#if info && !outdated}
          <h2 class="headline">{tr('Помощник %s подключён', info.version)}</h2>
          <p class="muted">{tr('Звук YouTube находит yt-dlp — как в приложении.')}</p>
        {:else if info}
          <h2 class="headline">{tr('Обнови помощник')}</h2>
          <p class="muted">{tr('Стоит %s, на сайте — %s. Скачай и открой так же, как в первый раз.', info.version, LATEST_HELPER)}</p>
        {:else}
          <h2 class="headline">{tr('Поставь помощник YouCloud')}</h2>
          <p class="muted">{tr('Маленькая программа рядом с расширением: звук YouTube через yt-dlp, как в приложении, — без «подтвердите, что вы не бот». И статус «Слушает YouCloud» в Discord.')}</p>
        {/if}
      </div>
      {#if !info || outdated}<a class="btn filled large" {href} download={file} onclick={() => (downloaded = true)}>
        <Icon name="download" size={22} />{tr('Скачать')}
      </a>{/if}
    </div>

    {#if !info || outdated}
      <ol class="steps">
        {#if mac}
          <li class:on={downloaded}>
            <span class="n">{#if downloaded}<Icon name="check" size={18} weight={700} />{:else}1{/if}</span>
            <span>{tr('Скачай')} <b>{file}</b> {tr('и открой его — появится')} <b>YouCloud Helper</b>.</span>
          </li>
          <li>
            <span class="n">2</span>
            <span>{tr('Дважды щёлкни по нему. Mac скажет, что не может проверить разработчика:')} <b>{tr('«Системные настройки» → «Конфиденциальность и безопасность» → «Всё равно открыть»')}</b>.</span>
          </li>
          <li>
            <span class="n">3</span>
            <span>{tr('В Терминале помощник поставит себя и скачает yt-dlp. Когда напишет «Готово», вернись сюда.')}</span>
          </li>
        {:else}
          <li class:on={downloaded}>
            <span class="n">{#if downloaded}<Icon name="check" size={18} weight={700} />{:else}1{/if}</span>
            <span>{tr('Скачай')} <b>{file}</b> {tr('— кнопка справа.')}</span>
          </li>
          <li>
            <span class="n">2</span>
            <span>{tr('Открой его. Программа без подписи, Windows может предупредить:')} <b>{tr('«Подробнее» → «Выполнить в любом случае»')}</b>.</span>
          </li>
          <li>
            <span class="n">3</span>
            <span>{tr('Помощник поставит себя и скачает yt-dlp. Когда напишет «Готово», вернись сюда.')}</span>
          </li>
        {/if}
      </ol>
      <p class="wait">
        {#if downloaded}<LoadingIndicator size={28} contained={false} />{tr('Жду помощник — он подключится сам')}{:else}{tr('Ставится в папку пользователя, права администратора не нужны.')}{/if}
      </p>
    {:else}
      <div class="toggle">
        <div>
          <span class="t-name">{tr('Статус в Discord')}</span>
          <span class="muted t-sub">{tr('Друзья видят, что ты слушаешь: трек, обложку и сколько осталось. На паузе статус пропадает. Нужен запущенный Discord на этом компьютере.')}</span>
          {#if discord.on}
            <span class="muted t-sub">{tr('Статуса не видно — в Discord включи «Настройки → Конфиденциальность активности → Показывать текущую активность».')}</span>
          {/if}
        </div>
        <Switch checked={discord.on} label={tr('Статус в Discord')} onchange={(v) => setDiscord(v)} />
      </div>
    {/if}
  </section>
{/if}

<style>
  .hc {
    margin-top: 16px;
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
    animation: turn 30s linear infinite reverse;
  }
  .mark path {
    fill: var(--tertiary-container);
    transition: fill 400ms var(--ease-emph);
  }
  .done .mark path {
    fill: var(--tertiary);
  }
  .mark-icon {
    position: absolute;
    left: 0;
    width: 84px;
    display: grid;
    place-items: center;
    color: var(--on-tertiary-container);
  }
  .done .mark-icon {
    color: var(--on-tertiary);
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
  .wait {
    display: flex;
    align-items: center;
    gap: 10px;
    font-size: 13.5px;
    color: var(--on-surface-variant);
    margin: 0;
  }
  .toggle {
    display: flex;
    align-items: center;
    gap: 16px;
    justify-content: space-between;
  }
  .toggle > div {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }
  .t-name {
    font-weight: 650;
  }
  .t-sub {
    font-size: 13px;
  }
  @media (max-width: 900px) {
    .steps {
      grid-template-columns: minmax(0, 1fr);
    }
  }
</style>
