# Revert-to-original-UI progress (bofo-android)
## v2.0.19 - Step 1 done: floating tab bar + mascot button
- Tab bar is now a floating white rounded pill (68 dp, 16 dp side margins, soft shadow, no divider) with a lavender highlight behind the active tab.
- Mascot button floats just above the bar, bottom-right.
- Bar and button are lifted above the system gesture area (insets listener in ShellFragment).
- Owner and admin menus untouched (same BottomNavigationView, same menus).
- Not built or run by Claude. Version bumped to 2.0.19 / 219 (your emulator showed 2.0.18).
## Next: Step 2 (splash + loading animation).
**To continue in any AI:** upload your latest project zip, then paste the whole of `REVERT_PROMPT.md` (in this folder, repo root) and say "Do Step 2".
Note: the bar highlight is Material's own indicator (fades/animates); the exact sliding highlight from the JavaFX dock can come in the final pass.
