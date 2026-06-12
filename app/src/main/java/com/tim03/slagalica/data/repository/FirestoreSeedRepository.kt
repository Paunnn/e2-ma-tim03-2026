package com.tim03.slagalica.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class FirestoreSeedRepository {
    private val db = FirebaseFirestore.getInstance()

    suspend fun seedDatabase() {
        seedKorakPoKorak()
        seedAsocijacije()
    }

    private suspend fun seedKorakPoKorak() {
        val collection = db.collection("korak_po_korak")
        val existing = collection.get().await()
        if (!existing.isEmpty) return

        val questions = listOf(
            mapOf(
                "answer" to "Steve Jobs",
                "steps" to listOf(
                    "Osnivač kompanije Apple i Pixar",
                    "Poznat po revolucionarnim prezentacijama proizvoda",
                    "Vratio se u Apple 1997. godine kao CEO",
                    "Predstavio prvi iPhone 2007. godine",
                    "Adoptiran pri rođenju, odrastao u Kaliforniji",
                    "Studirao na Reed Collegeu, napustio studije",
                    "Preminuo 2011. godine od raka gušterače"
                )
            ),
            mapOf(
                "answer" to "Nikola Tesla",
                "steps" to listOf(
                    "Srpsko-američki pronalazač i inženjer",
                    "Radio na razvoju naizmenične struje (AC)",
                    "Imao je slavni sukob sa Thomasom Edisonom",
                    "Osmislio je bežični prenos energije",
                    "Laboratorija u Kolumbiji Springs u Koloradu",
                    "Umro je sam u hotelskoj sobi u Njujorku",
                    "Rodio se 10. jula 1856. u Smiljanu u Lici"
                )
            ),
            mapOf(
                "answer" to "Novak Đoković",
                "steps" to listOf(
                    "Srpski teniser, jedan od najvećih svih vremena",
                    "Poznat je po neverovatnoj fizičkoj kondiciji",
                    "Drži rekord u broju nedjelja na ATP listi broj 1",
                    "Osvojio je sve četiri Grand Slam turnira",
                    "Odrastao je u Srbiji, trenirao u Münchenu",
                    "Pobednik Wimbledona više puta",
                    "Nadimak mu je 'Nole'"
                )
            ),
            mapOf(
                "answer" to "Leonardo da Vinci",
                "steps" to listOf(
                    "Renesansni genije, umetnik i naučnik",
                    "Naslikao je Mona Lisu",
                    "Projektovao je leteće mašine pre aviona",
                    "Radio je detaljne anatomske skice ljudskog tela",
                    "Živeo je u Italiji u 15. i 16. veku",
                    "Njegova dela se čuvaju u Luvru u Parizu",
                    "Rodio se u Vinčiju blizu Firence 1452. godine"
                )
            ),
            mapOf(
                "answer" to "Albert Einstein",
                "steps" to listOf(
                    "Nemački fizičar, jedan od najvećih naučnika",
                    "Razvio je teoriju relativiteta",
                    "Dobitnik Nobelove nagrade za fiziku 1921. godine",
                    "Poznata formula E=mc²",
                    "Emigrirao je u SAD bežeći od nacizma",
                    "Radio je u Princeton Institute for Advanced Study",
                    "Rodio se u Ulmu u Nemačkoj 1879. godine"
                )
            )
        )
        questions.forEach { collection.add(it).await() }
    }

    private suspend fun seedAsocijacije() {
        val collection = db.collection("asocijacije")
        val existing = collection.get().await()
        if (!existing.isEmpty) return

        val questions = listOf(
            mapOf(
                "columnA" to mapOf(
                    "clues" to listOf("Tigar", "Lav", "Leopard", "Gepard"),
                    "solution" to "Mačke"
                ),
                "columnB" to mapOf(
                    "clues" to listOf("Ajkula", "Delfin", "Hobotnica", "Tuna"),
                    "solution" to "Morska bića"
                ),
                "columnC" to mapOf(
                    "clues" to listOf("Orao", "Sova", "Vrabac", "Labud"),
                    "solution" to "Ptice"
                ),
                "columnD" to mapOf(
                    "clues" to listOf("Pas", "Mačka", "Hamster", "Zec"),
                    "solution" to "Kućni ljubimci"
                ),
                "finalSolution" to "ŽIVOTINJE"
            ),
            mapOf(
                "columnA" to mapOf(
                    "clues" to listOf("Beograd", "Novi Sad", "Niš", "Kragujevac"),
                    "solution" to "Srpski gradovi"
                ),
                "columnB" to mapOf(
                    "clues" to listOf("Drina", "Sava", "Tisa", "Morava"),
                    "solution" to "Reke Srbije"
                ),
                "columnC" to mapOf(
                    "clues" to listOf("Đoković", "Ivanović", "Troicki", "Zimnjić"),
                    "solution" to "Srpski teniseri"
                ),
                "columnD" to mapOf(
                    "clues" to listOf("Kopaonik", "Tara", "Zlatibor", "Fruška Gora"),
                    "solution" to "Planine Srbije"
                ),
                "finalSolution" to "SRBIJA"
            ),
            mapOf(
                "columnA" to mapOf(
                    "clues" to listOf("Paris", "London", "Berlin", "Madrid"),
                    "solution" to "Evropske prestonice"
                ),
                "columnB" to mapOf(
                    "clues" to listOf("Pikaso", "Rembrant", "Da Vinči", "Mikelanđelo"),
                    "solution" to "Slikari"
                ),
                "columnC" to mapOf(
                    "clues" to listOf("Gitara", "Klavir", "Violina", "Bubnjevi"),
                    "solution" to "Instrumenti"
                ),
                "columnD" to mapOf(
                    "clues" to listOf("Pitagora", "Arhimed", "Euklid", "Njutn"),
                    "solution" to "Naučnici"
                ),
                "finalSolution" to "KULTURA"
            ),
            mapOf(
                "columnA" to mapOf(
                    "clues" to listOf("Kabadahija", "Uštipak", "Gibanica", "Prebranac"),
                    "solution" to "Srpska jela"
                ),
                "columnB" to mapOf(
                    "clues" to listOf("Rakija", "Vino", "Pivo", "Sok od šljive"),
                    "solution" to "Srpska pića"
                ),
                "columnC" to mapOf(
                    "clues" to listOf("Slava", "Krsna slava", "Božić", "Vaskrs"),
                    "solution" to "Srpski praznici"
                ),
                "columnD" to mapOf(
                    "clues" to listOf("Šajkača", "Opanci", "Narodna nošnja", "Vezeni prsluk"),
                    "solution" to "Srpska tradicija"
                ),
                "finalSolution" to "SRPSKA KULTURA"
            )
        )
        questions.forEach { collection.add(it).await() }
    }
}
