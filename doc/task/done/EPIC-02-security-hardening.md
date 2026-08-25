# EPIC-02 — Security hardening (leftover items từ doc/Security.md) 🐛 (P1/P2, 8 pts)

> **Status (2026-08-25): ĐÃ XONG — T02.1/T02.2/T02.3 hoá ra đã fix từ trước (audit lại xác nhận), T02.4 hoãn theo đúng khuyến nghị gốc, T02.5 (L-01/L-02) fixed, L-03 đã có trong CLAUDE.md từ trước, L-04 hoãn có lý do. Build + full test suite + smoke test release build pass. Đã push.**

## T02.1 — M-04 ProGuard rules rỗng — ĐÃ FIX TỪ TRƯỚC (audit xác nhận lại)
`app/proguard-rules.pro` thực tế đã có 3483 byte, không rỗng — chứa `-keep` cho `com.applovin.**`, `com.google.android.gms.ads.**`, `com.samsunggalaxy.data.**` (Room), `com.samsunggalaxy.databinding.**`, `com.roy.sdkadbmob.**`, Kotlin Coroutines, MPAndroidChart, Navigation Component, enum, Parcelable, Serializable, và riêng `FVipManagement` fragment-factory keep rule. Mô tả "0 byte" trong doc gốc đã lỗi thời từ trước session này.

**AC verify**: chạy thật `./gradlew assembleProductionRelease` với keystore riêng (`myKeyStore/com.samsunggalaxy.bmicalculator/`) — BUILD SUCCESSFUL, R8 minify chạy sạch. Cài APK release lên `emulator-5554`, smoke test tay: Splash (App Open ad slot) → chọn ngôn ngữ → MainAct (banner AppLovin MAX hiện đúng) → History (Room DAO + DataBinding + MPAndroidChart tab load OK, empty state đúng) → VIP Membership (`FVipManagement` fragment instantiate qua FragmentFactory reflection — đúng rule `-keep` đã có). Không crash, không FATAL exception trong logcat ở bất kỳ bước nào.

## T02.2 — M-02 FileProvider path quá rộng — ĐÃ FIX TỪ TRƯỚC
`res/xml/file_path.xml` đã dùng `path="Android/data/com.samsunggalaxy.bmicalculator/files/"`, không phải `path="."` như doc gốc mô tả. Khớp đúng cách `CsvExporter.kt` (EPIC-08) dùng `getExternalFilesDir(null)`.

## T02.3 — M-03 WRITE_EXTERNAL_STORAGE — ĐÃ RESOLVED BY DESIGN
`AndroidManifest.xml` không có `WRITE_EXTERNAL_STORAGE`/`READ_EXTERNAL_STORAGE` — chỉ `INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS`, `AD_ID`. CSV export (EPIC-08) dùng scoped storage + FileProvider từ đầu, không cần permission rộng — đúng hướng resolution doc gốc đề xuất.

## T02.4 — M-01 Room DB không encryption — HOÃN (giữ nguyên khuyến nghị gốc)
Không làm. Lý do trong doc gốc vẫn đúng: app không login/cloud, rủi ro chủ yếu thiết bị root/adb backup (đã giảm nhờ H-02), SQLCipher tốn ~1-2MB APK + phức tạp migration. Chỉ nên làm khi có cloud sync (EPIC-09).

## T02.5 — L-01 dọn `http://` dead code — FIXED
`ext/Activity.kt:248` có 1 dòng comment chết `// playYoutube(activity, "http://www.youtube.com/watch?v=Hxy8BZGQ5Jo");` — không phải code chạy thật (dòng 173/259 trong doc gốc đã lỗi thời, không còn khớp). Grep toàn bộ `app/src/main/java` + `res` xác nhận mọi `http://` khác chỉ là XML namespace (`xmlns:android="http://schemas.android.com/apk/res/android"`), không phải network URL cần đổi `https://`. Đã xoá dòng comment chết.

## T02.5 — L-02 gộp `AppLog` — FIXED
Tạo `app/src/main/java/com/samsunggalaxy/utils/AppLog.kt`:
```kotlin
object AppLog {
    private const val TAG = "roy93~"
    fun d(message: String) { if (BuildConfig.DEBUG) Log.d(TAG, message) }
    fun w(message: String, throwable: Throwable? = null) { if (BuildConfig.DEBUG) Log.w(TAG, message, throwable) }
}
```
Thay thế 24 call site dùng tag `"roy93~"` gated `if (BuildConfig.DEBUG) Log.d/w(...)` ở `GalaxyApp.kt`, `ResultAct.kt`, `StreakManager.kt`, `SplashAct.kt`, `RecordSaveHelper.kt`, `MainAct.kt`, `HistoryActivity.kt` → `AppLog.d(...)`/`AppLog.w(...)`.
**Giữ nguyên, không đổi**: `Log.e("roy93~", ...)` (error diagnostics chung, không phải PII, không cần gate debug — đúng convention cũ) và các tag riêng biệt dùng để lọc log theo component (`"roy93~Err"` trong `GalaxyApp`'s `ErrorReporter`, `"BaseActivity"`, `"MainAct"`) — những tag này cố ý khác `"roy93~"` để filter riêng, không gộp vào `AppLog`.
Audit (`/code-review high`) phát hiện 3 import thừa (`android.util.Log`/`BuildConfig` không dùng nữa) sau khi thay thế ở `SplashAct.kt`, `HistoryActivity.kt` — đã xoá.

## L-03 SharedPreferences/DataStore song song — ĐÃ DOCUMENT TỪ TRƯỚC
CLAUDE.md's mục "### Persistence" đã ghi rõ convention (Room = structured data, DataStore = typed settings, SharedPreferences = chỉ gamification + locale) — không cần làm thêm gì. Đã cập nhật CLAUDE.md's dòng logging convention để phản ánh `AppLog` mới (mục Conventions).

## L-04 `exportSchema = false` — HOÃN
`AppDatabase.kt:13` giữ nguyên `exportSchema = false` cho cả debug/release. Bật `true` riêng cho debug variant cần flavor-specific source set cho 1 annotation compile-time value — chi phí/lợi ích không tương xứng so với lợi ích (chỉ giúp debug migration, không phải bug). Hoãn giống T02.4.

## Test & verify
- Unit test: `testDevDebugUnitTest` — 61/61 pass (0 fail).
- Instrumented test: 49/49 pass trên `emulator-5554` (network tắt lúc chạy để tránh ad che UI, bật lại sau).
- Build: `assembleDevDebug`, `compileDevDebugKotlin` sạch (chỉ warning deprecated cũ, không liên quan diff này); `assembleProductionRelease` thành công với R8 minify.
- Smoke test release build: xem T02.1 ở trên.
- Audit `/code-review high`: 1 lượt, 3 finding (import thừa) — cả 3 đã fix, rebuild + unit test lại xanh.
