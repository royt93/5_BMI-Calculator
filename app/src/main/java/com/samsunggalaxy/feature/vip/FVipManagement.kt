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
import com.samsunggalaxy.R
import com.samsunggalaxy.common.const.AdKeys
import com.samsunggalaxy.databinding.FVipManagementBinding
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

        binding.btnRedeem.setOnClickListener { handleRedeemClicked() }
        binding.btnWatchAdReward.setOnClickListener { handleWatchAdClicked() }
        binding.btnRevokeVip.setOnClickListener { handleRevokeClicked() }
        binding.btnRevokeFromCard.setOnClickListener { handleRevokeClicked() }
        binding.tvPrivacyPolicy.setOnClickListener { openPrivacyPolicy() }

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
        val grantedAt = vipPrefs.getGrantedAtMs()

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
        val input = binding.etRedeem.text?.toString()?.trim().orEmpty()
        if (input.isEmpty()) {
            binding.tilRedeem.error = getString(R.string.vip_redeem_invalid)
            return
        }
        val days = VipKeys.lookupDays(input)
        if (days == null) {
            binding.tilRedeem.error = getString(R.string.vip_redeem_invalid)
            return
        }
        binding.tilRedeem.error = null
        val ok = AdManager.activateVipByKey(requireContext(), AdKeys.VIP_SECRET, days)
        if (ok) {
            onVipActivated(days)
        } else {
            showFailedDialog()
        }
    }

    private fun handleWatchAdClicked() {
        val activity = activity ?: return
        AdManager.showRewarded(activity) { earned ->
            if (!isAdded || _binding == null) return@showRewarded
            if (earned) {
                grantViaRewarded()
            } else {
                // Fallback: try interstitial. Nếu cũng fail, hiển thị toast.
                AdManager.showInterstitial(activity) { adShown ->
                    if (!isAdded || _binding == null) return@showInterstitial
                    if (adShown) {
                        grantViaRewarded()
                    } else {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.vip_rewarded_load_failed),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            }
        }
    }

    private fun grantViaRewarded() {
        val ok = AdManager.activateVipByKey(requireContext(), AdKeys.VIP_SECRET, days = 3)
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
        vipPrefs.saveGrantedAtMs(System.currentTimeMillis())
        vipPrefs.markUserRedeemed()
        playConfetti()
        bindUi()
        binding.etRedeem.text?.clear()
        // C5: Reset error label sau khi activate thành công (kể cả qua rewarded path).
        binding.tilRedeem.error = null
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.vip_success_title)
            .setMessage(getString(R.string.vip_success_message, days))
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun showFailedDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.vip_failed_title)
            .setMessage(R.string.vip_redeem_invalid)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun handleRevokeClicked() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.vip_revoke_all_confirm_title)
            .setMessage(R.string.vip_revoke_all_confirm_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.confirm) { _, _ ->
                AdManager.clearVipByKey()
                vipPrefs.clearGrantedAtMs()
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
}
