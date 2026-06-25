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
        db.collection("chats").document(region).collection("messages").add(mapOf(
            "senderUid" to uid,
            "senderName" to username,
            "text" to text.trim(),
            "timestamp" to System.currentTimeMillis()
        )).await()
    }
}
