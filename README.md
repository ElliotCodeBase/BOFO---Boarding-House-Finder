# BOFO Finder (Boarding House Finder)

Native Android app (`bofo-android/`, Java + Gradle) with a Supabase backend (Auth, Postgres, Storage, Edge Functions in `supabase/`).

## Setup
1. Supabase: create a project, run `supabase/migrations/*.sql` in order.
2. Auth > Providers > Google: enable it (`docs/GOOGLE_LOGIN_SETUP.md`); add redirect URL `bofo://auth-callback`. Email login: `docs/EMAIL_LOGIN_SETUP.md`.
3. Edit `bofo-android/config.properties` (URL + anon key only; never the service-role key).
4. Deploy functions (`supabase functions deploy bofo-chat bofo-announce ...`), set secrets (`LLM_API_KEY`, `LLM_MODEL`). Push: `docs/PUSH_SETUP.md`.
5. Open `bofo-android/` in Android Studio, or see `bofo-android/docs/RELEASE.md` for release builds.

Rules for AI assistants: `CLAUDE.md`. Latest status: `HANDOFF_ANDROID.md`. Security: `docs/SECURITY.md`.
