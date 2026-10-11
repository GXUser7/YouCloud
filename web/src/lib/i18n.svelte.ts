// The site's English, as the app has it (i18n/English.kt there): a phrase is looked up by its
// Russian, so the pages keep reading in Russian and only [tr] knows there is another language.
// A phrase with %s keeps them, in the English order (%1$s) where that differs. Counted words go
// through [plural]. The language is the listener's pick in Settings; before one, the browser's.

import { EN, EN_PLURALS } from './i18n/en'

export type Lang = 'ru' | 'en'

const KEY = 'yc.lang'
// Where the browser speaks one of these, the site starts in Russian; anywhere else, in English.
const RUSSIAN_READERS = ['ru', 'uk', 'be', 'kk', 'ky', 'uz', 'tg', 'hy', 'az', 'ka']

function initial(): Lang {
  try {
    const v = localStorage.getItem(KEY)
    if (v === 'ru' || v === 'en') return v
  } catch {
    // no storage: the browser's language
  }
  const langs = navigator.languages?.length ? navigator.languages : [navigator.language]
  return langs.some((l) => RUSSIAN_READERS.includes(l?.slice(0, 2))) ? 'ru' : 'en'
}

export const i18n = $state({ lang: initial() })
document.documentElement.lang = i18n.lang

export function setLang(lang: Lang) {
  i18n.lang = lang
  document.documentElement.lang = lang
  try {
    localStorage.setItem(KEY, lang)
  } catch {
    // the choice just isn't kept
  }
}

const missing = new Set<string>()

/** [ru] in the listener's language, with [args] put in for its %s. */
export function tr(ru: string, ...args: (string | number | null | undefined)[]): string {
  let template = ru
  if (i18n.lang === 'en') {
    const en = EN[ru]
    if (en !== undefined) template = en
    else if (import.meta.env.DEV && !missing.has(ru)) {
      missing.add(ru)
      console.warn('[i18n] no English for', JSON.stringify(ru))
    }
  }
  if (!args.length) return template
  let next = 0
  return template.replace(/%(\d+)\$s|%s/g, (_, at: string | undefined) => String(args[at ? Number(at) - 1 : next++] ?? ''))
}

/**
 * "5 треков", "1 трек", "22 трека" — or, in English, "5 tracks": [one] names the word, and the
 * English pair is looked up by it.
 */
export function pluralWord(n: number, one: string, few: string, many: string): string {
  if (i18n.lang === 'en') {
    const pair = EN_PLURALS[one]
    if (pair) return Math.abs(n) === 1 ? pair[0] : pair[1]
    if (import.meta.env.DEV && !missing.has(one)) {
      missing.add(one)
      console.warn('[i18n] no English plural for', JSON.stringify(one))
    }
  }
  const m10 = Math.abs(n) % 10
  const m100 = Math.abs(n) % 100
  return m10 === 1 && m100 !== 11 ? one : m10 >= 2 && m10 <= 4 && (m100 < 12 || m100 > 14) ? few : many
}

/** The phrases looked up so far without an English one: for checking the dictionary. */
export const untranslated = () => [...missing]
