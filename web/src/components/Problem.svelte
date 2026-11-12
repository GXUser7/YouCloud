<script lang="ts">
  // What went wrong, said plainly, with a way to try again.
  import { tr } from '../lib/i18n.svelte'
  import Icon from './Icon.svelte'
  import { shapePath } from '../lib/shapes'

  // [empty]: nothing went wrong, there is just nothing to show — said in the page's own tone,
  // not in the error's red.
  let {
    title = tr('Не загрузилось'),
    message,
    onretry,
    empty = false,
  }: { title?: string; message: string; onretry?: () => void; empty?: boolean } = $props()
</script>

<section class="problem glass rise" class:empty>
  <span class="mark-wrap" aria-hidden="true">
    <svg class="mark" viewBox="0 0 100 100"><path d={shapePath('softBurst')} /></svg>
    <span class="glyph"><Icon name={empty ? 'search_off' : 'priority_high'} size={26} /></span>
  </span>
  <div class="text">
    <h2 class="headline">{title}</h2>
    <p class="muted">{message}</p>
  </div>
  {#if onretry}
    <button class="btn tonal" onclick={onretry}><Icon name="refresh" size={20} />{tr('Повторить')}</button>
  {/if}
</section>

<style>
  .problem {
    margin-top: var(--gap, 24px);
    border-radius: var(--r-2xl);
    padding: 24px 28px;
    display: flex;
    align-items: center;
    gap: 22px;
  }
  .mark-wrap {
    position: relative;
    width: 64px;
    height: 64px;
    flex: none;
    display: grid;
    place-items: center;
  }
  .mark-wrap > * {
    grid-area: 1 / 1;
  }
  .mark {
    width: 100%;
    height: 100%;
    animation: turn 20s linear infinite;
  }
  .mark path {
    fill: var(--error-container);
  }
  .glyph {
    display: grid;
    color: var(--on-error-container);
  }
  .empty .mark path {
    fill: var(--secondary-container);
  }
  .empty .glyph {
    color: var(--on-secondary-container);
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
