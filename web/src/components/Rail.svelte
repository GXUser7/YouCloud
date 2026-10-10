<script lang="ts">
  // The rail: only the services. Which one the page shows is all it decides — everything of the
  // service (picks, the listener's own music) is on its one page, and search, friends and settings
  // sit in the bar above the page. The chosen service fills its shape with the theme's colour and
  // the shape softens into a squircle, as Expressive's selected destinations do.
  import { SERVICES, type Service } from '../lib/catalog'
  import { signedIn } from '../lib/live'
  import { app, go, setService } from '../lib/state.svelte'
  import AppIcon from './AppIcon.svelte'
  import ServiceGlyph from './ServiceGlyph.svelte'

  function pick(svc: Service) {
    const same = app.service === svc
    setService(svc)
    // A service is a place of its own: picking one opens its page; picking it again, from any
    // page of it, comes back there too. Search keeps its words and asks the new service.
    if (app.route.name !== 'search' || same) go('home')
  }
</script>

<nav class="rail glass" aria-label="Сервисы">
  <button class="logo" onclick={() => go('home')} aria-label="YouCloud" title="YouCloud"><AppIcon size={36} /></button>

  <div class="services" role="radiogroup" aria-label="Сервис">
    {#each SERVICES as s (s.id)}
      {@const on = app.service === s.id}
      <button class="service" class:on role="radio" aria-checked={on} title={s.name} onclick={() => pick(s.id)}>
        <span class="shape">
          <ServiceGlyph service={s.id} size={26} />
          {#if signedIn(s.id)}<span class="dot" aria-label="вход выполнен"></span>{/if}
        </span>
        <span class="label">{s.short}</span>
      </button>
    {/each}
  </div>
</nav>

<style>
  /* A pill as tall as its services, not a second sidebar: in a browser that has one of its own
     (Masaki) the page keeps its calm. */
  .rail {
    grid-area: rail;
    align-self: start;
    border-radius: var(--r-2xl);
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 18px;
    padding: 14px 0 18px;
    z-index: 2;
  }
  .logo {
    display: grid;
    place-items: center;
    width: 52px;
    height: 52px;
    border-radius: var(--r-l);
    transition: transform var(--d-fast) var(--spring-fast);
  }
  .logo:active {
    transform: scale(0.92);
  }

  .services {
    display: flex;
    flex-direction: column;
    gap: 14px;
  }
  .service {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 6px;
    width: 72px;
    color: var(--on-surface-variant);
  }
  .shape {
    position: relative;
    display: grid;
    place-items: center;
    width: 56px;
    height: 56px;
    border-radius: 50%;
    isolation: isolate;
    transition:
      border-radius var(--d-spatial) var(--spring),
      background-color 220ms var(--ease-emph),
      color 220ms var(--ease-emph),
      transform var(--d-fast) var(--spring-fast);
  }
  .shape::after {
    content: '';
    position: absolute;
    inset: 0;
    border-radius: inherit;
    background: currentColor;
    opacity: 0;
    transition: opacity 150ms var(--ease-emph);
    z-index: -1;
  }
  .service:hover .shape::after {
    opacity: 0.08;
  }
  .service:active .shape {
    transform: scale(0.9);
  }
  .service.on {
    color: var(--on-surface);
  }
  .service.on .shape {
    border-radius: var(--r-l);
    background: var(--primary);
    color: var(--on-primary);
  }
  .label {
    font-size: 11.5px;
    font-weight: 650;
    letter-spacing: 0.01em;
    transition: color 220ms var(--ease-emph);
  }
  .dot {
    position: absolute;
    right: 5px;
    bottom: 5px;
    width: 10px;
    height: 10px;
    border-radius: 50%;
    background: var(--primary);
    border: 2px solid var(--surface-container);
  }
  .service.on .dot {
    background: var(--on-primary);
    border-color: var(--primary);
  }
</style>
