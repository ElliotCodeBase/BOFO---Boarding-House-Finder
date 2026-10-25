# BOFO Finder - rules for Claude (READ THIS FIRST, EVERY SESSION)

Read this file before doing anything, then only the top entry of `HANDOFF.md`. Do not read the rest of the repo unless the task needs it.

## 1. Deliver changes as a CHANGES-ONLY zip (saves credit)
- For every update, change or fix, NEVER send the whole project. Send ONE zip that contains ONLY the files you added or changed, in their real folders (e.g. `src/main/java/com/bofo/finder/ui/DirectionsCard.java`), so the user can unzip it over the project folder and overwrite.
- Name it `BOFO-vX.Y.Z-changes.zip` (the new version). Put a `CHANGED_FILES.txt` inside listing each file as `changed`, `new` or `DELETE` (a zip cannot delete: list files to delete by hand there and in the chat).
- Include the files the version bump touches (`config.properties`, `pom.xml`, `CHANGES.md`, `HANDOFF.md`), and nothing else. Never include the project zip, `target/`, `.git/`, or unchanged files.
- Reply in a few lines: what was fixed, what zip to unzip, the PR title, the Windows commands (see HANDOFF.md for the repo path and push commands).

## 2. Spend as few tokens as possible
- Find first, read second: `grep -n` for the symbol, then read only that line range. Do not open whole big files (`MapPane.java`, `BhDetailView.java`) to change ten lines.
- Change files with small targeted edits, not full rewrites. Do not reformat code you are not changing.
- Fix the cause once; batch related fixes into ONE version/zip instead of many tiny ones.
- Do not re-explain the repo, do not paste code in the chat that is already in the zip, no long recaps.
- Keep `HANDOFF.md` short: the newest 3 entries only. Move older ones to `docs/HANDOFF_ARCHIVE.md`.
- No Maven/JavaFX in Claude's sandbox: do not try to build. Parse-check with `javac` only, and say that it was not run on screen.
- Ask the user only when a wrong guess would be expensive; otherwise pick the sensible default and say it in one line.

## 3. Version and handoff (every change set / pull request)
1. `src/main/resources/com/bofo/finder/config.properties` -> `app.version` (shown in Settings > About)
2. `pom.xml` -> `<version>`, `<versionName>`, and `<versionCode>` +1
3. Add a short entry at the top of `HANDOFF.md` and `CHANGES.md` naming the new version.
4. Put the version in the pull request title (e.g. `vX.Y.Z - short summary`).
5. Search the repo for the old version string and replace it everywhere (history entries excepted).
Patch (x.y.Z) for fixes, minor (x.Y.0) for new features.
- Before the token budget runs out, write/update `HANDOFF.md`.

## 4. Safety
- Never commit `config.local.properties` or `KEYS.local.md`.
- Supabase: the user allows Claude to change the Supabase project when a feature needs it. Look at the existing tables first and say what was changed (also add a migration file under `supabase/migrations/`).
