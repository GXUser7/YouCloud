import { defineConfig, type Plugin } from 'vite'
import { svelte } from '@sveltejs/vite-plugin-svelte'
import { cloudflare } from '@cloudflare/vite-plugin'
import { fileURLToPath } from 'node:url'
import { EXTENSION_ZIP, packExtension } from './scripts/extension-zip'
import { buildHelper, HELPER_MAC, HELPER_WINDOWS, helperStamp } from './scripts/helper-build'

const EXTENSION = fileURLToPath(new URL('./extension', import.meta.url))
const HELPER = fileURLToPath(new URL('./helper', import.meta.url))

// The extension as the site hands it out: zipped into the build, and zipped fresh on each ask in dev.
function extensionZip(): Plugin {
  return {
    name: 'youcloud-extension-zip',
    configureServer(server) {
      server.middlewares.use('/' + EXTENSION_ZIP, (_req, res) => {
        res.setHeader('content-type', 'application/zip')
        res.end(packExtension(EXTENSION))
      })
    },
    generateBundle() {
      if (this.environment.name !== 'client') return
      this.emitFile({ type: 'asset', fileName: EXTENSION_ZIP, source: packExtension(EXTENSION) })
    },
  }
}

// The helper as the site hands it out: built into the site, and in dev built when first asked
// (again only after its sources change — Go takes a few seconds for the three of them).
function helperFiles(): Plugin {
  let built: { stamp: number; files: Record<string, Buffer> } | null = null
  const files = () => {
    const stamp = helperStamp(HELPER)
    if (built?.stamp !== stamp) built = { stamp, files: buildHelper(HELPER) }
    return built.files
  }
  return {
    name: 'youcloud-helper',
    configureServer(server) {
      for (const [name, type] of [
        [HELPER_WINDOWS, 'application/vnd.microsoft.portable-executable'],
        [HELPER_MAC, 'application/zip'],
      ]) {
        server.middlewares.use('/' + name, (_req, res) => {
          try {
            const body = files()[name]
            res.setHeader('content-type', type)
            res.end(body)
          } catch (e) {
            res.statusCode = 500
            res.end(String((e as Error).message))
          }
        })
      }
    },
    generateBundle() {
      if (this.environment.name !== 'client') return
      for (const [fileName, source] of Object.entries(files())) this.emitFile({ type: 'asset', fileName, source })
    },
  }
}

// The Cloudflare plugin runs the Worker inside the dev server, so `/x/` works locally on the same
// origin as the site, exactly as it will on Cloudflare.
export default defineConfig({
  plugins: [svelte(), cloudflare(), extensionZip(), helperFiles()],
  server: { port: 5190, strictPort: true },
})
