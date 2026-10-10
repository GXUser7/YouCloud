// Who the listener is on each service. Tokens stay in this browser (localStorage) and go only to
// their own service, through the Worker or the extension. Signing in is in lib/login.ts.

export interface ScAccount {
  token: string
  id: number
  name: string
  avatar?: string | null
}

export interface YaAccount {
  token: string
  uid: number
  name: string
  plus: boolean
}

function load<T>(key: string): T | null {
  try {
    const raw = localStorage.getItem('yc.acc.' + key)
    return raw ? (JSON.parse(raw) as T) : null
  } catch {
    return null
  }
}

function save(key: string, value: unknown) {
  try {
    if (value == null) localStorage.removeItem('yc.acc.' + key)
    else localStorage.setItem('yc.acc.' + key, JSON.stringify(value))
  } catch {
    // kept for this visit only
  }
}

export const accounts = $state({
  sc: load<ScAccount>('sc'),
  ya: load<YaAccount>('ya'),
  /** Whether this browser is signed in on YouTube: the extension signs YouTube's requests with it. */
  yt: false,
  /** The YouTube account those requests come back as — proof the session took. */
  ytName: null as string | null,
})

export const scToken = () => accounts.sc?.token
export const yaToken = () => accounts.ya?.token

export function setSoundCloud(account: ScAccount | null) {
  accounts.sc = account
  save('sc', account)
}

export function setYandex(account: YaAccount | null) {
  accounts.ya = account
  save('ya', account)
}
