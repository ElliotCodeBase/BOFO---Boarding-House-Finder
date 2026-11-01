// Deletes the signed-in user's account: removes their files via the Storage API, then the auth user
// (all rows cascade). Needed because SQL cannot delete from storage.objects.
// Deploy: supabase functions deploy delete-account   (SUPABASE_URL / ANON / SERVICE_ROLE are provided automatically)
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.45.4";

const cors = { "Access-Control-Allow-Origin": "*", "Access-Control-Allow-Headers": "authorization, content-type, apikey, x-client-info, x-bofo-app-version" };
const json = (b: unknown, s = 200) => new Response(JSON.stringify(b), { status: s, headers: { ...cors, "Content-Type": "application/json" } });

// Lists every file under a folder (recursing into sub-folders).
async function listAll(admin: ReturnType<typeof createClient>, bucket: string, prefix: string): Promise<string[]> {
  const out: string[] = [];
  const { data, error } = await admin.storage.from(bucket).list(prefix, { limit: 1000 });
  if (error) throw error;
  for (const e of data ?? []) {
    const path = `${prefix}/${e.name}`;
    if (e.id === null) out.push(...await listAll(admin, bucket, path));   // folder
    else out.push(path);
  }
  return out;
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  try {
    const url = Deno.env.get("SUPABASE_URL")!;
    const userClient = createClient(url, Deno.env.get("SUPABASE_ANON_KEY")!, { global: { headers: { Authorization: req.headers.get("Authorization") ?? "", "x-bofo-app-version": req.headers.get("x-bofo-app-version") ?? "" } } });
    const { data: u } = await userClient.auth.getUser();
    if (!u?.user) return json({ error: "unauthorized" }, 401);
    const uid = u.user.id;

    const admin = createClient(url, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
    for (const bucket of ["id-verification", "bh-photos", "avatars"]) {
      const files = await listAll(admin, bucket, uid);
      for (let i = 0; i < files.length; i += 100) {
        const { error } = await admin.storage.from(bucket).remove(files.slice(i, i + 100));
        if (error) throw error;
      }
    }
    const { error: derr } = await admin.auth.admin.deleteUser(uid);
    if (derr) throw derr;
    return json({ ok: true });
  } catch (e) {
    console.error("delete-account failed", e);
    return json({ error: "Could not delete account." }, 500);
  }
});
