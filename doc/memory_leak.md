# Memory Leak & Bug Report — BMI Calculator

> Cập nhật: 2026-03-18 | Phân tích toàn bộ 30 file Kotlin

---

## 🔴 MEMORY LEAK

### ML-01 · `SplashAct` — Animation vô hạn không bị hủy khi Activity destroy

**File:** `SplashAct.kt` — `animateFloatingCircles()` (dòng 73–117)

```kotlin
circle1.animate()
    .withEndAction { animateFloatingCircles() } // ← đệ quy vô tận
```

**Vấn đề:** Ba vòng tròn (circle1, circle2, circle3) được animate theo vòng lặp đệ quy bằng `withEndAction { animateFloatingCircles() }`. Khi Activity bị destroy, các `ViewPropertyAnimator` này **không bị hủy**, dẫn đến:
- Animator tiếp tục chạy sau khi Activity đã destroy.
- Callback `withEndAction` giữ tham chiếu đến View → View giữ tham chiếu đến Activity → **memory leak**.

**Fix:**
```kotlin
override fun onDestroy() {
    circle1.animate().cancel()
    circle2.animate().cancel()
    circle3.animate().cancel()
    handler.removeCallbacksAndMessages(null)
    super.onDestroy()
}
```

---

### ML-02 · `GalaxyApp` — `CoroutineScope` không bị cancel (Application scope)

**File:** `GalaxyApp.kt` — dòng 49 & 67

```kotlin
CoroutineScope(Dispatchers.IO).launch { ... } // ← không có lifecycle
```

**Vấn đề:** Dùng `CoroutineScope(Dispatchers.IO)` đứng thẳng trong `Application.onCreate()` — scope này không bao giờ bị cancel. Nếu có exception, coroutine sẽ chết lặng lẽ. Trong môi trường production nên dùng scope có vòng đời được kiểm soát.

**Fix:** Dùng `GlobalScope` với xử lý exception, hoặc tạo `applicationScope` riêng:
```kotlin
private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
```

---

### ML-03 · `ResultAct` — `CoroutineScope` không được cancel

**File:** `ResultAct.kt` — `saveToHistory()` (dòng 349)

```kotlin
CoroutineScope(Dispatchers.IO).launch { ... }
```

**Vấn đề:** Nếu user quay lại trước khi coroutine hoàn thành, coroutine vẫn chạy với tham chiếu đến `repository` và Activity context. Nên dùng `lifecycleScope.launch(Dispatchers.IO)` thay thế.

**Fix:**
```kotlin
lifecycleScope.launch(Dispatchers.IO) {
    // saveToHistory logic
}
```

---

### ML-04 · `WeightPickerAdt` — Adapter giữ tham chiếu `context` và `RecyclerView`

**File:** `WeightPickerAdt.kt` — dòng 12–16

```kotlin
class WeightPickerAdt(
    private val context: Context,       // ← giữ context
    private var dataList: List<String>,
    private val recyclerView: RecyclerView, // ← giữ RecyclerView
)
```

**Vấn đề:** Adapter giữ trực tiếp reference đến `RecyclerView` (để `smoothScrollToPosition`). RecyclerView → Activity window → Activity. Nếu Adapter bị giữ lâu hơn Activity (ví dụ cache), sẽ dẫn đến leak. Cũng không cần thiết phải lưu `context` vì có thể lấy từ `parent.context` trong `onCreateViewHolder`.

**Fix:**
```kotlin
override fun onBindViewHolder(holder: TextVH, position: Int) {
    holder.pickerTxt.text = dataList[position]
    holder.pickerTxt.setOnClickListener {
        (holder.itemView.parent as? RecyclerView)?.smoothScrollToPosition(position)
    }
}
```

---

### ML-05 · `AdMobManager` — `interstitialListener` là static reference đến Activity

**File:** `AdMobManager.kt` — dòng 79

```kotlin
var interstitialListener: InterstitialAdListener? = null
```

**Vấn đề:** `AdMobManager` là `object` (singleton). `interstitialListener` được gán bằng Activity instance (`ResultAct`). Nếu `onDestroy()` không được gọi (crash, process kill), listener vẫn giữ reference đến Activity → **memory leak qua singleton**.

**Tình trạng hiện tại:** `ResultAct.onDestroy()` có `AdMobManager.interstitialListener = null` — đã xử lý đúng. Tuy nhiên nếu Activity bị kill trước `onDestroy()` thì sẽ leak.

**Cải thiện:** Dùng `WeakReference` cho listener:
```kotlin
private var interstitialListenerRef: WeakReference<InterstitialAdListener>? = null
```

---

### ML-06 · `AdMobManager` — `currentActivity` WeakReference bị ghim trong `loadBanner`

**File:** `AdMobManager.kt` — `loadBanner()` dòng 211

```kotlin
val adView = AdView(context.applicationContext).apply {
    adListener = object : AdListener() { ... }
}
```

**Vấn đề:** `AdListener` anonymous class được tạo mỗi lần `loadBanner()` được gọi. Anonymous class này là inner class của lambda scope — nếu `context` là Activity (không phải applicationContext), sẽ giữ tham chiếu đến Activity. Hiện tại đã dùng `context.applicationContext` → an toàn, nhưng nếu sau này ai đó truyền Activity context thì sẽ leak.

---

## 🟠 BUG

### BUG-01 · `MainAct` — `onBackPressed()` deprecated vẫn còn được gọi

**File:** `MainAct.kt` — dòng 204

```kotlin
_binding.ivBack.setOnClickListener {
    onBackPressed() // ← deprecated từ API 33
}
```

**Vấn đề:** `onBackPressed()` đã deprecated. Dự án đã dùng `OnBackPressedDispatcher` ở phần khác (dòng 82) nhưng `ivBack` vẫn gọi method cũ.

**Fix:**
```kotlin
_binding.ivBack.setOnClickListener {
    onBackPressedDispatcher.onBackPressed()
}
```

---

### BUG-02 · `MainAct` — `getData()` tạo list từ 0 đến 150 (index 0 hiển thị như weight)

**File:** `MainAct.kt` — `getData()` dòng 228–234

```kotlin
for (i in 0 until count) {
    data.add(i.toString()) // ← i = 0, 1, 2, ... 150
}
```

**Vấn đề:** List weight picker bắt đầu từ `0` kg, điều này không hợp lý về mặt sức khỏe. `pickerLayoutManager.scrollToPosition(49)` → weight mặc định = 49 kg (index 49 = "49"). Nếu user scroll về đầu, có thể chọn được 0 kg.

**Fix:** Bắt đầu từ 1 hoặc giá trị tối thiểu hợp lý (ví dụ 10 kg):
```kotlin
for (i in 1..count) {
    data.add(i.toString())
}
```

---

### BUG-03 · `ResultAct` — `bmiCalMale()` và `bmiCalFemale()` cùng công thức

**File:** `ResultAct.kt` — dòng 314–320

```kotlin
private fun bmiCalMale() {
    result = ((weight / (height * height)) * 10000)
}

private fun bmiCalFemale() {
    result = ((weight / (height * height)) * 10000)
}
```

**Vấn đề:** Hai hàm hoàn toàn giống nhau — gender không ảnh hưởng gì đến kết quả BMI. Đây có thể là intentional (vì BMI không phân biệt giới tính) nhưng nếu cần tính Body Fat (vốn phân biệt giới tính) thì đây là bug nghiêm trọng.

**Fix:** Hợp nhất thành một hàm hoặc thêm logic khác biệt giới tính nếu cần.

---

### BUG-04 · `ResultAct` — BMI tính với `height` đơn vị cm nhưng công thức cần mét

**File:** `ResultAct.kt` — dòng 315

```kotlin
result = ((weight / (height * height)) * 10000)
```

**Vấn đề:** Công thức BMI chuẩn: `BMI = weight(kg) / height(m)²`. Ở đây `height` được nhận từ Intent với đơn vị **cm** (ví dụ 160), nên nhân thêm `10000` để bù (`160cm → 1.6m => 1.6² = 2.56, 160² = 25600, 25600/10000 = 2.56`). Tuy nhiên cách này dễ gây nhầm lẫn khi đọc code. Nên convert đơn vị tường minh.

**Fix (tường minh hơn):**
```kotlin
private fun bmiCal() {
    val heightInMeters = height / 100.0
    result = weight / (heightInMeters * heightInMeters)
}
```

---

### BUG-05 · `HistoryActivity` — `HistoryAdapter` dùng `notifyDataSetChanged()` thay vì DiffUtil

**File:** `HistoryActivity.kt` — dòng 185

```kotlin
fun submitList(newRecords: List<BmiRecord>) {
    records = newRecords
    notifyDataSetChanged() // ← kém hiệu quả, không có animation
}
```

**Vấn đề:** `notifyDataSetChanged()` update toàn bộ RecyclerView mỗi khi list thay đổi — không có animation xóa/thêm item, waste render. Nên dùng `ListAdapter` với `DiffUtil.ItemCallback`.

---

### BUG-06 · `HistoryActivity` — Crash tiềm ẩn khi swipe nhanh

**File:** `HistoryActivity.kt` — dòng 170–172

```kotlin
override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
    val position = viewHolder.adapterPosition
    val record = adapter.getRecordAt(position) // ← có thể IndexOutOfBoundsException
```

**Vấn đề:** `adapterPosition` có thể trả về `RecyclerView.NO_ID` (-1) nếu item đang được animate. Gọi `getRecordAt(-1)` sẽ throw `IndexOutOfBoundsException`.

**Fix:**
```kotlin
override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
    val position = viewHolder.adapterPosition
    if (position == RecyclerView.NO_ID) return
    val record = adapter.getRecordAt(position)
    showDeleteConfirmDialog(record)
}
```

---

### BUG-07 · `AdMobManager` — `initSplashScreen` dùng `collectLatest` trong `CoroutineScope(Dispatchers.Default)` không hủy được

**File:** `AdMobManager.kt` — dòng 514–536

```kotlin
CoroutineScope(Dispatchers.Default).launch {
    EventBus.eventFlow.collectLatest { value ->
        CoroutineScope(Dispatchers.Main).launch {
            loadAppOpenAd(...)
        }
    }
}
```

**Vấn đề:**
1. `CoroutineScope` không có lifecycle → không bao giờ bị cancel.
2. `collectLatest` sẽ giữ scope sống mãi chờ event tiếp theo → **coroutine leak**.
3. Nếu `EventBus.sendEvent()` bị gọi nhiều lần (rotation, multi-process), `loadAppOpenAd` sẽ được gọi nhiều lần.

---

### BUG-08 · `AdMobManager.getGAID()` — Dùng raw `Thread` thay vì Coroutine

**File:** `AdMobManager.kt` — dòng 154–165

```kotlin
fun getGAID(context: Context, callback: (String) -> Unit) {
    Thread {
        try { ... callback(id) }
        catch (e: Exception) { callback("") }
    }.start()
}
```

**Vấn đề:** Thread raw không có lifecycle management. Nếu context bị destroy trước khi Thread hoàn thành, callback vẫn được gọi với context đã invalid. Nên dùng Coroutine với `Dispatchers.IO`.

---

### BUG-09 · `BaseActivity.enableAdaptiveRefreshRate()` — gọi trong `onResume()` mỗi lần resume

**File:** `BaseActivity.kt` — dòng 34–40

```kotlin
override fun onResume() {
    super.onResume()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        enableAdaptiveRefreshRate() // ← gọi mỗi lần resume
    }
}
```

**Vấn đề:** `window.attributes` được set lại mỗi lần Activity resume — điều này không cần thiết và có thể gây flicker hoặc overhead không đáng. Nên chỉ cần gọi một lần trong `onCreate()`.

---

### BUG-10 · `SettingsActivity.restartApp()` — dùng `Handler` không được release

**File:** `SettingsActivity.kt` — dòng 74

```kotlin
android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
    // restart logic
    Process.killProcess(Process.myPid())
    exitProcess(0)
}, 200)
```

**Vấn đề:** Handler được tạo inline và không được lưu tham chiếu để cancel. Mặc dù sau đó process sẽ bị kill nên ít gây vấn đề thực tế, nhưng là code pattern xấu. Ngoài ra `exitProcess(0)` sau `killProcess()` là dư thừa.

---

### BUG-11 · `MainAct` — `startActivityForResult` deprecated

**File:** `MainAct.kt` — dòng 67

```kotlin
startActivityForResult(intent, REQUEST_CODE) // ← deprecated từ API 30
```

**Vấn đề:** `startActivityForResult` đã deprecated. Nên dùng `ActivityResultLauncher` (Activity Result API).

**Fix:**
```kotlin
private val resultLauncher = registerForActivityResult(
    ActivityResultContracts.StartActivityForResult()
) { result ->
    if (result.resultCode == RESULT_OK) {
        val shouldReset = result.data?.getBooleanExtra(REQUEST_RESULT, false)
        if (shouldReset == true) {
            animationView()
            _binding.startButton.alpha = 1f
        }
    }
}
// Thay thế startActivityForResult:
resultLauncher.launch(intent)
```

---

### BUG-12 · `ResultAct` — `println()` dùng trong production code

**File:** `BaseActivity.kt` — dòng 59

```kotlin
println("Adaptive refresh rate applied: ${highestRefreshRateMode.refreshRate} Hz")
```

**Vấn đề:** `println()` ghi ra `System.out` — không phải Logcat. Không thể filter theo tag, không bị tắt trong release build. Nên dùng `Log.d(TAG, ...)`.

---

### BUG-13 · `FileExt.kt` — `DEFAULT_FILENAME` là val top-level bị khởi tạo 1 lần khi load class

**File:** `FileExt.kt` — dòng 12

```kotlin
@JvmField
val DEFAULT_FILENAME = "BMI Calculator " + System.currentTimeMillis()
```

**Vấn đề:** `System.currentTimeMillis()` được gọi **một lần duy nhất** khi class được load, không phải mỗi khi `saveBitmap()` được gọi. Nếu user share nhiều lần trong một session, tất cả ảnh sẽ có cùng tên → ghi đè lên nhau.

**Fix:**
```kotlin
fun saveBitmap(
    activity: Activity,
    bitmap: Bitmap,
    filename: String = "BMI Calculator ${System.currentTimeMillis()}",
): Uri? { ... }
```

---

### BUG-14 · `AdMobManager.loadBanner()` — `tvLabelAd` bị ẩn khi load nhưng không bao giờ hiện lại

**File:** `AdMobManager.kt` — dòng 210

```kotlin
tvLabelAd.isVisible = false // ← bị ẩn
// ... onAdLoaded() không set tvLabelAd.isVisible = true
```

**Vấn đề:** `tvLabelAd` (nhãn "Ad") được ẩn khi bắt đầu load nhưng không bao giờ được set `isVisible = true` trong `onAdLoaded()`. Người dùng sẽ không nhìn thấy nhãn "Ad" dù banner đã load thành công.

---

## 🟡 CODE SMELL / CẢNH BÁO

### CS-01 · Nhiều Activity dùng `lateinit var` cho `binding`

**Files:** `MainAct.kt`, `ResultAct.kt`

```kotlin
private lateinit var binding: AMainBinding
private val _binding get() = binding // ← vô nghĩa nếu không null-check
```

**Vấn đề:** `private val _binding get() = binding` không null-safe hơn gì so với dùng `binding` trực tiếp. Pattern này thường dùng với Fragment (để set null trong `onDestroyView()`). Trong Activity, chỉ cần dùng `binding` trực tiếp.

---

### CS-02 · `PreferencesManager` không được dùng ở bất kỳ đâu

**File:** `PreferencesManager.kt`

**Vấn đề:** Class này định nghĩa các key như `THEME_MODE`, `UNIT_SYSTEM`, `LANGUAGE`, `ACTIVITY_LEVEL` nhưng không có Activity nào inject hoặc sử dụng nó. Cài đặt ngôn ngữ được thực hiện qua `LocaleHelper` với `SharedPreferences` riêng. **Dead code**.

---

### CS-03 · `HistoryAdapter` là inner class của file nhưng không dùng `DiffUtil`

**File:** `HistoryActivity.kt` — dòng 177+

**Vấn đề:** Nên tách `HistoryAdapter` ra file riêng và implement `ListAdapter<BmiRecord, ...>` với `DiffUtil.ItemCallback` để có hiệu năng tốt hơn.

---

### CS-04 · `AdMobManager.countInitSplashScreen` là biến global mutable gây race condition

**File:** `AdMobManager.kt` — dòng 506

```kotlin
var countInitSplashScreen = 0
```

**Vấn đề:** Không có `@Volatile` hay synchronization. Trong môi trường multi-thread (rotation nhanh, multi-process), giá trị có thể không nhất quán.

**Fix:** Dùng `AtomicInteger`:
```kotlin
private val countInitSplashScreen = AtomicInteger(0)
```

---

## 📊 Tổng kết

| Mức độ | Số lượng | Mô tả |
|--------|----------|-------|
| 🔴 Memory Leak | 6 | ML-01 → ML-06 |
| 🟠 Bug | 9 | BUG-01 → BUG-09 |
| 🟡 Code Smell | 4 | CS-01 → CS-04 |
| **Tổng** | **19** | |

### Ưu tiên xử lý

1. **Ngay lập tức:** ML-01 (animation loop), BUG-06 (crash when swipe), BUG-13 (filename collision)
2. **Quan trọng:** ML-03, ML-04, BUG-03, BUG-05 (DiffUtil)
3. **Cải thiện dần:** BUG-01, BUG-11 (deprecated API), CS-02 (dead code)
