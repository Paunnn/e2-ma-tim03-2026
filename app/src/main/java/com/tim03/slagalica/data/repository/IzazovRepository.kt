package com.tim03.slagalica.data.repository

import android.util.Log
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

    companion object {
        // How long a challenge stays open for joining/playing before it auto-finalizes.
        const val IZAZOV_TRAJANJE_MS = 30 * 60 * 1000L
    }

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
        val now = System.currentTimeMillis()
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
            "createdAt" to now,
            "expiresAt" to now + IZAZOV_TRAJANJE_MS
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
        finalize(izazovId)
    }

    private data class FinalizeData(
        val participants: List<String>,
        val scores: Map<String, Long>,
        val bidStars: Int,
        val bidTokens: Int
    )

    // Finalizes a challenge when it's due:
    //  - full (4 players) and everyone played, or
    //  - the poster closes it early (all current participants played, or cancels alone), or
    //  - the deadline passed (whoever didn't play forfeits their stake).
    // The open->finalizing flip runs in a transaction so only one client pays out.
    suspend fun finalize(izazovId: String, byPosterEarly: Boolean = false) {
        val ref = col.document(izazovId)
        val data = runCatching {
            db.runTransaction { tx ->
                val doc = tx.get(ref)
                if (doc.getString("status") != "open") return@runTransaction null
                val participants = (doc.get("participants") as? List<*>)?.filterIsInstance<String>()
                    ?: return@runTransaction null
                val scores = (doc.get("scores") as? Map<*, *>)?.mapNotNull { (k, v) ->
                    (k as? String)?.let { key -> (v as? Long)?.let { key to it } }
                }?.toMap() ?: emptyMap()
                val createdAt = doc.getLong("createdAt") ?: 0L
                val expiresAt = doc.getLong("expiresAt") ?: (createdAt + IZAZOV_TRAJANJE_MS)
                val expired = System.currentTimeMillis() > expiresAt
                val allPlayed = participants.all { scores.containsKey(it) }
                val due = expired ||
                    (allPlayed && participants.size >= 4) ||
                    (byPosterEarly && (allPlayed || participants.size == 1))
                if (!due) return@runTransaction null
                tx.update(ref, "status", "finalizing")
                FinalizeData(
                    participants, scores,
                    (doc.getLong("bidStars") ?: 0).toInt(),
                    (doc.getLong("bidTokens") ?: 0).toInt()
                )
            }.await()
        }.onFailure { Log.e("IzazovDbg", "finalize transaction failed", it) }.getOrNull() ?: return

        val played = data.participants.filter { data.scores.containsKey(it) }

        // No competition happened (nobody joined, or nobody played) - everyone gets
        // their stake back and the challenge closes without a winner.
        if (played.isEmpty() || data.participants.size == 1) {
            val batch = db.batch()
            if (data.bidTokens > 0) data.participants.forEach { uid ->
                batch.update(users.document(uid), "tokens", FieldValue.increment(data.bidTokens.toLong()))
            }
            batch.update(ref, mapOf("status" to "completed", "winnerId" to "", "runnerId" to ""))
            batch.commit().await()
            if (data.bidStars > 0) data.participants.forEach { userRepo.adjustStarsAndLeague(it, data.bidStars) }
            return
        }

        // Winner takes 75% of the total pot; runner-up (among those who played) gets
        // their stake back; joiners who never played forfeit theirs.
        val sorted = played.sortedByDescending { data.scores[it] ?: 0L }
        val winnerId = sorted[0]
        val runnerId = sorted.getOrNull(1) ?: ""
        val winnerStars = (data.bidStars * data.participants.size * 0.75).toInt()
        val winnerTokens = (data.bidTokens * data.participants.size * 0.75).toInt()

        val batch = db.batch()
        batch.update(ref, mapOf(
            "status" to "completed",
            "winnerId" to winnerId,
            "runnerId" to runnerId
        ))
        if (winnerTokens > 0) batch.update(users.document(winnerId), "tokens", FieldValue.increment(winnerTokens.toLong()))
        if (runnerId.isNotEmpty() && data.bidTokens > 0) {
            batch.update(users.document(runnerId), "tokens", FieldValue.increment(data.bidTokens.toLong()))
        }
        batch.commit().await()

        // Stars go through adjustStarsAndLeague (its own transaction) so league stays in sync
        if (winnerStars > 0) userRepo.adjustStarsAndLeague(winnerId, winnerStars)
        if (runnerId.isNotEmpty() && data.bidStars > 0) userRepo.adjustStarsAndLeague(runnerId, data.bidStars)
    }

    // Listens to recent sessions (open AND completed - the spec wants results shown on
    // the page after a challenge finishes). Region matching happens client-side and
    // tolerantly: an exact server-side string equality made a challenge invisible to the
    // whole region whenever one account's region value differed (blank, whitespace, case).
    fun listenToOpenSessions(region: String, myUid: String, onSessions: (List<IzazovSession>) -> Unit): ListenerRegistration {
        return col.orderBy("createdAt", Query.Direction.DESCENDING).limit(50)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    Log.e("IzazovDbg", "listenToOpenSessions failed", error)
                    return@addSnapshotListener
                }
                val norm = { s: String -> s.trim().lowercase() }
                val myRegion = norm(region)
                val list = snap?.documents
                    ?.mapNotNull { doc -> parseSession(doc.id, doc.data) }
                    ?.filter { s ->
                        s.participants.contains(myUid) ||
                            s.region.isBlank() || myRegion.isBlank() ||
                            norm(s.region) == myRegion ||
                            norm(s.region) == "srbija" || myRegion == "srbija"
                    }
                    // Open challenges first, then finished ones with results, newest first.
                    ?.sortedWith(compareBy({ it.status != "open" }, { -it.createdAt }))
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
                createdAt = data["createdAt"] as? Long ?: 0L,
                expiresAt = data["expiresAt"] as? Long
                    ?: ((data["createdAt"] as? Long ?: 0L) + IZAZOV_TRAJANJE_MS)
            )
        } catch (_: Exception) { null }
    }
}
