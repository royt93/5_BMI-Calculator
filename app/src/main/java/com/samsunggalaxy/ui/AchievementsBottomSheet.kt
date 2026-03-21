package com.samsunggalaxy.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.samsunggalaxy.R

class AchievementsBottomSheet : BottomSheetDialogFragment() {

    override fun getTheme(): Int = com.google.android.material.R.style.ThemeOverlay_Material3_BottomSheetDialog

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_achievements, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ctx = context ?: return

        // Close button
        view.findViewById<View>(R.id.ivCloseAchievements)?.setOnClickListener { dismiss() }

        // Title
        val earnedCount = BadgeManager.getEarnedCount(ctx)
        val totalCount = BadgeManager.Badge.values().size
        view.findViewById<TextView>(R.id.tvAchievementsTitle)?.text =
            getString(R.string.achievements_title, earnedCount, totalCount)

        // Badge grid
        val badges = BadgeManager.Badge.values().map { badge ->
            BadgeAdapter.BadgeItem(
                badge = badge,
                earned = BadgeManager.isEarned(ctx, badge),
                earnedDate = BadgeManager.getEarnedDate(ctx, badge)
            )
        }

        val rv = view.findViewById<RecyclerView>(R.id.rvBadges) ?: return
        rv.layoutManager = GridLayoutManager(ctx, 2)
        rv.adapter = BadgeAdapter(badges)
    }

    companion object {
        const val TAG = "AchievementsBottomSheet"
    }
}
