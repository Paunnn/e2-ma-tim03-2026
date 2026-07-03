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

    // Fire-and-forget, called from MainActivity.onStop: leaving the app (home button,
    // screen off, closing the task) un-counts the player - not just an explicit logout.
    fun markInactiveAsync() {
        val user = auth.currentUser ?: return
        if (user.isAnonymous) return
        db.collection("users").document(user.uid).update("loggedIn", false)
    }

    // Called whenever the auth screens are shown: Firebase persists the previous
    // account's session across app restarts, and that account must not count as an
    // active player just because the app was launched.
    suspend fun clearPresence() {
        val user = auth.currentUser ?: return
        if (user.isAnonymous) return
        db.collection("users").document(user.uid).update("loggedIn", false).await()
    }

    // Presence heartbeat - runs every minute, but only while the user is past the
    // login screen (gated in AppNavigation). Also re-asserts loggedIn, so accounts
    // whose logout write was lost heal themselves.
    suspend fun updatePresence() {
        val user = auth.currentUser ?: return
        if (user.isAnonymous) return
        db.collection("users").document(user.uid).update(
            mapOf(
                "lastActive" to System.currentTimeMillis(),
                "loggedIn" to true
            )
        ).await()
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

        val oldLeague = user.league
        val newLeague = leagueForStars(newStars)

        val oldTokenMilestone = oldStars / 50
        val newTokenMilestone = newStars / 50
        val tokensFromStars = (newTokenMilestone - oldTokenMilestone).coerceAtLeast(0)

        val updates = mutableMapOf<String, Any>(
            "stars" to newStars,
            "league" to newLeague,
            "weeklyStars" to FieldValue.increment(actualDelta.toLong()),
            "monthlyStars" to FieldValue.increment(actualDelta.toLong()),
            // One partija played in both cycles - this is what makes the player ranked
            // (spec 4a), regardless of whether they won or lost stars.
            "weeklyGamesPlayed" to FieldValue.increment(1L),
            "monthlyGamesPlayed" to FieldValue.increment(1L)
        )
        if (tokensFromStars > 0) {
            updates["tokens"] = FieldValue.increment(tokensFromStars.toLong())
        }
        db.collection("users").document(uid).update(updates).await()

        // Notify on league change
        if (newLeague != oldLeague) {
            val leagueName = leagueNameFor(newLeague)
            val msg = if (newLeague > oldLeague)
                "Napredovali ste u $leagueName ligu! Čestitamo! Dnevni bonus: ${5 + newLeague} tokena."
            else
                "Pali ste u $leagueName ligu."
            db.collection("notifications").add(mapOf(
                "userId" to uid,
                "channel" to "OTHER",
                "title" to "Promena lige!",
                "message" to msg,
                "timestamp" to System.currentTimeMillis(),
                "isRead" to false
            )).await()
        }

        return StarAwardResult(newStars, newLeague, tokensFromStars, actualDelta, oldLeague)
    }

    suspend fun applyMonthlyPenaltyIfNeeded(): Boolean {
        val uid = auth.currentUser?.uid ?: return false
        val monthYear = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.getDefault())
            .format(java.util.Date())
        val user = getCurrentUser() ?: return false
        if (user.lastMonthlyCheck == monthYear) return false

        var penaltyApplied = false
        val updates = mutableMapOf<String, Any>(
            "lastMonthlyCheck" to monthYear,
            "monthlyStars" to 0,
            "monthlyGamesPlayed" to 0
        )

        // If user didn't earn any stars this month, apply 30% penalty
        if (user.monthlyStars == 0 && user.stars > 0) {
            val penalty = (user.stars * 0.3).toInt().coerceAtLeast(1)
            val newStars = (user.stars - penalty).coerceAtLeast(0)
            val newLeague = leagueForStars(newStars)
            updates["stars"] = newStars
            updates["league"] = newLeague
            penaltyApplied = true

            db.collection("notifications").add(mapOf(
                "userId" to uid,
                "channel" to "RANKING",
                "title" to "Mesečni ciklus završen",
                "message" to "Niste se plasirali na mesečnoj rang listi. Izgubili ste $penalty zvezda (30%).",
                "timestamp" to System.currentTimeMillis(),
                "isRead" to false
            )).await()
        }

        db.collection("users").document(uid).update(updates).await()
        return penaltyApplied
    }

    private fun leagueNameFor(league: Int) = when (league) {
        0 -> "Početnik"; 1 -> "Bronzanu"; 2 -> "Srebrnu"
        3 -> "Zlatnu"; 4 -> "Platinastnu"; 5 -> "Dijamantsku"
        else -> "Ligu $league"
    }

    data class StarLeagueChange(val newStars: Int, val oldLeague: Int, val newLeague: Int)

    // Central entry point for any star gain/loss (missions, izazov, turnir bonuses...)
    // so that `league` never drifts out of sync with `stars` and league-change
    // notifications always fire, regardless of which feature awarded the stars.
    suspend fun adjustStarsAndLeague(uid: String, delta: Int): StarLeagueChange {
        val docRef = db.collection("users").document(uid)
        val result = db.runTransaction { tx ->
            val snap = tx.get(docRef)
            val oldStars = snap.getLong("stars")?.toInt() ?: 0
            val oldLeague = snap.getLong("league")?.toInt() ?: 0
            val newStars = (oldStars + delta).coerceAtLeast(0)
            val actualDelta = newStars - oldStars
            val newLeague = leagueForStars(newStars)
            tx.update(docRef, mapOf(
                "stars" to newStars,
                "league" to newLeague,
                "weeklyStars" to FieldValue.increment(actualDelta.toLong()),
                "monthlyStars" to FieldValue.increment(actualDelta.toLong())
            ))
            StarLeagueChange(newStars, oldLeague, newLeague)
        }.await()

        if (result.oldLeague != result.newLeague) {
            val leagueName = leagueNameFor(result.newLeague)
            val msg = if (result.newLeague > result.oldLeague)
                "Napredovali ste u $leagueName ligu! Čestitamo! Dnevni bonus: ${5 + result.newLeague} tokena."
            else
                "Pali ste u $leagueName ligu."
            runCatching {
                db.collection("notifications").add(mapOf(
                    "userId" to uid,
                    "channel" to "OTHER",
                    "title" to "Promena lige!",
                    "message" to msg,
                    "timestamp" to System.currentTimeMillis(),
                    "isRead" to false
                )).await()
            }
        }
        return result
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
        val updates = if (isWeekly) mapOf("weeklyStars" to 0, "weeklyGamesPlayed" to 0)
                      else mapOf("monthlyStars" to 0, "monthlyGamesPlayed" to 0)
        db.collection("users").document(uid).update(updates).await()
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

    suspend fun checkAndRefreshDailyTokens(): Int {
        val uid = auth.currentUser?.uid ?: return 0
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val user = getCurrentUser() ?: return 0
        if (user.lastDailyTokenDate == today) return 0
        val bonus = 5 + user.league
        db.collection("users").document(uid).update(
            mapOf(
                "tokens" to com.google.firebase.firestore.FieldValue.increment(bonus.toLong()),
                "lastDailyTokenDate" to today
            )
        ).await()
        return bonus
    }

    fun logout() {
        // Clear the flag before signing out - after signOut the write could be rejected.
        auth.currentUser?.takeIf { !it.isAnonymous }?.let {
            db.collection("users").document(it.uid).update("loggedIn", false)
        }
        auth.signOut()
    }
}

data class StarAwardResult(
    val newStars: Int,
    val newLeague: Int,
    val tokensEarned: Int,
    val starDelta: Int,
    val oldLeague: Int = newLeague
)
