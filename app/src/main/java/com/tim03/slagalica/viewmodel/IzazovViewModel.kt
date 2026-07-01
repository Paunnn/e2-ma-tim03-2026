package com.tim03.slagalica.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import com.tim03.slagalica.data.model.IzazovSession
import com.tim03.slagalica.data.repository.IzazovRepository
import com.tim03.slagalica.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class IzazovPhase { LIST, CREATING, PLAYING, RESULTS }

data class IzazovQuestion(val question: String, val options: List<String>, val correctIndex: Int)

private val QUESTIONS = listOf(
    IzazovQuestion("Koji grad je prestonica Srbije?", listOf("Novi Sad", "Beograd", "Niš", "Kragujevac"), 1),
    IzazovQuestion("Koliko sati ima jedan dan?", listOf("12", "18", "24", "48"), 2),
    IzazovQuestion("Koji broj je prost?", listOf("15", "16", "17", "18"), 2),
    IzazovQuestion("Koliko strana ima kocka?", listOf("4", "6", "8", "12"), 1),
    IzazovQuestion("Koliko meseci ima u godini?", listOf("10", "11", "12", "13"), 2),
    IzazovQuestion("Ko je napisao roman 'Na Drini ćuprija'?", listOf("Ivo Andrić", "Meša Selimović", "Dobrica Ćosić", "Branko Ćopić"), 0),
    IzazovQuestion("Koliko igrača ima fudbalski tim na terenu?", listOf("9", "10", "11", "12"), 2),
    IzazovQuestion("Koji je hemijski simbol zlata?", listOf("Zl", "Gl", "Au", "Ag"), 2),
    IzazovQuestion("Koliko stepeni ima pravi ugao?", listOf("45", "60", "90", "180"), 2),
    IzazovQuestion("Koji kontinent je najveći?", listOf("Amerika", "Afrika", "Azija", "Evropa"), 2),
)

data class IzazovUiState(
    val phase: IzazovPhase = IzazovPhase.LIST,
    val openSessions: List<IzazovSession> = emptyList(),
    val activeSession: IzazovSession? = null,
    val activeIzazovId: String = "",
    val createBidStars: Int = 0,
    val createBidTokens: Int = 0,
    val gameQuestions: List<IzazovQuestion> = emptyList(),
    val currentQuestionIndex: Int = 0,
    val selectedAnswer: Int = -1,
    val gameScore: Int = 0,
    val gameComplete: Boolean = false,
    val myUid: String = "",
    val myRegion: String = "",
    val myTokens: Int = 0,
    val myStars: Int = 0
)

class IzazovViewModel(
    private val repo: IzazovRepository = IzazovRepository(),
    private val userRepo: UserRepository = UserRepository()
) : ViewModel() {

    private val auth = FirebaseAuth.getInstance()

    private val _uiState = MutableStateFlow(IzazovUiState())
    val uiState: StateFlow<IzazovUiState> = _uiState.asStateFlow()

    private var listListener: ListenerRegistration? = null
    private var sessionListener: ListenerRegistration? = null

    init {
        loadUser()
    }

    private fun loadUser() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            runCatching {
                val user = userRepo.getCurrentUser() ?: return@runCatching
                val region = user.region.ifEmpty { "Srbija" }
                _uiState.value = _uiState.value.copy(
                    myUid = user.uid,
                    myRegion = region,
                    myTokens = user.tokens,
                    myStars = user.stars
                )
                startListeningToOpenSessions(region)
            }
        }
    }

    private fun startListeningToOpenSessions(region: String = _uiState.value.myRegion) {
        listListener?.remove()
        listListener = repo.listenToOpenSessions(region) { sessions ->
            _uiState.value = _uiState.value.copy(openSessions = sessions)
        }
    }

    fun setBidStars(value: Int) {
        _uiState.value = _uiState.value.copy(createBidStars = value.coerceIn(0, 10))
    }

    fun setBidTokens(value: Int) {
        _uiState.value = _uiState.value.copy(createBidTokens = value.coerceIn(0, 2))
    }

    fun createIzazov() {
        val uid = auth.currentUser?.uid ?: return
        val s = _uiState.value
        if (s.createBidStars == 0 && s.createBidTokens == 0) return
        viewModelScope.launch {
            runCatching {
                val user = userRepo.getCurrentUser() ?: return@runCatching
                if (user.stars < s.createBidStars || user.tokens < s.createBidTokens) return@runCatching
                val izazovId = repo.createIzazov(uid, user.username, s.myRegion, s.createBidStars, s.createBidTokens)
                startGame(izazovId, s.createBidStars, s.createBidTokens)
            }
        }
    }

    fun joinIzazov(session: IzazovSession) {
        val uid = auth.currentUser?.uid ?: return
        if (!session.canJoin(uid)) return
        viewModelScope.launch {
            runCatching {
                val user = userRepo.getCurrentUser() ?: return@runCatching
                if (user.stars < session.bidStars || user.tokens < session.bidTokens) return@runCatching
                repo.joinIzazov(session.id, uid, user.username, session.bidStars, session.bidTokens)
                startGame(session.id, session.bidStars, session.bidTokens)
            }
        }
    }

    fun playExisting(session: IzazovSession) {
        startGame(session.id, session.bidStars, session.bidTokens)
    }

    fun viewResults(session: IzazovSession) {
        val uid = auth.currentUser?.uid ?: return
        sessionListener?.remove()
        sessionListener = repo.listenToSession(session.id) { updated ->
            _uiState.value = _uiState.value.copy(activeSession = updated)
        }
        _uiState.value = _uiState.value.copy(
            phase = IzazovPhase.RESULTS,
            activeSession = session,
            activeIzazovId = session.id,
            myUid = uid
        )
    }

    private fun startGame(izazovId: String, bidStars: Int, bidTokens: Int) {
        val uid = auth.currentUser?.uid ?: return
        val shuffled = QUESTIONS.shuffled().take(5)
        _uiState.value = _uiState.value.copy(
            phase = IzazovPhase.PLAYING,
            activeIzazovId = izazovId,
            gameQuestions = shuffled,
            currentQuestionIndex = 0,
            selectedAnswer = -1,
            gameScore = 0,
            gameComplete = false,
            myUid = uid
        )
    }

    fun selectAnswer(answerIndex: Int) {
        val s = _uiState.value
        if (s.selectedAnswer != -1) return
        val question = s.gameQuestions.getOrNull(s.currentQuestionIndex) ?: return
        val correct = answerIndex == question.correctIndex
        val newScore = s.gameScore + if (correct) 10 else 0
        _uiState.value = s.copy(selectedAnswer = answerIndex, gameScore = newScore)
    }

    fun nextQuestion() {
        val s = _uiState.value
        val next = s.currentQuestionIndex + 1
        if (next >= s.gameQuestions.size) {
            // Game done
            _uiState.value = s.copy(gameComplete = true, currentQuestionIndex = next, selectedAnswer = -1)
            viewModelScope.launch {
                runCatching {
                    repo.submitScore(s.activeIzazovId, s.myUid, s.gameScore)
                    // Reload session for results
                    sessionListener?.remove()
                    sessionListener = repo.listenToSession(s.activeIzazovId) { updated ->
                        _uiState.value = _uiState.value.copy(activeSession = updated)
                    }
                    _uiState.value = _uiState.value.copy(phase = IzazovPhase.RESULTS)
                    loadUser()
                }
            }
        } else {
            _uiState.value = s.copy(currentQuestionIndex = next, selectedAnswer = -1)
        }
    }

    fun backToList() {
        listListener?.remove()
        listListener = null
        sessionListener?.remove()
        sessionListener = null
        val region = _uiState.value.myRegion
        val uid = _uiState.value.myUid
        val tokens = _uiState.value.myTokens
        val stars = _uiState.value.myStars
        _uiState.value = IzazovUiState(myUid = uid, myRegion = region, myTokens = tokens, myStars = stars)
        startListeningToOpenSessions(region)
        loadUser()
    }

    override fun onCleared() {
        super.onCleared()
        listListener?.remove()
        sessionListener?.remove()
    }
}
