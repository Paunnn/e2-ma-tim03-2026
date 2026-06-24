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
            val uids = players.map { it["uid"] as String }
            val names = players.map { it["username"] as String }
            val leagues = players.map { (it["league"] as? Int) ?: 0 }
            val avatars = players.map { (it["avatarIndex"] as? Int) ?: 0 }

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
                createdAt = doc.getLong("createdAt") ?: 0L
            )
            onUpdate(session)
        }
    }

    suspend fun reportSemiFinalResult(turnirId: String, semiField: String, winnerUid: String) {
        val turnirDoc = turnirs.document(turnirId).get().await()
        val semi1Winner = turnirDoc.getString("semi1Winner") ?: ""
        val semi2Winner = turnirDoc.getString("semi2Winner") ?: ""

        turnirs.document(turnirId).update(semiField, winnerUid).await()

        val newSemi1 = if (semiField == "semi1Winner") winnerUid else semi1Winner
        val newSemi2 = if (semiField == "semi2Winner") winnerUid else semi2Winner

        if (newSemi1.isNotEmpty() && newSemi2.isNotEmpty()) {
            // Both semis done → create final
            val playerData = turnirDoc.let { doc ->
                val uids = (doc.get("playerUids") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                val names = (doc.get("playerNames") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                uids.zip(names).toMap()
            }
            val finalRef = sessions.document()
            val p1Name = playerData[newSemi1] ?: "Igrač 1"
            val p2Name = playerData[newSemi2] ?: "Igrač 2"
            finalRef.set(mapOf(
                "player1Uid" to newSemi1, "player2Uid" to newSemi2,
                "player1Name" to p1Name, "player2Name" to p2Name,
                "playerUids" to listOf(newSemi1, newSemi2),
                "status" to "active",
                "isTournament" to true,
                "isFinal" to true,
                "turnirId" to turnirId,
                "player1GameScores" to mapOf<String, Any>(),
                "player2GameScores" to mapOf<String, Any>(),
                "createdAt" to FieldValue.serverTimestamp()
            )).await()

            turnirs.document(turnirId).update(
                mapOf(
                    "finalSessionId" to finalRef.id,
                    "status" to "final"
                )
            ).await()
        }
    }

    suspend fun reportFinalResult(turnirId: String, winnerUid: String, loserUid: String) {
        turnirs.document(turnirId).update(
            mapOf("tournamentWinner" to winnerUid, "status" to "completed")
        ).await()

        // Winner gets 3 extra tokens + 10 extra stars (on top of regular partija rewards)
        runCatching {
            db.collection("users").document(winnerUid).update(
                mapOf(
                    "tokens" to FieldValue.increment(3L),
                    "stars" to FieldValue.increment(10L),
                    "weeklyStars" to FieldValue.increment(10L),
                    "monthlyStars" to FieldValue.increment(10L)
                )
            ).await()
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

    suspend fun getSessionIsPlayer1(sessionId: String, myUid: String): Boolean {
        val doc = sessions.document(sessionId).get().await()
        return doc.getString("player1Uid") == myUid
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
