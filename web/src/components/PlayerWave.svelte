<script lang="ts">
  // «Волна в плеере»: behind the full player, a matte wave in the cover's colours (the page's
  // palette, which the cover tints), moving to what plays (lib/hearing.ts). Four looks: dunes low
  // on the screen; the «Моя волна» shape growing from under the cover, a ring leaving it on each
  // kick; a tide of the spectrum, the bass in the middle; a cloth of silk over all of it.
  //
  // It is drawn small — a fifth of the window — and blurred there, then stretched: soft is what
  // it is meant to be, and a small picture costs a laptop next to nothing. Thirty frames a second
  // while it plays, fifteen paused, none in a hidden tab; with reduced motion it stands still.
  import { onMount } from 'svelte'
  import { app, player, WAVE } from '../lib/state.svelte'
  import { BANDS, hear, stopHearing, type Levels } from '../lib/hearing'

  let { around }: { around?: HTMLElement } = $props()

  type RGB = [number, number, number]
  type Colors = { bg: RGB; deep: RGB; mid: RGB; light: RGB }

  // The picture is this many times smaller than the window; the blur, in its own pixels, per look.
  const SHRINK = 5
  const BLUR = { dunes: 7, shape: 6, tide: 6, silk: 4.5 }

  const REST: Levels = { bass: 0, mid: 0, high: 0, bands: new Float32Array(BANDS), energy: 0, heard: false }

  let canvas: HTMLCanvasElement
  let soft = $state(false)

  const calm = matchMedia('(prefers-reduced-motion: reduce)')

  function parse(value: string): RGB | null {
    const v = value.trim()
    if (/^#[0-9a-f]{6}$/i.test(v)) {
      const n = parseInt(v.slice(1), 16)
      return [n >> 16, (n >> 8) & 255, n & 255]
    }
    const m = /rgba?\(\s*([\d.]+)[\s,]+([\d.]+)[\s,]+([\d.]+)/.exec(v)
    return m ? [+m[1], +m[2], +m[3]] : null
  }

  /** The page's palette as it is now: theme.ts sets it from the cover, --seed being the cover's own. */
  function palette(): Colors | null {
    const style = getComputedStyle(document.documentElement)
    const get = (name: string) => parse(style.getPropertyValue(name))
    const bg = get('--surface'), deep = get('--primary-container'), primary = get('--primary')
    if (!bg || !deep || !primary) return null
    return { bg, deep, mid: get('--seed') ?? primary, light: get('--primary-fixed-dim') ?? primary }
  }

  const css = (c: RGB, a = 1) => `rgba(${Math.round(c[0])},${Math.round(c[1])},${Math.round(c[2])},${Math.max(0, Math.min(1, a)).toFixed(3)})`
  const mix = (a: RGB, b: RGB, t: number): RGB => [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t]

  /** A wave line across [W]: its height at every second pixel. */
  function across(W: number, y: (x: number) => number): number[] {
    const ys: number[] = []
    for (let x = 0; x <= W; x += 2) ys.push(y(x))
    return ys
  }

  /** The area under [ys] down to the bottom, filled. */
  function under(g: CanvasRenderingContext2D, W: number, H: number, ys: number[], fill: string | CanvasGradient) {
    g.beginPath()
    g.moveTo(0, H)
    ys.forEach((y, j) => g.lineTo(j * 2, y))
    g.lineTo(W, H)
    g.closePath()
    g.fillStyle = fill
    g.fill()
  }

  function crest(g: CanvasRenderingContext2D, ys: number[], color: string) {
    g.beginPath()
    ys.forEach((y, j) => (j ? g.lineTo(j * 2, y) : g.moveTo(0, y)))
    g.strokeStyle = color
    g.lineWidth = 1.5
    g.stroke()
  }

  /** Dunes: four layers low on the screen; the bass lifts the crests, the highs ripple their edges. */
  function dunes(g: CanvasRenderingContext2D, W: number, H: number, L: Levels, f: number, C: Colors) {
    const glow = g.createRadialGradient(W * 0.5, H * 1.05, 0, W * 0.5, H * 1.05, H * (0.75 + 0.25 * L.bass))
    glow.addColorStop(0, css(C.mid, 0.3 + 0.35 * L.bass))
    glow.addColorStop(1, css(C.mid, 0))
    g.fillStyle = glow
    g.fillRect(0, 0, W, H)
    for (let i = 0; i < 4; i++) {
      const d = i / 3
      const base = H * (0.5 + 0.24 * d)
      const amp = H * (0.03 + 0.04 * L.energy + (0.1 - 0.04 * d) * L.bass + 0.03 * L.mid)
      const k1 = 1.1 + 0.45 * i, k2 = 2.6 + 0.7 * i
      const s1 = f * (0.5 + 0.22 * i) + i * 1.7, s2 = -f * (0.8 + 0.15 * i) + i
      const ys = across(W, (x) => {
        const u = (x / W) * Math.PI * 2
        return base + amp * (0.65 * Math.sin(u * k1 + s1) + 0.35 * Math.sin(u * k2 + s2)) - H * 0.012 * L.high * Math.sin(u * 9 + f * 3)
      })
      const fill = g.createLinearGradient(0, base - amp, 0, H)
      fill.addColorStop(0, css(mix(C.deep, C.mid, 0.2 + 0.55 * d), 0.5 + 0.25 * d))
      fill.addColorStop(1, css(C.deep, 0.3 + 0.2 * d))
      under(g, W, H, ys, fill)
      if (i >= 2) crest(g, ys, css(C.light, 0.18 + 0.4 * L.high * L.energy))
    }
  }

  function lobed(g: CanvasRenderingContext2D, cx: number, cy: number, r: number, n: number, depth: number, turn: number) {
    g.beginPath()
    for (let i = 0; i <= 160; i++) {
      const a = (i / 160) * Math.PI * 2
      const rr = r * (1 + depth * Math.cos(n * (a + turn)))
      const x = cx + rr * Math.cos(a), y = cy + rr * Math.sin(a)
      if (i) g.lineTo(x, y)
      else g.moveTo(x, y)
    }
    g.closePath()
  }

  type Ring = { r: number; a: number; turn: number }

  /** The wave's shape: the mood's lobes under the cover, three turning inside each other; a ring
   *  leaves the cover on each kick (or now and then, when the wave only breathes). */
  function shape(g: CanvasRenderingContext2D, W: number, H: number, L: Levels, f: number, C: Colors, at: Spot, rings: Ring[], dt: number) {
    const mood = WAVE.moods.find((m) => m.seed === app.waveMood)
    const n = mood?.lobes ?? 10
    const cx = W * at.x, cy = H * at.y
    const R = H * at.r * 1.35 * (1 + 0.08 * L.bass)
    const far = g.createRadialGradient(W * 0.85, H * 0.25, 0, W * 0.85, H * 0.25, H * 0.9)
    far.addColorStop(0, css(C.deep, 0.4))
    far.addColorStop(1, css(C.deep, 0))
    g.fillStyle = far
    g.fillRect(0, 0, W, H)
    for (let i = rings.length - 1; i >= 0; i--) {
      const ring = rings[i]
      ring.r += dt * H * 0.42
      ring.a -= dt * 0.22
      if (ring.a <= 0) {
        rings.splice(i, 1)
        continue
      }
      lobed(g, cx, cy, ring.r, n, 0.05, ring.turn)
      g.strokeStyle = css(C.light, ring.a)
      g.lineWidth = H * 0.012
      g.stroke()
    }
    const depth = (mood?.depth ?? 0.07) * 0.6 + 0.05 * L.energy + 0.06 * L.bass
    // Outside in: darker and quieter outward, so the title and the controls beside the cover keep
    // to the dark.
    const layers: [number, RGB, number, number, number][] = [
      [1.9, mix(C.bg, C.deep, 0.7), f * 0.05, n + 2, 0.7],
      [1.45, C.deep, -f * 0.08, n, 0.7],
      [1.05, mix(C.deep, C.mid, 0.45), f * 0.12, n, 0.8],
    ]
    for (const [k, color, turn, lobes, alpha] of layers) {
      lobed(g, cx, cy, R * k, lobes, depth, turn)
      g.fillStyle = css(color, alpha)
      g.fill()
    }
    const core = g.createRadialGradient(cx, cy, 0, cx, cy, R * 1.2)
    core.addColorStop(0, css(C.light, 0.2 + 0.3 * L.bass))
    core.addColorStop(1, css(C.light, 0))
    g.fillStyle = core
    g.fillRect(0, 0, W, H)
  }

  function level(bands: Float32Array, p: number): number {
    const x = p * (BANDS - 1), i = Math.floor(x), k = x - i
    const a = bands[Math.min(i, BANDS - 1)], b = bands[Math.min(i + 1, BANDS - 1)]
    return a + (b - a) * (0.5 - 0.5 * Math.cos(k * Math.PI))
  }

  /** Tide: the spectrum melted along the bottom, the bass in the middle and the highs out to the
   *  edges; behind it a slower layer, and a swell that keeps both moving when the music is quiet. */
  function tide(g: CanvasRenderingContext2D, W: number, H: number, L: Levels, f: number, C: Colors, slow: Float32Array) {
    const sky = g.createLinearGradient(0, 0, 0, H)
    sky.addColorStop(0, css(C.deep, 0.25))
    sky.addColorStop(0.6, css(C.deep, 0))
    g.fillStyle = sky
    g.fillRect(0, 0, W, H)
    const layer = (bands: Float32Array, base: number, lift: number, color: RGB, alpha: number, swell: number) => {
      const ys = across(W, (x) => base - H * lift * level(bands, Math.abs(x / W - 0.5) * 2) - H * 0.035 * Math.sin((x / W) * Math.PI * 3 + f * swell))
      const fill = g.createLinearGradient(0, Math.min(...ys), 0, H)
      fill.addColorStop(0, css(color, alpha))
      fill.addColorStop(1, css(C.deep, alpha * 0.6))
      under(g, W, H, ys, fill)
      return ys
    }
    layer(slow, H * 0.8, 0.42, mix(C.deep, C.mid, 0.25), 0.7, 0.6)
    crest(g, layer(L.bands, H * 0.88, 0.36, C.mid, 0.6, -0.9), css(C.light, 0.25 + 0.45 * L.high * L.energy))
  }

  /** Silk: a cloth laid on the diagonal over the whole screen. The bass gathers its folds, a sheen
   *  runs across them, the highs make it glint; a veil of the surface keeps the words readable. */
  function silk(g: CanvasRenderingContext2D, W: number, H: number, L: Levels, f: number, C: Colors) {
    const FOLDS = 12
    const edge = (k: number, x: number) => {
      const u = (x / W) * Math.PI * 2
      const A = H * (0.04 + 0.04 * L.energy + 0.1 * L.bass * (0.6 + 0.4 * Math.sin(k * 1.3)))
      return H * (-0.35 + (1.7 * k) / FOLDS) - (x - W / 2) * 0.32 + A * Math.sin(u * 0.9 + f * 0.7 + k * 0.45) + A * 0.5 * Math.sin(u * 2.1 - f * 1.1 + k * 0.9)
    }
    for (let k = 0; k < FOLDS; k++) {
      g.beginPath()
      for (let x = 0; x <= W; x += 3) {
        if (x) g.lineTo(x, edge(k, x))
        else g.moveTo(x, edge(k, x))
      }
      for (let x = W - (W % 3); x >= 0; x -= 3) g.lineTo(x, edge(k + 1, x) + 1)
      g.closePath()
      const sheen = Math.pow(0.5 + 0.5 * Math.sin(k * 0.8 - f * 1.4), 3)
      const shade = 0.15 + 0.6 * sheen + 0.2 * L.high * L.energy
      const c = shade > 0.7 ? mix(C.mid, C.light, (shade - 0.7) / 0.6) : mix(C.deep, C.mid, shade / 0.7)
      const fill = g.createLinearGradient(0, H * 0.1, W * 0.3, H)
      fill.addColorStop(0, css(c, 0.9))
      fill.addColorStop(1, css(mix(c, C.deep, 0.5), 0.9))
      g.fillStyle = fill
      g.fill()
    }
    g.fillStyle = css(C.bg, 0.5)
    g.fillRect(0, 0, W, H)
  }

  /** Where the cover is, as shares of the picture: the shape grows from under it. */
  type Spot = { x: number; y: number; r: number }

  function spot(): Spot {
    const box = canvas.getBoundingClientRect()
    const art = around?.getBoundingClientRect()
    // Narrow, with the lyrics or the queue in its place, there is no cover: left of the middle.
    if (!art?.width || !box.width) return { x: 0.3, y: 0.52, r: 0.22 }
    return { x: (art.left + art.width / 2 - box.left) / box.width, y: (art.top + art.height / 2 - box.top) / box.height, r: art.width / 2 / box.height }
  }

  onMount(() => {
    const g = canvas.getContext('2d')!
    const scene = document.createElement('canvas')
    const s = scene.getContext('2d')!
    // The blur is the canvas's own where it can (Safari's can't): a CSS one would blur the whole
    // window-sized layer on every frame.
    soft = !('filter' in CanvasRenderingContext2D.prototype)

    let colors = palette()
    let shown: Colors | null = colors && { ...colors }
    let at = spot()
    const rings: Ring[] = []
    const slow = new Float32Array(BANDS)
    let wasKick = false
    let ringAt = 0
    let flow = 0, last = 0, drawn = 0, lookedAt = 0
    let raf = 0

    let W = 2, H = 2
    const sized = new ResizeObserver(() => {
      W = Math.max(2, Math.round(canvas.clientWidth / SHRINK))
      H = Math.max(2, Math.round(canvas.clientHeight / SHRINK))
      canvas.width = scene.width = W
      canvas.height = scene.height = H
      drawn = 0
    })
    sized.observe(canvas)

    const frame = (now: number) => {
      raf = requestAnimationFrame(frame)
      const still = calm.matches
      if (now - drawn < (still ? 500 : player.playing ? 33 : 66)) return
      const dt = last ? Math.min(0.1, (now - last) / 1000) : 0
      last = drawn = now
      // The palette and the cover's place, a few times a second: they change with the track and
      // the layout, not with every frame.
      if (now - lookedAt > 250) {
        lookedAt = now
        colors = palette() ?? colors
        at = spot()
      }
      if (!colors) return
      shown ??= { ...colors }
      const k = 1 - Math.exp(-dt * 3)
      for (const key of ['bg', 'deep', 'mid', 'light'] as const) shown[key] = mix(shown[key], colors[key], still ? 1 : k)
      const L = still ? REST : hear(now, dt, app.waveHears)
      if (!still) flow += dt * (0.15 + 0.85 * L.energy)
      for (let j = 0; j < BANDS; j++) slow[j] += (L.bands[j] * 1.15 - slow[j]) * (1 - Math.exp(-dt * 1.6))
      const kick = L.heard ? L.bass > 0.7 : player.playing && now - ringAt > 2400
      if (kick && !wasKick && now - ringAt > 250 && L.energy > 0.4) {
        rings.push({ r: H * at.r * 1.5, a: 0.55 * L.energy, turn: flow * 0.2 })
        ringAt = now
      }
      wasKick = L.heard ? L.bass > 0.45 : kick

      s.fillStyle = css(shown.bg)
      s.fillRect(0, 0, W, H)
      const look = app.waveLook
      if (look === 'shape') shape(s, W, H, L, flow, shown, at, rings, dt)
      else if (look === 'tide') tide(s, W, H, L, flow, shown, slow)
      else if (look === 'silk') silk(s, W, H, L, flow, shown)
      else dunes(s, W, H, L, flow, shown)

      // Blurred onto the canvas a little larger than it, so its edges don't fade to nothing.
      const blur = BLUR[look] ?? 6
      const m = soft ? 0 : blur * 2
      g.fillStyle = css(shown.bg)
      g.fillRect(0, 0, W, H)
      if (!soft) g.filter = `blur(${blur}px)`
      g.drawImage(scene, -m, -m, W + m * 2, H + m * 2)
      g.filter = 'none'
    }
    raf = requestAnimationFrame(frame)

    return () => {
      cancelAnimationFrame(raf)
      sized.disconnect()
      stopHearing()
    }
  })
</script>

<div class="wave" aria-hidden="true">
  <canvas bind:this={canvas} class:soft></canvas>
  <svg class="grain">
    <filter id="wave-grain" x="0" y="0" width="100%" height="100%">
      <feTurbulence type="fractalNoise" baseFrequency=".9" numOctaves="2" stitchTiles="stitch" />
      <feColorMatrix values="0 0 0 0 1  0 0 0 0 1  0 0 0 0 1  0 0 0 .55 0" />
    </filter>
    <rect width="100%" height="100%" filter="url(#wave-grain)" />
  </svg>
</div>

<style>
  .wave {
    position: absolute;
    inset: 0;
    overflow: hidden;
    pointer-events: none;
    background: var(--surface);
    animation: wave-in 900ms var(--ease-in-place);
  }
  @keyframes wave-in {
    from {
      opacity: 0;
    }
  }
  canvas {
    display: block;
    width: 100%;
    height: 100%;
  }
  /* Where the canvas can't blur by itself: the browser does, larger than the window for the edges. */
  canvas.soft {
    filter: blur(30px);
    transform: scale(1.08);
  }
  /* Light, the wave is quieter, so the words on it stay dark enough. */
  :global(:root[data-theme='light']) canvas {
    opacity: 0.8;
  }
  .grain {
    position: absolute;
    inset: 0;
    width: 100%;
    height: 100%;
    opacity: 0.07;
  }
</style>
