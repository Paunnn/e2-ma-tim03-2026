package com.tim03.slagalica.data.repository

import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.tim03.slagalica.data.model.User
import kotlinx.coroutines.tasks.await

class AuthRepository {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    suspend fun register(
        email: String,
        username: String,
        region: String,
        password: String
    ): Result<Unit> = runCatching {
        val usernameExists = firestore.collection("users")
            .whereEqualTo("username", username)
            .get()
            .await()
            .isEmpty
            .not()
        if (usernameExists) error("Korisničko ime je već zauzeto")

        val result = auth.createUserWithEmailAndPassword(email, password).await()
        val user = result.user ?: error("Greška pri registraciji")

        val userDoc = User(
            uid = user.uid,
            email = email,
            username = username,
            region = region
        )
        firestore.collection("users").document(user.uid).set(userDoc).await()
        user.sendEmailVerification().await()
        auth.signOut()
    }

    suspend fun login(identifier: String, password: String): Result<FirebaseUser> = runCatching {
        val email = if (identifier.contains("@")) {
            identifier
        } else {
            val snapshot = firestore.collection("users")
                .whereEqualTo("username", identifier)
                .get()
                .await()
            snapshot.documents.firstOrNull()?.getString("email")
                ?: error("Korisnik nije pronađen")
        }

        val result = auth.signInWithEmailAndPassword(email, password).await()
        val user = result.user ?: error("Greška pri prijavi")

        user.reload().await()
        if (!user.isEmailVerified) {
            auth.signOut()
            error("EMAIL_NOT_VERIFIED")
        }

        // Save FCM token after successful login
        runCatching {
            val token = FirebaseMessaging.getInstance().token.await()
            firestore.collection("users").document(user.uid).update("fcmToken", token).await()
        }

        user
    }

    suspend fun sendVerificationEmail(): Result<Unit> = runCatching {
        val user = auth.currentUser ?: error("Niste prijavljeni")
        user.sendEmailVerification().await()
    }

    suspend fun changePassword(oldPassword: String, newPassword: String): Result<Unit> = runCatching {
        val user = auth.currentUser ?: error("Niste prijavljeni")
        val email = user.email ?: error("Email nije dostupan")
        val credential = EmailAuthProvider.getCredential(email, oldPassword)
        user.reauthenticate(credential).await()
        user.updatePassword(newPassword).await()
    }

    suspend fun loginAsGuest(): Result<Unit> = runCatching {
        auth.signInAnonymously().await()

        val guestNum = runCatching {
            val counterRef = firestore.collection("config").document("guestCounter")
            firestore.runTransaction { tx ->
                val snap = tx.get(counterRef)
                val next = (snap.getLong("count") ?: 0L) + 1L
                tx.set(counterRef, mapOf("count" to next))
                next
            }.await()
        }.getOrElse { (System.currentTimeMillis() % 10000) }

        auth.currentUser?.updateProfile(
            UserProfileChangeRequest.Builder().setDisplayName("Guest$guestNum").build()
        )?.await()
    }

    fun cleanupGuestIfNeeded() {
        val user = auth.currentUser
        if (user?.isAnonymous == true) {
            user.delete().addOnCompleteListener { auth.signOut() }
        }
    }

    fun logout() = auth.signOut()

    fun currentUser(): FirebaseUser? = auth.currentUser

    suspend fun getCurrentUserData(): User? {
        val uid = auth.currentUser?.uid ?: return null
        return firestore.collection("users").document(uid).get().await()
            .toObject(User::class.java)
    }
}
