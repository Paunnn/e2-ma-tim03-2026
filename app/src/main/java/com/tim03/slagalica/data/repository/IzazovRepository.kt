package com.tim03.slagalica.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.tim03.slagalica.data.model.IzazovSession
import kotlinx.coroutines.tasks.await

class IzazovRepository {
    private val db = FirebaseFirestore.getInstance()
    private val userRepo = UserRepository()
    private val col = db.collection("izazov_sessions")
    private val users = db.collection("users")

    suspend fun createIzazov(
        posterId: String,
        posterName: String,
        region: String,
        bidStars: Int,
        bidTokens: Int
    ): String {
        if (bidTokens > 0) users.document(posterId).update("tokens", FieldValue.increment(-bidTokens.toLong())).await()
        if (bidStars > 0) userRepo.adjustStarsAndLeague(posterId, -bidStars)

        val ref = col.document()
        ref.set(mapOf(
            "posterId" to posterId,
            "posterName" to posterName,
            "region" to region,
            "bidStars" to bidStars,
            "bidTokens" to bidTokens,
            "participants" to listOf(posterId),
            "participantNames" to listOf(posterName),
            "scores" to mapOf<String, Any>(),
            "status" to "open",
            "winnerId" to "",
            "runnerId" to "",
            "createdAt" to System.currentTimeMillis()
        )).await()

        // Notify region members about new challenge
        runCatching {
            val regionFilter = if (region == "Srbija") "" else region
            val regionUsers = users.whereEqualTo("region", regionFilter).get().await()
            val timestamp = System.currentTimeMillis()
            val batch = db.batch()
            regionUsers.documents.forEach { doc ->
                if (doc.id != posterId) {
                    batch.set(db.collection("notifications").document(), mapOf(
                        "userId" to doc.id,
                        "channel" to "OTHER",
                        "title" to "Novi izazov! ⚔",
                        "message" to "$posterName je postavio izazov (${bidStars}★, ${bidTokens}T). Priključi se!",
                        "timestamp" to timestamp,
                        "isRead" to false
                    ))
                }
            }
            batch.commit().await()
        }

        return ref.id
    }

    suspend fun joinIzazov(
        izazovId: String,
        uid: String,
        username: String,
        bidStars: Int,
        bidTokens: Int
    ) {
        if (bidTokens > 0) users.document(uid).update("tokens", FieldValue.increment(-bidTokens.toLong())).await()
        if (bidStars > 0) userRepo.adjustStarsAndLeague(uid, -bidStars)

        col.document(izazovId).update(mapOf(
            "participants" to FieldValue.arrayUnion(uid),
            "participantNames" to FieldValue.arrayUnion(username)
        )).await()
    }

    suspend fun submitScore(izazovId: String, uid: String, score: Int) {
        col.document(izazovId).update("scores.$uid", score.toLong()).await()
        checkAndFinalize(izazovId)
    }

    private suspend fun checkAndFinalize(izazovId: String) {
        val doc = col.document(izazovId).get().await()
        val participants = (doc.get("participants") as? List<*>)?.filterIsInstance<String>() ?: return
        val scores = (doc.get("scores") as? Map<*, *>)?.mapNotNull {
            val k = it.key as? String ?: return@mapNotNull null
            val v = (it.value as? Long) ?: return@mapNotNull null
            k to v
        }?.toMap() ?: emptyMap()

        if (scores.size < participants.size) return // not everyone played yet

        val bidStars = (doc.getLong("bidStars") ?: 0).toInt()
        val bidTokens = (doc.getLong("bidTokens") ?: 0).toInt()
        val totalPotStars = bidStars * participants.size
        val totalPotTokens = bidTokens * participants.size

        val sorted = scores.entries.sortedByDescending { it.value }
        val winnerId = sorted.getOrNull(0)?.key ?: return
        val runnerId = sorted.getOrNull(1)?.key ?: ""

        // Winner gets 75% of pot
        val winnerStars = (totalPotStars * 0.75).toInt()
        val winnerTokens = (totalPotTokens * 0.75).toInt()
        // Runner-up gets back their bid
        val runnerStars = bidStars
        val runnerTokens = bidTokens

        val batch = db.batch()
        batch.update(col.document(izazovId), mapOf(
            "status" to "completed",
            "winnerId" to winnerId,
            "runnerId" to runnerId
        ))
        if (winnerTokens > 0) batch.update(users.document(winnerId), "tokens", FieldValue.increment(winnerTokens.toLong()))
        if (runnerId.isNotEmpty() && runnerTokens > 0) {
            batch.update(users.document(runnerId), "tokens", FieldValue.increment(runnerTokens.toLong()))
        }
        batch.commit().await()

        // Stars go through adjustStarsAndLeague (its own transaction) so league stays in sync
        if (winnerStars > 0) userRepo.adjustStarsAndLeague(winnerId, winnerStars)
        if (runnerId.isNotEmpty() && runnerStars > 0) userRepo.adjustStarsAndLeague(runnerId, runnerStars)
    }

    fun listenToOpenSessions(region: String, onSessions: (List<IzazovSession>) -> Unit): ListenerRegistration {
        var query: Query = col.whereEqualTo("status", "open")
        if (region.isNotEmpty()) query = query.whereEqualTo("region", region)
        return query.limit(20).addSnapshotListener { snap, _ ->
            val list = snap?.documents
                ?.mapNotNull { doc -> parseSession(doc.id, doc.data) }
                ?.sortedByDescending { it.createdAt }
                ?: emptyList()
            onSessions(list)
        }
    }

    fun listenToSession(izazovId: String, onUpdate: (IzazovSession) -> Unit): ListenerRegistration {
        return col.document(izazovId).addSnapshotListener { snap, _ ->
            val doc = snap?.takeIf { it.exists() } ?: return@addSnapshotListener
            parseSession(doc.id, doc.data)?.let { onUpdate(it) }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseSession(id: String, data: Map<String, Any>?): IzazovSession? {
        if (data == null) return null
        return try {
            val scoresRaw = data["scores"] as? Map<String, Any> ?: emptyMap()
            val scores = scoresRaw.mapValues { (it.value as? Long) ?: 0L }
            IzazovSession(
                id = id,
                posterId = data["posterId"] as? String ?: "",
                posterName = data["posterName"] as? String ?: "",
                region = data["region"] as? String ?: "",
                bidStars = (data["bidStars"] as? Long)?.toInt() ?: 0,
                bidTokens = (data["bidTokens"] as? Long)?.toInt() ?: 0,
                participants = (data["participants"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                participantNames = (data["participantNames"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                scores = scores,
                status = data["status"] as? String ?: "open",
                winnerId = data["winnerId"] as? String ?: "",
                runnerId = data["runnerId"] as? String ?: "",
                createdAt = data["createdAt"] as? Long ?: 0L
            )
        } catch (_: Exception) { null }
    }
}
