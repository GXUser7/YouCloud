import { extensionHost, onExtension, onHostEvent } from './extension'

/** Наличие моста и выбор фона независимы: оригинальный фон тоже работает внутри Masaki. */
export const masaki = $state({
  connected: document.documentElement.dataset.host === 'masaki',
  transparent: document.documentElement.dataset.masakiBackground !== 'original',
})

onExtension(() => { masaki.connected = extensionHost() === 'masaki' })
onHostEvent((event) => {
  if (event.event === 'theme' && typeof event.transparent === 'boolean') masaki.transparent = event.transparent
})

export const transparentInMasaki = () => masaki.connected && masaki.transparent
