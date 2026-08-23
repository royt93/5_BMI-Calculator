# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Native Android BMI Calculator app, single Gradle module (`:app`), written in Kotlin. Package `com.samsunggalaxy` / `applicationId com.samsunggalaxy.bmicalculator`. Uses View Binding + Data Binding, Room, DataStore, MPAndroidChart, and the JitPack-hosted `AdmobApplovinWrapper` SDK for ads + VIP.

## Build & run

Toolchain pinned to JDK 17 (`java.toolchain.languageVersion = 17`), Kotlin 2.1.21, AGP 8.9.2, `compileSdk` 36 / `minSdk` 24. Gradle config cache is enabled (`org.gradle.unsafe.configuration-cache=true`).

Two `productFlavors` × two `buildTypes`:

- `dev` / `production` (flavors — only difference is `app_name` resource: "BMI Calculator 2026 DEV" vs "BMI Calculator 2026")
- `debug` / `release` (release minifies + shrinks resources, signs from `keystore.properties`)

Common Gradle tasks (use the wrapper):

```bash
./gradlew assembleDevDebug              # day-to-day dev APK
./gradlew assembleProductionRelease     # signed release APK (needs keystore.properties + keystores.jks)
./gradlew compileDevDebugKotlin         # fast type-check (canonical "did it build?" check used in docs)
./gradlew installDevDebug               # install on connected device/emulator
./gradlew clean
./gradlew lintDevDebug                  # Android Lint (rule "NullSafeMutableLiveData" is disabled — see app/build.gradle.kts:104)
./gradlew testDevDebugUnitTest          # JVM unit tests (src/test)
./gradlew connectedDevDebugAndroidTest  # instrumented tests (src/androidTest), needs device
```

Run a single unit test: `./gradlew testDevDebugUnitTest --tests "com.samsunggalaxy.SomeTest.someMethod"`.

### Signing & secrets

Release signing reads from `keystore.properties` at repo root (key/value pairs: `storeFile`, `storePassword`, `keyAlias`, `keyPassword`). Both `keystore.properties` and `app/keystores.jks` are sensitive — **do not commit changes to them** and do not echo their contents in logs/PRs. The signing block is wrapped in an existence check so non-release builds work without the file.

`local.properties` (SDK path) is git-ignored as usual.

## High-level architecture

### Activity flow

`SplashAct` (LAUNCHER) → first-run shows `FirstRunLanguageSheet` to pick language → `MainAct` → user fills weight/height/age/gender → `ResultAct`. Side screens from `MainAct` menu: `HistoryActivity` (unified **Weight Dashboard**, see below), `CalculatorsActivity` (hub for `BmrCalculatorActivity`, `TdeeCalculatorActivity`, `IdealWeightCalculatorActivity`, `BodyFatCalculatorActivity`), `SettingsActivity`. All non-launcher activities are `exported=false`.

`HistoryActivity` (EPIC-07) merges what used to be two separate screens — a BMI-only chart here and a weight/height-only chart in `TrackerBottomSheet` — into one dashboard: a `TabLayout` series switcher (BMI / Weight / Height), a goal row (single source of truth for goal weight, moved from `ResultAct`'s old standalone goal card), a linear-regression ETA estimate (`CalculatorUtils.estimateGoalEtaDays`), and a quick-log FAB that inserts a weight-only `BmiRecord` reusing the latest known height/age/gender/profile without the full `MainAct` wizard. `TrackerBottomSheet` still exists in the codebase but `HistoryActivity` is now the canonical place to view trends — don't add new chart logic to `TrackerBottomSheet`. Goal-line math: convert `goalWeight` → BMI using the **latest** record's height (not the first record's — that was a bug, see `doc/task/todo/EPIC-00-critical-bugs.md` T-series), and for the Weight series draw the goal line directly at `goalWeight` (no conversion needed). `ResultAct`'s reward-ad "Get Detailed Plan" button is still commented-out dead code (`doc/task/todo/EPIC-03-reward-ad-detailed-plan.md`) — deferred, not yet wired to unlock anything in the dashboard.

Every Activity extends `BaseActivity` (sets locale via `LocaleHelper.onAttach`, forces `fontScale=1.0`, enables adaptive refresh rate once in `onCreate`).

### Application setup (`GalaxyApp.kt`)

Order in `onCreate`:

1. `DynamicColors.applyToActivitiesIfAvailable(this)` (Material You).
2. Build `AdSdkConfig` (banner/interstitial/app-open/reward IDs for both networks, `vipKeySecret = AdKeys.VIP_SECRET`, `safety = AdSafetyLimits.TEST` in debug / `.UTILITY` in release) and register `AdManager.errorReporter` (forwards SDK exceptions to logcat, gated `BuildConfig.DEBUG`).
3. `AdManager.setConfig(adConfig)` then `AdManager.initialize(this) { success, gaid -> ... }` — single call now (no separate AppLovin handshake step or early/late init split since the 1.1.3 migration). SDK 1.1.3 has a built-in 1-day first-install VIP grace period computed from `installBeginMs`; app code must **not** call `activateVipByKey` to grant that first day itself or it will stomp the SDK's own value.
4. `initializeDefaultProfile()` — on an `applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)`, creates a default `Profile` row if none exists. Required because the rest of the app saves records with `profileId` defaulted to the current/first profile (see `doc/BUGS_FIXED.md` #1 — Room auto-increment starts at 1, never 0).

`attachBaseContext` wraps the context with `LocaleHelper.onAttach(base)` so the app launches in the user-selected language.

### Ad layer

Ads are delegated entirely to the `com.roy.sdkadbmob.AdManager` singleton from `com.github.royt93:AdmobApplovinWrapper:1.1.3` (JitPack; class/package unchanged across the 1.1.1 → 1.1.3 bump). The wrapper handles AdMob + AppLovin MAX mediation, banner/interstitial/app-open/reward formats, GAID lookup, its own VIP-by-key state, error cooldown, and the "7-layer AdSafety" throttling.

`BuildConfig.IS_ENABLE_ADMOB` (defined in `app/build.gradle.kts`) toggles provider: **`false` ⇒ AppLovin MAX is used** in both debug and release. AdMob unit IDs are still passed in as fallbacks. SDK keys & unit IDs are injected as `buildConfigField` strings — see the `defaultConfig`/`buildTypes` blocks for the canonical values, and `doc/AD.MD` for the migration history.

Touchpoints: App Open on `SplashAct`, persistent Banner on `MainAct` and `ResultAct`, Interstitial on `ResultAct` back/delete, Reward Ad on `ResultAct` "Get Detailed Plan" button. Banner is destroyed/hidden whenever VIP is active — see `syncBannerWithVipState()` in `MainAct.kt` and `ResultAct.kt`.

The legacy hand-rolled `app/src/main/java/com/samsunggalaxy/sdkadbmob/AdMobManager.kt.bak` is kept as a reference only — do not re-introduce it. `UIUtils.kt` (edge-to-edge helpers) in the same package is still in use.

### VIP Membership

`feature/vip/` package: `VipActivity` (thin `BaseActivity` host, layout `a_vip`) hosts the `FVipManagement` fragment, which provides redeem-key input, a "watch rewarded ad → 3 days VIP" flow, revoke, and countdown/progress UI. Launched from `MainAct`'s menu/badge via a plain `Intent`.

VIP truth lives in the SDK, not app persistence: `AdManager.isVipByKeyActive()` / `AdManager.getVipByKeyExpiry()` / `AdManager.activateVipByKey(context, AdKeys.VIP_SECRET, days)` / `AdManager.clearVipByKey()`. `VipPrefs` (SharedPreferences file `vip_screen_prefs`) only supplements this with a `grantedAt` timestamp and a "redeemed at least once" flag, since the SDK doesn't expose a getter for when VIP was granted. Redeem keys are whitelisted in `VipKeys.kt` (Base64-obfuscated) and exposed app-wide via `AdKeys.VIP_SECRET` (`common/const/AdKeys.kt`, which also exposes `AdKeys.PRIVACY_POLICY_URL` from `BuildConfig.PRIVACY_POLICY_URL` for the VIP screen footer / consent dialog).

### Persistence

Three independent persistence mechanisms — pick the right one when adding state:

- **Room** (`data/AppDatabase.kt`, DB name `bmi_database`, version 2) — structured app data: `BmiRecord` (history) and `Profile` (multi-profile support, `goalWeight` column added in `MIGRATION_1_2`). All DB work goes through `BmiRepository`. When adding columns, write a `Migration` rather than bumping `fallbackToDestructiveMigration` — user health data must not be wiped.
- **DataStore Preferences** (`utils/PreferencesManager.kt`, file `bmi_settings`) — typed app settings: `theme_mode`, `unit_system`, `language`, `current_profile_id`, `activity_level`, `is_language_selected`.
- **SharedPreferences** — gamification + locale only:
  - `streak_prefs` (`StreakManager`) — current/best streak + last check date.
  - `badge_prefs` (`BadgeManager`) — per-badge earned flag + unlock timestamp.
  - `app_preferences` — `LocaleHelper` selected-language key and the in-app review timestamp (`BaseActivity.rateAppInApp`).

### i18n

Strings live in `app/src/main/res/values-<lang>/strings.xml`. Currently shipped locales: `ar de es fr hi id it ja ko nl pt ru th tr vi zh` plus default English. `LocaleHelper.onAttach` is called from both `GalaxyApp.attachBaseContext` and `BaseActivity.attachBaseContext`, so any new Activity that extends `BaseActivity` is automatically localized. First-run language is gathered by `FirstRunLanguageSheet` and persisted via `PreferencesManager.markLanguageSelected()`.

When adding user-visible strings: add the key to **every** locale's `strings.xml` (the gamification features in `doc/TODO.md` are the template).

### Theming

Dark mode is layout-driven: a `res/layout-night/` variant exists for several layouts and **must** be kept in sync with the `res/layout/` counterpart. Drawables use `res/drawable-night/` similarly. Colors come from `@color/textColor`, `@color/textColorAdditional`, and glassmorphism backgrounds (`bg_glass_*`).

## Conventions (enforced across the codebase)

These come from `doc/TODO.md`, `doc/memory_leak.md`, and `doc/BUGS_FIXED.md` — past incidents have hardened them:

- **No `lateinit`** — use nullable types with `?.` and `?: return`. The gamification rewrite specifically lists this; view binding holders also follow this pattern.
- **Coroutines must have a lifecycle owner.** Use `lifecycleScope` / `viewModelScope` inside Activities/Fragments. Only the `applicationScope` in `GalaxyApp` is allowed for app-wide background work. Do not write `CoroutineScope(...)launch { }` inline in Activities — it leaks (see `ML-02`, `ML-03`).
- **Handlers/animations must be cancelled in `onDestroy()`** with `isDestroyed`/`isFinishing` guards before any post-animation callbacks. `SplashAct` and `ResultAct` follow this pattern.
- **Wrap `Log.d`/`Log.w` in `if (BuildConfig.DEBUG)`** — `doc/Security.md` H-01 flags raw logging of health data, weight, height, BMI, and GAID as a GDPR/PII issue. Existing code uses the `"roy93~"` tag; new logs should follow suit and stay gated.
- **Layout duplication** — every layout under `res/layout/` that has a `res/layout-night/` twin must be edited in both places.
- **BMI/health math lives in `utils/CalculatorUtils.kt`** — when fixing a formula, also check it against `doc/BUGS_FIXED.md` items #5 (`log10(neg)` guard) and #6 (negative ideal weight floor) to avoid regressing.
- `BmiRepository.setCurrentProfileAtomic()` is a `@Transaction` method — never replace it with separate "clear all current + set one current" calls; the non-atomic version had a race condition.

## Useful docs

When a task touches one of these areas, read the matching doc first — they have the real reasoning the code doesn't repeat:

- `doc/AD.MD` — full ad-integration plan and the AdMob → AdmobWrapper migration steps.
- `doc/FEATURES_ADDED.md` — overview of the Room/DataStore additions and the Calculator hub.
- `doc/TODO.md` — gamification feature spec (Streak / Health Tips / Badges) including string keys per locale.
- `doc/Security.md` — open security findings (manifest secrets, backup rules, logging).
- `doc/BUGS_FIXED.md`, `doc/memory_leak.md` — catalog of past defects and the patterns introduced to avoid them.
