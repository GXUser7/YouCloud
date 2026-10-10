<script lang="ts">
  // What went wrong, said plainly, with a way to try again.
  import Icon from './Icon.svelte'
  import { shapePath } from '../lib/shapes'

  let { title = 'Не загрузилось', message, onretry }: { title?: string; message: string; onretry?: () => void } = $props()
</script>

<section class="problem glass rise">
  <svg class="mark" viewBox="0 0 100 100" aria-hidden="true"><path d={shapePath('softBurst')} /></svg>
  <div class="text">
    <h2 class="headline">{title}</h2>
    <p class="muted">{message}</p>
  </div>
  {#if onretry}
    <button class="btn tonal" onclick={onretry}><Icon name="refresh" size={20} />Повторить</button>
  {/if}
</section>

<style>
  .problem {
    margin-top: 32px;
    border-radius: var(--r-2xl);
    padding: 24px 28px;
    display: flex;
    align-items: center;
    gap: 22px;
  }
  .mark {
    width: 64px;
    height: 64px;
    flex: none;
    animation: turn 20s linear infinite;
  }
  .mark path {
    fill: var(--error-container);
  }
  .text {
    flex: 1;
    min-width: 0;
  }
  h2 {
    font-size: 22px;
    margin-bottom: 4px;
  }
  p {
    overflow-wrap: anywhere;
  }
  @keyframes turn {
    to {
      transform: rotate(360deg);
    }
  }
</style>
