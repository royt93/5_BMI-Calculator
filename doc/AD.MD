# 📊 KẾ HOẠCH TÍCH HỢP QUẢNG CÁO — BMI Calculator 2026

> **SDK:** `AdmobWrapper v1.1.1` | **Provider:** AppLovin MAX | **Cập nhật:** 2026-03-23 ✅

---

## MỤC LỤC

1. [Phân tích hiện trạng](#1-phân-tích-hiện-trạng)
2. [Thông tin Keys & IDs](#2-thông-tin-keys--ids)
3. [Kế hoạch triển khai — Step by Step](#3-kế-hoạch-triển-khai--step-by-step)
4. [Touchpoints quảng cáo](#4-touchpoints-quảng-cáo)

---

## 1. PHÂN TÍCH HIỆN TRẠNG

### Trước khi migrate

| File | Vai trò cũ |
|------|-----------|
| `sdkadbmob/AdMobManager.kt` | Singleton tự viết 697 dòng — **SẼ BỊ XOÁ** |
| `GalaxyApp.kt` | Init AdMob trong IO coroutine (sai thread) — **SẼ REFACTOR** |
| `SplashAct.kt` | App Open Ad via `AdMobManager.initSplashScreen()` — **SẼ REFACTOR** |
| `MainAct.kt` | Banner via `AdMobManager.loadBanner()` — **SẼ REFACTOR** |
| `ResultAct.kt` | Banner + Interstitial — `implements InterstitialAdListener` — **SẼ REFACTOR** |

### Vấn đề với setup cũ

- `AdMobManager.kt` tự viết: **thiếu 7 lớp AdSafety** (throttle, daily cap, CTR anomaly…)
- Chỉ hỗ trợ AdMob, **không support AppLovin MAX**
- Init MobileAds trong IO thread — **vi phạm yêu cầu Google**
- Thiếu `earlyInit()` → session clock bắt đầu muộn

---

## 2. THÔNG TIN KEYS & IDS

### AppLovin MAX (Provider chính)

| Config | Value |
|--------|-------|
| **SDK Key** | `e75FnQfS9XTTqM1Kne69U7PW_MBgAnGQTFvtwVVui6kRPKs5L7ws9twr5IQWwVfzPKZ5pF2IfDa7lguMgGlCyt` |
| **Banner ID** | `935687e95c2be5f5` |
| **Interstitial ID** | `e080595a143cf78e` |
| **Reward ID** | `584b6f127bd8534f` |
| **App Open ID** | `e349570297a4e092` |

### AdMob (Fallback / Debug test IDs)

| Config | Debug | Release |
|--------|-------|---------|
| **App ID** | *(Manifest)* | `ca-app-pub-3612191981543807~1273576268` |
| **Banner** | `ca-app-pub-3940256099942544/6300978111` | `ca-app-pub-3612191981543807/9117482667` |
| **Interstitial** | `ca-app-pub-3940256099942544/1033173712` | `ca-app-pub-3612191981543807/4216509777` |
| **App Open** | `ca-app-pub-3940256099942544/9257395921` | `ca-app-pub-3612191981543807/5066557013` |

> **IS_ENABLE_ADMOB = false** → AppLovin MAX được sử dụng (cả debug lẫn release)

---

## 3. KẾ HOẠCH TRIỂN KHAI — STEP BY STEP

> ⚠️ Làm đúng thứ tự, không nhảy bước!

---

### BƯỚC 1: settings.gradle.kts — Thêm JitPack

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") } // ← THÊM DÒNG NÀY
    }
}
```

---

### BƯỚC 2: app/build.gradle.kts — Đổi dependency + BuildConfig

```kotlin
android {
    defaultConfig {
        // AppLovin
        buildConfigField("String", "APPLOVIN_SDK_KEY",
            "\"e75FnQfS9XTTqM1Kne69U7PW_MBgAnGQTFvtwVVui6kRPKs5L7ws9twr5IQWwVfzPKZ5pF2IfDa7lguMgGlCyt\"")
        buildConfigField("String", "APPLOVIN_BANNER_ID",       "\"935687e95c2be5f5\"")
        buildConfigField("String", "APPLOVIN_INTERSTITIAL_ID", "\"e080595a143cf78e\"")
        buildConfigField("String", "APPLOVIN_APP_OPEN_ID",     "\"e349570297a4e092\"")
        buildConfigField("String", "APPLOVIN_REWARD_ID",       "\"584b6f127bd8534f\"")
    }

    buildTypes {
        getByName("debug") {
            buildConfigField("Boolean", "IS_ENABLE_ADMOB", "false")
            // Giữ AdMob debug IDs làm fallback reference
            buildConfigField("String", "ADMOB_BANNER_ID",       "\"ca-app-pub-3940256099942544/6300978111\"")
            buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"ca-app-pub-3940256099942544/1033173712\"")
            buildConfigField("String", "ADMOB_APP_OPEN_ID",     "\"ca-app-pub-3940256099942544/9257395921\"")
        }
        getByName("release") {
            buildConfigField("Boolean", "IS_ENABLE_ADMOB", "false")
            buildConfigField("String", "ADMOB_BANNER_ID",       "\"ca-app-pub-3612191981543807/9117482667\"")
            buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"ca-app-pub-3612191981543807/4216509777\"")
            buildConfigField("String", "ADMOB_APP_OPEN_ID",     "\"ca-app-pub-3612191981543807/5066557013\"")
        }
    }
}

dependencies {
    // XOÁ dòng cũ:
    // implementation("com.google.android.gms:play-services-ads:24.3.0")
    // implementation("com.google.ads.mediation:applovin:13.2.0.1")

    // THÊM 1 dòng mới:
    implementation("com.github.royt93:AdmobWrapper:1.1.1")
}
```

---

### BƯỚC 3: AndroidManifest.xml — Thêm AppLovin SDK Key

```xml
<application ...>
    <!-- Giữ nguyên AdMob App ID -->
    <meta-data
        android:name="com.google.android.gms.ads.APPLICATION_ID"
        android:value="ca-app-pub-3612191981543807~1273576268" />

    <!-- THÊM AppLovin SDK Key -->
    <meta-data
        android:name="applovin.sdk.key"
        android:value="e75FnQfS9XTTqM1Kne69U7PW_MBgAnGQTFvtwVVui6kRPKs5L7ws9twr5IQWwVfzPKZ5pF2IfDa7lguMgGlCyt" />
</application>
```

---

### BƯỚC 4: GalaxyApp.kt — Refactor khởi tạo SDK

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
            isEnableAdmob          = BuildConfig.IS_ENABLE_ADMOB,    // false → AppLovin
            admobBannerId          = BuildConfig.ADMOB_BANNER_ID,
            admobInterId           = BuildConfig.ADMOB_INTERSTITIAL_ID,
            admobAppOpenId         = BuildConfig.ADMOB_APP_OPEN_ID,
            applovinBannerId       = BuildConfig.APPLOVIN_BANNER_ID,
            applovinInterId        = BuildConfig.APPLOVIN_INTERSTITIAL_ID,
            applovinAppOpenId      = BuildConfig.APPLOVIN_APP_OPEN_ID,
            applovinSdkKey         = BuildConfig.APPLOVIN_SDK_KEY,
            setGAIDVipMember       = emptySet(),
        )

        AdManager.setConfig(adConfig)   // 1. Config (Main Thread)
        AdManager.earlyInit(this)       // 2. Session clock sớm nhất

        // AppLovin: init SDK trước, rồi mới gọi AdManager.init
        val sdk = AppLovinSdk.getInstance(this)
        sdk.mediationProvider = "max"
        sdk.initializeSdk {
            AdManager.init(this, adConfig) { success, gaid ->
                Log.d("GalaxyApp", "AdManager init: success=$success, gaid=$gaid")
            }
        }
    }
}
```

---

### BƯỚC 5: SplashAct.kt — App Open Ad

```kotlin
class SplashAct : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        startAnimations() // giữ nguyên

        AdManager.initSplashScreen(this) {
            goToMain() // SDK tự lo: show ad → callback / timeout 8s → callback
        }
    }
}
```

---

### BƯỚC 6: MainAct.kt — Banner Ad

```kotlin
class MainAct : BaseActivity() {
    private var adView: View? = null  // Đổi từ AdView? → View?

    override fun onCreate(...) {
        // ...
        adView = AdManager.loadBanner(
            context   = this,
            container = binding.flAd.bannerContainer,
            tvLabelAd = binding.flAd.tvLabelAd,
        )
    }

    override fun onResume()  { super.onResume();  AdManager.bannerResume(adView) }
    override fun onPause()   { super.onPause();   AdManager.bannerPause(adView) }
    override fun onDestroy() { super.onDestroy(); AdManager.bannerDestroy(adView) }
}
```

---

### BƯỚC 7: ResultAct.kt — Banner + Interstitial + Reward Ad

```kotlin
class ResultAct : BaseActivity() {
    private var adView: View? = null
    private var detailedPlanUnlocked = false

    override fun onCreate(...) {
        AdManager.loadInterstitial(this)

        adView = AdManager.loadBanner(
            context   = this,
            container = binding.flAd.bannerContainer,
            tvLabelAd = binding.flAd.tvLabelAd,
        )

        // Reward Ad — nút "🎁 Get Detailed Plan"
        binding.btnDetailedPlan.setOnClickListener {
            AdManager.showRewardedAd(this) { rewarded ->
                if (rewarded) {
                    unlockDetailedPlan()
                }
            }
        }
    }

    private fun backPreviousPage(isShowAd: Boolean) {
        animationViewUp()
        if (isShowAd) {
            AdManager.showInterstitial(this) { _ ->
                handler.postDelayed(backRunnable, 100)
            }
        } else {
            handler.postDelayed(backRunnable, 600)
        }
    }

    // Xoá: implements InterstitialAdListener, clearCurrentActivity, setCurrentActivity
    override fun onResume()  { super.onResume();  AdManager.bannerResume(adView) }
    override fun onPause()   { super.onPause();   AdManager.bannerPause(adView) }
    override fun onDestroy() { super.onDestroy(); AdManager.bannerDestroy(adView) }
}
```

---

### BƯỚC 8: Xoá file cũ

```
XOÁ: app/src/main/java/com/samsunggalaxy/sdkadbmob/AdMobManager.kt
XOÁ: import com.samsunggalaxy.sdkadbmob.AdMobManager (khắp nơi)
XOÁ: import com.samsunggalaxy.sdkadbmob.UIUtils (nếu đã merge vào wrapper)
```

---

## 4. TOUCHPOINTS QUẢNG CÁO

### Sơ đồ flow

```
[App Launch]
     ↓
[SplashAct] ──── APP OPEN AD ───→ (dismiss hoặc timeout 8s)
     ↓
[MainAct]   ──── BANNER (bottom) ── hiển thị liên tục
     ↓ (user nhấn nút tính BMI)
[ResultAct] ──── BANNER (bottom) ── hiển thị liên tục
     │
     ├─ [Nút Reload] ──────────────→ back MainAct (không show Inter)
     ├─ [Nút Delete] ──── INTER ──→ back MainAct
     └─ [Nút "Get Detailed Plan"] ── REWARD AD ──→ unlock detailed health card
```

### Bảng touchpoints

| Màn hình | Ad Type | Trigger | Ghi chú |
|----------|---------|---------|---------|
| `SplashAct` | **App Open** | Cold start | SDK tự lo timeout 8s |
| `MainAct` | **Banner** | Luôn hiển thị | Bottom |
| `ResultAct` | **Banner** | Luôn hiển thị | Bottom |
| `ResultAct` | **Interstitial** | Nút Delete/Back (isShowAd=true) | AdSafety tự throttle |
| `ResultAct` | **Reward** | Nút "🎁 Get Detailed Plan" | Unlock bảng phân tích chi tiết |

### Reward Ad — Chi tiết luồng

```
User xem kết quả BMI
   ↓
Bấm nút "🎁 Nhận lời khuyên chi tiết"
   ↓
AdManager.showRewardedAd() → User xem ad 30s
   ↓ (rewarded = true)
Unlock card "Personalized Health Plan":
   • Phân tích chi tiết theo từng chỉ số (BMR, TDEE, Body Fat ước tính)
   • Gợi ý chế độ ăn uống theo BMI category
   • Kế hoạch tập luyện tuần (Underweight/Healthy/Overweight/Obese)
   ↓ (rewarded = false — user skip ad)
Card ẩn, không unlock
```

> 💡 **Tại sao đây là touchpoint tốt nhất?**
> - User vừa tính BMI xong → **engagement cao nhất** trong toàn bộ app
> - Reward rõ ràng, có giá trị thực (không phải chỉ "bỏ ads")
> - Không ép buộc — nút bấm tùy chọn, không chặn flow chính
> - Phù hợp với Google Play policy (reward phải có giá trị thực cho user)
