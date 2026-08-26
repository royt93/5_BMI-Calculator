package com.samsunggalaxy.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.samsunggalaxy.R
import com.samsunggalaxy.data.Profile
import com.samsunggalaxy.utils.UnitFormatter

/** Idea I4 — Family Challenge Mode: one row of the multi-profile leaderboard. */
data class LeaderboardEntry(
    val profile: Profile,
    val currentStreak: Int,
    val bestStreak: Int,
    val weightChangeKg: Double?
)

/** Rank by current streak descending, tie-broken by best streak descending. */
fun List<LeaderboardEntry>.sortedForLeaderboard(): List<LeaderboardEntry> =
    sortedWith(compareByDescending<LeaderboardEntry> { it.currentStreak }.thenByDescending { it.bestStreak })

/** A "leaderboard" of fewer than 2 people isn't a comparison — show the empty state instead. */
fun List<LeaderboardEntry>.isTooFewForLeaderboard(): Boolean = size < 2

/**
 * Plain RecyclerView.Adapter (not ListAdapter) — the leaderboard is loaded once per screen visit,
 * no incremental diffing needed, matching this screen's one-shot data flow.
 */
class LeaderboardAdapter(
    private val entries: List<LeaderboardEntry>,
    private val unitSystem: String
) : RecyclerView.Adapter<LeaderboardAdapter.ViewHolder>() {

    class ViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val tvRank: TextView = view.findViewById(R.id.tvRank)
        val tvName: TextView = view.findViewById(R.id.tvLeaderboardName)
        val tvCurrentLabel: TextView = view.findViewById(R.id.tvLeaderboardCurrentLabel)
        val tvStreak: TextView = view.findViewById(R.id.tvLeaderboardStreak)
        val tvWeightChange: TextView = view.findViewById(R.id.tvLeaderboardWeightChange)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_leaderboard_row, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = entries[position]
        holder.tvRank.text = when (position) {
            0 -> "🥇"
            1 -> "🥈"
            2 -> "🥉"
            else -> "#${position + 1}"
        }
        holder.tvName.text = entry.profile.name
        holder.tvCurrentLabel.isVisible = entry.profile.isCurrent
        holder.tvStreak.text = "🔥 ${entry.currentStreak}"
        holder.tvWeightChange.text = entry.weightChangeKg?.let {
            UnitFormatter.formatSignedWeightDelta(it, unitSystem)
        } ?: holder.itemView.context.getString(R.string.family_challenge_no_recent_data)
    }

    override fun getItemCount(): Int = entries.size
}
