import { mount } from 'svelte'
import './app.css'
import App from './App.svelte'
// Ready for a Yandex token the extension holds, even before any page asks for a login.
import './lib/login'
// Signs in to YouCloud (Supabase) from the saved session and starts telling friends what plays.
import './lib/social.svelte'

mount(App, { target: document.getElementById('app')! })
