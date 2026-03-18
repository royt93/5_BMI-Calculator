# Fix Report — Memory Leak & Bug (BMI Calculator)

> Ngày: 2026-03-18 | Đã fix: **19 vấn đề** trên **9 file**

---

## Tổng hợp thay đổi

### 🔴 Memory Leak đã fix

| ID | File | Fix |
|----|------|-----|
| ML-01 | `SplashAct.kt` | Lưu `circle1/2/3` thành field, cancel animation trong `onDestroy()`, guard `if (!isDestroyed)` trong `withEndAction` |
| ML-02 | `GalaxyApp.kt` | Tạo `applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)` thay thế các `CoroutineScope` inline |
| ML-03 | `ResultAct.kt` | Thay `CoroutineScope(IO).launch` bằng `lifecycleScope.launch(IO)` trong `saveToHistory()` |
| ML-04 | `WeightPickerAdt.kt` | Bỏ tham số `recyclerView` khỏi constructor; resolve từ `holder.itemView.parent as RecyclerView` trong `onBindViewHolder` |
| ML-05 | `AdMobManager.kt` | Đổi `interstitialListener` thành property backing bằng `WeakReference<InterstitialAdListener>` |
| ML-06 | `AdMobManager.kt` | Thay `collectLatest` vô hạn bằng `eventFlow.first()` (collect 1 lần rồi tự cancel); cancel job cũ trước khi tạo mới |

---

### 🟠 Bug đã fix

| ID | File | Fix |
|----|------|-----|
| BUG-01 | `MainAct.kt` | `ivBack.setOnClickListener` dùng `onBackPressedDispatcher.onBackPressed()` thay deprecated `onBackPressed()` |
| BUG-02 | `MainAct.kt` | `getData()` vòng lặp `1..count` thay vì `0 until count` — weight bắt đầu từ 1 kg |
| BUG-03 | `ResultAct.kt` | Xóa `bmiCalMale()` và `bmiCalFemale()` trùng lặp, thay bằng `bmiCal()` duy nhất |
| BUG-04 | `ResultAct.kt` | Refactor công thức BMI: `val heightInMeters = height / 100.0; result = weight / (heightInMeters * heightInMeters)` |
| BUG-05 | `HistoryActivity.kt` | Chuyển `HistoryAdapter` sang `ListAdapter<BmiRecord>` + `DiffUtil.ItemCallback` |
| BUG-06 | `HistoryActivity.kt` | Guard trong `SwipeToDeleteCallback.onSwiped()`: `if (position < 0) return` tránh `IndexOutOfBoundsException` |
| BUG-07 | `AdMobManager.kt` | Đổi `var countInitSplashScreen = 0` thành `AtomicInteger(0)` để thread-safe |
| BUG-08 | `AdMobManager.kt` | Đổi `getGAID()` từ raw `Thread { }.start()` sang `CoroutineScope(IO + SupervisorJob()).launch { }` |
| BUG-09 | `FileExt.kt` | Xóa `DEFAULT_FILENAME` top-level; timestamp được tính mỗi lần gọi qua default parameter của `saveBitmap()` |
| BUG-11 | `MainAct.kt` | Thay `startActivityForResult` deprecated bằng `ActivityResultLauncher`; xóa `onActivityResult()` override |

---

### 🟡 Code Smell đã fix

| ID | File | Fix |
|----|------|-----|
| CS-02 | `BaseActivity.kt` | Chuyển `enableAdaptiveRefreshRate()` từ `onResume()` vào `onCreate()` — chỉ set 1 lần |
| CS-02 | `BaseActivity.kt` | Đổi `println(...)` thành `Log.d("BaseActivity", ...)` |
| ML-04 update | `MainAct.kt` | Bỏ tham số `_binding.weightRecyclerBtn` khỏi `WeightPickerAdt()` call site |

---

## Diff theo file

render_diffs(file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/1115BMI-Calculator/app/src/main/java/com/samsunggalaxy/ui/SplashAct.kt)

render_diffs(file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/1115BMI-Calculator/app/src/main/java/com/samsunggalaxy/GalaxyApp.kt)

render_diffs(file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/1115BMI-Calculator/app/src/main/java/com/samsunggalaxy/ui/ResultAct.kt)

render_diffs(file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/1115BMI-Calculator/app/src/main/java/com/samsunggalaxy/ui/MainAct.kt)

render_diffs(file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/1115BMI-Calculator/app/src/main/java/com/samsunggalaxy/ui/HistoryActivity.kt)

render_diffs(file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/1115BMI-Calculator/app/src/main/java/com/samsunggalaxy/adt/WeightPickerAdt.kt)

render_diffs(file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/1115BMI-Calculator/app/src/main/java/com/samsunggalaxy/sdkadbmob/AdMobManager.kt)

render_diffs(file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/1115BMI-Calculator/app/src/main/java/com/samsunggalaxy/ext/FileExt.kt)

render_diffs(file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/1115BMI-Calculator/app/src/main/java/com/samsunggalaxy/BaseActivity.kt)

---

## Vấn đề còn lại chưa fix (CS-01, CS-03)

| ID | Lý do giữ nguyên |
|----|-----------------|
| CS-01 (`_binding` alias) | Vô hại về memory, chỉ là style — không ảnh hưởng runtime |
| CS-03 (`PreferencesManager` dead code) | Là dead code nhưng có thể dùng trong tương lai; không ảnh hưởng runtime |
