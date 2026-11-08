# Handoff (Android) - v2.12.9: Admin UX pass 2 (More, Home shortcuts, loading / retry, ripple, back button). Next if wanted: Reports / Appeals cards, buttons, small-phone (320 dp) check on a device.
- New in `Ui`: `ripple`, `icon`, `loading`, `fail`. `MainActivity.goTab(int)`. `MoreScreen` rebuilt (group / row helpers). Version shows at the bottom of More (`BuildConfig.VERSION_NAME`).
- v2.12.8 files had been unzipped into `bofo-android/app-admin` etc.; they are now in the real `app-admin/` (delete the stray copies, see CHANGED_FILES.txt).

# Handoff (Android) - v2.12.8: Admin UI polish pass 1 (Ui.chip, Ui.empty, nav icons, header).

# Handoff (Android) - v2.12.7: build fix for v2.12.6. Lesson: this repo is only checked with `javac_pass.sh` (no Android SDK here), so a signature mismatch against Android classes is only found by Gradle on your PC.
