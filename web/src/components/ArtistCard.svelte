<script lang="ts">
  // An artist as a card: the picture in a circle, the name under it. Under the pointer the circle
  // morphs into an Expressive "sunny" and lifts, the picture drawing in a little behind it; pressed,
  // it gives. The shapes are polygons of the same points (lib/shapes), so the morph is smooth.
  import { tr } from '../lib/i18n.svelte'
  import { artist } from '../lib/catalog'
  import { go } from '../lib/state.svelte'
  import Cover from './Cover.svelte'

  let { id, size = 160 }: { id: string; size?: number } = $props()
  const a = $derived(artist(id))
</script>

<button class="artist" style:--w="{size}px" onclick={() => go('artist/' + id)}>
  <span class="lift">
    <span class="pic"><span class="img"><Cover seed={'artist-' + a.id} color={a.color} src={a.image} kind="artist" /></span></span>
  </span>
  <span class="name ellipsis">{a.name}</span>
  <span class="sub">{tr('Артист')}</span>
</button>

<style>
  .artist {
    width: var(--w);
    flex: none;
    display: flex;
    flex-direction: column;
    align-items: center;
    text-align: center;
  }
  /* The lift and the shadow: the shadow follows the shape, so it sits on this, outside the clip. */
  .lift {
    display: block;
    filter: drop-shadow(0 12px 18px color-mix(in oklab, #000 34%, transparent));
    transition:
      transform var(--d-spatial) var(--spring),
      filter 300ms var(--ease-emph);
  }
  .pic {
    display: block;
    width: var(--w);
    height: var(--w);
    clip-path: var(--shape-circle);
    transition: clip-path var(--d-slow) var(--spring);
  }
  .img {
    display: block;
    width: 100%;
    height: 100%;
    transition: transform var(--d-slow) var(--spring);
  }
  .artist:hover .lift,
  .artist:focus-visible .lift {
    transform: translateY(-4px);
    filter: drop-shadow(0 18px 24px color-mix(in oklab, #000 42%, transparent));
  }
  .artist:hover .pic,
  .artist:focus-visible .pic {
    clip-path: var(--shape-sunny);
  }
  .artist:hover .img,
  .artist:focus-visible .img {
    transform: scale(1.08) rotate(-3deg);
  }
  .artist:active .lift {
    transform: scale(0.96);
    transition-duration: var(--d-fast);
  }
  .artist:focus-visible {
    outline: none;
  }
  .artist:focus-visible .name {
    text-decoration: underline;
    text-underline-offset: 3px;
  }
  .name {
    margin-top: 12px;
    font-weight: 700;
    max-width: 100%;
  }
  .sub {
    font-size: 13px;
    color: var(--on-surface-variant);
  }
</style>
