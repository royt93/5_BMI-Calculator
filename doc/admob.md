# AdMob Integration — Architecture Review

## Tổng quan hệ thống

`AdMobManager.kt` là singleton quản lý **3 loại quảng cáo**: Banner, Interstitial, App Open. File 697 dòng, chứa cả AdMobManager + utilities (AppPreferences, EventBus, NetworkUtils, UIUtils).

---

## ✅ Điểm mạnh

| # | Feature | Chi tiết |
|---|---------|----------|
| 1 | **VIP Whitelist** | GAID-based skip ads cho VIP devices — production-ready |
| 2 | **Error Cooldown** | 15 phút cooldown sau lỗi load → tránh spam request |
| 3 | **WeakReference** | `currentActivity` + `interstitialListener` dùng WeakReference → no memory leak |
| 4 | **Thread-safe** | `AtomicInteger` cho splash counter, `@Volatile` cho singleton |
| 5 | **Network check** | Skip ad load khi offline → tiết kiệm tài nguyên |
| 6 | **Edge-to-Edge** | `UIUtils` hỗ trợ Android 10+ layout |
| 7 | **Comprehensive logging** | Mỗi event đều có Log.d → dễ debug production |

---

## ⚠️ Vấn đề cần lưu ý

### 1. 🔴 God Class — File quá lớn
- 697 dòng, 1 file chứa **6 class/object**: AdMobManager, AppPreferences, EventBus, NetworkUtils, UIUtils, extension function
- **Đề xuất**: Tách thành các file riêng:
  - `AdMobManager.kt` — chỉ ad logic
  - `AppPreferences.kt`
  - `EventBus.kt`
  - `NetworkUtils.kt`
  - `UIUtils.kt`

### 2. 🔴 CoroutineScope leak tiềm ẩn
```kotlin
// Line 131 — CoroutineScope không được quản lý lifecycle
CoroutineScope(Dispatchers.Default).launch {
    EventBus.sendEvent(true)
}

// Line 528 — splashCoroutineJob cancel được, nhưng CoroutineScope tạo mới mỗi lần
splashCoroutineJob = CoroutineScope(Dispatchers.Default + SupervisorJob()).launch { ... }
```
**Đề xuất**: Dùng 1 `CoroutineScope` duy nhất trong singleton, cancel trong Application.onTerminate().

### 3. 🟡 Banner Ad dùng `applicationContext`
```kotlin
// Line 221
val adView = AdView(context.applicationContext)
```
- Dùng `applicationContext` cho AdView có thể gây lỗi với một số ad format cần Activity context
- **Đề xuất**: Dùng `context` trực tiếp (Activity context) khi tạo AdView

### 4. 🟡 App Open Ad timeout logic phức tạp
```kotlin
// Line 410 — Chỉ skip khi KHÔNG phải DEBUG
if (BuildConfig.DEBUG) { /* nothing */ } else {
    if ((System.currentTimeMillis() - lastAppOpenLoadTime) < APP_OPEN_AD_TIME_OUT) { ... }
}
```
- Debug mode bỏ qua timeout check → load nhiều lần → có thể ảnh hưởng quota test
- Logic nested `if-else` khó đọc

### 5. 🟡 Interstitial callback chồng chéo
```kotlin
// showInterstitial() wraps originalCallback → 2 layers
interstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
    override fun onAdShowedFullScreenContent() {
        originalCallback?.onAdShowedFullScreenContent() // Gọi callback cũ
    }
}
```
- 2 callback layers (#1 từ `setInterstitialCallback`, #2 từ `showInterstitial`) → khó trace, dễ bug

### 6. 🟢 Commented code nhiều
- `getMyListVipDevice()` line 138-160: ~20 dòng comment GAID devices
- `AppLifecycleListener` line 616-653: class bị comment hoàn toàn
- `SHOW_INTERSTITIAL_CHANCE` line 335-341: logic bị comment
- **Đề xuất**: Xoá hoặc chuyển vào doc riêng

---

## 📊 Flow Analysis

```
App Start
  └─ GalaxyApp.onCreate()
       └─ AdMobManager.init()
            ├─ getGAID() → IO thread
            ├─ setTestDeviceIds()
            ├─ addVIPMember() (release only, first init)
            └─ EventBus.sendEvent(true)
                 └─ initSplashScreen() receives event
                      ├─ loadAppOpenAd()
                      │    └─ onLoaded → showAppOpenAd()
                      │         └─ onDismiss → onAdLoaded callback → show main UI
                      └─ onFailed → onAdLoaded callback → show main UI

Main Screen
  └─ loadBanner() → container + tvLabelAd
  └─ loadInterstitial() → preload for later

Result Screen / Navigation
  └─ showInterstitial() → show preloaded ad
       └─ onDoneFlow(true/false)
```

---

## 🎯 Đánh giá tổng thể

| Tiêu chí | Điểm | Ghi chú |
|----------|-------|---------|
| **Kiến trúc** | 7/10 | Singleton pattern phù hợp, nhưng God Class |
| **Memory Safety** | 9/10 | WeakReference, cleanup đúng cách |
| **Error Handling** | 8/10 | Cooldown, network check, null-safe |
| **Thread Safety** | 8/10 | AtomicInteger, Volatile, nhưng CoroutineScope leak |
| **Maintainability** | 6/10 | File quá lớn, callback chồng, comment thừa |
| **Production Ready** | 8/10 | VIP system, test devices, comprehensive logging |

**Overall: 7.5/10** — Hệ thống ads tốt, production-ready với VIP whitelist và error handling. Cần refactor tách file và cleanup comment.
