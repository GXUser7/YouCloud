<script lang="ts">
  // The listener's playlists and artists on the service, as rows of cards.
  import { tr } from '../../lib/i18n.svelte'
  import type { Library } from '../../lib/live'
  import ArtistCard from '../ArtistCard.svelte'
  import SetCard from '../SetCard.svelte'
  import Shelf from '../Shelf.svelte'

  let { lib }: { lib: Library | null } = $props()
</script>

{#if lib?.playlists.length}
  <Shelf title={tr('Твои плейлисты')}>
    {#each lib.playlists as id (id)}<SetCard {id} size={176} />{/each}
  </Shelf>
{/if}
{#if lib?.artists.length}
  <Shelf title={tr('Твои артисты')}>
    {#each lib.artists as id (id)}<ArtistCard {id} size={150} />{/each}
  </Shelf>
{/if}
