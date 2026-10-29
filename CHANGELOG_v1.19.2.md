# v1.19.2 - Cancelling Google sign-in no longer breaks the next try

## What changed
- `service/AuthService.java` only.
- **Cancel** (or closing the browser tab) used to close the sign-in listener on port 54321. Windows frees that port a moment later, so the next "Continue with Google" failed with `Sign-in failed: Address already in use: bind`.
- Now the listener stays open after Cancel and is reused on the next click. If a port was closed just before, the app retries the bind for up to ~1.2 s before giving up.
- It closes itself after a successful sign-in, as before. No CSS, Supabase or config change.

## How to open it
1. Close the running app window (do not use Ctrl+C), then from `C:\Users\user\bofo-finder`:
   `Expand-Archive "$HOME\Downloads\BOFO-v1.19.2-changes.zip" -DestinationPath . -Force`
2. `mvn clean javafx:run`

## Not run on screen
Only edited, not compiled or run here. Test: click Continue with Google, press Cancel, click it again - no red error.

## Fix guide
- Still "Port 54321 is busy": an old Java process holds it. Run
  `Get-NetTCPConnection -LocalPort 54321 | % { Stop-Process -Id $_.OwningProcess -Force }`
  then start the app again.
- Browser page never loads after picking your account: use `http://127.0.0.1:54321/callback` in `config.properties` and add it to Supabase > Authentication > URL Configuration > Redirect URLs.
