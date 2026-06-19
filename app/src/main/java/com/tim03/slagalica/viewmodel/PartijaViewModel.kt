package com.tim03.slagalica.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.ListenerRegistration
import com.tim03.slagalica.data.repository.PartijaSessionRepository
import com.tim03.slagalica.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class PartijaGame {
    KO_ZNA_ZNA, SPOJNICE, MOJ_BROJ, KORAK_PO_KORAK, ASOCIJACIJE, SKOCKO
}

data class PartijaUiState(
    val currentGameIndex: Int = 0,
    val myTotal: Int = 0,
    val oppTotal: Int = 0,
    val opponentName: String = "Protivnik",
    val isComplete: Boolean = false,
    val resultSaved: Boolean = false,
    val waitingForOpponent: Boolean = false,
    val forfeitWon: Boolean = false,
    val forfeitLoss: Boolean = false
)

class PartijaViewModel(
    val sessionId: String = "",
    val isPlayer1: Boolean = true,
    private val userRepo: UserRepository = UserRepository(),
    private val sessionRepo: PartijaSessionRepository = PartijaSessionRepository()
) : ViewModel() {

    companion object {
        val GAME_ORDER = listOf(
            PartijaGame.KO_ZNA_ZNA,
            PartijaGame.SPOJNICE,
            PartijaGame.MOJ_BROJ,
            PartijaGame.KORAK_PO_KORAK,
            PartijaGame.ASOCIJACIJE,
            PartijaGame.SKOCKO
        )
    }

    val isMultiplayer = sessionId.isNotEmpty()

    private val _uiState = MutableStateFlow(PartijaUiState())
    val uiState: StateFlow<PartijaUiState> = _uiState.asStateFlow()

    private var sessionListener: ListenerRegistration? = null

    init {
        if (isMultiplayer) listenToSession()
    }

    private fun listenToSession() {
        sessionListener = sessionRepo.listenToSession(sessionId) { session ->
            val s = _uiState.value
            if (s.isComplete) return@listenToSession

            val oppName = if (isPlayer1) session.player2Name else session.player1Name

            if (session.status == "forfeited" && session.forfeitedBy.isNotEmpty()) {
                val opponentForfeited = (isPlayer1 && session.forfeitedBy == "player2") ||
                                        (!isPlayer1 && session.forfeitedBy == "player1")
                _uiState.value = s.copy(
                    isComplete = true,
                    waitingForOpponent = false,
                    opponentName = oppName,
                    forfeitWon = opponentForfeited,
                    forfeitLoss = !opponentForfeited
                )
                return@listenToSession
            }

            if (s.waitingForOpponent) {
                val gameKey = s.currentGameIndex.toString()
                val oppScores = if (isPlayer1) session.player2GameScores else session.player1GameScores
                val oppScore = oppScores[gameKey]

                if (oppScore != null) {
                    val newOpp = s.oppTotal + oppScore.toInt()
                    val nextIndex = s.currentGameIndex + 1
                    _uiState.value = if (nextIndex >= GAME_ORDER.size) {
                        s.copy(oppTotal = newOpp, isComplete = true, waitingForOpponent = false, opponentName = oppName)
                    } else {
                        s.copy(currentGameIndex = nextIndex, oppTotal = newOpp, waitingForOpponent = false, opponentName = oppName)
                    }
                } else {
                    _uiState.value = s.copy(opponentName = oppName)
                }
            } else {
                _uiState.value = s.copy(opponentName = oppName)
            }
        }
    }

    fun forfeit() {
        val s = _uiState.value
        if (s.isComplete) return
        _uiState.value = s.copy(isComplete = true, waitingForOpponent = false, forfeitLoss = true)
        viewModelScope.launch {
            runCatching { sessionRepo.forfeit(sessionId, isPlayer1) }
        }
    }

    // Called by each game screen when it finishes.
    // In multiplayer, myScore is submitted to Firestore and oppScore is ignored
    // (the real opponent score arrives via the session listener).
    fun gameCompleted(myScore: Int, oppScore: Int) {
        val s = _uiState.value
        if (isMultiplayer) {
            val newMy = s.myTotal + myScore
            _uiState.value = s.copy(myTotal = newMy, waitingForOpponent = true)
            viewModelScope.launch {
                runCatching {
                    sessionRepo.submitGameScore(sessionId, isPlayer1, s.currentGameIndex, myScore)
                }
            }
        } else {
            val newMy = s.myTotal + myScore
            val newOpp = s.oppTotal + oppScore
            val nextIndex = s.currentGameIndex + 1
            _uiState.value = if (nextIndex >= GAME_ORDER.size) {
                s.copy(myTotal = newMy, oppTotal = newOpp, isComplete = true)
            } else {
                s.copy(currentGameIndex = nextIndex, myTotal = newMy, oppTotal = newOpp)
            }
        }
    }

    fun saveResult() {
        val s = _uiState.value
        if (s.resultSaved) return
        _uiState.value = s.copy(resultSaved = true)
        val won = when {
            s.forfeitWon -> true
            s.forfeitLoss -> false
            else -> s.myTotal > s.oppTotal
        }
        val drawn = !s.forfeitWon && !s.forfeitLoss && s.myTotal == s.oppTotal
        viewModelScope.launch {
            runCatching {
                userRepo.savePartijaResult(won, drawn)
                if (isMultiplayer && !s.forfeitLoss) sessionRepo.completeSession(sessionId)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        sessionListener?.remove()
    }
}

class PartijaViewModelFactory(
    private val sessionId: String,
    private val isPlayer1: Boolean
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        PartijaViewModel(sessionId = sessionId, isPlayer1 = isPlayer1) as T
}
