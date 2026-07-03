package com.tim03.slagalica.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.tim03.slagalica.data.model.TurnirSession
import kotlinx.coroutines.tasks.await

class TurnirRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val userRepo = UserRepository()
    private val queue = db.collection("tournament_queue")
    private val results = db.collection("tournament_results")
    private val turnirs = db.collection("turnir_sessions")
    private val sessions = db.collection("partija_sessions")

    suspend fun joinQueue(uid: String, username: String, league: Int, avatarIndex: Int) {
        queue.document(uid).set(
            mapOf(
                "uid" to uid,
                "username" to username,
                "league" to league,
                "avatarIndex" to avatarIndex,
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }

    suspend fun leaveQueue(uid: String) {
        runCatching { queue.document(uid).delete().await() }
        runCatching { results.document(uid).delete().await() }
    }

    fun listenToQueue(
        myUid: String,
        onQueueUpdate: (players: List<Map<String, Any>>) -> Unit
    ): ListenerRegistration {
        return queue.orderBy("createdAt").addSnapshotListener { snap, _ ->
            val docs = snap?.documents ?: return@addSnapshotListener
            val players = docs.map { doc ->
                mapOf(
                    "uid" to (doc.getString("uid") ?: ""),
                    "username" to (doc.getString("username") ?: ""),
                    "league" to (doc.getLong("league")?.toInt() ?: 0),
                    "avatarIndex" to (doc.getLong("avatarIndex")?.toInt() ?: 0)
                )
            }
            // Only notify if current user is in the queue
            if (players.any { it["uid"] == myUid }) {
                onQueueUpdate(players)
            }
        }
    }

    fun listenForMyResult(
        myUid: String,
        onResult: (turnirId: String) -> Unit
    ): ListenerRegistration {
        return results.document(myUid).addSnapshotListener { snap, _ ->
            val doc = snap?.takeIf { it.exists() } ?: return@addSnapshotListener
            val turnirId = doc.getString("turnirId") ?: return@addSnapshotListener
            onResult(turnirId)
        }
    }

    suspend fun deleteMyResult(myUid: String) {
        runCatching { results.document(myUid).delete().await() }
    }

    suspend fun tryCreateTournament(players: List<Map<String, Any>>): String? {
        return try {
            // A tournament is exactly 4 players; anyone past the 4th stays in the queue.
            val four = players.take(4)
            val uids = four.map { it["uid"] as String }
            val names = four.map { it["username"] as String }
            val leagues = four.map { (it["league"] as? Int) ?: 0 }
            val avatars = four.map { (it["avatarIndex"] as? Int) ?: 0 }

            val turnirRef = turnirs.document()

            // Create 2 semi-final sessions
            val semi1Ref = sessions.document()
            val semi2Ref = sessions.document()

            db.runTransaction { tx ->
                // Verify all 4 players still in queue
                uids.forEach { uid ->
                    val entry = tx.get(queue.document(uid))
                    if (!entry.exists()) throw Exception("Player no longer in queue")
                }

                // Create semi-final 1: players[0] vs players[1]
                tx.set(semi1Ref, mapOf(
                    "player1Uid" to uids[0], "player2Uid" to uids[1],
                    "player1Name" to names[0], "player2Name" to names[1],
                    "playerUids" to listOf(uids[0], uids[1]),
                    "status" to "active",
                    "isTournament" to true,
                    "turnirId" to turnirRef.id,
                    "player1GameScores" to mapOf<String, Any>(),
                    "player2GameScores" to mapOf<String, Any>(),
                    "createdAt" to FieldValue.serverTimestamp()
                ))

                // Create semi-final 2: players[2] vs players[3]
                tx.set(semi2Ref, mapOf(
                    "player1Uid" to uids[2], "player2Uid" to uids[3],
                    "player1Name" to names[2], "player2Name" to names[3],
                    "playerUids" to listOf(uids[2], uids[3]),
                    "status" to "active",
                    "isTournament" to true,
                    "turnirId" to turnirRef.id,
                    "player1GameScores" to mapOf<String, Any>(),
                    "player2GameScores" to mapOf<String, Any>(),
                    "createdAt" to FieldValue.serverTimestamp()
                ))

                // Create TurnirSession
                tx.set(turnirRef, mapOf(
                    "playerUids" to uids,
                    "playerNames" to names,
                    "playerLeagues" to leagues,
                    "playerAvatarIndices" to avatars,
                    "status" to "semifinal",
                    "semi1SessionId" to semi1Ref.id,
                    "semi2SessionId" to semi2Ref.id,
                    "finalSessionId" to "",
                    "semi1Winner" to "",
                    "semi2Winner" to "",
                    "tournamentWinner" to "",
                    "createdAt" to FieldValue.serverTimestamp()
                ))

                // Write result docs for each player
                uids.forEachIndexed { idx, uid ->
                    tx.set(results.document(uid), mapOf("turnirId" to turnirRef.id))
                }

                // Remove from queue
                uids.forEach { uid -> tx.delete(queue.document(uid)) }
            }.await()

            turnirRef.id
        } catch (e: Exception) {
            null
        }
    }

    fun listenToTurnir(
        turnirId: String,
        onUpdate: (TurnirSession) -> Unit
    ): ListenerRegistration {
        return turnirs.document(turnirId).addSnapshotListener { snap, _ ->
            val doc = snap?.takeIf { it.exists() } ?: return@addSnapshotListener
            val session = TurnirSession(
                id = doc.id,
                playerUids = (doc.get("playerUids") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                playerNames = (doc.get("playerNames") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                playerLeagues = (doc.get("playerLeagues") as? List<*>)?.mapNotNull { (it as? Long)?.toInt() } ?: emptyList(),
                playerAvatarIndices = (doc.get("playerAvatarIndices") as? List<*>)?.mapNotNull { (it as? Long)?.toInt() } ?: emptyList(),
                status = doc.getString("status") ?: "semifinal",
                semi1SessionId = doc.getString("semi1SessionId") ?: "",
                semi2SessionId = doc.getString("semi2SessionId") ?: "",
                finalSessionId = doc.getString("finalSessionId") ?: "",
                semi1Winner = doc.getString("semi1Winner") ?: "",
                semi2Winner = doc.getString("semi2Winner") ?: "",
                tournamentWinner = doc.getString("tournamentWinner") ?: "",
                createdAt = doc.getTimestamp("createdAt")?.toDate()?.time ?: 0L
            )
            onUpdate(session)
        }
    }

    // Runs as a single transaction: both semi winners can report at the same moment,
    // and exactly one of them creates the final (player1 = semi1 winner).
    suspend fun reportSemiFinalResult(turnirId: String, semiField: String, winnerUid: String) {
        val turnirRef = turnirs.document(turnirId)
        val finalRef = sessions.document()
        db.runTransaction { tx ->
            val doc = tx.get(turnirRef)
            val semi1Winner = if (semiField == "semi1Winner") winnerUid else doc.getString("semi1Winner") ?: ""
            val semi2Winner = if (semiField == "semi2Winner") winnerUid else doc.getString("semi2Winner") ?: ""
            val finalExists = (doc.getString("finalSessionId") ?: "").isNotEmpty()

            val updates = mutableMapOf<String, Any>(semiField to winnerUid)

            if (semi1Winner.isNotEmpty() && semi2Winner.isNotEmpty() && !finalExists) {
                val uids = (doc.get("playerUids") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                val names = (doc.get("playerNames") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                val nameByUid = uids.zip(names).toMap()
                tx.set(finalRef, mapOf(
                    "player1Uid" to semi1Winner, "player2Uid" to semi2Winner,
                    "player1Name" to (nameByUid[semi1Winner] ?: "Igrač 1"),
                    "player2Name" to (nameByUid[semi2Winner] ?: "Igrač 2"),
                    "playerUids" to listOf(semi1Winner, semi2Winner),
                    "status" to "active",
                    "isTournament" to true,
                    "isFinal" to true,
                    "turnirId" to turnirId,
                    "player1GameScores" to mapOf<String, Any>(),
                    "player2GameScores" to mapOf<String, Any>(),
                    "createdAt" to FieldValue.serverTimestamp()
                ))
                updates["finalSessionId"] = finalRef.id
                updates["status"] = "final"
            }

            tx.update(turnirRef, updates)
        }.await()
    }

    suspend fun reportFinalResult(turnirId: String, winnerUid: String, loserUid: String) {
        // Both finalists report the result; only the call that flips the status to
        // "completed" hands out the rewards, so the winner can't be rewarded twice.
        val shouldReward = db.runTransaction { tx ->
            val doc = tx.get(turnirs.document(turnirId))
            if (doc.getString("status") == "completed") return@runTransaction false
            tx.update(turnirs.document(turnirId),
                mapOf("tournamentWinner" to winnerUid, "status" to "completed"))
            true
        }.await()
        if (!shouldReward) return

        // Winner gets 3 extra tokens + 10 extra stars (on top of regular partija rewards)
        runCatching {
            db.collection("users").document(winnerUid).update(
                "tokens", FieldValue.increment(3L)
            ).await()
            userRepo.adjustStarsAndLeague(winnerUid, 10)
            db.collection("notifications").add(
                mapOf(
                    "userId" to winnerUid,
                    "channel" to "REWARD",
                    "title" to "Pobednik turnira!",
                    "message" to "Čestitamo! Pobedili ste turnir. Dobili ste 3 tokena i 10 bonus zvezda!",
                    "timestamp" to System.currentTimeMillis(),
                    "isRead" to false
                )
            ).await()
        }
    }

    suspend fun getTurnirSession(turnirId: String): TurnirSession? {
        return try {
            val doc = turnirs.document(turnirId).get().await()
            if (!doc.exists()) return null
            TurnirSession(
                id = doc.id,
                playerUids = (doc.get("playerUids") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                playerNames = (doc.get("playerNames") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                status = doc.getString("status") ?: "",
                semi1SessionId = doc.getString("semi1SessionId") ?: "",
                semi2SessionId = doc.getString("semi2SessionId") ?: "",
                finalSessionId = doc.getString("finalSessionId") ?: "",
                semi1Winner = doc.getString("semi1Winner") ?: "",
                semi2Winner = doc.getString("semi2Winner") ?: ""
            )
        } catch (e: Exception) { null }
    }
}
