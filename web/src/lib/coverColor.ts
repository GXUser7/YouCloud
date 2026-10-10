// The colour a cover gives the page, as the app's CoverTheme picks it: the cover's pixels
// quantized, and Material's scoring of which colour suits a theme best. SoundCloud's and Yandex's
// image servers both allow it (Access-Control-Allow-Origin: *).

import { argbFromRgb, hexFromArgb, QuantizerCelebi, Score } from '@material/material-color-utilities'
import { reach } from './media.svelte'

const cache = new Map<string, Promise<string | null>>()

function read(url: string): Promise<string | null> {
  return new Promise((resolve) => {
    const img = new Image()
    img.crossOrigin = 'anonymous'
    img.decoding = 'async'
    img.onload = () => {
      try {
        const size = 48
        const canvas = document.createElement('canvas')
        canvas.width = canvas.height = size
        const ctx = canvas.getContext('2d', { willReadFrequently: true })!
        ctx.drawImage(img, 0, 0, size, size)
        const data = ctx.getImageData(0, 0, size, size).data
        const pixels: number[] = []
        for (let i = 0; i < data.length; i += 4) if (data[i + 3] > 250) pixels.push(argbFromRgb(data[i], data[i + 1], data[i + 2]))
        const ranked = Score.score(QuantizerCelebi.quantize(pixels, 64), { desired: 1, fallbackColorARGB: 0, filter: true })
        resolve(ranked[0] ? hexFromArgb(ranked[0]) : null)
      } catch {
        resolve(null)
      }
    }
    img.onerror = () => resolve(null)
    img.src = url
  })
}

export function coverColor(url: string | null | undefined): Promise<string | null> {
  if (!url) return Promise.resolve(null)
  // Keyed by where it is read from: a cover that failed directly is read again through the Worker.
  const from = reach(url)
  let found = cache.get(from)
  if (!found) {
    found = read(from)
    cache.set(from, found)
  }
  return found
}
