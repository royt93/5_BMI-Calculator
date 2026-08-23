# EPIC-02 — Security hardening (leftover items từ doc/Security.md) 🐛 (P1/P2, 8 pts)

> C-01/H-01/H-02/H-03 đã fix — không lặp lại ở đây. Chỉ còn Medium/Low.

## T02.1 — M-04 ProGuard rules rỗng (P1, 3 pts)
**File**: `app/proguard-rules.pro` (0 byte)
**Rủi ro**: release build minify có thể strip/rename sai Room entities, DataBinding, AppLovin/AdMob reflection class → crash chỉ xảy ra ở `assembleProductionRelease`, khó phát hiện qua `assembleDevDebug`.
**Fix**: thêm `-keep` rules cho `com.applovin.**`, `com.samsunggalaxy.data.**` (Room), `com.google.android.gms.ads.**`, `com.samsunggalaxy.databinding.**`.
**AC**: build `assembleProductionRelease` thành công + smoke test app không crash khi mở History/VIP/Ads sau minify.

## T02.2 — M-02 FileProvider path quá rộng (P2, 2 pts)
**File**: `res/xml/file_path.xml` — `<external-path name="external_files" path="." />`
**Fix**: giới hạn `path="Android/data/com.samsunggalaxy.bmicalculator/files/"`.
**Lưu ý**: kiểm tra tính năng share/export ảnh hiện dùng FileProvider path nào trước khi siết, tránh vỡ flow chia sẻ.

## T02.3 — M-03 WRITE_EXTERNAL_STORAGE + tools:ignore="ScopedStorage" (P2, 1 pt)
**File**: `AndroidManifest.xml`
**Quyết định cần user**: permission này có đang được dùng thật (export ảnh/PDF) không? Nếu không có tính năng nào ghi ra external storage → xoá permission luôn (giảm attack surface + tránh Google Play từ chối). Nếu N4 (CSV/PDF export, xem EPIC-08) được chọn làm → giữ nhưng chuyển sang MediaStore API, bỏ `tools:ignore`.
**Liên quan**: `AndroidManifest.xml` cũng còn `READ_EXTERNAL_STORAGE maxSdkVersion="32"` (phát hiện thêm ngoài doc/Security.md gốc) — cùng loại quyết định.

## T02.4 — M-01 Room DB không encryption (P2, để backlog — không block release)
**File**: `data/AppDatabase.kt`
**Đánh giá**: dữ liệu BMI/weight là sức khỏe nhạy cảm nhưng app không có login/cloud — rủi ro chủ yếu là thiết bị root/adb backup (đã giảm nhờ H-02 fix). SQLCipher thêm ~1-2MB APK + phức tạp migration. **Khuyến nghị: hoãn**, chỉ làm nếu roadmap thêm cloud sync (EPIC-09).

## T02.5 — L-01, L-02, L-03, L-04 gộp dọn dẹp nhỏ (P3, 2 pts)
- L-01: `http://` → `https://` trong `ext/Activity.kt:173,259`.
- L-02: tạo `object AppLog { const val TAG = if (BuildConfig.DEBUG) "roy93~" else "" }` thay hardcode `"roy93~"` rải rác.
- L-03: SharedPreferences + DataStore song song (StreakManager, BadgeManager dùng SharedPrefs; settings dùng DataStore) — không bug nhưng nên note convention rõ trong CLAUDE.md: "gamification = SharedPrefs, app settings = DataStore" (đã đúng thực tế, chỉ cần document, không cần migrate).
- L-04: `exportSchema = false` — cân nhắc bật `true` cho debug build variant để dễ debug migration sau này.
