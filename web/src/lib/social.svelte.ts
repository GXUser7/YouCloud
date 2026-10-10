// YouCloud accounts and friends, on the app's own Supabase project (supabase/migrations): an
// account is a nick and a password, friends see what each other plays (now_playing), and every
// change comes over Realtime while the site is open. The site tells friends what it plays, as the
// app's NowPlayingPublisher does.

import { createClient, type RealtimeChannel, type Session } from '@supabase/supabase-js'
import { artistNames, type Track } from './catalog'
import { currentTrack, player } from './state.svelte'

const URL = 'https://eolrdgyugwgjkmpzihar.supabase.co'
// The project's publishable key: what every client ships, the row rules guard the data.
const KEY = 'sb_publishable_JmC1zt5tZS3TjVAcXARDOw_PgCmide6'
// Never a real domain: the database checks only the part before the @ against the nick.
const EMAIL_DOMAIN = 'youcloud.invalid'

export const supabase = createClient(URL, KEY, {
  auth: { storageKey: 'yc.supabase', persistSession: true, autoRefreshToken: true, detectSessionInUrl: false },
})

export interface ShowcaseItem {
  title: string
  subtitle?: string | null
  cover?: string | null
  link?: string | null
  service?: string | null
}

export interface Profile {
  id: string
  nick: string
  name?: string | null
  color?: string | null
  avatar_v: number
  share_listening: boolean
  fav_artist?: ShowcaseItem | null
  fav_track?: ShowcaseItem | null
  fav_album?: ShowcaseItem | null
}

export type Relation = 'self' | 'friend' | 'outgoing' | 'incoming' | 'none'

export interface Person {
  id: string
  nick: string
  name?: string | null
  color?: string | null
  avatar_v: number
  relation: Relation
  friends?: number
  title?: string | null
  artist?: string | null
  cover_url?: string | null
  service?: string | null
  track_url?: string | null
  playing?: boolean | null
  started_at?: string | null
  duration_ms?: number | null
  updated_at?: string | null
}

export const social = $state({
  ready: false,
  me: null as Profile | null,
  friends: [] as Person[],
  friendsLoaded: false,
})

export const shownName = (p: { name?: string | null; nick: string }) => p.name?.trim() || p.nick
export const emailOf = (nick: string) => `${nick.trim().toLowerCase()}@${EMAIL_DOMAIN}`

export const avatarUrl = (p: { id: string; avatar_v: number }) =>
  p.avatar_v > 0 ? `${URL}/storage/v1/object/public/avatars/${p.id}/avatar.webp?v=${p.avatar_v}` : null

/** A track told about longer ago than this, still "playing", is taken as gone quiet. */
const STALE_AFTER_MS = 12 * 60_000

export function listeningNow(p: Person, now = Date.now()): boolean {
  if (!p.playing || !p.title || !p.updated_at) return false
  return now - Date.parse(p.updated_at) < STALE_AFTER_MS
}

/** How far into the track a friend is, as far as can be told: only while it plays. */
export function positionOf(p: Person, now = Date.now()): number | null {
  if (!p.started_at) return null
  const ms = now - Date.parse(p.started_at)
  return Math.max(0, p.duration_ms ? Math.min(ms, p.duration_ms) : ms) / 1000
}

/** "5 мин назад", "вчера". */
export function ago(iso: string | null | undefined, now = Date.now()): string {
  if (!iso) return ''
  const min = Math.round((now - Date.parse(iso)) / 60_000)
  if (min < 1) return 'только что'
  if (min < 60) return `${min} мин назад`
  const h = Math.round(min / 60)
  if (h < 24) return `${h} ч назад`
  const d = Math.round(h / 24)
  return d === 1 ? 'вчера' : `${d} дн назад`
}

/** What went wrong, in words for the screen, as the app says it. */
export function socialMessage(e: unknown): string {
  const err = e as { code?: string; status?: number; message?: string }
  switch (err?.code) {
    case 'user_already_exists':
    case 'email_exists':
      return 'Этот ник уже занят'
    case 'invalid_credentials':
    case 'invalid_grant':
      return 'Неверный ник или пароль'
    case 'weak_password':
      return 'Пароль слишком простой'
    case 'over_request_rate_limit':
      return 'Слишком много попыток. Попробуй чуть позже'
    case 'email_address_invalid':
      return 'Сервер не принял ник'
  }
  if (err?.status === 429) return 'Слишком много попыток. Попробуй чуть позже'
  if (err?.message?.includes('Failed to fetch')) return 'Нет связи с сервером'
  return err?.message || 'Что-то пошло не так'
}

// ------------------------------------------------------------------------------------ account

async function refreshMe(session: Session | null) {
  if (!session) {
    social.me = null
    return
  }
  const { data, error } = await supabase
    .from('profiles')
    .select('id,nick,name,color,avatar_v,share_listening,fav_artist,fav_track,fav_album')
    .eq('id', session.user.id)
    .single()
  if (!error && data) social.me = data as Profile
}

export async function nickAvailable(nick: string): Promise<boolean> {
  const { data, error } = await supabase.rpc('nick_available', { candidate: nick.trim().toLowerCase() })
  if (error) throw error
  return data as boolean
}

export async function signIn(nick: string, password: string) {
  const { error } = await supabase.auth.signInWithPassword({ email: emailOf(nick), password })
  if (error) throw error
}

const CODE_ALPHABET = '23456789ABCDEFGHJKLMNPQRSTUVWXYZ'

async function sha256(text: string): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(text))
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, '0')).join('')
}

/** Makes the account and signs in; answers the recovery code, shown once (only its hash is kept). */
export async function signUp(nick: string, password: string, name: string, color: string): Promise<string> {
  const clean = nick.trim().toLowerCase()
  const { data, error } = await supabase.auth.signUp({
    email: emailOf(clean),
    password,
    options: { data: { nick: clean, name: name.trim(), color } },
  })
  if (error) throw error
  const userId = data.user?.id
  if (!userId) throw new Error('Аккаунт не создался')
  const random = crypto.getRandomValues(new Uint8Array(16))
  const code = [...random].map((b) => CODE_ALPHABET[b % CODE_ALPHABET.length]).join('')
  await supabase.rpc('set_recovery_code', { hash: await sha256(`${userId}:${code}`) })
  return code.match(/.{4}/g)!.join('-')
}

export async function signOut() {
  await tellStopped()
  await supabase.auth.signOut()
}

export async function setShareListening(on: boolean) {
  if (!social.me) return
  const { error } = await supabase.from('profiles').update({ share_listening: on }).eq('id', social.me.id)
  if (error) throw error
  social.me = { ...social.me, share_listening: on }
}

// ------------------------------------------------------------------------------------ friends

export async function loadFriends() {
  const { data, error } = await supabase.rpc('friends_overview')
  if (error) throw error
  social.friends = (data ?? []) as Person[]
  social.friendsLoaded = true
}

export async function searchPeople(q: string): Promise<Person[]> {
  if (!q.trim()) return []
  const { data, error } = await supabase.rpc('search_profiles', { q })
  if (error) throw error
  return (data ?? []) as Person[]
}

async function changeFriend(fn: 'request_friend' | 'accept_friend' | 'remove_friend', target: string): Promise<Relation> {
  const { data, error } = await supabase.rpc(fn, { target })
  if (error) throw error
  loadFriends().catch(() => {})
  return data as Relation
}

export const requestFriend = (id: string) => changeFriend('request_friend', id)
export const acceptFriend = (id: string) => changeFriend('accept_friend', id)
export const removeFriend = (id: string) => changeFriend('remove_friend', id)

// Changes to friends, friendships and profiles, as they happen. Bursts of them reload once.
let channel: RealtimeChannel | null = null
let reloadTimer: ReturnType<typeof setTimeout> | undefined

function watch(on: boolean) {
  if (!on) {
    channel?.unsubscribe()
    channel = null
    return
  }
  if (channel) return
  const reload = () => {
    clearTimeout(reloadTimer)
    reloadTimer = setTimeout(() => {
      loadFriends().catch(() => {})
      supabase.auth.getSession().then(({ data }) => refreshMe(data.session))
    }, 400)
  }
  channel = supabase.channel('yc-friends')
  for (const table of ['now_playing', 'friendships', 'profiles']) {
    channel.on('postgres_changes', { event: '*', schema: 'public', table }, reload)
  }
  channel.subscribe()
}

let accessToken: string | null = null

supabase.auth.onAuthStateChange((event, session) => {
  accessToken = session?.access_token ?? null
  // Supabase asks not to await its own calls inside this callback.
  setTimeout(async () => {
    await refreshMe(session)
    social.ready = true
    if (session && (event === 'SIGNED_IN' || event === 'INITIAL_SESSION')) {
      loadFriends().catch(() => {})
      watch(true)
    }
    if (!session) {
      social.friends = []
      social.friendsLoaded = false
      watch(false)
    }
  })
})

// ------------------------------------------------------------------------------------ now playing

const SERVICE_NAMES = { sc: 'soundcloud', ya: 'yandex', yt: 'youtube' } as const

type Snapshot = {
  title: string
  artist: string | null
  cover_url: string | null
  service: string
  track_url: string | null
  playing: boolean
  started_at: string | null
  duration_ms: number | null
}

function snapshot(t: Track, playing: boolean): Snapshot {
  return {
    title: t.title,
    artist: artistNames(t) || null,
    cover_url: t.cover?.startsWith('http') ? t.cover : null,
    service: SERVICE_NAMES[t.service],
    track_url: t.link ?? null,
    playing,
    started_at: playing ? new Date(Date.now() - player.position * 1000).toISOString() : null,
    duration_ms: Math.round((player.duration || t.duration) * 1000) || null,
  }
}

let told: (Snapshot & { at: number }) | null = null

async function tell(s: Snapshot) {
  const me = social.me
  if (!me || !me.share_listening) return
  told = { ...s, at: Date.now() }
  await supabase.from('now_playing').upsert({ user_id: me.id, ...s, updated_at: new Date().toISOString() }, { onConflict: 'user_id' })
}

async function tellStopped() {
  if (told?.playing) await tell({ ...told, playing: false, started_at: null }).catch(() => {})
}

const SETTLE_MS = 2_500
const HEARTBEAT_MS = 5 * 60_000

let settle: ReturnType<typeof setTimeout> | undefined

/** Tells friends what plays once it has settled (a skip through the queue isn't news), and every
 *  few minutes while it plays, so a row long untouched means the listener went quiet. */
function schedule() {
  clearTimeout(settle)
  settle = setTimeout(() => {
    const t = currentTrack()
    if (!t) {
      tellStopped()
      return
    }
    tell(snapshot(t, player.playing && !player.loading)).catch(() => {})
  }, SETTLE_MS)
}

$effect.root(() => {
  $effect(() => {
    // What friends see changes with the track, playing or not, and with a jump within it.
    void currentTrack()?.id
    void player.playing
    void player.seeks
    void social.me?.id
    void social.me?.share_listening
    schedule()
  })
})

setInterval(() => {
  if (told?.playing && Date.now() - told.at >= HEARTBEAT_MS - SETTLE_MS) schedule()
}, 30_000)

// Closing the tab: the track stops for friends too. A keepalive request outlives the page.
addEventListener('pagehide', () => {
  const me = social.me
  if (!me || !told?.playing || !accessToken) return
  fetch(`${URL}/rest/v1/now_playing?on_conflict=user_id`, {
    method: 'POST',
    keepalive: true,
    headers: { apikey: KEY, authorization: `Bearer ${accessToken}`, 'content-type': 'application/json', prefer: 'resolution=merge-duplicates' },
    body: JSON.stringify({ user_id: me.id, ...told, at: undefined, playing: false, started_at: null, updated_at: new Date().toISOString() }),
  })
})
