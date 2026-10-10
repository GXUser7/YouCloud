import { defineConfig, type Plugin } from 'vite'
import { svelte } from '@sveltejs/vite-plugin-svelte'
import { cloudflare } from '@cloudflare/vite-plugin'
import { fileURLToPath } from 'node:url'
import { EXTENSION_ZIP, packExtension } from './scripts/extension-zip'

const EXTENSION = fileURLToPath(new URL('./extension', import.meta.url))

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

// The Cloudflare plugin runs the Worker inside the dev server, so `/x/` works locally on the same
// origin as the site, exactly as it will on Cloudflare.
export default defineConfig({
  plugins: [svelte(), cloudflare(), extensionZip()],
  server: { port: 5190, strictPort: true },
})
