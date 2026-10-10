// Yandex ID sends the browser back to music.yandex.ru with the new token in the address
// (#access_token=…) — the same place the app's login WebView catches it. The background keeps it
// only if YouCloud asked for a login a moment ago, and closes this tab.

const api = globalThis.browser ?? globalThis.chrome
const hash = new URLSearchParams(location.hash.slice(1))
const token = hash.get('access_token')
if (token) {
  api.runtime.sendMessage({ type: 'yc-ya-token', token, expiresIn: Number(hash.get('expires_in') ?? 0) })
}
