package com.tim03.slagalica.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.tim03.slagalica.data.model.SpojniceQuestion
import kotlinx.coroutines.tasks.await

class SpojniceRepository {
    private val db = FirebaseFirestore.getInstance()

    suspend fun getRounds(count: Int = 2): List<SpojniceQuestion> {
        val snapshot = db.collection("spojnice").get().await()
        return snapshot.documents.shuffled().take(count).map { doc ->
            SpojniceQuestion(
                id = doc.id,
                criterion = doc.getString("criterion") ?: "",
                leftItems = (doc.get("leftItems") as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                rightItems = (doc.get("rightItems") as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                correctMapping = (doc.get("correctMapping") as? List<*>)?.map { (it as? Long)?.toInt() ?: 0 } ?: emptyList()
            )
        }
    }
}
