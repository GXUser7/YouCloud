<script lang="ts">
  // An invitation to sign in to a service, signing in right here when the extension can.
  import { tr } from '../lib/i18n.svelte'
  import type { Service } from '../lib/catalog'
  import { serviceName } from '../lib/catalog'
  import { connect } from '../lib/login'
  import { reason, toast } from '../lib/toast.svelte'
  import Icon from './Icon.svelte'
  import ServiceGlyph from './ServiceGlyph.svelte'

  let { service, text }: { service: Service; text: string } = $props()
  let busy = $state(false)
  let error = $state('')

  async function signIn() {
    busy = true
    error = ''
    try {
      await connect(service)
    } catch (e) {
      error = reason(e)
      toast(error)
    } finally {
      busy = false
    }
  }
</script>

<section class="signin glass rise">
  <span class="mark"><ServiceGlyph {service} size={44} /></span>
  <div class="text">
    <h2 class="headline">{tr('Войди в %s', serviceName(service))}</h2>
    <p class="muted">{error || text}</p>
  </div>
  <button class="btn filled large" disabled={busy} onclick={signIn}>
    <Icon name="login" />{busy ? tr('Жду вход…') : tr('Войти')}
  </button>
</section>

<style>
  .signin {
    margin-top: 28px;
    border-radius: var(--r-2xl);
    padding: 24px 28px;
    display: flex;
    align-items: center;
    gap: 22px;
  }
  .mark {
    width: 84px;
    height: 84px;
    border-radius: 32%;
    display: grid;
    place-items: center;
    background: var(--primary-container);
    color: var(--on-primary-container);
    flex: none;
  }
  .text {
    flex: 1;
    min-width: 0;
  }
  h2 {
    font-size: 24px;
    margin-bottom: 4px;
  }
</style>
