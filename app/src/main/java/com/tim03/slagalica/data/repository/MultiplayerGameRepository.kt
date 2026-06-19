package com.tim03.slagalica.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await

class MultiplayerGameRepository {
    private val db = FirebaseFirestore.getInstance()

    private fun sessionRef(id: String) = db.collection("partija_sessions").document(id)

    // P1 writes initial data for the current game; sets gGameIdx, gPhase, gData
    suspend fun initGame(sessionId: String, gameIdx: Int, phase: String, data: Map<String, Any>) {
        sessionRef(sessionId).update(
            mapOf("gGameIdx" to gameIdx.toLong(), "gPhase" to phase, "gData" to data)
        ).await()
    }

    // Listen for game state changes; both players use this
    fun listenToGameState(
        sessionId: String,
        onUpdate: (gameIdx: Int, phase: String, data: Map<String, Any>) -> Unit
    ): ListenerRegistration {
        return sessionRef(sessionId).addSnapshotListener { snap, _ ->
            val doc = snap?.takeIf { it.exists() } ?: return@addSnapshotListener
            val gameIdx = (doc.getLong("gGameIdx") ?: -1L).toInt()
            val phase = doc.getString("gPhase") ?: return@addSnapshotListener
            @Suppress("UNCHECKED_CAST")
            val data = (doc.get("gData") as? Map<String, Any>) ?: emptyMap()
            onUpdate(gameIdx, phase, data)
        }
    }

    // Update only the phase
    suspend fun setPhase(sessionId: String, phase: String) {
        sessionRef(sessionId).update("gPhase", phase).await()
    }

    // Update phase + specific gData sub-fields using Firestore dot notation
    suspend fun setPhaseAndData(sessionId: String, phase: String, dataUpdates: Map<String, Any>) {
        val updates = mutableMapOf<String, Any>("gPhase" to phase)
        dataUpdates.forEach { (k, v) -> updates["gData.$k"] = v }
        sessionRef(sessionId).update(updates).await()
    }

    // Update gData sub-fields only (no phase change). Fire-and-forget for real-time updates.
    fun updateGameData(sessionId: String, dataUpdates: Map<String, Any>) {
        val updates = mutableMapOf<String, Any>()
        dataUpdates.forEach { (k, v) -> updates["gData.$k"] = v }
        sessionRef(sessionId).update(updates)
    }
}
