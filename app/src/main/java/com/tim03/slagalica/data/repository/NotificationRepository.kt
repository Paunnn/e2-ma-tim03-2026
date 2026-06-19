package com.tim03.slagalica.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.tim03.slagalica.data.model.NotificationModel
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class NotificationRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    fun observeNotifications(): Flow<List<NotificationModel>> = callbackFlow {
        var firestoreListener: ListenerRegistration? = null

        val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            firestoreListener?.remove()
            firestoreListener = null

            val uid = firebaseAuth.currentUser?.uid
            if (uid == null) {
                trySend(emptyList())
                return@AuthStateListener
            }

            firestoreListener = db.collection("notifications")
                .whereEqualTo("userId", uid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) {
                        trySend(emptyList())
                        return@addSnapshotListener
                    }
                    val items = snapshot.documents.mapNotNull { doc ->
                        try {
                            NotificationModel(
                                id = doc.id,
                                userId = doc.getString("userId") ?: "",
                                channel = doc.getString("channel") ?: "OTHER",
                                title = doc.getString("title") ?: "",
                                message = doc.getString("message") ?: "",
                                timestamp = doc.getLong("timestamp") ?: 0L,
                                isRead = doc.getBoolean("isRead") ?: false
                            )
                        } catch (e: Exception) {
                            null
                        }
                    }.sortedByDescending { it.timestamp }
                    trySend(items)
                }
        }

        auth.addAuthStateListener(authStateListener)

        awaitClose {
            firestoreListener?.remove()
            auth.removeAuthStateListener(authStateListener)
        }
    }

    suspend fun markAsRead(notificationId: String) {
        try {
            db.collection("notifications").document(notificationId)
                .update("isRead", true).await()
        } catch (_: Exception) {}
    }

    suspend fun markAllAsRead() {
        val uid = auth.currentUser?.uid ?: return
        try {
            val unread = db.collection("notifications")
                .whereEqualTo("userId", uid)
                .whereEqualTo("isRead", false)
                .get().await()
            val batch = db.batch()
            unread.documents.forEach { doc ->
                batch.update(doc.reference, "isRead", true)
            }
            batch.commit().await()
        } catch (_: Exception) {}
    }

    suspend fun addNotification(userId: String, channel: String, title: String, message: String) {
        try {
            db.collection("notifications").add(
                hashMapOf(
                    "userId" to userId,
                    "channel" to channel,
                    "title" to title,
                    "message" to message,
                    "timestamp" to System.currentTimeMillis(),
                    "isRead" to false
                )
            ).await()
        } catch (_: Exception) {}
    }

    suspend fun seedSampleNotificationsIfEmpty() {
        val uid = auth.currentUser?.uid ?: return
        try {
            val existing = db.collection("notifications")
                .whereEqualTo("userId", uid).limit(1).get().await()
            if (!existing.isEmpty) return

            val samples = listOf(
                Triple("RANKING", "Nedeljni rezultati!", "Zauzeli ste 3. mesto na nedeljnoj rang listi."),
                Triple("REWARD", "Nagrada!", "Dobili ste 2 tokena za 3. mesto na rang listi."),
                Triple("REWARD", "Nova liga!", "Prešli ste u 1. ligu! Čestitamo!"),
                Triple("RANKING", "Mesečni rezultati", "Zauzeli ste 5. mesto na mesečnoj rang listi."),
                Triple("OTHER", "Dnevne misije", "Završili ste sve dnevne misije. Dobijate bonus!"),
                Triple("OTHER", "Dobrodošli!", "Dobrodošli u Slagalicu. Srećno u takmičenju!")
            )
            samples.forEach { (ch, title, msg) -> addNotification(uid, ch, title, msg) }
        } catch (_: Exception) {}
    }
}
