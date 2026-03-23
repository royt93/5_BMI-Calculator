# 📊 KẾ HOẠCH TÍCH HỢP QUẢNG CÁO — BMI Calculator 2026

> **SDK Target:** `AdmobWrapper v1.1.1` (JitPack: `com.github.royt93:AdmobWrapper:1.1.1`)
> **Cập nhật:** 2026-03-23 | **Trạng thái:** Planning 📝

---

## MỤC LỤC

1. [Phân tích hiện trạng](#1-phân-tích-hiện-trạng)
2. [Kiến trúc mới sau khi migrate](#2-kiến-trúc-mới-sau-khi-migrate)
3. [Thông tin cần bổ sung (Keys & IDs)](#3-thông-tin-cần-bổ-sung-keys--ids)
4. [Kế hoạch triển khai — Step by Step](#4-kế-hoạch-triển-khai--step-by-step)
5. [Touchpoints quảng cáo](#5-touchpoints-quảng-cáo)

---

## 1. PHÂN TÍCH HIỆN TRẠNG

### Tình trạng tích hợp quảng cáo hiện tại

Dự án **đã tích hợp AdMob** thủ công thông qua class `AdMobManager.kt` tự viết (697 dòng), **chưa dùng AdmobWrapper SDK**.

#### Thành phần hiện có

| File | Vai trò hiện tại |
|------|-----------------|
| `sdkadbmob/AdMobManager.kt` | Singleton tự viết, xử lý Banner / Interstitial / App Open Ad cho AdMob. Có VIP whitelist, error cooldown. |
| `GalaxyApp.kt` | Khởi tạo `MobileAds.initialize()` + `AdMobManager.init()` trong coroutine IO. |
| `SplashAct.kt` | Gọi `AdMobManager.initSplashScreen()` → load + show App Open Ad. |
| `MainAct.kt` | Gọi `AdMobManager.loadBanner()` → Banner ở bottom. Lifecycle: `onResume/onPause/onDestroy`. |
| `ResultAct.kt` | Banner + Interstitial — load inter tại `onCreate`, show inter khi user bấm nút xoá kết quả. |

#### Dependencies hiện tại

```kotlin
// app/build.gradle.kts
implementation("com.google.android.gms:play-services-ads:24.3.0")  // AdMob
implementation("com.google.ads.mediation:applovin:13.2.0.1")        // AppLovin mediation (chưa dùng chủ động)
// applovin-sdk đang bị comment out
```

#### Ad IDs đang dùng

| Loại | Debug (Google Test) | Release (Production) |
|------|--------------------|--------------------|
| **AdMob App ID** | *(trong Manifest)* | `ca-app-pub-3612191981543807~1273576268` |
| **Banner** | `ca-app-pub-3940256099942544/6300978111` | `ca-app-pub-3612191981543807/9117482667` |
| **Interstitial** | `ca-app-pub-3940256099942544/1033173712` | `ca-app-pub-3612191981543807/4216509777` |
| **App Open** | `ca-app-pub-3940256099942544/9257395921` | `ca-app-pub-3612191981543807/5066557013` |

#### Vấn đề với setup hiện tại

- `AdMobManager.kt` là class tự viết, **thiếu 7 lớp AdSafety** (Session Cap, Hourly Cap, Daily Cap, Throttle, CTR Anomaly…)
- Chỉ hỗ trợ **AdMob**, không support AppLovin MAX dual-provider
- Không có auto-preload, không có duplicate guard
- Init AdMob trong **IO coroutine** — không đúng yêu cầu của Google (phải Main Thread)
- `GalaxyApp` thiếu `earlyInit()` → session clock bắt đầu muộn

---

## 2. KIẾN TRÚC MỚI SAU KHI MIGRATE

### Thay thế hoàn toàn

```
[TRƯỚC]                              [SAU]
AdMobManager.kt (tự viết)   →   AdmobWrapper SDK (JitPack)
   ↓                                  ↓
GalaxyApp → SplashAct            GalaxyApp → SplashAct
MainAct → Banner                 MainAct → Banner
ResultAct → Banner + Inter       ResultAct → Banner + Inter
(không có AdSafety)              +7 lớp AdSafety tự động
```

### Lựa chọn Provider

| Provider | Khi nào dùng |
|---------|-------------|
| **AdMob** (`IS_ENABLE_ADMOB = true`) | Mặc định, nếu không có AppLovin key |
| **AppLovin MAX** (`IS_ENABLE_ADMOB = false`) | Hiệu quả hơn nếu có AppLovin account |

> **Khuyến nghị ban đầu:** Dùng `IS_ENABLE_ADMOB = true` (AdMob) vì đã có đủ Ad IDs. Sau đó bổ sung AppLovin nếu muốn tối ưu fill rate.

---

## 3. THÔNG TIN CẦN BỔ SUNG (KEYS & IDS)

### ✅ Đã có (AdMob)

Tất cả AdMob IDs đã có sẵn trong `app/build.gradle.kts`:
- AdMob App ID: `ca-app-pub-3612191981543807~1273576268`
- Banner ID (release): `ca-app-pub-3612191981543807/9117482667`
- Interstitial ID (release): `ca-app-pub-3612191981543807/4216509777`
- App Open ID (release): `ca-app-pub-3612191981543807/5066557013`

**Reward Ad** — Dự án chưa có Reward Ad. Nếu muốn bổ sung:
- Cần tạo Reward Ad Unit mới trong [AdMob Console](https://admob.google.com)
- Điền vào placeholder `ADMOB_REWARD_ID` bên dưới

### ❓ Cần bổ sung (nếu muốn dùng AppLovin)

Điền các thông tin sau nếu chọn AppLovin MAX làm provider:

```
APPLOVIN_SDK_KEY       = ______________________________
APPLOVIN_BANNER_ID     = ______________________________
APPLOVIN_INTERSTITIAL_ID = ____________________________
APPLOVIN_APP_OPEN_ID   = ______________________________
APPLOVIN_REWARD_ID     = ______________________________ (nếu có)
```

> 💡 Nếu **chỉ dùng AdMob** (IS_ENABLE_ADMOB = true), không cần AppLovin keys. Gán giá trị `""` cho các AppLovin fields.

### Reward Ad ID (tùy chọn)

| Loại | Debug | Release |
|------|-------|---------|
| **Reward (AdMob)** | `ca-app-pub-3940256099942544/5224354917` *(Google test)* | *(cần tạo mới)* |

---

## 4. KẾ HOẠCH TRIỂN KHAI — STEP BY STEP

> ⚠️ Làm đúng thứ tự, không nhảy bước!

---

### BƯỚC 1: Thêm JitPack và dependency AdmobWrapper

**`settings.gradle.kts` (root):**
```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") } // ← THÊM DÒNG NÀY
    }
}
```

**`app/build.gradle.kts`** — Thay thế phần dependencies và buildConfigField:

```kotlin
android {
    buildFeatures {
        dataBinding = true
        buildConfig = true // ← đã có, giữ nguyên
    }

    defaultConfig {
        // ... các config hiện tại giữ nguyên ...

        // AppLovin (điền key thực hoặc để "" nếu dùng AdMob)
        buildConfigField("String", "APPLOVIN_SDK_KEY",         "\"\"")
        buildConfigField("String", "APPLOVIN_BANNER_ID",       "\"\"")
        buildConfigField("String", "APPLOVIN_INTERSTITIAL_ID", "\"\"")
        buildConfigField("String", "APPLOVIN_APP_OPEN_ID",     "\"\"")
        buildConfigField("String", "APPLOVIN_REWARD_ID",       "\"\"")
    }

    buildTypes {
        getByName("debug") {
            buildConfigField("Boolean", "IS_ENABLE_ADMOB", "true")
            buildConfigField("String", "ADMOB_BANNER_ID",       "\"ca-app-pub-3940256099942544/6300978111\"")
            buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"ca-app-pub-3940256099942544/1033173712\"")
            buildConfigField("String", "ADMOB_APP_OPEN_ID",     "\"ca-app-pub-3940256099942544/9257395921\"")
            buildConfigField("String", "ADMOB_REWARD_ID",       "\"ca-app-pub-3940256099942544/5224354917\"")
        }
        getByName("release") {
            buildConfigField("Boolean", "IS_ENABLE_ADMOB", "true")
            buildConfigField("String", "ADMOB_BANNER_ID",       "\"ca-app-pub-3612191981543807/9117482667\"")
            buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"ca-app-pub-3612191981543807/4216509777\"")
            buildConfigField("String", "ADMOB_APP_OPEN_ID",     "\"ca-app-pub-3612191981543807/5066557013\"")
            buildConfigField("String", "ADMOB_REWARD_ID",       "\"\"") // ← Điền ID khi có
            // ... các config release hiện tại ...
        }
    }
}

dependencies {
    // ✅ Xoá dòng cũ:
    // implementation("com.google.android.gms:play-services-ads:24.3.0")
    // implementation("com.google.ads.mediation:applovin:13.2.0.1")

    // ✅ Thêm dòng mới — 1 dòng kéo toàn bộ!
    implementation("com.github.royt93:AdmobWrapper:1.1.1")

    // ... các dependencies khác giữ nguyên ...
}
```

---

### BƯỚC 2: Cập nhật AndroidManifest.xml

Trong `AndroidManifest.xml`, **giữ nguyên** meta-data AdMob hiện có:
```xml
<!-- Đã có — giữ nguyên -->
<meta-data
    android:name="com.google.android.gms.ads.APPLICATION_ID"
    android:value="ca-app-pub-3612191981543807~1273576268" />

<!-- Thêm nếu dùng AppLovin -->
<!-- <meta-data
    android:name="applovin.sdk.key"
    android:value="YOUR_APPLOVIN_SDK_KEY" /> -->
```

---

### BƯỚC 3: Refactor GalaxyApp.kt

**Xoá** class `AdMobManager` tự viết khỏi `sdkadbmob/` (hoặc giữ backup). Sửa `GalaxyApp.kt`:

```kotlin
class GalaxyApp : Application() {

    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
        setupAdWrapper()
        initializeDefaultProfile()
    }

    private fun setupAdWrapper() {
        val adConfig = AdSdkConfig(
            isEnableAdmob          = BuildConfig.IS_ENABLE_ADMOB,
            admobBannerId          = BuildConfig.ADMOB_BANNER_ID,
            admobInterId           = BuildConfig.ADMOB_INTERSTITIAL_ID,
            admobAppOpenId         = BuildConfig.ADMOB_APP_OPEN_ID,
            applovinBannerId       = BuildConfig.APPLOVIN_BANNER_ID,
            applovinInterId        = BuildConfig.APPLOVIN_INTERSTITIAL_ID,
            applovinAppOpenId      = BuildConfig.APPLOVIN_APP_OPEN_ID,
            applovinSdkKey         = BuildConfig.APPLOVIN_SDK_KEY,
            setGAIDVipMember       = emptySet(), // Thêm GAID thiết bị dev/test tại đây
        )

        // QUAN TRỌNG: Thứ tự 3 dòng này không được đổi!
        AdManager.setConfig(adConfig)   // 1. Gắn config (Main Thread)
        AdManager.earlyInit(this)       // 2. Khởi động session clock sớm nhất

        if (BuildConfig.IS_ENABLE_ADMOB) {
            MobileAds.initialize(this) { _ ->
                AdManager.init(this, adConfig) { success, gaid ->
                    Log.d("GalaxyApp", "AdManager init: success=$success, gaid=$gaid")
                }
            }
        } else {
            val sdk = AppLovinSdk.getInstance(this)
            sdk.mediationProvider = "max"
            sdk.initializeSdk {
                AdManager.init(this, adConfig) { success, gaid ->
                    Log.d("GalaxyApp", "AdManager init: success=$success, gaid=$gaid")
                }
            }
        }
    }
}
```

---

### BƯỚC 4: Refactor SplashAct.kt (App Open Ad)

Chỉ cần đổi 1 file — **SDK lo toàn bộ**:

```kotlin
class SplashAct : AppCompatActivity() {        // hoặc BaseActivity
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        startAnimations() // giữ nguyên animation hiện tại

        AdManager.initSplashScreen(this) {
            goToMain() // callback này kích hoạt khi App Open Ad xong hoặc timeout 8s
        }
    }

    private fun goToMain() {
        startActivity(Intent(this, MainAct::class.java))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
}
```

---

### BƯỚC 5: Refactor MainAct.kt (Banner Ad)

```kotlin
class MainAct : BaseActivity() {
    private var adView: View? = null  // ← Đổi từ AdView? sang View?

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ...setup views như cũ...

        // Load banner
        adView = AdManager.loadBanner(
            context   = this,
            container = binding.flAd.bannerContainer,
            tvLabelAd = binding.flAd.tvLabelAd,
        )
    }

    // BẮT BUỘC copy đủ 3 override này
    override fun onResume()  { super.onResume();  AdManager.bannerResume(adView) }
    override fun onPause()   { super.onPause();   AdManager.bannerPause(adView) }
    override fun onDestroy() { super.onDestroy(); AdManager.bannerDestroy(adView) }
}
```

---

### BƯỚC 6: Refactor ResultAct.kt (Banner + Interstitial Ad)

```kotlin
class ResultAct : BaseActivity() {
    private var adView: View? = null  // ← Đổi từ AdView? sang View?

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ...setup views như cũ...

        // Xoá: AdMobManager.setCurrentActivity / interstitialListener / loadInterstitial
        // Thay bằng:
        AdManager.loadInterstitial(this)

        adView = AdManager.loadBanner(
            context   = this,
            container = binding.flAd.bannerContainer,
            tvLabelAd = binding.flAd.tvLabelAd,
        )
    }

    // BẮT BUỘC copy đủ 3 override này
    override fun onResume()  { super.onResume();  AdManager.bannerResume(adView) }
    override fun onPause()   { super.onPause();   AdManager.bannerPause(adView) }
    override fun onDestroy() { super.onDestroy(); AdManager.bannerDestroy(adView) }

    // Khi user bấm nút xoá kết quả → show Interstitial trước khi finish
    private fun backPreviousPage(isShowAd: Boolean) {
        animationViewUp()
        if (isShowAd) {
            AdManager.showInterstitial(this) { adShown ->
                Log.d("ResultAct", "Ad shown: $adShown")
                handler.postDelayed(backRunnable, 100)
            }
        } else {
            handler.postDelayed(backRunnable, 600)
        }
    }
}
```

> ⚠️ **Xoá** `implements AdMobManager.InterstitialAdListener` và toàn bộ override methods `onAdLoaded/onAdDismissed/...` vì SDK mới không cần nữa.

---

### BƯỚC 7: Xoá file cũ

- **Xoá** `sdkadbmob/AdMobManager.kt` (hoặc rename thành `AdMobManager.kt.bak` để backup)
- Xoá tất cả import `com.samsunggalaxy.sdkadbmob.AdMobManager` khỏi các file

---

### BƯỚC 8: (Tùy chọn) Thêm Reward Ad

Nếu muốn, có thể gắn Reward Ad tại một trong các điểm:
- **CalculatorsActivity** → trước khi mở từng máy tính (BMR, TDEE, Ideal Weight…)
- **HistoryActivity** → trước khi export/chia sẻ lịch sử

```kotlin
// Ví dụ trong CalculatorsActivity
private fun openCalculatorWithAd(intent: Intent) {
    AdManager.showRewardedAd(this) { rewarded ->
        startActivity(intent)
    }
}
```

---

## 5. TOUCHPOINTS QUẢNG CÁO

### Sơ đồ flow quảng cáo

```
[App Launch]
     ↓
[SplashAct] ──── APP OPEN AD ────→ (sau dismiss hoặc timeout 8s)
     ↓
[MainAct]   ──── BANNER (bottom) ─ hiển thị suốt khi ở màn hình chính
     ↓ (user nhấn Start)
[ResultAct] ──── BANNER (bottom) ─ hiển thị kết quả BMI
     │
     ├─ [Nút Reload] → back về MainAct (KHÔNG show Inter)
     └─ [Nút Delete] → INTERSTITIAL → back về MainAct

[MainAct → Menu → HistoryActivity] ─ (hiện chưa có ad, có thể thêm Banner tùy chọn)
[MainAct → Menu → CalculatorsActivity] ─ (hiện chưa có ad, có thể thêm Inter tùy chọn)
```

### Bảng touchpoints chi tiết

| Màn hình | Loại Ad | Thời điểm hiển thị | Ghi chú |
|----------|---------|-------------------|---------|
| `SplashAct` | **App Open Ad** | Ngay khi app khởi động | SDK tự lo timeout 8s |
| `MainAct` | **Banner** | Luôn hiển thị bottom | Resume/Pause/Destroy lifecycle |
| `ResultAct` | **Banner** | Luôn hiển thị bottom | Resume/Pause/Destroy lifecycle |
| `ResultAct` | **Interstitial** | Khi user bấm nút "Delete/Back" | Chỉ show khi `isShowAd=true` |
| `HistoryActivity` | *(tùy chọn)* Banner | Có thể thêm ở bottom | Hiện chưa có |
| `CalculatorsActivity` | *(tùy chọn)* Interstitial | Trước khi mở sub-calculator | Có thể thêm |
| `BmrCalculatorActivity` | *(tùy chọn)* Banner | Bottom | Hiện chưa có |
| `TdeeCalculatorActivity` | *(tùy chọn)* Banner | Bottom | Hiện chưa có |
| `IdealWeightCalculatorActivity` | *(tùy chọn)* Banner | Bottom | Hiện chưa có |
| `BodyFatCalculatorActivity` | *(tùy chọn)* Banner | Bottom | Hiện chưa có |

> **Reward Ad** — Hiện dự án **không có** tính năng phù hợp để gắn Reward Ad (không có "unlock premium content / xem thêm tính năng"). Có thể bổ sung sau nếu cần.

---

## THÔNG TIN CẦN BỔ SUNG TỪ BẠN

Để tôi có thể bắt đầu viết code, bạn cần xác nhận:

| # | Thông tin | Giá trị |
|---|-----------|---------|
| 1 | **Provider ưu tiên** | AdMob (true) hay AppLovin MAX (false)? |
| 2 | **AppLovin SDK Key** | *(nếu dùng AppLovin)* |
| 3 | **AppLovin Banner ID** | *(nếu dùng AppLovin)* |
| 4 | **AppLovin Interstitial ID** | *(nếu dùng AppLovin)* |
| 5 | **AppLovin App Open ID** | *(nếu dùng AppLovin)* |
| 6 | **AdMob Reward ID** | *(nếu muốn thêm Reward Ad)* |
| 7 | **Có muốn thêm Banner vào HistoryActivity không?** | Có / Không |
| 8 | **Có muốn thêm Interstitial vào CalculatorsActivity không?** | Có / Không |
| 9 | **Có muốn thêm Banner vào các sub-calculator (BMR, TDEE…) không?** | Có / Không |

---

> 🎯 **Tóm tắt:** Chỉ cần xác nhận 9 điểm trên là tôi có thể triển khai ngay. Không cần hiểu sâu SDK — `AdmobWrapper v1.1.1` lo toàn bộ AdSafety, preload, lifecycle tự động.
