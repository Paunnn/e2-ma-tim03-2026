package com.tim03.slagalica.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.tim03.slagalica.data.model.DailyMissions
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DailyMissionsRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val userRepo = UserRepository()
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

    fun observeTodayMissions(onUpdate: (DailyMissions) -> Unit): ListenerRegistration {
        val uid = auth.currentUser?.uid
            ?: return db.collection("daily_missions").addSnapshotListener { _, _ -> }
        val id = "${uid}_${today()}"
        return db.collection("daily_missions").document(id).addSnapshotListener { snap, _ ->
            if (snap == null || !snap.exists()) {
                onUpdate(DailyMissions(userId = uid, date = today()))
                return@addSnapshotListener
            }
            onUpdate(DailyMissions(
                userId = uid,
                date = today(),
                winGame = snap.getBoolean("winGame") ?: false,
                sendMessage = snap.getBoolean("sendMessage") ?: false,
                playFriendly = snap.getBoolean("playFriendly") ?: false,
                winTournament = snap.getBoolean("winTournament") ?: false,
                bonusCollected = snap.getBoolean("bonusCollected") ?: false
            ))
        }
    }

    // Returns true if mission was newly completed (was false before).
    // Also awards 3 stars immediately so any caller path gets the reward.
    suspend fun completeMission(field: String): Boolean {
        val uid = auth.currentUser?.uid ?: return false
        val id = docId() ?: return false
        val docRef = db.collection("daily_missions").document(id)
        val doc = docRef.get().await()
        if (doc.getBoolean(field) == true) return false
        // set+merge instead of update: the day's doc may not exist yet if this is
        // the first mission action of the day and Home hasn't opened to create it.
        docRef.set(mapOf(field to true), SetOptions.merge()).await()
        runCatching { userRepo.adjustStarsAndLeague(uid, 3) }
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

        db.collection("daily_missions").document(id)
            .set(mapOf("bonusCollected" to true), SetOptions.merge()).await()
        db.collection("users").document(uid).update(
            "tokens", FieldValue.increment(2L)
        ).await()
        runCatching { userRepo.adjustStarsAndLeague(uid, 3) }
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

}
