// What every service's home is made of: the sets to lead with, songs to play in place, the
// listener's library on the service, and cached snapshot loading with silent background updates.

import { onDestroy, untrack } from 'svelte'
import { accounts } from './accounts.svelte'
import { createCatalog, defaultCatalog, type Service } from './catalog'
import { extension } from './extension'
import {
  accountKeyFor,
  buildHomeSnapshot,
  cacheGenerationFor,
  homeCacheKey,
  hydrateCatalogFromSnapshot,
  liveYtAccountKey,
  peekHomeCacheL1,
  purgeHomeCacheEntry,
  readHomeCache,
  waitForYtAccountResolved,
  writeHomeCacheIfChanged,
  type HomeSnapshot,
} from './homeCache'
import { i18n, tr, type Lang } from './i18n.svelte'
import * as live from './live'
import { markLiked } from './state.svelte'

/** How many of the listener's playlists home shows as tiles up top; the rest are a row of cards. */
export const QUICK_PLAYLISTS = 7

/** The row home leads with: the listener's own (made for them, mixes) before what everyone gets. */
export function leading(sections: live.Section[]): live.Section | undefined {
  const withSets = sections.filter((s) => s.sets.length)
  return withSets.find((s) => /для тебя|for you|made for|микс|mix/i.test(s.title)) ?? withSets[0]
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

// ---------------------------------------------------------------------------------- R1 & R2 controller

export type HomeStatus = 'checking' | 'loading' | 'ready' | 'error'

const inFlightBg = new Map<string, Promise<void>>()

/**
 * Silent background revalidation (R2): fetches fresh home (+ library when signed in) into an
 * isolated CatalogContext, compares canonical hash against [cachedHash], and persists to L1+L2
 * for the next open without mutating defaultCatalog, player.liked, or any mounted screen state.
 */
export function revalidateHomeInBackground(
  service: Service,
  expectedAccountKey: string,
  expectedLang: Lang,
  cachedHash: string,
): Promise<void> {
  const key = homeCacheKey(service, expectedAccountKey, expectedLang)
  const gen = cacheGenerationFor(service)
  const bgKey = `${key}:${gen}`
  const existing = inFlightBg.get(bgKey)
  if (existing) return existing

  const task = (async () => {
    try {
      if (service === 'ya') {
        if (!(await extension())) return
      }
      if (service === 'yt') {
        await waitForYtAccountResolved()
      }
      if (accountKeyFor(service) !== expectedAccountKey || i18n.lang !== expectedLang || cacheGenerationFor(service) !== gen) {
        return
      }
      const wantLib = expectedAccountKey !== 'anon'
      if (live.signedIn(service) !== wantLib) return

      const isoCat = createCatalog()
      let sections: live.Section[]
      let lib: live.Library | null = null
      if (wantLib) {
        ;[sections, lib] = await Promise.all([
          live.home(service, isoCat),
          live.library(service, isoCat, { strict: true }),
        ])
      } else {
        sections = await live.home(service, isoCat)
      }

      if (accountKeyFor(service) !== expectedAccountKey || i18n.lang !== expectedLang || cacheGenerationFor(service) !== gen) {
        return
      }

      const accountLabel = service === 'yt' && accounts.ytName ? tr('Для тебя, %s', accounts.ytName) : undefined
      const next = buildHomeSnapshot({
        service,
        accountKey: expectedAccountKey,
        lang: expectedLang,
        signedIn: wantLib,
        accountLabel,
        sections,
        lib,
        cat: isoCat,
      })
      if (next.hash !== cachedHash) {
        await writeHomeCacheIfChanged(next)
      }
    } catch {
      // Background errors are swallowed silently; keep existing valid cache and open view intact.
    }
  })().finally(() => {
    if (inFlightBg.get(bgKey) === task) inFlightBg.delete(bgKey)
  })

  inFlightBg.set(bgKey, task)
  return task
}

export function useHome(service: Service, opts: { waitForExtension?: boolean } = {}) {
  const state = $state({
    status: 'checking' as HomeStatus,
    sections: [] as live.Section[],
    lib: null as live.Library | null,
    signedIn: false,
    servedFromCache: false,
    error: null as unknown,
    ytNote: '',
  })

  const lang = i18n.lang
  let disposed = false
  let runId = 0

  onDestroy(() => {
    disposed = true
  })

  function applySnapshot(snap: HomeSnapshot) {
    hydrateCatalogFromSnapshot(snap, defaultCatalog)
    if (snap.lib) markLiked(snap.lib.likedIds)
    state.sections = snap.sections
    state.lib = snap.lib
    state.signedIn = snap.signedIn
    state.ytNote = snap.accountLabel ?? ''
    state.servedFromCache = true
    state.error = null
    state.status = 'ready'
    void revalidateHomeInBackground(service, snap.accountKey, snap.lang, snap.hash)
  }

  async function loadForeground(id: number) {
    if (opts.waitForExtension) {
      const ext = await extension()
      if (disposed || id !== runId) return
      if (!ext) {
        state.status = 'checking'
        return
      }
    }

    state.status = 'loading'
    state.error = null
    state.servedFromCache = false

    const reqSignedIn = service === 'yt' ? accounts.yt : live.signedIn(service)
    const reqAccountKey = reqSignedIn ? (service === 'yt' ? liveYtAccountKey() : accountKeyFor(service)) : 'anon'
    const reqLang = i18n.lang
    const reqGen = cacheGenerationFor(service)
    state.signedIn = reqSignedIn
    state.lib = null

    let libPartial = false
    const homePromise = live.home(service, defaultCatalog)
    const libPromise = reqSignedIn
      ? live.library(service, defaultCatalog, { onPartial: () => (libPartial = true) })
      : null
    // Prevent unhandled rejection if homePromise rejects before libPromise is awaited.
    libPromise?.catch(() => {})

    let sections: live.Section[]
    try {
      sections = await homePromise
    } catch (e) {
      if (disposed || id !== runId) return
      state.error = e
      state.status = 'error'
      return
    }
    if (disposed || id !== runId) return

    const accountLabel = service === 'yt' && accounts.ytName ? tr('Для тебя, %s', accounts.ytName) : undefined
    state.sections = sections
    state.ytNote = accountLabel ?? ''
    state.status = 'ready'

    let lib: live.Library | null = null
    if (libPromise) {
      try {
        lib = await libPromise
      } catch {
        // Library failed on initial load; keep sections rendered and do not cache an incomplete signed-in snapshot.
        return
      }
      if (disposed || id !== runId) return
      markLiked(lib.likedIds)
      state.lib = lib
      if (libPartial) return
    }

    const currentKey = service === 'yt' ? liveYtAccountKey() : accountKeyFor(service)
    if (currentKey !== reqAccountKey || i18n.lang !== reqLang || cacheGenerationFor(service) !== reqGen) {
      return
    }
    const snap = buildHomeSnapshot({
      service,
      accountKey: reqAccountKey,
      lang: reqLang,
      signedIn: reqSignedIn,
      accountLabel,
      sections,
      lib,
      cat: defaultCatalog,
    })
    void writeHomeCacheIfChanged(snap)
  }

  function tryApplyL1(key: string): boolean {
    const l1 = peekHomeCacheL1(service, key, lang)
    if (!l1) return false
    try {
      applySnapshot(l1)
      return true
    } catch {
      void purgeHomeCacheEntry(service, key, lang)
      state.servedFromCache = false
      state.sections = []
      state.lib = null
      return false
    }
  }

  async function bootFromCacheOrNetwork(id: number, key: string) {
    try {
      const l2 = await readHomeCache(service, key, lang)
      if (disposed || id !== runId) return
      if (l2) {
        try {
          applySnapshot(l2)
          return
        } catch {
          await purgeHomeCacheEntry(service, key, lang)
          if (disposed || id !== runId) return
          state.servedFromCache = false
          state.sections = []
          state.lib = null
        }
      }
    } catch {
      // Corrupt or unreadable cache must never strand the page on 'checking'.
    }
    await loadForeground(id)
  }

  async function openForKey(nextKey: string) {
    const id = ++runId
    if (tryApplyL1(nextKey)) return
    state.status = 'checking'
    await bootFromCacheOrNetwork(id, nextKey)
  }

  const initialKey = accountKeyFor(service)
  if (!tryApplyL1(initialKey)) {
    void bootFromCacheOrNetwork(++runId, initialKey)
  }

  if (service === 'yt') {
    let wasYtSignedIn = accounts.yt
    $effect(() => {
      const signed = accounts.yt
      if (signed !== wasYtSignedIn) {
        wasYtSignedIn = signed
        // Startup refreshYouTube() must never wipe or reload an already-displayed cached view.
        if (!untrack(() => state.servedFromCache)) {
          const nextKey = untrack(() => liveYtAccountKey())
          untrack(() => void openForKey(nextKey))
        }
      }
    })
  } else {
    let trackedKey = initialKey
    $effect(() => {
      const nextKey = accountKeyFor(service)
      if (nextKey !== trackedKey) {
        trackedKey = nextKey
        untrack(() => void openForKey(nextKey))
      }
    })
  }

  return {
    get status() { return state.status },
    get sections() { return state.sections },
    get lib() { return state.lib },
    get signedIn() { return state.signedIn },
    get servedFromCache() { return state.servedFromCache },
    get error() { return state.error },
    get ytNote() { return state.ytNote },
    retry: () => {
      const id = ++runId
      void loadForeground(id)
    },
  }
}
