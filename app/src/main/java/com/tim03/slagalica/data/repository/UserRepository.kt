package com.tim03.slagalica.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.tim03.slagalica.data.model.User
import kotlinx.coroutines.tasks.await

class UserRepository {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    suspend fun getCurrentUser(): User? {
        val firebaseUser = auth.currentUser ?: return null
        if (firebaseUser.isAnonymous) {
            return User(uid = firebaseUser.uid, username = firebaseUser.displayName ?: "Guest")
        }
        val doc = db.collection("users").document(firebaseUser.uid).get().await()
        return doc.toObject(User::class.java)?.copy(uid = firebaseUser.uid)
    }

    suspend fun updateAvatar(avatarIndex: Int) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).update("avatarIndex", avatarIndex).await()
    }

    suspend fun saveKoZnaZnaResult(correct: Int, incorrect: Int) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).update(
            mapOf(
                "koZnaZnaCorrect" to FieldValue.increment(correct.toLong()),
                "koZnaZnaIncorrect" to FieldValue.increment(incorrect.toLong()),
                "koZnaZnaRounds" to FieldValue.increment(1L)
            )
        ).await()
    }

    suspend fun saveSpojniceResult(connected: Int, totalPairs: Int) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).update(
            mapOf(
                "spojniceConnected" to FieldValue.increment(connected.toLong()),
                "spojniceTotalPairs" to FieldValue.increment(totalPairs.toLong()),
                "spojniceRounds" to FieldValue.increment(1L)
            )
        ).await()
    }

    suspend fun saveGameResult(won: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        val field = if (won) "gamesWon" else "gamesLost"
        db.collection("users").document(uid).update(field, FieldValue.increment(1L)).await()
    }

    suspend fun saveMojBrojResult(hits: Int, rounds: Int) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).update(
            mapOf(
                "mojBrojHits" to FieldValue.increment(hits.toLong()),
                "mojBrojRounds" to FieldValue.increment(rounds.toLong())
            )
        ).await()
    }

    suspend fun saveKorakPoKorakResult(correctStep: Int?) {
        val uid = auth.currentUser?.uid ?: return
        val updates = mutableMapOf<String, Any>(
            "korakRounds" to FieldValue.increment(1L)
        )
        if (correctStep != null && correctStep in 1..7) {
            updates["korakStep$correctStep"] = FieldValue.increment(1L)
        }
        db.collection("users").document(uid).update(updates).await()
    }

    suspend fun saveAsocijacijeResult(solved: Int, total: Int) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).update(
            mapOf(
                "asocijacijeSolved" to FieldValue.increment(solved.toLong()),
                "asocijacijeTotal" to FieldValue.increment(total.toLong())
            )
        ).await()
    }

    suspend fun saveSkockoResult(solvedAtAttempt: Int?, rounds: Int) {
        val uid = auth.currentUser?.uid ?: return
        val updates = mutableMapOf<String, Any>(
            "skockoRounds" to FieldValue.increment(rounds.toLong())
        )
        if (solvedAtAttempt != null && solvedAtAttempt in 1..6) {
            updates["skockoAttempt$solvedAtAttempt"] = FieldValue.increment(1L)
        }
        db.collection("users").document(uid).update(updates).await()
    }

    suspend fun savePartijaResult(won: Boolean, drawn: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        val field = when {
            won -> "partijaWon"
            drawn -> "partijaDrawn"
            else -> "partijaLost"
        }
        db.collection("users").document(uid).update(field, FieldValue.increment(1L)).await()
    }

    // Returns (newStars, newLeague, tokensEarned, starDelta)
    suspend fun awardStarsFromPartija(won: Boolean, totalPoints: Int, isFriendly: Boolean): StarAwardResult {
        if (isFriendly) return StarAwardResult(0, 0, 0, 0)
        val uid = auth.currentUser?.uid ?: return StarAwardResult(0, 0, 0, 0)
        val user = getCurrentUser() ?: return StarAwardResult(0, 0, 0, 0)

        val starsFromPoints = totalPoints / 40
        val rawDelta = if (won) 10 + starsFromPoints else -10 + starsFromPoints
        val oldStars = user.stars
        val newStars = (oldStars + rawDelta).coerceAtLeast(0)
        val actualDelta = newStars - oldStars

        val newLeague = leagueForStars(newStars)

        val oldTokenMilestone = oldStars / 50
        val newTokenMilestone = newStars / 50
        val tokensFromStars = (newTokenMilestone - oldTokenMilestone).coerceAtLeast(0)

        val updates = mutableMapOf<String, Any>(
            "stars" to newStars,
            "league" to newLeague,
            "weeklyStars" to FieldValue.increment(actualDelta.toLong()),
            "monthlyStars" to FieldValue.increment(actualDelta.toLong())
        )
        if (tokensFromStars > 0) {
            updates["tokens"] = FieldValue.increment(tokensFromStars.toLong())
        }
        db.collection("users").document(uid).update(updates).await()
        return StarAwardResult(newStars, newLeague, tokensFromStars, actualDelta)
    }

    suspend fun addTokens(uid: String, count: Int) {
        if (count == 0) return
        db.collection("users").document(uid).update("tokens", FieldValue.increment(count.toLong())).await()
    }

    suspend fun addStars(uid: String, count: Int) {
        val doc = db.collection("users").document(uid).get().await()
        val oldStars = doc.getLong("stars")?.toInt() ?: 0
        val newStars = (oldStars + count).coerceAtLeast(0)
        val newLeague = leagueForStars(newStars)
        db.collection("users").document(uid).update(
            mapOf(
                "stars" to newStars,
                "league" to newLeague,
                "weeklyStars" to FieldValue.increment(count.toLong()),
                "monthlyStars" to FieldValue.increment(count.toLong())
            )
        ).await()
    }

    suspend fun resetCycleStars(isWeekly: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        val field = if (isWeekly) "weeklyStars" else "monthlyStars"
        db.collection("users").document(uid).update(field, 0).await()
    }

    suspend fun saveFcmToken(token: String) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).update("fcmToken", token).await()
    }

    fun leagueForStars(stars: Int): Int = when {
        stars >= 1600 -> 5
        stars >= 800 -> 4
        stars >= 400 -> 3
        stars >= 200 -> 2
        stars >= 100 -> 1
        else -> 0
    }

    fun logout() = auth.signOut()
}

data class StarAwardResult(
    val newStars: Int,
    val newLeague: Int,
    val tokensEarned: Int,
    val starDelta: Int
)
