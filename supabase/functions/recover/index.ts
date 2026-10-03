// A new password for an account whose old one is lost: there is no email to send a link to, so
// the recovery code shown at sign-up stands in for one. Only the code's hash is kept
// (public.recovery_codes); it is hashed with the account's id, as the app does, and compared.
// Used once: the app makes a new code after signing in with the new password.
//
// Deployed with verify_jwt off: whoever calls it has, by definition, no session.

import { createClient } from "npm:@supabase/supabase-js@2";

const NICK = /^[a-z0-9_.]{3,20}$/;
const CODE_ALPHABET = /^[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{16}$/;

function reply(status: number, body: Record<string, unknown>): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

async function sha256Hex(text: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
  return Array.from(new Uint8Array(digest)).map((b) => b.toString(16).padStart(2, "0")).join("");
}

function sameText(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}

Deno.serve(async (req) => {
  if (req.method !== "POST") return reply(405, { error: "method_not_allowed" });

  let input: { nick?: unknown; code?: unknown; password?: unknown };
  try {
    input = await req.json();
  } catch {
    return reply(400, { error: "bad_request" });
  }
  const nick = typeof input.nick === "string" ? input.nick.trim().toLowerCase().replace(/^@/, "") : "";
  const code = typeof input.code === "string" ? input.code.toUpperCase().replace(/[^0-9A-Z]/g, "") : "";
  const password = typeof input.password === "string" ? input.password : "";
  if (!NICK.test(nick) || !CODE_ALPHABET.test(code)) return reply(400, { error: "wrong_code" });
  if (password.length < 8) return reply(400, { error: "weak_password" });

  const admin = createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    { auth: { persistSession: false, autoRefreshToken: false } },
  );

  const { data: profile, error: profileError } = await admin
    .from("profiles").select("id").eq("nick", nick).maybeSingle();
  if (profileError) return reply(500, { error: "server_error" });
  // The same answer for a nick that isn't there as for a wrong code: no telling which nicks exist.
  if (!profile) return reply(400, { error: "wrong_code" });

  const { data: saved, error: codeError } = await admin
    .from("recovery_codes").select("code_hash").eq("user_id", profile.id).maybeSingle();
  if (codeError) return reply(500, { error: "server_error" });
  const hash = await sha256Hex(`${profile.id}:${code}`);
  if (!saved || !sameText(saved.code_hash, hash)) return reply(400, { error: "wrong_code" });

  const { error: updateError } = await admin.auth.admin.updateUserById(profile.id, { password });
  if (updateError) return reply(500, { error: "server_error" });
  await admin.from("recovery_codes").delete().eq("user_id", profile.id);

  return reply(200, { ok: true });
});
