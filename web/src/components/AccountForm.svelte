<script lang="ts">
  // Signing in to YouCloud, or making an account: a nick and a password, no email — the same
  // account as in the app. A new account gets a recovery code, shown once.
  import { nickAvailable, signIn, signUp, socialMessage } from '../lib/social.svelte'
  import ButtonGroup from './ButtonGroup.svelte'
  import Icon from './Icon.svelte'

  const COLORS = ['#9CC2A2', '#DDAE7A', '#E57373', '#64B5F6', '#BA68C8', '#4DB6AC', '#FFB74D', '#F06292']

  let mode = $state<'in' | 'up'>('in')
  let nick = $state('')
  let password = $state('')
  let name = $state('')
  let color = $state(COLORS[Math.floor(Math.random() * COLORS.length)])
  let busy = $state(false)
  let error = $state('')
  let code = $state<string | null>(null)
  let free = $state<boolean | null>(null)
  let nickTimer: ReturnType<typeof setTimeout>

  const nickValid = $derived(/^[a-z0-9_.]{3,20}$/.test(nick.trim().toLowerCase()))

  function nickTyped() {
    free = null
    clearTimeout(nickTimer)
    if (mode !== 'up' || !nickValid) return
    nickTimer = setTimeout(() => nickAvailable(nick).then((v) => (free = v)).catch(() => {}), 400)
  }

  async function submit(e: SubmitEvent) {
    e.preventDefault()
    busy = true
    error = ''
    try {
      if (mode === 'in') await signIn(nick, password)
      else code = await signUp(nick, password, name || nick, color)
    } catch (err) {
      error = socialMessage(err)
    } finally {
      busy = false
    }
  }
</script>

{#if code}
  <div class="code">
    <Icon name="key" size={28} />
    <h3 class="headline">Код восстановления</h3>
    <p class="muted">Почты у аккаунта нет — без этого кода забытый пароль не вернуть. Сохрани его, он показывается один раз.</p>
    <div class="code-box">{code}</div>
    <button class="btn tonal" onclick={() => navigator.clipboard?.writeText(code!)}><Icon name="content_copy" size={20} />Скопировать</button>
  </div>
{:else}
  <form class="form" onsubmit={submit}>
    <ButtonGroup
      options={[
        { value: 'in', label: 'Вход' },
        { value: 'up', label: 'Новый аккаунт' },
      ]}
      value={mode}
      stretch
      onchange={(v) => {
        if (v) mode = v as 'in' | 'up'
        error = ''
        nickTyped()
      }}
    />
    <label class="field">
      <Icon name="alternate_email" size={20} />
      <input bind:value={nick} oninput={nickTyped} placeholder="Ник" autocomplete="username" spellcheck="false" />
      {#if mode === 'up' && nick}
        <span class="hint" class:bad={!nickValid || free === false}>
          {!nickValid ? '3–20: a-z, 0-9, _ .' : free === false ? 'занят' : free ? 'свободен' : ''}
        </span>
      {/if}
    </label>
    {#if mode === 'up'}
      <label class="field">
        <Icon name="person" size={20} />
        <input bind:value={name} placeholder="Имя (видят друзья)" maxlength="40" />
      </label>
    {/if}
    <label class="field">
      <Icon name="lock" size={20} />
      <input bind:value={password} type="password" placeholder="Пароль" autocomplete={mode === 'in' ? 'current-password' : 'new-password'} />
    </label>
    {#if mode === 'up'}
      <div class="colors" role="radiogroup" aria-label="Цвет аватара">
        {#each COLORS as c}
          <button type="button" class="swatch" class:on={c === color} style:--c={c} onclick={() => (color = c)} aria-label="Цвет {c}"></button>
        {/each}
      </div>
    {/if}
    {#if error}<p class="error">{error}</p>{/if}
    <button class="btn filled large" type="submit" disabled={busy || !nickValid || password.length < 6 || (mode === 'up' && free === false)}>
      <Icon name="login" />{busy ? 'Секунду…' : mode === 'in' ? 'Войти' : 'Создать аккаунт'}
    </button>
    <p class="muted small">Тот же аккаунт, что в приложении YouCloud. Забыл пароль — восстанови его кодом в приложении.</p>
  </form>
{/if}

<style>
  .form,
  .code {
    display: flex;
    flex-direction: column;
    gap: 12px;
    max-width: 420px;
  }
  .field {
    display: flex;
    align-items: center;
    gap: 10px;
    height: 52px;
    padding: 0 16px;
    border-radius: var(--r-full);
    border: 1px solid var(--outline-variant);
    color: var(--on-surface-variant);
    transition:
      border-color 200ms,
      border-radius var(--d-spatial) var(--spring);
  }
  .field:focus-within {
    border-color: var(--primary);
    border-radius: var(--r-l);
  }
  .field input {
    flex: 1;
    min-width: 0;
    border: 0;
    outline: 0;
    background: none;
    font-size: 16px;
    color: var(--on-surface);
  }
  .hint {
    font-size: 12px;
    font-weight: 650;
    color: var(--primary);
  }
  .hint.bad,
  .error {
    color: var(--error);
  }
  .error {
    font-weight: 600;
  }
  .colors {
    display: flex;
    gap: 8px;
  }
  .swatch {
    width: 32px;
    height: 32px;
    border-radius: 50%;
    background: var(--c);
    transition: border-radius var(--d-fast) var(--spring-fast);
  }
  .swatch.on {
    border-radius: 30%;
    outline: 3px solid var(--primary);
    outline-offset: 2px;
  }
  .small {
    font-size: 12.5px;
  }
  .code-box {
    font-family: var(--font-display);
    font-size: 26px;
    letter-spacing: 0.06em;
    padding: 16px;
    border-radius: var(--r-l);
    background: var(--primary-container);
    color: var(--on-primary-container);
    text-align: center;
    user-select: all;
  }
</style>
