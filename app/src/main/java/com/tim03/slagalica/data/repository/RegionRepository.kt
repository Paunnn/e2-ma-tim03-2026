package com.tim03.slagalica.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.tim03.slagalica.data.model.User
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class MapPlayerDot(
    val uid: String,
    val username: String,
    val region: String
)

data class RegionStats(
    val region: String,
    val icon: String,
    val totalMonthlyStars: Long,
    val activePlayers: Int,
    val totalPlayers: Int,
    // How many closed monthly cycles this region finished 1st/2nd/3rd (from region_history).
    val firstPlaces: Int = 0,
    val secondPlaces: Int = 0,
    val thirdPlaces: Int = 0,
    val rank: Int = 0
)

data class RegionLeaderboardEntry(
    val uid: String,
    val username: String,
    val monthlyStars: Int,
    val league: Int,
    val avatarIndex: Int,
    val rank: Int = 0
)

val SERBIA_REGIONS = listOf(
    "Beograd" to "🏙",
    "Vojvodina" to "🌾",
    "Centralna Srbija" to "🌲",
    "Jugoistočna Srbija" to "⛰",
    "Zapadna Srbija" to "🏔",
    "Istočna Srbija" to "☀"
)

class RegionRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val monthKeyFmt = SimpleDateFormat("yyyy-MM", Locale.getDefault())



    suspend fun getRegionalLeaderboard(): List<RegionStats> {
        val snap = db.collection("users").get().await()
        val byRegion = mutableMapOf<String, MutableList<User>>()

        SERBIA_REGIONS.forEach { (r, _) -> byRegion[r] = mutableListOf() }

        snap.documents.forEach { doc ->
            val user = doc.toObject(User::class.java)?.copy(uid = doc.id) ?: return@forEach
            val r = user.region.ifBlank { "Ostalo" }
            byRegion.getOrPut(r) { mutableListOf() }.add(user)
        }

        val regionTotals = byRegion.mapValues { (_, users) -> users.sumOf { it.monthlyStars }.toLong() }
        // Close the previous monthly cycle (once, whoever loads first in a new month)
        // so podium counts survive the monthlyStars reset.
        runCatching { snapshotRegionCycleIfNeeded(regionTotals) }
        val podiums = runCatching { getRegionPodiums() }.getOrDefault(emptyMap())

        // Heal stuck flags: loggedIn=true with a long-dead heartbeat means the app was
        // killed/crashed without its onStop write going through (or the flag predates
        // presence tracking). Clear such flags so they never inflate the count again.
        // A genuinely active player refreshes lastActive every 60s, so 5 min is safe.
        val now = System.currentTimeMillis()
        val staleMs = 5 * 60 * 1000L
        val isActive = { u: User -> u.loggedIn && now - u.lastActive <= staleMs }
        byRegion.values.flatten()
            .filter { it.loggedIn && now - it.lastActive > staleMs }
            .forEach { stale ->
                runCatching { db.collection("users").document(stale.uid).update("loggedIn", false) }
            }

        val stats = byRegion.entries.map { (region, users) ->
            val icon = SERBIA_REGIONS.firstOrNull { it.first == region }?.second ?: "📍"
            val podium = podiums[region] ?: intArrayOf(0, 0, 0)
            RegionStats(
                region = region,
                icon = icon,
                totalMonthlyStars = regionTotals[region] ?: 0L,
                // "Currently active" = logged in and in the app right now: +1 on login,
                // -1 on logout, on leaving the app (MainActivity.onStop), or when the
                // stale-flag sweep above catches a killed/crashed session.
                activePlayers = users.count(isActive),
                totalPlayers = users.size,
                firstPlaces = podium[0],
                secondPlaces = podium[1],
                thirdPlaces = podium[2]
            )
        }.sortedByDescending { it.totalMonthlyStars }

        return stats.mapIndexed { i, s -> s.copy(rank = i + 1) }
    }

    // ─── Monthly cycle history (region_history/{monthKey}) ───
    // The app has no backend, so the first client that opens the region screen in a
    // new month writes the final ranking of the just-closed month. The region_meta/state
    // transaction guarantees only one client does it.

    private suspend fun snapshotRegionCycleIfNeeded(regionTotals: Map<String, Long>) {
        val currentMonth = monthKeyFmt.format(Calendar.getInstance().time)
        val metaRef = db.collection("region_meta").document("state")
        val closedMonth = db.runTransaction { tx ->
            val doc = tx.get(metaRef)
            val last = doc.getString("lastSnapshotMonth") ?: ""
            when {
                last == currentMonth -> null
                else -> {
                    tx.set(metaRef, mapOf("lastSnapshotMonth" to currentMonth))
                    // First run ever: just initialize the marker, there is no closed cycle yet.
                    last.ifEmpty { null }
                }
            }
        }.await() ?: return

        // Only regions that actually earned stars compete for the podium.
        val ranked = regionTotals.entries.filter { it.value > 0 }.sortedByDescending { it.value }
        db.collection("region_history").document(closedMonth).set(mapOf(
            "monthKey" to closedMonth,
            "first" to (ranked.getOrNull(0)?.key ?: ""),
            "second" to (ranked.getOrNull(1)?.key ?: ""),
            "third" to (ranked.getOrNull(2)?.key ?: ""),
            "totals" to regionTotals
        )).await()
    }

    // region -> [firstPlaces, secondPlaces, thirdPlaces] across all closed cycles.
    private suspend fun getRegionPodiums(): Map<String, IntArray> {
        val snap = db.collection("region_history").get().await()
        val counts = mutableMapOf<String, IntArray>()
        fun bump(region: String?, slot: Int) {
            if (region.isNullOrEmpty()) return
            counts.getOrPut(region) { intArrayOf(0, 0, 0) }[slot]++
        }
        snap.documents.forEach { doc ->
            bump(doc.getString("first"), 0)
            bump(doc.getString("second"), 1)
            bump(doc.getString("third"), 2)
        }
        return counts
    }

    // Placement (1/2/3, else 0) of a region in the PREVIOUS monthly cycle - used for
    // the gold/silver/bronze avatar frame (spec 5e).
    suspend fun getPreviousCyclePlacement(region: String): Int {
        if (region.isBlank()) return 0
        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, -1)
        val prevMonth = monthKeyFmt.format(cal.time)
        val doc = db.collection("region_history").document(prevMonth).get().await()
        if (!doc.exists()) return 0
        return when (region) {
            doc.getString("first") -> 1
            doc.getString("second") -> 2
            doc.getString("third") -> 3
            else -> 0
        }
    }

    suspend fun getRegionPlayers(region: String): List<RegionLeaderboardEntry> {
        val snap = db.collection("users")
            .whereEqualTo("region", region)
            .get().await()
        val entries = snap.documents.mapNotNull { doc ->
            val uid = doc.id
            RegionLeaderboardEntry(
                uid = uid,
                username = doc.getString("username") ?: "",
                monthlyStars = (doc.getLong("monthlyStars") ?: 0L).toInt(),
                league = (doc.getLong("league") ?: 0L).toInt(),
                avatarIndex = (doc.getLong("avatarIndex") ?: 0L).toInt()
            )
        }.sortedByDescending { it.monthlyStars }
        return entries.mapIndexed { i, e -> e.copy(rank = i + 1) }
    }

    suspend fun getAllPlayersForMap(): List<MapPlayerDot> {
        val snap = db.collection("users").get().await()
        return snap.documents.mapNotNull { doc ->
            val region = doc.getString("region")?.ifBlank { null } ?: return@mapNotNull null
            MapPlayerDot(
                uid = doc.id,
                username = doc.getString("username") ?: "",
                region = region
            )
        }
    }

    fun myRegion(): String {
        val uid = auth.currentUser?.uid ?: return ""
        return ""
    }
}
