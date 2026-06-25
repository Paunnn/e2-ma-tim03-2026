package com.tim03.slagalica.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await

data class FriendInvite(
    val id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val receiverId: String = "",
    val status: String = "pending", // pending, accepted, rejected, cancelled
    val sessionId: String = "",
    val createdAt: Long = 0L
)

class FriendInviteRepository {
    private val db = FirebaseFirestore.getInstance()
    private val invites = db.collection("friend_invites")
    private val sessions = db.collection("partija_sessions")

    suspend fun findUserByUsername(username: String): Pair<String, String>? {
        val result = db.collection("users")
            .whereEqualTo("username", username).limit(1).get().await()
        val doc = result.documents.firstOrNull() ?: return null
        return Pair(doc.id, doc.getString("username") ?: "")
    }

    suspend fun sendInvite(senderId: String, senderName: String, receiverId: String): String {
        val ref = invites.document()
        ref.set(mapOf(
            "senderId" to senderId,
            "senderName" to senderName,
            "receiverId" to receiverId,
            "status" to "pending",
            "sessionId" to "",
            "createdAt" to System.currentTimeMillis()
        )).await()
        return ref.id
    }

    suspend fun acceptInvite(
        inviteId: String,
        senderId: String,
        senderName: String,
        receiverId: String,
        receiverName: String
    ): String {
        val sessionRef = sessions.document()
        sessionRef.set(mapOf(
            "player1Uid" to senderId,
            "player2Uid" to receiverId,
            "player1Name" to senderName,
            "player2Name" to receiverName,
            "playerUids" to listOf(senderId, receiverId),
            "status" to "active",
            "isFriendly" to true,
            "player1GameScores" to mapOf<String, Any>(),
            "player2GameScores" to mapOf<String, Any>(),
            "createdAt" to FieldValue.serverTimestamp()
        )).await()
        invites.document(inviteId).update(mapOf(
            "status" to "accepted",
            "sessionId" to sessionRef.id
        )).await()
        return sessionRef.id
    }

    suspend fun rejectInvite(inviteId: String) {
        runCatching { invites.document(inviteId).update("status", "rejected").await() }
    }

    suspend fun cancelInvite(inviteId: String) {
        runCatching { invites.document(inviteId).update("status", "cancelled").await() }
    }

    fun listenForIncomingInvites(
        myUid: String,
        onInvite: (FriendInvite?) -> Unit
    ): ListenerRegistration {
        return invites
            .whereEqualTo("receiverId", myUid)
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snap, _ ->
                val doc = snap?.documents?.firstOrNull()
                if (doc == null) {
                    onInvite(null)
                    return@addSnapshotListener
                }
                onInvite(FriendInvite(
                    id = doc.id,
                    senderId = doc.getString("senderId") ?: "",
                    senderName = doc.getString("senderName") ?: "",
                    receiverId = myUid,
                    status = "pending",
                    createdAt = doc.getLong("createdAt") ?: 0L
                ))
            }
    }

    fun listenForInviteResponse(
        inviteId: String,
        onUpdate: (FriendInvite) -> Unit
    ): ListenerRegistration {
        return invites.document(inviteId).addSnapshotListener { snap, _ ->
            val doc = snap?.takeIf { it.exists() } ?: return@addSnapshotListener
            onUpdate(FriendInvite(
                id = doc.id,
                senderId = doc.getString("senderId") ?: "",
                senderName = doc.getString("senderName") ?: "",
                receiverId = doc.getString("receiverId") ?: "",
                status = doc.getString("status") ?: "pending",
                sessionId = doc.getString("sessionId") ?: ""
            ))
        }
    }
}
