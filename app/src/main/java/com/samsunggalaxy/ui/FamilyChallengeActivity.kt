package com.samsunggalaxy.ui

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.samsunggalaxy.BaseActivity
import com.samsunggalaxy.R
import com.samsunggalaxy.data.AppDatabase
import com.samsunggalaxy.data.BmiRepository
import com.samsunggalaxy.sdkadbmob.UIUtils
import com.samsunggalaxy.utils.CalculatorUtils
import com.samsunggalaxy.utils.PreferencesManager
import com.samsunggalaxy.utils.UnitFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Idea I4 — Family Challenge Mode: a lightweight leaderboard comparing streak/progress across
 * every profile on this device (EPIC-05 multi-profile backend), no server/account needed.
 */
class FamilyChallengeActivity : BaseActivity() {
    companion object {
        private const val RECENT_WINDOW_DAYS = 30L
    }

    private lateinit var repository: BmiRepository
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyStateContainer: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UIUtils.setupEdgeToEdge1(window)
        setContentView(R.layout.a_family_challenge)
        UIUtils.setupEdgeToEdge2(
            rootView = findViewById(R.id.layoutRoot),
            paddingTop = true,
            paddingBottom = true
        )

        val database = AppDatabase.getDatabase(this)
        repository = BmiRepository(database.bmiDao(), database.profileDao(), database.bodyMeasurementDao())

        recyclerView = findViewById(R.id.rvLeaderboard)
        recyclerView.layoutManager = LinearLayoutManager(this)
        emptyStateContainer = findViewById(R.id.emptyStateContainer)

        findViewById<View>(R.id.ivBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnAddProfile).setOnClickListener {
            ProfileSwitcherBottomSheet().show(supportFragmentManager, ProfileSwitcherBottomSheet.TAG)
        }

        // Reload after add/rename/delete/switch — the sheet doesn't know about this leaderboard.
        supportFragmentManager.setFragmentResultListener(
            ProfileSwitcherBottomSheet.REQUEST_KEY, this
        ) { _, _ -> loadLeaderboard() }

        loadLeaderboard()
    }

    private fun loadLeaderboard() {
        lifecycleScope.launch(Dispatchers.IO) {
            val unitSystem = PreferencesManager(this@FamilyChallengeActivity).unitSystem.first()
            val profiles = repository.getAllProfilesOnce()
            val sinceMs = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(RECENT_WINDOW_DAYS)

            val entries = profiles.map { profile ->
                val streak = StreakManager.getDisplayStreak(this@FamilyChallengeActivity, profile.id)
                val recentRecords = repository.getRecordsSince(profile.id, sinceMs)
                val weightChange = CalculatorUtils.calculateWeightChange(recentRecords.map { it.timestamp to it.weight })
                LeaderboardEntry(profile, streak.current, streak.best, weightChange)
            }.sortedForLeaderboard()

            withContext(Dispatchers.Main) {
                if (isFinishing || isDestroyed) return@withContext
                if (entries.isTooFewForLeaderboard()) {
                    recyclerView.isVisible = false
                    emptyStateContainer.isVisible = true
                } else {
                    recyclerView.isVisible = true
                    emptyStateContainer.isVisible = false
                    recyclerView.adapter = LeaderboardAdapter(entries, unitSystem)
                }
            }
        }
    }
}
