// Exercise the real fitting action without a browser. The fake layout only supplies the DOM
// measurements; the action's scheduling and font-size search run unchanged.
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import ts from 'typescript'

const frames = new Map()
const events = new Map()
let sequence = 0
let observer
const exports = {}
const source = ts.transpileModule(readFileSync(new URL('../src/lib/fit.ts', import.meta.url), 'utf8'), {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
}).outputText
vm.runInNewContext(source, {
  exports,
  document: {},
  requestAnimationFrame: (callback) => { const id = ++sequence; frames.set(id, callback); return id },
  cancelAnimationFrame: (id) => frames.delete(id),
  addEventListener: (name, callback) => events.set(name, callback),
  removeEventListener: (name, callback) => { if (events.get(name) === callback) events.delete(name) },
  ResizeObserver: class {
    constructor(callback) { this.callback = callback; this.disconnected = false; observer = this }
    observe(node) { this.node = node }
    disconnect() { this.disconnected = true }
    resized(width) { if (!this.disconnected) this.callback([{ contentRect: { width } }]) }
  },
})

let width = 1800
const node = {
  textContent: 'привет',
  style: { fontSize: '16px' },
  get clientWidth() { return width },
  get scrollWidth() { return Math.ceil(Math.min(width, this.textContent.length * Number.parseFloat(this.style.fontSize) * 0.6)) },
  get scrollHeight() {
    const size = Number.parseFloat(this.style.fontSize)
    const lines = Math.ceil(this.textContent.length * size * 0.6 / width)
    return Math.ceil(lines * size * 0.88)
  },
}
const limit = 420
const options = (text) => ({ text, max: 420, min: 28, height: () => limit })
function paintFrame() {
  const pending = [...frames.values()]
  frames.clear()
  for (const callback of pending) callback()
}

const action = exports.fit(node, options(node.textContent))
assert.equal(Number.parseFloat(node.style.fontSize), 420, 'a short poster title retains its original huge maximum')
observer.resized(width)
paintFrame()

// Svelte updates an action's options before replacing its text node in the same DOM flush.
// A synchronous measure here would see the old six-letter title and leave the new title huge.
const longTitle = 'привет (prod. eloqwnc3, anandaa) — длинное название с несколькими словами'
action.update(options(longTitle))
assert.equal(Number.parseFloat(node.style.fontSize), 420, 'text updates must defer measurement until the DOM flush finishes')
assert.equal(frames.size, 1)
node.textContent = longTitle
paintFrame()
const wideSize = Number.parseFloat(node.style.fontSize)
assert.ok(wideSize < 300, 'the new long title is measured instead of the previous short one')
assert.ok(node.scrollHeight <= limit + 1)

// The title can become narrower without a window-resize event (a panel or column changed).
width = 900
observer.resized(width)
paintFrame()
assert.ok(Number.parseFloat(node.style.fontSize) < wideSize, 'a changed title column is fitted again')
assert.ok(node.scrollHeight <= limit + 1)
observer.resized(width)
assert.equal(frames.size, 0, 'unchanged widths do not create a resize loop')

// Several changes in one flush coalesce, and removing the title cancels pending work.
action.update(options('следующий трек'))
events.get('resize')()
assert.equal(frames.size, 1)
action.destroy()
assert.equal(frames.size, 0, 'unmounted titles must not be measured on a later frame')
assert.equal(observer.disconnected, true)
assert.equal(events.has('resize'), false)

console.log('Title fitting: DOM-update ordering, original poster scale, column resize and cleanup passed')
