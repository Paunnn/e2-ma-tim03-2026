package com.tim03.slagalica.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.tim03.slagalica.data.model.KorakPoKorakQuestion
import kotlinx.coroutines.tasks.await

class KorakPoKorakRepository {
    private val firestore = FirebaseFirestore.getInstance()

    suspend fun getRandomQuestion(): KorakPoKorakQuestion {
        return try {
            val snapshot = firestore.collection("korak_po_korak").get().await()
            if (snapshot.isEmpty) return fallbackQuestion()
            val doc = snapshot.documents.random()
            KorakPoKorakQuestion(
                id = doc.id,
                answer = doc.getString("answer") ?: "",
                steps = (doc.get("steps") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            )
        } catch (e: Exception) {
            fallbackQuestion()
        }
    }

    private fun fallbackQuestion() = KorakPoKorakQuestion(
        id = "local_1",
        answer = "Steve Jobs",
        steps = listOf(
            "Osnivač kompanije Apple i Pixar",
            "Poznat po revolucionarnim prezentacijama",
            "Vratio se u Apple 1997. godine",
            "Predstavio prvi iPhone 2007. godine",
            "Autor knjige 'The Journey is the Reward'",
            "Studirao na Reed Collegeu",
            "Preminuo 2011. godine"
        )
    )
}
