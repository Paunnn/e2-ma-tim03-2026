package com.tim03.slagalica.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.tim03.slagalica.data.model.User
import kotlinx.coroutines.tasks.await

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

    suspend fun getRegionalLeaderboard(): List<RegionStats> {
        val snap = db.collection("users").get().await()
        val byRegion = mutableMapOf<String, MutableList<User>>()

        SERBIA_REGIONS.forEach { (r, _) -> byRegion[r] = mutableListOf() }

        snap.documents.forEach { doc ->
            val user = doc.toObject(User::class.java)?.copy(uid = doc.id) ?: return@forEach
            val r = user.region.ifBlank { "Ostalo" }
            byRegion.getOrPut(r) { mutableListOf() }.add(user)
        }

        val stats = byRegion.entries.map { (region, users) ->
            val icon = SERBIA_REGIONS.firstOrNull { it.first == region }?.second ?: "📍"
            RegionStats(
                region = region,
                icon = icon,
                totalMonthlyStars = users.sumOf { it.monthlyStars }.toLong(),
                // Every registered account in the region counts as an active player;
                // filtering by monthlyStars > 0 hid users who hadn't scored this month yet.
                activePlayers = users.size,
                totalPlayers = users.size
            )
        }.sortedByDescending { it.totalMonthlyStars }

        return stats.mapIndexed { i, s -> s.copy(rank = i + 1) }
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
