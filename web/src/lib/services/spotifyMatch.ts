/**
 * Pure cross-service audio matching and priority scoring helpers for Spotify tracks on YouCloud Web.
 * Separated from Svelte/network imports so both the app bundle and Node regression scripts share
 * the exact same matching logic.
 */

export type SpotifyAudioPriority = 'yt' | 'sc'

export const TIER_STRICT = 150_000
export const TIER_LOOSE = 80_000
export const BONUS_SC_PRIORITY = 36_000
export const BONUS_YT_SONG = 26_000
export const BONUS_YT_VIDEO = 25_000
export const BONUS_YT_WEB = 24_000
export const BONUS_YT_FALLBACK_SONG = 18_000
export const BONUS_YT_FALLBACK_VIDEO = 17_000
export const BONUS_YT_FALLBACK_WEB = 16_000
export const BONUS_YANDEX = 10_000
export const BONUS_SOUNDCLOUD = 0

export interface SpotifyPriorityBonuses {
  preferSoundCloud: boolean
  sc: number
  ytSong: number
  ytVideo: number
  ytWeb: number
  ya: number
}

/** Computes source score bonuses for connected playback services given user preference. */
export function resolveSpotifyPriorityBonuses(
  priority: SpotifyAudioPriority,
  scConnected: boolean,
): SpotifyPriorityBonuses {
  const preferSoundCloud = priority === 'sc' && scConnected
  return {
    preferSoundCloud,
    sc: preferSoundCloud ? BONUS_SC_PRIORITY : BONUS_SOUNDCLOUD,
    ytSong: preferSoundCloud ? BONUS_YT_FALLBACK_SONG : BONUS_YT_SONG,
    ytVideo: preferSoundCloud ? BONUS_YT_FALLBACK_VIDEO : BONUS_YT_VIDEO,
    ytWeb: preferSoundCloud ? BONUS_YT_FALLBACK_WEB : BONUS_YT_WEB,
    ya: BONUS_YANDEX,
  }
}

const BRACKET_NOISE =
  /\s*[\[(](?:feat\.?|ft\.?|featuring|with|prod\.?|official\s*(?:music\s*)?(?:video|audio|lyric\s*video|visualizer|visualiser|mv|hd|4k)|music\s*video|lyric(?:s)?(?:\s*video)?|color\s*coded(?:\s*lyrics?)?|kan\s*[/|]\s*rom\s*[/|]\s*eng|rom\s*[/|]\s*eng|eng\s*sub(?:s)?|audio|visualizer|visualiser|hd|hq|4k|explicit|clean(?:\s+edit|\s+version)?|video\s+edit|remaster(?:ed)?(?:\s+\d{4})?|single\s+version|album\s+version|radio\s+edit|original\s+mix|full(?:\s+ver(?:sion)?)?|ost|op\d*|ed\d*)[^)\]]*[)\]]/gi
const DASH_NOISE =
  /\s+[-–—]\s+(?:\d{4}\s+)?(?:remaster(?:ed)?(?:\s+\d{4})?|single\s+version|album\s+version|radio\s+edit|original\s+version|explicit|clean(?:\s+version)?|from\s+["«].*)$/gi
const TRAILING_FEAT = /\s+(?:feat\.?|ft\.?|featuring)\s+.+$/i
const TRAILING_LYRICS = /\s+lyrics?$/i
const SAFE_EDITION_NOISE = /\b(?:clean|video|radio)\s+edit\b|[\[(]\s*hd\s*[)\]]/gi

const EDITION_WORDS =
  /\b(?:live|remix|cover|karaoke|instrumental|acoustic|arrangement|soundfont|jammable|ai\s+cover|ai\s+version|version\s+by|sung\s+by|played\s+by|performed\s+by|in\s+the\s+style\s+of|sped[\s-]+up|slowed|nightcore|8d|mashup|demo|edit|reverb|bootleg|acapella|a\s+cappella|backing\s+track|minus(?:\s+one)?|off\s+vocal|no\s+vocal(?:s)?|without\s+vocal(?:s)?|vocal(?:s)?\s+only|guitar|piano|bass|drum(?:s)?|violin|cello|flute|saxophone|orchestra(?:l)?|symphonic|trio|quartet|duo|music\s+box|lullaby|city\s+pop|8[\s-]*bit|chiptune|lo[\s-]*fi|lofi|synthesia|tutorial|lesson|tabs?|chords?|midi|ringtone|pitched|pitch[\s-]+shifted|daycore|doomer|but|dance\s+practice|choreography|behind\s+the\s+scenes|making\s+of|teaser|trailer|reaction|analysis|review|breakdown|interview|commentary|snippet|preview|soviet\s+ver(?:sion)?|минус(?:овка)?|кавер|караоке|инструментал|под\s+гитару|на\s+пианино|на\s+гитаре|без\s+слов|без\s+вокала)\b|[+-]\d+\s*key\b|原曲歌手|カラオケ|弾いてみた|歌ってみた|叩いてみた|踊ってみた|吹いてみた|切り抜き|リアクション|ピアノ|ギター/gi
const SUSPICIOUS_CHANNEL_WORDS =
  /\b(?:backing\s+track(?:s)?|minus(?:\s+tracks?|\s+one)?|karaoke|instrumental(?:s)?|covers?|piano|guitar|violin|drum(?:s)?|bass|trio|quartet|orchestra|music\s+box|lullaby|acapella|a\s+cappella|jammable|soundfont|synthesia|tabs?|tutorial(?:s)?|nightcore|slowed|sped[\s-]+up|8d\s+audio|gameplay|liveplay|beatmaps?|typing|keyboard|asmr|beat\s+saber|clone\s+hero|geometry\s+dash|montage(?:s)?|fragmovie(?:s)?|highlights?|valorant|csgo|cs2|fortnite|warzone|apex\s+legends|overwatch|roblox|minecraft|amv|gmv|mmv|минус(?:овка|овки)?|караоке|кавер(?:ы)?|прохождение|геймплей)\b|(?<![\p{L}\p{N}])osu(?:![\p{L}]*)?(?![\p{L}\p{N}])|歌っちゃ王/giu
const GAMEPLAY_PATTERNS =
  /(?:^\s*\d{6}\s+|(?<![\p{L}\p{N}])osu(?:![\p{L}]*)?(?![\p{L}\p{N}])|\b(?:gameplay|playthrough|liveplay|handcam|beatmap|full\s+combo|all\s+perfect|beat\s+saber|geometry\s+dash|clone\s+hero|guitar\s+hero|rock\s+band|stepmania|etterna|quaver|muse\s+dash|project\s+sekai|proseka|arcaea|cytus|phigros|friday\s+night\s+funkin|fnf|maimai|chunithm|sound\s+voltex|sdvx|typing|keyboard|keycaps|sound\s+test|asmr|speedrun|montage(?:s)?|fragmovie(?:s)?|highlights?|valorant|csgo|cs2|fortnite|warzone|apex\s+legends|overwatch|roblox|minecraft|amv|gmv|mmv|прохождение|геймплей)\b|\b(?:hd|hr|dt|nc|ez|fl|ht){2,}\b|\+\s*(?:hd|hr|dt|nc|ez|fl|ht)+\b|\b\d{2,4}\s*pp\b|\b\d{2,3}\.\d{1,2}\s*%|[\[(]\s*(?:fc|ss|choke|nomod|nm|dt|hr)\s*[)\]])/giu

const TITLE_SPLIT = /\s*[-–—|]\s+|\s+[-–—|:]\s*|:\s+/
const BY_SPLIT = /\s+by\s+/i
const ARTIST_SEPARATORS = /\s*(?:,|&|\+|\/|\bfeat\.?\b|\bft\.?\b|\bfeaturing\b|\bwith\b|\band\b|\bx\b|\bи\b)\s*/i

function normalized(text: string): string {
  return text
    .toLowerCase()
    .normalize('NFKD')
    .replace(/\p{M}+/gu, '')
    .replace(/['’`ʼ]/g, '')
    .replace(/(?<=[\p{L}\p{N}])!(?=[\p{L}\p{N}])/gu, 'i')
    .replace(/(?<=[\p{L}\p{N}])\$(?=[\p{L}\p{N}])/gu, 's')
    .replace(/&/g, ' and ')
    .replace(/[^\p{L}\p{N}]+/gu, ' ')
    .trim()
}

/** Strips non-musical parenthetical noise from track titles before query building and scoring. */
export function cleanAudioTitle(raw: string): string {
  let result = raw.trim()
  for (let i = 0; i < 3; i++) {
    const next = result
      .replace(BRACKET_NOISE, '')
      .replace(DASH_NOISE, '')
      .replace(TRAILING_FEAT, '')
      .replace(TRAILING_LYRICS, '')
      .trim()
    if (next === result) break
    result = next
  }
  return result || raw.trim()
}

function durationToleranceSec(targetDurSec: number): number {
  return Math.max(12, Math.min(25, Math.floor(targetDurSec / 8)))
}

function officialMvToleranceSec(targetDurSec: number): number {
  return Math.max(durationToleranceSec(targetDurSec), Math.min(60, Math.floor(targetDurSec / 3)))
}

function editionTags(title: string): Set<string> {
  const cleaned = title.replace(SAFE_EDITION_NOISE, ' ')
  const out = new Set<string>()
  for (const m of cleaned.matchAll(EDITION_WORDS)) {
    out.add(m[0].toLowerCase().replace(/[\s-]+/g, ' ').trim())
  }
  for (const m of cleaned.matchAll(GAMEPLAY_PATTERNS)) {
    out.add('game:' + m[0].toLowerCase().replace(/[\s-]+/g, ' ').trim())
  }
  return out
}

function channelVariantTags(rawArtist: string): Set<string> {
  const out = new Set<string>()
  for (const m of rawArtist.matchAll(SUSPICIOUS_CHANNEL_WORDS)) {
    out.add(m[0].toLowerCase().replace(/[\s-]+/g, ' ').trim())
  }
  return out
}

function sameSet(a: Set<string>, b: Set<string>): boolean {
  if (a.size !== b.size) return false
  for (const x of a) if (!b.has(x)) return false
  return true
}

function addArtistVariants(out: Set<string>, raw: string) {
  const cleaned = normalized(raw)
    .replace(/\s*(?:-\s*)?topic$/, '')
    .replace(/\s*vevo$/, '')
    .replace(/\s*official$/, '')
    .trim()
  if (!cleaned) return
  out.add(cleaned)
  const words = cleaned.split(' ').filter(Boolean)
  const latin = words.filter((w) => /[a-z]/.test(w) && ![...w].some((c) => c.charCodeAt(0) > 127))
  const nonLatin = words.filter((w) => [...w].some((c) => c.charCodeAt(0) > 127))
  if (latin.length && nonLatin.length) {
    const l = latin.join(' ')
    const nl = nonLatin.join(' ')
    if (l.length >= 2) out.add(l)
    if (nl.length >= 2) out.add(nl)
  }
}

function splitArtistCredits(names: string[]): string[] {
  const out = new Set<string>()
  for (const raw of names) {
    if (!raw?.trim()) continue
    addArtistVariants(out, raw)
    for (const part of raw.split(ARTIST_SEPARATORS)) {
      addArtistVariants(out, part)
    }
  }
  return [...out]
}

function artistPartVariants(part: string): string[] {
  const out = new Set<string>()
  addArtistVariants(out, part)
  for (const sub of part.split(ARTIST_SEPARATORS)) {
    addArtistVariants(out, sub)
  }
  return [...out].filter((x) => x.length >= 2)
}

function artistMatchBonus(targetArtists: string[], candArtists: string[], leadTarget: string): number | null {
  if (!targetArtists.length || !candArtists.length) return null
  if (candArtists.includes(leadTarget)) return 2_000
  if (targetArtists.some((a) => candArtists.includes(a))) return 1_400
  return null
}

function titleSimilarityScore(targetTitle: string, candidateTitle: string): number | null {
  if (!targetTitle || !candidateTitle) return null
  if (targetTitle === candidateTitle) return 10_000
  if (targetTitle.replace(/\s+/g, '') === candidateTitle.replace(/\s+/g, '')) return 9_500
  const tWords = targetTitle.split(' ').filter(Boolean)
  const cWords = candidateTitle.split(' ').filter(Boolean)
  if (!tWords.length || !cWords.length) return null
  if (tWords.length === 1) {
    return cWords.length === 1 && tWords[0] === cWords[0] ? 10_000 : null
  }
  const cSet = new Set(cWords)
  const tSet = new Set(tWords)
  let intersection = 0
  for (const w of tSet) if (cSet.has(w)) intersection++
  const union = new Set([...tSet, ...cSet]).size
  const jaccard = intersection / union
  const extraWords = Math.abs(cWords.length - tWords.length)
  const targetContained = ` ${candidateTitle} `.includes(` ${targetTitle} `) && extraWords <= 3
  const candContained = ` ${targetTitle} `.includes(` ${candidateTitle} `) && extraWords <= 2
  if (!targetContained && !candContained && jaccard < 0.72) return null
  const bonus = targetContained || candContained ? 1_000 : 0
  return 6_000 + Math.round(jaccard * 2_500) + bonus
}

/**
 * Scores a candidate track against Spotify metadata.
 * When `allowArtistMismatch` is true (used for SoundCloud reuploads), uploader channel mismatches
 * are permitted while edition, gameplay/montage and duration guards still apply.
 */
export function matchCandidateScore(
  targetTitle: string,
  targetArtistsRaw: string[],
  targetDurSec: number,
  candTitle: string,
  candArtistRaw: string,
  candDurSec: number | undefined,
  candViews: number | undefined,
  looseDuration: boolean,
  allowArtistMismatch = false,
): number | null {
  if (!candDurSec || candDurSec <= 0 || targetDurSec <= 0) return null

  const targetEditions = editionTags(targetTitle)
  if (!sameSet(targetEditions, editionTags(candTitle))) return null

  const candChannelVariants = channelVariantTags(candArtistRaw)
  if (candChannelVariants.size) {
    const allowed = new Set([...channelVariantTags(targetArtistsRaw.join(' ')), ...targetEditions])
    for (const v of candChannelVariants) if (!allowed.has(v)) return null
  }

  const targetArtists = splitArtistCredits(targetArtistsRaw)
  const leadTarget = targetArtists[0]
  if (!leadTarget) return null
  const cleanTarget = normalized(cleanAudioTitle(targetTitle))
  if (!cleanTarget) return null

  const candCredits = splitArtistCredits([candArtistRaw])
  const channelArtistBonus = artistMatchBonus(targetArtists, candCredits, leadTarget)

  const diffSec = Math.abs(targetDurSec - candDurSec)
  const strictTol = durationToleranceSec(targetDurSec)
  const isOfficialMvOverrun =
    !looseDuration &&
    channelArtistBonus !== null &&
    candDurSec > targetDurSec &&
    diffSec > strictTol &&
    diffSec <= officialMvToleranceSec(targetDurSec)

  if (looseDuration) {
    if (candDurSec < 50) return null
    const ratio = candDurSec / targetDurSec
    if (ratio < 0.55 || ratio > 1.45) return null
  } else if (!isOfficialMvOverrun && diffSec > strictTol) {
    return null
  }

  const cleanCand = normalized(cleanAudioTitle(candTitle))
  let bestTitleScore = titleSimilarityScore(cleanTarget, cleanCand)
  let bestArtistBonus = channelArtistBonus

  const considerSplit = (artistPart: string, titlePart: string) => {
    const partArtists = artistPartVariants(artistPart)
    const partArtistBonus = artistMatchBonus(targetArtists, partArtists, leadTarget)
    const partTitleScore = titleSimilarityScore(cleanTarget, normalized(cleanAudioTitle(titlePart)))
    if (partTitleScore !== null && (partArtistBonus !== null || bestArtistBonus !== null)) {
      const combined = Math.max(partArtistBonus ?? 0, bestArtistBonus ?? 0)
      if (bestTitleScore === null || bestArtistBonus === null || partTitleScore > bestTitleScore) {
        bestTitleScore = partTitleScore
        bestArtistBonus = combined
      }
    }
  }

  const allParts = candTitle.split(TITLE_SPLIT).map((p) => p.trim()).filter(Boolean)
  if (allParts.length >= 2) {
    considerSplit(allParts[0], allParts.slice(1).join(' - '))
    considerSplit(allParts[allParts.length - 1], allParts.slice(0, -1).join(' - '))
    for (let i = 0; i < allParts.length - 1; i++) {
      considerSplit(allParts[i], allParts[i + 1])
      considerSplit(allParts[i + 1], allParts[i])
    }
  }
  const byIdx = candTitle.search(BY_SPLIT)
  let byParts: string[] = []
  if (byIdx > 0) {
    byParts = candTitle.split(BY_SPLIT)
    if (byParts.length >= 2) considerSplit(byParts.slice(1).join(' '), byParts[0])
  }

  if (allowArtistMismatch && bestArtistBonus === null && bestTitleScore === null) {
    for (const part of allParts) {
      const partTitleScore = titleSimilarityScore(cleanTarget, normalized(cleanAudioTitle(part)))
      if (partTitleScore !== null && (bestTitleScore === null || partTitleScore > bestTitleScore)) {
        bestTitleScore = partTitleScore
      }
    }
    if (byParts.length >= 2) {
      const partTitleScore = titleSimilarityScore(cleanTarget, normalized(cleanAudioTitle(byParts[0])))
      if (partTitleScore !== null && (bestTitleScore === null || partTitleScore > bestTitleScore)) {
        bestTitleScore = partTitleScore
      }
    }
  }

  if (bestTitleScore === null) return null
  let artistScore: number
  if (bestArtistBonus !== null) {
    artistScore = bestArtistBonus
  } else if (allowArtistMismatch) {
    const paddedCand = ` ${cleanCand} `
    artistScore = targetArtists.some((a) => a.length >= 2 && paddedCand.includes(` ${a} `)) ? 800 : 0
  } else {
    return null
  }
  if ((looseDuration || isOfficialMvOverrun) && (bestTitleScore < 9_500 || (!allowArtistMismatch && artistScore < 2_000))) {
    return null
  }

  const officialChannelBonus = channelArtistBonus !== null ? 2_500 : 0
  let viewsAdjustment = 0
  if (candViews !== undefined) {
    if (channelArtistBonus !== null) {
      if (candViews >= 1_000) viewsAdjustment = Math.min(1_500, Math.round((Math.log10(candViews) - 2) * 250))
    } else if (candViews < 500) {
      viewsAdjustment = -3_000
    } else if (candViews < 2_000) {
      viewsAdjustment = -1_000
    } else if (candViews >= 10_000) {
      viewsAdjustment = Math.min(1_200, Math.round((Math.log10(candViews) - 3) * 300))
    }
  }
  const diffMs = diffSec * 1000
  const durationPenalty = isOfficialMvOverrun ? Math.min(2_500, Math.floor(diffMs / 10)) : Math.floor(diffMs / 10)
  return Math.max(1, bestTitleScore + artistScore + officialChannelBonus + viewsAdjustment - durationPenalty)
}

const POPULAR_BLOCKED_EDITIONS = new Set([
  'karaoke', 'backing track', 'minus', 'minus one', 'off vocal', 'no vocal', 'no vocals',
  'without vocal', 'without vocals', 'tutorial', 'lesson', 'tab', 'tabs', 'chord', 'chords',
  'synthesia', 'midi', 'ringtone', 'reaction', 'analysis', 'review', 'breakdown', 'interview',
  'commentary', 'teaser', 'trailer', 'snippet', 'preview', 'минус', 'минусовка', 'караоке',
  'под гитару', 'на пианино', 'на гитаре', 'без слов', 'без вокала', 'прохождение', 'геймплей',
  'カラオケ', '原曲歌手', 'リアクション', '切り抜き',
])

/** Fallback ranking for YouTube candidates when strict and loose matching return nothing. */
export function popularYouTubeFallbackScore(
  targetTitle: string,
  targetArtistsRaw: string[],
  targetDurSec: number,
  candTitle: string,
  candArtistRaw: string,
  candDurSec: number | undefined,
  candViews: number | undefined,
): number | null {
  const minDur = targetDurSec >= 15 && targetDurSec <= 45 ? Math.max(12, Math.floor(targetDurSec / 2)) : 30
  const maxDur = targetDurSec > 450 ? targetDurSec * 2 : 900
  if (!candTitle.trim() || (candDurSec && (candDurSec < minDur || candDurSec > maxDur))) return null

  const targetEditions = editionTags(targetTitle)
  for (const tag of editionTags(candTitle)) {
    if (!targetEditions.has(tag) && (tag.startsWith('game:') || POPULAR_BLOCKED_EDITIONS.has(tag))) return null
  }
  const allowedChannel = new Set([...channelVariantTags(targetArtistsRaw.join(' ')), ...targetEditions])
  let channelPenalty = 0
  for (const v of channelVariantTags(candArtistRaw)) {
    if (!allowedChannel.has(v)) {
      channelPenalty = 4_000
      break
    }
  }

  const targetTitleWords = normalized(cleanAudioTitle(targetTitle)).split(' ').filter((w) => w.length >= 2)
  const targetArtists = splitArtistCredits(targetArtistsRaw)
  const targetArtistWords = targetArtists.flatMap((a) => a.split(' ')).filter((w) => w.length >= 2)
  const candCredits = splitArtistCredits([candArtistRaw])
  const candWords = new Set(
    [...normalized(candTitle).split(' '), ...candCredits.flatMap((a) => a.split(' '))].filter((w) => w.length >= 2),
  )

  const titleHits = targetTitleWords.filter((w) => candWords.has(w)).length
  const artistHits = targetArtistWords.filter((w) => candWords.has(w)).length
  const leadTarget = targetArtists[0]
  const officialBonus = leadTarget && artistMatchBonus(targetArtists, candCredits, leadTarget) !== null ? 5_000 : 0
  const hasOverlap = titleHits > 0 || artistHits > 0 || officialBonus > 0
  const tierBase = hasOverlap ? 15_000 : 1_000
  const views = Math.max(0, candViews ?? 0)
  const viewsScore = views > 0 ? Math.round(Math.log10(views + 1) * 2_000) : 0
  const durPenalty =
    targetDurSec > 0 && candDurSec ? Math.min(6_000, Math.floor((Math.abs(targetDurSec - candDurSec) * 1000) / 50)) : 0
  return Math.max(1, tierBase + viewsScore + officialBonus + titleHits * 800 + artistHits * 600 - durPenalty - channelPenalty)
}
