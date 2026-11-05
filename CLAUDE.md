# BOFO Finder (Android) - rules for Claude (READ FIRST, EVERY SESSION)

Native Android app in `bofo-android/` (Java 17, Gradle Groovy, one Activity + Fragments, XML + ViewBinding, Material 3, no Kotlin in app code). Backend: Supabase (`supabase/`). `applicationId com.bofo.finder`, deep link `bofo://auth-callback`.
Read this file, then only the top entry of `HANDOFF_ANDROID.md`. Do not read the rest of the repo unless the task needs it. Older history was removed from the repo; use git history if needed.

## 1. Deliver changes as a CHANGES-ONLY zip
- Never send the whole project. Send ONE zip `BOFO-android-vX.Y.Z-changes.zip` with only added/changed files in their real folders, plus `CHANGED_FILES.txt` (`changed` / `new` / `DELETE`; a zip cannot delete, so list deletions in the chat too).
- Include the version-bump files (`bofo-android/app/build.gradle`, `CHANGES_ANDROID.md`, `HANDOFF_ANDROID.md`) and nothing else. Never include `build/`, `.git/` or unchanged files.
- Reply in a few lines: what was fixed, which zip, the PR title, the commands below.

## 2. Spend as few tokens as possible
- `grep -n` first, then read only the needed line range. Never open whole big files to change a few lines.
- Small targeted edits, no rewrites or reformatting. Batch related fixes into ONE version/zip.
- No long recaps; do not paste code that is already in the zip.
- Keep `HANDOFF_ANDROID.md` and `CHANGES_ANDROID.md` to the newest 3 entries; delete older ones (git history keeps them).
- No Android SDK in the sandbox: never claim it builds or runs. Say it was not run.
- Ask the user only when a wrong guess is expensive; otherwise pick a sensible default and say it in one line.

## 3. Version rule
- The version line is **2.4.N+** (see `versionName` / `versionCode` in `bofo-android/app/build.gradle`; do not trust numbers in old docs or chat). Bump both by +1 (patch for fixes, minor for features) on every change set.
- Version shown in Settings = `BuildConfig.VERSION_NAME`. Put it in the PR title and at the top of `CHANGES_ANDROID.md` and `HANDOFF_ANDROID.md`. Replace the old version string elsewhere (history entries excepted).
- Before tokens run out, update `HANDOFF_ANDROID.md` and `BOFO-AI-SMARTER-STATUS.md`.

## 4. Where the project lives
- Repo root on the user's PC: `C:\Users\user\bofo-finder` (open the `bofo-android` subfolder in Android Studio). Unzip INTO the repo root, overwriting. End replies with (PowerShell):
  `git checkout main; git pull; Expand-Archive "$HOME\Downloads\<zip>" "C:\Users\user\bofo-finder" -Force`
  `cd C:\Users\user\bofo-finder; git checkout -b <branch>; git add -A; git commit -m "<PR title>"; git push -u origin <branch>; gh pr merge`

## 5. Safety
- Never commit `local.properties`, `config.local.properties`, `KEYS.local.md`, keystores or any service_role key.
- Supabase changes only with the user's current OK in this chat. Look at existing tables first, say what changed, and add a migration under `supabase/migrations/`.
- AI access to user data (see `docs/SECURITY.md`): never read or print `auth.users`, `public.users`, chats, ID verifications, agreements, favorites or AI chat logs; if a task seems to need them, stop and ask. Never attribute data to a real person's account; demo data uses `@example.com` test accounts. Prefer role `bofo_ai_dev` (migration 016); use the owner-level connector only for a schema change asked for in this chat. Say in one line what will change before touching the live DB and list what changed after. Never `select *` on user tables. Never request or paste the postgres password or service_role key.
