// Stand-in catalogue for the design: what the three services and the friends list will hand the
// pages once the real APIs are wired. Names are made up; covers are drawn from each item's colour.

import {
  artist,
  artistNames,
  artists,
  putArtist,
  putSet,
  putTrack,
  serviceName,
  sets,
  SERVICES,
  set,
  track,
  tracks,
  type Artist,
  type Service,
  type SetKind,
  type Track,
  type TrackSet,
} from './catalog'

export { artist, artistNames, artists, serviceName, sets, SERVICES, set, track, tracks }
export type { Artist, Service, SetKind, Track, TrackSet }

export interface Friend {
  id: string
  nick: string
  name: string
  color: string
  relation: 'friend' | 'incoming' | 'outgoing'
  playing?: { track: string; live: boolean; at: number }
  seen?: string
}

const artistList: Artist[] = [
  { id: 'kira', name: 'Kira Volna', service: 'sc', color: '#7e57c2', followers: '48 тыс. подписчиков', albums: 3 },
  { id: 'lowtide', name: 'Low Tide Club', service: 'sc', color: '#26a69a', followers: '12 тыс. подписчиков', albums: 2 },
  { id: 'saturn', name: 'сатурн в огне', service: 'sc', color: '#ef6c00', followers: '6,1 тыс. подписчиков', albums: 4 },
  { id: 'moth', name: 'moth/mouth', service: 'sc', color: '#8d6e63', followers: '3,4 тыс. подписчиков', albums: 1 },
  { id: 'glass', name: 'glassbeam', service: 'sc', color: '#42a5f5', followers: '21 тыс. подписчиков', albums: 2 },
  { id: 'nora', name: 'Нора', service: 'sc', color: '#ec407a', followers: '9,8 тыс. подписчиков', albums: 2 },
  { id: 'severny', name: 'Северный ветер', service: 'ya', color: '#5c6bc0', followers: '310 тыс. слушателей', albums: 6 },
  { id: 'mart', name: 'Мартовские', service: 'ya', color: '#ffb300', followers: '1,2 млн слушателей', albums: 8 },
  { id: 'ostrova', name: 'Острова', service: 'ya', color: '#00897b', followers: '84 тыс. слушателей', albums: 3 },
  { id: 'teply', name: 'Тёплый шум', service: 'ya', color: '#d84315', followers: '156 тыс. слушателей', albums: 5 },
  { id: 'luma', name: 'Luma', service: 'ya', color: '#ab47bc', followers: '42 тыс. слушателей', albums: 2 },
  { id: 'lis', name: 'Полярный лис', service: 'ya', color: '#039be5', followers: '670 тыс. слушателей', albums: 7 },
  { id: 'neon', name: 'Neon Harbor', service: 'yt', color: '#e91e63', followers: '2,3 млн подписчиков', albums: 5 },
  { id: 'paper', name: 'Paper Planets', service: 'yt', color: '#7cb342', followers: '880 тыс. подписчиков', albums: 4 },
  { id: 'cafe', name: 'Café Static', service: 'yt', color: '#a1887f', followers: '5,4 млн подписчиков', albums: 10 },
  { id: 'orbit', name: 'Orbit Girls', service: 'yt', color: '#29b6f6', followers: '1,1 млн подписчиков', albums: 3 },
  { id: 'dune', name: 'Dune Theory', service: 'yt', color: '#ffa726', followers: '430 тыс. подписчиков', albums: 2 },
  { id: 'hush', name: 'Hush Hours', service: 'yt', color: '#5e35b1', followers: '760 тыс. подписчиков', albums: 6 },
]

const t = (
  id: string,
  title: string,
  artistIds: string[],
  service: Service,
  duration: number,
  color: string,
  album?: string,
  explicit = false,
): Track => ({
  id,
  title,
  artists: artistIds.map((a) => ({ id: a, name: artistList.find((x) => x.id === a)!.name })),
  service,
  duration,
  color,
  album: album ? { id: album, title: '' } : undefined,
  explicit,
})

const trackList: Track[] = [
  t('sc1', 'ночной автобус', ['kira'], 'sc', 154, '#a8673a', 'sc-al-kira'),
  t('sc2', 'стекло и неон', ['kira', 'glass'], 'sc', 131, '#3f51b5', 'sc-al-kira'),
  t('sc3', 'Velvet Static', ['lowtide'], 'sc', 203, '#00897b'),
  t('sc4', 'не звони после двух', ['saturn'], 'sc', 118, '#e65100', undefined, true),
  t('sc5', 'Satellite Heart', ['glass'], 'sc', 176, '#1e88e5'),
  t('sc6', 'холодный чай', ['nora'], 'sc', 142, '#d81b60'),
  t('sc7', 'Low Tide', ['lowtide'], 'sc', 228, '#00695c'),
  t('sc8', 'дым над рекой', ['moth'], 'sc', 97, '#795548'),
  t('sc9', 'Paper Moon Drive', ['glass', 'nora'], 'sc', 189, '#5c6bc0'),
  t('sc10', 'аварийный выход', ['saturn'], 'sc', 125, '#bf360c', undefined, true),
  t('sc11', 'Midnight Arcade', ['kira'], 'sc', 167, '#8e24aa', 'sc-al-kira'),
  t('sc12', 'последний этаж', ['moth', 'saturn'], 'sc', 109, '#6d4c41'),
  t('ya1', 'Сентябрь навсегда', ['severny'], 'ya', 214, '#3949ab', 'ya-al-perelet'),
  t('ya2', 'Огни на трассе', ['severny'], 'ya', 187, '#283593', 'ya-al-perelet'),
  t('ya3', 'Солнце за МКАДом', ['mart'], 'ya', 199, '#f9a825', 'ya-al-mkad'),
  t('ya4', 'Летим', ['mart', 'lis'], 'ya', 176, '#ff8f00', 'ya-al-mkad'),
  t('ya5', 'Тише воды', ['ostrova'], 'ya', 241, '#00796b'),
  t('ya6', 'Мандарины', ['teply'], 'ya', 158, '#e64a19'),
  t('ya7', 'Острова', ['ostrova'], 'ya', 226, '#00897b'),
  t('ya8', 'Северное сияние', ['lis'], 'ya', 205, '#0288d1', 'ya-al-siyanie'),
  t('ya9', 'Ламповый', ['teply'], 'ya', 173, '#bf360c'),
  t('ya10', 'Кометы', ['luma'], 'ya', 192, '#8e24aa'),
  t('ya11', 'Перелёт', ['severny'], 'ya', 233, '#1a237e', 'ya-al-perelet'),
  t('ya12', 'Полярная ночь', ['lis'], 'ya', 218, '#01579b', 'ya-al-siyanie'),
  t('ya13', 'Вокзалы', ['severny'], 'ya', 196, '#303f9f', 'ya-al-perelet'),
  t('ya14', 'Тёплый февраль', ['severny', 'luma'], 'ya', 208, '#5e35b1', 'ya-al-perelet'),
  t('yt1', 'Coffee & Rain', ['cafe'], 'yt', 182, '#8d6e63'),
  t('yt2', 'Harbor Lights', ['neon'], 'yt', 207, '#c2185b', 'yt-al-harbor'),
  t('yt3', 'Slow Motion Summer', ['dune'], 'yt', 194, '#fb8c00'),
  t('yt4', 'Paper Planets', ['paper'], 'yt', 171, '#689f38'),
  t('yt5', 'Orbit', ['orbit'], 'yt', 188, '#039be5'),
  t('yt6', 'Golden Hour Loop', ['cafe'], 'yt', 246, '#a1887f'),
  t('yt7', 'Snow Day', ['hush'], 'yt', 163, '#5e35b1'),
  t('yt8', 'Night Shift', ['neon'], 'yt', 199, '#ad1457', 'yt-al-harbor'),
  t('yt9', 'Lantern', ['hush', 'cafe'], 'yt', 177, '#4527a0'),
  t('yt10', 'Soft Focus', ['paper'], 'yt', 152, '#558b2f'),
  t('yt11', 'City Pop Dream', ['orbit', 'neon'], 'yt', 215, '#0277bd'),
  t('yt12', 'Afterglow', ['dune'], 'yt', 229, '#ef6c00'),
]

const s = (
  id: string,
  kind: SetKind,
  title: string,
  subtitle: string,
  service: Service,
  color: string,
  tracks: string[],
  extra: Partial<TrackSet> = {},
): TrackSet => ({ id, kind, title, subtitle, service, color, tracks, loaded: true, ...extra })

const scAll = trackList.filter((x) => x.service === 'sc').map((x) => x.id)
const yaAll = trackList.filter((x) => x.service === 'ya').map((x) => x.id)
const ytAll = trackList.filter((x) => x.service === 'yt').map((x) => x.id)
const rotate = (ids: string[], by: number) => ids.slice(by).concat(ids.slice(0, by))

const setList: TrackSet[] = [
  s('sc-mix1', 'mix', 'Твой микс 1', 'Kira Volna, glassbeam, Нора, сатурн в огне', 'sc', '#4dd0e1', scAll, { label: 'MIX 1' }),
  s('sc-mix2', 'mix', 'Твой микс 2', 'Low Tide Club, moth/mouth, Нора', 'sc', '#ffb74d', rotate(scAll, 3), { label: 'MIX 2' }),
  s('sc-mix3', 'mix', 'Твой микс 3', 'сатурн в огне, Kira Volna', 'sc', '#ba68c8', rotate(scAll, 6), { label: 'MIX 3' }),
  s('sc-mix4', 'mix', 'Твой микс 4', 'glassbeam, Low Tide Club', 'sc', '#81c784', rotate(scAll, 8), { label: 'MIX 4' }),
  s('sc-mix5', 'mix', 'Твой микс 5', 'Нора, moth/mouth, glassbeam', 'sc', '#f06292', rotate(scAll, 10), { label: 'MIX 5' }),
  s('sc-st-kira', 'station', 'Kira Volna', 'Станция по артисту', 'sc', '#7e57c2', rotate(scAll, 1)),
  s('sc-st-lowtide', 'station', 'Low Tide Club', 'Станция по артисту', 'sc', '#26a69a', rotate(scAll, 2)),
  s('sc-st-saturn', 'station', 'сатурн в огне', 'Станция по артисту', 'sc', '#ef6c00', rotate(scAll, 4)),
  s('sc-st-glass', 'station', 'glassbeam', 'Станция по артисту', 'sc', '#42a5f5', rotate(scAll, 5)),
  s('sc-st-nora', 'station', 'Нора', 'Станция по артисту', 'sc', '#ec407a', rotate(scAll, 7)),
  s('sc-g-hiphop', 'genre', 'Хип-хоп', 'Топ-50 недели', 'sc', '#ff7043', rotate(scAll, 2)),
  s('sc-g-electro', 'genre', 'Электроника', 'Топ-50 недели', 'sc', '#26c6da', rotate(scAll, 4)),
  s('sc-g-indie', 'genre', 'Инди', 'Топ-50 недели', 'sc', '#9ccc65', rotate(scAll, 6)),
  s('sc-g-lofi', 'genre', 'Лоу-фай', 'Топ-50 недели', 'sc', '#a1887f', rotate(scAll, 8)),
  s('sc-g-pop', 'genre', 'Поп', 'Топ-50 недели', 'sc', '#f48fb1', rotate(scAll, 9)),
  s('sc-g-rock', 'genre', 'Рок', 'Топ-50 недели', 'sc', '#90a4ae', rotate(scAll, 11)),
  s('sc-al-kira', 'album', 'Ночные маршруты', 'Kira Volna', 'sc', '#6a4fb3', ['sc1', 'sc2', 'sc11', 'sc5', 'sc9'], { year: 2026 }),
  s('sc-likes', 'liked', 'Нравится', '214 треков', 'sc', '#ff7043', ['sc6', 'sc1', 'sc9', 'sc3', 'sc11', 'sc4', 'sc7']),
  s('sc-pl-night', 'playlist', 'ночь, город, наушники', 'Плейлист · 38 треков', 'sc', '#5c6bc0', rotate(scAll, 5)),
  s('sc-pl-gym', 'playlist', 'зал', 'Плейлист · 22 трека', 'sc', '#e53935', rotate(scAll, 2)),

  s('ya-day', 'personal', 'Плейлист дня', 'Обновляется каждый день', 'ya', '#ffca28', yaAll),
  s('ya-deja', 'personal', 'Дежавю', 'То, что ты давно не слышал', 'ya', '#ab47bc', rotate(yaAll, 4)),
  s('ya-prem', 'personal', 'Премьера', 'Новинки от твоих артистов', 'ya', '#ef5350', rotate(yaAll, 7)),
  s('ya-taynik', 'personal', 'Тайник', 'Редкие треки под твой вкус', 'ya', '#26a69a', rotate(yaAll, 9)),
  s('ya-otkr', 'personal', 'Открытия', 'Новые для тебя артисты', 'ya', '#42a5f5', rotate(yaAll, 2)),
  s('ya-al-perelet', 'album', 'Перелёт', 'Северный ветер', 'ya', '#3949ab', ['ya11', 'ya1', 'ya2', 'ya13', 'ya14'], { year: 2026 }),
  s('ya-al-mkad', 'album', 'Солнце за МКАДом', 'Мартовские', 'ya', '#f9a825', ['ya3', 'ya4'], { year: 2026 }),
  s('ya-al-siyanie', 'album', 'Сияние', 'Полярный лис', 'ya', '#0288d1', ['ya8', 'ya12'], { year: 2025 }),
  s('ya-al-lamp', 'single', 'Ламповый', 'Тёплый шум', 'ya', '#bf360c', ['ya9'], { year: 2026 }),
  s('ya-al-komety', 'single', 'Кометы', 'Luma', 'ya', '#8e24aa', ['ya10'], { year: 2026 }),
  s('ya-al-ostrova', 'album', 'Тише воды', 'Острова', 'ya', '#00796b', ['ya5', 'ya7'], { year: 2025 }),
  s('ya-pl-autumn', 'playlist', 'Осень в наушниках', 'Яндекс Музыка', 'ya', '#ff7043', rotate(yaAll, 3)),
  s('ya-pl-run', 'playlist', 'Для пробежки', 'Яндекс Музыка', 'ya', '#66bb6a', rotate(yaAll, 5)),
  s('ya-pl-morning', 'playlist', 'Бодрое утро', 'Яндекс Музыка', 'ya', '#ffee58', rotate(yaAll, 1)),
  s('ya-pl-night', 'playlist', 'Ночная смена', 'Яндекс Музыка', 'ya', '#3f51b5', rotate(yaAll, 8)),
  s('ya-pl-indie', 'playlist', 'Русский инди', 'Яндекс Музыка', 'ya', '#8d6e63', rotate(yaAll, 10)),
  s('ya-likes', 'liked', 'Мне нравится', '486 треков', 'ya', '#ef5350', ['ya8', 'ya3', 'ya1', 'ya6', 'ya10', 'ya5', 'ya12', 'ya4']),
  s('ya-pl-car', 'playlist', 'В машину', 'Плейлист · 64 трека', 'ya', '#78909c', rotate(yaAll, 6)),

  s('yt-liked', 'liked', 'Понравившаяся музыка', '128 треков', 'yt', '#e53935', ['yt2', 'yt1', 'yt5', 'yt7', 'yt11', 'yt3']),
  s('yt-mix1', 'mix', 'Мой микс 1', 'Neon Harbor, Orbit Girls, Dune Theory', 'yt', '#ec407a', ytAll, { label: 'Микс' }),
  s('yt-mix2', 'mix', 'Мой микс 2', 'Café Static, Hush Hours', 'yt', '#8d6e63', rotate(ytAll, 4), { label: 'Микс' }),
  s('yt-mix3', 'mix', 'Мой микс 3', 'Paper Planets, Dune Theory', 'yt', '#7cb342', rotate(ytAll, 7), { label: 'Микс' }),
  s('yt-super', 'mix', 'Мой супермикс', 'Всё, что ты любишь, вперемешку', 'yt', '#7e57c2', rotate(ytAll, 2), { label: 'Супер' }),
  s('yt-disc', 'mix', 'Открытия недели', 'Обновляется по средам', 'yt', '#26c6da', rotate(ytAll, 9), { label: 'Новое' }),
  s('yt-al-harbor', 'album', 'Harbor Lights', 'Neon Harbor', 'yt', '#c2185b', ['yt2', 'yt8', 'yt11'], { year: 2025 }),
  s('yt-al-golden', 'album', 'Golden Hour', 'Café Static', 'yt', '#a1887f', ['yt6', 'yt1', 'yt9'], { year: 2024 }),
  s('yt-al-orbit', 'album', 'Orbit', 'Orbit Girls', 'yt', '#039be5', ['yt5', 'yt11'], { year: 2026 }),
  s('yt-al-dune', 'single', 'Afterglow', 'Dune Theory', 'yt', '#ef6c00', ['yt12'], { year: 2026 }),
  s('yt-al-hush', 'album', 'Snow Day', 'Hush Hours', 'yt', '#5e35b1', ['yt7', 'yt9'], { year: 2025 }),
  s('yt-pl-study', 'playlist', 'Учёба и фокус', 'Плейлист · 51 трек', 'yt', '#4db6ac', rotate(ytAll, 3)),
]

artistList.forEach(putArtist)
setList.forEach(putSet)
trackList.forEach((x) => putTrack({ ...x, album: x.album ? { id: x.album.id, title: setList.find((s) => s.id === x.album!.id)?.title ?? '' } : undefined }))

export const tracksOf = (ids: string[]) => ids.map(track)
export const byService = <T extends { service: Service }>(list: Iterable<T>, svc: Service) => [...list].filter((x) => x.service === svc)

export const home = {
  sc: {
    mixes: ['sc-mix1', 'sc-mix2', 'sc-mix3', 'sc-mix4', 'sc-mix5'],
    stations: ['sc-st-kira', 'sc-st-lowtide', 'sc-st-saturn', 'sc-st-glass', 'sc-st-nora'],
    trending: ['sc-g-hiphop', 'sc-g-electro', 'sc-g-indie', 'sc-g-lofi', 'sc-g-pop', 'sc-g-rock'],
  },
  ya: {
    personal: ['ya-day', 'ya-deja', 'ya-prem', 'ya-taynik', 'ya-otkr'],
    releases: ['ya-al-perelet', 'ya-al-mkad', 'ya-al-siyanie', 'ya-al-lamp', 'ya-al-komety', 'ya-al-ostrova'],
    playlists: ['ya-pl-autumn', 'ya-pl-run', 'ya-pl-morning', 'ya-pl-night', 'ya-pl-indie'],
  },
  yt: {
    quick: ['yt2', 'yt1', 'yt5', 'yt7', 'yt11', 'yt3', 'yt9', 'yt4', 'yt12', 'yt6', 'yt8', 'yt10'],
    mixes: ['yt-mix1', 'yt-mix2', 'yt-mix3', 'yt-super', 'yt-disc'],
    again: ['yt-al-harbor', 'yt-al-golden', 'yt-pl-study', 'yt-al-orbit', 'yt-al-hush'],
    albums: ['yt-al-orbit', 'yt-al-dune', 'yt-al-hush', 'yt-al-golden', 'yt-al-harbor'],
  },
}

export const library: Record<Service, { liked: string; playlists: string[]; artists: string[] }> = {
  sc: { liked: 'sc-likes', playlists: ['sc-pl-night', 'sc-pl-gym', 'sc-al-kira', 'sc-mix1'], artists: ['kira', 'glass', 'nora', 'lowtide'] },
  ya: { liked: 'ya-likes', playlists: ['ya-pl-car', 'ya-al-perelet', 'ya-pl-autumn', 'ya-day'], artists: ['severny', 'mart', 'lis', 'teply'] },
  yt: { liked: 'yt-liked', playlists: ['yt-pl-study', 'yt-al-harbor', 'yt-super', 'yt-al-golden'], artists: ['neon', 'cafe', 'orbit', 'hush'] },
}

export const releasesOf = (artistId: string) =>
  setList.filter((x) => (x.kind === 'album' || x.kind === 'single') && x.subtitle === artist(artistId).name)

export const tracksOfArtist = (artistId: string) => trackList.filter((x) => x.artists.some((a) => a.id === artistId))

export const similarTo = (artistId: string) => {
  const a = artist(artistId)
  return artistList.filter((x) => x.service === a.service && x.id !== artistId)
}

export const searchSuggestions = ['северный ветер', 'lofi', 'kira volna', 'осень', 'neon harbor']

export const me = { nick: 'gxuser7', name: 'GX', color: '#ddae7a' }

export const friends: Friend[] = [
  { id: 'f1', nick: 'lisa.wav', name: 'Лиза', color: '#e57373', relation: 'friend', playing: { track: 'ya8', live: true, at: 64 } },
  { id: 'f2', nick: 'artemka', name: 'Артём', color: '#64b5f6', relation: 'friend', playing: { track: 'sc4', live: true, at: 31 } },
  { id: 'f5', nick: 'nastya.s', name: 'Настя', color: '#ba68c8', relation: 'friend', playing: { track: 'yt6', live: true, at: 150 } },
  { id: 'f3', nick: 'polina_k', name: 'Полина', color: '#81c784', relation: 'friend', playing: { track: 'yt5', live: false, at: 88 }, seen: '12 мин назад' },
  { id: 'f4', nick: 'dimon', name: 'Дима', color: '#ffb74d', relation: 'friend', seen: 'вчера' },
  { id: 'f6', nick: 'maks_ozon', name: 'Макс', color: '#4db6ac', relation: 'incoming' },
  { id: 'f7', nick: 'vera', name: 'Вера', color: '#f06292', relation: 'outgoing' },
]

/** Lyrics for any track in the design: written for it, timed across three minutes. */
export const lyrics: [number, string][] = [
  [0, '♪'],
  [9, 'Город гасит окна по одному'],
  [15, 'и считает, сколько нас не спит'],
  [21, 'я ловлю твой голос в темноту,'],
  [27, 'будто он ещё со мной звучит'],
  [34, 'Мы по кругу, как ночной маршрут,'],
  [40, 'остановки помнят наизусть'],
  [46, 'всё, что мы оставили вчера,'],
  [52, 'всё, к чему я больше не вернусь'],
  [60, 'Не гаси, не гаси этот свет,'],
  [66, 'пусть горит до утра на седьмом'],
  [72, 'если завтра нас здесь уже нет —'],
  [78, 'мы останемся в нём'],
  [86, '♪'],
  [96, 'Провода гудят над головой,'],
  [102, 'фонари уходят в полукруг'],
  [108, 'я опять останусь на конечной,'],
  [114, 'потому что здесь тепло от рук'],
  [122, 'Не гаси, не гаси этот свет,'],
  [128, 'пусть горит до утра на седьмом'],
  [134, 'если завтра нас здесь уже нет —'],
  [140, 'мы останемся в нём'],
  [148, 'мы останемся в нём'],
  [156, '♪'],
]

export const WAVE_MOODS = [
  { seed: 'active', title: 'Бодрое', lobes: 12, depth: 0.085, color: '#ff7a4d' },
  { seed: 'fun', title: 'Весёлое', lobes: 6, depth: 0.15, color: '#ffc53d' },
  { seed: 'calm', title: 'Спокойное', lobes: 9, depth: 0.06, color: '#3dd3b0' },
  { seed: 'sad', title: 'Грустное', lobes: 5, depth: 0.04, color: '#9d8cff' },
]

export const WAVE_MODES = [
  { seed: 'favorite', title: 'Любимое' },
  { seed: 'discover', title: 'Незнакомое' },
  { seed: 'popular', title: 'Популярное' },
]

export const artistByName = (name: string) => artistList.find((a) => a.name === name)
