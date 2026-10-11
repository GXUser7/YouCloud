// What every service's home is made of, whatever its layout: the sets to lead with, songs to
// play in place, the listener's library on the service — and which of the three layouts draws
// it, as picked in Settings.

import type { Service } from './catalog'
import { tr } from './i18n.svelte'
import * as live from './live'
import { markLiked } from './state.svelte'

export type HomeVariant = 'bento' | 'stage' | 'quick'

/** The layouts, named in the listener's language. */
export const HOME_VARIANTS: { value: HomeVariant; label: string; hint: string }[] = [
  { value: 'bento', label: tr('Бенто'), hint: tr('Плитки разного размера: главное крупно, рядом «Любимое»') },
  { value: 'stage', label: tr('Сцена'), hint: tr('Большая карусель, страница в цветах обложки') },
  { value: 'quick', label: tr('Для тебя'), hint: tr('Приветствие и плитки, чтобы сразу включить') },
]

function storedVariant(): HomeVariant {
  try {
    const v = localStorage.getItem('yc.homeVariant')
    return HOME_VARIANTS.some((x) => x.value === v) ? (v as HomeVariant) : 'bento'
  } catch {
    return 'bento'
  }
}

export const home = $state({ variant: storedVariant() })

export function setHomeVariant(v: HomeVariant) {
  home.variant = v
  try {
    localStorage.setItem('yc.homeVariant', v)
  } catch {
    // the choice just isn't kept
  }
}

/** The row home leads with: the listener's own (made for them, mixes) before what everyone gets. */
export function leading(sections: live.Section[]): live.Section | undefined {
  const withSets = sections.filter((s) => s.sets.length)
  return withSets.find((s) => /для тебя|for you|микс/i.test(s.title)) ?? withSets[0]
}

/** Songs to play in place (YouTube Music's "Быстрый выбор"). */
export const quickPicks = (sections: live.Section[]) => sections.find((s) => s.tracks?.length)

/** The listener's library on [service] while signed in there: likes, playlists, artists. */
export function libraryOf(service: Service) {
  const state = $state({ lib: null as live.Library | null, loading: false, error: null as string | null })
  $effect(() => {
    if (!live.signedIn(service)) {
      state.lib = null
      return
    }
    state.loading = true
    live
      .library(service)
      .then((lib) => {
        markLiked(lib.likedIds)
        state.lib = lib
        state.error = null
      })
      .catch((e) => (state.error = e?.message ?? String(e)))
      .finally(() => (state.loading = false))
  })
  return state
}

/** "Доброе утро" and the rest, by the hour here. */
export function greeting(now = new Date()): string {
  const h = now.getHours()
  return tr(h < 5 ? tr('Доброй ночи') : h < 12 ? tr('Доброе утро') : h < 18 ? tr('Добрый день') : tr('Добрый вечер'))
}
