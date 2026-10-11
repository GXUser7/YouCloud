// The app's own Supabase project (supabase/migrations): YouCloud accounts, friends and «Любимое».
// One client for the site, so one session.

import { createClient } from '@supabase/supabase-js'

export const SUPABASE_URL = 'https://eolrdgyugwgjkmpzihar.supabase.co'
// The project's publishable key: what every client ships, the row rules guard the data.
export const SUPABASE_KEY = 'sb_publishable_JmC1zt5tZS3TjVAcXARDOw_PgCmide6'

export const supabase = createClient(SUPABASE_URL, SUPABASE_KEY, {
  auth: { storageKey: 'yc.supabase', persistSession: true, autoRefreshToken: true, detectSessionInUrl: false },
})
