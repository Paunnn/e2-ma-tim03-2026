package com.tim03.slagalica.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.tim03.slagalica.data.model.KoZnaZnaQuestion
import kotlinx.coroutines.tasks.await

class KoZnaZnaRepository {
    private val db = FirebaseFirestore.getInstance()

    suspend fun getQuestions(count: Int = 5): List<KoZnaZnaQuestion> {
        val snapshot = db.collection("ko_zna_zna").get().await()
        return snapshot.documents.shuffled().take(count).map { doc ->
            KoZnaZnaQuestion(
                id = doc.id,
                question = doc.getString("question") ?: "",
                answers = (doc.get("answers") as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                correctIndex = (doc.getLong("correctIndex") ?: 0L).toInt()
            )
        }
    }
}
