import { mount } from 'svelte'
import './app.css'
import App from './App.svelte'
import { installShapes } from './lib/shapes'
// Ready for a Yandex token the extension holds, even before any page asks for a login.
import './lib/login'
// Signs in to YouCloud (Supabase) from the saved session and starts telling friends what plays.
import './lib/social.svelte'
// Tells the helper what plays, for the status in Discord (and wakes it, so yt-dlp is ready early).
import './lib/presence.svelte'

// The shapes covers and pictures morph between (lib/shapes), named once for the whole page.
installShapes()

mount(App, { target: document.getElementById('app')! })
