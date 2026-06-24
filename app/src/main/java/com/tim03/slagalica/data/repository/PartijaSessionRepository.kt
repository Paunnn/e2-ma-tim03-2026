package com.tim03.slagalica.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.tim03.slagalica.data.model.PartijaSession
import kotlinx.coroutines.tasks.await

class PartijaSessionRepository {
    private val db = FirebaseFirestore.getInstance()
    private val sessions = db.collection("partija_sessions")

    fun listenToSession(
        sessionId: String,
        onUpdate: (PartijaSession) -> Unit
    ): ListenerRegistration {
        return sessions.document(sessionId).addSnapshotListener { snap, _ ->
            val doc = snap?.takeIf { it.exists() } ?: return@addSnapshotListener
            val session = PartijaSession(
                id = doc.id,
                player1Uid = doc.getString("player1Uid") ?: "",
                player2Uid = doc.getString("player2Uid") ?: "",
                player1Name = doc.getString("player1Name") ?: "",
                player2Name = doc.getString("player2Name") ?: "",
                playerUids = (doc.get("playerUids") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                status = doc.getString("status") ?: "active",
                forfeitedBy = doc.getString("forfeitedBy") ?: "",
                player1GameScores = parseScores(doc.get("player1GameScores")),
                player2GameScores = parseScores(doc.get("player2GameScores")),
                isTournament = doc.getBoolean("isTournament") ?: false,
                isFinal = doc.getBoolean("isFinal") ?: false,
                turnirId = doc.getString("turnirId") ?: ""
            )
            onUpdate(session)
        }
    }

    suspend fun submitGameScore(
        sessionId: String,
        isPlayer1: Boolean,
        gameIndex: Int,
        score: Int
    ) {
        val field = if (isPlayer1) "player1GameScores.$gameIndex" else "player2GameScores.$gameIndex"
        sessions.document(sessionId).update(field, score.toLong()).await()
    }

    suspend fun completeSession(sessionId: String) {
        runCatching { sessions.document(sessionId).update("status", "complete").await() }
    }

    suspend fun forfeit(sessionId: String, isPlayer1: Boolean) {
        runCatching {
            sessions.document(sessionId).update(mapOf(
                "status" to "forfeited",
                "forfeitedBy" to if (isPlayer1) "player1" else "player2"
            )).await()
        }
    }

    suspend fun getSession(sessionId: String): PartijaSession? {
        return try {
            val doc = sessions.document(sessionId).get().await()
            if (!doc.exists()) return null
            PartijaSession(
                id = doc.id,
                player1Uid = doc.getString("player1Uid") ?: "",
                player2Uid = doc.getString("player2Uid") ?: "",
                player1Name = doc.getString("player1Name") ?: "",
                player2Name = doc.getString("player2Name") ?: "",
                playerUids = (doc.get("playerUids") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                status = doc.getString("status") ?: "active",
                forfeitedBy = doc.getString("forfeitedBy") ?: "",
                player1GameScores = parseScores(doc.get("player1GameScores")),
                player2GameScores = parseScores(doc.get("player2GameScores")),
                isTournament = doc.getBoolean("isTournament") ?: false,
                isFinal = doc.getBoolean("isFinal") ?: false,
                turnirId = doc.getString("turnirId") ?: ""
            )
        } catch (e: Exception) { null }
    }

    private fun parseScores(raw: Any?): Map<String, Long> {
        return (raw as? Map<*, *>)
            ?.entries
            ?.mapNotNull { (k, v) -> (k as? String)?.let { key -> (v as? Long)?.let { key to it } } }
            ?.toMap()
            ?: emptyMap()
    }
}
