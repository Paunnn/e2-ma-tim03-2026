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
        val uid = auth.currentUser?.uid ?: return null
        val doc = db.collection("users").document(uid).get().await()
        return doc.toObject(User::class.java)?.copy(uid = uid)
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

    fun logout() = auth.signOut()
}
