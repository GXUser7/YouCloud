<script lang="ts">
  import { artist } from '../lib/catalog'
  import { go } from '../lib/state.svelte'
  import Cover from './Cover.svelte'

  let { id, size = 160 }: { id: string; size?: number } = $props()
  const a = $derived(artist(id))
</script>

<button class="artist" style:--w="{size}px" onclick={() => go('artist/' + id)}>
  <span class="pic"><Cover seed={'artist-' + a.id} color={a.color} src={a.image} kind="artist" /></span>
  <span class="name ellipsis">{a.name}</span>
  <span class="sub">Артист</span>
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
  .pic {
    width: var(--w);
    height: var(--w);
    border-radius: 50%;
    overflow: hidden;
    box-shadow: var(--shadow);
    transition: border-radius var(--d-spatial) var(--spring);
  }
  .artist:hover .pic {
    border-radius: 36%;
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
