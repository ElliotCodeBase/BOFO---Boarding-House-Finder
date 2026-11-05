# Key rotation checklist and git history purge

**Rotated keys protect you. Cleaning history does not**: the repo is public, so assume anything ever committed was copied. Do the rotation first.

## Rotate (in this order)
1. **Firebase / Google service account** (`fcm-service-account.json`): Google Cloud Console > IAM > Service accounts > the firebase-adminsdk account > Keys > **delete the old key**, create a new one. Put the new JSON only in Supabase: `supabase secrets set FCM_SERVICE_ACCOUNT="$(cat new.json | tr -d '\n')"` (file kept outside the repo, e.g. `C:\Users\user\bofo-secrets\`). Redeploy: `supabase functions deploy send-push --no-verify-jwt`.
2. **PUSH_WEBHOOK_SECRET**: new random string (`openssl rand -hex 32`); `supabase secrets set PUSH_WEBHOOK_SECRET=...`; update the same value in the DB: `update public.push_settings set value='<new>' where key='secret';`.
3. **Supabase keys**: Project Settings > API: roll the **service_role** key (and the JWT secret if the service key was ever outside your PC). The anon key is public, but if you roll the JWT secret you must update `config.properties` and ship a new app build.
4. **Other secrets you set in Supabase** (if they ever lived in a file or chat): `OPENROUTER_API_KEY` (and any old Groq key), `PAYMONGO_SECRET_KEY`, `PAYMONGO_WEBHOOK_SECRET`. Roll each at its provider and `supabase secrets set` again.
5. **Android signing**: if `.jks` or `keystore.properties` were ever committed, treat the upload key as exposed. With Play App Signing enabled you can request an upload-key reset in Play Console. The new admin app gets its own fresh key (Stage 3). Keep keystores outside the repo and backed up.
6. **Dev/test accounts** (S-04), run in the SQL editor:
   - Look first (read-only): `select id, email, created_at from auth.users where email ilike '%@example.com';`
   - Delete: `delete from auth.users where email in ('bofo.dev.admin@example.com','bofo.dev.student@example.com','bofo.dev.owner@example.com');`
   - Dashboard > Authentication > Providers: disable Email/Password if unused.
7. **Admin MFA** (S-03): each admin enrolls TOTP in the app, sign in again, then `select public.set_admin_mfa_required(true);`
8. GitHub: Settings > Code security: turn on **secret scanning**, **push protection**, Dependabot alerts. Settings > Branches: protect `main` (require PR, block force-push). Then rotate any GitHub Actions secret that was ever echoed.

## Find out if anything was committed (run on your PC, in the repo)
```
git log --all --oneline -- secrets/ "*adminsdk*" "*.jks" "*.keystore" google-services.json keystore.properties .env
git log --all -p -S"private_key" --oneline | head
git log --all -p -S"service_role" --oneline | head
```
Empty output = good news for those patterns (still rotate S-01 if you are unsure).

## Purge history (only after rotating)
```
pip install git-filter-repo
git clone --mirror https://github.com/<you>/<repo>.git bofo-mirror.git && cd bofo-mirror.git
git filter-repo --invert-paths --path secrets/ --path-glob "*adminsdk*.json" --path-glob "*.jks" --path-glob "*.keystore" --path keystore.properties --path supabase/.temp/
git push --force --all && git push --force --tags
```
Then: everyone re-clones; ask GitHub Support to purge cached views and PR refs; old forks keep copies (another reason rotation matters). BFG alternative: `bfg --delete-files "*adminsdk*.json"`.

## Right now, in the working repo
```
git rm --cached supabase/.temp/linked-project.json
git rm -r --cached secrets 2>$null     # no-op if it was never tracked
git add .gitignore bofo-android/.gitignore
```
