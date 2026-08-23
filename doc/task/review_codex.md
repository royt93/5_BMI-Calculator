# 1. Top bugs/code-quality issues

1. **High — weight-gain goals are immediately “achieved.”** Any current weight below the target makes `diff <= 0`, so the UI reports 100% completion; progress only models weight loss (`ResultAct.kt:269-277`).
2. **High — streaks remain visually active after missed days.** `getStreakData()` returns persisted `current` without comparing `lastDate` with today/yesterday; Main displays that stale value (`StreakManager.kt:39-45`, `MainAct.kt:257-267`).
3. **Medium — “Other” gender silently uses female formulas.** Main offers F/O/M but serializes every non-M choice as `1`; Result treats `1` as female for BMR/ideal weight (`MainAct.kt:299-312`, `MainAct.kt:85-89`, `ResultAct.kt:505-509`).
4. **Medium — trend coloring gives unsafe health meaning.** Every BMI decrease is green and increase red, even when an underweight user worsens or an underweight user gains toward healthy (`HistoryActivity.kt:264-275`). Tracker makes the same assumption for weight regardless of goal direction (`TrackerBottomSheet.kt:101-117`).
5. **Medium — sharing permanently deposits a JPEG in Pictures on every tap.** `saveBitmap` inserts into MediaStore and sharing never cleans it up, gradually cluttering the gallery (`FileExt.kt:13-44`, `ResultAct.kt:350-374`).
6. **Medium — invalid Navy measurements are presented as a valid 0.0%.** The calculator returns zero when waist ≤ neck, then the activity displays it as a result rather than an error (`CalculatorUtils.kt:96-102`, `BodyFatCalculatorActivity.kt:48-54`).
7. **Low — result copy bypasses localization.** BMI status, errors, BMI/history labels and units are hard-coded English, despite 17 locale resources (`ResultAct.kt:488-501`, `ResultAct.kt:359-376`, `HistoryActivity.kt:247-250`).
8. **Low — rapid repeated Calculate taps can save duplicates.** Each Result activity auto-inserts during setup with no uniqueness/debounce guard (`ResultAct.kt:134-137`, `ResultAct.kt:522-550`).

# 2. Top "half-finished or dead" features

- **Multi-profile support:** Room entity/DAO/repository CRUD exists, but no profile-management or switching UI calls it (`Profile.kt:6-14`, `BmiRepository.kt:31-59`).
- **Settings scaffolding:** theme, units, current-profile and activity-level DataStore keys/readers/writers have no callers; Settings exposes only language (`PreferencesManager.kt:16-77`, `SettingsActivity.kt:28-47`).
- **Rewarded “detailed health plan”:** result wiring is explicitly commented out while localized reward-plan copy remains (`ResultAct.kt:152-153`, `res/values/strings.xml:532`).
- **Body-fat history:** `bodyFatPercentage` is persisted but BMI saves always write `null`, and the standalone body-fat calculator never saves (`BmiRecord.kt:20`, `ResultAct.kt:534-546`, `BodyFatCalculatorActivity.kt:41-58`).

# 3. Top 3 recommended NEW features to build next

1. **Goal-aware weight log + chart:** Pro — extends the existing Room/chart/goal foundation into a repeat-use product. Con — requires separating weigh-ins from automatic BMI-result saves.
2. **Reminder-driven check-ins:** Pro — local notifications can turn streaks and goals into retention. Con — notification permission/timing UX can annoy users.
3. **Metric/imperial support:** Pro — broadens international usability and activates existing settings scaffolding. Con — conversions must be consistent across every calculator, chart and record.

# 4. Top 3 "flagship/exclusive" differentiator ideas

1. **Goal-aware trend coach (local-only):** explain whether the rolling trend is toward the user’s target, estimate ETA, and flag plateaus—without treating all loss as good.
2. **Private health timeline (local-only):** unify weight, BMI, body fat, waist and notes into one filterable chart with offline export/import.
3. **Adaptive weekly plan (new infrastructure for truly personalized AI; cheap rule-based local MVP):** generate calorie range, check-in cadence and next-week adjustments from TDEE plus observed trend.

# 5. One thing I’d explicitly NOT build

**Social feed/challenges.** Moderation, identity, privacy and backend costs overwhelm the ROI for a small local-first calculator; first win durable personal tracking and coaching.
