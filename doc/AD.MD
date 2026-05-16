# 📊 KẾ HOẠCH TÍCH HỢP QUẢNG CÁO — BMI Calculator 2026

> **SDK đích:** `com.github.royt93:AdmobApplovinWrapper:1.1.3` | **Provider runtime:** AppLovin MAX (`IS_ENABLE_ADMOB=false`) | **Cập nhật:** 2026-05-16

---

## 0. BỐI CẢNH MIGRATE (1.1.1 → 1.1.3)

| Field | Giá trị |
|---|---|
| Project cwd | `/Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260323_BMI-Calculator` |
| Lib version cũ | `com.github.royt93:AdmobWrapper:1.1.1` |
| Lib version mới | `com.github.royt93:AdmobApplovinWrapper:1.1.3` |
| Build variant debug bắt buộc xanh | `assembleDevDebug` |
| Build variant release bắt buộc xanh | `assembleProductionRelease` |
| Provider chính | AppLovin MAX |
| Provider fallback | AdMob (chỉ active khi `BuildConfig.IS_ENABLE_ADMOB=true`) |
| AdMob Rewarded ID (debug + release) | `ca-app-pub-3940256099942544/5224354917` — **TEST ID**, TODO cấp ID thật khi user có |
| Privacy Policy URL | `https://loitp.notion.site/Term-Privacy-Policy-Disclaimer-319b1cd8783942fa8923d2a3c9bce60f` |
| VIP key 30 ngày | hardcoded Base64 trong `VipKeys.kt` |
| VIP key 3 ngày | hardcoded Base64 trong `VipKeys.kt` |

---

## 1. TOUCHPOINT TABLE

| Slot | File hiện tại | Trigger | API mới | API cũ thay thế |
|---|---|---|---|---|
| Banner | `MainAct.kt:278`, `ResultAct.kt:131` | onCreate / setupViews | `AdManager.loadBanner(autoManageLifecycle=true)` | `AdManager.loadBanner` + manual `bannerResume/Pause/Destroy` |
| Interstitial | `ResultAct.kt:365` | Click delete/back | `AdManager.showInterstitial` (giữ nguyên) | (chưa đổi) |
| App Open | `SplashAct.kt:84` + `ProcessLifecycle` foreground resume | `awaitSplashComplete` sau consent | `AdManager.initSplashScreen` |
| Rewarded | `FVipManagement.kt` (sẽ tạo) — VIP screen, nút "Watch ad → 3 days VIP" | User click | `AdManager.loadRewarded` + `showRewarded`, fallback `showInterstitial` | (chưa có) |
| Consent (UMP) | `SplashAct.kt` `onCreate` | App launch | `AdManager.requestConsentInfoUpdate` | (chưa có) |
| VIP screen | `feature/vip/FVipManagement.kt` (sẽ tạo) | User mở từ MainAct toolbar | full UI 13 components + 5 anims | (chưa có) |

---

## 2. KẾ HOẠCH 6 BƯỚC

### Step A — Bump lib + BuildConfig (build.gradle.kts)

- Đổi dependency: `com.github.royt93:AdmobWrapper:1.1.1` → `com.github.royt93:AdmobApplovinWrapper:1.1.3`.
- Thêm `buildConfigField` cho `ADMOB_REWARDED_ID` (cả debug+release, đều test ID tạm), `APPLOVIN_REWARDED_ID` (đã có với tên cũ `APPLOVIN_REWARD_ID` — rename).
- Thêm `buildConfigField` `PRIVACY_POLICY_URL` (String).
- Thêm `buildConfigField` cho 2 Base64 VIP secret (`VIP_30D_B64`, `VIP_3D_B64`, `VIP_SECRET_B64`) — alternative: hardcode trong `VipKeys.kt`. **Chốt: hardcode trong `VipKeys.kt`** vì rule template 10.9, không qua BuildConfig.

### Step B — Refactor `GalaxyApp.onCreate`

- Bỏ `AppLovinSdk.getInstance(this).mediationProvider = "max"` + `applovinSdk.initializeSdk { ... }` (lib tự lo qua `initialize`).
- Bỏ `AdManager.earlyInit(this)` (gộp trong `initialize`).
- Thêm field `applovinSdkKey = BuildConfig.APPLOVIN_SDK_KEY` + `admobRewardedId` + `applovinRewardedId` + `vipKeySecret = VipKeys.VIP_SECRET` + `safety = if (BuildConfig.DEBUG) AdSafetyLimits.TEST else AdSafetyLimits.UTILITY` vào `AdSdkConfig`.
- Thay 4-step init bằng `AdManager.setConfig(adConfig)` + `AdManager.initialize(this) { success, gaid -> ... }`.
- Thêm `AdManager.errorReporter` để forward exception vào `Log.w("roy93~Err", ...)`.

### Step C — `SplashAct` wire consent + awaitSplashComplete

- Trong `onCreate`, **trước** khi gọi `AdManager.initSplashScreen`, gọi `AdManager.requestConsentInfoUpdate(this) { canRequestAds -> ... }`.
- Sau callback consent → giữ logic cũ (call `initSplashScreen` rồi `checkFirstRunAndProceed`). Cân nhắc dùng `awaitSplashComplete` (suspend) thay `initSplashScreen` — **giữ `initSplashScreen` để minimal change** (cả 2 API đều supported).
- Nếu `canRequestAds == false` → skip ads, vào main ngay.

#### Consent test scenarios (per spec Step 8 — document only, không cần chạy thực)

| Scenario | Trigger | Expected behavior |
|---|---|---|
| Non-EEA region (VN/US/...) | Mở app lần đầu ở region không thuộc EEA/UK/CH | `requestConsentInfoUpdate` callback `canRequestAds=true` ngay, không hiện dialog. `initSplashScreen` chạy bình thường, App Open load + show. |
| EEA + user **Accept** | Mở app ở EEA region, dialog hiện, user tap "Consent" | Dialog dismiss → `canRequestAds=true` → personalized ad load bình thường. |
| EEA + user **Deny** | Mở app ở EEA region, user tap "Manage options" → uncheck → save | `canRequestAds=false` → `SplashAct` skip `initSplashScreen` → `checkFirstRunAndProceed` ngay → vào main không show App Open. Banner + Interstitial sau đó vẫn được lib load với non-personalized mode (lib tự xử). |

Verify ở build release: log filter `roy93~` xem có dòng "Consent denied → skip ads, navigate" khi deny.

### Step D — Banner: simplify `MainAct` + `ResultAct`

- `loadBanner` mặc định `autoManageLifecycle = true` ở SDK 1.1.3 → **bỏ** `bannerResume/Pause/Destroy` trong cả `MainAct` và `ResultAct`. SDK tự hook lifecycle.
- Giữ field `adView` không cần thiết nữa nếu không cần destroy mid-lifecycle. **Chốt: giữ field `adView` để future-proof + giảm noise diff**.

### Step E — VIP feature (Section 9 + 10 của template)

- 1-day grace: **SDK 1.1.3 đã built-in** (verified `AdManager.kt:499-543` của SDK source — spec template ghi "từ 1.1.4" là sai; thực tế đã có trong 1.1.3+ và SDK reports version 1.1.5 in `SdkVersion.kt`). App-side KHÔNG gọi `activateVipByKey` để grant 1 ngày (sẽ stomp giá trị SDK ghi `installBeginMs+24h`). Tuân thủ spec Step 9.1 letter.
- Tạo `feature/vip/`: `FVipManagement.kt` (Fragment), `VipKeys.kt` (Base64-encoded), `VipPrefs.kt` (grantedAt user-redeemed only — KHÔNG lưu first-install flag per spec).
- Layout `f_vip_management.xml` đủ 13 thành phần (Section 10.3 template).
- 5 animations (Section 10.4): pulse, slide-in, crown shimmer, count-up, confetti (option B — không thêm dependency konfetti).
- Strings: thêm vào **toàn bộ 17 locales** project (en default + ar/de/es/fr/hi/id/it/ja/ko/nl/pt/ru/th/tr/vi/zh) — các locale ngoài en+vi tạm dùng English placeholder, đánh dấu cần translation.
- Grace entry detection (Step 9.3): `!VipPrefs.userRedeemedAtLeastOnce() && AdManager.isVipByKeyActive()` → label `vip_entry_first_install`. Khi user tự redeem qua TextField hoặc earn rewarded → `VipPrefs.markUserRedeemed()` flip để chuyển sang label `vip_entry_redeemed`.

### Step F — Toolbar badge + entry point

- `MainAct` toolbar: thêm `Chip` VIP badge (gold khi active, GONE khi free).
- Menu / Settings entry để mở `FVipManagement`. Cân nhắc dùng `Intent` → host activity mới `VipActivity` vì project hiện không có Navigation graph wire sẵn cho fragment standalone. **Chốt: tạo `VipActivity` host fragment để gọn**.

---

## 3. BẢNG SO SÁNH TRƯỚC/SAU

| Aspect | Trước (1.1.1) | Sau (1.1.3) |
|---|---|---|
| Provider | AppLovin MAX | AppLovin MAX (giữ nguyên) |
| Architecture | Monolith `AdManager` | Provider pattern (internal, no API impact) |
| App Open | `initSplashScreen` callback | `initSplashScreen` (giữ) hoặc `awaitSplashComplete` suspend |
| Consent | Chưa có | `requestConsentInfoUpdate` ở Splash trước khi load ad |
| Init code | 4 step (setConfig + earlyInit + AppLovin handshake + AdManager.init) | 2 step (`setConfig` + `initialize`) |
| Banner lifecycle | Manual `bannerResume/Pause/Destroy` | Auto qua `ActivityLifecycleCallbacks` |
| Listener cleanup | Manual `interstitialListener = null` (chưa có code) | Auto qua `ActivityLifecycleCallbacks` |
| Frequency cap | Mặc định lib (chưa custom) | `UTILITY` preset release, `TEST` preset debug |
| Rewarded | Chưa có | `loadRewarded` + `showRewarded` trong VIP screen |
| VIP | Chưa có | Full VIP screen + key activation + grace 1d |
| Observability | `Log.d("roy93~", ...)` | `AdManager.errorReporter` forward exception |

---

## 4. INFO USER CẦN CẤP (HIỆN TRẠNG)

- ✅ Privacy Policy URL: `https://loitp.notion.site/Term-Privacy-Policy-Disclaimer-319b1cd8783942fa8923d2a3c9bce60f`
- ⚠️ AdMob Rewarded ID (debug + release): tạm dùng test ID `ca-app-pub-3940256099942544/5224354917` — **cần ID thật trước khi production**.
- ✅ AppLovin Rewarded ID: `584b6f127bd8534f` (đã có sẵn).
- ✅ AdMob App ID: `ca-app-pub-3612191981543807~1273576268` (đã khai báo trong AndroidManifest).
- ⚠️ AdMob test device ID (EEA consent UMP test): chưa cấp — cần khi QA EEA flow.
- ⚠️ Test scenario EEA (Non-EEA / Accept / Deny): chưa run thực — sẽ log path chính + path fallback ở Step 8 template.

---

## 4.5. RUNTIME BUGS ĐÃ FIX (post-migrate)

| ID | Bug | Root cause | Fix |
|---|---|---|---|
| R1 | App crash `IllegalStateException: Invalid initialization process` ngay khi launch (GalaxyApp.kt:60 → AdManager.initialize() → AppLovinSdk.initialize) | AppLovin SDK 13.x (bundle trong AdmobApplovinWrapper 1.1.3+) cấm đồng thời khai báo `applovin.sdk.key` trong manifest VÀ pass qua `AppLovinSdkInitializationConfiguration` qua code. Migration script wire key qua `AdSdkConfig.applovinSdkKey = BuildConfig.APPLOVIN_SDK_KEY` nhưng quên xoá `<meta-data android:name="applovin.sdk.key" ...>` cũ trong `AndroidManifest.xml:31-33`. | Xoá block `<meta-data applovin.sdk.key>` khỏi AndroidManifest. Giữ AdMob `<meta-data com.google.android.gms.ads.APPLICATION_ID>` (Google Mobile Ads vẫn yêu cầu manifest entry). |

**Lưu ý quan trọng cho future migration**: lib AppLovin SDK upgrade thường có hành vi mới quanh init flow. Khi bump lib version → CHECK lib sample manifest (`_AdmobWrapper/app/src/main/AndroidManifest.xml`) và đối chiếu với manifest consumer app.

---

## 5. NOTES

- `getVipByKeyGrantedAt()` **không có** trong API lib 1.1.3 → app phải persist `grantedAt` riêng qua `VipPrefs` (SharedPreferences `vip_screen_prefs`) khi user redeem qua UI. Cho first-install grace (SDK ghi), app fallback `grantedForUi = expiry - 24h` để vẽ progress bar.
- 1-day grace flow auto của SDK đã built-in trong `AdManager.init()` stage (xem `AdManager.kt:499-543` lib source): gate `isAddVIPMemberFirstInitSuccess()` + `BuildConfig.DEBUG` skip + query `InstallReferrerHelper.queryInstallBeginMs` → `saveVipByKeyUntil(installBeginMs + 86_400_000L)` (path chính) hoặc `saveVipByKeyUntil(now + 86_400_000L)` (fallback nếu installBeginMs unreliable). Spec template ghi "từ 1.1.4" — thực tế lib 1.1.3 source đã có; lib version stamp là 1.1.5 mặc dù Maven coordinate 1.1.3.
- AdSafety preset `UTILITY` (release): `minTimeBetweenFullscreenAds=90s`, `maxFullscreenAdsPerSession=3`, `maxFullscreenAdsPerHour=2`, `maxFullscreenAdsPerDay=5` — phù hợp BMI tool app.
- AdSafety preset `TEST` (debug): bypass throttle để QC test nhanh.
- Spec template Step 1 nêu tên field `interstitialMinIntervalSec`/`appOpenMinIntervalSec`/... — **outdated**, lib thực tế dùng `minTimeBetweenFullscreenAds`/`minTimeAppOpenResume`/`maxFullscreenAdsPerSession`/`maxRapidResumesPerMinute`. Dùng presets `AdSafetyLimits.UTILITY` / `AdSafetyLimits.TEST` đã wire sẵn các giá trị production-ready, không cần override field names.
