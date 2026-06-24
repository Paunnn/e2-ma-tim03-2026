package com.tim03.slagalica.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.tim03.slagalica.data.model.LeaderboardEntry
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class LeaderboardRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val fmt = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())

    suspend fun getWeeklyLeaderboard(): List<LeaderboardEntry> {
        val snapshot = db.collection("users")
            .whereGreaterThan("weeklyStars", 0)
            .get().await()
        return snapshot.documents
            .mapNotNull { doc ->
                val uid = doc.id
                val username = doc.getString("username") ?: return@mapNotNull null
                LeaderboardEntry(
                    uid = uid,
                    username = username,
                    stars = doc.getLong("stars")?.toInt() ?: 0,
                    weeklyStars = doc.getLong("weeklyStars")?.toInt() ?: 0,
                    monthlyStars = doc.getLong("monthlyStars")?.toInt() ?: 0,
                    league = doc.getLong("league")?.toInt() ?: 0,
                    avatarIndex = doc.getLong("avatarIndex")?.toInt() ?: 0,
                    region = doc.getString("region") ?: ""
                )
            }
            .sortedByDescending { it.weeklyStars }
            .mapIndexed { i, e -> e.copy(rank = i + 1) }
    }

    suspend fun getMonthlyLeaderboard(): List<LeaderboardEntry> {
        val snapshot = db.collection("users")
            .whereGreaterThan("monthlyStars", 0)
            .get().await()
        return snapshot.documents
            .mapNotNull { doc ->
                val uid = doc.id
                val username = doc.getString("username") ?: return@mapNotNull null
                LeaderboardEntry(
                    uid = uid,
                    username = username,
                    stars = doc.getLong("stars")?.toInt() ?: 0,
                    weeklyStars = doc.getLong("weeklyStars")?.toInt() ?: 0,
                    monthlyStars = doc.getLong("monthlyStars")?.toInt() ?: 0,
                    league = doc.getLong("league")?.toInt() ?: 0,
                    avatarIndex = doc.getLong("avatarIndex")?.toInt() ?: 0,
                    region = doc.getString("region") ?: ""
                )
            }
            .sortedByDescending { it.monthlyStars }
            .mapIndexed { i, e -> e.copy(rank = i + 1) }
    }

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

    // Distributes tokens to top players at cycle end and saves notification
    suspend fun distributeWeeklyRewards(entries: List<LeaderboardEntry>) {
        val rewards = mapOf(1 to 5, 2 to 3, 3 to 2)
        entries.take(10).forEachIndexed { idx, entry ->
            val rank = idx + 1
            val tokens = rewards[rank] ?: if (rank in 4..10) 1 else return@forEachIndexed
            runCatching {
                db.collection("users").document(entry.uid).update(
                    "tokens", FieldValue.increment(tokens.toLong())
                ).await()
                db.collection("notifications").add(
                    hashMapOf(
                        "userId" to entry.uid,
                        "channel" to "RANKING",
                        "title" to "Nedeljna nagrada!",
                        "message" to "Zauzeli ste $rank. mesto na nedeljnoj rang listi i dobili $tokens tokena!",
                        "timestamp" to System.currentTimeMillis(),
                        "isRead" to false
                    )
                ).await()
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
                db.collection("notifications").add(
                    hashMapOf(
                        "userId" to entry.uid,
                        "channel" to "RANKING",
                        "title" to "Mesečna nagrada!",
                        "message" to "Zauzeli ste $rank. mesto na mesečnoj rang listi i dobili $tokens tokena!",
                        "timestamp" to System.currentTimeMillis(),
                        "isRead" to false
                    )
                ).await()
            }
        }
    }

    // Resets weeklyStars for all users - called at start of new week cycle
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

    // Checks if current user should lose 30% stars for not placing on monthly ranking
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
