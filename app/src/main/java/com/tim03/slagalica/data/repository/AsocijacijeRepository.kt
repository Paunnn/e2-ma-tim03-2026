package com.tim03.slagalica.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.tim03.slagalica.data.model.AsocijacijeQuestion
import com.tim03.slagalica.data.model.ColumnData
import kotlinx.coroutines.tasks.await

class AsocijacijeRepository {
    private val db = FirebaseFirestore.getInstance()

    @Suppress("UNCHECKED_CAST")
    suspend fun getRandomQuestion(): AsocijacijeQuestion? {
        return try {
            val snapshot = db.collection("asocijacije").get().await()
            if (snapshot.isEmpty) return null
            val doc = snapshot.documents.random()

            fun parseColumn(raw: Any?): ColumnData {
                val map = raw as? Map<String, Any> ?: return ColumnData()
                return ColumnData(
                    clues = (map["clues"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                    solution = (map["solution"] as? String) ?: ""
                )
            }

            AsocijacijeQuestion(
                id = doc.id,
                columnA = parseColumn(doc.get("columnA")),
                columnB = parseColumn(doc.get("columnB")),
                columnC = parseColumn(doc.get("columnC")),
                columnD = parseColumn(doc.get("columnD")),
                finalSolution = doc.getString("finalSolution") ?: ""
            )
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getTwoRandomQuestions(): Pair<AsocijacijeQuestion?, AsocijacijeQuestion?> {
        return try {
            val snapshot = db.collection("asocijacije").get().await()
            if (snapshot.isEmpty) return Pair(null, null)

            val docs = snapshot.documents.shuffled()

            fun parseColumn(raw: Any?): ColumnData {
                val map = raw as? Map<String, Any> ?: return ColumnData()
                return ColumnData(
                    clues = (map["clues"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                    solution = (map["solution"] as? String) ?: ""
                )
            }

            fun docToQuestion(doc: com.google.firebase.firestore.DocumentSnapshot): AsocijacijeQuestion =
                AsocijacijeQuestion(
                    id = doc.id,
                    columnA = parseColumn(doc.get("columnA")),
                    columnB = parseColumn(doc.get("columnB")),
                    columnC = parseColumn(doc.get("columnC")),
                    columnD = parseColumn(doc.get("columnD")),
                    finalSolution = doc.getString("finalSolution") ?: ""
                )

            val q1 = docToQuestion(docs[0])
            val q2 = if (docs.size > 1) docToQuestion(docs[1]) else docToQuestion(docs[0])
            Pair(q1, q2)
        } catch (e: Exception) {
            Pair(null, null)
        }
    }
}
