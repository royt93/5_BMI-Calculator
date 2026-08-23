# Independent Review — BMI Calculator (Claude)

## 1. Top Bugs / Code-Quality Issues

1. **Goal-weight progress breaks for weight-gain goals** — `ResultAct.kt:269-279` (`updateGoalUI`). `diff = weight - goalWeight`; if `diff <= 0` (goal weight above current, i.e. a bulking/gain goal) it immediately reports 100% "achieved" in healthy-green, even on day one. `BadgeManager.kt:64-66` correctly handles both directions (`abs(currentWeight-goalWeight)<=1.0`), so the badge and the goal-card UI disagree.
2. **Inconsistent BMI category boundaries on the same screen** — `ResultAct.kt:493-500` (`showResult`, healthy = `<24.9`) vs `ResultAct.kt:164-169` (`setupHealthTips`, healthy = `<25.0`) vs `CalculatorUtils.getBMICategory` (healthy = `<24.9`). A BMI of 24.95 shows "Overweight" as the headline but healthy-zone tips underneath.
3. **Hardcoded, unlocalized result strings** — `ResultAct.kt:494,496,498,500` ("You are Under Weight" / "Healthy" / "Overweight" / "Suffering from Obesity") ship in English regardless of the 16 supported locales, unlike everything else in the app.
4. **`getBMICategory` unused/duplicate source of truth** — `CalculatorUtils.kt:19-26` returns English literals nothing calls (grep shows no callers); category logic is re-implemented ad hoc in `ResultAct` and `TrackerBottomSheet` instead, risking drift like #2.
5. **Multi-profile plumbing built, never wired to UI** — `ProfileDao`/`BmiRepository.getAllProfiles/insertProfile/setCurrentProfile` (`BmiRepository.kt:32-54`) have zero callers outside the DAO/repo themselves; only `createDefaultProfile()` (`GalaxyApp.kt`) runs. Every screen defaults to `profileId = 1L`, so the "multi-profile" claim in CLAUDE.md is aspirational, not real.
6. **`SettingsActivity` doesn't expose theme or unit toggles** despite full DataStore support — see §2 below (also a correctness risk: dead `THEME_MODE`/`UNIT_SYSTEM` state can silently diverge from what's rendered).
7. **Reward-ad touchpoint documented but absent** — CLAUDE.md says "Reward Ad on ResultAct 'Get Detailed Plan' button", but `ResultAct.kt:152-153` has it commented out (`// setupRewardButton()`, "deferred until SDK adds showRewardedAd support"). Stale doc vs. code.
8. **Minor:** `showGoalDialog` recomputes `currentBmi` locally (`ResultAct.kt:292`) instead of reusing the already-computed `result` field — harmless today but a second formula instance to keep in sync if the BMI formula ever changes.

## 2. Half-Finished / Dead Features

- **Multi-profile switching** — full Room schema + DAO + repo methods exist (`ProfileDao.kt`, `BmiRepository.kt:32-54`), no Activity/Fragment calls them. Biggest orphaned subsystem in the codebase.
- **Unit system (metric/imperial)** — `PreferencesManager.kt:18,29-31,55-59` (`UNIT_SYSTEM` key, `unitSystem` flow, `setUnitSystem`) has no reader and no UI; app is metric-only everywhere (kg/cm hardcoded, e.g. `TrackerBottomSheet.kt:90`).
- **Theme mode preference** — `PreferencesManager.kt:17,25-27,49-53` (`THEME_MODE`) same story: never read, no toggle in `SettingsActivity.kt`; app just follows system dark mode via `layout-night`.
- **Reward-ad "Get Detailed Plan" button** — commented out, `ResultAct.kt:152-153`.
- **`AdMobManager.kt.bak`** — dead hand-rolled ad manager left in the tree as a reference file (already flagged as "do not reintroduce" in CLAUDE.md, but it's still unusual to ship a `.bak` inside `src/main`).

## 3. Top 3 Recommended New Features

1. **Manual weight-log entry (independent of full BMI recalculation)** — Pro: `TrackerBottomSheet` chart already exists and is starved for data since new points only appear when the user runs the whole wizard; a lightweight "+ log weight" FAB would 3-5x chart usefulness. Con: needs a new quick-entry `BmiRecord` path that reuses last-known height/age instead of asking for both every time.
2. **Reminder notifications for weigh-in streaks** — Pro: `StreakManager`/`BadgeManager` already reward daily use but nothing prompts the user to open the app; `POST_NOTIFICATIONS` permission is already declared in the manifest, unused. Con: needs WorkManager scheduling + per-locale notification copy.
3. **Wire up the unit-system toggle** — Pro: cheapest possible win (infra 100% built, just needs a Settings row + conversion at input/display boundaries via existing `CalculatorUtils.kgToLbs/lbsToKg/cmToInches`); opens the app to US/UK/imperial users currently excluded. Con: touches every screen showing weight/height, so testing surface is wide even though the plumbing is trivial.

## 4. Top 3 Flagship Differentiator Ideas

1. **"Body trend" composite dashboard** — one screen correlating weight, BMI, BMR/TDEE and goal progress over time (not tab-separated like `TrackerBottomSheet` today), with milestone callouts pulled from `BadgeManager`. *Cheap*: pure Room + MPAndroidChart, no new infra.
2. **Local, on-device "what-if" projection** ("at this rate, you'll reach goal weight by ~X date") computed from the existing `BmiRecord` history's linear trend. *Cheap*: math over data already stored, no backend, no ML service.
3. **Family/shared-device profile mode with quick-switch avatars** — actually finish the already-built `Profile` table (see §1.5) into a first-class feature (avatar picker, per-profile history/streaks/badges). *Cheap-ish*: schema exists; work is UI + wiring `getAllProfiles`/`setCurrentProfile`, no server needed. (A synced/cloud version across devices would require new infra — explicitly not this.)

## 5. What I Would Explicitly NOT Build

**Cloud sync / account system.** The entire architecture is local-only (Room + DataStore, no backend, no auth). Adding sync means standing up new infrastructure (server, auth, conflict resolution, privacy policy rewrite) for a BMI calculator whose competitive edge is being fast, private, and offline — the opposite of what a "hundreds of generic BMI calculators" market needs. Finish the local features (profiles, unit toggle, trend dashboard) first; sync only pays off after there's a paying/retained user base large enough to justify the ongoing infra cost.
