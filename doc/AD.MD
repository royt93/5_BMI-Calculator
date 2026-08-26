# 📊 KẾ HOẠCH TÍCH HỢP QUẢNG CÁO — BMI Calculator 2026

> **SDK đích:** `com.github.royt93:AdmobApplovinWrapper:1.6.21` | **Provider runtime:** AdMob (`IS_ENABLE_ADMOB=true`, đổi 2026-08-26) | **Cập nhật:** 2026-08-26

---

## 🔀 Đổi provider AppLovin → AdMob (2026-08-26, quyết định user sau round review độc lập)

User quyết định chuyển hẳn provider chính sang AdMob (cả debug + release) sau khi đọc kết quả 3-agent
review ở trên. Quyết định user, không phải AI tự đổi (đúng theo rule "Cấm tự ý đổi IS_ENABLE_ADMOB").

**Đã cấp / xác nhận lúc quyết định:**
- Debug: dùng nguyên Google test ID (chủ ý, không phải thiếu sót — QC không cần đăng ký test device).
- Release: banner/interstitial/app-open đã có ID thật (publisher `3612191981543807`) từ trước.
- Release rewarded: **user xác nhận sẽ cấp ID thật sau** — hiện `app/build.gradle.kts` còn để Google test
  ID kèm comment 🚨 BLOCKER, không được build/ship `assembleProductionRelease` trước khi thay.
- User xác nhận UMP consent form **đã Publish** trên AdMob Console.

**Smoke test thật trên device (Samsung A50s, `devDebug`, `IS_ENABLE_ADMOB=true`):**
- `initialize()` → `provider=AdMob` thật (không phải AppLovin) — log xác nhận `AdMob ready`,
  `DynamiteModule` load `com.google.android.gms.ads.dynamite`.
- Cả 4 format load được qua AdMob SDK thật: Interstitial ✅ Loaded, Banner ✅ Loaded + Impression,
  Rewarded ✅ Loaded, App Open ✅ Loaded. Không crash.
- Banner hiện đúng nhãn "Quảng cáo thử nghiệm" (test ad) — test-device hash tự động registered qua
  whitelist QA hard-code của SDK (log: "AdMob — QC test device hashes registered (debug=true)").
- `paidEventListener` bắn đúng cho AdMob: `AdRevenue: banner 0.0 USD UNKNOWN
  com.google.ads.mediation.admob.AdMobAdapter` — xác nhận wiring provider-agnostic hoạt động thật.
- `admobRewardedInterstitialId` rỗng → log warning "rewarded interstitial LUÔN no-fill" — đúng như spec
  (field này app không dùng, không phải lỗi).

**⚠️ Phát hiện quan trọng — mâu thuẫn với xác nhận của user:**
Log VẪN ra đúng lỗi `Publisher misconfiguration: ... no form(s) configured for the input app ID.
Received app ID: ca-app-pub-3612191981543807~1273576268` dù user xác nhận đã Publish UMP form. Không
chặn ad (SDK fail-open → `canRequestAds=true` khi fetch lỗi), nhưng nghĩa là **consent UI thật cho user
EEA/UK/CH chưa hoạt động** — form trên AdMob Console có thể chưa đúng khớp app ID này, hoặc chưa
propagate xong (Google đôi khi mất vài giờ sau Publish). **Cần user tự vào lại AdMob Console → Privacy &
messaging, xác nhận đúng app `ca-app-pub-3612191981543807~1273576268` có form Published, trước khi tin
consent EEA đã sẵn sàng cho production.**

## 🔀 Round 2 — user cấp full config AdMob account MỚI (2026-08-26, doc/admob/admob.MD)

User cấp `doc/admob/admob.MD` + screenshot AdMob Console — hoá ra là **account/app AdMob khác hẳn**:
publisher `3004713799155145` (App ID `ca-app-pub-3004713799155145~2001950526`), không phải publisher
`3612191981543807` đã dùng ở round 1. Đã audit + thay toàn bộ:

| Field | Cũ (publisher 3612191981543807) | Mới (publisher 3004713799155145) |
|---|---|---|
| App ID (manifest) | `~1273576268` | `~2001950526` |
| Banner | `/9117482667` | `/9886405901` |
| Interstitial | `/4216509777` | `/2805614932` |
| App Open | `/5066557013` | `/7978050569` |
| Rewarded | Google test ID (blocker) | `/9179451599` — **ID thật, blocker đã hết** |

- Package name (`com.samsunggalaxy.bmicalculator`) và privacy policy URL trong `admob.MD` khớp đúng
  100% với `applicationId` + `BuildConfig.PRIVACY_POLICY_URL` hiện có — không cần đổi gì thêm.
- `admob.MD` có thêm ID **native** (`/2800026878`) — **hiện app KHÔNG dùng native ad** (SDK
  `AdmobApplovinWrapper` không có API native trong Phụ lục A đã research), ID này chưa wire vào đâu, để
  dành nếu tương lai thêm native ad slot.
- Build lại `assembleDevDebug` + `assembleProductionRelease` — xanh cả 2 sau khi đổi ID.
- Smoke test lại trên device thật — **đổi device**: Samsung A50s trước đó đã ngắt kết nối, chỉ còn
  **Samsung A11 (SM-A115F, R9JN61LDLFJ)**, dùng máy này (đã báo user, chỉ 1 device nên không hỏi thêm).
  Cold start không crash, `provider=AdMob` init thành công với App ID mới, debug vẫn dùng đúng Google
  test ad-unit ID (không đổi debug ID — chủ ý, tránh invalid traffic khi QA UI tự động) — Interstitial/
  Rewarded/App Open đều `✅ Loaded`.
- **Xác nhận lại lỗi UMP xảy ra với đúng App ID MỚI** (`ca-app-pub-3004713799155145~2001950526`) —
  không phải cache/data cũ. Kết luận: form UMP mà user Publish ở round 1 là cho **account AdMob CŨ**
  (`3612191981543807`); account MỚI (`3004713799155145`, account thực sự đang dùng bây giờ) **CHƯA có
  form nào** → **blocker mới, chưa release được cho EEA/UK/CH** cho tới khi user tạo + Publish UMP form
  đúng cho app ID `ca-app-pub-3004713799155145~2001950526` trên AdMob Console.
- **Chưa live-test 4 ad-unit ID thật mới** (chỉ verify đúng format `ca-app-pub-.../<10-digit>`, khớp
  publisher, chưa thấy request thật tới các ID đó) — cố ý không flip debug sang ID thật để tránh risk
  invalid-traffic khi test bằng UI automation. Muốn verify thật: đăng ký device này làm AdMob test
  device (Bước 3b, `AdManager.copyTestDeviceHashToClipboard`) rồi build tạm 1 bản trỏ debug sang 4 ID
  thật, hoặc dùng Play Console Internal Testing track.

**Còn lại trước khi release thật:**
1. ✅ ~~Cấp `ADMOB_REWARDED_ID` production thật~~ — đã xong (round 2).
2. 🚨 **Tạo + Publish UMP consent form cho app `ca-app-pub-3004713799155145~2001950526`** trên AdMob
   Console (Privacy & messaging) — form cũ publish cho account khác không có tác dụng cho app này.
3. Live-verify 4 ad-unit ID thật (banner/interstitial/appopen/rewarded publisher `3004713799155145`) —
   chưa test thật, chỉ verify đúng format/wiring.
4. Chưa test flow VIP/redeem/interstitial fallback lại với `IS_ENABLE_ADMOB=true` — logic không phụ
   thuộc provider (đều qua `AdManager` abstraction) nên rủi ro thấp, nhưng chưa click-through riêng.

---

# 🆕 MIGRATE 1.1.3 → 1.6.21 (2026-08-26 — ✅ Implemented + smoke-tested)

> Nguồn: `doc/AD_PROMPT_AOS.MD` (Section 0 + Step 1–7, phần AI-migration). Tag `1.6.21` — mới nhất trên
> JitPack lúc migrate (`gh api repos/royt93/AdmobApplovinWrapper/tags`), CI `build-and-test` xanh.
>
> **Kết quả smoke test thật trên Samsung A50s (SM-A507FN, Android 11), build `devDebug`:**
> - Cold start: init/consent/AppLovin provider init — không crash, log sạch (`getDiagnostics` path).
> - `paidEventListener` bắn đúng (`AdRevenue: rewarded 0.0 USD ESTIMATED AppLovin`) — xác nhận wiring
>   Bước 2 hoạt động thật, không chỉ compile được.
> - VIP token ECDSA — full vòng đời verify thật trên device: mint token qua
>   `AdManager.generateVipToken` (instrumented test, key riêng đã sinh) → `activateVipByToken` trả
>   `true` → dialog "Đã kích hoạt VIP 7 ngày" → active card hiện đúng countdown/expiry → `clearVipByKey`
>   qua nút "Thu hồi VIP" → quay về Free state đúng. Không dùng `activateVipByKey`/`VipKeys.lookupDays`
>   nào còn sót lại.
> - Rewarded ad request/show qua `showRewarded` hoạt động qua SDK thật (không mock) — AdSafety counter
>   tăng đúng (`hourly=1/999`, `daily=1/9999`). **Không verify được nhánh `earned=true` → `grantVipDays`**
>   vì test-ad creative của AppLovin trên máy này không render nút đóng sau >2 phút chờ (quirk môi
>   trường test ads, không phải lỗi code — cùng hiện tượng lặp lại ở App Open ad sau đó). `grantVipDays`
>   là 1 lời gọi API đơn giản khớp đúng chữ ký SDK đã verify qua javap, rủi ro thấp — **khuyến nghị QA
>   xác nhận thủ công nhánh earned=true khi có network/device khác** trước khi release.
> - Build `assembleDevDebug` + `assembleProductionRelease` xanh cả hai — `vipKeySecret` hiện tại
>   (không phải default mẫu) nên release không crash ở `setConfig()`.
>
> Build variant khác/flow khác (banner/interstitial ở MainAct+ResultAct, App Open resume) — không
> đổi code, kế thừa hành vi cũ, không smoke lại riêng.

## 🔍 Independent review round (2026-08-26, sau khi user hỏi lại "sao không dùng AdMob")

User đặt câu hỏi tại sao dùng AppLovin thay vì AdMob. Fact-check: `IS_ENABLE_ADMOB=false` là quyết định
từ 1 commit CŨ ("Introduced IS_ENABLE_ADMOB (set to false) to prioritize AppLovin MAX"), không phải đổi
trong lần migrate hôm nay — và `AD_PROMPT_AOS.MD` cấm rõ AI tự đổi flag này. Đã chạy 3 agent AI độc lập
(`codex exec`, `claude` phiên thứ 2, `gemini`/`agy`, mỗi agent không thấy context của nhau) review lại
câu hỏi provider + soát diff migrate. Kết quả:

- **Provider AppLovin vs AdMob**: **KHÔNG đồng thuận** — codex và claude#2 cho rằng giữ AppLovin hợp lý
  (không có bằng chứng AdMob tốt hơn chỉ từ code, đây là quyết định vận hành cần dữ liệu eCPM/fill thật
  để đổi, không phải lỗi kỹ thuật). Gemini cho rằng KHÔNG hợp lý cho app utility (AppLovin MAX tối ưu cho
  gaming/UA traffic dày, thiếu mediation adapter trong dependency tree, utility app traffic mỏng sẽ eCPM
  thấp hơn AdMob). **Kết luận: đây là quyết định business cần A/B test thật, không phải bug — giữ nguyên
  AppLovin theo scope đã duyệt, KHÔNG tự đổi.**
- **Correction 1 quan trọng**: nhận định ban đầu của tôi "AdMob ID 100% còn test ID" là **SAI**. Thực tế
  chỉ `ADMOB_REWARDED_ID` bản release còn là Google test ID (`app/build.gradle.kts` dòng ~82); banner/
  interstitial/app-open release đã là ID thật (publisher `3612191981543807`). Cả 3 agent đồng thuận điểm
  này.
- **2 gap thật trong diff hôm nay** (cả 3 agent hội tụ, đối chiếu đúng `AD_PROMPT_AOS.MD:1726` +
  `:1731`) — **đã fix**:
  1. `FVipManagement`/`VipPrefs` vẫn tự lưu `grantedAtMs` qua `System.currentTimeMillis()` thay vì đọc
     `AdManager.getVipGrantedAtMs()` (SDK 1.6.21 đã expose, spec cấm tự lưu vì sẽ lệch khi VIP cấp qua
     đường khác) → đã đổi `bindUi()` sang đọc thẳng SDK, xoá `saveGrantedAtMs`/`getGrantedAtMs`/
     `clearGrantedAtMs` khỏi `VipPrefs.kt`.
  2. Bản sửa bug fallback ban đầu **xoá hẳn** interstitial thay vì giữ nó ở dạng monetization-only (spec
     dòng 1731: fallback interstitial vẫn phải **show** để kiếm tiền, chỉ KHÔNG được cấp reward) → đã
     khôi phục `AdManager.showInterstitial(activity) { ... }` trong nhánh `earned=false`, callback chỉ
     toast, không gọi bất kỳ hàm cấp VIP nào.
- **1 gap policy pre-existing phát hiện thêm** (không phải lỗi hôm nay, nhưng đúng phạm vi Step 7 rule 2
  của chính spec migrate) — **đã fix**: `AdSdkConfig.appOpenExcludedActivities` chưa từng khai
  `SplashAct::class.java`. Field cũ `appOpenExcludedActivityNames` chỉ fallback theo string
  `"SplashActivity"` — class thật của app là `SplashAct`, không khớp → App Open có thể đè lên splash/
  language-sheet nếu app bị background rồi resume đúng lúc đó. Đã thêm
  `appOpenExcludedActivities = listOf(SplashAct::class.java)` vào `GalaxyApp.kt`.
- **1 gap i18n** (claude#2 phát hiện): string `attribution_ads_label` mới chỉ có ở `values/` mặc định,
  thiếu 15 locale còn lại — vi phạm convention chính project (CLAUDE.md: thêm string mới vào MỌI locale).
  Đã thêm (English fallback, cùng nội dung `values/`) vào cả 15 file `values-*/strings.xml`.
- Build lại `assembleDevDebug` + `assembleProductionRelease` + `lintDevDebug` sau các fix — xanh hết.

**QA round 2 trên device thật (Samsung A50s) sau các fix trên:**
- Mint token mới (5 ngày) qua instrumented test tạm → redeem qua UI → dialog "Đã kích hoạt VIP 5 ngày"
  đúng → active card hiện `Kích hoạt: 26/08/2026 20:45` — **đọc đúng từ `AdManager.getVipGrantedAtMs()`**
  (không còn qua `VipPrefs` đã xoá), countdown/progress render đúng.
- "Thu hồi VIP" → confirm dialog → `clearVipByKey()` → về Free state đúng, không cần dọn `VipPrefs`
  riêng (đúng như comment mới).
- `appOpenExcludedActivities` mới không phá luồng `initSplashScreen` (App Open ở Splash vẫn load/show
  bình thường — exclusion chỉ chặn nhánh **tự-động resume**, không ảnh hưởng lời gọi tường minh).
- Bấm "Xem quảng cáo → 3 ngày VIP" → `showRewarded` load/show qua SDK thật, không crash tới lúc ad hiện
  (cùng pipeline y hệt lần smoke test đầu). **Vẫn KHÔNG verify được nhánh `earned=false` → interstitial
  fallback mới khôi phục** — cùng quirk môi trường: test-ad creative không có nút đóng/không tự dismiss
  sau nhiều phút chờ, kể cả back button. Force-stop app để thoát, cold start lại sau đó sạch, không
  crash residual.
- **Còn lại chưa verify sống**: nhánh `earned=false` → `AdManager.showInterstitial(activity) { toast }`
  (code đã review kỹ bởi 3 agent, chữ ký khớp đúng SDK qua javap, nhưng chưa tận mắt thấy chạy) và nhánh
  `earned=true` → `grantVipDays` (như QA round 1). Cả hai đều bị chặn bởi cùng 1 hạn chế môi trường test
  ads, không phải nghi vấn về code. Khuyến nghị QA xác nhận 2 nhánh này trên network/device khác trước
  release, hoặc dùng `AdManager.openAdInspector()` để soi trực tiếp.

## 0. Bối cảnh

| Field | Giá trị |
|---|---|
| Lib version cũ → mới | `1.1.3` → `1.6.21` |
| Flavor | `dev`/`production` (không cần `gms`/`nongms` — app chỉ ship Google Play, dùng thẳng AAR JitPack) |
| Provider | AppLovin MAX giữ nguyên (`IS_ENABLE_ADMOB=false` cả 2 flavor) |
| Build variant debug/release phải xanh | `assembleDevDebug` / `assembleProductionRelease` |
| VIP flow | **Đổi sang token ECDSA** (quyết định user 2026-08-26) — bỏ `activateVipByKey` + `VipKeys.kt` static Base64 list |
| Lần fail trước | User không nhớ rõ nguyên nhân — không có ghi chép; xử lý bằng cách build+test từng bước nhỏ, không đổi nhiều thứ cùng lúc |

## 1. Touchpoint table

| Slot | File | API cũ (1.1.3) | API mới (1.6.21) | Đổi gì |
|---|---|---|---|---|
| Init | `GalaxyApp.kt:37-66` | `AdSdkConfig(...)` + `setConfig` + `initialize` | giữ nguyên 2-step, thêm field mới | thêm `paidEventListener` (revenue tracking), thêm `vipTokenPublicKey`, đổi `vipKeySecret` sang giá trị riêng ≥16 ký tự |
| Consent | `SplashAct.kt:88` | `requestConsentInfoUpdate` + `initSplashScreen` | giữ nguyên — cả 2 API vẫn tồn tại ở 1.6.21 | không đổi (đã đúng pattern) |
| Banner | `MainAct.kt:362`, `ResultAct.kt:139` | `loadBanner(autoManageLifecycle=true)` | giữ nguyên | không đổi |
| Interstitial | `ResultAct.kt:420` | `showInterstitial` | giữ nguyên | không đổi |
| Rewarded → VIP | `FVipManagement.kt:352-370` | `showRewarded` → `activateVipByKey(days=3)` | `showRewarded` → `AdManager.grantVipDays(ctx, 3)` | đổi API cấp thưởng (không qua verify key nữa) |
| VIP redeem (user nhập key) | `FVipManagement.kt:340-347` | `VipKeys.lookupDays` (static) → `activateVipByKey` | user nhập **token** → `activateVipByToken(ctx, token)` | đổi model phân phối — xem mục 3 |
| Interstitial fallback khi rewarded fail | `FVipManagement.kt:358-368` | **BUG**: gọi `grantViaRewarded()` nếu `adShown=true` | KHÔNG cấp thưởng — chỉ show ad (nếu có) rồi báo fail | bắt buộc sửa — vi phạm Step 7 rule 6 (chính sách reward) |

## 2. Kế hoạch từng bước

### Bước 1 — Bump lib version
- `app/build.gradle.kts:140`: `1.1.3` → `1.6.21`.
- Build thử ngay (`compileDevDebugKotlin`) để lộ mọi breaking API compile-time trước khi sửa logic.

### Bước 2 — `AdSdkConfig` / `GalaxyApp.onCreate`
- Đổi `vipKeySecret` sang chuỗi ngẫu nhiên riêng ≥16 ký tự (secret hiện tại `VipKeys.VIP_SECRET` — kiểm tra không phải giá trị mẫu `"TPLdev0402dev@"`, nếu đúng vậy đổi ngay — **release sẽ crash nếu để mặc định**).
- Thêm `vipTokenPublicKey = BuildConfig.VIP_TOKEN_PUBLIC_KEY` vào `AdSdkConfig` (xem Bước 3 — sinh keypair).
- Thêm `AdManager.paidEventListener = PaidEventListener { adType, valueMicros, currency, precision, adSource -> ... }` **trong `Application.onCreate()`** (không set từ Activity — SDK auto-xoá listener khi Activity destroy).

### Bước 3 — Sinh ECDSA keypair + chuyển VIP redeem sang token
1. Chạy `AdManager.generateVipKeyPair()` **1 lần, offline** (debug build hoặc unit test tạm) → lấy `(privateKeyBase64, publicKeyBase64)`.
2. Public key → `buildConfigField("String", "VIP_TOKEN_PUBLIC_KEY", "\"<public>\"")`.
3. Private key → **KHÔNG commit vào repo app này** — lưu vào repo private `@mckimquyen/myKeyStore` (cùng chỗ đang giữ keystore signing, theo pattern hiện tại của project) hoặc password manager riêng.
4. Xoá `VipKeys.kt` static Base64 key list + `lookupDays()`.
5. `FVipManagement.handleRedeemClicked()`: đổi validate từ `VipKeys.lookupDays(input)` sang `AdManager.activateVipByToken(requireContext(), input)` (token tự mã hoá số ngày, không cần lookup local nữa).
6. `FVipManagement.grantViaRewarded()`: đổi `activateVipByKey(ctx, secret, 3)` → `AdManager.grantVipDays(requireContext(), 3)`.
7. **Cần công cụ mint token cho support/QA** (thay việc gửi static key qua Notion/Slack trước đây) — đề xuất: 1 unit test JVM tạm (`@Ignore`, chạy tay khi cần) gọi `AdManager.generateVipToken(privateKeyBase64, days)` in ra token, KHÔNG ship vào app. Quyết định cụ thể hình thức tool này hỏi lại user khi tới bước code.

### Bước 4 — Fix bug policy (Step 7 rule 6)
- `FVipManagement.handleWatchAdClicked()`: nhánh fallback interstitial hiện gọi `grantViaRewarded()` khi `adShown=true` — **xoá dòng này**. Fallback interstitial (nếu còn giữ) chỉ được show ad làm "an ủi", KHÔNG gọi bất kỳ hàm cấp VIP nào. Nếu rewarded fail → luôn kết thúc bằng toast `vip_rewarded_load_failed`, không nhánh nào cấp thưởng.

### Bước 5 — Đọc kỹ 4 breaking change 1.5.5→1.6.1 (áp dụng dù nhảy thẳng 1.1.3→1.6.21)
- `grantRewardOnEarn` default nay `true` — không cần đổi code (project không cấp thưởng ở `rewardedListener`, đã cấp ở callback `showRewarded`), nhưng verify KHÔNG có chỗ nào cấp thưởng trùng qua `AdEvent.Rewarded`/`rewardedListener` (grep hiện tại: không có) — giữ nguyên, chỉ cấp ở callback `showRewarded`.
- `AdSafetyStatus.totalImpressions`/`fullscreenClicks` rename → project không dùng field này ở đâu (grep xác nhận) — không cần sửa.
- Số ad/user/ngày sẽ tăng dần theo session-reset mới (30 phút background) — không cần code, chỉ **theo dõi tuần đầu sau release**.
- GPP (US states) — bỏ qua, app hiện không target riêng US state nào ngoài chuẩn.

### Bước 6 — Manifest housekeeping
- Thêm `<attribution android:tag="ads" android:label="Ad Networks (AdMob / AppLovin MAX)" />` vào `<manifest>` (targetSdk 37 ≥ 31, tránh warning logcat liên tục).
- Giữ nguyên `<meta-data com.google.android.gms.ads.APPLICATION_ID>` hiện có (đúng rồi, không đổi).

### Bước 7 — Build & test
- `./gradlew compileDevDebugKotlin` sau Bước 1 (soi lỗi compile sớm).
- `./gradlew assembleDevDebug assembleProductionRelease` — cả 2 phải xanh.
- `./gradlew testDevDebugUnitTest`.
- Test device thật: verify banner/interstitial/rewarded/VIP redeem bằng **token mới** + `AdManager.getDiagnostics()` log để confirm consent/provider đúng.
- Đăng ký lại test-device hash nếu SDK đổi format hash (Bước 3b trong AD_PROMPT_AOS.MD) — verify nhãn "Test Ad" vẫn hiện đúng sau upgrade.

## 3. Đã giải quyết / còn tồn đọng

- ✅ **Tool mint token**: dùng instrumented test tạm (`app/src/androidTest`, xoá ngay sau khi lấy output)
  gọi `AdManager.generateVipKeyPair()` rồi `AdManager.generateVipToken(...)` — chạy trên chính device,
  không cần tool riêng. Muốn mint thêm cho user/QA thật: viết lại instrumented test tương tự với private
  key ở `@mckimquyen/myKeyStore/com.samsunggalaxy.bmicalculator/vip_token_keypair.txt`, xoá sau khi dùng.
- ✅ **`VipKeys.VIP_SECRET`**: đã xác nhận KHÔNG phải giá trị mẫu (`"9fA0q7eN!27cLx04@..."`, 31 ký tự) —
  giữ nguyên, không đổi (đổi sẽ vô hiệu hoá tamper-check của user đang active VIP).
- ⚠️ **Còn tồn đọng**: AdMob ID thật (banner/interstitial/appopen/rewarded) vẫn là **Google test ID** vì
  `IS_ENABLE_ADMOB=false` — không chặn migrate lần này (AdMob đang tắt), nhưng nếu tương lai bật AdMob
  cần ID thật trước.
- ⚠️ **Còn tồn đọng**: nhánh `earned=true` của rewarded ad (`grantViaRewarded` → `grantVipDays`) chưa
  verify được trên device thật do test-ad creative không đóng được (xem ghi chú smoke test ở đầu file)
  — cần QA xác nhận thủ công trước khi release.

## 4. KHÔNG làm (theo "Cấm" của template)

- Không đổi `IS_ENABLE_ADMOB` (giữ AppLovin MAX).
- Không thêm ad slot mới ngoài 4 slot hiện có.
- Không refactor code không liên quan ad/VIP.
- Không tự fill ad ID production — chỉ đổi API/config theo kế hoạch trên.

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
