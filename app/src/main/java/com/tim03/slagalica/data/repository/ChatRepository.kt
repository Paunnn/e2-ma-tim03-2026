package com.tim03.slagalica.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.tim03.slagalica.data.model.ChatMessage
import kotlinx.coroutines.tasks.await

class ChatRepository {
    private val db = FirebaseFirestore.getInstance()

    fun listenToMessages(region: String, onMessages: (List<ChatMessage>) -> Unit): ListenerRegistration {
        return db.collection("chats").document(region).collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .limitToLast(50)
            .addSnapshotListener { snap, _ ->
                val msgs = snap?.documents?.mapNotNull { doc ->
                    try {
                        ChatMessage(
                            id = doc.id,
                            senderUid = doc.getString("senderUid") ?: "",
                            senderName = doc.getString("senderName") ?: "",
                            text = doc.getString("text") ?: "",
                            timestamp = doc.getLong("timestamp") ?: 0L
                        )
                    } catch (_: Exception) { null }
                } ?: emptyList()
                onMessages(msgs)
            }
    }

    suspend fun sendMessage(region: String, uid: String, username: String, text: String) {
        val trimmed = text.trim()
        db.collection("chats").document(region).collection("messages").add(mapOf(
            "senderUid" to uid,
            "senderName" to username,
            "text" to trimmed,
            "timestamp" to System.currentTimeMillis()
        )).await()

        // Notify region members so they get a local notification even when on a different screen.
        // (Killed-app push requires Firebase Cloud Functions — not implemented here.)
        runCatching {
            val regionFilter = if (region == "Srbija") "" else region
            val regionUsers = db.collection("users")
                .whereEqualTo("region", regionFilter)
                .get().await()
            val preview = if (trimmed.length > 80) trimmed.take(77) + "…" else trimmed
            val timestamp = System.currentTimeMillis()
            val batch = db.batch()
            regionUsers.documents.forEach { doc ->
                if (doc.id != uid) {
                    batch.set(db.collection("notifications").document(), mapOf(
                        "userId" to doc.id,
                        "channel" to "CHAT",
                        "title" to "$username ($region)",
                        "message" to preview,
                        "timestamp" to timestamp,
                        "isRead" to false
                    ))
                }
            }
            batch.commit().await()
        }
    }
}
