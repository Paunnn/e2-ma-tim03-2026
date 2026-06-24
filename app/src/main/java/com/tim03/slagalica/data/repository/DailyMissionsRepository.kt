package com.tim03.slagalica.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.tim03.slagalica.data.model.DailyMissions
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DailyMissionsRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private fun today() = dateFmt.format(Date())

    private fun docId(): String? {
        val uid = auth.currentUser?.uid ?: return null
        return "${uid}_${today()}"
    }

    suspend fun getTodayMissions(): DailyMissions {
        val uid = auth.currentUser?.uid ?: return DailyMissions()
        val id = "${uid}_${today()}"
        val doc = db.collection("daily_missions").document(id).get().await()
        return if (doc.exists()) {
            DailyMissions(
                userId = uid,
                date = today(),
                winGame = doc.getBoolean("winGame") ?: false,
                sendMessage = doc.getBoolean("sendMessage") ?: false,
                playFriendly = doc.getBoolean("playFriendly") ?: false,
                winTournament = doc.getBoolean("winTournament") ?: false,
                bonusCollected = doc.getBoolean("bonusCollected") ?: false
            )
        } else {
            val newMission = DailyMissions(userId = uid, date = today())
            db.collection("daily_missions").document(id).set(
                mapOf(
                    "userId" to uid,
                    "date" to today(),
                    "winGame" to false,
                    "sendMessage" to false,
                    "playFriendly" to false,
                    "winTournament" to false,
                    "bonusCollected" to false
                )
            ).await()
            newMission
        }
    }

    // Returns true if mission was newly completed (was false before)
    suspend fun completeMission(field: String): Boolean {
        val id = docId() ?: return false
        val doc = db.collection("daily_missions").document(id).get().await()
        if (doc.getBoolean(field) == true) return false
        db.collection("daily_missions").document(id).update(field, true).await()
        return true
    }

    suspend fun completeWinGame() = completeMission("winGame")
    suspend fun completeSendMessage() = completeMission("sendMessage")
    suspend fun completePlayFriendly() = completeMission("playFriendly")
    suspend fun completeWinTournament() = completeMission("winTournament")

    // Awards bonus when all missions done (2 tokens + 3 stars), returns true if collected
    suspend fun collectBonusIfEligible(): Boolean {
        val uid = auth.currentUser?.uid ?: return false
        val id = "${uid}_${today()}"
        val doc = db.collection("daily_missions").document(id).get().await()
        if (doc.getBoolean("bonusCollected") == true) return false
        val allDone = listOf("winGame", "sendMessage", "playFriendly", "winTournament")
            .all { doc.getBoolean(it) == true }
        if (!allDone) return false

        db.collection("daily_missions").document(id).update("bonusCollected", true).await()
        db.collection("users").document(uid).update(
            mapOf(
                "tokens" to FieldValue.increment(2L),
                "stars" to FieldValue.increment(3L),
                "weeklyStars" to FieldValue.increment(3L),
                "monthlyStars" to FieldValue.increment(3L)
            )
        ).await()
        db.collection("notifications").add(
            mapOf(
                "userId" to uid,
                "channel" to "REWARD",
                "title" to "Sve dnevne misije!",
                "message" to "Završili ste sve dnevne misije. Dobili ste 2 tokena i 3 zvezde!",
                "timestamp" to System.currentTimeMillis(),
                "isRead" to false
            )
        ).await()
        return true
    }

    // Awards 3 stars for a single mission completion
    suspend fun awardMissionStars() {
        val uid = auth.currentUser?.uid ?: return
        runCatching {
            db.collection("users").document(uid).update(
                mapOf(
                    "stars" to FieldValue.increment(3L),
                    "weeklyStars" to FieldValue.increment(3L),
                    "monthlyStars" to FieldValue.increment(3L)
                )
            ).await()
        }
    }
}
