# BOFO Finder - rules for Claude

- **Every pull request / change set MUST bump the app version.** Update all of these together:
  1. `src/main/resources/com/bofo/finder/config.properties` -> `app.version` (this is what Settings > About shows)
  2. `pom.xml` -> project `<version>`, `<versionName>`, and increment `<versionCode>` by 1
  3. Add a short entry to the top of `HANDOFF.md` and `CHANGES.md` naming the new version.
  4. Put the new version in the pull request title (e.g. `vX.Y.Z - short summary`).
  5. Search the repo for the old version string and replace it everywhere it appears.
  Patch (x.y.Z) for fixes, minor (x.Y.0) for new features.
- Before the token budget runs out, write/update `HANDOFF.md`.
- Never commit `config.local.properties` or `KEYS.local.md`.
