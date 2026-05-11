# 🔐 Security Review — BMI Calculator Android App

> **Ngày review:** 2026-05-11 | **Reviewer:** Antigravity AI  
> **Phạm vi:** Toàn bộ source code, cấu hình build, manifest, và data layer

---

## Tổng quan kết quả

| Mức độ | Số lượng | Trạng thái |
|--------|----------|------------|
| 🔴 **Critical** | 1 | Cần fix ngay |
| 🟠 **High** | 3 | Nên fix trước khi release |
| 🟡 **Medium** | 4 | Fix trong sprint tới |
| 🟢 **Low / Info** | 4 | Cải thiện dần |

---

## 🔴 Critical

### C-01: AppLovin SDK Key lộ trong AndroidManifest.xml

**File:** `AndroidManifest.xml` line 36

```xml
<!-- ❌ KEY LỘ RA NGOÀI — có thể bị đọc bằng apktool -->
<meta-data
    android:name="applovin.sdk.key"
    android:value="e75FnQfS9XTTqM1Kne69U7PW_MBgAnGQTFvtwVVui6kRPKs5L7ws9twr5IQWwVfzPKZ5pF2IfDa7lguMgGlCyt" />
```

**Rủi ro:** Bất kỳ ai decompile APK bằng `apktool` đều đọc được key này. Key AppLovin có thể bị dùng để tạo fake impressions, gây thiệt hại tài chính.

> **⚠️ LƯU Ý:** SDK key trong Manifest **không thể che bởi ProGuard** vì nó nằm trong XML resource, không phải Java bytecode.

**Fix:** Đây là yêu cầu bắt buộc của AppLovin SDK — key phải ở Manifest. Cách giảm thiểu rủi ro:
1. Kích hoạt **AppLovin Publisher Fraud Protection** trong dashboard.
2. Giới hạn key theo Bundle ID trong AppLovin Console.
3. Monitor bất thường về impressions trong dashboard AppLovin.

---

## 🟠 High

### H-01: `Log.d()` chứa dữ liệu sức khỏe người dùng trong production build

**Files:** `ResultAct.kt`, `GalaxyApp.kt`, `MainAct.kt`, `StreakManager.kt`, v.v.

```kotlin
// ❌ Log này chạy trong CẢ production build
Log.d("roy93~", "saveToHistory: profileId=$profileId, weight=$weight, height=$height, bmi=$result")
Log.d("roy93~", "AdManager init: success=$success, gaid=$gaid")  // Lộ GAID
```

**Rủi ro:** 
- Dữ liệu BMI, cân nặng, chiều cao là **dữ liệu sức khỏe nhạy cảm** (GDPR Category).
- GAID (Google Advertising ID) là PII — log ra là vi phạm chính sách Google Play.
- Bất kỳ app nào có `READ_LOGS` permission đều đọc được trên Android < 4.1.

**Fix:** Bọc tất cả `Log.d/w` bằng `BuildConfig.DEBUG`:

```kotlin
// ✅ Đúng cách
if (BuildConfig.DEBUG) {
    Log.d("roy93~", "saveToHistory: weight=$weight, bmi=$result")
}
```

Hoặc dùng Timber với `DebugTree` chỉ plant trong debug:
```kotlin
// GalaxyApp.onCreate()
if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
```

---

### H-02: `android:allowBackup="true"` — Backup toàn bộ DB không kiểm soát

**File:** `AndroidManifest.xml` line 18  
**File:** `xml_backup_rules.xml` — **File rỗng hoàn toàn**

```xml
<!-- AndroidManifest.xml -->
android:allowBackup="true"  <!-- ❌ Backup tất cả -->

<!-- xml_backup_rules.xml — RỖNG, không có rule nào -->
<full-backup-content></full-backup-content>
```

**Rủi ro:** `bmi_database` (Room DB chứa lịch sử BMI, profile người dùng) và DataStore (`bmi_settings`) được backup lên Google Drive của user. Trên Android < 12, adb backup có thể extract dữ liệu này mà không cần root.

**Fix:** Cập nhật backup rules để loại trừ database và preferences:

```xml
<!-- xml_backup_rules.xml -->
<?xml version="1.0" encoding="utf-8"?>
<full-backup-content>
    <exclude domain="database" path="bmi_database" />
    <exclude domain="database" path="bmi_database-shm" />
    <exclude domain="database" path="bmi_database-wal" />
    <exclude domain="sharedpref" path="bmi_settings.preferences_pb" />
</full-backup-content>
```

```xml
<!-- xml_data_extraction_rules.xml -->
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="database" path="bmi_database" />
        <exclude domain="sharedpref" path="." />
    </cloud-backup>
    <device-transfer>
        <exclude domain="database" path="bmi_database" />
    </device-transfer>
</data-extraction-rules>
```

---

### H-03: `MainAct` exported=true không có intent-filter — Attack surface không cần thiết

**File:** `AndroidManifest.xml` line 53-59

```xml
<!-- ❌ exported=true nhưng không có intent-filter hợp lệ -->
<activity
    android:name=".ui.MainAct"
    android:exported="true">
    <!-- intent-filter bị comment hết -->
</activity>
```

**Rủi ro:** Activity exported=true có thể bị launch trực tiếp bởi app khác mà không qua SplashAct → bypass logic khởi tạo (database chưa sẵn sàng, AdManager chưa init).

**Fix:** Đổi thành `exported="false"` vì không có intent-filter hợp lệ nào:

```xml
<activity
    android:name=".ui.MainAct"
    android:exported="false" />
```

---

## 🟡 Medium

### M-01: Room Database không bật encryption

**File:** `AppDatabase.kt`

```kotlin
Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "bmi_database")
    // ❌ Không có encryption
    .build()
```

**Rủi ro:** File `bmi_database` có thể đọc được trên thiết bị đã root hoặc qua ADB backup trên Android < 12.

**Fix:** Dùng SQLCipher (nếu data rất nhạy cảm) hoặc ít nhất bật `WAL` + giới hạn backup như H-02.

---

### M-02: FileProvider path quá rộng (`path="."`)

**File:** `file_path.xml`

```xml
<!-- ❌ Chia sẻ TOÀN BỘ external storage -->
<external-path name="external_files" path="." />
```

**Rủi ro:** Bất kỳ file nào trong external storage đều có thể được chia sẻ qua FileProvider này.

**Fix:** Giới hạn đúng thư mục:

```xml
<external-path name="external_files" path="Android/data/com.samsunggalaxy.bmicalculator/files/" />
```

---

### M-03: `WRITE_EXTERNAL_STORAGE` với `tools:ignore="ScopedStorage"`

**File:** `AndroidManifest.xml` line 6-7

```xml
<uses-permission
    android:name="android.permission.WRITE_EXTERNAL_STORAGE"
    tools:ignore="ScopedStorage" />
```

**Rủi ro:** Suppress cảnh báo Scoped Storage không phải fix. App target SDK 36 nhưng vẫn giữ permission viết storage cũ — có thể bị Google Play từ chối hoặc cần justify trong khai báo.

**Đánh giá:** Nếu feature export ảnh/PDF thực sự cần → dùng `MediaStore` API thay thế. Nếu không cần → xóa permission.

---

### M-04: `proguard-rules.pro` rỗng hoàn toàn

**File:** `proguard-rules.pro` — 0 bytes

**Rủi ro:** ProGuard/R8 chạy với rules mặc định. Một số class quan trọng như Room entities, Data classes, Reflection-based SDK có thể bị strip/rename sai → crash trên release build. Quan trọng hơn, **không có rule bảo vệ riêng** cho các class nhạy cảm.

**Fix cơ bản cần thêm:**

```proguard
# AppLovin MAX
-keep class com.applovin.** { *; }
-dontwarn com.applovin.**

# Room entities
-keep class com.samsunggalaxy.data.** { *; }

# AdMob / Google Ads
-keep class com.google.android.gms.ads.** { *; }

# DataBinding
-keep class com.samsunggalaxy.databinding.** { *; }
```

---

## 🟢 Low / Info

### L-01: `http://` URL hardcode trong Activity.kt

**File:** `ext/Activity.kt` line 173, 259

```kotlin
Uri.parse("http://play.google.com/store/apps/details?id=$packageName")
// Nên dùng https://
```

**Fix:** Đổi `http://` → `https://` cho tất cả URL.

---

### L-02: Debug tag `"roy93~"` hardcode trong production

**Khuyến nghị:** Tạo constant:
```kotlin
object AppLog {
    const val TAG = if (BuildConfig.DEBUG) "roy93~" else ""
}
```

---

### L-03: SharedPreferences và DataStore tồn tại song song — không nhất quán

App dùng cả `DataStore` (PreferencesManager) lẫn `SharedPreferences` (LocaleHelper, StreakManager, BadgeManager) cho các preference khác nhau. Không phải lỗi bảo mật nhưng gây khó maintain và tăng attack surface.

---

### L-04: `exportSchema = false` trong Room

```kotlin
@Database(exportSchema = false)
```

Tốt cho production (không lộ schema), nhưng sẽ khó debug migration sau này. Cân nhắc bật lại cho debug build.

---

## ✅ Điểm tốt đã có

| Hạng mục | Trạng thái |
|----------|------------|
| Signing credentials → `keystore.properties` | ✅ Đã fix |
| Release build: `isMinifyEnabled`, `isShrinkResources`, `isDebuggable=false` | ✅ Tốt |
| Tất cả Activity không cần thiết đều `exported=false` | ✅ Tốt |
| FileProvider `exported=false` | ✅ Tốt |
| `minSdk = 24` — tránh các lỗ hổng Android cũ | ✅ Tốt |
| Room dùng `applicationContext` — tránh memory leak | ✅ Tốt |
| DataStore thay SharedPreferences cho core settings | ✅ Tốt |

---

## 🎯 Ưu tiên fix

```
Tuần này:   H-01 (Log dữ liệu sức khỏe) + H-03 (MainAct exported)
Tuần sau:   H-02 (Backup rules) + M-04 (ProGuard rules)
Sprint sau: M-01 (DB encryption) + M-02 (FileProvider path) + L-01 (https)
```
