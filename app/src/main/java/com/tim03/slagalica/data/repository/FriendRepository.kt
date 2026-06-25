package com.tim03.slagalica.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.tim03.slagalica.data.model.FriendInfo
import com.tim03.slagalica.data.model.Friendship
import kotlinx.coroutines.tasks.await

class FriendRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val requests = db.collection("friend_requests")
    private val users = db.collection("users")

    private fun myFriends(uid: String) = db.collection("friendships").document(uid).collection("list")

    suspend fun findUserByUid(uid: String): FriendInfo? {
        val doc = users.document(uid).get().await()
        return if (doc.exists()) parseFriendInfo(doc.id, doc.data) else null
    }

    suspend fun findUserByUsername(username: String): FriendInfo? {
        val result = users.whereEqualTo("username", username).limit(1).get().await()
        val doc = result.documents.firstOrNull() ?: return null
        return parseFriendInfo(doc.id, doc.data)
    }

    suspend fun sendFriendRequest(receiverId: String): Boolean {
        val myUid = auth.currentUser?.uid ?: return false
        if (myUid == receiverId) return false
        val me = users.document(myUid).get().await()
        val myName = me.getString("username") ?: return false
        val them = users.document(receiverId).get().await()
        val theirName = them.getString("username") ?: return false

        // Check if already friends or request pending
        val existing = myFriends(myUid).document(receiverId).get().await()
        if (existing.exists()) return false

        val ref = requests.document()
        ref.set(mapOf(
            "senderId" to myUid,
            "senderName" to myName,
            "receiverId" to receiverId,
            "receiverName" to theirName,
            "status" to "pending",
            "createdAt" to System.currentTimeMillis()
        )).await()
        return true
    }

    suspend fun acceptRequest(request: Friendship) {
        val myUid = auth.currentUser?.uid ?: return
        // Mark request accepted
        requests.document(request.id).update("status", "accepted").await()
        // Write to both users' friend lists
        val batch = db.batch()
        batch.set(myFriends(myUid).document(request.senderId), mapOf(
            "uid" to request.senderId,
            "username" to request.senderName,
            "addedAt" to System.currentTimeMillis()
        ))
        batch.set(myFriends(request.senderId).document(myUid), mapOf(
            "uid" to myUid,
            "username" to request.receiverName,
            "addedAt" to System.currentTimeMillis()
        ))
        batch.commit().await()
        // Clean up request
        requests.document(request.id).delete().await()
    }

    suspend fun rejectRequest(requestId: String) {
        runCatching { requests.document(requestId).delete().await() }
    }

    suspend fun removeFriend(friendUid: String) {
        val myUid = auth.currentUser?.uid ?: return
        myFriends(myUid).document(friendUid).delete().await()
        myFriends(friendUid).document(myUid).delete().await()
    }

    fun listenToPendingRequests(onRequests: (List<Friendship>) -> Unit): ListenerRegistration {
        val myUid = auth.currentUser?.uid ?: return requests.addSnapshotListener { _, _ -> }
        return requests
            .whereEqualTo("receiverId", myUid)
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snap, _ ->
                val list = snap?.documents?.mapNotNull { doc ->
                    try {
                        Friendship(
                            id = doc.id,
                            senderId = doc.getString("senderId") ?: "",
                            senderName = doc.getString("senderName") ?: "",
                            receiverId = doc.getString("receiverId") ?: "",
                            receiverName = doc.getString("receiverName") ?: "",
                            status = "pending",
                            createdAt = doc.getLong("createdAt") ?: 0L
                        )
                    } catch (_: Exception) { null }
                } ?: emptyList()
                onRequests(list)
            }
    }

    suspend fun getFriendsList(): List<FriendInfo> {
        val myUid = auth.currentUser?.uid ?: return emptyList()
        val friendDocs = myFriends(myUid).get().await()
        return friendDocs.documents.mapNotNull { doc ->
            val friendUid = doc.getString("uid") ?: return@mapNotNull null
            val userDoc = users.document(friendUid).get().await()
            parseFriendInfo(friendUid, userDoc.data)
        }
    }

    fun listenToFriendsList(onFriends: (List<FriendInfo>) -> Unit): ListenerRegistration {
        val myUid = auth.currentUser?.uid ?: return db.collection("friendships")
            .addSnapshotListener { _, _ -> }
        return myFriends(myUid).addSnapshotListener { snap, _ ->
            val uids = snap?.documents?.mapNotNull { it.getString("uid") } ?: emptyList()
            if (uids.isEmpty()) { onFriends(emptyList()); return@addSnapshotListener }
            // Fetch user data for each friend
            val friends = mutableListOf<FriendInfo>()
            var pending = uids.size
            uids.forEach { uid ->
                users.document(uid).get().addOnSuccessListener { doc ->
                    parseFriendInfo(uid, doc.data)?.let { friends.add(it) }
                    if (--pending == 0) onFriends(friends.sortedBy { it.username })
                }.addOnFailureListener {
                    if (--pending == 0) onFriends(friends.sortedBy { it.username })
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseFriendInfo(uid: String, data: Map<String, Any>?): FriendInfo? {
        if (data == null) return null
        return FriendInfo(
            uid = uid,
            username = data["username"] as? String ?: "",
            stars = (data["stars"] as? Long)?.toInt() ?: 0,
            league = (data["league"] as? Long)?.toInt() ?: 0,
            avatarIndex = (data["avatarIndex"] as? Long)?.toInt() ?: 0,
            monthlyStars = (data["monthlyStars"] as? Long)?.toInt() ?: 0,
            region = data["region"] as? String ?: ""
        )
    }
}
