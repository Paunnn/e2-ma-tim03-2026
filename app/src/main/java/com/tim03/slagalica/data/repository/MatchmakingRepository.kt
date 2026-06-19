package com.tim03.slagalica.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await

class MatchmakingRepository {
    private val db = FirebaseFirestore.getInstance()
    private val queue = db.collection("matchmaking_queue")
    private val results = db.collection("matchmaking_results")
    private val sessions = db.collection("partija_sessions")

    suspend fun joinQueue(uid: String, username: String) {
        queue.document(uid).set(
            mapOf(
                "uid" to uid,
                "username" to username,
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
        onOpponentFound: (uid: String, name: String) -> Unit
    ): ListenerRegistration {
        return queue.addSnapshotListener { snap, _ ->
            val opponent = snap?.documents?.firstOrNull { it.id != myUid } ?: return@addSnapshotListener
            val uid = opponent.getString("uid") ?: return@addSnapshotListener
            val name = opponent.getString("username") ?: "Protivnik"
            onOpponentFound(uid, name)
        }
    }

    // Atomically creates the session and notifies both players via matchmaking_results.
    // Returns Pair(sessionId, isPlayer1) to the creator; the other player reads their result doc.
    // Returns null if we lost the race (other device already created the session).
    suspend fun tryCreateSession(
        myUid: String,
        myName: String,
        opponentUid: String,
        opponentName: String
    ): Pair<String, Boolean>? {
        val iAmPlayer1 = (0..1).random() == 0
        val p1Uid = if (iAmPlayer1) myUid else opponentUid
        val p1Name = if (iAmPlayer1) myName else opponentName
        val p2Uid = if (iAmPlayer1) opponentUid else myUid
        val p2Name = if (iAmPlayer1) opponentName else myName

        return try {
            val sessionRef = sessions.document()
            db.runTransaction { tx ->
                val myEntry = tx.get(queue.document(myUid))
                val opEntry = tx.get(queue.document(opponentUid))
                if (!myEntry.exists() || !opEntry.exists()) {
                    throw Exception("Players no longer in queue")
                }
                tx.set(
                    sessionRef, mapOf(
                        "player1Uid" to p1Uid,
                        "player2Uid" to p2Uid,
                        "player1Name" to p1Name,
                        "player2Name" to p2Name,
                        "playerUids" to listOf(p1Uid, p2Uid),
                        "status" to "active",
                        "player1GameScores" to mapOf<String, Any>(),
                        "player2GameScores" to mapOf<String, Any>(),
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
                // Write a result doc for each player so both are notified simultaneously.
                tx.set(
                    results.document(p1Uid), mapOf(
                        "sessionId" to sessionRef.id,
                        "isPlayer1" to true,
                        "opponentName" to p2Name
                    )
                )
                tx.set(
                    results.document(p2Uid), mapOf(
                        "sessionId" to sessionRef.id,
                        "isPlayer1" to false,
                        "opponentName" to p1Name
                    )
                )
                tx.delete(queue.document(myUid))
                tx.delete(queue.document(opponentUid))
            }.await()
            Pair(sessionRef.id, iAmPlayer1)
        } catch (e: Exception) {
            null
        }
    }

    // Each player listens to their own result doc written by the transaction above.
    // This avoids complex queries and old-session confusion.
    fun listenForMyResult(
        myUid: String,
        onResult: (sessionId: String, isPlayer1: Boolean, opponentName: String) -> Unit
    ): ListenerRegistration {
        return results.document(myUid).addSnapshotListener { snap, _ ->
            val doc = snap?.takeIf { it.exists() } ?: return@addSnapshotListener
            val sessionId = doc.getString("sessionId") ?: return@addSnapshotListener
            val isPlayer1 = doc.getBoolean("isPlayer1") ?: true
            val opponentName = doc.getString("opponentName") ?: "Protivnik"
            onResult(sessionId, isPlayer1, opponentName)
        }
    }

    suspend fun deleteMyResult(myUid: String) {
        runCatching { results.document(myUid).delete().await() }
    }
}
