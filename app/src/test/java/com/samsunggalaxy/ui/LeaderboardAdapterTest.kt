package com.samsunggalaxy.ui

import com.samsunggalaxy.data.Profile
import org.junit.Assert.assertEquals
import org.junit.Test

/** Idea I4 — Family Challenge Mode: leaderboard ranking rules. */
class LeaderboardAdapterTest {

    private fun entry(name: String, currentStreak: Int, bestStreak: Int) = LeaderboardEntry(
        profile = Profile(id = 1, name = name),
        currentStreak = currentStreak,
        bestStreak = bestStreak,
        weightChangeKg = null
    )

    @Test
    fun `sorts by current streak descending`() {
        val entries = listOf(entry("A", 3, 3), entry("B", 9, 9), entry("C", 5, 5))

        val result = entries.sortedForLeaderboard()

        assertEquals(listOf("B", "C", "A"), result.map { it.profile.name })
    }

    @Test
    fun `ties on current streak break by best streak descending`() {
        val entries = listOf(entry("A", 5, 10), entry("B", 5, 20), entry("C", 5, 5))

        val result = entries.sortedForLeaderboard()

        assertEquals(listOf("B", "A", "C"), result.map { it.profile.name })
    }

    @Test
    fun `single entry stays as is`() {
        val entries = listOf(entry("Solo", 1, 1))

        val result = entries.sortedForLeaderboard()

        assertEquals(listOf("Solo"), result.map { it.profile.name })
    }

    @Test
    fun `empty list stays empty`() {
        assertEquals(emptyList<LeaderboardEntry>(), emptyList<LeaderboardEntry>().sortedForLeaderboard())
    }

    @Test
    fun `fewer than 2 entries is too few for a leaderboard`() {
        assertEquals(true, emptyList<LeaderboardEntry>().isTooFewForLeaderboard())
        assertEquals(true, listOf(entry("Solo", 1, 1)).isTooFewForLeaderboard())
    }

    @Test
    fun `2 or more entries is enough for a leaderboard`() {
        assertEquals(false, listOf(entry("A", 1, 1), entry("B", 2, 2)).isTooFewForLeaderboard())
    }
}
