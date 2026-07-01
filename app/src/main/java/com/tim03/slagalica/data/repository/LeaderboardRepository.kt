package com.tim03.slagalica.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.tim03.slagalica.data.model.LeaderboardEntry
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

sealed class RewardResult {
    object None : RewardResult()
    data class Earned(val tokens: Int, val rank: Int, val isMonthly: Boolean) : RewardResult()
}

class LeaderboardRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val fmt = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    private val weekKeyFmt = SimpleDateFormat("yyyy-ww", Locale.getDefault())
    private val monthKeyFmt = SimpleDateFormat("yyyy-MM", Locale.getDefault())

    private val weeklyTokenRewards = mapOf(1 to 5, 2 to 3, 3 to 2)
    private val monthlyTokenRewards = mapOf(1 to 10, 2 to 6, 3 to 4)

    private fun weekStartDate(): String = weekKeyFmt.format(Calendar.getInstance().time)
    private fun monthStartDate(): String = monthKeyFmt.format(Calendar.getInstance().time)

    private suspend fun fetchAllEntries(): List<LeaderboardEntry> {
        val snapshot = db.collection("users").get().await()
        return snapshot.documents.mapNotNull { doc ->
            val username = doc.getString("username") ?: return@mapNotNull null
            LeaderboardEntry(
                uid = doc.id,
                username = username,
                stars = doc.getLong("stars")?.toInt() ?: 0,
                weeklyStars = doc.getLong("weeklyStars")?.toInt() ?: 0,
                monthlyStars = doc.getLong("monthlyStars")?.toInt() ?: 0,
                league = doc.getLong("league")?.toInt() ?: 0,
                avatarIndex = doc.getLong("avatarIndex")?.toInt() ?: 0,
                region = doc.getString("region") ?: ""
            )
        }
    }

    suspend fun getWeeklyLeaderboard(): List<LeaderboardEntry> =
        fetchAllEntries()
            .filter { it.weeklyStars > 0 }
            .sortedByDescending { it.weeklyStars }
            .mapIndexed { i, e -> e.copy(rank = i + 1) }

    suspend fun getMonthlyLeaderboard(): List<LeaderboardEntry> =
        fetchAllEntries()
            .filter { it.monthlyStars > 0 }
            .sortedByDescending { it.monthlyStars }
            .mapIndexed { i, e -> e.copy(rank = i + 1) }

    fun getCurrentWeekDates(): Pair<String, String> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
        val start = fmt.format(cal.time)
        cal.add(Calendar.DAY_OF_WEEK, 6)
        val end = fmt.format(cal.time)
        return Pair(start, end)
    }

    fun getCurrentMonthDates(): Pair<String, String> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val start = fmt.format(cal.time)
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        val end = fmt.format(cal.time)
        return Pair(start, end)
    }

    suspend fun checkAndDistributeWeeklyRewards(): RewardResult {
        val uid = auth.currentUser?.uid ?: return RewardResult.None
        val weekKey = weekStartDate()
        val userDoc = db.collection("users").document(uid).get().await()
        val lastKey = userDoc.getString("lastWeeklyRewardDate") ?: ""
        if (lastKey == weekKey) return RewardResult.None

        // New week — rank based on current weeklyStars (previous cycle data)
        val entries = fetchAllEntries()
            .filter { it.weeklyStars > 0 }
            .sortedByDescending { it.weeklyStars }
        val myRank = entries.indexOfFirst { it.uid == uid } + 1
        val tokens = when {
            myRank in 1..3 -> weeklyTokenRewards[myRank] ?: 0
            myRank in 4..10 -> 1
            else -> 0
        }

        val updates = mutableMapOf<String, Any>("lastWeeklyRewardDate" to weekKey, "weeklyStars" to 0)
        if (tokens > 0) {
            updates["tokens"] = FieldValue.increment(tokens.toLong())
            updates["pendingWeeklyRewardTokens"] = tokens
            updates["pendingWeeklyRewardRank"] = myRank
            db.collection("notifications").add(mapOf(
                "userId" to uid,
                "channel" to "RANKING",
                "title" to "Nedeljni plasman!",
                "message" to "Zauzeli ste $myRank. mesto i dobili $tokens tokena!",
                "timestamp" to System.currentTimeMillis(),
                "isRead" to false
            )).await()
        }
        db.collection("users").document(uid).update(updates).await()
        return if (tokens > 0) RewardResult.Earned(tokens, myRank, false) else RewardResult.None
    }

    suspend fun checkAndDistributeMonthlyRewards(): RewardResult {
        val uid = auth.currentUser?.uid ?: return RewardResult.None
        val monthKey = monthStartDate()
        val userDoc = db.collection("users").document(uid).get().await()
        val lastKey = userDoc.getString("lastMonthlyRewardDate") ?: ""
        if (lastKey == monthKey) return RewardResult.None

        val entries = fetchAllEntries()
            .filter { it.monthlyStars > 0 }
            .sortedByDescending { it.monthlyStars }
        val myRank = entries.indexOfFirst { it.uid == uid } + 1
        val tokens = when {
            myRank in 1..3 -> monthlyTokenRewards[myRank] ?: 0
            myRank in 4..10 -> 2
            else -> 0
        }

        val updates = mutableMapOf<String, Any>("lastMonthlyRewardDate" to monthKey, "monthlyStars" to 0)
        if (tokens > 0) {
            updates["tokens"] = FieldValue.increment(tokens.toLong())
            updates["pendingMonthlyRewardTokens"] = tokens
            updates["pendingMonthlyRewardRank"] = myRank
            db.collection("notifications").add(mapOf(
                "userId" to uid,
                "channel" to "RANKING",
                "title" to "Mesečni plasman!",
                "message" to "Zauzeli ste $myRank. mesto i dobili $tokens tokena!",
                "timestamp" to System.currentTimeMillis(),
                "isRead" to false
            )).await()
        }
        db.collection("users").document(uid).update(updates).await()
        return if (tokens > 0) RewardResult.Earned(tokens, myRank, true) else RewardResult.None
    }

    suspend fun clearPendingWeeklyReward() {
        val uid = auth.currentUser?.uid ?: return
        runCatching {
            db.collection("users").document(uid).update(
                mapOf("pendingWeeklyRewardTokens" to 0, "pendingWeeklyRewardRank" to 0)
            ).await()
        }
    }

    suspend fun clearPendingMonthlyReward() {
        val uid = auth.currentUser?.uid ?: return
        runCatching {
            db.collection("users").document(uid).update(
                mapOf("pendingMonthlyRewardTokens" to 0, "pendingMonthlyRewardRank" to 0)
            ).await()
        }
    }

    suspend fun distributeWeeklyRewards(entries: List<LeaderboardEntry>) {
        val rewards = mapOf(1 to 5, 2 to 3, 3 to 2)
        entries.take(10).forEachIndexed { idx, entry ->
            val rank = idx + 1
            val tokens = rewards[rank] ?: if (rank in 4..10) 1 else return@forEachIndexed
            runCatching {
                db.collection("users").document(entry.uid).update(
                    "tokens", FieldValue.increment(tokens.toLong())
                ).await()
                db.collection("notifications").add(hashMapOf(
                    "userId" to entry.uid,
                    "channel" to "RANKING",
                    "title" to "Nedeljna nagrada!",
                    "message" to "Zauzeli ste $rank. mesto na nedeljnoj rang listi i dobili $tokens tokena!",
                    "timestamp" to System.currentTimeMillis(),
                    "isRead" to false
                )).await()
            }
        }
    }

    suspend fun distributeMonthlyRewards(entries: List<LeaderboardEntry>) {
        val rewards = mapOf(1 to 10, 2 to 6, 3 to 4)
        entries.take(10).forEachIndexed { idx, entry ->
            val rank = idx + 1
            val tokens = rewards[rank] ?: if (rank in 4..10) 2 else return@forEachIndexed
            runCatching {
                db.collection("users").document(entry.uid).update(
                    "tokens", FieldValue.increment(tokens.toLong())
                ).await()
                db.collection("notifications").add(hashMapOf(
                    "userId" to entry.uid,
                    "channel" to "RANKING",
                    "title" to "Mesečna nagrada!",
                    "message" to "Zauzeli ste $rank. mesto na mesečnoj rang listi i dobili $tokens tokena!",
                    "timestamp" to System.currentTimeMillis(),
                    "isRead" to false
                )).await()
            }
        }
    }

    suspend fun resetWeeklyStarsForCurrentUser() {
        val uid = auth.currentUser?.uid ?: return
        runCatching {
            db.collection("users").document(uid).update("weeklyStars", 0).await()
        }
    }

    suspend fun resetMonthlyStarsForCurrentUser() {
        val uid = auth.currentUser?.uid ?: return
        runCatching {
            db.collection("users").document(uid).update("monthlyStars", 0).await()
        }
    }

    suspend fun applyMonthlyStarPenaltyIfNeeded(monthlyEntries: List<LeaderboardEntry>) {
        val uid = auth.currentUser?.uid ?: return
        val placed = monthlyEntries.any { it.uid == uid }
        if (placed) return
        val doc = db.collection("users").document(uid).get().await()
        val stars = doc.getLong("stars")?.toInt() ?: return
        if (stars == 0) return
        val newStars = (stars * 0.7).toInt()
        val newLeague = UserRepository().leagueForStars(newStars)
        db.collection("users").document(uid).update(
            mapOf("stars" to newStars, "league" to newLeague)
        ).await()
    }
}
