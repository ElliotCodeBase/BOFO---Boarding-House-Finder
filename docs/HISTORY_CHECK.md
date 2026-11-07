# S-01: search the whole git history for secrets (you run it; I do not have your history)

Run in PowerShell, repo root `C:\Users\user\bofo-finder`. First `git fetch --all`. The commands print **commit + file names only** (`-l`), never the secret itself.

## 1. Files that must never have been committed
```
git log --all --name-only --pretty=format: | Sort-Object -Unique | Select-String -Pattern '\.jks$|\.keystore$|google-services\.json$|local\.properties$|KEYS\.local|config\.local|\.env$|service-account.*\.json$'
```

## 2. Secret patterns inside any commit (names of commits and files only)
```
$pat = 'service_role|sb_secret_|-----BEGIN [A-Z ]*PRIVATE KEY-----|"private_key"|sk_live_|sk_test_|sk-or-v1-|ghp_[A-Za-z0-9]{20,}|AIza[0-9A-Za-z_-]{35}|eyJ[A-Za-z0-9_-]{10,}\.eyJ[A-Za-z0-9_-]{10,}\.|postgres(ql)?://[^ ]*:[^ ]*@|storePassword|keyPassword'
git rev-list --all | ForEach-Object { $c = $_; git grep -I -l -E $pat $c | ForEach-Object { "$c  $_" } } | Sort-Object -Unique | Out-File history-hits.txt
(Get-Content history-hits.txt).Count
```
`history-hits.txt` stays on your PC. **Do not commit it** (add to `.gitignore` or delete it after). Docs and test files that only mention a word like `service_role` are expected hits: open each file at that commit with `git show <commit>:<path>` and look.

## 3. A JWT hit: which key is it?
The Supabase **anon** key and the project URL are public by design (they ship inside the app): no action. A **service_role** key is a full-access secret. Decode the middle part:
```
$p = '<paste only the middle part of the JWT, between the two dots>'; $p = $p.Replace('-','+').Replace('_','/'); while ($p.Length % 4) { $p += '=' }
[Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($p))
```
Look at `"role"`: `anon` = fine, `service_role` = treat as leaked.

## 4. What to do for each kind of hit (ROTATE FIRST, then purge; purging does not un-leak a key)
| Hit | Rotate first | Then |
|-----|--------------|------|
| `service_role` JWT / `sb_secret_` / DB password / `postgres://` | Supabase > Project Settings > API keys (roll the secret key / JWT secret) and reset the DB password; update Edge Function secrets and CI | purge history |
| Firebase service account (`private_key`) | Google Cloud > IAM > Service accounts > delete the key, create a new one, update `FCM_SERVICE_ACCOUNT` secret | purge |
| `AIza...` key | Google Cloud > Credentials: restrict it (Android package + SHA-1) or regenerate | purge optional |
| PayMongo `sk_live_` / `sk_test_`, webhook secret | PayMongo dashboard: roll the key and webhook secret, update Edge Function secrets | purge |
| OpenRouter `sk-or-v1-` | OpenRouter > Keys: delete and create a new one, update `OPENROUTER_API_KEY` | purge |
| GitHub `ghp_` token | GitHub > Settings > Developer settings: revoke | purge |
| `.jks` / `.keystore` / `storePassword` | A leaked signing key cannot be rotated for an app already published with Play App Signing: ask Play support; for sideloaded builds make a new keystore and reinstall | purge |
| `google-services.json` | Not a secret by itself; restrict its API key (row `AIza`) | none |

## 5. Purge (only after rotating; rewrites history, everyone must re-clone)
```
pip install git-filter-repo
git clone --mirror https://github.com/ElliotCodeBase/BOFO---Boarding-House-Finder.git bofo-mirror.git
cd bofo-mirror.git
git filter-repo --path <file-to-remove> --invert-paths      # repeat --path for each file; or: --replace-text replacements.txt
git push --force
```
Then: GitHub support can clear cached views of old commits; delete forks; every clone re-cloned. If no hit needs purging, skip this section. Tell me the NUMBER of hits per row (not the values) and I will help decide.
