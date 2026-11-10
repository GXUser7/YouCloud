const SELECTOR = '.glass, .glass-strong, .glass-overlay, [data-host-glass]'

/** Передаём только геометрию стекла: обои и их пиксели остаются в браузере, вне страницы. */
export function observeHostGlass(): () => void {
  let frame = 0
  let until = 0
  let previous = ''
  let disposed = false
  const observed = new Set<Element>()
  const resize = new ResizeObserver(() => wake())
  const send = (panels: object[]) => {
    const payload = { width: innerWidth, height: innerHeight, panels }
    const key = JSON.stringify(payload)
    if (key === previous) return
    previous = key
    window.postMessage({ yc: 'page', kind: 'glass', ...payload }, location.origin)
  }
  const measure = () => {
    frame = 0
    // В фоне Chromium сам приостанавливает rAF. document.hidden у восстановленного webview
    // может обновиться позже первого кадра: не оставляем из-за этого выбранную вкладку без стекла.
    const elements = [...document.querySelectorAll<HTMLElement>(SELECTOR)]
    const present = new Set<Element>(elements)
    for (const element of observed) if (!present.has(element)) { resize.unobserve(element); observed.delete(element) }
    const panels: object[] = []
    for (const element of elements) {
      if (!observed.has(element)) { observed.add(element); resize.observe(element) }
      // Вложенное стекло уже видит размытие внешнего контейнера; его содержимое размывает сам сайт.
      if (element.parentElement?.closest(SELECTOR)) continue
      const style = getComputedStyle(element)
      const blur = Number(/blur\(([\d.]+)px\)/.exec(style.backdropFilter)?.[1] ?? 0)
      if (!blur || !element.checkVisibility({ checkVisibilityCSS: true, checkOpacity: true })) continue
      const rect = element.getBoundingClientRect()
      if (!rect.width || !rect.height) continue
      const scaleX = rect.width / element.offsetWidth, scaleY = rect.height / element.offsetHeight
      const clip = [0, 0, innerWidth, innerHeight]
      let opacity = Number(style.opacity)
      let fade: number[] | undefined
      for (let ancestor = element.parentElement; ancestor; ancestor = ancestor.parentElement) {
        const css = getComputedStyle(ancestor)
        const box = ancestor.getBoundingClientRect()
        opacity *= Number(css.opacity)
        if (/hidden|clip|scroll|auto/.test(css.overflowX)) {
          clip[0] = Math.max(clip[0], box.left)
          clip[2] = Math.min(clip[2], box.right)
        }
        if (/hidden|clip|scroll|auto/.test(css.overflowY)) {
          clip[1] = Math.max(clip[1], box.top)
          clip[3] = Math.min(clip[3], box.bottom)
        }
        const edge = parseFloat(css.getPropertyValue('--edge-fade-height'))
        if (css.maskImage !== 'none' && edge > 0) fade = [box.top, box.bottom, edge]
      }
      if (rect.right <= clip[0] || rect.left >= clip[2] || rect.bottom <= clip[1] || rect.top >= clip[3] || opacity < 0.01) continue
      const radii = [style.borderTopLeftRadius, style.borderTopRightRadius, style.borderBottomRightRadius, style.borderBottomLeftRadius]
        .flatMap((value) => {
          const [x, y = x] = value.split(' ').map(parseFloat)
          return [x * scaleX, y * scaleY]
        })
      panels.push({ x: rect.x, y: rect.y, w: rect.width, h: rect.height, radii, clip, fade,
        blur: blur * (scaleX + scaleY) / 2, saturation: Number(/saturate\(([\d.]+)\)/.exec(style.backdropFilter)?.[1] ?? 1), opacity })
      if (panels.length === 64) break
    }
    send(panels)
    if (performance.now() < until) frame = requestAnimationFrame(measure)
  }
  function wake() {
    if (disposed) return
    until = performance.now() + 700
    if (!frame) frame = requestAnimationFrame(measure)
  }
  const mutations = new MutationObserver((records) => {
    if (records.some((record) => record.type === 'attributes' ||
      [...record.addedNodes, ...record.removedNodes].some((node) => node instanceof Element))) wake()
  })
  mutations.observe(document.body, { childList: true, subtree: true, attributes: true, attributeFilter: ['class', 'hidden', 'open', 'data-theme'] })
  const events = ['scroll', 'resize', 'transitionrun', 'transitionend', 'animationstart', 'animationend', 'visibilitychange']
  for (const event of events) window.addEventListener(event, wake, { capture: true, passive: true })
  wake()
  return () => {
    disposed = true
    cancelAnimationFrame(frame)
    resize.disconnect()
    mutations.disconnect()
    for (const event of events) window.removeEventListener(event, wake, true)
    send([])
  }
}
