// YouCloud's extension, the page half: runs only on YouCloud's own site and carries its requests to
// the background and the answers back, by window messages. The page learns the extension is here
// from the hello it says on load, or in answer to a ping. A token a login caught (Yandex's,
// SoundCloud's) is handed to the page here, then forgotten by the extension.

const api = globalThis.browser ?? globalThis.chrome
const version = api.runtime.getManifest().version
const reply = (data) => window.postMessage({ yc: 'ext', ...data }, location.origin)

window.addEventListener('message', (event) => {
  // A copy left over from before an update is cut off from the extension: the new one answers.
  if (!api.runtime?.id) return
  if (event.source !== window || event.origin !== location.origin) return
  const data = event.data
  if (!data || typeof data !== 'object' || data.yc !== 'page') return
  if (data.kind === 'ping') {
    reply({ kind: 'hello', version })
    // A token caught while this page was closed or reloading waits for the page to be listening.
    handOverTokens()
  }
  if (data.kind === 'fetch' || data.kind === 'call') {
    const message = data.kind === 'fetch' ? { type: 'yc-fetch', ...data.request } : { type: 'yc-call', name: data.name }
    // After the extension is reloaded this page's copy of it is cut off: say so rather than go quiet.
    try {
      api.runtime
        .sendMessage(message)
        .then((answer) => reply({ kind: 'response', id: data.id, ...(answer ?? { error: 'no answer' }) }))
        .catch((e) => reply({ kind: 'response', id: data.id, error: String(e?.message ?? e) }))
    } catch (e) {
      reply({ kind: 'response', id: data.id, error: String(e?.message ?? e) })
    }
  }
})

const TOKENS = { yaToken: 'ya-token', scToken: 'sc-token' }

async function handOverTokens() {
  const found = await api.storage.local.get(Object.keys(TOKENS))
  for (const [key, kind] of Object.entries(TOKENS)) {
    if (!found[key]) continue
    await api.storage.local.remove(key)
    reply({ kind, ...found[key] })
  }
}

api.storage.onChanged.addListener((changes, area) => {
  if (area === 'local' && Object.keys(TOKENS).some((k) => changes[k]?.newValue)) handOverTokens()
})

reply({ kind: 'hello', version })
