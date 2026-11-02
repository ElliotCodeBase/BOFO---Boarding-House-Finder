# BOFO Android v2.0.127 - Changelog

## What was wrong
Sign out of an owner account, create a new student account: the app opened the Owner tabs, and Settings offered "Switch to owner", so you could not get to student.

## Cause
The role the screens use (`AuthRouter.role`) was set only at sign-in. It was never cleared on sign out, and never set after choosing Student/Owner on a new account, so the old owner role stayed in memory.

## What I changed
| File | Change |
|---|---|
| `ui/AuthRouter.java` | New `reset()` (role back to student). Called when there is no session or the session is dead (401). |
| `ui/SettingsFragment.java` | Sign out and Delete account call `AuthRouter.reset()` |
| `ui/RoleFragment.java` | After saving the role, reads the real role from the server and sets `AuthRouter.role` / `realRole` before opening the app |
| `app/build.gradle` | 2.0.127, versionCode 327 |
| `CHANGES_ANDROID.md` | New entry |

## How to test
1. Install the build. Sign in as an owner, then Settings > Sign out.
2. Create a new account, pick Student, finish setup: you should land on the Student tabs.
3. Settings should say "Switch to owner" and it should work, and switching back too.

## If something goes wrong
| Problem | Fix |
|---|---|
| Build error in `RoleFragment.java` about types | The `Async.run` there now returns a `Role` (was Boolean). Make sure the success lambda is `saved -> {` and the `Profile` import is present. |
| Still opens Owner tabs for an account that is really student | Fully close the app and reopen (clears memory). If it persists, Settings > Sign out once, then sign in again. |
| Admin account opens as student | Admin role comes from the server at sign-in (`route`), not from this change. Sign out and in again. |
| Undo | `git checkout -- bofo-android/app/src/main/java/com/bofo/finder/ui/AuthRouter.java bofo-android/app/src/main/java/com/bofo/finder/ui/SettingsFragment.java bofo-android/app/src/main/java/com/bofo/finder/ui/RoleFragment.java bofo-android/app/build.gradle CHANGES_ANDROID.md` |

Note: this build also needs the earlier v2.0.125 and v2.0.126 files applied.
