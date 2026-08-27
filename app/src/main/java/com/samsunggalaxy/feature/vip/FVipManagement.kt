package com.samsunggalaxy.feature.vip

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.roy.sdkadbmob.AdManager
import com.samsunggalaxy.BuildConfig
import com.samsunggalaxy.R
import com.samsunggalaxy.common.const.AdKeys
import com.samsunggalaxy.databinding.FVipManagementBinding
import com.samsunggalaxy.utils.AppLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * VIP management screen — Fragment.
 *
 * Tuân thủ memory-leak rules (template 10.5):
 * - `_binding` nullable + `binding` get() → check `_binding == null` ở async callback.
 * - `CountDownTimer` cancel ở `onDestroyView`.
 * - Mọi `ObjectAnimator` / `ValueAnimator` cancel + removeAllUpdateListeners ở `onDestroyView`.
 */
class FVipManagement : Fragment() {

    private var _binding: FVipManagementBinding? = null
    private val binding get() = _binding!!

    private val vipPrefs by lazy { VipPrefs(requireContext().applicationContext) }

    private var countDownTimer: CountDownTimer? = null
    private var pulseAnimator: ObjectAnimator? = null
    private var shimmerAnimator: ObjectAnimator? = null
    private var countUpAnimator: ValueAnimator? = null
    private var confettiAnimator: ObjectAnimator? = null
    private var lastMinute: Int? = null

    /**
     * Mốc `getVipByKeyExpiry()` NGAY TRƯỚC lần `activateVipByToken` đang chạy — dùng tính
     * `daysAdded()` khi kết quả thật tới muộn qua `setPendingVipTokenResultListener` (token nhập
     * đúng lúc SDK chưa init xong). Set trên main thread TRƯỚC khi thread nền chạy — pending
     * listener có thể fire (từ IO thread của init()) trước khi nhánh "queued" bên dưới kịp set,
     * set muộn sẽ đọc `null` và tính sai +0 ngày (theo đúng pattern sample chính thức của SDK,
     * `ActVipManagement.kt`).
     *
     * CHỈ THUỘC VỀ đúng 1 lần redeem tại 1 thời điểm trong ĐA SỐ trường hợp — `handleRedeemClicked()`
     * chặn gọi `activateVipByToken()` lần 2 khi `AdManager.isVipTokenPending` còn true (xem
     * entry-guard đầu hàm đó; bài học từ generation-counter approach ở review 2026-08-27 vòng 4 — bị 3
     * agent độc lập chỉ ra là vẫn có thể lẫn baseline giữa 2 lần redeem chồng nhau; entry-guard là cách
     * chặn tận gốc thay vì cố phân loại đúng/sai sau khi đã cho phép 2 lệnh SDK tồn tại song song).
     *
     * KHÔNG loại bỏ được 100% (review vòng 5, codex đối chiếu source SDK 1.6.21 —
     * `retryPendingVipTokenIfAny()` gọi `pendingActivateToken.getAndSet(null)` khiến
     * `isVipTokenPending` về `false` NGAY LẬP TỨC lúc SDK tự retry token cũ, TRƯỚC KHI verify chữ ký +
     * invoke listener xong): còn 1 cửa sổ mili-giây, SDK-internal, entry-guard không đọc được, nếu user
     * bấm redeem token MỚI đúng lúc đó. Hẹp hơn NHIỀU so với bug gốc (cửa sổ 20 giây do chính app tạo
     * ra qua safety-net) và cần SDK expose thêm API (`isVipTokenResolutionInFlight` hoặc
     * `tryActivateVipByToken()` atomic) mới đóng dứt điểm — xem `doc/AD.MD` mục "Round 5".
     */
    private var expiryBeforeQueuedActivate: Long? = null

    /**
     * Dialog kết quả redeem ĐANG hiện (queued/thành công/thất bại/no-effect) — MỌI dialog kết quả
     * đều đi qua `showResultDialog()` dùng chung field này, tự dismiss dialog TRƯỚC đó trước khi
     * hiện dialog mới. Lý do dùng chung 1 field thay vì tách riêng theo loại: SDK không có
     * correlation ID giữa lần gọi `activateVipByToken` gốc và `setPendingVipTokenResultListener`
     * (xem `handleRedeemClicked`/`handlePendingTokenResult`) — có 1 race SDK-internal đã verify
     * bằng source thật (independent review 2026-08-26, đối chiếu `AdManager.kt` —
     * `pendingActivateToken.getAndSet(null)` dequeue NGAY LẬP TỨC lúc bắt đầu retry, TRƯỚC khi
     * verify chữ ký + invoke listener xong) có thể khiến dialog "Thất bại" hiện trước dialog
     * "Thành công" thật cho ĐÚNG 1 lần redeem — không có API app-side nào loại bỏ hoàn toàn race
     * này. Dùng chung field đảm bảo dialog ĐÚNG luôn là dialog cuối cùng user thấy (tự động dismiss
     * dialog sai khi kết quả thật tới), thay vì cố ngăn race không ngăn được.
     */
    private var activeResultDialog: androidx.appcompat.app.AlertDialog? = null

    /** Safety-net: mở khoá `btnRedeem` nếu token bị queued nhưng không có kết quả sau 20s (vd mất
     *  mạng — SDK re-queue lại, KHÔNG invoke listener cho tới khi có mạng) — tránh user bị kẹt
     *  vĩnh viễn không redeem lại được. */
    private var redeemLockoutRunnable: Runnable? = null

    private val dateFormatter by lazy {
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FVipManagementBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // BẮT BUỘC gọi setCurrentActivity TRƯỚC setPendingVipTokenResultListener (đúng pattern
        // sample chính thức SDK) — nếu không, SDK gán owner của listener theo currentActivity HIỆN
        // TẠI, mà tại thời điểm onViewCreated này chạy, ActivityLifecycleCallbacks tự-track của SDK
        // CHƯA kịp cập nhật currentActivity thành Activity đang chứa fragment này (chỉ fire SAU khi
        // onCreate() của Activity trả về) — owner khi đó vẫn trỏ Activity TRƯỚC. Activity đó bị
        // destroy sẽ tự xoá NHẦM listener của màn này → đúng bug "VIP cấp thầm lặng" mà cơ chế
        // pending-listener sinh ra để chặn.
        AdManager.setCurrentActivity(requireActivity())
        AdManager.setPendingVipTokenResultListener { activated -> handlePendingTokenResult(activated) }

        binding.btnRedeem.setOnClickListener { handleRedeemClicked() }
        binding.btnWatchAdReward.setOnClickListener { handleWatchAdClicked() }
        binding.btnRevokeVip.setOnClickListener { handleRevokeClicked() }
        binding.btnRevokeFromCard.setOnClickListener { handleRevokeClicked() }
        binding.tvPrivacyPolicy.setOnClickListener { openPrivacyPolicy() }

        // Debug-only: long-press vương miện log AdManager.getDiagnostics() — đính kèm output này
        // khi báo lỗi ad (Phụ lục C, AD_PROMPT_AOS.MD). AppLog.d tự gate BuildConfig.DEBUG nên
        // listener này vô hại nếu lỡ chạy ở release (không log gì).
        if (BuildConfig.DEBUG) {
            binding.ivCrown.setOnLongClickListener {
                AppLog.d(AdManager.getDiagnostics())
                Toast.makeText(requireContext(), "getDiagnostics() → logcat", Toast.LENGTH_SHORT).show()
                true
            }
        }

        // Preload rewarded ad sớm (idempotent, SDK auto-guard nếu đã loading/loaded).
        // Đảm bảo click "Watch ad → 3D" có ad sẵn → giảm fallback interstitial.
        AdManager.loadRewarded(requireContext())

        // Animation #2 — slide-in từ dưới (entry animation toàn container).
        val slideIn = AnimationUtils.loadAnimation(requireContext(), R.anim.slide_in_bottom)
        binding.root.startAnimation(slideIn)

        bindUi()
    }

    override fun onResume() {
        super.onResume()
        // Animation #1 — pulse cho "Watch ad → 3D" + Animation #3 — crown shimmer.
        startPulse()
        startCrownShimmer()
        // Refresh UI khi user back từ ad → VIP có thể đã active.
        bindUi()
    }

    override fun onPause() {
        super.onPause()
        pulseAnimator?.cancel()
        shimmerAnimator?.cancel()
    }

    override fun onDestroyView() {
        // Closure của listener giữ fragment (qua handlePendingTokenResult) — AdManager là singleton
        // sống suốt process, không gỡ sẽ leak fragment đã destroy (đúng pattern sample SDK).
        AdManager.setPendingVipTokenResultListener(null)
        activeResultDialog?.dismiss(); activeResultDialog = null
        redeemLockoutRunnable?.let { binding.root.removeCallbacks(it) }; redeemLockoutRunnable = null
        countDownTimer?.cancel(); countDownTimer = null
        pulseAnimator?.cancel(); pulseAnimator?.removeAllUpdateListeners(); pulseAnimator = null
        shimmerAnimator?.cancel(); shimmerAnimator?.removeAllUpdateListeners(); shimmerAnimator = null
        countUpAnimator?.cancel(); countUpAnimator?.removeAllUpdateListeners(); countUpAnimator = null
        confettiAnimator?.cancel(); confettiAnimator?.removeAllUpdateListeners(); confettiAnimator = null
        _binding = null
        super.onDestroyView()
    }

    // ============================================================
    // UI Binding
    // ============================================================

    private fun bindUi() {
        if (_binding == null) return
        val active = AdManager.isVipByKeyActive()
        val expiry = AdManager.getVipByKeyExpiry()
        val grantedAt = AdManager.getVipGrantedAtMs()

        if (active && expiry > 0L) {
            renderActive(grantedAt, expiry)
        } else {
            renderFree()
        }
    }

    private fun renderActive(grantedAtMs: Long, expiresAtMs: Long) {
        // Hero glow: gold halo backdrop khi VIP active.
        binding.heroGlow.setBackgroundResource(R.drawable.bg_vip_hero_glow_active)
        binding.ivCrown.imageTintList = ColorStateList.valueOf(
            ContextCompat.getColor(requireContext(), R.color.vip_gold_dark)
        )
        binding.tvStatusTitle.setText(R.string.vip_active)
        binding.tvStatusSubtitle.text = getString(R.string.vip_until, dateFormatter.format(Date(expiresAtMs)))

        val grantedForUi = if (grantedAtMs > 0L) grantedAtMs else expiresAtMs - TimeUnit.DAYS.toMillis(1)
        binding.tvActivatedAt.text = getString(R.string.vip_activated_at, dateFormatter.format(Date(grantedForUi)))
        binding.tvExpiresAt.text = getString(R.string.vip_expires_at, dateFormatter.format(Date(expiresAtMs)))

        // Stats card chứa countdown + progress + activated/expires inline.
        binding.cardStats.visibility = View.VISIBLE

        // Hide Unlock section khi user đã VIP (redeem key/watch ad không cần).
        binding.sectionUnlock.visibility = View.GONE

        bindActiveEntryCard(grantedForUi, expiresAtMs)

        binding.btnRevokeVip.isEnabled = true
        binding.btnRevokeVip.visibility = View.VISIBLE
        startCountdownTimer(grantedForUi, expiresAtMs)
    }

    private fun renderFree() {
        binding.heroGlow.setBackgroundResource(R.drawable.bg_vip_hero_glow_free)
        binding.ivCrown.imageTintList = ColorStateList.valueOf(
            ContextCompat.getColor(requireContext(), R.color.textColorAdditional)
        )
        binding.tvStatusTitle.setText(R.string.vip_free_user)
        binding.tvStatusSubtitle.text = ""

        binding.cardStats.visibility = View.GONE
        binding.cardActiveVip.visibility = View.GONE

        // Show Unlock section khi user là Free — họ cần redeem/watch ad để VIP.
        binding.sectionUnlock.visibility = View.VISIBLE

        binding.btnRevokeVip.isEnabled = false
        binding.btnRevokeVip.visibility = View.GONE

        countDownTimer?.cancel()
        countDownTimer = null
    }

    private fun bindActiveEntryCard(grantedAtMs: Long, expiresAtMs: Long) {
        // Grace entry detection: VIP active + user CHƯA bao giờ redeem qua UI
        // → suy ra đến từ SDK 1.1.3 auto-grace (install referrer 1d).
        // Khi user tự redeem hoặc earn rewarded → `markUserRedeemed()` flip flag.
        val isFirstInstallGrace = !vipPrefs.userRedeemedAtLeastOnce()

        binding.cardActiveVip.visibility = View.VISIBLE
        if (isFirstInstallGrace) {
            binding.tvActiveLabel.setText(R.string.vip_entry_first_install)
        } else {
            val totalDays = TimeUnit.MILLISECONDS.toDays(expiresAtMs - grantedAtMs).toInt().coerceAtLeast(1)
            binding.tvActiveLabel.text = getString(R.string.vip_entry_redeemed, totalDays)
        }
        binding.tvActiveDescription.text = getString(R.string.vip_until, dateFormatter.format(Date(expiresAtMs)))
    }

    // ============================================================
    // Countdown + Progress (elapsed-semantic)
    // ============================================================

    private fun startCountdownTimer(grantedAtMs: Long, expiresAtMs: Long) {
        countDownTimer?.cancel()
        val now = System.currentTimeMillis()
        val remaining = (expiresAtMs - now).coerceAtLeast(0L)
        if (remaining <= 0L) {
            binding.tvCountdown.text = formatRemaining(0L)
            binding.progressVip.setProgressCompat(100, true)
            return
        }
        binding.tvCountdown.text = formatRemaining(remaining)
        binding.progressVip.setProgressCompat(
            computeElapsedProgress(grantedAtMs, expiresAtMs, now),
            /* animated = */ true,
        )
        lastMinute = (remaining / 60_000L).toInt()
        countDownTimer = object : CountDownTimer(remaining, 1_000L) {
            override fun onTick(millisUntilFinished: Long) {
                if (_binding == null) return
                binding.tvCountdown.text = formatRemaining(millisUntilFinished)
                val current = System.currentTimeMillis()
                binding.progressVip.setProgressCompat(
                    computeElapsedProgress(grantedAtMs, expiresAtMs, current),
                    true,
                )
                // Animation #4 — count-up khi minute đổi (không mỗi giây).
                val newMinute = (millisUntilFinished / 60_000L).toInt()
                if (lastMinute != null && lastMinute != newMinute) {
                    animateMinuteChange(lastMinute!!, newMinute)
                }
                lastMinute = newMinute
            }

            override fun onFinish() {
                if (_binding == null) return
                binding.tvCountdown.text = formatRemaining(0L)
                binding.progressVip.setProgressCompat(100, true)
                bindUi()
            }
        }.start()
    }

    private fun computeElapsedProgress(grantedAtMs: Long, expiresAtMs: Long, nowMs: Long): Int {
        val total = expiresAtMs - grantedAtMs
        if (total <= 0L) return 100
        val elapsed = nowMs - grantedAtMs
        return ((elapsed.toDouble() / total.toDouble()) * 100.0)
            .toInt()
            .coerceIn(0, 100)
    }

    private fun formatRemaining(ms: Long): String {
        val days = TimeUnit.MILLISECONDS.toDays(ms)
        val hours = TimeUnit.MILLISECONDS.toHours(ms) % 24
        val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
        return getString(R.string.vip_remaining, days.toInt(), hours.toInt(), minutes.toInt(), seconds.toInt())
    }

    // ============================================================
    // Animations
    // ============================================================

    private fun startPulse() {
        val target = _binding?.btnWatchAdReward ?: return
        pulseAnimator?.cancel()
        pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(
            target,
            android.animation.PropertyValuesHolder.ofFloat(View.SCALE_X, 1.0f, 1.05f),
            android.animation.PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.0f, 1.05f),
        ).apply {
            duration = 1_600L
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            start()
        }
    }

    private fun startCrownShimmer() {
        val target = _binding?.ivCrown ?: return
        shimmerAnimator?.cancel()
        shimmerAnimator = ObjectAnimator.ofFloat(target, View.ROTATION, -5f, 5f).apply {
            duration = 3_000L
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            start()
        }
    }

    private fun animateMinuteChange(from: Int, to: Int) {
        if (_binding == null || from == to) return
        countUpAnimator?.cancel()
        countUpAnimator = ValueAnimator.ofInt(from, to).apply {
            duration = 400L
            addUpdateListener {
                // chỉ làm flash nhẹ vào label countdown, không thay text (text đã update qua onTick)
                _binding?.tvCountdown?.alpha = 0.6f + 0.4f * (it.animatedFraction)
            }
            start()
        }
    }

    private fun playConfetti() {
        val target = _binding?.ivCrown ?: return
        confettiAnimator?.cancel()
        // Lưu ý: KHÔNG dùng View.ROTATION ở confetti — sẽ conflict với shimmerAnimator
        // (cùng property → snap khi end). Thay bằng scale + alpha pulse.
        confettiAnimator = ObjectAnimator.ofPropertyValuesHolder(
            target,
            android.animation.PropertyValuesHolder.ofFloat(View.SCALE_X, 1.0f, 1.4f, 1.0f),
            android.animation.PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.0f, 1.4f, 1.0f),
            android.animation.PropertyValuesHolder.ofFloat(View.ALPHA, 1.0f, 0.6f, 1.0f),
        ).apply {
            duration = 1_000L
            start()
        }
        performHapticConfirm(target)
    }

    private fun performHapticConfirm(view: View) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val vib = requireContext().getSystemService(Vibrator::class.java)
                vib?.vibrate(VibrationEffect.createOneShot(50L, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (_: Exception) {
            // Haptic optional, silently ignore on devices that block it.
        }
    }

    // ============================================================
    // Actions
    // ============================================================

    private fun handleRedeemClicked() {
        val token = binding.etRedeem.text?.toString()?.trim().orEmpty()
        if (token.isEmpty()) {
            binding.tilRedeem.error = getString(R.string.vip_redeem_invalid)
            return
        }
        binding.tilRedeem.error = null

        // Chặn TẬN GỐC multi-submit thay vì cố phân loại callback bằng generation counter (vòng
        // review 2026-08-27 thứ 4: 3 agent độc lập đều xác nhận generation counter KHÔNG đóng được
        // gap này — SDK không có correlation ID nên callback `setPendingVipTokenResultListener` của
        // token CŨ không thể tự nhận diện "tôi thuộc generation nào", dẫn tới có thể đọc/xoá nhầm
        // `expiryBeforeQueuedActivate` của lần redeem MỚI đang chạy, thậm chí nuốt mất 1 kết quả thất
        // bại thật). Giải pháp đúng: trong TUYỆT ĐẠI ĐA SỐ trường hợp không để 2 lệnh
        // `activateVipByToken()` cùng tồn tại — SDK chỉ giữ được 1 token pending tại 1 thời điểm
        // (`isVipTokenPending`), nên khi còn 1 token pending chưa resolve, KHÔNG gọi SDK lần nữa, chỉ
        // nhắc lại dialog "đang chờ" và gia hạn safety-net. Vẫn còn 1 cửa sổ mili-giây SDK-internal
        // chưa đóng được 100% — xem KDoc `expiryBeforeQueuedActivate` và `doc/AD.MD` mục "Round 5".
        if (AdManager.isVipTokenPending) {
            if (expiryBeforeQueuedActivate == null) {
                expiryBeforeQueuedActivate = AdManager.getVipByKeyExpiry()
            }
            binding.btnRedeem.isEnabled = false
            showTokenQueuedDialog()
            armRedeemLockoutSafetyNet()
            return
        }

        // activateVipByToken() dùng SharedPreferences commit() đồng bộ (chống crash-replay) — chạy
        // ngoài main thread để tránh StrictMode violation/jank (đúng pattern sample SDK). LƯU Ý
        // khác sample gốc (Activity, `this` luôn an toàn làm Context): ở Fragment, `requireContext()`
        // PHẢI được gọi ở ĐÂY, trên main thread, TRƯỚC khi tạo Thread — gọi bên trong lambda Thread
        // sẽ evaluate lười lúc thread thực thi, và nếu user back/thoát màn hình giữa lúc đó, Fragment
        // đã detach → `requireContext()` throw `IllegalStateException` KHÔNG bắt được trên thread
        // nền → crash toàn app (independent review 2026-08-26 bắt được, không phải nitpick).
        val ctx = requireContext().applicationContext
        val expiryBeforeActivate = AdManager.getVipByKeyExpiry()
        expiryBeforeQueuedActivate = expiryBeforeActivate
        binding.btnRedeem.isEnabled = false
        Thread {
            val ok = AdManager.activateVipByToken(ctx, token)
            activity?.runOnUiThread {
                if (!isAdded || _binding == null) return@runOnUiThread
                when {
                    ok -> {
                        expiryBeforeQueuedActivate = null
                        binding.btnRedeem.isEnabled = true
                        val addedDays = daysAdded(
                            maxOf(expiryBeforeActivate, System.currentTimeMillis()),
                            AdManager.getVipByKeyExpiry(),
                        )
                        if (addedDays > 0) onVipActivated(addedDays) else showTokenNoEffectDialog()
                    }
                    expiryBeforeQueuedActivate == null -> {
                        // Race: `setPendingVipTokenResultListener` của CHÍNH lần này đã fire VÀ xử lý
                        // xong (dialog kết quả thật đã hiện) TRƯỚC KHI nhánh này của thread nền kịp
                        // chạy — `false` ở đây là stale, KHÔNG phải kết quả thật. Không hiện gì thêm,
                        // tránh đè dialog "Thất bại" lên dialog kết quả đúng đã hiện (independent
                        // review bắt được). An toàn vì entry-guard phía trên đảm bảo không có lần
                        // redeem nào khác chen vào giữa — field này chỉ có thể bị chính lần gọi này
                        // ghi/xoá.
                        binding.btnRedeem.isEnabled = true
                    }
                    AdManager.isVipTokenPending -> {
                        // Token hợp lệ nhưng SDK chưa init xong nên bị xếp hàng — `false` ở đây
                        // KHÔNG có nghĩa là từ chối. Kết quả thật tới qua
                        // setPendingVipTokenResultListener (đăng ký ở onViewCreated).
                        showTokenQueuedDialog()
                        armRedeemLockoutSafetyNet()
                    }
                    else -> {
                        expiryBeforeQueuedActivate = null
                        binding.btnRedeem.isEnabled = true
                        showFailedDialog()
                    }
                }
            }
        }.start()
    }

    /**
     * Kết quả thật của token bị xếp hàng lúc SDK chưa init xong — tới muộn, bất đồng bộ. Có thể
     * fire ở 1 fragment instance KHÁC với lúc redeem (vd app bị kill/mở lại giữa lúc chờ — SDK
     * persist pending token qua process death, `expiryBeforeQueuedActivate` in-memory thì không) —
     * `activated == true` LUÔN là activate thật thành công, dù `before` có `null` hay không (fix
     * bug thật từ independent review: code cũ yêu cầu `before != null` mới coi là thành công, nên
     * báo sai "Thất bại" cho 1 lần activate thành công thật qua đường process-restart).
     */
    private fun handlePendingTokenResult(activated: Boolean) {
        activity?.runOnUiThread {
            if (!isAdded || _binding == null) return@runOnUiThread
            // Dọn state vô điều kiện: entry-guard ở `handleRedeemClicked()` (kiểm tra
            // `AdManager.isVipTokenPending` trước khi gọi SDK) loại bỏ được đường phổ biến khiến 2
            // token cùng "đang chờ" tồn tại song song — callback này trong TUYỆT ĐẠI ĐA SỐ trường hợp
            // thuộc về đúng 1 lần redeem duy nhất đang track, không cần phân biệt generation nào. Vẫn
            // còn 1 cửa sổ mili-giây SDK-internal chưa đóng được 100% — xem KDoc
            // `expiryBeforeQueuedActivate` và `doc/AD.MD` mục "Round 5".
            val before = expiryBeforeQueuedActivate
            expiryBeforeQueuedActivate = null
            binding.btnRedeem.isEnabled = true
            redeemLockoutRunnable?.let { binding.root.removeCallbacks(it) }
            redeemLockoutRunnable = null
            if (activated) {
                // Không biết mốc thật (fragment mới / before=0 nghĩa chưa từng có VIP) → fallback
                // "now" — báo số ngày còn lại thay vì số ngày cộng thêm, chấp nhận được, còn hơn báo
                // sai "Thất bại" cho 1 lần activate thành công thật.
                val baseline = before?.let { maxOf(it, System.currentTimeMillis()) } ?: System.currentTimeMillis()
                val addedDays = daysAdded(baseline, AdManager.getVipByKeyExpiry())
                if (addedDays > 0) onVipActivated(addedDays) else showTokenNoEffectDialog()
            } else {
                showFailedDialog()
            }
        }
    }

    private fun handleWatchAdClicked() {
        val activity = activity ?: return
        AdManager.showRewarded(activity) { earned ->
            if (!isAdded || _binding == null) return@showRewarded
            if (earned) {
                grantViaRewarded()
            } else {
                // Policy reward-ad (Step 7 rule 6 + AD_PROMPT_AOS.MD:1731): rewarded không earned →
                // fallback interstitial CHỈ để monetize, TUYỆT ĐỐI không cấp reward ở nhánh này
                // (dù shown hay không, user KHÔNG earn lần này).
                AdManager.showInterstitial(activity) {
                    if (!isAdded || _binding == null) return@showInterstitial
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.vip_rewarded_load_failed),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }

    private fun grantViaRewarded() {
        val ok = AdManager.grantVipDays(requireContext(), days = 3)
        if (ok) {
            Toast.makeText(
                requireContext(),
                getString(R.string.vip_rewarded_earned_message),
                Toast.LENGTH_SHORT,
            ).show()
            onVipActivated(3)
        } else {
            showFailedDialog()
        }
    }

    private fun onVipActivated(days: Int) {
        // grantedAtMs KHÔNG tự lưu — SDK 1.6.21 đã expose AdManager.getVipGrantedAtMs(), tự lưu sẽ
        // sai khi VIP được cấp qua đường khác (vd cộng dồn token trên VIP đang active).
        vipPrefs.markUserRedeemed()
        playConfetti()
        bindUi()
        binding.etRedeem.text?.clear()
        // C5: Reset error label sau khi activate thành công (kể cả qua rewarded path).
        binding.tilRedeem.error = null
        showResultDialog(R.string.vip_success_title, getString(R.string.vip_success_message, days))
    }

    private fun showFailedDialog() {
        showResultDialog(R.string.vip_failed_title, getString(R.string.vip_redeem_invalid))
    }

    /** Token hợp lệ nhưng bị xếp hàng chờ SDK init xong — KHÔNG có kết quả thật, không phải fail. */
    private fun showTokenQueuedDialog() {
        showResultDialog(R.string.vip_queued_title, getString(R.string.vip_queued_message))
    }

    /** Token hợp lệ + đã đánh dấu dùng (chống replay) nhưng không kéo dài hạn VIP hiện tại (maxOf, không cộng dồn). */
    private fun showTokenNoEffectDialog() {
        vipPrefs.markUserRedeemed()
        bindUi()
        binding.etRedeem.text?.clear()
        binding.tilRedeem.error = null
        showResultDialog(R.string.vip_token_no_effect_title, getString(R.string.vip_token_no_effect_message))
    }

    /**
     * Hiện dialog kết quả redeem, tự dismiss dialog kết quả TRƯỚC đó (nếu còn) — xem KDoc
     * `activeResultDialog`. Đảm bảo dialog ĐÚNG luôn là dialog cuối cùng user thấy, kể cả khi race
     * SDK-internal (không loại bỏ được từ app-side) khiến dialog sai hiện trước.
     */
    private fun showResultDialog(titleRes: Int, message: String) {
        if (!isAdded || _binding == null) return
        activeResultDialog?.dismiss()
        activeResultDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(titleRes)
            .setMessage(message)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    /**
     * Mở khoá `btnRedeem` sau 20s nếu token vẫn còn "queued" — xem KDoc `redeemLockoutRunnable`.
     * CHỈ mở khoá UI (để user không bị kẹt nhìn nút disable vĩnh viễn) — TUYỆT ĐỐI không đụng
     * `expiryBeforeQueuedActivate` ở đây. Nếu user bấm lại sau khi nút mở khoá, entry-guard
     * `AdManager.isVipTokenPending` trong `handleRedeemClicked()` sẽ chặn không cho gọi SDK lần 2
     * (token cũ nhiều khả năng vẫn chưa resolve), nên baseline vẫn phải nguyên vẹn chờ đúng 1 kết
     * quả thật duy nhất tới qua `handlePendingTokenResult`.
     */
    private fun armRedeemLockoutSafetyNet() {
        redeemLockoutRunnable?.let { binding.root.removeCallbacks(it) }
        redeemLockoutRunnable = Runnable {
            if (!isAdded || _binding == null) return@Runnable
            binding.btnRedeem.isEnabled = true
        }
        binding.root.postDelayed(redeemLockoutRunnable!!, 20_000L)
    }

    private fun handleRevokeClicked() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.vip_revoke_all_confirm_title)
            .setMessage(R.string.vip_revoke_all_confirm_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.confirm) { _, _ ->
                // clearVipByKey() đã tự xoá cả getVipGrantedAtMs() phía SDK, không cần dọn riêng.
                AdManager.clearVipByKey()
                bindUi()
            }
            .show()
    }

    private fun openPrivacyPolicy() {
        val url = AdKeys.PRIVACY_POLICY_URL
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            // No app can handle ACTION_VIEW (e.g. no browser installed) — a raw URL toast isn't
            // localized and isn't actionable for the user; a plain error message is.
            Toast.makeText(requireContext(), getString(R.string.could_not_open_link), Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        /**
         * Số ngày token vừa cấp THỰC SỰ cộng thêm (không phải tổng hạn còn lại) — `activateVipByToken`
         * dùng `maxOf(expiry cũ, expiry mới)` nội bộ nên hạn không "cộng dồn" nếu VIP hiện tại đã dài
         * hơn token vừa dùng; báo đúng delta để user biết token có tác dụng hay không. `internal` để
         * unit test gọi trực tiếp không cần khởi tạo Fragment.
         */
        internal fun daysAdded(baselineMs: Long, expiresAtMs: Long): Int {
            val deltaMs = (expiresAtMs - baselineMs).coerceAtLeast(0L)
            return kotlin.math.ceil(deltaMs / TimeUnit.DAYS.toMillis(1).toDouble()).toInt()
        }
    }
}
